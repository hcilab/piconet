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

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
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
public class DeviceControlActivity extends FragmentActivity implements BluetoothDialogFragment.OnDeviceSelectedListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private String mDeviceAddress;
    private ArrayList<String> mDeviceAddresses;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;

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

    public void onDeviceSelected(BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice == null) {

        } else {
            String deviceName = bluetoothDevice.getName();
            String deviceAddress = bluetoothDevice.getAddress();
            mDeviceAddresses.add(deviceAddress);
            mSelectedDeviceListAdapter.addDevice(bluetoothDevice);
            int deviceConnectionStatus = mBluetoothLeService.getConnectionStatus(deviceAddress);
            mSelectedDeviceListAdapter.setDeviceState(deviceAddress, deviceConnectionStatus);
            //Toast.makeText(this, deviceName+" at address: "+deviceAddress+" clicked", Toast.LENGTH_SHORT).show();
            if (mBluetoothLeService != null) {
                ArrayList<String> arr = new ArrayList<String>();
                arr.add(deviceAddress);
                final boolean result = mBluetoothLeService.connect(arr);
                Log.d(TAG, "Connect request result=" + result);
            }
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
            ArrayList<String> arr = new ArrayList<String>();
            arr.add(mDeviceAddress);
            mBluetoothLeService.connect(arr);
            BluetoothDevice selectedDevice = mBluetoothLeService.getDevice(mDeviceAddress);
            onDeviceSelected(selectedDevice);
            mSelectedDeviceListAdapter.addAllDevices(mBluetoothLeService.getAllDeviceItems());
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

    //TODO: Transfer connection control over to service so it can be dynamically updated
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            String deviceAddress = intent.getStringExtra(BluetoothLeService.DEVICE_ADDRESS);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                invalidateOptionsMenu();
                mSelectedDeviceListAdapter.updateConnectionButton(deviceAddress, action);
                Log.d(TAG, "onReceive: " + deviceAddress + " connected");
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                invalidateOptionsMenu();
                clearUI();
                commSwitch();   //Stop notification on Rx
                mSelectedDeviceListAdapter.updateConnectionButton(deviceAddress, action);
                Log.d(TAG, "onReceive: " + deviceAddress + " disconnected");
            } else if (BluetoothLeService.ACTION_GATT_CONNECTING.equals(action)) {
                invalidateOptionsMenu();
                clearUI();
                mSelectedDeviceListAdapter.setDeviceState(deviceAddress, BluetoothLeService.STATE_CONNECTING);
                mSelectedDeviceListAdapter.updateConnectionButton(deviceAddress, action);
                Log.d(TAG, "onReceive: Connecting state: " + mSelectedDeviceListAdapter.getDeviceState(deviceAddress));
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices(mDeviceAddress));
                Log.d(TAG, "onReceive: Services discovered!");
                commSwitch();   //Start notifications on Rx
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
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
        //mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mDeviceAddresses = new ArrayList<String>();

        // Sets up UI references.

        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        //mDataField = (TextView) findViewById(R.id.data_value);*/

        getActionBar().setTitle("Paired devices");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        mDeviceList = findViewById(R.id.selected_device_list);
        TextView emptyList = (TextView) findViewById(R.id.empty);
        mDeviceList.setEmptyView(emptyList);
        mSelectedDeviceListAdapter = new SelectedDeviceListAdapter(this);

        mDeviceList.setAdapter(mSelectedDeviceListAdapter);
        mDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {

                BluetoothDevice device = mSelectedDeviceListAdapter.getItem(position);
                setSelectedDevice(device);
                Toast.makeText(getApplication(), "Item clicked!", Toast.LENGTH_SHORT);
            }
        });
    }


    public void setSelectedDevice(BluetoothDevice deviceIn){
        if(deviceIn == null){
            mDeviceAddress = null;
        }else {
            mDeviceAddress = deviceIn.getAddress();
        }
        displayGattServices(mBluetoothLeService.getSupportedGattServices(mDeviceAddress));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddresses);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
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
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
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

    private void parseData(byte[] data) {
        if (data != null) {
            if(data.length < 1)
                return;

            KinematicData canedata = DataRead.translateData(data, System.currentTimeMillis());
            Log.d(TAG, "parseData: canedata: "+ canedata.toString());
            /*
            byte[] byteArr = new byte[16];
            byte[] temp = new byte[2];
            final StringBuilder str = new StringBuilder(data.length);
            for(int i = 0; i < 2; i = i+2){
                temp[i] = data[i];
                str.append(String.format("%02X ", data[i]));
            }
            Log.d(TAG, "parseData: raw "+(str));
            ByteBuffer buff = ByteBuffer.wrap(temp);
            Log.d(TAG, "parseData: int "+buff.getShort());
            */
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
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTING);
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

    /*
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
    }*/
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
        int status = mSelectedDeviceListAdapter.getDeviceState(address);//mBluetoothLeService.getConnectionStatus(address);
        Log.d(TAG, "connectButtonClicked: Connection state "+status);
        if (status > BluetoothProfile.STATE_DISCONNECTED){
            if(status == BluetoothProfile.STATE_CONNECTING){
                Log.d(TAG, "connectButtonClicked: Connecting");
            }
            mBluetoothLeService.disconnect(arr);
        }else {
            mBluetoothLeService.connect(arr);
        }
    }

    public void forgetButtonClicked(View view, String address){
        Log.d(TAG, "forgetButtonClicked: Address "+address);
        mBluetoothLeService.removeDevice(address);
        mSelectedDeviceListAdapter.removeItem(address);
        
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


}
