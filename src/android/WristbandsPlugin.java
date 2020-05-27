package com.cordova.plugin.wristbands;

import com.minew.beaconset.BluetoothState;
import com.minew.beaconset.MinewBeaconManager;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONObject;

/**
 * This class echoes a string called from JavaScript.
 */
public class WristbandsPlugin extends CordovaPlugin {

    private MinewBeaconManager mMinewBeaconManager;
    private boolean isScanning;
    private static final int PERMISSIONS_REQUEST_FINE_LOCATION = 1;
    private static final int PERMISSIONS_REQUEST_BLUETOOTH = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final String ACTION_STRING_ACTIVITY = "ToActivity";
    private static CallbackContext callbackContext;

    private String wristbandModel;
    private String trackedUUID;
    private String trackedMajor;
    private String trackedMinor;
    private String wristbandCommand;
    private String postURL;
    private int timerString;
    private JSONObject returnJSONParameters;
    //private Shared Core;

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
                //startScan(); //TODO
            } else if (wristbandCommand.equals("stop")){
                //stopScan(); //TODO
            }

            return true;
        }
        return false;
    }

    private void setDevice(){

        IntentFilter filter = new IntentFilter(ACTION_STRING_ACTIVITY);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String value =  intent.getExtras().getString("beaconData");
                sendSuccess(value);
            }
        };
        cordova.getActivity().registerReceiver(receiver, filter);

        if (!hasLocationPermissions() || !hasBluetoothPermissions() )
            requestNeededPermissions();
        else {
            initManager();

            //Core = new Shared(mMinewBeaconManager, trackedUUID, trackedMajor, trackedMinor, postURL, timerString);
            //Core.setDevice();

            //Set preferences to be used by the WristbandsSerice
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(cordova.getContext());;
            SharedPreferences.Editor editor = preferences.edit();

            editor.putString("trackedUUID", trackedUUID); // value to store
            editor.putString("trackedMajor", trackedMajor); // value to store
            editor.putString("trackedMinor", trackedMinor); // value to store
            editor.putString("postURL", postURL); // value to store
            editor.putInt("timerString", timerString); // value to store
            editor.commit();

            Intent intent = new Intent(cordova.getContext(), WristbandsService.class);
            cordova.getActivity().startService(intent);
        }
    }

    private void initManager() {
        if (mMinewBeaconManager != null) {
            checkBluetooth();
        }

        mMinewBeaconManager = MinewBeaconManager.getInstance(cordova.getContext());
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
