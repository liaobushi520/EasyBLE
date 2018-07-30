package com.liaobusi.easyble;

/**
 * Created by liaozhongjun on 2018/3/7.
 */

public interface BLEWatchOperation {


      void readWatchData();

      void writeDataToWatch(String s);

      void observeWatchData();

}
