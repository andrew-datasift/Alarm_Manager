/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.datasift.operations.alarmmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.net.URL;
import java.util.Date;

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

/**
 *
 * @author andrewm
 */
public class PagerDuty {
    
        private DefaultHttpClient httpclient = new DefaultHttpClient();
        private ResponseHandler<String> responseHandler = new BasicResponseHandler();
        private JSONParser jsonParser = new JSONParser();
        private String eventsurl = "https://events.pagerduty.com/generic/2010-04-15/create_event.json";
        private String service_key;
        private long lasttime = 0;
        
        public PagerDuty(String url, String key){
            eventsurl = url;
            service_key = key;
            
        }
    
        public JSONObject sendEvent(String message)
            throws Exception {
            
        // Was the last pager event less than 5 minutes ago. If no skip, else send event.
        if ( (lasttime + 600000) > System.currentTimeMillis()) return (JSONObject)jsonParser.parse("{\"status\":\"Not_sent\",\"message\":\"Less than 10 minutes since last pagerduty notification\"}");
        
        // Construct standard URL for requests
        HttpPost httpost = new HttpPost(eventsurl);
        // Content-type MUST be set to 'application/json'
        httpost.addHeader("Content-type", "application/json; charset=utf-8");


        HashMap reqData = new HashMap();
        reqData.put("service_key", service_key);
        reqData.put("event_type", "trigger");
        reqData.put("description", message);
        reqData.put("type", "rpc");


        // Set the POST content to be a JSON-serialized version of request data
        httpost.setEntity(new StringEntity(JSONValue.toJSONString(reqData)));

        // Execute the request, and return the JSON-deserialized data
        String response = httpclient.execute(httpost, responseHandler);

        lasttime = System.currentTimeMillis();
        
        return (JSONObject)jsonParser.parse(response);
    }
        
        
    public JSONObject resolveEvent(String message)
            throws Exception {
        // Construct standard URL for requests
        HttpPost httpost = new HttpPost(eventsurl);
        // Content-type MUST be set to 'application/json'
        httpost.addHeader("Content-type", "application/json; charset=utf-8");


        HashMap reqData = new HashMap();
        reqData.put("service_key", service_key);
        reqData.put("event_type", "resolve");
        reqData.put("incident_key", "ced0a365bf9446c2b19fd2c708a1985f");
        reqData.put("description", message);
        reqData.put("type", "rpc");


        // Set the POST content to be a JSON-serialized version of request data
        httpost.setEntity(new StringEntity(JSONValue.toJSONString(reqData)));

        // Execute the request, and return the JSON-deserialized data
        String response = httpclient.execute(httpost, responseHandler);
        return (JSONObject)jsonParser.parse(response);
    }
    
}


