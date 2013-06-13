package com.datasift.operations.alarmmanager;


import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpEntity;
import java.net.URL;


import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

/*
 * GraphiteInterface implements the graphite REST API to return data in json format.
 */

public class GraphiteInterface {
    private static String graphitehost;
    private static String username;
    private static String password;
    private static int graphiteport;
    DefaultHttpClient httpclient;
    
    public GraphiteInterface (String _graphitehost, int _graphiteport, String _username, String _password) throws Exception{
        if (!_graphitehost.startsWith("http")) _graphitehost = "https://" + _graphitehost;
        graphitehost = _graphitehost;
        username = _username;
        password = _password;
        httpclient = new DefaultHttpClient();
        
        httpclient.getCredentialsProvider().setCredentials(
                    new AuthScope(new URL(graphitehost).getHost(), _graphiteport),
                    new UsernamePasswordCredentials(username, password));
        ConnectionTest();
    }
    
    private void ConnectionTest() throws Exception{
        HttpResponse response1 = getData("/render?format=json");
        if (response1.getStatusLine().getStatusCode() == 200){
            Logger.writeline("Connection test to graphite successful");
        } else {
            Logger.writewarn("Did not recieve a 200 HTTP response from graphite during connection test");
            Logger.writeline(response1.getStatusLine());
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
            HttpResponse response1 = getData(query);
            if (response1.getStatusLine().getStatusCode() != 200) throw  new org.apache.http.client.HttpResponseException(response1.getStatusLine().getStatusCode(), response1.getStatusLine().toString());
            HttpEntity entity1 = response1.getEntity();
            responsestring = EntityUtils.toString(entity1);
            jsonresponse = (JSONArray)parser.parse(responsestring);
        } catch (org.apache.http.client.HttpResponseException e) {
            Logger.writeerror("Grapite returned a non-zero code:", e);
            throw e;
        } catch (Exception e)
        {
            Logger.writeerror("cannot parse Graphite HTTP response into JSON:\n", e);
            Logger.writeerror("response from Graphite: " + responsestring);
            throw e;
        }
        
        
        //System.out.println(jsonresponse);
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
            Logger.writeerror("Username or password incorrect for graphite. Quitting");
            Logger.writeline("HTTP request response:");
            Logger.writeline(responsestring);
            throw new Exception("Graphite username or password incorrect");
            } 
            
        } catch (java.net.UnknownHostException e){
            Logger.writeerror("Unable to resolve hostname \"" + graphitehost + "\". Quitting");
            throw e;
        } catch (org.apache.http.conn.HttpHostConnectException e){
            Logger.writeerror("Graphite server address unreachable: \"" + graphitehost + "\". Quitting");
            throw e;
        }
        return response1;
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
        return cleanURL;
    }
}
