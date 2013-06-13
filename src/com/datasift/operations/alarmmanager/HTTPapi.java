
package com.datasift.operations.alarmmanager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
 
import java.io.IOException;
 
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class HTTPapi  extends AbstractHandler implements Runnable {
    
    AlarmManagerState ams;
    
    public HTTPapi(AlarmManagerState _ams){
        ams = _ams;
    }

    @Override
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response) 
        throws IOException, ServletException
    {
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        if (request.getMethod().equals("GET")) response.getWriter().println(handleGET(request));
        else if (request.getMethod().equals("POST")) response.getWriter().println(handlePOST(request));
        else response.getWriter().println("Request not recognised"); 

    }
    
    private String handleGET(HttpServletRequest request){
        String query = request.getQueryString();
        String output = "Query not found";
        if (query != null && query.equalsIgnoreCase("triggered")) output = ams.ShowCurrentAlarms();
        else output = ams.ShowAllAlarms();
        return output;
    }
    
    private String handlePOST(HttpServletRequest request){
        String[] query = request.getQueryString().split("&");
        String output = "Query not found";
        if (query[0] != null && query[0].equalsIgnoreCase("set_multiplier")) output = handleMultiplierUpdate(query);
        else output = "Unsupported POST message";
        return output;
    }
    
    private String handleMultiplierUpdate(String[] query){
        Double multiplier = null;
        Integer minutes = null;
        Integer alarm = null;
        for (int i=0; i<query.length; i++){
            String[] pair = query[i].split("=");
            try {
                if (pair[0].equalsIgnoreCase("multiplier")) multiplier = Double.parseDouble(pair[1]);
                else if (pair[0].equalsIgnoreCase("minutes")) minutes = Integer.parseInt(pair[1]);
                else if (pair[0].equalsIgnoreCase("alarm_id")) alarm = Integer.parseInt(pair[1]);
            } catch (NumberFormatException e) {
                Logger.writeerror("Error setting new threshold, cannot parse value", e);
                return "Error setting new threshold, cannot parse value";
            }
        }
        
        if (multiplier == null || minutes == null || alarm == null) {
                Logger.writeerror("Error setting new threshold, required field not present (multiplier, minutes and alarm_id)");
                return "Error setting new threshold, required field not present (multiplier, minutes and alarm_id)";
        }
        
        return ams.IncreaseThreshold(alarm, multiplier, minutes);
    }
    
 
    public void launch() throws Exception
    {
        Server server = new Server(8080);
        server.setHandler(this);
 
        server.start();
        server.join();
    }
    
    
    
    @Override
    public void run(){
		//Display info about this particular thread
                try {
                    launch();
                } catch (Exception e) {
                    
                }
    }
}
