/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.datasift.operations.alarmmanager;


import java.io.IOException;

import org.rendersnake.HtmlCanvas;
import static org.rendersnake.HtmlAttributesFactory.*;
import org.rendersnake.Renderable;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Date;

public class HTMLGenerator {
    
    private static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger("AlarmManager.HTTPapi");
    AlarmManagerState ams;
    
    public class GotoTop implements Renderable {
 
    @Override
    public void renderOn(HtmlCanvas html) throws IOException {
        html.a(href("#top").class_("toplink")).content("top");
    }
}
    
    public HTMLGenerator(AlarmManagerState _ams){
        ams = _ams;
    }
    

    public String getIndex() throws IOException {



        HtmlCanvas html = new HtmlCanvas();
        

        html.html().body();
        
        html.h4().content("Currently triggered alerts");
        
        add_current_alarms(html);

        add_adjusted_alarms(html);
        
        add_all_alarms(html);
        
        html._body()._html();




        // write the file
        final String rendered = html.toHtml();
        return rendered;
    }
    
    private void add_adjusted_alarms(HtmlCanvas html) throws IOException {
        
        if (ams.IncreasedThresholds.keySet().isEmpty()) return;
        
        Iterator it = ams.IncreasedThresholds.keySet().iterator();
        
        html.h4().content("The following alarms have had their thresholds temporarily adjusted");
                
        html.table(border("1"))
                .tr()
                    .th().content("ID")
                    .th().content("Name")
                    .th().content("Event Class")
                    .th().content("Component")
                    .th().content("Type")
                    .th().content("Path")
                ._tr();

        
        while (it.hasNext()) {
            Alarm alarm = ams.AlarmsMap.get((Integer)it.next());
                 html.tr()
                    .td()
                        .a(href("/alarmmanager/alarm/" + alarm.ID.toString()))
                            .content(alarm.ID.toString())
                    ._td()
                    .td().content(alarm.name)
                    .td().content(alarm.event_class)
                    .td().content(alarm.component)
                    .td().content(alarm.type)
                    .td().content(alarm.path)
                ._tr();
        }
        
                // close the table
        html._table();
    }
    
    private void add_all_alarms(HtmlCanvas html) throws IOException {

        html.h4().content("All Alarms");
                
        html.table(border("1"))
                .tr()
                    .th().content("ID")
                    .th().content("Name")
                    .th().content("Event Class")
                    .th().content("Component")
                    .th().content("Type")
                    .th().content("Path")
                ._tr();
        
        Iterator it = ams.AlarmsMap.keySet().iterator();
        while (it.hasNext()) {
            Alarm alarm = ams.AlarmsMap.get((Integer)it.next());
                 html.tr()
                    .td()
                        .a(href("/alarmmanager/alarm/" + alarm.ID.toString()))
                            .content(alarm.ID.toString())
                    ._td()
                    .td().content(alarm.name)
                    .td().content(alarm.event_class)
                    .td().content(alarm.component)
                    .td().content(alarm.type)
                    .td().content(alarm.path)
                ._tr();
        }
        
        // close the table
        html._table();
    }
    
    public String getAlarm(String alarmID) throws IOException {

        Alarm alarm = ams.AlarmsMap.get(Integer.parseInt(alarmID));
        

        HtmlCanvas html = new HtmlCanvas();
        
        html.a(href("/alarmmanager")).content("Return to index");
        
        html
            .h2()
                .content("Summary of alarm: " + alarm.name);
        
        if (alarm.active) html.h3().font(color("green")).content("Active");
        else html.h3().font(color("green")).content("Inactive");
        

        String[] thresholds = new String[4];
        

       

        String thresholdtype = "maximum";


        if (!alarm.greater_than) thresholdtype = "minimum";
        
        if (alarm.threshold_offset != 0.0) {
            Date offsetdate = ams.IncreasedThresholds.get(alarm.ID);
            html.h3().font(color("red")).content("This alarm currently has a threshold offset of " + alarm.threshold_offset + " applied until " + offsetdate.toString());
            Double withoffset;
            for (int i=0; i<4; i++){
                if (alarm.thresholds[i+2] != null ) {
                    withoffset = alarm.thresholds[i+2] + alarm.threshold_offset;
                    thresholds[i] = alarm.thresholds[i+2].toString() + " (" + withoffset + " with offset applied)";
                } else {
                    thresholds[i] = "none";
                }
            }
        } else {
            for (int i=0; i<4; i++){
                if (alarm.thresholds[i+2] != null ) {
                    thresholds[i] = alarm.thresholds[i+2].toString();
                } else {
                    thresholds[i] = "none";
                }
            }
        }
        
                
        html.html().body().table(border("1"))
                .tr()
                    .th().content("Field")
                    .th().content("Value")
                ._tr()
                .tr()
                    .td().content("Name")
                    .td().content(alarm.name)
                ._tr()
                .tr()
                    .td().content("Component")
                    .td().content(alarm.component)
                ._tr()
                .tr()
                    .td().content("Event Class")
                    .td().content(alarm.event_class)
                ._tr()
                .tr()
                    .td().content("Summary")
                    .td().content(alarm.summary)
                ._tr()
                .tr()
                    .td().content("Description")
                    .td().content(alarm.description + "")
                ._tr()
                .tr()
                    .td().content("Threshold Type")
                    .td().content(thresholdtype)
                ._tr()
                .tr()
                    .td().content("Info threhold (2)")
                    .td().content(thresholds[0])
                ._tr()
                .tr()
                    .td().content("Warn Threshold (3)")
                    .td().content(thresholds[1])
                ._tr()       
                .tr()
                    .td().content("Error Threshold (4)")
                    .td().content(thresholds[2])
                ._tr()
                .tr()
                    .td().content("Critical Threshold (5)")
                    .td().content(thresholds[3])
                ._tr()
                .tr()
                    .td().content("Clear Threshold")
                    .td().content(alarm.thresholds[0].toString())
                ._tr()
                .tr()
                    .td().content("No of events to trigger")
                    .td().content(alarm.triggerincrements.toString())
                ._tr()
                .tr()
                    .td().content("No of events to clear")
                    .td().content(alarm.clearincrements.toString())
                ._tr();
        html._table();

        if (!alarm.timesList.isEmpty()) {
            html.h4().content("Time specific thresholds");
            
            add_time_thresholds(html, alarm);
            
            
        }
        
        
        html.h4().content("Last 2 hours");
        
        html.img(src("https://graphite.sysms.net/render/?target=" + alarm.path + "&height=400&width=600&from=-2hours"));
        
        html.h4().content("Last 24 hours");
        
        html.img(src("https://graphite.sysms.net/render/?target=" + alarm.path + "&height=400&width=600&from=-24hours"));
        
        html.hr();
        
        html.h3().content("Modify alarm");
        
        html
          .form(action("/alarmmanager/alarm/"+alarm.ID.toString()).method("post"))
              .label(for_("OFFSET")).write("Threshold offset: ")._label()
              .input(id("OFFSET").name("OFFSET").value("0").size(10))
              .label(for_("TIME")).write(" Time to apply offset (minutes): ")._label()
              .input(id("TIME").name("TIME").value("0").size(10))
              .input(type("hidden").name("ALARMID").value(alarm.ID.toString()))
              .input(type("submit").value("Apply offset"))
          ._form();

                
                

        // close the table
        html._body()._html();

        // write the file
        final String rendered = html.toHtml();
        return rendered;
    }
    
    private void add_time_thresholds(HtmlCanvas html, Alarm alarm) throws IOException {

        String[] thresholds = new String[4];
        String clear = alarm.thresholds[0].toString();
        String start;
        String end;

        
        html.html().body().table(border("1"))
                .tr()
                    .th().content("Start Time")
                    .th().content("End Time")
                    .th().content("Clear")
                    .th().content("Info")
                    .th().content("Warn")
                    .th().content("Error")
                    .th().content("Critical")
                ._tr();
        
        for (AlarmTime at : alarm.timesList) {
            
            if (alarm.threshold_offset != 0.0) {
                Double withoffset;
                for (int i=0; i<4; i++){
                    if (at.thresholds[i+2] != null ) {
                        withoffset = at.thresholds[i+2] + alarm.threshold_offset;
                        thresholds[i] = at.thresholds[i+2].toString() + " (" + withoffset + " with offset applied)";
                    } else {
                        thresholds[i] = "none";
                    }
                }
            } else {
                for (int i=0; i<4; i++){
                    if (at.thresholds[i+2] != null ) {
                        thresholds[i] = at.thresholds[i+2].toString();
                    } else {
                        thresholds[i] = "none";
                    }
                }
            }
       

            if (at.start < 999) {start = "0" + Integer.toString(at.start);} else {start = Integer.toString(at.start);}
            if (at.end < 999) {end = "0" + Integer.toString(at.end);} else {end = Integer.toString(at.end);}
        
                 html.tr()
                    .td().content(start)
                    .td().content(end)
                    .td().content(clear)
                    .td().content(thresholds[0])
                    .td().content(thresholds[1])
                    .td().content(thresholds[2])
                    .td().content(thresholds[3])
                ._tr();
        }
        
        html._table();
        
    }
    
    private void add_current_alarms(HtmlCanvas html) throws IOException {
        
        
        html.table(border("1"))
                .tr()
                    .th().content("Alarm ID")
                    .th().content("Alarm Name")
                    .th().content("Event Class")
                    .th().content("Component")
                    .th().content("Severity")
                    .th().content("Summary")
                ._tr();
        
        for (ZenossAlarmProperties zap : ams.CurrentAlarms.values()){
            if (zap.severity != 0) {
                html.tr();

                if (zap.sourcealarmID != null && zap.sourcealarmID != 0) {
                    html.td()
                        .a(href("/alarmmanager/alarm/" + zap.sourcealarmID.toString()))
                            .content(zap.sourcealarmID.toString())
                    ._td()
                    .td().content(ams.AlarmsMap.get(zap.sourcealarmID).name);
                } else {
                    html.td().content("N/A")
                        .td().content("N/A");
                }
                
                html.td().content(zap.eventclass)
                    .td().content(zap.component)
                    .td().content(zap.severity.toString())
                    .td().content(zap.summary);
                
                html._tr();
            }

        }
        
        html._table();
    }

}

