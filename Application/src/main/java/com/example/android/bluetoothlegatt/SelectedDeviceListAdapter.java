package com.example.android.bluetoothlegatt;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class SelectedDeviceListAdapter extends BaseAdapter {
    private final static String TAG = SelectedDeviceListAdapter.class.getSimpleName();
    private ArrayList<BluetoothDeviceItem> mLeDevices;
    private LayoutInflater mInflator;
    private Context mContext;

    public SelectedDeviceListAdapter(Context context) {
        super();
        mLeDevices = new ArrayList<BluetoothDeviceItem>();
        mInflator = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);//getLayoutInflater();
        mContext = context;
    }

    public void addDevice(BluetoothDevice device) {
        if(!mLeDevices.contains(device)) {
            mLeDevices.add(new BluetoothDeviceItem(device));
        }
    }

    public BluetoothDevice getDevice(int position) {
        return mLeDevices.get(position).getDevice();
    }

    public int getItemPosition(String address){
        if(mLeDevices.size() == 0){
            return -1;
        }
        for(int i = 0; i < mLeDevices.size(); i++){
            if(mLeDevices.get(i).getDevice().getAddress().equals(address)){
                return i;
            }
        }
        Log.d(TAG, "getItemId: Couldn't find device "+address);
        return -1;
    }

    public void clear() {
        mLeDevices.clear();
    }

    @Override
    public int getCount() {
        return mLeDevices.size();
    }


    public BluetoothDevice getItem(int i) {
        return mLeDevices.get(i).getDevice();
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        final int position = i;
        final ControlViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = mInflator.inflate(R.layout.connected_device, null);
            viewHolder = new ControlViewHolder();
            viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
            viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
            viewHolder.connectDevice = view.findViewById(R.id.connect_device);
            viewHolder.forgetDevice = view.findViewById(R.id.forget_device);

            view.setTag(viewHolder);
        } else {
            viewHolder = (ControlViewHolder) view.getTag();
        }

        final BluetoothDevice device = mLeDevices.get(i).getDevice();
        final String deviceName = device.getName();
        if (deviceName != null && deviceName.length() > 0)
            viewHolder.deviceName.setText(deviceName);
        else
            viewHolder.deviceName.setText(R.string.unknown_device);
        viewHolder.deviceAddress.setText(device.getAddress());

        View.OnClickListener mOnConnectClickListener = new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(mContext instanceof DeviceControlActivity){
                    ((DeviceControlActivity)mContext).connectButtonClicked(v, device.getAddress());
                }
            }
        };
        View.OnClickListener mOnForgetClickListener = new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(mContext instanceof DeviceControlActivity){
                    ((DeviceControlActivity)mContext).forgetButtonClicked(v, device.getAddress());
                }
            }
        };

        viewHolder.connectDevice.setOnClickListener(mOnConnectClickListener);
        viewHolder.forgetDevice.setOnClickListener(mOnForgetClickListener);

        viewHolder.connectDevice.setText(getStateText(mLeDevices.get(i).getConnectionState()));

        return view;
    }

    static class ControlViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        Button connectDevice;
        Button forgetDevice;

        public ControlViewHolder(View view){
            deviceName = (TextView) view.findViewById(R.id.device_name);
            deviceAddress = (TextView) view.findViewById(R.id.device_address);
            connectDevice = (Button) view.findViewById(R.id.connect_device);
            forgetDevice = (Button) view.findViewById(R.id.forget_device);
        }

        public ControlViewHolder(){
            deviceName = null;
            deviceAddress = null;
            connectDevice = null;
            forgetDevice = null;
        }
    }

    private class BluetoothDeviceItem{
        BluetoothDevice device;
        int connectionState;

        public BluetoothDeviceItem(BluetoothDevice deviceIn){
            device = deviceIn;
            connectionState = 0;
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
    /*
    class CustomOnClickListener implements View.OnClickListener
    {

        int position;
        public CustomOnClickListener(int positionIn) {
            position = positionIn;
        }

        @Override
        public void onClick(View v)
        {
            Log.d(TAG, "Button clicked, position: "+ Integer.toString(position));
        }

    }*/

    public void setButtonText(String address){

        int pos = getItemPosition(address);
    }

    public String getStateText(int state){
        switch (state){
            case BluetoothLeService.STATE_DISCONNECTED:
                return "Disconnected";
            case BluetoothLeService.STATE_CONNECTED:
                return "Connected";
            case BluetoothLeService.STATE_CONNECTING:
                return "Connecting";
        }
        return "";
    }

    public void removeItem(String address){
        int pos = getItemPosition(address);
        if(pos >= 0)
            mLeDevices.remove(pos);
    }

    public void setDeviceState(String address, int state){
        int position = getItemPosition(address);
        if(position >= 0)
            mLeDevices.get(position).setConnectionState(state);
    }
}
