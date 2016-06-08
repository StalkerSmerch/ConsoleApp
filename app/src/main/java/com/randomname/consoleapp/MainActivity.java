package com.randomname.consoleapp;

//http://microsin.net/programming/android/usb-host.html
//http://microsin.net/programming/android/get-usb-devices-list.html
//http://www.learn2prog.ru/android-usb


import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION =
            "com.randomname.consoleapp.USB_PERMISSION";
    private static final String TAG =
            "com.randomname.consoleapp ";
    private TextView lgView;
    private UsbManager mUsbManager;
    private UsbDevice mDevice = null;
    private UsbDeviceConnection mConnection;
    private UsbEndpoint mEndpointIntr;
    private UsbSerialDevice mSerialPort;
    private static PendingIntent mPermissionIntent;
    private String cmd = "";
    private ScrollView mSVSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        lgView = (TextView)findViewById(R.id.logTextView);
        mSVSettings = (ScrollView) findViewById(R.id.svSettings);
    }

    public UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback()
    {
        public String text = null;
        public int counter = 0;
        @Override
        public void onReceivedData(byte[] arg0)
        {
            try {
                text = new String(arg0, "UTF-8");
                cmd = cmd+new String(arg0, "UTF-8");
                MainActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        //lgView.setText( lgView.getText() + text);
                        lgView.setText(cmd);
                        counter++;
                    }
                });
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    public void onClickClose(View view){
        mSVSettings.setVisibility(View.GONE);
    }

    public void onRadioButtonClicked(View view){

    }

    public void onClickSend(View view){
        if(mConnection!=null) {
            EditText etForMessage = (EditText) findViewById(R.id.etForMessage);
            String text = etForMessage.getText().toString();
            try {
                for (int i = 0; i < text.length(); i++) {
                    mSerialPort.write(Character.toString(text.charAt(i)).getBytes("UTF-8"));
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        else {
            cmd += "\nERROR: Can't send data because connection not found!";
            lgView.setText(cmd);
        }
}

    public void onResume() {
        super.onResume();
        mUsbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        // This snippet will open the first usb device connected, excluding usb root hubs
        HashMap<String, UsbDevice> usbDevices = mUsbManager.getDeviceList();
        if(!usbDevices.isEmpty())
        {

            boolean keep = true;
            for(Map.Entry<String, UsbDevice> entry : usbDevices.entrySet())
            {
                mDevice = entry.getValue();

                int deviceVID = mDevice.getVendorId();
                int devicePID = mDevice.getProductId();
                // We are supposing here there is only one device connected and it is our serial device
                mUsbManager.requestPermission(mDevice, mPermissionIntent);
                if(mUsbManager.hasPermission(mDevice)) {
                    mConnection = (UsbDeviceConnection) mUsbManager.openDevice(mDevice);
                    cmd += "Device: " + mDevice.getDeviceName()+"\n" + "Vendor Id: " + mDevice.getVendorId()+"\n";
                    lgView.setText(cmd);
                    keep = false;

                    //Установка соединения типа Serial
                    mSerialPort = UsbSerialDevice.createUsbSerialDevice(mDevice, mConnection);
                    if (mSerialPort != null) {
                        if (mSerialPort.open()) {
                            // Devices are opened with default values, Usually 9600,8,1,None,OFF
                            // CDC driver default values 115200,8,1,None,OFF
                            mSerialPort.setBaudRate(9600);
                            mSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            mSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            mSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            mSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            mSerialPort.read(mCallback);
                        } else {
                            // Serial port could not be opened, maybe an I/O error or it CDC driver was chosen it does not really fit
                            cmd += "\n" + "ERROR: Cant open serial port!";
                            lgView.setText(cmd);
                        }
                    } else {
                        // No driver for given device, even generic CDC driver could not be loaded
                        cmd+="\n" + "ERROR: No driver for given device, even generic CDC driver could not be loaded!";
                        lgView.setText(cmd);
                    }

                    if (!keep)
                        break;
                }
            }
        }

    }

    public void onBackPressed() {
        if(mSVSettings.getVisibility()!=View.VISIBLE){
            super.onBackPressed();
        }
        else{
            mSVSettings.setVisibility(View.GONE);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                mSVSettings.setVisibility(View.VISIBLE);
                return true;
            case R.id.action_update:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @SuppressLint("LongLogTag")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                        }
                    }
                    else {
                        Log.e(TAG, "permission denied for " + device);
                    }
                }
            }
        }
    };

}
