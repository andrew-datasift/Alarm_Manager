package com.datasift.operations.alarmmanager;

import java.util.Timer;


/*
 * This is the main class called when running the alarm manager.
 * It parses the command line arguments for the config file then creates a new AlarmManagerState
 * which holds the config and takes care of downloading data from graphite, comparing with thresholds, and
 * sending events to Zenoss 
 */

/*
 * TODO: Load testing
 */

public class alarmManager {
    


    private static AlarmManagerState state;
    
    public static void main(String[] args) throws Exception{
        
        String configfile="";
        Boolean once = false;
        int interval = 60000;
        
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
            } else if (args[i].equals("-i")) {
                i++;
                interval = Integer.parseInt(args[i]) * 1000;
                i++;
            }
            else {
                Logger.writeerror("Unrecognised command line argument:" + args[i]);
                return;
            }
        }
        
        if ( configfile.equals("") )
        { Logger.writeline("please run with arguments \"-c [config filename]\""); 
          return;}
        
        try { state = new AlarmManagerState(configfile); }
        catch (java.io.FileNotFoundException e) { Logger.writeline("Cannot find config file, please specify with -c");
                                return;}

        checkAlarms(state, once, interval);
  
    }
    
    /*
     * checkAlarms uses the Timer scheduler to run and AlarmManagerState.run() method
     * at the prescribed interval.
     */
    
    /*
     * TODO: Add entry to config to set the port for the HTTP interface, and handle any failures gracefully.
     */

    private static void checkAlarms (AlarmManagerState ams, Boolean once, int interval) throws Exception {
        //HTTPapi httpapi = new HTTPapi();
        //httpapi.launch();
        Thread thread1 = new Thread(new HTTPapi(state), "thread1");
        thread1.start();
        Timer timer = new Timer();
        if (once) ams.run();
        else timer.schedule(ams,0, interval);
        return;
        
    }
    

}
