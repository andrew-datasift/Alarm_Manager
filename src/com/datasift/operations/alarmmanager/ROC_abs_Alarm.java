package com.datasift.operations.alarmmanager;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import java.util.Date;
/**
 * This defines a Rate of Change Absolute Alarm, which measures how much a value has changed over a given time and
 * checks that this is below a predefined threshold.
 * Rate of change is classed as the difference between the maximum value and the minimum value in a time window.
 */
public class ROC_abs_Alarm extends Alarm {
    
    Integer window = 60;
    
    public ROC_abs_Alarm(JSONObject thisalarm) throws Exception{
        GetCommonElements(thisalarm);
        try {
            if (thisalarm.get("window") != null) window=Integer.parseInt(thisalarm.get("window").toString());
        } catch (Exception e) {
            Logger.writeerror("Problem parsing alarm on " + this.path + " window value could not be read.", e);
            throw e;
        }
    }
    
     /*
     * processresponse takes a dataset from graphite (as returned by the graphite JSON REST interface).
     * returns a ZenossAlarmProperties object which contains all the information ZenossInterface needs to raise or clear an alarm.
     */
    
    @Override
    public ZenossAlarmProperties processresponse(JSONObject dataset){
        String device = getdevicename( (String)dataset.get("target") );
        String uniqueID = ID.toString() + "_" + device;
        ZenossAlarmProperties zap = new ZenossAlarmProperties(0,prodState,device,component,event_class,summary,uniqueID);
        JSONArray datapoints = (JSONArray)dataset.get("datapoints");
        if (datapoints.size() == 0) return zap;

        
        //Work backwards through values to find max and min
        Double max = null;
        Double min = null;
        Long now = (new Date()).getTime() / 1000;
        
        
        for (int i=(datapoints.size()-1); i>=0; i--){
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
        // zap.summary = zap.summary + latestmeasurement + " / " + getthresholdsfortime(datapoints)[zap.severity];
        
        return zap;
    }
    
}