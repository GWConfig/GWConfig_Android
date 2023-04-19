package com.moko.support.commuregw.entity;


import java.io.Serializable;

public class BleCharResponse implements Serializable {

    public String mac;
    public int result_code;
    public String service_uuid;
    public String char_uuid;
    public String payload;
}
