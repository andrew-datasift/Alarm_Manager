package com.datasift.operations.alarmmanager;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import java.util.Date;
import org.apache.log4j.Logger;
/**
 * This defines a Rate of Change Absolute Alarm, which measures how much a value has changed over a given time and
 * checks that this is below a predefined threshold.
 * Rate of change is classed as the difference between the maximum value and the minimum value in a time window.
 */
public class ROC_abs_Alarm extends Alarm {
    
    Integer window = 10;
    private static Logger logger = Logger.getLogger("AlarmManager.Alarm.ROC_abs_Alarm");
    
    public ROC_abs_Alarm(JSONObject thisalarm) throws Exception{
        GetCommonElements(thisalarm);
        try {
            if (thisalarm.get("window") != null) window=Integer.parseInt(thisalarm.get("window").toString());
        } catch (Exception e) {
            logger.error("Problem parsing alarm on " + this.path + " window value could not be read.", e);
            throw e;
        }
        
        String comparison;
        if (greater_than) comparison = "above";
        else comparison = "below";
        if (thisalarm.get("summary") == null) summary = name + " change in " + window + " min is " + comparison + " threshold: ";
        else summary = summary + " rate of change in " + window + " minutes is " + comparison + " threshold: ";
    }
    
     /*
     * processresponse takes a dataset from graphite (as returned by the graphite JSON REST interface).
     * returns a ZenossAlarmProperties object which contains all the information ZenossInterface needs to raise or clear an alarm.
     */
    
    @Override
    public ZenossAlarmProperties processresponse(JSONObject dataset){
        String uniquecomponent = getcomponent((String)dataset.get("target"));
        String uniqueID = ID.toString() + "_" + uniquecomponent;
        ZenossAlarmProperties zap = new ZenossAlarmProperties(0,prodState,"Graphite",uniquecomponent,event_class,summary,uniqueID);
        JSONArray datapoints = (JSONArray)dataset.get("datapoints");
        if (datapoints.size() == 0) return zap;

        
        //Work backwards through values to find max and min
        Double max = null;
        Double min = null;
        Long now = (new Date()).getTime() / 1000;
        
        //We discard the most recent value for ROC alarms because, in graphite, the most recent value often cannot be trusted. It could
        //be zero showing a change where none existsm, or in a sumSeries it could be between zero and the true value.
        
        for (int i=(datapoints.size()-2); i>=0; i--){
            JSONArray currentdatapoint = (JSONArray)datapoints.get(i);
            Long lasttimestamp = (Long)currentdatapoint.get(1);
            if (lasttimestamp < now - (window * 60)) break;
            if (currentdatapoint.get(0) != null){
                Double currentvalue = new Double(currentdatapoint.get(0).toString());
                if (max == null || currentvalue > max) max = currentvalue;
                if (min == null || currentvalue < min) min = currentvalue;
            }
        }
        
        
        if (max == null || min == null) return zap;
        Double roc = max - min;
        zap.severity=getcurrentseveritylevel(datapoints, roc);
        if (zap.severity == -1) return zap;
        zap.summary = zap.summary + " " +  String.format("%.0f", roc) + " / " + String.format("%.0f", getthresholdsfortime(datapoints)[zap.severity]);
        // zap.summary = zap.summary + latestmeasurement + " / " + getthresholdsfortime(datapoints)[zap.severity];
        
        return zap;
    }
    
}
