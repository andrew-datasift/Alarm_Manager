
package com.datasift.operations.alarmmanager;
import static com.datasift.operations.alarmmanager.Alarm.logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
/**
 *
 * Absolute_Alarm is a simple threshold check which alarms if the latest current value is above or below the threshold.
 */
public class Absolute_Alarm extends Alarm{
    static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger("AlarmManager.Alarm");
    
    public Absolute_Alarm(JSONObject thisalarm) throws Exception{
        GetCommonElements(thisalarm);
        String comparison;
        if (greater_than) comparison = "above";
        else comparison = "below";
        if (thisalarm.get("summary") == null) summary = summary + " is " + comparison + " threshold: ";

    }
    
    /*
     * processresponse takes a dataset from graphite (as returned by the graphite JSON REST interface).
     * returns a ZenossAlarmProperties object which contains all the information ZenossInterface needs to raise or clear an alarm.
     */
    

    @Override
    public ZenossAlarmProperties processalarm(JSONObject dataset){
        String uniquecomponent = getcomponent((String)dataset.get("target"));
        //String uniqueID = ID.toString() + "_" + uniquecomponent;
        String target = (String)dataset.get("target");
        ZenossAlarmProperties zap = new ZenossAlarmProperties(0,prodState,"Graphite",uniquecomponent,event_class,summary,target);
        zap.sourcealarmID = this.ID;
        JSONArray datapoints = (JSONArray)dataset.get("datapoints");

        
        
        Double[] localthresholds = getthresholdsfortime(datapoints);

        
        boolean clear = true;
        
        if ((datapoints.size() < clearincrements+1) || (datapoints.size() < triggerincrements+1)){
            zap.summary = ("Graphite did not return enough data to process alarm. See message for details");
            zap.message = ("Graphite did not return enough data to process alarm. Metric path in config: " + path + ". Metric path returned: " + target);
            zap.severity = 4;
            return zap;
        }
        
        logger.info("clear threshold = " + localthresholds[0]);
        
        // Alarms are most likely to be clear, so check for a clear alarm first
        for (int i=2; i<=(clearincrements+1); i++){
            JSONArray currentdatapoint = (JSONArray)datapoints.get(datapoints.size()-i);
            logger.info(currentdatapoint);
            if (currentdatapoint.get(0) != null) {
                Double currentvalue = new Double(currentdatapoint.get(0).toString());
                if ( (greater_than && (currentvalue > localthresholds[0])) || (!greater_than && (currentvalue < localthresholds[0])) ) {
                    clear = false;
                    break;
                }
            }
            else {
                clear = false;
                break;
            }
            

        }
        
        // if none of the values are above the clear threshold then return with clear severity (-1)
        if (clear) logger.info("alarm is clear");
        if (clear) return zap;
        
        boolean breached;
        Integer severity = -1;
        
        /* 
         * Step through all of the severities in turn to see if all the values within the increments window are above that severity.
         * If they are then move the severity to that level and check the next. If they are not then stop.
         */
        
        for (int i=1; i<=5; i++){
            if (localthresholds[localthresholds.length-i] != null){
                Double threshold = localthresholds[localthresholds.length-i];
                logger.debug("Checking severity " + (localthresholds.length-i) + " = " + threshold);
                breached = true;
                for (int x=2; x<=(triggerincrements+1); x++){
                    JSONArray currentdatapoint = (JSONArray)datapoints.get(datapoints.size()-x);
                    if (currentdatapoint.get(0) != null){
                        Double currentvalue = new Double(currentdatapoint.get(0).toString());
                        if ( (greater_than && (currentvalue <= threshold)) || (!greater_than && (currentvalue >= threshold)) ) {
                            breached = false;
                            logger.debug("Severity is not breached");
                            break;
                        }
                    }

                }
                if (breached) {
                    severity = thresholds.length-i;
                    break;
                }
            }

        }
        
        Double latestmeasurement = null;
        for (int i=2; i<datapoints.size(); i++){
            JSONArray currentdatapoint = (JSONArray)datapoints.get(datapoints.size()-i);
            if (currentdatapoint.get(0) != null){
                latestmeasurement = new Double(currentdatapoint.get(0).toString());
                break;
            }
        }
        
        zap.severity=severity;
        

        if (zap.severity == -1) return zap;
        zap.summary = zap.summary + " " +  String.format("%.0f", latestmeasurement) + " / " + String.format("%.0f", localthresholds[zap.severity]);
        return zap;
    }

    
}
