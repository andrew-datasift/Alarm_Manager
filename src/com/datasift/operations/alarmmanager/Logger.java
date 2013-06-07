
package com.datasift.operations.alarmmanager;


public class Logger {
    
    public static void writeline(Object s){
        System.out.println(s);
    }
    
    public static void writewarn(Object s){
        System.out.println("WARN" + s.toString());
    }
    
    public static void writeerror(Object s){
        System.out.println("ERROR: " + s);
    }
    
    public static void writeerror(Object s, Exception e){
        System.out.println("ERROR: " + s);
        e.printStackTrace();
    }
    
}
