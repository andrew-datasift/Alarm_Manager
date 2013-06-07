
package com.datasift.operations.alarmmanager;
import org.json.simple.JSONObject;
/**
 * This defines a Rate of Change Percent Alarm, which measures how much a value has changed over a given time and
 * checks that this within a given percentage of a defined threshold.
 * Rate of change is classed as the difference between the maximum value and the minimum value in a time window.
 */
public class ROC_pc_Alarm extends Alarm {
    
    public ROC_pc_Alarm(JSONObject thisalarm) throws Exception{
        GetCommonElements(thisalarm);
    }
    
}
