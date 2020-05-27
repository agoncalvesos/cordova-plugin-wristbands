package com.cordova.plugin.wristbands;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;

import com.minew.beaconset.BluetoothState;
import com.minew.beaconset.MinewBeacon;
import com.minew.beaconset.MinewBeaconManager;
import com.minew.beaconset.MinewBeaconManagerListener;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class WristbandsService extends Service {

    private MinewBeaconManager mMinewBeaconManager;
    private boolean isScanning;
    private boolean beaconInRange;

    private String trackedUUID;
    private String trackedMajor;
    private String trackedMinor;
    private String postURL;
    private int timerString;
    private JSONObject returnJSONParameters;

    //Strings to register to create intent filter for registering the recivers
    private static final String ACTION_STRING_ACTIVITY = "ToActivity";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        trackedUUID = preferences.getString("trackedUUID", "");
        trackedMajor = preferences.getString("trackedMajor", "");
        trackedMinor = preferences.getString("trackedMinor", "");
        postURL = preferences.getString("postURL", "");
        timerString = preferences.getInt("timerString", 10);

        mMinewBeaconManager = MinewBeaconManager.getInstance(this);

        setDevice();

        return super.onStartCommand(intent, flags, startId);
    }

    private void setDevice(){

        setDelegate();

        startScan();

        scheduledTimer();
    }

    public void startScan(){
        try {
            mMinewBeaconManager.startScan();
            isScanning = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopScan(){
        if (mMinewBeaconManager != null) {
            mMinewBeaconManager.stopScan();
            isScanning = false;
        }
    }

    private void setDelegate() {
        Log.d("wristband", "setDevice!");

        mMinewBeaconManager.setMinewbeaconManagerListener(new MinewBeaconManagerListener() {

            /**
             *   if the manager find some new beacon, it will call back this method.
             *
             *  @param minewBeacons  new beacons the manager scanned
             */
            @Override
            public void onAppearBeacons(List<MinewBeacon> minewBeacons) {
                Log.d("wristband", "onAppearBeacons" + minewBeacons.size());

                for (MinewBeacon beacon : minewBeacons){
                    String uuid = beacon.getUuid();
                    String major = beacon.getMajor();
                    String minor = beacon.getMinor();


                    if (trackedUUID.equals(uuid) && trackedMajor.equals(major) && trackedMinor.equals(minor)){
                        beaconInRange = true;
                    }
                }
            }

            /**
             *  if a beacon didn't update data in 10 seconds, we think this beacon is out of rang, the manager will call back this method.
             *
             *  @param minewBeacons beacons out of range
             */
            @Override
            public void onDisappearBeacons(List<MinewBeacon> minewBeacons) {
                Log.d("wristband", "onDisappearBeacons" + minewBeacons.size());

                for (MinewBeacon beacon : minewBeacons){

                    String uuid = beacon.getUuid();
                    String major = beacon.getMajor();
                    String minor = beacon.getMinor();


                    if (trackedUUID.equals(uuid) && trackedMajor.equals(major) && trackedMinor.equals(minor)){
                        beaconInRange = false;
                    }
                }
            }

            /**
             *  the manager calls back this method every 1 seconds, you can get all scanned beacons.
             *
             *  @param minewBeacons all scanned beacons
             */
            @Override
            public void onRangeBeacons(final List<MinewBeacon> minewBeacons) {
                Log.d("wristband", "onRangeBeacons" + minewBeacons.size());

                for (MinewBeacon beacon : minewBeacons){

                    String uuid = beacon.getUuid();
                    String major = beacon.getMajor();
                    String minor = beacon.getMinor();


                    if (trackedUUID.equals(uuid) && trackedMajor.equals(major) && trackedMinor.equals(minor)){
                        returnJSONParameters = beaconToJSONObject(beacon);
                        String beaconData = returnJSONParameters.toString();
                        Log.d("wristband", "onRangeBeacons" + beaconData);

                        Intent intent = new Intent(ACTION_STRING_ACTIVITY);
                        intent.putExtra("beaconData",beaconData);
                        sendBroadcast(intent);
                    }
                }
            }

            @Override
            public void onUpdateBluetoothState(BluetoothState state) {
                switch (state) {
                    case BluetoothStatePowerOn:
                        //Toast.makeText(cordova.getContext(), "BluetoothStatePowerOn", Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothStatePowerOff:
                        //Toast.makeText(cordova.getContext(), "BluetoothStatePowerOff", Toast.LENGTH_SHORT).show();
                        beaconInRange = false;
                        break;
                }
            }
        });
    }

    public JSONObject beaconToJSONObject(MinewBeacon beacon) {

        JSONObject jo = new JSONObject();
        try {
            //RSSI
            int rssi = beacon.getRssi();
            jo.put("rssi", rssi);

            //Distance
            double powered = (-59.0f-(double)rssi)/20.0;
            double distance = Math.pow(10, powered);
            jo.put("distance", distance);

            //UUID address
            jo.put("uuid", beacon.getUuid());

            //In Range
            jo.put("range", beaconInRange);

            //mac
            jo.put("mac", beacon.getMacAddress());

            //name
            jo.put("name", beacon.getName());

            //major
            jo.put("major", beacon.getMajor());

            //minor
            jo.put("minor", beacon.getMinor());

            //battery
            jo.put("battery", beacon.getBattery());

            //timestamp
            jo.put("timeStamp", DateFormat.format("dd-MM-yyyy HH:mm:ss", new Date()));



        } catch (Exception e) {

        }

        return jo;
    }

    void scheduledTimer(){
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                JSONObject jo = new JSONObject();
                try {
                    //UUID address
                    jo.put("uuid", trackedUUID);

                    //In Range
                    jo.put("range", beaconInRange);

                    //major
                    jo.put("major", trackedMajor);

                    //minor
                    jo.put("minor", trackedMinor);

                    //timestamp
                    jo.put("timeStamp", DateFormat.format("dd-MM-yyyy HH:mm:ss", new Date()));

                    URL url = new URL(postURL);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    try {
                        urlConnection.setDoOutput(true);
                        urlConnection.setChunkedStreamingMode(0);
                        urlConnection.setRequestProperty("Content-Type", "application/json; utf-8");

                        //OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                        try(OutputStream os = urlConnection.getOutputStream()) {
                            byte[] input = returnJSONParameters.toString(2).getBytes("utf-8");
                            os.write(input, 0, input.length);
                        }

                        try(BufferedReader br = new BufferedReader(
                                new InputStreamReader(urlConnection.getInputStream(), "utf-8"))) {
                            StringBuilder response = new StringBuilder();
                            String responseLine = null;
                            while ((responseLine = br.readLine()) != null) {
                                response.append(responseLine.trim());
                            }
                            System.out.println(response.toString());
                        }

                    } finally {
                        urlConnection.disconnect();
                    }

                } catch (Exception e) {

                }
            }
        }, 0,this.timerString * 1000);//put here time 1000 milliseconds=1 second
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        //unregisterReceiver(serviceReceiver);

        Log.d("WristbandsService", "onDestroy!");
    }
}