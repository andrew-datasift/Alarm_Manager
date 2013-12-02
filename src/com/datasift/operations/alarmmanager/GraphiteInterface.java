package com.datasift.operations.alarmmanager;


import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpEntity;
import java.net.URL;
import org.apache.log4j.Logger;

import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

/*
 * GraphiteInterface implements the graphite REST API to return data in json format.
 */

public class GraphiteInterface {
    private static Logger logger = Logger.getLogger("AlarmManager.GraphiteInterface");
    private static String graphitehost;
    private static String username;
    private static String password;
    private static int graphiteport;
    DefaultHttpClient httpclient;
    
    public GraphiteInterface (String _graphitehost, int _graphiteport, String _username, String _password) throws Exception{
        if (!_graphitehost.startsWith("http")) _graphitehost = "https://" + _graphitehost;
        graphitehost = _graphitehost + ":" + _graphiteport;
        username = _username;
        password = _password;
        httpclient = new DefaultHttpClient();
        
        if (!username.equals("") && !password.equals("")){
            
        httpclient.getCredentialsProvider().setCredentials(
                    new AuthScope(new URL(graphitehost).getHost(), _graphiteport),
                    new UsernamePasswordCredentials(username, password));
        }
        ConnectionTest();
    }
    
    private void ConnectionTest() throws Exception{
        HttpResponse response1 = getData("/render?format=json");
        if (response1.getStatusLine().getStatusCode() == 200){
            logger.info("Connection test to graphite successful");
        } else {
            logger.error("Did not recieve a 200 HTTP response from graphite during connection test");
            logger.error(response1.getStatusLine());
        }
    }
    
    /*
     * getJson is the method called buy the alarm manager to retrieve the data for all the alarms.
     * It makes the request to graphite and returns the results as a JSON object.
     * Calls getData to make the http request.
     */
    
    public JSONArray getJson (String query) throws Exception{
            
        JSONParser parser = new JSONParser();
        JSONArray jsonresponse = new JSONArray();
        String responsestring = "";
        try {
            responsestring = getResponseString(query);
            jsonresponse = (JSONArray)parser.parse(responsestring);
        } catch (org.apache.http.client.HttpResponseException e) {
            throw e;
        } catch (Exception e)
        {
            logger.error("cannot parse Graphite HTTP response into JSON:\n", e);
            logger.error("response from Graphite: " + responsestring);
            throw e;
        }
        return jsonresponse;
    }
    
    /*
     * getData makes an HTTP request to the graphite server and returns the entire response object.
     */

    public HttpResponse getData (String _query) throws Exception{
        String query = cleanupURL(_query);
        HttpResponse response1;
        try {
            HttpGet httpGet = new HttpGet(graphitehost + query);
            response1 = httpclient.execute(httpGet);
            httpGet.releaseConnection();
            
            if (response1.getStatusLine().getStatusCode() == 401){
            String responsestring = EntityUtils.toString(response1.getEntity());
            logger.fatal("Username or password incorrect for graphite. Quitting");
            logger.error("HTTP request response:");
            logger.error(responsestring);
            throw new Exception("Graphite username or password incorrect");
            } 
            
        } catch (java.net.UnknownHostException e){
            logger.fatal("Unable to resolve hostname \"" + graphitehost + "\". Quitting", e);
            throw e;
        } catch (org.apache.http.conn.HttpHostConnectException e){
            logger.fatal("Graphite server address unreachable: \"" + graphitehost + "\". Quitting", e);
            throw e;
        }
        return response1;
    }
    
    
        public String getResponseString (String _query) throws Exception{
        String query = cleanupURL(_query);
        HttpResponse response1;
        String httpresponse = "";
        try {
            HttpGet httpGet = new HttpGet(graphitehost + query);

            response1 = httpclient.execute(httpGet);
            HttpEntity entity1 = response1.getEntity();
            httpresponse = EntityUtils.toString(entity1);

            httpGet.releaseConnection();

            
            if (response1.getStatusLine().getStatusCode() == 401){
            String responsestring = EntityUtils.toString(response1.getEntity());
            logger.fatal("Username or password incorrect for graphite. Quitting");
            logger.error("HTTP request response:");
            logger.error(responsestring);
            throw new Exception("Graphite username or password incorrect");
            } 
            
            // TODO: If graphite returns a non-200 code, eg for unexpected error, then try again once before sending alarm.
            
            /*
             * Graphite can, if overloaded, return an error code for a valid response, which will then trigger an error to zenoss
             * If we get a non-zero error the request is sent again, then only if we get a fail twice will the error be triggered.
             */

            if (response1.getStatusLine().getStatusCode() != 200){
            logger.warn("Graphite returned a non-zero response code: " + response1.getStatusLine().getStatusCode() + ". Resending request.");
            response1 = httpclient.execute(httpGet);
            entity1 = response1.getEntity();
            httpresponse = EntityUtils.toString(entity1);
            httpGet.releaseConnection();
            } 
            
            if (response1.getStatusLine().getStatusCode() != 200){
                throw new org.apache.http.client.HttpResponseException(response1.getStatusLine().getStatusCode(), response1.getStatusLine().toString());
            }
        } catch (java.net.UnknownHostException e){
            logger.fatal("Unable to resolve hostname \"" + graphitehost + "\". Quitting", e);
            throw e;
        } catch (org.apache.http.conn.HttpHostConnectException e){
            logger.fatal("Graphite server address unreachable: \"" + graphitehost + "\". Quitting", e);
            throw e;
        }
        return httpresponse;
    }

    
    /*
     * Cleanup URL is neccessary because the apache HTTP library used here does not handle special characters
     * well in outgoing requests. Thus the ascii code is used for each of the characters below.
     */
    
    private String cleanupURL(String dirtyURL){
        String cleanURL = dirtyURL;
        cleanURL = cleanURL.replace("\"","%22");
        cleanURL = cleanURL.replace(",","%2c");
        cleanURL = cleanURL.replace("^","%5e");
        cleanURL = cleanURL.replace("(","%28");
        cleanURL = cleanURL.replace(")","%29");
        cleanURL = cleanURL.replace("{","%7b");
        cleanURL = cleanURL.replace("}","%7d");
        cleanURL = cleanURL.replace(" ","");
        return cleanURL;
    }
}
