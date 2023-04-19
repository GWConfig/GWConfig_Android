package com.moko.support.commuregw.callback;

import com.moko.support.commuregw.entity.DeviceInfo;

public interface MokoScanDeviceCallback {
    void onStartScan();

    void onScanDevice(DeviceInfo device);

    void onStopScan();
}
