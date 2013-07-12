Alarm_Manager
=============

Graphite alarm generator
A utility to take in metrics from Graphite, compare the results to given thresholds, and produce alarms in Zenoss based on a series of rules.


Configuration
=============

The alarm generator takes its configuration from a file in json format which is passed using the -c command line argument and is read on startup.

An example configuration file is included in the repository which, combined with this document, should make the configuration schema clear.

At the top level the json object is split into two sections:
* 'config' - Details for connecting to graphite and zenoss, as well as the location of a state file for persisting information
* 'alarms' - An array containing each of the alarms that will be handled by the application.#

config
------

	"config": {
		"graphite": {
			"address": "http://ded3011",
			"port": 81,
			"username": "andrewm",
			"password": "Tgt86197"
		},
		"zenoss": {
			"address": "https://spider.sysms.net",
			"port": "9080",
			"username": "graphitezenossbridge",
			"password": "LKJliosjaifjw045erfd"
		},
		"statefile": "/home/andrewm/.graphitezenossstate",
	},

Each entry in the alarms array is a tuple containing the following values (If a default is given the value is not required):

* 'active' - "true" or "false" used to activate or deactivate the alarm (default: true)
* 'path' - The graphite metric path that is to be measured (required).
* 'type' - The kind of alarm to be measured, eg absolute value, rate of change, holt winters boundries, etc (required).
* 'component' - String that will be used to populate the component field of the zenoss alarm (required).
* 'event_class' - String that will be used to populate the event class field of the zenoss alarm (default: "/Status").
* 'summary' - String that is prefixes the current value and threshold in the summary field of the alarm (default: the metric path)
* 'description' - A plain text description value for reference only. Can be used to include helpful information for ops team.
* 'trigger_increments' - the number of times the metric must breach the threshold to activate the alarm (requrired).
* 'clear_increments' - the number of times the metric must be below the clear threshold for an alarm to be cleared (requrired).
* 'window' - [Only used for rate of change alarm] - the number of values to use for rate of change calculation.

Threshold Configuration
=======================

Thresholds can be configured as a default for the day or for a given time of day window. For example during known quiet periods low throughput alarms could have lower thresholds, or alarms could trigger earlier during working hours to catch problems early before the end of the day.
Severity comes in thresholds, 1-5 and clear. The metric must be above the threshold for that severity for the alarm to trigger, but below the clear threhsold for the alarm to clear (or vice versa if it is a min value alarm). EG:

* '"severity4": 15' - Trigger a severity 4 (error) alarm if the value goes above 15 for the required number of increments.
* '"severity5": 20' - Trigger a severity 5 (critical) alarm if the value goes above 20 for the required number of increments.
* '"clear": 10' - Clear alarm if the value goes below 10 for the required number of increments.

The values above describe the default thresholds that are used if there is no time specific threshold for that time of day. The alarm tuple can also contain an array called "times" which contains tuples each with the following values:

* '"start"' - Integer showing the time that this alarm period starts in HHMM 
* '"end"' - Integer showing the time that this alarm period end in HHMM 

Then the severity and clear thresholds are added as above. Those thresholds will then be used between the start and end times. The alarm manager makes no attempt to sanity check the times given, they can overlap and have gaps. When an alarm comes in it will check the values in the "times" array in order to see if the current time is after the start time and before the end time. If it gets to the last entry without a match then it uses the defaults, if there are no defaults then it stops processing that alarm.
