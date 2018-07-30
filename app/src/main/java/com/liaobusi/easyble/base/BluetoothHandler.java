package com.liaobusi.easyble.base;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.liaobusi.easyble.base.BluetoothGattCallback2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Created by liaozhongjun on 2018/3/6.
 */

public class BluetoothHandler extends BluetoothGattCallback {


    private static final String TAG = "BluetoothHandler";
    private final Looper mServiceLooper;
    private final Handler mHandler;

    private BluetoothTask mCurrentTask;

    private final Object mLock = new Object();
    private BluetoothGattCallback2 mBluetoothGattCallback;

    private SparseArray<Byte[]> readValues = new SparseArray();

    public static class BluetoothTask {

        private String name;

        private long timeout;

        private Callable<Boolean> runnable;

        private UUID gattService;

        private UUID gattCharacteristic;

        private boolean preDispose;


        public BluetoothTask(String name, Callable<Boolean> runnable, @NonNull UUID service, @NonNull UUID characteristic) {
            this(name, runnable, -1, service, characteristic, true);
        }

        public BluetoothTask(String name, Callable<Boolean> runnable, long timeout, @NonNull UUID service, @NonNull UUID characteristic) {
            this(name, runnable, timeout, service, characteristic, true);
        }

        public BluetoothTask(String name, Callable<Boolean> runnable, long timeout, @NonNull UUID service, @NonNull UUID characteristic, boolean preDispose) {
            this.name = name;
            this.timeout = timeout;
            this.runnable = runnable;
            this.gattService = service;
            this.gattCharacteristic = characteristic;
        }
    }

    private class TaskExecutor implements Runnable {

        private BluetoothTask task;

        public TaskExecutor(BluetoothTask task) {
            this.task = task;
        }

        @Override
        public void run() {
            synchronized (mLock) {
                readValues.clear();
                mCurrentTask = task;
                try {
                    Log.e(TAG, "executing task " + task.name);
                    boolean result = task.runnable.call();
                    if (!result) {
                        Log.e("task execute", task.name + " execute fail");
                        mCurrentTask = null;
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if (task.timeout == -1) {
                        mLock.wait();
                    } else {
                        mLock.wait(task.timeout);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    mCurrentTask = null;
                }

            }
        }
    }


    public BluetoothHandler(BluetoothGattCallback2 callback) {

        HandlerThread thread = new HandlerThread("BtManagerService");
        thread.start();

        mServiceLooper = thread.getLooper();
        mHandler = new Handler(mServiceLooper);

        mBluetoothGattCallback = callback;

    }

    public void submit(BluetoothTask task) {
        mHandler.sendMessage(Message.obtain(mHandler, new TaskExecutor(task)));
    }

    public void quit() {
        mServiceLooper.quit();
    }

    private void checkReleaseLock(BluetoothGattCharacteristic characteristic) {
        synchronized (mLock) {
            if (mCurrentTask != null && mCurrentTask.gattCharacteristic.equals(characteristic.getUuid()) && mCurrentTask.gattService.equals(characteristic.getService().getUuid())) {
                mLock.notifyAll();
            }
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (mCurrentTask == null) {
            Log.w(TAG, "current no task");
            return;
        }
        if (mCurrentTask.gattCharacteristic.equals(characteristic.getUuid()) && mCurrentTask.gattService.equals(characteristic.getService().getUuid())) {
            if (!mCurrentTask.preDispose) {
                mBluetoothGattCallback.onCharacteristicRead(gatt, characteristic, status);
                return;
            }
            byte[] bytes = characteristic.getValue();
            if (bytes == null || bytes.length < 2) {
                Log.i(TAG, "read data invade");
                return;
            }
            int total = bytes[1] & 0xff;
            int index = bytes[0] & 0xff;
            Byte[] dest = new Byte[bytes.length - 2];
            System.arraycopy(bytes, 2, dest, 0, dest.length);
            readValues.put(index, dest);
            if (total == readValues.size()) {//read over
                List<Byte> values = new ArrayList<>();
                for (int i = 0; i < readValues.size(); i++) {
                    Byte[] list = readValues.get(i);
                    values.addAll(Arrays.asList(list));
                }
                mBluetoothGattCallback.onCharacteristicReadComplete(gatt, characteristic, status, values);
                checkReleaseLock(characteristic);
            }
        } else {
            Log.w(TAG, "current task uuid=" + mCurrentTask.gattCharacteristic + " ,but read uuid " + characteristic.getUuid());
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        mBluetoothGattCallback.onCharacteristicWrite(gatt, characteristic, status);
        checkReleaseLock(characteristic);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        mBluetoothGattCallback.onCharacteristicChanged(gatt, characteristic);
        checkReleaseLock(characteristic);

    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        mBluetoothGattCallback.onConnectionStateChange(gatt, status, newState);
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        mBluetoothGattCallback.onServicesDiscovered(gatt, status);
    }
}
