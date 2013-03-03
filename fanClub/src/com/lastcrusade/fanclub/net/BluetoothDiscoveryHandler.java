package com.lastcrusade.fanclub.net;

import java.util.ArrayList;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import com.lastcrusade.fanclub.R;
import com.lastcrusade.fanclub.service.ConnectionService;
import com.lastcrusade.fanclub.util.BroadcastIntent;
import com.lastcrusade.fanclub.util.Toaster;

/**
 * A generic handler for discovering devices.  This handler will accumulate discovered devices and
 * pop up a dialog to allow the user to pick the device or devices to connect to.
 * 
 * @author Jesse Rosalia
 *
 */
public class BluetoothDiscoveryHandler {

    private static final String TAG = "BluetoothDiscoveryHandler";
    
    private final Context context;
    private final BluetoothAdapter adapter;

    private ArrayList<BluetoothDevice> discoveredDevices;

    private boolean remoteInitiated;

    public BluetoothDiscoveryHandler(Context context, BluetoothAdapter adapter) {
        this.context = context;
        this.adapter = adapter;
    }

    /**
     * Call to indicate the start of discovery.  This MUST be called before devices are discovered.
     * 
     */
    public void onDiscoveryStarted(boolean remoteInitiated) {
        Log.w(TAG, "Discovery started");
        this.remoteInitiated = remoteInitiated;
        this.discoveredDevices = new ArrayList<BluetoothDevice>();
    }

    /**
     * Call to indicate the end of discovery.  This MUST be called to pop up the dialog box.
     * 
     */
    public void onDiscoveryFinished() {
        Log.w(TAG, "Discovery finished");
        if (this.discoveredDevices.isEmpty()) {
            Toaster.iToast(this.context, R.string.no_fans_found);
        } else {

            sendDiscoveredDevices();
        }
    }

    private void sendDiscoveredDevices() {
        //if its remote initiated, we want to send a different action
        String action = this.remoteInitiated
                          ? ConnectionService.ACTION_REMOTE_FIND_FINISHED
                          : ConnectionService.ACTION_FIND_FINISHED;
        new BroadcastIntent(action)
            .putParcelableArrayListExtra(ConnectionService.EXTRA_DEVICES, this.discoveredDevices)
            .send(this.context);
    }

    /**
     * Call to hold a discovered device for selection by the user.
     * 
     * @param device
     */
    public void onDiscoveryFound(BluetoothDevice device) {
        Log.w(TAG,
                "Device found: " + device.getName() + "(" + device.getAddress()
                        + ")");

        //only connect to devices that can support our service
        for (BluetoothDevice bonded : adapter.getBondedDevices()) {
            if (bonded.getAddress().equals(device.getAddress())) {
                Log.w(TAG, "Already paired!  Using paired device");
                device = adapter.getRemoteDevice(bonded.getAddress());
            }
        }

        this.discoveredDevices.add(device);
    }
}