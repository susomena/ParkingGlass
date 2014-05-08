package com.susomena.parkingglass;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;

import android.app.Activity;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.android.glass.app.Card;

public class MainActivity extends Activity {
	Location l;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//setContentView(R.layout.activity_main);
		
		LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
		Criteria criteria = new Criteria();
		String provider = manager.getBestProvider(criteria, true);
		l = manager.getLastKnownLocation(provider);
		
		Handler handler = new Handler(){
			@Override
			public void handleMessage(Message msg) {
				int plazas = msg.getData().getInt("plazas");
				String name = msg.getData().getString("name");
				double lat = msg.getData().getDouble("lat");
				double lon = msg.getData().getDouble("lon");
				
				Card card = new Card(MainActivity.this);
				card.setText(name);
				card.setFootnote("Plazas libres: "+plazas);
				setContentView(card.getView());
				
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setData(Uri.parse("google.navigation:q="+lat+","+lon));
				startActivity(intent);
			}
		};
		
		HTTPThread t = new HTTPThread(handler);
		t.start();
	}

	class HTTPThread extends Thread {
		Handler handler;

		public HTTPThread(Handler h) {
			handler = h;
		}

		public void run() {
			HttpClient httpclient = new DefaultHttpClient();
			HttpGet httpget = new HttpGet(
					"http://gcmena.hol.es/AutomaticApiRest/getData.php?f=json&t=location");

			try {
				HttpResponse response = httpclient.execute(httpget);

				if (response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_OK) {
					BufferedReader reader = new BufferedReader(
							new InputStreamReader(response.getEntity()
									.getContent()));
					String source = "";
					String line;

					while ((line = reader.readLine()) != null) {
						source += line;
					}

					JSONArray array = new JSONArray(source);
					
					double lat = array.getJSONObject(0).getDouble("lat");
					double lon = array.getJSONObject(0).getDouble("lon");
					
					double d = Math.sqrt((l.getLatitude()-lat)*(l.getLatitude()-lat)+((l.getLongitude()-lon)*(l.getLongitude()-lon)));
					
					int n = 0;
					
					for(int i=1; i<array.length(); i++){
						if(Math.sqrt((l.getLatitude()-lat)*(l.getLatitude()-lat)+((l.getLongitude()-lon)*(l.getLongitude()-lon)))<d){
							d = Math.sqrt((l.getLatitude()-lat)*(l.getLatitude()-lat)+((l.getLongitude()-lon)*(l.getLongitude()-lon)));
							n = i;
						}
					}
					
					String name = array.getJSONObject(n).getString("name");
					
					httpget = new HttpGet(
							"http://trafico.alicante.es/es/trafico/parking.asp?idparking="+(n+1));
					
					response = httpclient.execute(httpget);
					
					if (response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_OK) {
						reader = new BufferedReader(
								new InputStreamReader(response.getEntity()
										.getContent()));
						source = "";

						while ((line = reader.readLine()) != null) {
							source += line;
						}
						
						int k = source.indexOf("dato_numero_g");
						k = source.indexOf(">", k);
						int k2 = source.indexOf("<", k);
						String p = source.substring(k+1, k2);
						int plazas = Integer.parseInt(p);
						
						Message msg = handler.obtainMessage();
						Bundle data = new Bundle();
						data.putInt("plazas", plazas);
						data.putString("name", name);
						data.putDouble("lat", array.getJSONObject(n).getDouble("lat"));
						data.putDouble("lon", array.getJSONObject(n).getDouble("lon"));
						msg.setData(data);
						handler.sendMessage(msg);
					}
				}
			} catch (Exception e) {
				Log.e("Error http", "");
				e.printStackTrace();
			}
		}
	}
}
