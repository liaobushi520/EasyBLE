package com.liaobusi.easyble.base;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build;
import android.support.annotation.RequiresApi;

import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class BluetoothGattCallback2 extends BluetoothGattCallback {
    public abstract void onCharacteristicReadComplete(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status, List<Byte> values);
}
