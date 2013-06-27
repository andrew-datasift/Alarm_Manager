
package com.datasift.operations.alarmmanager;
import org.json.simple.JSONObject;
import java.util.Date;
import java.text.SimpleDateFormat;
/**
 * Alarms can have different thresholds for different times of day.
 * AlarmTime holds the thresholds for each severity, along with a start and end time, for a given period.
 */
public class AlarmTime{
        int start;
        int end;
        Double[] thresholds = new Double[6];
        
        /*
         * The constructor takes a JSON object, typically grabbed from a config file, which holds the neccessary values.
         * "start" and "end" are times in 24 hour HHMM notation.
         */
            
        public AlarmTime(JSONObject at){
            
            start=Integer.parseInt(at.get("start").toString());
            end=Integer.parseInt(at.get("end").toString());
            
            if (at.get("severity5") != null) thresholds[5]=Double.parseDouble(at.get("severity5").toString());
            if (at.get("severity4") != null) thresholds[4]=Double.parseDouble(at.get("severity4").toString());
            if (at.get("severity3") != null) thresholds[3]=Double.parseDouble(at.get("severity3").toString());
            if (at.get("severity2") != null) thresholds[2]=Double.parseDouble(at.get("severity2").toString());
            if (at.get("severity1") != null) thresholds[1]=Double.parseDouble(at.get("severity1").toString());
            if (at.get("clear") != null) thresholds[0]=Double.parseDouble(at.get("clear").toString());
            
         /*
         * If no clear threshold is explicitly given then take the lowest given threshold as the clear.
         */
        
            if (thresholds[0] == null){
                for (int i=1; i<=5; i++){
                if ( thresholds[i] != null) {
                    thresholds[0] = thresholds[i];
                    break;
                    }
                }
            }


        }
        
        /**
         * checktime will return true if a given epoch timestamp is between the start and end times.
         * This is used to find if the thresholds it contains apply at the given time.
         */
        
        public boolean checktime(Long _timestamp){
            Date timestamp = new Date(_timestamp);
            SimpleDateFormat ft = new SimpleDateFormat("HHmm");
            int inttime = Integer.parseInt(ft.format(timestamp));
            if (start <= end){
                if ((inttime <= end) && (inttime >= start)) return true;
            } else {
                if ((inttime >= start) || (inttime <= end)) return true;
            }
            return false;
        }
    }
