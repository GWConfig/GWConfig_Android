package com.moko.support.commuregw.entity;

import java.util.List;

public class BatchDFUBeacon {

    public String firmware_url;
    public String init_data_url;
    public List<BleDevice> ble_dev;

    public static class BleDevice {
        public String mac;
        public String passwd;
        // 0:Wait;1:Upgrading;2:Success;3:Failed
        public int status;
    }
}
