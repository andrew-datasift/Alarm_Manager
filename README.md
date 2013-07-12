Alarm_Manager
=============

Graphite alarm generator
A utility to take in metrics from Graphite, compare the results to given thresholds, and produce alarms in Zenoss based on a series of rules.


Configuration
=============

The alarm generator takes its configuration from a file in json format which is passed using the -c command line argument and is read on startup. Logging is configured seperately via log4j (see logging section below).

An example configuration file is included in the repository which, combined with this document, should make the configuration schema clear.

At the top level the json object is split into two sections:
* 'config' - Details for connecting to graphite and zenoss, as well as the location of a state file for persisting information
* 'alarms' - An array containing each of the alarms that will be handled by the application.

### 'config' section

	"config": {
		"graphite": {
			"address": "localhost",
			"port": 81,
			"username": "",
			"password": ""
		},
		"zenoss": {
			"address": "https://zenoss.example.net",
			"port": "9080",
			"username": "alarmmanager",
			"password": "xxxyyyzzz"
		},
		"statefile": "/opt/alarmmanager/.graphitezenossstate",
		"httpport": 8080 
	},

This sections for zenoss and graphite should be self explanatory. They both communicate via HTTP and will assume basic auth via http unless "https" is prepended to the address.
The statefile is used to store the current state of the alarm manager so that it can keep track of triggered alarms, suspended metrics, etc, past restarts.
The HTTP port is used for the API detailed below, 8080 is assumed unless specified otherwise. If the requested port is unavailable then a warning will be printed in the log but execution will not be affected.

### 'alarms' section

	{
		"active": true,
		"name": "Read events average",
		"path": "average(devices.servers.*.reader.events_processed)",
		"type": "absolute",
		"component": "graphite",
		"event_class": "/Status",
		"summary": "Average number of reader events processed",
		"description": "The average number of events processed by the reader module across all servers",
		"trigger_increments": 10,
		"clear_increments": 5,
		"clear": 10000,
		"severity3": 8000,
		"severity4": 5000,
		"threshold_type": "min"
	},
	{
		"active": true,
		"name": "Growth of space used by mysql database on db1",
		"path": "services.mysql.db1.space_used",
		"type": "ROC absolute",
		"component": "graphite",
		"event_class": "/Status",
		"trigger_increments": 5,
		"clear_increments": 5,
		"window": 30,
		"severity3": 100,
		"threshold_type": "max"
	},


Each entry in the alarms array is a tuple containing the following values (If a default is given the value is not required):

* 'active' - "true" or "false" used to activate or deactivate the alarm (default: true)
* 'name' - Plain text id for this alarm. This will be used for the alarm summary unless specified otherwise. (required)
* 'path' - The graphite metric path that is to be measured (required).
* 'type' - The kind of alarm to be measured, eg absolute value, rate of change, holt winters boundries, etc (required).
* 'component' - String that will be used to populate the component field of the zenoss alarm when triggered (required).
* 'event_class' - String that will be used to populate the event class field of the zenoss alarm (default: "/Status").
* 'summary' - String that prefixes the current value and threshold in the summary field of the alarm (default: the alarm name)
* 'description' - A plain text description value for reference only. Can be used to include helpful information for dealing with alarm, or any other useful info.
* 'trigger_increments' - the number of times the metric must breach the threshold to activate the alarm (requrired).
* 'clear_increments' - the number of times the metric must be below the clear threshold for an alarm to be cleared (requrired).
* 'threshold_type' - "max" or "min". If max then alarm is triggered if the value is above the threshold, if min then triggered when below. (default: min)
* 'window' - [Only used for rate of change alarm] - the number of values to use for rate of change calculation.


Threshold Configuration
=======================

Thresholds can be set for multiple severities for one alarm; 1(debug)-5(critical) and clear. The metric must be above the threshold for that severity for the alarm to trigger, but below the clear threshold for the alarm to clear (or vice versa if it is a min value alarm). EG:

		"trigger_increments": 5,  Value must stay above threshold for 5 minutes to alarm
		"clear_increments": 5,    Value must stay below or equal to threshold for 5 minutes to clear
		"severity4": 15'          Trigger a severity 4 (error) alarm if the value goes above 15 for the required number of increments.
		"severity5": 20'          Trigger a severity 5 (critical) alarm if the value goes above 20 for the required number of increments.
		"clear": 10'              Clear alarm if the value goes below 10 for the required number of increments.

So in the example above a metric that goes to 15 would not alarm. If it goes to 16 then after 5 minutes it would trigger a severity 4 alarm. If it goes to 21 then after 5 minutes it would become severity 5. If it dropped back to 11 then the alarm would not clear, but it would not be incremented further. If it dropped to 10 then it would be cleared after 5 minutes.

Alarms can also have different thresholds at different times of day. For example during known quiet periods low throughput alarms could have lower thresholds, or alarms could trigger earlier during working hours to catch problems early before the end of the day. This is done by adding a "times" tuple to the alarm config:


       "times":
        [
            {
            "start": 0900,
            "end": 1730,
            "severity3": 600,
            "severity2": 120,
            "clear": 10
            },
            {
            "start": 1730,
            "end": 0900,
            "severity2": 120,
            "clear": 10
            }
        ]

Those thresholds will then be used between the start and end times. The alarm manager makes no attempt to sanity check the times given, they can overlap and have gaps. When a set of results comes in it will check the values in the "times" array in order to see if the current time is after the start time and before the end time, if so it will use those thresholds. If it gets to the last entry without a match then it will look for default severity thresholds without specified times, and if there is still no match it will stop processing that alarm.


Adjusting alarms via the JSON API
========

Sometimes an alarm goes off in error, is due to a known fault, or the cause has been fixed but it will take time for the situation to resolve itself. In these cases you want to clear the alarm, but you also want to be notified if the problem re-occurs or the metrics start heading in the wrong direction again. Acknowledging the alarm effectively silences forever until it clears and comes back, which can hide further issues or lead to an alarm going off again when not wanted.

The alarmmanager allows you to adjust the threshold for any alarm by a given amount for a given time period. For example if a queue length alarm is triggered and then the cause is fixed, but the queue will take hours to fix. You only want the alarm to go off again if the queue takes longer than expected to clear, or if it starts going in the wrong direction again.

#### retrieving currently configured alarms

The alarm manager exposes the http api at the address specified in the config file, a request to this address will return all of the configured alarms in JSON format. Each alarm has a unique internal ID generated from the path, component, alarm type and threshold type, which will be needed when making changes to alarms.

EG

    [batman:~]$ curl "localhost:8080"
    {"alarms": [
    {"ID": "-1984525734", "name": "OpenConnections", "component": "OpenConnections", "type": "absolute", "threshold_type": "max", "metric_path":sumSeries(platform.servers.*.*.active_connections), "threshold_offset":0.0},
    {"ID": "-1417137606", "name": "Number of open files", "component": "Number of open files", "type": "absolute", "threshold_type": "max", "metric_path":maxSeries(devices.servers.*.exampleapp.lsof), "threshold_offset":0.0},
    {"ID": "1023009232", "name": "inputstream_filter_rate", "component": "filtering_rate", "type": "absolute", "threshold_type": "min", "metric_path":filtering.recieved.stream.rate, "threshold_offset":0.0}
    ]
    }

Alternatively you can request just the alarms that are currently triggered:

    [batman:~]$ curl "localhost:8080"
    {"alarms": [
    {"ID": "-1984525734", "name": "OpenConnections", "component": "OpenConnections", "type": "absolute", "threshold_type": "max", "metric_path":sumSeries(platform.servers.*.*.active_connections), "threshold_offset":0.0}
    }

The ID field is the identifier which needs to be used to adjust the alarm, the threshold offset field is the current value by which the threshold is adjusted.

#### Adjusting the threshold

To adjust the threshold use a post in the following format:

    [batman:~]$ curl -XPOST "localhost:8080/?set_offset&alarm_id=-1984525734&offset=100&minutes=60"
    Offset for alarm -1984525734 set to 100.0 for 60 minutes.

The thresholds for this alarm will then be increased by 100 for one hour then revert back to normal. The offset is applied to all severity thresholds, and can be both positive and negative.



Logging via LOG4J
=================

Logging is configured via a log4j properties file. There are two loggers that can be configured seperately:

log4j.logger.AlarmManager - this will configure the logging that is internal to the alarm manager. Setting to debug will show internal information regarding each run of metrics checking.
log4j.logger.org.apache.http - This is the logger for the apache http libraries used by the alarm manager. Debug on this logger will produce very verbose information on the communication with graphite and zenoss.