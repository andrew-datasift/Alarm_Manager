package com.datasift.operations.alarmmanager;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
/**
 * This alarm checks that a graphite data set does not sit outside the values predicted by a HoltWinters prediction.
 * HoltWinters is a mathmatical model which predicts values based on history with older values having decreasing relevence over time.
 * Graphite can produce a holt winters prediction for the current time based on old values, along with "confidence bands", if the current value
 * lies outside those bands it means the value is not in line with the trend and may consitiute a problem.
 */
public class HoltWinters_Alarm extends Alarm {
    
    public HoltWinters_Alarm(JSONObject thisalarm) throws Exception{
        GetCommonElements(thisalarm);
        
        if (path.contains("$$")) throw new Exception("Holtwinters alarms cannot contain a wildcard in the path. Skipping alarm for " + path);
        searchquery="&target=aliasSub(holtWintersAberration(" + path + "),\"^\",\"" + ID + "_\")";
        
        if (thisalarm.get("summary") == null) {
            summary = path + " deviation from holtwinters confidence bands is outside threshold: ";
        }
    }
    
        @Override
    public ZenossAlarmProperties processresponse(JSONObject dataset){
        String device = getdevicename( (String)dataset.get("target") );
        String uniqueID = ID.toString() + "_" + device;
        ZenossAlarmProperties zap = new ZenossAlarmProperties(0,prodState,device,component,event_class,summary,uniqueID);
        JSONArray datapoints = (JSONArray)dataset.get("datapoints");
        if (datapoints.size() == 0) return zap;

        
        Double latestmeasurement = null;
        for (int i=1; i<4; i++){
            JSONArray currentdatapoint = (JSONArray)datapoints.get(datapoints.size()-i);
            if (currentdatapoint.get(0) != null){
                latestmeasurement = new Double(currentdatapoint.get(0).toString());
                break;
            }
        }
        if (latestmeasurement == null) return zap;
        
        // Holt winters looks for a deviation from predicion, so any movement + or - from threshold should trigger.
        // Thus difference from zero is compared to the threshold, not the actual value.
        
        Double deviation = latestmeasurement;
        if (latestmeasurement < 0) deviation = (0 - latestmeasurement);
        
        zap.severity=getcurrentseveritylevel(datapoints, deviation);

        
        // Add the value to the summary
        zap.summary = zap.summary + " " + deviation + " / " + getthresholdsfortime(datapoints)[zap.severity];
        return zap;
    }
    
}
