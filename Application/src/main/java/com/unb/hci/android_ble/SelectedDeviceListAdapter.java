package com.unb.hci.android_ble;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

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

    public void addAllDevices(ArrayList<BluetoothDeviceItem> arrayIn){
        if(arrayIn == null || mLeDevices == null){
            return;
        }else {
            boolean found = false;
            for(int i =0; i < arrayIn.size(); i++){
                for(int j = 0; j < mLeDevices.size(); j++){
                    if(arrayIn.get(i).getDevice().getAddress().equalsIgnoreCase(mLeDevices.get(j).getDevice().getAddress())){
                        found = true;
                    }
                }
                if(!found){
                    mLeDevices.add(arrayIn.get(i));
                }
                found = false;
            }
        }
        notifyDataSetChanged();
    }

    public void addDevice(BluetoothDevice device) {
        if(!mLeDevices.contains(device)) {
            mLeDevices.add(new BluetoothDeviceItem(device));
            notifyDataSetChanged();
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
        if(mLeDevices.get(i).getConnectionState() == BluetoothLeService.STATE_DISCONNECTED){
            viewHolder.connectDevice.setBackgroundColor(Color.RED);
        }else if(mLeDevices.get(i).getConnectionState() == BluetoothLeService.STATE_CONNECTED){
            viewHolder.connectDevice.setBackgroundColor(Color.GREEN);
        }else if(mLeDevices.get(i).getConnectionState() == BluetoothLeService.STATE_CONNECTING) {
            viewHolder.connectDevice.setBackgroundColor(Color.BLUE);
        }
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
                return "Connecting...";
        }
        return "";
    }

    public void removeItem(String address){
        int pos = getItemPosition(address);
        if(pos >= 0) {
            mLeDevices.remove(pos);
            notifyDataSetChanged();
        }
    }

    public void setDeviceState(String address, int state){
        int position = getItemPosition(address);
        if(position >= 0) {
            mLeDevices.get(position).setConnectionState(state);
            notifyDataSetChanged();
        }
    }

    public int getDeviceState(String address){
        int position = getItemPosition(address);
        if(position >= 0) {
            return mLeDevices.get(position).getConnectionState();
        }
        return -1;
    }

    public void updateConnectionButton(String address, String state){
        if(state.equals(BluetoothLeService.ACTION_GATT_CONNECTED)) {
            setDeviceState(address,BluetoothLeService.STATE_CONNECTED);
            //Toast.makeText(this, address + " connected", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "updateConnectionButton: "+address + " connected");
        }else if(state.equals(BluetoothLeService.ACTION_GATT_DISCONNECTED)){
            setDeviceState(address,BluetoothLeService.STATE_DISCONNECTED);
            //Toast.makeText(this, address + " disconnected", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "updateConnectionButton: "+address + " disconnecting");
        }else if(state.equals(BluetoothLeService.ACTION_GATT_CONNECTING)){
            setDeviceState(address,BluetoothLeService.STATE_CONNECTING);
            //Toast.makeText(this, address + " connecting", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "updateConnectionButton: "+address + " connecting");
        }
        notifyDataSetChanged();
    }
}
