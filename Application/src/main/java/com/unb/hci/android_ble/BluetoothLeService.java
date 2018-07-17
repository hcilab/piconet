/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unb.hci.android_ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    //Reused by all devices
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    //Specific to each device
    private LinkedList <String> mBluetoothDeviceAddress = new LinkedList<String>();
    private LinkedList <BluetoothGatt> mBluetoothGatt = new LinkedList<BluetoothGatt>();
    private DBHelper dbHelper;
    private KinematicData[] kinematicDataArr;
    private int caneArrSize = 10;
    private int index;
    private HashMap<String,Boolean> notificationsMap;
    public boolean recording;

    //Constants
    public static final int STATE_DISCONNECTED = BluetoothProfile.STATE_DISCONNECTED;   //0
    public static final int STATE_CONNECTING = BluetoothProfile.STATE_CONNECTING;       //1
    public static final int STATE_CONNECTED = BluetoothProfile.STATE_CONNECTED;         //2

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_CONNECTING =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTING";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public final static String EXTRA_BYTES =
            "com.example.bluetooth.le.EXTRA_BYTES";
    public final static String DEVICE_ADDRESS =
            "com.example.bluetooth.le.DEVICE_ADDRESS";

    // Service Constants for Bluefruit BLE shield
    public static final String UUID_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_RX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_TX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_DFU = "00001530-1212-EFDE-1523-785FEABCD123";
    public static final int kTxMaxCharacters = 20;

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            int deviceNum = getDeviceNumber(gatt);
            if(deviceNum < 0){
                Log.d(TAG, "onConnectionStateChange: No devices found");
                return;
            }else {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    intentAction = ACTION_GATT_CONNECTED;
                    broadcastUpdate(intentAction, gatt.getDevice().getAddress());
                    Log.i(TAG, "Connected to GATT server: " + gatt.getDevice().getAddress());
                    // Attempts to discover services after successful connection.
                    Log.i(TAG, "Attempting to start service discovery:" +
                            mBluetoothGatt.get(deviceNum).discoverServices());

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    intentAction = ACTION_GATT_DISCONNECTED;
                    Log.i(TAG, "Disconnected from GATT server.");
                    broadcastUpdate(intentAction, gatt.getDevice().getAddress());
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //setNotifications(gatt.getDevice().getAddress());
                //recordingSwitch(gatt);
                Toast.makeText(getApplicationContext(),"Ready to start recording!",Toast.LENGTH_LONG);
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d(TAG, "onCharacteristicRead: Data received!");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                parseData(characteristic.getValue());
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            //Log.d(TAG, "onCharacteristicChanged: Data received!");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            parseData(characteristic.getValue());
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
        //Log.d(TAG, "broadcastUpdate: Broadcast sent: "+action);
    }

    private void broadcastUpdate(final String action, String address) {
        final Intent intent = new Intent(action);
        intent.putExtra(DEVICE_ADDRESS, address);
        sendBroadcast(intent);
        //Log.d(TAG, "broadcastUpdate: Broadcast sent: "+action);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        //Log.d(TAG, "broadcastUpdate: Broadcast sent, characteristic: "+characteristic.getUuid());

        // For all profiles, writes the data formatted in HEX.
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            //intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            intent.putExtra(EXTRA_DATA, stringBuilder.toString());
            intent.putExtra(EXTRA_BYTES, data);
            //Log.d(TAG, "broadcastUpdate: Characteristic Read: "+stringBuilder.toString());
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        dbHelper = DBHelper.getInstance(this);
        kinematicDataArr = new KinematicData[caneArrSize];
        index = 0;

        notificationsMap = new HashMap<String,Boolean>();
        recording = false;

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param addresses The device addresses of the destination devices.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final ArrayList<String> addresses) {
        if (mBluetoothAdapter == null || addresses == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        Log.d(TAG, "connect: Trying to connect to "+addresses.toString());
        if(addresses.size() < 1){
            Log.w(TAG, "connect: No devices selected.");
            return false;
        }
        for(int i = 0; i < addresses.size(); i++) {
            int deviceNum = getDeviceNumber(addresses.get(i));
            if (deviceNum == -2) {
                Log.d(TAG, "connect: Gatt list not initialized");
            }
            // Previously connected device.  Try to reconnect.
            if (deviceNum >= 0) {
                if(getConnectionStatus(addresses.get(i)) > STATE_DISCONNECTED) {
                        continue;
                }
                Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
                broadcastUpdate(ACTION_GATT_CONNECTING, addresses.get(i));
                if (mBluetoothGatt.get(deviceNum).connect()) {
                    Log.d(TAG, "connect: Successfully reconnected to " + mBluetoothGatt.get(deviceNum).getDevice().getAddress());
                    return true;
                } else {
                    Log.d(TAG, "connect: Could not reconnect to " + mBluetoothGatt.get(deviceNum).getDevice().getAddress());
                    mBluetoothGatt.get(deviceNum).disconnect();
                    broadcastUpdate(ACTION_GATT_DISCONNECTED,addresses.get(i));
                    return false;
                }
            }

            try {
                final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(addresses.get(i));
                if (device == null) {
                    Log.w(TAG, "Device not found.  Unable to connect.");
                    return false;
                }

                broadcastUpdate(ACTION_GATT_CONNECTING, addresses.get(i));
                // We want to directly connect to the device, so we are setting the autoConnect
                // parameter to false.
                BluetoothGatt temp = device.connectGatt(this, false, mGattCallback);
                if(temp != null) {
                    mBluetoothGatt.add(temp);
                    Log.d(TAG, "Trying to create a new connection.");
                    mBluetoothDeviceAddress.add(addresses.get(i));
                    Log.d(TAG, "connect: Connecting to " + addresses.get(i));
                }else{
                    Log.d(TAG, "connect: Error connecting to " + addresses.get(i));
                    return false;
                }
            }catch(Exception e){
                Log.d(TAG, "connect: Cannot find address");
                return false;
            }
        }

        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnectAll() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        for(int i = 0; i < mBluetoothGatt.size(); i++)
            if (mBluetoothGatt.get(i) != null)
                mBluetoothGatt.get(i).disconnect();
    }

    public boolean disconnect(final ArrayList<String> addresses) {
        if (mBluetoothAdapter == null || addresses == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        Log.d(TAG, "connect: Trying to connect to "+addresses.toString());
        if(addresses.size() < 1){
            Log.w(TAG, "connect: No devices selected.");
            return false;
        }
        for(int i = 0; i < addresses.size(); i++) {
            int deviceNum = getDeviceNumber(addresses.get(i));
            if (deviceNum == -2) {
                Log.d(TAG, "connect: Gatt list not initialized");
            }
            // Previously connected device.  Try to reconnect.
            if (deviceNum >= 0) {//mBluetoothGatt. != null) {
                if(getConnectionStatus(addresses.get(i)) < STATE_CONNECTED) {
                    broadcastUpdate(ACTION_GATT_DISCONNECTED, addresses.get(i));
                }

                mBluetoothGatt.get(deviceNum).disconnect();
            }

            final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(addresses.get(i));
            if (device == null) {
                Log.w(TAG, "Device not found.  Unable to disconnect.");
                return false;
            }
        }

        return true;
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        for(int i = 0; i < mBluetoothGatt.size(); i++)
            mBluetoothGatt.get(i).close();
        mBluetoothGatt = null;
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled, String address) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        if(characteristic == null){
            Log.w(TAG, "Characteristic not found");
            return;
        }
        int deviceNum = getDeviceNumber(address);
        if(deviceNum < 0){
            Log.w(TAG, "Could not find device for characteristic notification");
            return;
        }
        BluetoothGatt mGatt = mBluetoothGatt.get(deviceNum);

        mGatt.setCharacteristicNotification(characteristic, enabled);

        Log.d(TAG, "setCharacteristicNotification: Something's set for notification!");

        // This is specific to Heart Rate Measurement.
        //if (UUID_RX.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            if(descriptor == null)
                return;
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            boolean writesuccess = mGatt.writeDescriptor(descriptor);
            Log.d(TAG, "setCharacteristicNotification: Notification set - " + Boolean.valueOf(enabled).toString() + " on " + (characteristic.getUuid()).toString());
        //}
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getAllSupportedGattServices() {
        if (mBluetoothGatt == null) return null;
        List<BluetoothGattService> serviceList = new ArrayList<BluetoothGattService>();
        for(int i=0; i<mBluetoothGatt.size(); i++){
            List<BluetoothGattService> services = mBluetoothGatt.get(i).getServices();
            serviceList.addAll(services);
            String temp = "";
        }
        return serviceList;
    }

    public List<BluetoothGattService> getSupportedGattServices(String address) {
        if (mBluetoothGatt == null) return null;
        int devNum = getDeviceNumber(address);
        List<BluetoothGattService> services = null;
        if(devNum >= 0) {
            services = mBluetoothGatt.get(devNum).getServices();
        }
        return services;
    }

    public void writeCustomCharacteristic(String value) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        /*check if the service is available on the device*/
        BluetoothGatt mGatt = null;
        BluetoothGattService mCustomService = null;
        for(int i=0; i < mBluetoothGatt.size(); i++){
            if(mCustomService == null) {
                mGatt = mBluetoothGatt.get(i);
                mCustomService = mGatt.getService(UUID.fromString(UUID_SERVICE));
            }
        }
        /*check if the service is available on the device*/
        if(mCustomService == null){
            Log.w(TAG, "Custom BLE Service not found");
            return;
        }
        /*get the write characteristic from the service*/
        BluetoothGattCharacteristic mWriteCharacteristic = mCustomService.getCharacteristic(UUID.fromString(UUID_TX));
        mWriteCharacteristic.setValue(value);
        if(mGatt.writeCharacteristic(mWriteCharacteristic) == false){
            Log.w(TAG, "Failed to write characteristic");
        }
    }

    private int getDeviceNumber(BluetoothGatt gatt){
        if(mBluetoothGatt == null){
            return -2;
        }
        for(int i = 0; i < mBluetoothGatt.size(); i++){
            if(gatt.getDevice().getAddress().equals(mBluetoothGatt.get(i).getDevice().getAddress())){
                Log.d(TAG, "getDeviceNumber: "+i);
                return i;
            }
        }
        return -1;
    }
    private int getDeviceNumber(String address){
        if(mBluetoothGatt == null){
            return -2;
        }
        for(int i = 0; i < mBluetoothGatt.size(); i++){
            if(address == mBluetoothGatt.get(i).getDevice().getAddress()){
                Log.d(TAG, "getDeviceNumber: "+i);
                return i;
            }
        }
        return -1;
    }
    public BluetoothDevice getDevice(String address){
        if(mBluetoothGatt == null){
            return null;
        }
        for(int i = 0; i < mBluetoothGatt.size(); i++){
            if(address == mBluetoothGatt.get(i).getDevice().getAddress()){
                Log.d(TAG, "getDeviceNumber: "+i);
                return mBluetoothGatt.get(i).getDevice();
            }
        }
        return null;
    }

    public ArrayList<BluetoothDevice> getAllDevices(){
        ArrayList<BluetoothDevice> arrayOut = new ArrayList<BluetoothDevice>();
        Log.d(TAG, "Connected devices: ");
        for(int i = 0; i < mBluetoothGatt.size(); i++){
            Log.d(TAG,mBluetoothGatt.get(i).getDevice().getAddress());
            arrayOut.add(mBluetoothGatt.get(i).getDevice());
        }
        return arrayOut;
    }

    public ArrayList<BluetoothDeviceItem> getAllDeviceItems(){
        ArrayList<BluetoothDeviceItem> arrayOut = new ArrayList<BluetoothDeviceItem>();
        Log.d(TAG, "Connected devices: ");
        BluetoothDevice temp = null;
        for(int i = 0; i < mBluetoothGatt.size(); i++){
            temp = mBluetoothGatt.get(i).getDevice();
            Log.d(TAG,temp.getAddress());
            arrayOut.add(new BluetoothDeviceItem(temp, getConnectionStatus(temp.getAddress())));
        }
        return arrayOut;
    }

    public int getConnectionStatus(String address){
        int deviceNum = getDeviceNumber(address);
        if(deviceNum < 0)
            return 0;
        return mBluetoothManager.getConnectionState(getDevice(address), BluetoothProfile.GATT);

    }

    public void removeDevice(String address){
        int deviceNum = getDeviceNumber(address);
        ArrayList<String> arr = new ArrayList<String>();
        arr.add(address);
        disconnect(arr);

        removeCharacteristics(mBluetoothGatt.get(deviceNum));
        //Potential for race condition if other methods are modifying these values
        mBluetoothGatt.set(deviceNum,null);
        mBluetoothDeviceAddress.set(deviceNum,null);
        mBluetoothGatt.remove(deviceNum);
        mBluetoothDeviceAddress.remove(deviceNum);
        broadcastUpdate(ACTION_GATT_DISCONNECTED, address);
    }

    private void parseData(byte[] data) {
        if (data != null) {
            KinematicData canedata = DataRead.translateData(data, System.currentTimeMillis());
            if(canedata == null)
                return;
            synchronized (this){
                kinematicDataArr[index] = canedata;
                index++;
                if(index == caneArrSize){
                    dbHelper.fullInsertLoop(kinematicDataArr);
                    index = 0;
                    //Log.d(TAG, "parseData: Data inserted");
                }
            }
            //Log.d(TAG, "parseData: canedata: "+ canedata.toString());
        }
    }

    public boolean setNotifications(String address){
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        int devicenum = getDeviceNumber(address);
        BluetoothGatt mGatt = mBluetoothGatt.get(devicenum);
        boolean found = false;
        for(BluetoothGattService service: mGatt.getServices()){
            for(BluetoothGattCharacteristic characteristic: service.getCharacteristics()){
                if(checkIfNotifiable(characteristic)){
                    setCharacteristicNotification(characteristic, true, mGatt.getDevice().getAddress());
                    found = true;
                }
            }
        }
        return found;
    }

    public boolean checkIfNotifiable(BluetoothGattCharacteristic characteristic){
        return KnownUUIDs.checkNotifiable(characteristic.getUuid().toString());
    }

    public boolean changeUartComms(boolean currentState){ //true if notifications state switch successful, false ow
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        BluetoothGatt mGatt = null;
        BluetoothGattService mCustomService = null;
        for(int i=0; i < mBluetoothGatt.size(); i++){
            if(mCustomService == null) {
                mGatt = mBluetoothGatt.get(i);
                mCustomService = mGatt.getService(UUID.fromString(UUID_SERVICE));
            }
        }
        /*check if the service is available on the device*/
        if(mCustomService == null){
            Log.w(TAG, "Custom BLE Service not found");
            return false;
        }
        /*start/stop UART notifications*/
        BluetoothGattCharacteristic rxCharacteristic = mCustomService.getCharacteristic(UUID.fromString(UUID_RX));
        if (rxCharacteristic != null) {
            setCharacteristicNotification(rxCharacteristic, !currentState, mGatt.getDevice().getAddress());
            Log.d(TAG, "changeUartComms: Rx notifications successfully switched");
            return true;
        }
        Log.d(TAG, "changeUartComms: Error retrieving Rx channel");
        return false;
    }

    //TODO update setCharacteristicNotification to give boolean for success/failure of action
    public boolean recordingSwitch(){
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        boolean switchie = false;
        for(BluetoothGatt gatt: mBluetoothGatt){
            for(BluetoothGattService service: getAllSupportedGattServices()){
                for(BluetoothGattCharacteristic characteristic: service.getCharacteristics()){
                    if(checkIfNotifiable(characteristic)){
                        Boolean temp = notificationsMap.get(characteristic.getUuid().toString());
                        if(temp == null){
                            setCharacteristicNotification(characteristic, true, gatt.getDevice().getAddress());
                            notificationsMap.put(characteristic.getUuid().toString(), true);
                            switchie = true;
                        }else{
                            setCharacteristicNotification(characteristic, !(temp.booleanValue()),gatt.getDevice().getAddress());
                            notificationsMap.put(characteristic.getUuid().toString(), !temp.booleanValue());
                            switchie = true;
                        }
                    }
                }
            }
        }

        if(switchie){
            recording = !recording;
        }else{
            Toast.makeText(getApplicationContext(),"Could not switch recording state",Toast.LENGTH_SHORT);
        }
        return switchie;
    }

    public boolean recordingSwitch(BluetoothGatt gatt){
        boolean switchie = false;
        if (mBluetoothAdapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        for(BluetoothGattService service: gatt.getServices()){
            for(BluetoothGattCharacteristic characteristic: service.getCharacteristics()){
                if(checkIfNotifiable(characteristic)){
                    Boolean temp = notificationsMap.get(characteristic.getUuid().toString());
                    if(temp == null){
                        setCharacteristicNotification(characteristic, true, gatt.getDevice().getAddress());
                        notificationsMap.put(characteristic.getUuid().toString(), true);
                        switchie = true;
                    }else{
                        setCharacteristicNotification(characteristic, !(temp.booleanValue()),gatt.getDevice().getAddress());
                        notificationsMap.put(characteristic.getUuid().toString(), !temp.booleanValue());
                        switchie = true;
                    }
                }
            }
        }

        if(switchie){
            recording = !recording;
        }else{
            Toast.makeText(getApplicationContext(),"Could not switch recording state",Toast.LENGTH_SHORT);
        }
        return switchie;
    }

    public boolean removeCharacteristics(BluetoothGatt gatt){
        boolean switchie = false;
        if (mBluetoothAdapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        for(BluetoothGattService service: gatt.getServices()){
            for(BluetoothGattCharacteristic characteristic: service.getCharacteristics()){
                if(checkIfNotifiable(characteristic)){
                    Boolean temp = notificationsMap.get(characteristic.getUuid().toString());
                    if(temp == null){
                        continue;
                    }else{
                        notificationsMap.remove(characteristic.getUuid().toString());
                        switchie = true;
                    }
                }
            }
        }
        return switchie;
    }
}
