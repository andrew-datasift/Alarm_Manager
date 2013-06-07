
package com.datasift.operations.alarmmanager;
import org.json.simple.JSONObject;

public class Baseline_Alarm extends Alarm {
    
    
    public Baseline_Alarm(JSONObject thisalarm) throws Exception{
        GetCommonElements(thisalarm);
    }
    
}
