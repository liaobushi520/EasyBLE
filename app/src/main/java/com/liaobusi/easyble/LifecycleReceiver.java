package com.liaobusi.easyble;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.OnLifecycleEvent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.arch.lifecycle.LifecycleObserver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public abstract class LifecycleReceiver extends BroadcastReceiver implements LifecycleObserver {

    private Context mContext;
    private static final String TAG="BLE";
    public LifecycleReceiver(Context context){
        mContext=context;
    }
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    public void register(){
        mContext.registerReceiver(this,makeFilter(getActions()));
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void unregister(){
        mContext.unregisterReceiver(this);
    }

    public abstract String[] getActions();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action=intent.getAction();
        switch (action){
            case BluetoothService.ACTION_GATT_CONNECTING:
                onBLEConnecting();
                break;
            case BluetoothService.ACTION_GATT_CONNECTED:
                onBLEConnected();
                break;
            case  BluetoothService.ACTION_GATT_SERVICE_DISCOVERYED:
                onBLEServiceDiscoveryed();
                break;
            case BluetoothService.ACTION_GATT_DISCONNECTED:
                onBLEDisconnected();
                break;
            case BluetoothService.ACTION_GATT_GET_SCAN_BLE_DEVICES:
                Bundle data= intent.getBundleExtra(BluetoothService.EXTRA_BLUETOOTH_SERVICE);
                Parcelable[] devices= data.getParcelableArray(BluetoothService.KEY_SCAN_DEVICES);
                List<BluetoothDevice> list=new ArrayList<>();
                if(devices==null){
                    onBLEScan(list);
                    return;
                }
                for(Parcelable parcelable:devices){
                    if(parcelable instanceof BluetoothDevice){
                        list.add((BluetoothDevice) parcelable);
                    }
                }
                onBLEScan(list);
                break;

        }
    }
    public void onBLEConnecting(){
        Log.e(TAG,"connecting");
    };

    public   void onBLEConnected(){
        Log.e(TAG,"connected");
    };

    public  void onBLEDisconnected(){
        Log.e(TAG,"disconnect");

    };
    public   void onBLEScan(List<BluetoothDevice> devices){
        Log.e(TAG,"scan "+devices.size());
    };

    public void onBLEServiceDiscoveryed(){
        Log.e(TAG,"discoveryed service ");
    };
    public static IntentFilter makeFilter(String... actions) {
        if (actions == null || actions.length <= 0) {
            return null;
        }
        final IntentFilter intentFilter = new IntentFilter();
        for (String action : actions) {
            intentFilter.addAction(action);
        }
        return intentFilter;
    }


}
