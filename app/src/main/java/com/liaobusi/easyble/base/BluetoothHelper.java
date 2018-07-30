package com.liaobusi.easyble.base;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothHelper {

    private static final String TAG = "ble";

    public static List<byte[]> splitStringData(String s) {
        byte[] bytes = s.getBytes();
        int num = bytes.length / 18;
        List<byte[]> arrs = new ArrayList<>();
        for (int i = 0; i <= num; i++) {
            final byte[] destArr;
            if (i < num) {
                destArr = new byte[20];
                destArr[0] = (byte) (i & 0xff);
                destArr[1] = (byte) ((num + 1) & 0xff);
                System.arraycopy(bytes, i * 18, destArr, 2, 18);
            } else {
                int start = i * 18;
                int length = bytes.length - start;
                destArr = new byte[length + 2];
                destArr[0] = (byte) (i & 0xff);
                destArr[1] = (byte) ((num + 1) & 0xff);
                System.arraycopy(bytes, start, destArr, 2, length);
            }
            arrs.add(destArr);
        }
        return arrs;
    }

    public static boolean readCharacteristic(BluetoothGatt gatt, UUID characterUUID) {
        if (gatt != null) {
            BluetoothGattCharacteristic charac = findCharacteristic(gatt, characterUUID);
            if (charac != null) {
                return gatt.readCharacteristic(charac);
            }
        }
        return false;
    }

    public static boolean readCharacteristic(BluetoothGatt gatt, UUID serviceUUID, UUID characterUUID) {
        if (gatt != null) {
            BluetoothGattCharacteristic charac = findCharacteristic(gatt,
                    serviceUUID, characterUUID);
            if (charac != null)
                return gatt.readCharacteristic(charac);
        }
        return false;
    }


    public static boolean writeCharacteristic(BluetoothGatt gatt, UUID characterUUID, byte[] data) {
        return writeCharacteristic(gatt, null, characterUUID, data);
    }

    public static boolean writeCharacteristic(BluetoothGatt gatt, UUID serviceUUID, UUID characterUUID, byte[] data) {
        if (gatt != null) {
            BluetoothGattCharacteristic charac;
            if (serviceUUID == null) {
                charac = findCharacteristic(gatt, characterUUID);
            } else {
                charac = findCharacteristic(gatt, serviceUUID, characterUUID);
            }
            if (charac != null) {
                if (data != null) {
                    charac.setValue(data);
                }
                charac.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                return gatt.writeCharacteristic(charac);
            }
            Log.e("ble", "can not find " + characterUUID);
        }
        return false;
    }


    private static BluetoothGattService findServiceByCharacteristic(BluetoothGatt gatt, UUID characteristicUUid) {
        List<BluetoothGattService> bleServiceList = getSupportedGattServices(gatt);
        if (bleServiceList != null) {
            for (BluetoothGattService gattService : bleServiceList) {
                BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(characteristicUUid);
                if (characteristic != null) {
                    return gattService;
                }
            }
        }
        return null;
    }


    public static boolean characteristicChangedNotification(BluetoothGatt gatt, UUID serviceUUID, UUID characterUUID, UUID descriptorUUID, boolean enable) {
        if (gatt != null) {
            BluetoothGattCharacteristic characteristic = findCharacteristic(gatt, serviceUUID, characterUUID);
            if (characteristic != null) {
                boolean result = gatt.setCharacteristicNotification(characteristic, enable);
                if (result) {
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
                    if (descriptor != null) {
                        if (enable) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        } else {
                            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                        }
                        return gatt.writeDescriptor(descriptor);
                    }
                }
            }
        }
        return false;
    }


    private static BluetoothGattCharacteristic findCharacteristic(BluetoothGatt gatt, UUID characteristicUUid) {
        BluetoothGattService service = findServiceByCharacteristic(gatt, characteristicUUid);
        if (service != null) {
            return service.getCharacteristic(characteristicUUid);
        }
        return null;
    }


    public static BluetoothGattCharacteristic findCharacteristic(BluetoothGatt gatt, UUID serviceUUid,
                                                                 UUID characteristicUUid) {
        BluetoothGattService gattService = gatt.getService(serviceUUid);
        if (gattService != null) {
            return gattService.getCharacteristic(characteristicUUid);
        }
        return null;

    }

    public static List<BluetoothGattService> getSupportedGattServices(BluetoothGatt gatt) {
        if (gatt == null) return null;
        return gatt.getServices();
    }

}
