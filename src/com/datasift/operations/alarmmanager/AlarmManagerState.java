package com.datasift.operations.alarmmanager;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ArrayList;
import java.util.TimerTask;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Date;
import org.apache.log4j.Logger;
import java.util.concurrent.ConcurrentHashMap;

public class AlarmManagerState extends TimerTask {
    
    private static Logger logger = Logger.getLogger("AlarmManager.AlarmManagerState");
    public Integer httpport = null;
    private File statefile;
    private String graphite_address;
    private String graphite_username;
    private String graphite_password;
    private Integer graphite_port;
    private String zenoss_address;
    private String zenoss_username;
    private String zenoss_password;
    private Integer zenoss_port;
    private String pagerdutykey;
    private String pagerdutyurl;
    private String alarms_dir;

    private GraphiteInterface graphite;
    private ZenossInterface zenoss;
    private PagerDuty pagerduty;
    
    // Enabling test mode means alarms are not sent to zenoss, only printed, and all output is forwarded to stdout.
    private boolean testmode = false;
    
    
    /*
     * If graphite recieves a URI with more that 16000 characters it returns a 414 error, so we build up queries shorter than
     * this length and then store all of these queries in the graphitequeries array. (We don't every alarm as a seperate request to graphite for
     * obvious reasons"
     */
    

    private String graphitequery = "";
    private ArrayList graphitequeries = new ArrayList();
    
    
    // The various alarm objects, one for each configured alarm, are stored against their ID in this map.
    // The ID is a hash of the metric ID and the alarm type. It is included in the query and comes back with each result from graphite
    // it is then be used to match the result with the alarm that has to process it.
    HashMap<Integer, Alarm> AlarmsMap = new HashMap<Integer, Alarm>();
    
    // IncreasedThresholds keeps track of which alarms have had their thresholds temporarily increased. It holds the alarm ID which maps to a
    // Date object representing the time that the incresed threshold is due to expire.
    HashMap<Integer, Date> IncreasedThresholds = new HashMap<Integer, Date>();
    
    // IncreasedThresholdValues holds a copy of the increased threshold values purely so they can be saved to a file to allow threshold
    // changes to persist through a restart. The alarm itself holds the value that is actually used for calculation. (I know it breaks the
    // golden rule of duplicating data, but it makes the save and restore more elegant).
    HashMap<Integer, Double> IncreasedThresholdValues = new HashMap<Integer, Double>();
    
    // The three HashMaps below use a string for the alarm ID. This is because they use the expanded alarm ID which allows for multiple
    // alarms to be stored for each alarm in the config file. This occurs when a path holds a wildcard character and returns data for
    // multiple servers.
    
    // CurrentAlarms keeps track of any alarms currently triggered, so that they can be cleared or incremented as appropriate.
    ConcurrentHashMap<String, ZenossAlarmProperties> CurrentAlarms = new ConcurrentHashMap<String, ZenossAlarmProperties>();
    
    
    // LastAlarmSeverity stores the severity that each alarm triggered during the previous run. This information is used to either
    // increment or clear IncrementsCounter on the subsequent run.
    HashMap<String, Integer> LastAlarmSeverity = new HashMap<String, Integer>();
    
    // The EventClasses hashset contains all of the event classes that appear in the config. We build it up as the alarms are parsed then check
    // zenoss to make sure they all exist. Alarms sent on event classes that do not exist will be capped at info level. If this is the case for any alarms
    // an invalid config alarm is triggered.
    HashSet<String> EventClasses = new HashSet<String>();
    
    // The sendpageralert flag is set to false at the start of a run. it is set to true when when we get an error from zenoss. If it is true
    // at the end of a run then we send an alert to pagerduty that there was an error communicating with zenoss.
    private Boolean sendpageralert = false;
    

    
    @Override
    public void run(){
        
        // Set the pager duty flag to false. If it is set to true during the run then there was a problem reaching zenoss so we send an alert to pagerduty.
        sendpageralert = false;
        
        Thread.currentThread().setName("Metric_checker");
        logger.debug("============Starting new metrics run.===========");

        for (Object i:graphitequeries){
            try {
            this.checkalarms((String)i);
            } catch (Exception e) {
                ZenossAlarmProperties graphitealarm = new ZenossAlarmProperties(4,1000,"graphite","monitoring","/Status","GraphiteAlarmManager encountered an error processing metrics, see log file for details.", "Exception returned: " + e.getMessage(), "0");
                triggeralarm(graphitealarm);
                logger.error("Error processing alarms:", e);
            }
        } 

        logger.debug("=============Finished processing new metrics.==============");
        logger.debug("Currently active alarms:");
        try {
            logger.debug(ShowCurrentAlarms());
        } catch (Exception e) {
            logger.error("Problem parsing array of current alarms.", e);
        }
        logger.debug("=============Metrics run complete metrics.==============");
        
        try {
            ZenossAlarmProperties heartbeat = new ZenossAlarmProperties(2,1000,"graphite","monitoring","/Status","GraphiteAlarmManager heartbeat", "GraphiteAlarmManager heartbeat");
            triggeralarm(heartbeat);
        } catch (Exception e) {
            logger.error("Problem sending heartbeat to zenoss");
            sendpageralert = true;
        }
        
        if (sendpageralert) {
            try {
                logger.error("There was a problem reaching zenoss. Sending an alert to pagerduty");
                pagerdutyalert("Graphite alarm manager was unable to send an alert to zenoss");
            } catch (Exception e) {
                logger.error("There was also an error sending the page to pagerduty. Response:", e);
            }
        }

        
    }
    
    // Primary function of the constructor is to parse the config file as provided on the command line

  
    
    public AlarmManagerState(String configfile, Boolean _testmode) throws Exception {
        
            JSONParser parser = new JSONParser();
            JSONObject jsonconfig;
            testmode = _testmode;
            
            try {
                jsonconfig = (JSONObject)parser.parse(new FileReader(configfile));
                parse_config_json(jsonconfig);
            } catch (ParseException e) {
                logger.fatal("Cannot parse config file. Quitting.", e);
                throw e;
            }

            // the graphite object holds all details for connecting to graphite
            // and also handles all requests to the graphite REST API
            graphite = new GraphiteInterface(graphite_address, graphite_port, graphite_username, graphite_password);
            
            // the zenoss object holds all details for connecting to zenoss
            // and also handles all requests to the zenoss REST API
            // ZenossInterface will make a test connection, if it failes it is a fatal error unless testmode
            // is active, in which case just log the error.
            
            try {
                zenoss = new ZenossInterface(zenoss_address, zenoss_port, zenoss_username, zenoss_password);
            } catch (Exception e) {
                if (testmode) { 
                    logger.warn("Error creating zenoss connection. Continuing as AlarmManager is in test mode.");
                } else {
                    throw e;
                }
                
            }
            
            
            // pagerduty is used to send an alarm to the person on call if we cannot reach zenoss
            pagerduty = new PagerDuty(pagerdutyurl, pagerdutykey);
            
            File directory = new File(alarms_dir);
            if (!directory.isDirectory()) throw new Exception("Alarms directory in config is not a directory");
            if (directory.list().length == 0) throw new Exception("Alarms directory is empty");
            
            
            for (File currentfile : directory.listFiles()) {
                try {
                    parse_alarms_file(currentfile);
                } catch (ParseException e) {
                    ZenossAlarmProperties graphitealarm = new ZenossAlarmProperties(5,1000,"graphite","monitoring","/Status","GraphiteAlarmManager cannot parse alarms json file", "GraphiteAlarmManager cannot parse alarms json file", "0");
                    triggeralarm(graphitealarm);
                    logger.error("Cannot parse alarms file " + currentfile.getName() + ". Continuing with other files", e);
                }
            }
            
            try {
                for (String eventclass:EventClasses){
                    logger.info("Checking zenoss for event class " + eventclass);
                    if (!zenoss.checkEventClass(eventclass)){
                        ZenossAlarmProperties graphitealarm = new ZenossAlarmProperties(4,1000,"graphite","monitoring","/Status","Invalid event class found in alarmmanager config: " + eventclass, "The event class " + eventclass + "appears in the alarms file but does not exist in zenoss. Alerts on this event class will be limited to info level", "0");
                        triggeralarm(graphitealarm);
                        logger.error("Event class " + eventclass + " not found in zenoss. Alarms may not produce the correct severity.");

                    } else {
                        logger.info("success");
                    }
                }
            } catch (Exception e) {
                if (testmode) { 
                    logger.warn("Error checking event classes from zenoss. Running in testmode so continuing");
                } else {
                    throw e;
                }
            }

            logger.info("Total number of alarms processed: " + AlarmsMap.size());
            logger.debug(ShowAllAlarms());


            readstatefromfile();

            Iterator it = CurrentAlarms.keySet().iterator();

            while (it.hasNext()){
                String id = (String)it.next();
                Integer intkey = Integer.parseInt(id.split("_")[0]);
                if (!AlarmsMap.containsKey(intkey)){
                    logger.info("State file contains the following alarm, but it does not appear in the configuration. It will be removed from the state file.");
                    logger.info(CurrentAlarms.get(id));
                    it.remove();
                }
            }
            
        
    }
        
/*
 * The configuration for graphite and zenoss is stored in a json file, which is parsed by parse_config_json.
 * This file is seperate from the file which holds the alarms, this is so that if there is a failure parsing the alarms file
 * an appropriate alarm can be sent to zenoss.
 */
    
    private void parse_config_json (JSONObject JSONroot) throws Exception {
        JSONObject config = (JSONObject)JSONroot.get("config");
        JSONObject graphiteconf = (JSONObject)config.get("graphite");
        JSONObject zenossconf = (JSONObject)config.get("zenoss");
        JSONObject pargerdutyconf = (JSONObject)config.get("pagerduty");
        
        
        /* The alarm manager needs to know the current state of alarms to function properly. They are held in memory but also saved to
         * a file. This section loads them from that file on startup so that it does not loose track of alarms on restart.
         */
        String statefilename = "~/.graphitezenossstate";
        if ((String)config.get("statefile") != null) statefilename=(String)config.get("statefile");
        statefile = new File(statefilename);
        
        alarms_dir=(String)config.get("alarms_dir");
        if (alarms_dir == null || alarms_dir.equals("")) throw new Exception("No alarms directory given in config");
        
        
        try {
            if (config.get("httpport") != null) httpport = Integer.parseInt(config.get("httpport").toString());
        } catch (NumberFormatException e) {
            logger.error("Cannot parse HTTP port value, HTTP API will not be enabled.");
        }
        
        graphite_address = (String)graphiteconf.get("address");
        graphite_port = Integer.parseInt(graphiteconf.get("port").toString());
        graphite_username = (String)graphiteconf.get("username");
        graphite_password = (String)graphiteconf.get("password");


        zenoss_address = (String)zenossconf.get("address");
        zenoss_port = Integer.parseInt(zenossconf.get("port").toString());
        zenoss_username = (String)zenossconf.get("username");
        zenoss_password = (String)zenossconf.get("password");
        
        pagerdutyurl = (String)pargerdutyconf.get("url");
        pagerdutykey = (String)pargerdutyconf.get("servicekey");
    }
    
    private void parse_alarms_file (File file) throws Exception {
        
        int localalarmcounter = 0;
        
        logger.info("Parsing alarms in " + file.getName());
        JSONParser parser = new JSONParser();
        JSONObject jsonalarms = (JSONObject)parser.parse(new FileReader(file));
        JSONArray alarms = (JSONArray)jsonalarms.get("alarms");
        

        for (int temp = 0; temp < alarms.size(); temp++) {
            JSONObject thisalarm = (JSONObject)alarms.get(temp);
            String type = (String)thisalarm.get("type");
            Alarm tempalarm = new Alarm();
            try {
                if (type.equalsIgnoreCase("absolute")) tempalarm = new Absolute_Alarm(thisalarm);          
                else if (type.equalsIgnoreCase("ROC percent")) tempalarm = new ROC_pc_Alarm(thisalarm);
                else if (type.equalsIgnoreCase("ROC absolute") || type.equalsIgnoreCase("ROC")) tempalarm = new ROC_abs_Alarm(thisalarm);  
                else if (type.equalsIgnoreCase("baseline")) tempalarm = new Baseline_Alarm(thisalarm);
                else if (type.equalsIgnoreCase("holt winters")) tempalarm = new HoltWinters_Alarm(thisalarm);
                else {logger.error("unrecognised type " + type);}
                
                /*
                 * Each alarm is stored against its ID which is a unique hash of various values. This hash is included in the outgoing
                 * request to graphite, and is then returned with the results to match it with the alarm that has to process it.
                 * The alarm object creates its own ID hash, its own part of the search query, and analysis of its own result set.
                 */

                /*
                 * In order to minimise load on graphite the data for many alarms is retrieved at once by creating a large query URI
                 * with multiple search terms. However graphite will return a 414 error if the URI is larger thatn 16000 characters.
                 * The graphiteinterface class expands the URI slightly by converting some special characters to hex codes, so we limit here to 
                 * 12000. If adding the latest alarm will make the query larger than 12000 characters then the current string is stored in the
                 * "graphitequeries" list and a new graphitequery string is started.
                 */
                
                if (tempalarm.active){
                    AlarmsMap.put(tempalarm.ID,tempalarm);
                    localalarmcounter++;
                    if (!tempalarm.event_class.equals("/Status")) EventClasses.add(tempalarm.event_class);
                    if ((graphitequery.length() + tempalarm.searchquery.length()) > 5000) {
                        graphitequeries.add(graphitequery);
                        graphitequery = "";
                    }
                    graphitequery = graphitequery+tempalarm.searchquery; 
                                      

                }
                
            }
            catch (Exception e) {
                logger.error("failed to process alarm on \"" + thisalarm.get("path") + "\"", e);
            }
         }
        
        /* Add the current graphite query to the array of queries to send. By doing this here the queries sent to graphite should
         * roughly match the alarms files in the config directory, which should make reading the log a little easier.
         */
        graphitequeries.add(graphitequery);
        graphitequery = "";
        
        logger.info("Finished processing " + file.getName() + ". " + localalarmcounter + " alarms processed.");
    }
        
    
    /*
     * checkalarms performs the graphite check then parses over all of the incoming results.
     * Each result will have a unique alarm ID. The matching alarm is pulled from AlarmMap and used to process
     * that set of results.
     */
    
    public synchronized void checkalarms(String _query){
        
        
        String query = "/render?" + _query + "&from=-2h&format=json";
        logger.debug("Sending the following query to graphite:");
        logger.debug(query);
        JSONArray response;
        
        
        // Try and get data from graphite; trigger an alarm if it fails.
        try {
            response = graphite.getJson(query);
        } catch (org.apache.http.client.HttpResponseException e) {
            ZenossAlarmProperties graphitealarm = new ZenossAlarmProperties(5,1000,"graphite","monitoring","/Status","Graphite returned an error code to alarm manager: " + e.getMessage(), "See log file for full details", "0");
            triggeralarm(graphitealarm);
            return;
        } catch (Exception e) {
            ZenossAlarmProperties graphitealarm = new ZenossAlarmProperties(5,1000,"graphite","monitoring","/Status","GraphiteAlarmManager cannot parse data from graphite", "Exception returned: " + e.getMessage(), "0");
            triggeralarm(graphitealarm);
            return;
        }
        
        //Go through the stored map of alarms with an incresed threshold and clear the value for any with a time in the past.        

        Iterator it = IncreasedThresholds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            
            if ( ((Date)pairs.getValue()).before(new Date()) ) {
                try {
                    AlarmsMap.get((Integer)pairs.getKey()).set_offset(0.0);
                    logger.info("resetting threshold for alarm " + pairs.getKey());
                    IncreasedThresholdValues.remove((Integer)pairs.getKey());
                    it.remove();
                } catch (NullPointerException e) {
                    logger.warn("An alarm offset is present in the stored state file but the alarm does not exist. Removing offset");
                    IncreasedThresholdValues.remove((Integer)pairs.getKey());
                }
            }

        }

        /*
         * Graphite returns data in JSON format. Each metric in the query is send with an ID corresponding to its alarm, which graphite sends back
         * in the response.
         * In the returned data this ID is used to look up the alarm object in the AlarmsMap so it can be used to check the incoming data.
         * Some alarms which contain wildcards for hostname will have multiple data sets returned (one for each host) however the ID will be the same.
         */


        for (Object i:response){

            JSONObject dataset = (JSONObject)i;
            try {
                process_response_for_alarm(dataset);
            } catch (Exception e) {
                String target = ((String)dataset.get("target"));
                ZenossAlarmProperties graphitealarm = new ZenossAlarmProperties(4,1000,"graphite","monitoring","/Status","GraphiteAlarmManager encountered an error processing metric, see log file for details.", "Error occured on metric path: " + target + "Exception returned: " + e.getMessage(), "0");
                triggeralarm(graphitealarm);
                logger.error("Error processing alarms:", e);
            }
     
        }


        
        /*
         * AlarmManagerState must know the details of all current alarms to make sure that alarms are created, incremented and cleared correctly.
         * Thus after every run the results are saved to a file.
         */
        
        savestatetofile();
        
    }
    
    
    private void process_response_for_alarm(JSONObject dataset) throws Exception{
            
            String target = ((String)dataset.get("target"));
            logger.debug("response for " + target);
            logger.debug((JSONArray)dataset.get("datapoints"));
            Integer alarmID = Integer.parseInt(((String)dataset.get("target")).split("_", 2)[0]);
            Alarm currentalarm = (Alarm)AlarmsMap.get(alarmID);
            
            // some alarms have a link to a wiki page which can be added to the message. The string form of that link is created here
            
            String linkmessage = "";
            
            if (currentalarm.wikilink != null){
                linkmessage = "\r\n<br />Wiki page entry:\r\n<br /><a href='" + currentalarm.wikilink + "' target='_blank'>" + currentalarm.wikilink + "</a>";
            }

            String graphURL;
     
            
            Integer nonecount = nonevalues(dataset);
            if (((nonecount > 4) && (nonecount >= currentalarm.triggerincrements) && (nonecount > currentalarm.nodataoverride)) ) {
                if (currentalarm.substitute_component) graphURL = "https://graphite.sysms.net/render/?target=" + ((String)dataset.get("target")).split("_", 2)[1] + "&height=300&width=500&from=-2hours";
                else graphURL = "https://graphite.sysms.net/render/?target=" + currentalarm.path + "&height=300&width=500&from=-2hours";
                ZenossAlarmProperties nodataalarm = new ZenossAlarmProperties(currentalarm.nodataseverity,currentalarm.prodState,"Graphite",currentalarm.getcomponent(target),currentalarm.event_class,currentalarm.name + " returned too many \'none\' values: " + nonecount,target, true);
                nodataalarm.sourcealarmID=alarmID;
                nodataalarm.message=nodataalarm.message + " <img src='" + graphURL + "' />";
                nodataalarm.message=nodataalarm.message + "\r\n<br /><a href='" + graphURL + "' target='_blank'>" + graphURL + "</a>";
                nodataalarm.message=nodataalarm.message + linkmessage;
                triggeralarm(nodataalarm);
            }
            else {
                if ((CurrentAlarms.get(target) != null) && (CurrentAlarms.get(target).nodataalarm)) clearalarm(new ZenossAlarmProperties(currentalarm.nodataseverity,currentalarm.prodState,"Graphite",currentalarm.component,currentalarm.event_class,"",target));


                // The alarm object creates a zap object that holds all of the results, even if the alarm is clear.
                ZenossAlarmProperties zap = ((Alarm)AlarmsMap.get(alarmID)).processalarm(dataset);

                
                Double threshold = 0.0;
                // Find the threshold for the current severity level and add to graph. If the threshold is -1 or 0 then the alarm is clear or not triggered
                // in this case the threshold is not needed.
                if (zap.severity >= 1){
                    try { threshold = currentalarm.getthresholdsfortime((JSONArray)dataset.get("datapoints"))[zap.severity]; }
                    catch (Exception e) { logger.error("Cannot retrieve threshold for alarm " + alarmID, e); }
                }

    
                String graphpath = currentalarm.path.replaceAll("\\$\\$", "*");
                graphURL = "https://graphite.sysms.net/render/?target=" + graphpath + "&target=alias(threshold(" + String.format("%.0f", threshold) + "),\"Threshold\")&height=300&width=500&from=-2hours";
                zap.message=zap.message + "<br> <img src='" + graphURL + "' />";
                zap.message=zap.message + "\r\n<br /><a href='" + graphURL + "' target='_blank'>" + graphURL + "</a>";
                zap.message=zap.message + linkmessage;
                int alarmlevel = zap.severity;
                
                logger.info(zap.toString());


                /* If the level is 0 (clear)then check if an alarm has been triggered. If it has, and enough clear values have
                 * come in, then clear the alarm.
                 */
                
                /* if an alarm level has been breached and there is already an alarm out for at that level then re-send to
                 * zenoss to increment it.
                 * If the alarm is a different level then re-issue at the new level by clearing the alarm and issuing a new one.
                 */
                
                if ((zap.severity == 0 && alarm_already_triggered(zap.ID))){
                    clearalarm(zap);
                } else if (zap.severity >= 1) {
                    if (alarm_already_triggered(zap.ID) && !((CurrentAlarms.get(zap.ID)).severity == alarmlevel)) clearalarm(zap);
                    triggeralarm(zap);
                }

                // -1 means no threshold was triggered but the clear threshold was also no met, which means do nothing. 
                
            }
    }
    
    
    private boolean alarm_already_triggered(String AlarmID){

           if ((CurrentAlarms.get(AlarmID) == null) || (CurrentAlarms.get(AlarmID).severity == 0)) return false;
           else return true;

    }
    
    // Clears the alarm in zenoss as well as clearing the current alarms and the increments counter

    private void clearalarm(ZenossAlarmProperties zap){
        CurrentAlarms.remove(zap.ID);
        try {
            if (testmode) {
                logger.info("Clearing alarm on " + zap.device + " component: " + zap.component + " event class " + zap.eventclass + " ID: " + zap.ID );
            }
            else {
                logger.info("Clearing alarm on " + zap.device + " component: " + zap.component + " event class " + zap.eventclass + " ID: " + zap.ID );
                zenoss.closeEvent(zap);
            }
        } catch (Exception e){
            logger.error("Error clearing alarm " + zap.ID, e);
        }

    }
        
    private void triggeralarm(ZenossAlarmProperties zap){
        try {
            if (testmode) {
                System.out.println("triggering alarm severity: " + zap.severity + " device: " + zap.device + " component: " + zap.component + " summary: " + zap.summary);
            }
            else {
                logger.info("triggering alarm severity: " + zap.severity + " device: " + zap.device + " component: " + zap.component + " summary: " + zap.summary);
                zenoss.createEvent(zap);
            }
            if (!zap.ID.equals("0")) CurrentAlarms.put(zap.ID, zap);
        } catch (Exception e) {
            logger.error("Problem sending event to Zenoss for alarm on " + zap.ID, e);
            sendpageralert = true;
        }
        
    }
    
    
    private void savestatetofile(){

        try {
            FileOutputStream f = new FileOutputStream(statefile);  
            ObjectOutputStream s = new ObjectOutputStream(f);          
            s.writeObject(CurrentAlarms);
            s.writeObject(IncreasedThresholds);
            s.writeObject(IncreasedThresholdValues);
            s.writeObject(LastAlarmSeverity);
            s.flush();
            s.close();
            f.close();
        } catch (FileNotFoundException e) {
            logger.error("Cannot open state file " + statefile.getAbsolutePath() + " alarm state will not be saved.", e);
        } catch (IOException e){
            logger.error("Error writing to file " + statefile.getAbsolutePath() + " alarm state will not be saved.", e);
        }
    }
    
    
    
    private void readstatefromfile(){ 
        try {
        FileInputStream f = new FileInputStream(statefile);  
        ObjectInputStream s = new ObjectInputStream(f);  
        CurrentAlarms = (ConcurrentHashMap<String,ZenossAlarmProperties>)s.readObject();    
        IncreasedThresholds = (HashMap<Integer, Date>)s.readObject(); 
        IncreasedThresholdValues = (HashMap<Integer, Double>)s.readObject();
        LastAlarmSeverity = (HashMap<String, Integer>)s.readObject(); 
        s.close();
        f.close();

        /*
         * Load the increased thresholds that were saved in the file back into the relevant alarms.
         * If a threshold increase has expired while the system was shut down then this will be picked up
         * on the first run and cleared based on the expiry time in "IncreasedThresholds".
         */
        for (Integer key:IncreasedThresholdValues.keySet()) { 
            try {
               AlarmsMap.get(key).set_offset(IncreasedThresholdValues.get(key));
            } catch (NullPointerException e) {
               logger.error("Error setting multiplier for alarm " + key + ". Alarm not found");
            }
        }
        
        } catch (FileNotFoundException e) {
            logger.warn("State file" + statefile.getAbsolutePath() + " does not exist. Previous alarm state cannot be read.");
        } catch (IOException e){
            logger.warn("Error reading from file " + statefile.getAbsolutePath() + " previous state cannot be read.", e);
        } catch (ClassNotFoundException e){
            logger.warn("Error parsing data in " + statefile.getAbsolutePath() + " previous state cannot be read.", e);
        }
    }
    
    public synchronized String ShowAllAlarms(){
        
        String s = "{\"alarms\": [";
                
        Iterator it = AlarmsMap.keySet().iterator();
        while (it.hasNext()) {
            s = s + "\n" + (AlarmsMap.get((Integer)it.next()).toString());
            if (it.hasNext()) s = s + ",";
        }

        s = s + "\n]\n}";
        return s;

    }
    
    public synchronized String ShowIncreasedThresholdAlarms(){
        
        String s = "{\"alarms\": [";
                
        Iterator it = IncreasedThresholds.keySet().iterator();
        while (it.hasNext()) {
            s = s + "\n" + (AlarmsMap.get((Integer)it.next()).toString());
            if (it.hasNext()) s = s + ",";
        }

        s = s + "\n]\n}";
        return s;

    }

    
    public synchronized String ShowCurrentAlarms(){
        String s = "{\"triggered_alarms\": [";
        
        Iterator it = CurrentAlarms.values().iterator();
        
        while (it.hasNext()){
            ZenossAlarmProperties zap = (ZenossAlarmProperties)it.next();
            if (zap.severity != 0) {
                s = s + "\n" + (zap.toString());
                if (it.hasNext()) s = s + ",";
            }
        }
        s = s + "\n]\n}";
        return s;
    }
    
    
    public synchronized String IncreaseThreshold(int AlarmID, Double offset, int minutes){
        String response;
        
      
        try {
            AlarmsMap.get(AlarmID).set_offset(offset);
            
            if (offset == 0.0) {
                if (IncreasedThresholds.containsKey(AlarmID)) IncreasedThresholds.remove(AlarmID);
                if (IncreasedThresholdValues.containsKey(AlarmID)) IncreasedThresholdValues.remove(AlarmID);
                                response = "{\"success\": true, \"response\": \"Offset for alarm " + AlarmID + " cleared\"}";
            } else {
                Date expiry = new Date(new Date().getTime() + (minutes * 60000));
                IncreasedThresholds.put(AlarmID, expiry);
                IncreasedThresholdValues.put(AlarmID, offset);
                response = "{\"success\": true, \"response\": \"Offset for alarm " + AlarmID + " set to " + offset + " for " + minutes + " minutes.\"}";
            }

        } catch (NullPointerException e) {
            response = "{\"success\": false, \"response\": \"Offset setting for alarm " + AlarmID + ". Alarm not found\"}";
            logger.error(response);
        }
        savestatetofile();
        logger.info(response);
        return response;
    }
    
    
    public Integer nonevalues(JSONObject dataset){
        JSONArray datapoints = (JSONArray)dataset.get("datapoints");

        /*
         * Count back from the last value how many entries are null. The method actually starts at the penultimate value.
         * When calculating alerts the alarm classes disregard the last value as they very often contain incomplete metrics, so
         * this method must do the same for consistancy.
         */
        
        Integer count = 0;
        while (count<datapoints.size()-1){
            JSONArray currentdatapoint = (JSONArray)datapoints.get(datapoints.size()-count-2);
            if (currentdatapoint.get(0) != null) break;
            else count++;
        }
                
        return count;
    }
    
    private void pagerdutyalert(String message){
        if (!testmode) {
            try {
                JSONObject response = pagerduty.sendEvent(message);
                logger.info("Alert sent to pagerduty");
                logger.info(response.toJSONString());
            }
            catch (Exception e) {
                logger.error("Unable to send event to pagerduty", e);
            }
        }
    }
    
}
