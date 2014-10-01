
package com.datasift.operations.alarmmanager;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

public class Baseline_Alarm extends Alarm {
    
         /*
         * The latest datapoint is often null as not all graphite inputs report at the same time.
         * Thus we work back through the dataset. If the set contains only null values the alarmmanager should have generates an error
         * before this point, so we handle that case by returning a clear value.
         */

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
    public Integer getcurrentseveritylevel(JSONArray datapoints, Double latestmeasurement){
        Double[] localthresholds = getthresholdsfortime(datapoints);
        for (int i=5; i>0; i--){
           if ( (localthresholds[i] != null) && (latestmeasurement > (localthresholds[i] + threshold_offset)) ) return i;
        }
        
        if ( latestmeasurement <= (localthresholds[0] + threshold_offset)) return 0;

        
        return -1;
        
    }
    
}
