package com.datasift.operations.alarmmanager;
import java.io.FileReader;
import java.util.HashMap;
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


public class AlarmManagerState extends TimerTask {
    
    File statefile;
    String graphite_address;
    String graphite_username;
    String graphite_password;
    Integer graphite_port;
    String zenoss_address;
    String zenoss_username;
    String zenoss_password;
    Integer zenoss_port;
    String graphitequery = "";
    GraphiteInterface graphite;
    ZenossInterface zenoss;
    // The various alarm objects, one for each configured alarm, are stored against their ID in this map.
    // The ID is a hash of the metric ID and the alarm type. It is included in the query and comes back with each result from graphite
    // it is then be used to match the result with the alarm that has to process it.
    HashMap<Integer, Alarm> AlarmsMap = new HashMap<Integer, Alarm>();
    
    // IncreasedThresholds keeps track of which alarms have had their thresholds temporarily increased. It holds the alarm ID which maps to a
    // Date object representing the time that the incresed threshold is due to expire.
    HashMap<Integer, Date> IncreasedThresholds = new HashMap<Integer, Date>();
    
    // The three HashMaps below use a string for the alarm ID. This is because they use the expanded alarm ID which allows for multiple
    // alarms to be stored for each alarm in the config file. This occurs when a path holds a wildcard character and returns data for
    // multiple servers.
    
    // CurrentAlarms keeps track of any alarms currently triggered, so that they can be cleared or incremented as appropriate.
    HashMap<String, Integer> CurrentAlarms = new HashMap<String, Integer>();
    
    // IncrementsCounter keeps track of how many times an alarm has reported an above threshold value, so the alarm is triggered
    // when the correct number of results has been passed.
    HashMap<String, Integer> IncrementsCounter = new HashMap<String, Integer>();
    
    // LastAlarmSeverity stores the severity that each alarm triggered during the previous run. This information is used to either
    // increment or clear IncrementsCounter on the subsequent run.
    HashMap<String, Integer> LastAlarmSeverity = new HashMap<String, Integer>();
    

    
    @Override
    public void run(){
        this.checkalarms();
    }
    
    // Primary function of the constructor is to parse the config file as provided on the command line

    
    public AlarmManagerState(String configfile) throws Exception {
        
            JSONParser parser = new JSONParser();
            JSONObject jsonconfig;
            
            try {
                jsonconfig = (JSONObject)parser.parse(new FileReader(configfile));
                parse_json(jsonconfig);
            } catch (ParseException e) {
                Logger.writeerror("Cannot parse config file. Quitting.", e);
            }

            // the graphite object holds all details for connecting to graphite
            // and also handles all requests to the graphite REST API
            graphite = new GraphiteInterface(graphite_address, graphite_port, graphite_username, graphite_password);
            
            // the zenoss object holds all details for connecting to zenoss
            // and also handles all requests to the zenoss REST API
            zenoss = new ZenossInterface(zenoss_address, zenoss_port, zenoss_username, zenoss_password);
        
    }
    
    /*
     * The config input file is in json format.
     * parse_json reads in the graphite and zenoss details then
     * loops through the alarms config and creates each of the alarm objects
     * to store them in the alarms hashmap.
     * It also builds up the graphite query which is stored as a string.
     */
    
    private void parse_json (JSONObject JSONroot){
        JSONObject config = (JSONObject)JSONroot.get("config");
        JSONObject graphiteconf = (JSONObject)config.get("graphite");
        JSONObject zenossconf = (JSONObject)config.get("zenoss");
        JSONArray alarms = (JSONArray)JSONroot.get("alarms");
        
        
        /* The alarm manager needs to know the current state of alarms to function properly. They are held in memory but also saved to
         * a file. This section loads them from that file on startup so that it does not loose track of alarms on restart.
         */
        String statefilename = "~/.graphitezenossstate";
        if ((String)config.get("statefile") != null) statefilename=(String)config.get("statefile");
        statefile = new File(statefilename);
        readalarmsfromfile();
       
        
        graphite_address = (String)graphiteconf.get("address");
        graphite_port = Integer.parseInt(graphiteconf.get("port").toString());
        graphite_username = (String)graphiteconf.get("username");
        graphite_password = (String)graphiteconf.get("password");

        
        
        
        zenoss_address = (String)zenossconf.get("address");
        zenoss_port = Integer.parseInt(zenossconf.get("port").toString());
        zenoss_username = (String)zenossconf.get("username");
        zenoss_password = (String)zenossconf.get("password");


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
                else {Logger.writeline("unrecognised type " + type);}
                
                /*
                 * Each alarm is stored against its ID which is a unique hash of various values. This hash is included in the outgoing
                 * request to graphite, and is then returned with the results to match it with the alarm that has to process it.
                 * The alarm object creates its own ID hash, its own part of the search query, and analysis of its own result set.
                 */

                
                if (tempalarm.active){
                    AlarmsMap.put(tempalarm.ID,tempalarm);
                    graphitequery = graphitequery+tempalarm.searchquery; 
                                      
                   /*
                   * The graphite query for all data is made as a single http get. The search query is built
                   * up at the same time as the alarms hashmap and stored as the graphitequery string.
                   */
                }
                
            }
            catch (Exception e) {
                Logger.writeerror("failed to process alarm on \"" + thisalarm.get("path") + "\"", e);
            }
         }
        
        Logger.writeline("Number of alarms processed: " + AlarmsMap.size());

    

    }
    
    /*
     * checkalarms performs the graphite check then parses over all of the incoming results.
     * Each result will have a unique alarm ID. The matching alarm is pulled from AlarmMap and used to process
     * that set of results.
     */

    public synchronized void checkalarms(){
        String query = "/render?" + graphitequery + "&from=-360s&format=json";
        JSONArray response = new JSONArray();
        
        // Try and get data from graphite; trigger an alarm if it fails.
        try {
            response = graphite.getJson(query);
        } catch (Exception e) {
            ZenossAlarmProperties graphitealarm = new ZenossAlarmProperties(2,1000,"graphitezenoss","monitoring","/Status","GraphiteZenossBridge cannot receive data from graphite", "Exception returned: " + e.getMessage(), "no_data");
            triggeralarm(graphitealarm);
            return;
        }

        
        //Go through the stored map of alarms with an incresed threshold and clear the value for any with a time in the past.

        for (Integer i:IncreasedThresholds.keySet()){
            if ( ((Date)IncreasedThresholds.get(i)).before(new Date()) ) {
                AlarmsMap.get(i).set_multiplier(1.0);
                IncreasedThresholds.remove(i);
                Logger.writeline("resetting threshold for alarm " + i);
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
            Integer alarmID = Integer.parseInt(((String)dataset.get("target")).split("_")[0]);
            
            // The alarm object creates a zap object that holds all of the results, even if the alarm is clear.
            ZenossAlarmProperties zap = ((Alarm)AlarmsMap.get(alarmID)).checkalarm(dataset);
            
            String graphURL = "https://graphite.sysms.net/render/?target=" + ((String)dataset.get("target")).split("_")[1] + "&height=300&width=500&from=-2hours";
            zap.message=zap.message + " <img src='" + graphURL + "' />";
            zap.message=zap.message + "\r\n<br /><a href='" + graphURL + "' target='_blank'>" + graphURL + "</a>";
            int alarmlevel = zap.severity;
            

            int lastseverity = getlastseverity(zap.ID);
            if (lastseverity == zap.severity) incrementcounterforID(zap.ID);
            else IncrementsCounter.put(zap.ID, 1);
            int currentcounter = getcountervalue(zap.ID);
            LastAlarmSeverity.put(zap.ID, zap.severity);
            
            
            /* If the level is 0 (clear)then check if an alarm has been triggered. If it has, and enough clear values have
             * come in, then clear the alarm.
             */

            // -1 means no threshold was triggered but the clear threshold was also no met, which means do nothing. 
            
            if (alarmlevel == 0 && (alarm_already_triggered(zap.ID)) && (currentcounter >= ((Alarm)AlarmsMap.get(alarmID)).clearincrements)){
                clearalarm(zap);
            } else if (alarmlevel > 0) {
                if (alarm_already_triggered(zap.ID)){
                    
                    /* if an alarm level has been breached and there is already an alarm out for at that level then re-send to
                     * zenoss to increment it.
                     * If the alarm is a different level then re-issue at the new level by clearing the alarm and issuing a new one.
                     * This will happen immediately, without waiting for the increments threshold to be reached. This is because 
                     */
                    if (CurrentAlarms.get(zap.ID) == alarmlevel){
                        triggeralarm(zap);
                    } else {
                            System.out.println("new alarm threshold - clearing old alarm");
                            clearalarm(zap);
                            System.out.println("new alarm threshold - triggering new alarm");
                            triggeralarm(zap);
                    }
                    
                /* if an alarm level is breached and there is not already an alarm out then increment the counter and, if it reaches
                 * enough values, trigger the alarm.
                 */
                } else {
                    if (currentcounter >= ((Alarm)AlarmsMap.get(alarmID)).triggerincrements) {
                        triggeralarm(zap);
                    }
                }
            }
            
            System.out.println("Counter level" + IncrementsCounter.get(zap.ID));
        }
        
        /*
         * AlarmManagerState must know the details of all current alarms to make sure that alarms are created, incremented and cleared correctly.
         * Thus after every run the results are saved to a file.
         */
        
        savealarmstofile();
        
    }
    
    private boolean alarm_already_triggered(String AlarmID){

           if ((CurrentAlarms.get(AlarmID) == null) || (CurrentAlarms.get(AlarmID) == 0)) return false;
           else return true;

    }
    
    // Clears the alarm in zenoss as well as clearing the current alarms and the increments counter

    private void clearalarm(ZenossAlarmProperties zap){
        System.out.println("clearing alarm");
        CurrentAlarms.remove(zap.ID);
        try {
            zenoss.closeEvent(zap);
            IncrementsCounter.put(zap.ID, 0);
        } catch (Exception e){
            Logger.writeerror("Error clearing alarm " + zap.ID, e);
        }

    }
        
    private void triggeralarm(ZenossAlarmProperties zap){
        try {
            zenoss.createEvent(zap);
            CurrentAlarms.put(zap.ID, zap.severity);
        } catch (Exception e) {
            Logger.writeerror("Problem sending event to Zenoss for alarm on " + zap.ID, e);
        }
        System.out.println("triggering alarm severity: " + zap.severity + " device: " + zap.device + " component: " + zap.component);
    }
    
    
    private void incrementcounterforID(String ID){
        Integer current = IncrementsCounter.get(ID);
        if (current == null) current = 0;
        IncrementsCounter.put(ID, current + 1);
    }
    
    private Integer getcountervalue(String ID){
        Integer last = IncrementsCounter.get(ID);
        if (last == null) return 0;
        return last;
    }
    
    private Integer getlastseverity(String ID){
        Integer last = LastAlarmSeverity.get(ID);
        if (last == null) return 0;
        return last;
    }
    
    private void savealarmstofile(){
        try {
            FileOutputStream f = new FileOutputStream(statefile);  
            ObjectOutputStream s = new ObjectOutputStream(f);          
            s.writeObject(CurrentAlarms);
            s.flush();
            s.close();
            f.close();
        } catch (FileNotFoundException e) {
            Logger.writeerror("Cannot open state file " + statefile.getAbsolutePath() + " alarm state will not be saved.", e);
        } catch (IOException e){
            Logger.writeerror("Error writing to file " + statefile.getAbsolutePath() + " alarm state will not be saved.", e);
        }
    }
    
    
    
    private void readalarmsfromfile(){ 
        try {
        FileInputStream f = new FileInputStream(statefile);  
        ObjectInputStream s = new ObjectInputStream(f);  
        CurrentAlarms = (HashMap<String,Integer>)s.readObject();         
        s.close();
        f.close();
        } catch (FileNotFoundException e) {
            Logger.writeline("State file" + statefile.getAbsolutePath() + " does not exist. Previous alarm state cannot be read.");
        } catch (IOException e){
            Logger.writeerror("Error reading from file" + statefile.getAbsolutePath() + " previous state cannot be read.", e);
        } catch (ClassNotFoundException e){
            Logger.writeerror("Error parsing data in " + statefile.getAbsolutePath() + " previous state cannot be read.", e);
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
        
                
        Iterator it = CurrentAlarms.keySet().iterator();
        while (it.hasNext()) {
            Integer key2 = Integer.parseInt(((String)it.next()).split("_")[0]);
            s = s + "\n" + (AlarmsMap.get(key2).toString());
            if (it.hasNext()) s = s + ",";
        }

        s = s + "\n]\n}";
        return s;
    }
    
    
    public synchronized String IncreaseThreshold(int AlarmID, Double multiplier, int minutes){
        String response = "";
        try {
            AlarmsMap.get(AlarmID).set_multiplier(multiplier);
            Date expiry = new Date(new Date().getTime() + (minutes * 60000));
            IncreasedThresholds.put(AlarmID, expiry);
            response = "Multiplier for alarm " + AlarmID + " set to " + multiplier + " for " + minutes + " minutes.";
        } catch (NullPointerException e) {
            response = "Error setting multiplier for alarm " + AlarmID + ". Alarm not found";
            Logger.writeerror(response);
        }
        return response;
    }
    
}
