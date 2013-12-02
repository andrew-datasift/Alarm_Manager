package com.datasift.operations.alarmmanager;

import org.json.simple.JSONObject;
import java.util.ArrayList;
import org.json.simple.JSONArray;
import org.apache.log4j.Logger;

/*
 * Alarm is the parent class to all of the individual alarm type objects.
 * It contains methods to create and access all of the common values, such as the graphite search string and description.
 * Checking the current values returned by graphite is handled by the individial instances.
 */


public class Alarm {
    private static Logger logger = Logger.getLogger("AlarmManager.Alarm");
    // These are all of the attribures which are defined in the config file for the alarm
    String name;
    String type;
    String description;
    String summary;
    String component;
    String event_class = "/Status";
    Integer prodState = 1000;
    Boolean greater_than;
    String path;
    Integer triggerincrements;
    Integer clearincrements;
    Boolean active;
    
    // nodataseverity is the severity of the alarm that is triggered if there is no data from graphite.
    // It will be set to the highest avaliable severity for this alarm. Default to 3.
    Integer nodataseverity = 3;
    
    // Search query is the graphite search query generated from the path.
    String searchquery;
    
    // ID is a unique ID for this alarm which is a hash generated from the values above.
    Integer ID;
    
    // Threshold offset is used when the alarm manager has been configured to temporarily adjust the threshold
    // of an alarm for a specified time. It can be positive or negative and is added to the threshold when the alarm is called..
    Double threshold_offset = 0.0;
    Boolean substitute_component=false;
    
    // timesList holds all of the time specific thresholds, if any.
    ArrayList<AlarmTime> timesList = new ArrayList<AlarmTime>();
    
    // The thresholds array holds the default (non-time specific) thresholds, if any.
    Double[] thresholds = new Double[6];

    /*
     * The default constructor which creates an empty alarm with no values set to inactive.
     * Should not be used; the invividual child classes should always be used instead.
     */
    
    public Alarm(){
        active = false;
    }
   
    /*
     * A number of elements are common to all types of alarm. GetCommonElements parses the JSON object from the config file.
     * path - the graphite metric path
     * component - the name of the component to alarm on if thresholds are breached
     * summary - the string to prefix the summary field, this is suffixed with the current value
     * triggerincrements - the number of occurances above the threshold required to trigger an alarm
     * clearincrements - the number of occurances below the threshold required to clear and alarm
     * ID - A unique internal ID generated from the path, component and event class
     * timeList - contains all of the AlarmTime objects that contain thresholds for given time periods
     * threholds - contains the default thresholds that are used when the current time does not match any of the times in "timeList"
     * threshold_type - this can be max (alarm if above threshold) or min (alarm if below threshold). What constitutes an above or below
     *                  threshold reading is handled by the individual alarm instances (rate of change, holt winters deviation, etc)
     */
    

    
    public void GetCommonElements (JSONObject alarmconfig) throws Exception{

        if (alarmconfig.get("path") == null) throw new Exception("\"Path\" value cannot be empty");
        if (alarmconfig.get("name") == null) throw new Exception("\"Name\" value cannot be empty");
        path=(String)alarmconfig.get("path");
        name=(String)alarmconfig.get("name");
        component=(String)alarmconfig.get("component");
        type = (String)alarmconfig.get("type");
        
        if (alarmconfig.get("component") == null) component = name;
                else component=(String)alarmconfig.get("component");
        
        if (alarmconfig.get("summary") == null) summary = name;
                else summary=(String)alarmconfig.get("summary");
        
        description=(String)alarmconfig.get("description");
        triggerincrements=Integer.parseInt(alarmconfig.get("trigger_increments").toString());
        clearincrements=Integer.parseInt(alarmconfig.get("clear_increments").toString());
        
        if (alarmconfig.get("event_class") != null) event_class=(String)alarmconfig.get("event_class");
        
        
        String thresholdtype = (String)alarmconfig.get("threshold_type");
        if (thresholdtype == null) 
            { greater_than = true; }
        else {
            if (thresholdtype.equalsIgnoreCase("max"))
                { greater_than = true; }
            else if ( thresholdtype.equalsIgnoreCase("min") )
                { greater_than = false; }
            else {
                logger.error("Error parsing alarm for " + path + "value for \"threshold_type\" could not be read.");
                throw new Exception("Unacceptable value for \"threshold_type\" in alarm on " + path); 
            }
        }
        
        ID = (path+component+event_class+(String)alarmconfig.get("type")+greater_than.toString() ).hashCode();
        
        
        /*
         * The alarm manager can have wildcards in the hostnames. EG "devices.servers.hdp$$.system.uptime" will monitor all the HDP nodes.
         * the "&&" is turned into a "*" for the graphite search. We do not use "*" in the config because this character needs to be used
         * for summary metrics that return a single value.
         * If && is found the "substitute_hostname" flag is set so the alarm can look for multiple returned values.
         */
        
        if (path.contains("$$")){
            searchquery="&target=aliasSub(" + path.replaceAll("\\$\\$", "*") + ",\"^\",\"" + ID + "_\")";
            substitute_component=true;
        } else {
            searchquery="&target=aliasSub(" + path + ",\"^\",\"" + ID + "_\")";
        }
        
        
        /*
         * the thresholds array holds the default thresholds that are only used if no time-specific thresholds apply
         * to the current time.
         */
        
        for (int i=0; i<6; i++){
            thresholds[i] = null;
        }
        
        if (alarmconfig.get("prodstate") != null) prodState=Integer.parseInt(alarmconfig.get("prodstate").toString());
        
        if (alarmconfig.get("severity5") != null) thresholds[5]=Double.parseDouble(alarmconfig.get("severity5").toString());
        if (alarmconfig.get("severity4") != null) thresholds[4]=Double.parseDouble(alarmconfig.get("severity4").toString());
        if (alarmconfig.get("severity3") != null) thresholds[3]=Double.parseDouble(alarmconfig.get("severity3").toString());
        if (alarmconfig.get("severity2") != null) thresholds[2]=Double.parseDouble(alarmconfig.get("severity2").toString());
        if (alarmconfig.get("severity1") != null) thresholds[1]=Double.parseDouble(alarmconfig.get("severity1").toString());
        if (alarmconfig.get("clear") != null) thresholds[0]=Double.parseDouble(alarmconfig.get("clear").toString());

        
        /*
         * If no clear threshold is explicitly given then take the lowest given threshold as the clear.
         */
        
        if (thresholds[0] == null){
           for (int i=1; i<=5; i++){
           if ( thresholds[i] != null) {
               thresholds[0] = thresholds[i];
               break;
           }
         }
        }
        
        /*
         * Find the highest threshold for this alarm and use that as the nodata alarm
         */
        
        for (int i=5; i>0; i--){
           if ( thresholds[i] != null) {
               nodataseverity = i;
               break;
           }
        }
           
           
        try { triggerincrements=Integer.parseInt(alarmconfig.get("trigger_increments").toString()); }
        catch (NumberFormatException e)
            {
            logger.error("Error parsing alarm for " + path + "value for trigger_increments could not be read.", e);
            throw new Exception("Unacceptable value for \"trigger_increments\" in alarm on " + path);
            }
        
        try { clearincrements=Integer.parseInt(alarmconfig.get("clear_increments").toString()); }
        catch (NumberFormatException e)
            { 
            logger.error("Error parsing alarm for " + path + "value for trigger_increments could not be read.", e);
            throw new Exception("Unacceptable value for \"clear_increments\" in alarm on " + path);
            }
        
        
            

        
        Object _active = alarmconfig.get("active");
        if (_active == null) active = true;
        else if (_active instanceof Boolean ) active = (Boolean)_active;
        else if (_active instanceof String) active = Boolean.parseBoolean((String)_active);
       
        JSONArray times = (JSONArray)alarmconfig.get("times");
        if (times != null && times.size() != 0)
        {
        
            for (int temp = 0; temp < times.size(); temp++) {
            
            try{ 
                AlarmTime at = new AlarmTime((JSONObject)times.get(temp)); 
                timesList.add(at);
                }
            catch (Exception e) {
                logger.error("Unacceptable value in time field for alarm on " + path, e);
                throw new Exception("Unacceptable value in time field for alarm on " + path);
                }
            }
        }
        
        
        if (timesList.isEmpty() &&  thresholds[1] == null &&  thresholds[2] == null &&  thresholds[3] == null &&  thresholds[4] == null &&  thresholds[5] == null){
            logger.error("No default thresholds or time specific thresholds provided for alarm " + path);
            throw new Exception("No default thresholds or time specific thresholds provided for alarm " + path);
        }


    }
    
    /*
     * processresponse takes a dataset from graphite (as returned by the graphite JSON REST interface).
     * returns a ZenossAlarmProperties object which contains all the information ZenossInterface needs to raise or clear an alarm.
     * This method always returns a clear (level 0), and should not be used. It is overridden in each of the child classes.
     */
    
    
    public ZenossAlarmProperties processresponse(JSONObject dataset){
        JSONArray datapoints = (JSONArray)dataset.get("datapoints");
        return new ZenossAlarmProperties(0, prodState, "", "", "", "",ID.toString());
    }
    
    /*
     * checkalarm is a wrapper method. This will be called in order to generate an alarm from a set of graphite data.
     * This method will then perform any standard functions, such as checking the validity of the dataset, then call the
     * processresponse method which will be unique for each alarm instance.
     * 
     */
    
    public ZenossAlarmProperties checkalarm(JSONObject dataset){
     
        String uniquecomponent = getcomponent((String)dataset.get("target"));
        String uniqueID = ID.toString() + "_" + uniquecomponent;
        if (checkfornodata(dataset)) return new ZenossAlarmProperties(3,prodState,"Graphite",uniquecomponent,event_class,summary + " returned too many none values",uniqueID.toString());
        return processresponse(dataset);
    }

    /*
     * getcurrentseveritylevel is given a set of datapoints as returned by graphite. It returns the current alarm threshold for that
     * datapoint at that time. It takes no account of the number of repeat values needed to cause an alarm; that is handled elsewhere.
     * 
     * The value is multiplied (or divided in the case of a lower-than alarm) by the threshold_multipler. This is usually 1, but can be set to a higher
     * value in order to temporarily increase an alarms threshold.
     * 
     * If an alarm is not triggered but is above the clear threshold then -1 will be returned.
     */
    
    public Integer getcurrentseveritylevel(JSONArray datapoints, Double latestmeasurement){
        Double[] localthresholds = getthresholdsfortime(datapoints);
        for (int i=5; i>0; i--){
           if (greater_than) {
               if ( (localthresholds[i] != null) && (latestmeasurement > (localthresholds[i] + threshold_offset)) ) return i;
           } else {
               if ( (localthresholds[i] != null) && (latestmeasurement < (localthresholds[i] - threshold_offset)) ) return i;
           }
           
        }
        
        if (greater_than) {
            if ( latestmeasurement <= (localthresholds[0] + threshold_offset)) return 0;
        } else {
            if ( latestmeasurement >= (localthresholds[0] + threshold_offset)) return 0;
        }
        

        
        return -1;
        
    }
    
    /*
     * Returns the thresholds for the AlarmTime object in timesList that matches the current time. If no value matches it returns
     * the default thresholds array.
     */
    
    public Double[] getthresholdsfortime(JSONArray datapoints){

        Long lasttimestamp = (Long)((JSONArray)datapoints.get(datapoints.size()-1)).get(1);
        boolean timespecific = false;
        int i = 0;
        for (AlarmTime at:timesList){
            if (at.checktime(lasttimestamp * 1000)) {
                return ((AlarmTime)timesList.get(i)).thresholds;
            }
            i++;
        }

        return thresholds;
        
    }
    
    /*
     * If the path contains a hostname wildcard then we must include the hostname in the alarm.
     * This method finds where the $$ appears in the search string and pulls out the same field in the returned data,
     * which will be the hostname to which the data applies.
     */
    

    
    public String getcomponent(String target){
        
        if (!substitute_component){
            return component;
        }
        
        String[] patharray = path.split("\\.");
        String[] targetarray = target.split("\\.");
        int i = 0;
        for (String field:patharray){
            if (field.contains("$$")) break;
            i++;
        }
        
        return targetarray[i] + "_" + component;
        

        
    }
    
    /*
     * Each alarm contains a link to a graph of the data which caused it. For non-wildcard alarms this is simply the search string in a different format.
     * For alarms containing a hostname wildcard it must match that unique host, thus the value is taken from the returned data.
     */
    
    public String getmessage(){
      return "";  
    }
  
    /*
     * checkfornodata will return true if the first 10 values in the dataset are empty. If they are
     * then the alarm manager will trigger a missing data alarm to notify that the data is missing from graphite
     */
    public Boolean checkfornodata(JSONObject dataset){
        JSONArray datapoints = (JSONArray)dataset.get("datapoints");
        if (datapoints.size() == 0) return true;
        
        // If the dataset itself is smaller than 6 then use that

        
        for (int i=1; i<6; i++){
            if (i > datapoints.size()) break;
            JSONArray currentdatapoint = (JSONArray)datapoints.get(datapoints.size()-i);
            if (currentdatapoint.get(0) != null){
                return false;
            }
        }
        return true;
    }
    
    
    
    @Override
    public String toString(){
        String threshold_type = "min";
        if (greater_than) threshold_type = "max";
        String s = "{\"ID\": \"" + ID + "\", \"name\": \"" + name + "\", \"component\": \"" + component + "\", \"type\": \"" + type + "\", \"threshold_type\": \"" + threshold_type + "\", \"metric_path\":" + path + ", \"threshold_offset\":" + threshold_offset + "}";
        return s;
    }
    
    public void set_offset(Double _offset){
        threshold_offset = _offset;
    }

    
}
