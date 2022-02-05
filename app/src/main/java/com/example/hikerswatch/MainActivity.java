package com.example.hikerswatch;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {

    LocationManager locationManager;
    LocationListener locationListener;
    Location currentLocation = null;
    Address currentAddress = null;
    Geocoder geocoder;
    String weatherString = null;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //run when a 'request permission' action has completed.

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        //if there are results and if the first result says a permission was granted...
        if (grantResults.length >0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            //check that the specific permission 'ACCESS_FINE_LOCATION' is granted (either now or previously)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locationListener);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                //run this when a location update is received
                Log.i("location",location.toString());
                currentLocation = location;

                try {
                    List<Address> addressList = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addressList != null && addressList.size() > 0) {
                        //if addressList.get(0).toString()
                        currentAddress = addressList.get(0);
                        Log.i("Address", currentAddress.toString());
                        String JSONurl = "http://api.openweathermap.org/data/2.5/weather?lat=" + currentAddress.getLatitude() + "&lon=" + currentAddress.getLongitude() + "&appid=4ac1986ba1e7e17b0e86a43cc13ded0f";
                        getWebContent(JSONurl);
                        updateDisplay();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            //if we don't already have permission to 'ACCESS_FINE_LOCATION', request it.
            ActivityCompat.requestPermissions(this,new String[] {Manifest.permission.ACCESS_FINE_LOCATION},1);
        }else{
            //if we do already have permission to 'ACCESS_FINE_LOCATION', take the next step: requesting location updates.
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
    }

    private void updateDisplay(){
        String outputText = "";
        outputText += String.format("Latitude: %.6f", currentLocation.getLatitude()) + "\n\n";
        outputText += String.format("Longitude: %.6f", currentLocation.getLongitude()) + "\n\n";
        outputText += String.format("Accuracy: %.1f", currentLocation.getAccuracy()) + " m\n\n";
        outputText += String.format("Altitude: %.1f", currentLocation.getAltitude())+" m";
        outputText += "\n\nAddress:\n" + ((currentAddress != null) ? currentAddress.getAddressLine(0) : "Not found.");

        if (weatherString != null){
            outputText += "\n\nWeather:\n" + weatherString;
        }

        TextView outputView = findViewById(R.id.outputView);
        outputView.setText(outputText);
    }


    private void getWebContent(String url){
        GetWebContentTask task = new GetWebContentTask();
        String result = null;
        try {
            task.execute(url);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class GetWebContentTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {

            String result = "";
            URL url;

            try {
                url = new URL(urls[0]);
                InputStream in;
                if (urls[0].contains("https")) {
                    HttpsURLConnection urlConnection = null;
                    urlConnection = (HttpsURLConnection) url.openConnection();
                    in = urlConnection.getInputStream();
                }else {
                    HttpURLConnection urlConnection = null;
                    urlConnection = (HttpURLConnection) url.openConnection();
                    in = urlConnection.getInputStream();
                }

                Log.i("Extracting JSON",url.toString());
                long startTime = System.currentTimeMillis();

                BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                int data = reader.read();
                StringBuilder sb = new StringBuilder("");
                while (data != -1) {
                    sb.append((char) data);
                    data = reader.read();
                }
                result = sb.toString();

                long endTime = System.currentTimeMillis();
                Log.i("Content Extraction time",String.format("%d ms",(endTime-startTime)));

                reader.close();

                return result;
            } catch(FileNotFoundException e){
                return "Failed: Unknown City";
            }catch (Exception e) {
                e.printStackTrace();
                return "Failed";
            }
        }

        protected void onPostExecute(String webContent){
            try {
                System.out.println(webContent);
                String targetText = "";
                if (webContent.contains("Failed")){
                    //do nothing
                }else {
                    JSONObject theJSON = new JSONObject(webContent);

                    JSONObject mainObject = theJSON.getJSONObject("main");
                    double tempKelvin = mainObject.getDouble("temp");
                    double tempC = tempKelvin - 273.15;
                    targetText = String.format("%.1f", tempC) + "°C\n";
                    //Log.i("targetText", targetText);

                    JSONArray weatherData = theJSON.getJSONArray("weather");

                    for (int i = 0; i < weatherData.length(); i++) {
                        JSONObject thisObservation = weatherData.getJSONObject(i);
                        if (i > 0) targetText += "\n";
                        targetText += thisObservation.getString("main");//+": "+thisObservation.getString("description");
                    }
                    weatherString = targetText;
                    updateDisplay();
                }
                //outTextView.setText(targetText);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }


}
