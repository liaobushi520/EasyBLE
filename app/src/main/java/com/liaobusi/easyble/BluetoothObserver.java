package com.chinamobile.watchassistant.business.bluetooth;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BluetoothObserver implements LifecycleObserver {

    public static interface ServiceListener {
        void onServiceConnected(BluetoothService bluetoothService);

        void onServiceDisconnected();
    }

    private BluetoothService mService;
    private boolean mBound;
    private ServiceListener mListener;
    private Context context;

    private ServiceConnection mConnetion = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            if (mListener != null) {
                mListener.onServiceConnected(mService);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
            if (mListener != null) {
                mListener.onServiceDisconnected();
            }
        }
    };


    public BluetoothObserver(Context context, ServiceListener serviceListener) {
        this.context = context;
        this.mListener = serviceListener;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void bind() {
        Intent gattServiceIntent = new Intent(context, BluetoothService.class);
        context.bindService(gattServiceIntent, mConnetion, Context.BIND_AUTO_CREATE);
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void unbind() {
        if (mBound) {
            context.unbindService(mConnetion);
        }
    }


}
