/*******************************************************************************
 * Copyright (c) 2011 ETH Zurich.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Paolo Carta - Implementation
 *     Theus Hossmann - Implementation
 *     Dominik Schatzmann - Message specification
 ******************************************************************************/

package ch.ethz.twimight.net.tds;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.util.Log;
import ch.ethz.twimight.security.RevocationListEntry;
import ch.ethz.twimight.util.Constants;

/**
 * API for communication with the Twimight Disaster Server
 * @author thossmann
 *
 */
public class TDSCommunication {
	// the object names in requests and responses
	private static final String MESSAGE = "message";
	private static final String CERTIFICATE = "certificate";
	private static final String LOCATION = "location";
	private static final String BLUETOOTH = "bluetooth";
	private static final String AUTHENTICATION = "authentication";
	private static final String VERSION = "version";
	private static final String REVOCATION = "revocation";
	private static final String FOLLOWER = "follower";	
	private static final String STATISTIC = "statistic";	
	private static final String NOTIFICATION = "notification";
	private static final String BUGS = "bugs";
	
	
	
	
	private static final String TAG = "TDSCommunication";
	private TDSRequestMessage tdsRequest;
	private TDSResponseMessage tdsResponse;
	
	/**
	 * In the constructor we create the request message and populate it with the mandatory objects
	 * @throws JSONException 
	 */
	public TDSCommunication(Context context, int consumerId, String oauthAccessToken, String oauthAccessTokenSecret) throws JSONException{
		tdsRequest = new TDSRequestMessage(context);
		tdsRequest.createAuthenticationObject(consumerId, oauthAccessToken, oauthAccessTokenSecret);
		
		tdsResponse = new TDSResponseMessage(context);
	}
	
	/**
	 * Creates a new Bluetooth object in the reqeust
	 * @return
	 * @throws JSONException
	 */
	public int createBluetoothObject(String mac) throws JSONException{
		tdsRequest.createBluetoothObject(mac);
		return 0;
	}
	
	/**
	 * Creates a new Bug object in the request
	 * @return
	 * @throws JSONException
	 */
	public int createBugObject(String description, int type) throws JSONException{
		tdsRequest.createBugObject(description, type);
		return 0;
	}
	
	/**
	 * Creates a new certiricate object in the request
	 * @return
	 * @throws JSONException
	 */
	public int createCertificateObject(KeyPair toSign, KeyPair toRevoke) throws Exception{
		tdsRequest.createCertificateObject(toSign, toRevoke);
		return 0;
	}
	
	/**
	 * Creates a new location object in the request
	 * @return
	 * @throws JSONException 
	 */
	public int createLocationObject(ArrayList<Location> locationList) throws Exception{
		tdsRequest.createLocationObject(locationList);
		return 0;
	}
	
	/**
	 * Creates a new revocation object in the request
	 * @return
	 * @throws JSONException 
	 */
	public int createRevocationObject(int currentVersion) throws Exception{
		tdsRequest.createRevocationObject(currentVersion);
		return 0;
	}
	
	/**
	 * Creates a new follower object in the request
	 * @return
	 * @throws JSONException 
	 */
	public int createFollowerObject(long lastUpdate) throws Exception{
		tdsRequest.createFollowerObject(lastUpdate);
		return 0;
	}
	
	/**
	 * Creates a new Statistics object in the request
	 * @return
	 * @throws JSONException 
	 */
	public int createStatisticObject(Cursor stats, long follCount) throws Exception{
		tdsRequest.createStatisticObject(stats,follCount);
		return 0;
	}
	
	/**
	 * Sends the request to the Twimight disaster server. Blocking!
	 * @return
	 */
	public boolean sendRequest(HttpClient client, String url){
		
		// check the parameters
		if(client==null) return false;
		
		// first we fetch the request object
		JSONObject requestObject;
		try {
			requestObject = assembleRequest();
		} catch (Exception e) {
			Log.e(TAG, "JSON exception while assembling request!");
			return false;
		}
		
		// do we have a request object?
		if(requestObject== null) return false;
		

		// create the HTTP request
		HttpPost post = new HttpPost(url);

		// now we serialize the JSON Object into the post request
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(MESSAGE,requestObject.toString()));

		UrlEncodedFormEntity ent = null;
		try {
			ent = new UrlEncodedFormEntity(params);
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG,"Unsupported Encoding Exception!",e);
			return false;
		}

		post.setEntity(ent);			

		// and make the actual request!
		HttpResponse response = null;
		try {
			response = client.execute(post);
		} catch (ClientProtocolException e) {
			Log.d(TAG,"HTTP POST request failed! " + e.toString());
			return false;
		} catch (IOException e) {
			Log.d(TAG,"HTTP POST request failed!" + e.toString());
			return false;
		}	

		// read the response
		HttpEntity resEntity = response.getEntity();
		
		if (resEntity != null) {		
			
			String result = null;
			try {
				result = EntityUtils.toString(resEntity);
			} catch (ParseException e) {
				Log.i(TAG,"Parse Error while parsing response!" + e.toString());
				return false;
			} catch (IOException e) {
				Log.i(TAG,"IO Error while parsing response!" + e.toString());
				return false;
			}
			Log.i(TAG,"result = " + result);

			try {
				if(disassembleResponse(result) != 0){
					Log.i(TAG, "Error while parsing result");
				}
				
			} catch (Exception e) {
				Log.i(TAG,"JSON Error while parsing result!" + e.toString());
				return false;
			}					
		} else 
			return false;
		
		return true;
		
	}
	
	/**
	 * Assemples the request to one big JSON Object. Returns null if mandatory fields are missing.
	 * @throws JSONException 
	 */
	private JSONObject assembleRequest() throws Exception{
		JSONObject requestObject = new JSONObject();
		
		// first, we add the version
		if(tdsRequest.hasVersion()){
			requestObject.put(VERSION, tdsRequest.getVersion());
		} else {
			return null;
		}
		
		// the authentication
		if(tdsRequest.hasAuthenticationObject()){
			requestObject.put(AUTHENTICATION, tdsRequest.getAuthenticationObject());
		} else {
			return null;
		}
		
		// bug
		if(tdsRequest.hasBugObject()){
			requestObject.put(BUGS, tdsRequest.getBugObject());
		}

		// bluetooth
		if(tdsRequest.hasBluetoothObject()){
			requestObject.put(BLUETOOTH, tdsRequest.getBluetoothObject());
		}

		// location
		if(tdsRequest.hasLocationObject()){
			requestObject.put(LOCATION, tdsRequest.getLocationObject());
		}

		// certificate
		if(tdsRequest.hasCertificatObject()){
			requestObject.put(CERTIFICATE, tdsRequest.getCertificateObject());
		}

		// revocation
		if(tdsRequest.hasRevocationObject()){
			requestObject.put(REVOCATION, tdsRequest.getRevocationObject());
		}

		// follower
		if(tdsRequest.hasFollowerObject()){
			requestObject.put(FOLLOWER, tdsRequest.getFollowerObject());
		}
		
		// statistics
		if(tdsRequest.hasStatisticObject()){
			requestObject.put(STATISTIC, tdsRequest.getStatisticObject());
		}

		Log.i(TAG, requestObject.toString(5));
		return requestObject;
	}
	
	private int disassembleResponse(String responseString) throws Exception{
		
		JSONObject responseObject = new JSONObject(responseString);
		JSONObject messageObject = responseObject.getJSONObject(MESSAGE);
		
		if(messageObject == null) return -1;
		
		try{
			// bug
			String status = messageObject.getString("status");
			tdsResponse.setBugResponseString(status);
			return 0;
		} catch(JSONException e) {
			Log.i(TAG, "error mapping bug response");
		}
		
		// version
		int responseVersion = messageObject.getInt("version"); 
		if(responseVersion != tdsResponse.getVersion()){
			Log.e(TAG, "TDS message version mismatch!");
			return -1;
		}
		
		// authentication
		JSONObject authenticationObject = messageObject.getJSONObject(AUTHENTICATION);
		if(authenticationObject != null){
			tdsResponse.setAuthenticationObject(authenticationObject);
		} else {
			Log.e(TAG, "Authentication failed");
			return -1;
		}
		
		try{
			// bluetooth
			JSONObject bluetoothObject = messageObject.getJSONObject(BLUETOOTH);
			tdsResponse.setBluetoothObject(bluetoothObject);
		} catch(JSONException e) {
			Log.i(TAG, "No Bluetooth object");
		}
		
		try{
			// location
			JSONObject locationObject = messageObject.getJSONObject(LOCATION);
			tdsResponse.setLocationObject(locationObject);
		} catch(JSONException e){
			Log.i(TAG, "No location object");
		}

		try{
			// certificate
			JSONObject certificateObject = messageObject.getJSONObject(CERTIFICATE);
			tdsResponse.setCertificateObject(certificateObject);
		} catch(JSONException e){
			Log.i(TAG, "No certificate object");
		}

		try{
			// revocation
			JSONObject revocationObject = messageObject.getJSONObject(REVOCATION);
			tdsResponse.setRevocationObject(revocationObject);
		} catch(JSONException e){
			Log.i(TAG, "No revocation object");
		}

		try{
			// follower
			JSONObject followerObject = messageObject.getJSONObject(FOLLOWER);
			tdsResponse.setFollowerObject(followerObject);
		} catch(JSONException e){
			Log.i(TAG, "No follower object");
		}
		
		try{
			// notification
			JSONObject notificationObject = messageObject.getJSONObject(NOTIFICATION);
			tdsResponse.setNotificationObject(notificationObject);
		} catch(JSONException e){
			Log.i(TAG, "No follower object");
		}

		return 0;
	}
	
	public String parseAuthentication() throws Exception{
		return tdsResponse.parseAuthentication();
	}
	
	public List<String> parseBluetooth() throws Exception{
		return tdsResponse.parseBluetooth();
	}
	
	public JSONObject getNotification() throws Exception{
		return tdsResponse.getNotification();
	}
	
	public String getBugResponseString() throws Exception{
		return tdsResponse.getBugResponseString();
	}
	
	public int parseLocation() throws Exception{
		return tdsResponse.parseLocation();
	}
	
	public String parseCertificate() throws Exception{
		return tdsResponse.parseCertificate();
	}

	public int parseCertificateStatus() throws Exception{
		return tdsResponse.parseCertificateStatus();
	}

	public List<RevocationListEntry> parseRevocation() throws Exception{
		return tdsResponse.parseRevocation();
	}

	public int parseRevocationVersion() throws Exception{
		return tdsResponse.parseRevocationVersion();
	}

	public List<TDSPublicKey> parseFollower() throws Exception{
		return tdsResponse.parseFollower();
	}


	public long parseFollowerLastUpdate() throws Exception{
		return tdsResponse.parseFollowerLastUpdate();
	}


	
}
