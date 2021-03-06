
package com.datasift.operations.alarmmanager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.net.BindException;
import org.apache.log4j.Logger;
import java.io.IOException;
 
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class HTTPapi  extends AbstractHandler implements Runnable {
    private static Logger logger = Logger.getLogger("AlarmManager.HTTPapi");
    AlarmManagerState ams;
    Integer port;

    
    public HTTPapi(AlarmManagerState _ams, Integer _port){
        ams = _ams;
        port = _port;
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
        
        try {
            if (baseRequest.getRequestURI().startsWith("/alarmmanager")) response.getWriter().println(handleHTML(baseRequest));
            else if (request.getMethod().equals("GET")) response.getWriter().println(handleGET(request));
            else if (request.getMethod().equals("POST")) response.getWriter().println(handlePOST(request));
            else response.getWriter().println("Request not recognised"); 
        } catch (Exception e) {
            logger.error("Exception processing request to HTTP api", e);
        }

    }
    
    private String handleHTML(Request baseRequest) throws IOException {
        String path = baseRequest.getRequestURI();
        HTMLGenerator index = new HTMLGenerator(ams);
        if (baseRequest.getParameter("OFFSET") != null) {
            String offset = (baseRequest.getParameter("OFFSET"));
            String time = (baseRequest.getParameter("TIME"));
            String alarmid = (baseRequest.getParameter("ALARMID"));
            try {
                handleOffsetUpdate(alarmid, offset, time);
            } catch (Exception e) {
                logger.error("Error processing offset request via HTML interface", e);
            }
            
            return index.getAlarm(baseRequest.getParameter("ALARMID"));
        }

        if (  path.startsWith("/alarmmanager/alarm/") ) return index.getAlarm(path.substring(path.lastIndexOf('/') + 1));
        
        else return index.getIndex();
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
        if (query[0] != null && query[0].equalsIgnoreCase("set_offset")) output = handleOffsetUpdate(query);
        else output = "Unsupported POST message";
        return output;
    }
    
    private void handleOffsetUpdate(String _alarmid, String _offset, String _minutes) {
        Double offset = null;
        Integer minutes = null;
        Integer alarm = null;
        try {
            offset = Double.parseDouble(_offset);
            minutes = Integer.parseInt(_minutes);
            alarm = Integer.parseInt(_alarmid);
        } catch (NumberFormatException e) {
            logger.error("Error setting alarm ID, unable to parse input", e);
        }
        ams.IncreaseThreshold(alarm, offset, minutes);
        
    }
    
    private String handleOffsetUpdate(String[] query){
        Double offset = null;
        Integer minutes = null;
        Integer alarm = null;
        for (int i=0; i<query.length; i++){
            String[] pair = query[i].split("=");
            try {
                if (pair[0].equalsIgnoreCase("offset")) offset = Double.parseDouble(pair[1]);
                else if (pair[0].equalsIgnoreCase("minutes")) minutes = Integer.parseInt(pair[1]);
                else if (pair[0].equalsIgnoreCase("alarm_id")) alarm = Integer.parseInt(pair[1]);
            } catch (NumberFormatException e) {
                logger.error("{\"success\": false, \"response\": \"Error setting new threshold, cannot parse value\"}", e);
                return "{\"success\": false, \"response\": \"Error setting new threshold, cannot parse value\"}";
            }
        }
        
        if (offset == null || minutes == null || alarm == null) {
                logger.error("Error setting new threshold, required field not present (offset, minutes and alarm_id)");
                return "{\"success\": false, \"response\": \"Error setting new threshold, required field not present (offset, minutes and alarm_id)\"}";
        }
        logger.info("Increasing threshold for alarm ID " + alarm + " by " + offset + " for " + minutes + " minutes.");
        return ams.IncreaseThreshold(alarm, offset, minutes);
    }
    
 
    public void launch() throws Exception
    {
        Server server = new Server(port);
        server.setHandler(this);
        logger.info("HTTP server is listenning on port " + port);
        server.start();
        server.join();
        
    }
    
    
    
    @Override
    public void run() {
                try {
                    launch();  
                } catch (BindException e) {
                    logger.error("Cannot bind to specified port, address in use. HTTP API will not be available");
                } catch (Exception e) {
                    logger.error("Error from HTTP API", e);
                }
    }
}
