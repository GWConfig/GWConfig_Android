package com.moko.support.commuregw.event;

public class DeviceModifyNameEvent {

    private String mac;

    public DeviceModifyNameEvent(String mac) {
        this.mac = mac;
    }

    public String getMac() {
        return mac;
    }
}
