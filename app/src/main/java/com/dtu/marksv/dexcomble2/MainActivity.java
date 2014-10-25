/**
 * https://github.com/devunwired/accessory-samples/blob/master/BluetoothGatt/src/com/example/bluetoothgatt/MainActivity.java
 */

package com.dtu.marksv.dexcomble2;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.UUID;


public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback {

    public static final String TAG = "BluetoothGatt";

    public static final String DEVICE_NAME = "DEXCOMRX";
    public static final String EXTENDED_SN = "SM42390263000000";
    public static byte[] AUTH_CODE = EXTENDED_SN.getBytes(StandardCharsets.US_ASCII);

    /* SERVICE: Device Information  */
    public static final UUID INFO_SERVICE = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB");
    public static final UUID INFO_MODEL_NUMBER_CHAR = UUID.fromString("00002A24-0000-1000-8000-00805F9B34FB");
    public static final UUID INFO_HARDWARE_REVISION_CHAR = UUID.fromString("00002A27-0000-1000-8000-00805F9B34FB");
    public static final UUID INFO_FIRMWARE_REVISION_CHAR = UUID.fromString("00002A26-0000-1000-8000-00805F9B34FB");
    public static final UUID INFO_MANUFACTURER_NAME_CHAR = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB");

    /* SERVICE: TX Power  */
    public static final UUID TX_POWER_SERVICE = UUID.fromString("00001804-0000-1000-8000-00805F9B34FB");
    public static final UUID TX_POWER_LEVEL_CHAR = UUID.fromString("00002A07-0000-1000-8000-00805F9B34FB");

    /* SERVICE: Gen4RcvService  */
    public static final UUID RECEIVER_SERVICE = UUID.fromString("F0ACA0B1-EBFA-F96F-28DA-076C35A521DB");
    public static final UUID RECEIVER_AUTH_CHAR = UUID.fromString("F0ACACAC-EBFA-F96F-28DA-076C35A521DB");
    public static final UUID RECEIVER_STATUS_CHAR = UUID.fromString("F0ACB0CD-EBFA-F96F-28DA-076C35A521DB");
    public static final UUID RECEIVER_HEARTBEAT_CHAR = UUID.fromString("F0AC2B18-EBFA-F96F-28DA-076C35A521DB");
    public static final UUID RECEIVER_ARRAY_SVR_CHAR = UUID.fromString("F0ACB20A-EBFA-F96F-28DA-076C35A521DB");
    public static final UUID RECEIVER_ARRAY_CLIENT_CHAR = UUID.fromString("F0ACB20B-EBFA-F96F-28DA-076C35A521DB");
    public static final UUID RECEIVER_SMARTPHONE_CMD_CHAR = UUID.fromString("F0ACB0CC-EBFA-F96F-28DA-076C35A521DB");

    /* SERVICE: ShareTestService  */

    /* DESCRIPTORS */
    public static final UUID RECEIVER_STATUS_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    //public static final UUID RECEIVER_STATUS_CONFIG = UUID.fromString("00002904-0000-1000-8000-00805f9b34fb");

    public static final String STATUS_VALID = "1";
    public static final String STATUS_INVALID = "0";
    public static final String STATUS_NOT_ENTERED = "X";
    
    private BluetoothAdapter bluetoothAdapter;
    private SparseArray<BluetoothDevice> devices;
    private BluetoothGatt connectedGatt;
    private ProgressDialog progress;
    private boolean isConnected = false;

    private TextView deviceName, deviceAddress, deviceConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);

        /* Initiate UI components */
        deviceName = (TextView) findViewById(R.id.deviceName);
        deviceAddress = (TextView) findViewById(R.id.deviceAddress);
        deviceConnected = (TextView) findViewById(R.id.isConnected);

        /* Initiate bluetooth */
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();

        devices = new SparseArray<BluetoothDevice>();

        /* Initiate progress window */
        progress = new ProgressDialog(this);
        progress.setIndeterminate(true);
        progress.setCancelable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* Check if bluetooth is enabled. If not ask to enable it. */
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            /* Bluetooth is disabled */
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        /* Make sure dialog is hidden */
        progress.dismiss();
        /* Cancel any scans in progress */
        handler.removeCallbacks(stopRunnable);
        handler.removeCallbacks(startRunnable);
        bluetoothAdapter.stopLeScan(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        /* Disconnect from connected device */
        if (connectedGatt != null) {
            connectedGatt.disconnect();
            connectedGatt = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        /* Add the "scan" option to the menu */
        getMenuInflater().inflate(R.menu.main, menu);
        /* Add any device elements we've discovered to the overflow menu */
        for (int i=0; i < devices.size(); i++) {
            BluetoothDevice device = devices.valueAt(i);
            menu.add(0, devices.keyAt(i), 0, device.getName());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan:
                devices.clear();
                startScan();
                return true;
            default:
                /* Obtain the discovered device to connect with */
                BluetoothDevice device = devices.get(item.getItemId());
                Log.i(TAG, "Connecting to " + device.getName());
                setDeviceName(device.getName());
                setDeviceAddress(device.getAddress());
                /*
                 * Make a connection with the device using the special LE-specific
                 * connectGatt() method, passing in a callback for GATT events
                 */
                connectedGatt = device.connectGatt(this, false, gattCallback);
                //Display progress UI
                handler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Connecting to " + device.getName() + "..."));
                return super.onOptionsItemSelected(item);
        }
    }

    private Runnable stopRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };
    private Runnable startRunnable = new Runnable() {
        @Override
        public void run() {
            startScan();
        }
    };

    private void startScan() {
        bluetoothAdapter.startLeScan(this);
        setProgressBarIndeterminateVisibility(true);

        handler.postDelayed(stopRunnable, 2500);
    }

    private void stopScan() {
        bluetoothAdapter.stopLeScan(this);
        setProgressBarIndeterminateVisibility(false);
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        Log.i(TAG, "New LE Device: " + device.getName() + " @ " + rssi);

        /* Only interested in specific devices. Filter on name. */
        if (DEVICE_NAME.equals(device.getName())) {
            devices.put(device.hashCode(), device);

            invalidateOptionsMenu();
        }
    }

    /* Callback for GATT */
    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "Connection State Change: "+status+" -> "+connectionState(newState));
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                /*
                 * Once successfully connected, we must next discover all the services on the
                 * device before we can read and write their characteristics.
                 */
                gatt.discoverServices();

                handler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Discovering Services..."));
                handler.sendEmptyMessage(MSG_CONNECTION_STATUS);

            } else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                /*
                 * If at any point we disconnect, send a message to clear the weather values
                 * out of the UI
                 */
                handler.sendEmptyMessage(MSG_CLEAR);
                handler.sendEmptyMessage(MSG_DISMISS);
                handler.sendEmptyMessage(MSG_CONNECTION_STATUS);
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                /*
                 * If there is a failure at any stage, simply disconnect
                 */
                gatt.disconnect();
                isConnected = false;
                handler.sendEmptyMessage(MSG_CLEAR);
                handler.sendEmptyMessage(MSG_DISMISS);
                handler.sendEmptyMessage(MSG_CONNECTION_STATUS);
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Services Discovered");
            handler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Securing Communication..."));

            reset();
//            readNextChar(gatt);
//            setNextNotify(gatt);
            stateMachine(gatt);
//            BluetoothGattService service = gatt.getService(RECEIVER_SERVICE);
//            BluetoothGattCharacteristic characteristic = service.getCharacteristic(RECEIVER_STATUS_CHAR);
//
//            gatt.readCharacteristic(characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            
            /* If a read has been called on RECEIVER_STATUS_CHAR read authentication ack code  */
            if (characteristic.getUuid().equals(RECEIVER_STATUS_CHAR)) {
                String statusCode = new String(characteristic.getValue());
                String statusStr = "";
                
                if (statusCode.equals(STATUS_VALID)) {
                    statusStr = "VALID";
                    handler.sendEmptyMessage(MSG_DISMISS);
                } else if (statusCode.equals(STATUS_INVALID)){
                    statusStr = "INVALID";
                } else if (statusCode.equals(STATUS_NOT_ENTERED)) {
                    statusStr = "CODE NOT ENTERED";
                }

                Log.d(TAG, "Authentication Status Response: " + statusStr);
                if (statusStr.equals("VALID")) {
                    advance();
                    stateMachine(gatt);
                }
            }
            if (characteristic.getUuid().equals(RECEIVER_ARRAY_CLIENT_CHAR)) {
                Log.d(TAG, "Read some Characteristic Value: ");
                for (int i = 0; i < characteristic.getValue().length; i++) {
                    Log.d(TAG, "array["+i+"] : " + characteristic.getValue()[i]);
                }
            }

//            setNextNotify(gatt);
//            setNextNotify(gatt);
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            /* Check authentication status, if auth char is written to */
            if (characteristic.getUuid().equals(RECEIVER_AUTH_CHAR)) {
                BluetoothGattCharacteristic status_char = gatt.getService(RECEIVER_SERVICE)
                        .getCharacteristic(RECEIVER_STATUS_CHAR);

                /* Read the authentication ack response */
                gatt.readCharacteristic(status_char);
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(RECEIVER_STATUS_CHAR)) {
                handler.sendMessage(Message.obtain(null, MSG_STATUS, characteristic));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {       

            /* When notify has been set for status char authenticate communication */
//            if (descriptor.getUuid().equals(RECEIVER_STATUS_CHAR)) {
//            if (!isAuth)
//              authenticate(gatt);
//            }
//            readNextChar(gatt);

        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "Remote RSSI: "+rssi);
        }

        private String connectionState(int status) {
            switch (status) {
                case BluetoothProfile.STATE_CONNECTED:
                    return "Connected";
                case BluetoothProfile.STATE_DISCONNECTED:
                    return "Disconnected";
                case BluetoothProfile.STATE_CONNECTING:
                    return "Connecting";
                case BluetoothProfile.STATE_DISCONNECTING:
                    return "Disconnecting";
                default:
                    return String.valueOf(status);
            }
        }

        private int state = 0;
        private void reset() { state = 0; }
        private void advance() { state++; }

        public void stateMachine(BluetoothGatt gatt) {
            switch (state) {
                case 0:
                    authenticate(gatt);
                    break;
                case 1:
                    readCharacteristic(gatt);
                    break;
            }
        }

        private void authenticate(BluetoothGatt gatt) {
            /* Writing authentication code */
            Log.d(TAG, "Authenticating...");
            BluetoothGattCharacteristic authChar = gatt.getService(RECEIVER_SERVICE)
                    .getCharacteristic(RECEIVER_AUTH_CHAR);
            authChar.setValue(AUTH_CODE);
            gatt.writeCharacteristic(authChar);
        }
        private void readCharacteristic(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic = gatt.getService(RECEIVER_SERVICE)
                    .getCharacteristic(RECEIVER_ARRAY_CLIENT_CHAR);
            Log.d(TAG, "Reading Characteristic: " + characteristic.getUuid().toString());
            gatt.readCharacteristic(characteristic);
        }
    };

    /* Handler */
    private static final int MSG_PROGRESS = 101;
    private static final int MSG_DISMISS = 102;
    private static final int MSG_CLEAR = 201;
    private static final int MSG_CONNECTION_STATUS = 301;
    private static final int MSG_STATUS = 401;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            BluetoothGattCharacteristic characteristic;
            switch (msg.what) {
                case MSG_PROGRESS:
                    progress.setMessage((String) msg.obj);
                    if (!progress.isShowing()) {
                        progress.show();
                    }
                    break;
                case MSG_DISMISS:
                    progress.hide();
                    break;
                case MSG_CLEAR:
                    clearDisplay();
                    break;
                case MSG_CONNECTION_STATUS:
                    setIsConnected(isConnected);
                    break;
                case MSG_STATUS:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining status value");
                        return;
                    }
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < characteristic.getValue().length; i++) {
                        result.append(characteristic.getValue()[i]);
                    }

                    Log.d(TAG, "Status Code: " + result.toString());
                    break;
            }
        }
    };

    /* Methods to update the UI */
    public void setDeviceName(String name) {
        deviceName.setText(name);
    }

    public void setDeviceAddress(String address) {
        deviceName.setText(address);
    }

    public void setIsConnected(boolean isConnected) {
        deviceConnected.setText(""+isConnected);
    }

    public void clearDisplay() {
        deviceName.setText("---");
        deviceAddress.setText("---");
    }
}
