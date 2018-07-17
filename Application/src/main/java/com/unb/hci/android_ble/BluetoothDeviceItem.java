package com.unb.hci.android_ble;

import android.bluetooth.BluetoothDevice;

public class BluetoothDeviceItem{
    BluetoothDevice device;
    int connectionState;

    public BluetoothDeviceItem(BluetoothDevice deviceIn){
        device = deviceIn;
        connectionState = 0;
    }

    public BluetoothDeviceItem(BluetoothDevice deviceIn, int connectionStateIn){
        device = deviceIn;
        connectionState = connectionStateIn;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public int getConnectionState() {
        return connectionState;
    }

    public void setConnectionState(int newState){
        connectionState = newState;
    }
}
