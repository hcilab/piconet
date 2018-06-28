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

package com.example.android.bluetoothlegatt;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends FragmentActivity implements BluetoothDialogFragment.OnDeviceSelectedListener{
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ArrayList<String> mDeviceAddresses;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private boolean mUartConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private static BluetoothDialogFragment btFragment;
    private ListView mDeviceList;
    private SelectedDeviceListAdapter mSelectedDeviceListAdapter;

    public void onDeviceSelected(BluetoothDevice bluetoothDevice){
        String deviceName = bluetoothDevice.getName();
        String deviceAddress = bluetoothDevice.getAddress();
        mDeviceAddresses.add(deviceAddress);
        mSelectedDeviceListAdapter.addDevice(bluetoothDevice);
        int deviceConnectionStatus = BluetoothLeService.STATE_DISCONNECTED;
        if(mBluetoothLeService.isConnected(deviceAddress)){
            deviceConnectionStatus = BluetoothLeService.STATE_CONNECTED;
        }
        mSelectedDeviceListAdapter.setDeviceState(deviceAddress,deviceConnectionStatus);
        mSelectedDeviceListAdapter.notifyDataSetChanged();
        //Toast.makeText(this, deviceName+" at address: "+deviceAddress+" clicked", Toast.LENGTH_SHORT).show();
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(deviceAddress);
            Log.d(TAG, "Connect request result=" + result);
            //btFragment.dismiss();
        }
        setSelectedDevice(bluetoothDevice);
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
            BluetoothDevice selectedDevice = mBluetoothLeService.getDevice(mDeviceAddress);
            onDeviceSelected(selectedDevice);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            mDeviceAddresses = new ArrayList<String>();
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            String deviceAddress = intent.getStringExtra(BluetoothLeService.DEVICE_ADDRESS);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                if(deviceAddress.equals(mDeviceAddress))
                    updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
                updateConnectionButton(deviceAddress,action);
                Log.d(TAG, "onReceive: "+deviceAddress+" connected");
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                if(deviceAddress.equals(mDeviceAddress))
                    updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
                commSwitch();   //Stop notification on Rx
                updateConnectionButton(deviceAddress,action);
                Log.d(TAG, "onReceive: "+deviceAddress+" disconnected");
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices(mDeviceAddress));
                Log.d(TAG, "onReceive: Services discovered!");
                commSwitch();   //Start notifications on Rx
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                Log.d(TAG, "onReceive: Data available!");
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false, mDeviceAddress);
                                mNotifyCharacteristic = null;
                            }
                            //mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true, mDeviceAddress);
                        }
                        return true;
                    }
                    return false;
                }
    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mDeviceAddresses = new ArrayList<String>();
        //mDeviceAddresses.add(mDeviceAddress);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        mDeviceList = findViewById(R.id.selected_device_list);
        mSelectedDeviceListAdapter = new SelectedDeviceListAdapter(this);

        mDeviceList.setAdapter(mSelectedDeviceListAdapter);
        mDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {

                BluetoothDevice device = mSelectedDeviceListAdapter.getItem(position);
                setSelectedDevice(device);
                Toast.makeText(getApplication(),"Item clicked!",Toast.LENGTH_SHORT);
            }
        });
        /*
        mDeviceList.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                final BluetoothDevice device = mSelectedDeviceListAdapter.getDevice(position);
                if (device == null)
                    Log.d("BLEdialog","Null device");
                else {
                    //mCallback.onDeviceSelected(device);
                }
                //dismiss();
            }
        });*/
    }

    public void setSelectedDevice(BluetoothDevice deviceIn){
        if(deviceIn == null){
            mDeviceAddress = null;
            mDeviceName = null;
        }else {
            mDeviceAddress = deviceIn.getAddress();
            mDeviceName = deviceIn.getName();
        }
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        if(mBluetoothLeService.isConnected(mDeviceAddress)) {
            updateConnectionState(R.string.connected);
            mConnected = true;
        }else{
            updateConnectionState(R.string.disconnected);
            mConnected = false;
        }
        displayGattServices(mBluetoothLeService.getSupportedGattServices(mDeviceAddress));
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddresses);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        /*
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        */
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            /*
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect(mDeviceAddresses);
                return true;
                */
            case R.id.menu_scan:
                btFragment = new BluetoothDialogFragment();
                btFragment.show(getSupportFragmentManager(), "Bluetooth");
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
            Log.d(TAG, "displayData: "+data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            /*currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));*/
            currentServiceData.put(
                    LIST_NAME, KnownUUIDs.getServiceName(uuid));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                /*currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));*/
                currentCharaData.put(
                        LIST_NAME, KnownUUIDs.getCharacteristicName(uuid));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            //charas 0 and 1 are RX and TX respectively
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void sendDataToArduino(View v){
        if(mBluetoothLeService != null) {
            try{
                EditText ed = null;//(EditText) findViewById(R.id.editText);
                String content = ed.getText().toString();
                mBluetoothLeService.writeCustomCharacteristic(content);//(0xAA);
                ed.setText("");
            }catch(Exception e){
                System.out.println(e.getMessage());
            }
        }
    }

    public void commSwitch(View v){
        if(mBluetoothLeService != null) {
            try {
                boolean switchSuccessful = mBluetoothLeService.changeUartComms(mUartConnected);
                if (switchSuccessful)
                    mUartConnected = !mUartConnected;
                Button commButton = null;//(Button) findViewById(R.id.button3);
                if (mUartConnected) {
                    commButton.setText("Stop Uart Comms");
                } else {
                    commButton.setText("Start Uart Comms");
                }
            }catch(Exception e){
            System.out.println(e.getMessage());
        }

        }
    }
    public void commSwitch(){
        /*if(mBluetoothLeService != null) {
            boolean switchSuccessful = mBluetoothLeService.changeUartComms(mUartConnected);
            if(switchSuccessful)
                mUartConnected = !mUartConnected;
            Button commButton = (Button)findViewById(R.id.button3);
            if(mUartConnected){
                commButton.setText("Stop Uart Comms");
            }else{
                commButton.setText("Start Uart Comms");
            }
        }*/
    }

    public void reScanDevices(View v){
        if (btFragment != null) {
            // If article frag is available, we're in two-pane layout...

            // Call a method in the ArticleFragment to update its content
            btFragment.reScan();
        }
    }

    public void connectButtonClicked(View view, String address){
        Log.d(TAG, "connectButtonClicked: Address "+address);
        ArrayList<String> arr = new ArrayList<String>();
        arr.add(address);
        if (mBluetoothLeService.isConnected(address)){
            mBluetoothLeService.disconnect(arr);
        }else {
            mBluetoothLeService.connect(arr);
        }
    }

    public void forgetButtonClicked(View view, String address){
        Log.d(TAG, "forgetButtonClicked: Address "+address);
        mBluetoothLeService.removeDevice(address);
        mSelectedDeviceListAdapter.removeItem(address);
        mSelectedDeviceListAdapter.notifyDataSetChanged();
        
        int removed = 0;
        for(int i = 0; i < mDeviceAddresses.size(); i++){
            if(mDeviceAddresses.get(i).equals(address)) {
                mDeviceAddresses.remove(i - removed);
                removed++;
                Log.d(TAG, "forgetButtonClicked: address"+address+" removed from activity list");
            }
        }

        if(mDeviceAddresses.size() > 0){
            setSelectedDevice(mBluetoothLeService.getDevice(mDeviceAddresses.get(0)));
        }else{
            setSelectedDevice(null);
        }
    }

    public void updateConnectionButton(String address, String state){
        if(state.equals(BluetoothLeService.ACTION_GATT_CONNECTED)) {
            mSelectedDeviceListAdapter.setDeviceState(address,BluetoothLeService.STATE_CONNECTED);
            //Toast.makeText(this, address + " connected", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "updateConnectionButton: "+address + " connected");
        }else if(state.equals(BluetoothLeService.ACTION_GATT_DISCONNECTED)){
            mSelectedDeviceListAdapter.setDeviceState(address,BluetoothLeService.STATE_DISCONNECTED);
            //Toast.makeText(this, address + " disconnected", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "updateConnectionButton: "+address + " disconnecting");
        }else if(state.equals(BluetoothLeService.ACTION_GATT_CONNECTING)){
            mSelectedDeviceListAdapter.setDeviceState(address,BluetoothLeService.STATE_CONNECTING);
            //Toast.makeText(this, address + " connecting", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "updateConnectionButton: "+address + " connecting");
        }
        mSelectedDeviceListAdapter.notifyDataSetChanged();
    }
}
