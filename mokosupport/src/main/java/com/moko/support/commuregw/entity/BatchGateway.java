package com.moko.support.commuregw.entity;

public class BatchGateway {
    public String mac;
    public String subscribeTopic;
    public String publishTopic;
    // 0:Wait;1:Upgrading;2:Success;3:Failed;4:Timeout
    public int status;
}
