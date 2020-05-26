package com.cordova.plugin.wristbands;

import com.minew.beacon.BeaconValueIndex;
import com.minew.beacon.BluetoothState;
import com.minew.beacon.MinewBeacon;
import com.minew.beacon.MinewBeaconManager;
import com.minew.beacon.MinewBeaconManagerListener;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * This class echoes a string called from JavaScript.
 */
public class WristbandsPlugin extends CordovaPlugin {

    private MinewBeaconManager mMinewBeaconManager;
    private boolean isScanning;
    private boolean beaconInRange;

    private static final int PERMISSIONS_REQUEST_FINE_LOCATION = 1;
    private static final int PERMISSIONS_REQUEST_BLUETOOTH = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static CallbackContext callbackContext;

    private String wristbandModel;
    private String trackedUUID;
    private String trackedMajor;
    private String trackedMinor;
    private String wristbandCommand;
    private String postURL;
    private int timerString;
    private JSONObject returnJSONParameters;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        if (action.equals("setDevice")) {
            cordova.setActivityResultCallback(this);

            //Checking if parameters are valid
            //Beacon Model
            wristbandModel =  args.getString(0);
            if (wristbandModel == null || wristbandModel.isEmpty()){
                sendError("wristBandModel input parameter cannot be empty");
            }

            //UUID
            trackedUUID =  args.getString(1);
            if (trackedUUID == null || trackedUUID.isEmpty()){
                sendError("trackedUUID input parameter cannot be empty");
            }

            //Major
            trackedMajor =  args.getString(2);
            if (trackedMajor == null || trackedMajor.isEmpty()){
                sendError("trackedMajor input parameter cannot be empty");
            }

            //Minor
            trackedMinor =  args.getString(3);
            if (trackedMinor == null || trackedMinor.isEmpty()){
                sendError("trackedMinor input parameter cannot be empty");
            }

            //Command
            wristbandCommand =  args.getString(4);
            if (trackedMinor == null || wristbandCommand.isEmpty()){
                sendError("wristbandCommand input parameter cannot be empty");
            }

            //URL
            postURL =  args.getString(5);
            if (postURL == null || postURL.isEmpty()){
                sendError("postURL input parameter cannot be empty");
            }

            //Timer
            timerString =  args.getInt(6);
            if (timerString == 0){
                sendError("timerString input parameter should be > 0");
            }

            //Let's make it run...
            if (wristbandCommand.equals("init")){
                setDevice();
            }
            else if (wristbandCommand.equals("start")){
                startScan();
            } else if (wristbandCommand.equals("stop")){
                stopScan();
            }
            return true;
        }
        return false;
    }

    private void setDevice(){
        if (!hasLocationPermissions() || !hasBluetoothPermissions() )
            requestNeededPermissions();
        else {
            initManager();

            setDelegate();

            startScan();

            scheduledTimer();
        }
    }

    private void initManager() {
        if (mMinewBeaconManager != null) {
            checkBluetooth();
        }

        mMinewBeaconManager = MinewBeaconManager.getInstance(cordova.getContext());
    }

    private void startScan(){
        try {
            mMinewBeaconManager.startScan();
            isScanning = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopScan(){
        if (mMinewBeaconManager != null) {
            mMinewBeaconManager.stopScan();
            isScanning = false;
        }
    }

    private void setDelegate() {
        Log.d("wristband", "setDevice!");
        mMinewBeaconManager.setDeviceManagerDelegateListener(new MinewBeaconManagerListener() {
            /**
             *   if the manager find some new beacon, it will call back this method.
             *
             *  @param minewBeacons  new beacons the manager scanned
             */
            @Override
            public void onAppearBeacons(List<MinewBeacon> minewBeacons) {
                Log.d("wristband", "onAppearBeacons" + minewBeacons.size());

                for (MinewBeacon beacon : minewBeacons){

                    String uuid = beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_UUID).getStringValue();
                    String major = beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_Major).getStringValue();
                    String minor = beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_Minor).getStringValue();


                    if (trackedUUID.equals(uuid) && trackedMajor.equals(major) && trackedMinor.equals(minor)){
                        beaconInRange = true;
                    }
                }
                //TODO
                //if ([trackedUUID isEqualToString:uuid] && [trackedMajor isEqualToString:major] && [trackedMinor isEqualToString:minor]){
                //    beaconInRange = YES;
                //}

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

                    String uuid = beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_UUID).getStringValue();
                    String major = beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_Major).getStringValue();
                    String minor = beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_Minor).getStringValue();


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

                    String uuid = beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_UUID).getStringValue();
                    String major = beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_Major).getStringValue();
                    String minor = beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_Minor).getStringValue();


                    if (trackedUUID.equals(uuid) && trackedMajor.equals(major) && trackedMinor.equals(minor)){
                        returnJSONParameters = beaconToJSONObject(beacon);
                        String beaconData = returnJSONParameters.toString();
                        Log.d("wristband", "onRangeBeacons" + beaconData);
                        sendSuccess(beaconData);
                    }
                }
            }

                /**
                 *  the manager calls back this method when BluetoothStateChanged.
                 *
                 *  @param state BluetoothState
                 */
            @Override
            public void onUpdateState(BluetoothState state) {
                switch (state) {
                    case BluetoothStatePowerOn:
                        Toast.makeText(cordova.getContext(), "BluetoothStatePowerOn", Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothStatePowerOff:
                        Toast.makeText(cordova.getContext(), "BluetoothStatePowerOff", Toast.LENGTH_SHORT).show();
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
            int rssi = beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_RSSI).getIntValue();
            jo.put("rssi", rssi);

            //Distance
            double powered = (-59.0f-(double)rssi)/20.0;
            double distance = Math.pow(10, powered);
            jo.put("distance", distance);

            //UUID address
            jo.put("uuid", beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_UUID).getStringValue());

            //In Range
            jo.put("range", beaconInRange);

            //mac
            jo.put("mac", beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_MAC).getStringValue());

            //name
            jo.put("name", beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_Name).getStringValue());

            //major
            jo.put("major", beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_Major).getStringValue());

            //minor
            jo.put("minor", beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_Minor).getStringValue());

            //battery
            jo.put("battery", beacon.getBeaconValue(BeaconValueIndex.MinewBeaconValueIndex_BatteryLevel).getIntValue());

            //timestamp
            jo.put("timeStamp", DateFormat.format("dd-MM-yyyy HH:mm:ss", new Date()));



        } catch (Exception e) {

        }

        return jo;
    }

    private void sendError(String message) {
        callbackContext.error(message);

    }

    private void sendSuccess(String message) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, message);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
        //callbackContext.success(message);
    }

    private void showBLEDialog() {
        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        cordova.getActivity().startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (isScanning) {
            mMinewBeaconManager.stopScan();
        }
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

    /**
     * Permissions
     *
     * Since Android M (API 23 - 6.0) to use beacons we need to access user location
     * https://developer.android.com/guide/topics/connectivity/bluetooth#Permissions
     * @return
     */

    private void checkBluetooth() {
        BluetoothState bluetoothState = mMinewBeaconManager.checkBluetoothState();
        switch (bluetoothState) {
            case BluetoothStateNotSupported:
                sendError("Bluetooth state not supported");
                break;
            case BluetoothStatePowerOff:
                showBLEDialog();
                break;
            case BluetoothStatePowerOn:
                break;
        }
    }

    private boolean hasLocationPermissions(){

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            return cordova.getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    private boolean hasBluetoothPermissions(){

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            return cordova.getActivity().checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    private void requestNeededPermissions(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            if (!hasLocationPermissions()) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getContext());
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener((DialogInterface dialog) -> {
                    cordova.requestPermissions(this, PERMISSIONS_REQUEST_FINE_LOCATION, new String[]{Manifest.permission.ACCESS_FINE_LOCATION});
                });
                builder.show();
            } else if (!hasBluetoothPermissions()){
                final AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getContext());
                builder.setTitle("This app needs bluetooth access");
                builder.setMessage("Please grant bluetooth access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener((DialogInterface dialog) -> {
                    cordova.requestPermissions(this, PERMISSIONS_REQUEST_BLUETOOTH, new String[]{Manifest.permission.BLUETOOTH});
                });
            }
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        super.onRequestPermissionResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSIONS_REQUEST_FINE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("WristbandsPlugin", "fine location permission granted");
                    setDevice();
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getContext());
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener((DialogInterface dialog) -> {

                    });
                    builder.show();
                }
                return;
            }
            case PERMISSIONS_REQUEST_BLUETOOTH: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("WristbandsPlugin", "bluetooth permission granted");
                    setDevice();
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getContext());
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since bluetooth access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener((DialogInterface dialog) -> {

                    });
                    builder.show();
                }
                return;
            }
        }
    }
}
