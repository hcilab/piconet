package com.unb.hci.android_ble;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private final static String TAG = MainActivity.class.getSimpleName();
    BluetoothLeService mBluetoothLeService;
    String trialID = "trial0";
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        setButtons();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_devices:
                final Intent intent = new Intent(this, DeviceControlActivity.class);
                startActivity(intent);
                return true;
            case R.id.menu_export:
                EditText partID = findViewById(R.id.trial_id);
                DBExport.startActionExportDB(this,Long.toString(100000), partID.getText().toString());
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
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
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    public void countButtonClicked(View view){
        dbHelper = DBHelper.getInstance(this);
        int count = dbHelper.getFullTableCount();
        Toast.makeText(this,"Database Row Count: "+count,Toast.LENGTH_SHORT).show();
    }

    public void recordButtonClicked(View view){
        boolean switchSuccess = mBluetoothLeService.recordingSwitch();
        if(switchSuccess){
            Button recordButton = findViewById(R.id.recording_button);
            if(mBluetoothLeService.recording){
                recordButton.setText("Stop Recording");
                recordButton.setBackgroundColor(Color.RED);
            }else{
                recordButton.setText("Record");
                recordButton.setBackgroundColor(Color.GREEN);
            }
        }
    }

    public void setButtons(){
        Button recordButton = findViewById(R.id.recording_button);
        if(mBluetoothLeService == null){
            recordButton.setText("Record");
            recordButton.setBackgroundColor(Color.GREEN);
        }else {
            if (mBluetoothLeService.recording) {
                recordButton.setText("Stop Recording");
                recordButton.setBackgroundColor(Color.RED);
            } else {
                recordButton.setText("Record");
                recordButton.setBackgroundColor(Color.GREEN);
            }
        }
    }

}
