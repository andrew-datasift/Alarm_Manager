package com.datasift.operations.alarmmanager;

/*
 * ZenossAlarmProperties is returned by the alarm classes when checking thresholds. It contains all of the details for raising and clearing an alarm.
 */

public class ZenossAlarmProperties {
    public Integer severity;
    public Integer prodState;
    public String device;
    public String component;
    public String eventclass;
    public String summary;
    public String ID;
    public String message;
    
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
}
