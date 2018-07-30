package com.liaobusi.easyble;

/**
 * Created by liaozhongjun on 2018/3/7.
 */

public interface BluetoothOperation {

    void scan(boolean scan,boolean outer);

    boolean connect(String address);

    boolean reconnect(boolean useLastAddress);

    void disconnect();

    void close();

    int getConnectState();



}
