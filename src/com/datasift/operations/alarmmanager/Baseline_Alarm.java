
package com.datasift.operations.alarmmanager;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

public class Baseline_Alarm extends Alarm {
    

    Double target;
    
    public Baseline_Alarm(JSONObject thisalarm) throws Exception{
        GetCommonElements(thisalarm);
        String comparison;
        if (greater_than) comparison = "above";
        else comparison = "below";
        if (thisalarm.get("summary") == null) summary = path + " is " + comparison + " threshold: ";
        else summary = summary + " is " + comparison + " threshold: ";
    }
    
    @Override
    public ZenossAlarmProperties processresponse(JSONObject dataset){
        String device = getdevicename( (String)dataset.get("target") );
        String uniqueID = ID.toString() + "_" + device;
        ZenossAlarmProperties zap = new ZenossAlarmProperties(0,prodState,device,component,event_class,summary,uniqueID);
        JSONArray datapoints = (JSONArray)dataset.get("datapoints");

        
        /*
         * The latest datapoint is often null as not all graphite inputs report at the same time.
         * Thus we work back through the dataset. If the set contains only null values the alarmmanager should have generates an error
         * before this point, so we handle that case by returning a clear value.
         */
        
        Double latestmeasurement = null;
        for (int i=1; i<datapoints.size(); i++){
            JSONArray currentdatapoint = (JSONArray)datapoints.get(datapoints.size()-i);
            if (currentdatapoint.get(0) != null){
                latestmeasurement = new Double(currentdatapoint.get(0).toString());
                break;
            }
        }
        if (latestmeasurement == null) return zap;
        
        zap.severity=getcurrentseveritylevel(datapoints, latestmeasurement);
        if (zap.severity == -1) return zap;
        zap.summary = zap.summary + " " + latestmeasurement + " / " + getthresholdsfortime(datapoints)[zap.severity];
        return zap;
    }
}
