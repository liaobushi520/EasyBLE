package com.liaobusi.easyble;

import android.app.Service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;

import android.bluetooth.BluetoothGattCharacteristic;

import android.bluetooth.BluetoothProfile;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

import android.os.Parcelable;
import android.support.annotation.Nullable;

import android.text.TextUtils;
import android.util.Log;

import com.liaobusi.easyble.base.BluetoothGattCallback2;
import com.liaobusi.easyble.base.BluetoothHandler;
import com.liaobusi.easyble.base.BluetoothHelper;

import java.util.ArrayList;
import java.util.HashMap;

import java.util.List;
import java.util.Map;

import java.util.concurrent.Callable;


/**
 * Created by liaozhongjun on 2018/3/7.
 */

public class BluetoothService extends Service {

    private BroadcastReceiver mBlutoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1);
                if (state == BluetoothAdapter.STATE_ON) {
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                }else if(state==BluetoothAdapter.STATE_OFF){
                    broadcastUpdate(ACTION_GATT_DISCONNECTED);

                }
            }

        }
    };

    public static final String TAG = BluetoothService.class.getSimpleName();
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.droi.btlib.service.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.droi.btlib.service.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_CONNECTING =
            "com.droi.btlib.service.ACTION_GATT_DISCONNECTING";
    public final static String ACTION_GATT_GET_SCAN_BLE_DEVICES =
            "com.droi.btlib.service.ACTION_GATT_GET_SCAN_BLE_DEVICES";
    public final static String ACTION_GATT_SERVICE_DISCOVERYED =
            "com.droi.btlib.service.ACTION_GATT_SERVICE_DISCOVERYED";


    public static final String EXTRA_BLUETOOTH_SERVICE = "extra_bluetooth_service";
    public static final String KEY_SCAN_DEVICES = "scan_devices";

    private int mConnectionState;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private String mBluetoothDeviceAddress;
    private IBinder mBinder = new LocalBinder();

    private Map<String, BluetoothDevice> mCachedScanBleDevices = new HashMap<>();

    private boolean mScanning;
    private Handler mHandler = new Handler();
    private BluetoothHandler mBluetoothHandler;


    private BluetoothOperation mBluetoothOperationHandler = new BluetoothOperationHandler();
    private BLEWatchOperation mBLEWatchOperationHandler = new BLEWatchOperationHandler();

    private boolean autoReconnect = false;


    @Override
    public void onCreate() {
        super.onCreate();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothHandler = new BluetoothHandler(mGattCallback);
        mConnectionState = STATE_DISCONNECTED;
        broadcastUpdate(ACTION_GATT_DISCONNECTED);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBlutoothStateReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBluetoothOperationHandler.close();
        unregisterReceiver(mBlutoothStateReceiver);
        mBluetoothHandler.quit();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    public class LocalBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    public BLEWatchOperation getBLEWatchOperation() {
        return mBLEWatchOperationHandler;
    }

    public BluetoothOperation getBluetoothOperation() {
        return mBluetoothOperationHandler;
    }


    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final Bundle data) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_BLUETOOTH_SERVICE, data);
        sendBroadcast(intent);
    }


    private class BLEWatchOperationHandler implements BLEWatchOperation {

        @Override
        public void readWatchData() {
            mBluetoothHandler.submit(new BluetoothHandler.BluetoothTask("read_watch_info", new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return BluetoothHelper.readCharacteristic(mBluetoothGatt, BLEConstants.WATCH_SERVICE_UUID, BLEConstants.CHARACTERISTIC_NOTIFICATION_UUID);
                }
            }, 3000, BLEConstants.WATCH_SERVICE_UUID, BLEConstants.CHARACTERISTIC_NOTIFICATION_UUID));
        }
        @Override
        public void writeDataToWatch(final String s) {
            if (TextUtils.isEmpty(s)) {
                return;
            }
            List<byte[]> byteArr = BluetoothHelper.splitStringData(s);
            for (final byte[] bytes : byteArr) {
                mBluetoothHandler.submit(new BluetoothHandler.BluetoothTask("write data", new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        return BluetoothHelper.writeCharacteristic(mBluetoothGatt, BLEConstants.WATCH_SERVICE_UUID, BLEConstants.CHARACTERISTIC_NOTIFICATION_UUID, bytes);
                    }
                }, 3000, BLEConstants.WATCH_SERVICE_UUID, BLEConstants.CHARACTERISTIC_NOTIFICATION_UUID));
            }

        }

        @Override
        public void observeWatchData() {
            BluetoothHelper.characteristicChangedNotification(mBluetoothGatt,BLEConstants.WATCH_SERVICE_UUID,BLEConstants.CHARACTERISTIC_FIND_PHONE_UUID,null,true);
        }
    }


    private class BluetoothOperationHandler implements BluetoothOperation {

        private static final long SCAN_PERIOD = 60 * 1000;

        private Runnable autoReconnectScanRunnable = new Runnable() {
            @Override
            public void run() {
                scan(true, false);
            }
        };

        private int reconnectCount = 0;


        private long getReconnectScanDelay() {
            if (reconnectCount <= 5) {
                return 30 * 1000;
            } else if (reconnectCount <= 10) {
                return 60 * 1000;
            } else if (reconnectCount <= 20) {
                return 15 * 60 * 1000;
            } else if (reconnectCount <= 40) {
                return 30 * 60 * 1000;
            }
            return 60 * 60 * 1000;
        }

        private Runnable delayStopScanRunnable = new Runnable() {
            @Override
            public void run() {
                mScanning = false;
                mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback2);
                if (autoReconnect) {
                    reconnectCount++;
                    mHandler.postDelayed(autoReconnectScanRunnable, getReconnectScanDelay());
                }
            }
        };

        @Override
        public void scan(boolean enable, boolean outer) {
            if (outer) {
                autoReconnect = false;
                mHandler.removeCallbacks(delayStopScanRunnable);
                mHandler.removeCallbacks(autoReconnectScanRunnable);
            }

            if (enable) {
                // Stops scanning after a pre-defined scan period.
                mCachedScanBleDevices.clear();
                mHandler.postDelayed(delayStopScanRunnable, SCAN_PERIOD);
                mScanning = true;
                List<ScanFilter> scanFilters = new ArrayList<>();
                scanFilters.add(new ScanFilter.Builder()
                        .build());
                ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                mBluetoothAdapter.getBluetoothLeScanner().startScan(scanFilters, scanSettings, mLeScanCallback2);
            } else {
                mScanning = false;
                mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback2);
            }
        }


        @Override
        public boolean connect(String address) {
            if (mBluetoothAdapter == null || address == null) {
                Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
                return false;
            }

            // Previously connected device.  Try to reconnect.
            if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                    && mBluetoothGatt != null) {
                Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
                if (mBluetoothGatt.connect()) {
                    mConnectionState = STATE_CONNECTING;
                    broadcastUpdate(ACTION_GATT_CONNECTING);
                    Log.i(TAG, "Connecting GATT server " + mBluetoothDeviceAddress);
                    return true;
                } else {
                    return false;
                }
            }

            final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            if (device == null) {
                Log.w(TAG, "Device not found.  Unable to connect.");
                return false;
            }
            // We want to directly connect to the device, so we are setting the autoConnect
            // parameter to false.
            mBluetoothGatt = device.connectGatt(BluetoothService.this, false, mBluetoothHandler);
            Log.d(TAG, "Trying to create a new connection.");
            mBluetoothDeviceAddress = address;
            mConnectionState = STATE_CONNECTING;
            broadcastUpdate(ACTION_GATT_CONNECTING);
            Log.i(TAG, "Connecting GATT server " + mBluetoothDeviceAddress);
            return true;
        }

        @Override
        public boolean reconnect(boolean useLastAddress) {
            if (useLastAddress) {
                return connect(mBluetoothDeviceAddress);
            }
            if (getConnectState() == STATE_CONNECTED) {
                Log.i(TAG, "connected, no need to reconnect");
                return true;
            }
            if (autoReconnect) {
                return true;
            }
            reconnectCount = 0;
            autoReconnect = true;
            scan(true, false);
            return true;

        }

        @Override
        public void disconnect() {
            if (mBluetoothAdapter == null || mBluetoothGatt == null) {
                Log.w(TAG, "BluetoothAdapter not initialized");
                return;
            }
            mBluetoothGatt.disconnect();
        }


        @Override
        public void close() {
            if (mBluetoothGatt == null) {
                return;
            }
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        @Override
        public int getConnectState() {
            return mConnectionState;
        }

    }


    private BluetoothGattCallback2 mGattCallback = new BluetoothGattCallback2() {
        @Override
        public void onCharacteristicReadComplete(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status, List<Byte> values) {

        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
                autoReconnect = false;
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
                mBluetoothOperationHandler.close();
                Log.e(TAG, "reconnect" + status + "  " + newState);
                mBluetoothOperationHandler.reconnect(false);
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICE_DISCOVERYED);
            } else {
                Log.i(TAG, "Can not discovery service");
            }
        }


        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (characteristic.getUuid().equals(BLEConstants.CHARACTERISTIC_NOTIFICATION_UUID)) {
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
        }
    };


    private ScanCallback mLeScanCallback2 = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (!mScanning) {
                return;
            }
            if (autoReconnect) {//inner reconnect ,so no need to broadcast
                mBluetoothOperationHandler.scan(false, false);
                mBluetoothOperationHandler.connect(device.getAddress());
                return;
            }

            if (mCachedScanBleDevices.containsKey(device.getAddress())) {
                return;
            }
            mCachedScanBleDevices.put(device.getAddress(), device);
            Bundle bundle = new Bundle();
            Parcelable[] parcelables = new Parcelable[mCachedScanBleDevices.size()];
            mCachedScanBleDevices.values().toArray(parcelables);
            bundle.putParcelableArray(KEY_SCAN_DEVICES, parcelables);
            broadcastUpdate(ACTION_GATT_GET_SCAN_BLE_DEVICES, bundle);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };


}
