package com.moko.support.commuregw.entity;


import java.io.Serializable;

public class BleDevice implements Serializable {

    public String adv_name;
    public String mac;
    public int rssi;
    public int index;
    //0:badge,
    //1:gateway
    public int type_code;
    //值类型：number
    //0/1
    public int connectable;
}
