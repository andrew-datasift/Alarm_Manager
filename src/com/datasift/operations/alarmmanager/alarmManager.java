package com.datasift.operations.alarmmanager;

import java.util.Timer;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.simple.JSONObject;



/*
 * This is the main class called when running the alarm manager.
 * It parses the command line arguments for the config file then creates a new AlarmManagerState
 * which holds the config and takes care of downloading data from graphite, comparing with thresholds, and
 * sending events to Zenoss 
 */



public class alarmManager {
    


    private static AlarmManagerState state;
    static Logger logger = Logger.getLogger("AlarmManager");
    
    public static void main(String[] args) throws Exception{
        

        logger.info("AlarmManager started");
        String configfile="";
        Boolean once = false;
        int interval = 60000;
        boolean testmode = false;
        PropertyConfigurator.configure(System.getProperty("log4j.configuration"));
        
        
        /* 
         * Parse command line arguments
         * -c [filename] for the config file, this is required
         * --once for ensuring the process only runs once and quits
         * -t [interval] the number of seconds between each run. Default is 60 as that is default graphite granularity.
         */
        
        int i = 0;
        while (i < args.length){
            if (args[i].equals("-c")) {
                i++;
                configfile = args[i];
                i++;
            } else if (args[i].equals("--once")) {
                once = true;
                i++;
            } else if (args[i].equals("-t")) {
                testmode = true;
                i++;
            } else if (args[i].equals("-i")) {
                i++;
                interval = Integer.parseInt(args[i]) * 1000;
                i++;
            }
            else {
                logger.error("Unrecognised command line argument:" + args[i]);
                return;
            }
        }
        
        if ( configfile.equals("") )
        {  
          logger.error("Configuration file not found. Please run with argument \"-c [config filename]\""); 
          return;}
        
        
        try { state = new AlarmManagerState(configfile, testmode); }
        catch (java.io.FileNotFoundException e) { logger.error("Cannot find config file, please specify");
                                return;}
        catch (Exception e) {
            System.out.println("Cannot start alarmmanager. See log file for details");
            logger.fatal("Fatal error starting alarmmanager", e);
            return;
        }

        checkAlarms(state, once, interval);
  
    }
    
    /*
     * checkAlarms uses the Timer scheduler to run and AlarmManagerState.run() method
     * at the prescribed interval.
     */

    private static void checkAlarms (AlarmManagerState ams, Boolean once, int interval) throws Exception {
        //HTTPapi httpapi = new HTTPapi();
        //httpapi.launch();
        if (state.httpport != null) {
            Thread thread1 = new Thread(new HTTPapi(state, state.httpport), "HTTP_API");
            thread1.start();
        } else {
            logger.error("HTTP port not specified in config file, HTTP API will not be available.");
        }
        Timer timer = new Timer();
        if (once) ams.run();
        else timer.schedule(ams,0, interval);
        return;
        
    }
    

}
