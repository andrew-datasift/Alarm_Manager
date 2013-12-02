package com.datasift.operations.alarmmanager;
    
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import org.apache.xmlrpc.client.XmlRpcClient; 
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl; 
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.apache.log4j.Logger;

/*
 * Zenoss has two APIs; the REST api and the JSON api. This object implements both.
 * By default the REST api is used, but an alternate constructor can be called to implement the JSON API.
 */

public class ZenossInterface {

    private String ZENOSS_HOST;
    private String ZENOSS_PROTOCOL;
    private String ZENOSS_INSTANCE;
    private String ZENOSS_USERNAME;
    private String ZENOSS_PASSWORD;
    private Boolean JSONAPI = false;
    private static Logger logger = Logger.getLogger("AlarmManager.ZenossInterface");
    private final static HashMap ROUTERS = new HashMap();
    static {
        ROUTERS.put("MessagingRouter", "messaging");
        ROUTERS.put("EventsRouter", "evconsole");
        ROUTERS.put("ProcessRouter", "process");
        ROUTERS.put("ServiceRouter", "service");
        ROUTERS.put("DeviceRouter", "device");
        ROUTERS.put("NetworkRouter", "messaging");
        ROUTERS.put("TemplateRouter", "template");
        ROUTERS.put("DetailNavRouter", "detailnav");
        ROUTERS.put("ReportRouter", "report");
        ROUTERS.put("MibRouter", "mib");
        ROUTERS.put("ZenPackRouter", "zenpack");
    }

    private DefaultHttpClient httpclient = new DefaultHttpClient();
    private ResponseHandler<String> responseHandler = new BasicResponseHandler();
    private JSONParser jsonParser = new JSONParser();
    private int reqCount = 1;

    // Constructor logs in to the Zenoss instance (getting the auth cookie)
    public ZenossInterface(String address, Integer port, String username, String password) throws Exception {
        
        if (!address.startsWith("http")) address = "https://" + address;
        ZENOSS_HOST = address.split("://",2)[1] + ":" + port;
        ZENOSS_PROTOCOL = address.split("://",2)[0];
        ZENOSS_INSTANCE = address + ":" + port;
        ZENOSS_USERNAME = username;
        ZENOSS_PASSWORD = password;

        
        HttpPost httpost = new HttpPost(ZENOSS_INSTANCE +
                           "/zport/acl_users/cookieAuthHelper/login");

        List <NameValuePair> nvps = new ArrayList <NameValuePair>();
        nvps.add(new BasicNameValuePair("__ac_name", ZENOSS_USERNAME));
        nvps.add(new BasicNameValuePair("__ac_password", ZENOSS_PASSWORD));
        nvps.add(new BasicNameValuePair("submitted", "true"));
        nvps.add(new BasicNameValuePair("came_from", ZENOSS_INSTANCE +
                                        "/zport/dmd"));

        httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
        

        try {
            // Response from POST not needed, just the cookie
            HttpResponse response = httpclient.execute(httpost);
            // Consume so we can reuse httpclient
            response.getEntity().consumeContent();
        }
        catch (java.net.ConnectException e){
            logger.fatal("cannot establish connection to Zenoss", e);
            throw e;
        }
        catch (Exception e){
            logger.fatal("Error collecting cookie from Zenoss", e);
            throw e;
        }
        
        
        try {connectionTest();}
        catch (org.apache.http.client.ClientProtocolException e){
            logger.error("Test query to Zenoss failed");
            if (e.getMessage().equals("Moved Temporarily")){
                logger.error("Error \"Moved Temporarily\" was returned. This is usually due to an incorrect username or password");
            }
            throw e;
        }
        logger.info("Connection test to Zenoss successful");
        
    }
    
    
    // Alternate constructor
    public static ZenossInterface createZenossInterfaceJSON(String address, Integer port, String username, String password) throws Exception {
        ZenossInterface zenoss = new ZenossInterface(address,  port,  username, password);
        zenoss.JSONAPI = true;
        return zenoss;
    }
    
    public static ZenossInterface createZenossInterfaceREST(String address, Integer port, String username, String password) throws Exception {
        ZenossInterface zenoss = new ZenossInterface(address,  port,  username, password);
        zenoss.JSONAPI = false;
        return zenoss;
    }
    

    /* routerRequest is the main method used to communicate with the Zenoss instance
     * This is never called directly and should remain private to ensure consistancy.
     * the public interface methods below build up the appropriate router requests.
     */
    private JSONObject routerRequest(String router, String method, HashMap data)
            throws Exception {
        // Construct standard URL for requests
        HttpPost httpost = new HttpPost(ZENOSS_INSTANCE +  "/zport/dmd/" +
                            ROUTERS.get(router) + "_router");
        // Content-type MUST be set to 'application/json'
        httpost.addHeader("Content-type", "application/json; charset=utf-8");

        ArrayList packagedData = new ArrayList();
        packagedData.add(data);

        HashMap reqData = new HashMap();
        reqData.put("action", router);
        reqData.put("method", method);
        reqData.put("data", packagedData);
        reqData.put("type", "rpc");
        // Increment the request count ('tid'). More important if sending multiple
        // calls in a single request
        reqData.put("tid", String.valueOf(this.reqCount++));

        // Set the POST content to be a JSON-serialized version of request data
        httpost.setEntity(new StringEntity(JSONValue.toJSONString(reqData)));

        // Execute the request, and return the JSON-deserialized data
        String response = httpclient.execute(httpost, responseHandler);
        return (JSONObject)jsonParser.parse(response);
    }

    
    private JSONObject connectionTest()
            throws Exception {
        HashMap data = new HashMap();
        data.put("start", 0);
        data.put("limit", 1);
        HashMap params = new HashMap();
        params.put("severity", new ArrayList()
                                   {{ add(5);}});
        params.put("prodState", new ArrayList()
                                   {{ add(1000); }});
        params.put("eventState", new ArrayList()
                                     {{ add(0); add(1); }});
        data.put("params", params);

        return (JSONObject) this.routerRequest("EventsRouter",
                                               "query", data).get("result");
    }
    
    
    
    public JSONObject getEvents(String device, String component, String eventClass)
            throws Exception {
        HashMap data = new HashMap();
        data.put("start", 0);
        data.put("limit", 100);
        data.put("dir", "DESC");
        data.put("sort", "severity");
        HashMap params = new HashMap();
        params.put("severity", new ArrayList()
                                   {{ add(5); add(4); add(3); }});
        params.put("prodState", new ArrayList()
                                   {{ add(1000); }});
        params.put("eventState", new ArrayList()
                                     {{ add(0); add(1); }});

        if (device != null) params.put("device", device);
        if (component != null) params.put("component", component);
        if (eventClass != null) params.put("eventClass", eventClass);
        data.put("params", params);

        return (JSONObject) this.routerRequest("EventsRouter",
                                               "query", data).get("result");
    }
    

    public JSONObject getEvents() throws Exception {
        return getEvents(null, null, null);
    }

    
    /*
     * The create event methods all take a ZenossAlarmProperties object. This is the object that is created by an
     * alarm processing a result set and contains all the details neccessary for triggering or clearing al alarm.
     */
    public String createEvent(ZenossAlarmProperties zap) throws Exception { 
        if (JSONAPI) return createEventJSON(zap);
        else return createEventREST(zap);
    }
    
    public String createEventREST(ZenossAlarmProperties zap) throws Exception { 

        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl(); 
        config.setServerURL(new URL(ZENOSS_PROTOCOL + "://" + ZENOSS_USERNAME + ":" + ZENOSS_PASSWORD + "@" + ZENOSS_HOST + "/zport/dmd/ZenEventManager")); 
        config.setBasicUserName(ZENOSS_USERNAME); 
        config.setBasicPassword(ZENOSS_PASSWORD); 

        XmlRpcClient client = new XmlRpcClient(); 
        client.setConfig(config); 
        
        HashMap<String,Object> params = new HashMap<String,Object>(); 
         
        params.put("device", zap.device); 
        params.put("component", zap.component); 
        params.put("summary", zap.summary); 
        params.put("severity", zap.severity.toString()); 
        params.put("eventClass", zap.eventclass); 
        params.put("message", zap.message);
        params.put("eventKey", "GraphiteZenossBridge_" + zap.ID.toString());
        
        System.out.println("Sending event to Zenoss");
        System.out.println(zap.toString());
        
        try {
          return client.execute("sendEvent", new Object[]{params}).toString(); 
        } catch (org.apache.xmlrpc.client.XmlRpcClientException e) {
            if (e.getMessage().equals("Failed to parse server's response: Unknown type: nil")){
                logger.warn("Clearing alarm \"" + zap.summary + "\" alarm already cleared.");
            }
            return "";
        }
    } 

    
    public String createEventJSON(ZenossAlarmProperties zap) throws Exception {
        HashMap data = new HashMap();
        data.put("device", zap.device);
        data.put("component", zap.component);
        data.put("severity", zap.severity.toString());
        data.put("summary", zap.summary);
        data.put("evclass", zap.eventclass);
        data.put("evclasskey", "GraphiteZenossBridge_" + zap.ID.toString());

        return this.routerRequest("EventsRouter", "add_event", data).toString();
    }
    
    /*
     * Closing an event differs between the JSON and REST APIs. Most significantly with the REST API triggering an alarm
     * at level 0 is equivalent to a close, whereas the REST API has a specific "close" event.
     */
    
    public String closeEvent(ZenossAlarmProperties zap) throws Exception {
        return closeEventREST(zap);
    }
    

    public String closeEventJSON(ZenossAlarmProperties zap) throws Exception {

        HashMap data = new HashMap();
        HashMap params = new HashMap();
        params.put("device", zap.device);
        params.put("component", zap.component);
        params.put("eventClass", zap.eventclass);
        data.put("params", params);

        return this.routerRequest("EventsRouter", "close", data).toString();
    }
    
    public String closeEventREST(ZenossAlarmProperties zap) throws Exception {
        return createEventREST(new ZenossAlarmProperties(0, zap.prodState, zap.device, zap.component, zap.eventclass, zap.summary, zap.ID));
    }

    public JSONObject closeEventsbyDevice(String device, String component, String eventClass) throws Exception {
        final String _device = device;
        HashMap data = new HashMap();
        HashMap params = new HashMap();
        if (device != null) params.put("device", device);
        if (component != null) params.put("component", component);
        if (eventClass != null) params.put("eventClass", eventClass);
        data.put("params", params);

        return this.routerRequest("EventsRouter", "close", data);
    }


    public void close() throws Exception {
        httpclient.getConnectionManager().shutdown();
    }

}