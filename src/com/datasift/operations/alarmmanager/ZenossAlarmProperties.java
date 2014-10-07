package com.datasift.operations.alarmmanager;

/*
 * ZenossAlarmProperties is returned by the alarm classes when checking thresholds. It contains all of the details for raising and clearing an alarm.
 */

public class ZenossAlarmProperties implements java.io.Serializable {
    public Integer severity;
    public Integer prodState;
    public String device;
    public String component;
    public String eventclass;
    public String summary;
    public String ID;
    public String message;
    public Double threshold;
    public Integer sourcealarmID = 0;
    
    // nodataalarm is a flag that this alarm is for graphite not returning data for this metric. This is used by AlarmManagerState to keep track
    // of such alarms so they can be cleared when data appears.
    public Boolean nodataalarm = false;
    
    public ZenossAlarmProperties(Integer _severity, Integer _prodState, String _device, String _component, String _eventclass, String _summary, String _ID){
        severity = _severity;
        prodState = _prodState;
        device = _device;
        component = _component;
        eventclass = _eventclass;
        summary = _summary;
        message = _summary;
        ID = _ID;
    }
    
    public ZenossAlarmProperties(Integer _severity, Integer _prodState, String _device, String _component, String _eventclass, String _summary, String _message, String _ID){
        severity = _severity;
        prodState = _prodState;
        device = _device;
        component = _component;
        eventclass = _eventclass;
        summary = _summary;
        message = _message;
        ID = _ID;
    }
    
    public ZenossAlarmProperties(Integer _severity, Integer _prodState, String _device, String _component, String _eventclass, String _summary, String _ID, Boolean _nodata){
        severity = _severity;
        prodState = _prodState;
        device = _device;
        component = _component;
        eventclass = _eventclass;
        summary = _summary;
        message = _summary;
        ID = _ID;
        nodataalarm =  _nodata;
    }
    
    
    public ZenossAlarmProperties(Integer _severity, Integer _prodState, String _device, String _component, String _eventclass, String _summary, String _message, String _ID, Boolean _nodata){
        severity = _severity;
        prodState = _prodState;
        device = _device;
        component = _component;
        eventclass = _eventclass;
        summary = _summary;
        message = _message;
        ID = _ID;
        nodataalarm = _nodata;
    }
    
    @Override
    public String toString(){
        String s = "{";
        
        s = s + "\"severity\": \"" + severity + "\",";
        s = s + "\"prodState\": \"" + prodState + "\",";
        s = s + "\"device\": \"" + device + "\",";
        s = s + "\"component\": \"" + component + "\",";
        s = s + "\"eventclass\": \"" + eventclass + "\",";
        s = s + "\"summary\": \"" + summary + "\",";
        if (nodataalarm) s = s + "\"nodataalarm\": \"" + nodataalarm + "\",";
        s = s + "\"ID\": \"" + ID + "\"}";

        
        return s;
    }
}
