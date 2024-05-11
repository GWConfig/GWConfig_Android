package com.moko.commuregw.entity;


import android.os.Parcel;
import android.os.Parcelable;

public class GatewayConfig implements Parcelable {
    public String host = "";
    public String port = "";
    public String clientId = "";
    public String topicSubscribe = "";
    public String topicPublish = "";
    public boolean cleanSession = true;
    public int qos = 0;
    public int keepAlive = 60;
    public String username = "";
    public String password = "";
    public int sslEnable = 0;
    public String caPath = "";
    public String clientCertPath = "";
    public String clientKeyPath = "";
    public int certType = 3;
    public int security = 0;
    public int eapType = 2;
    public String wifiSSID = "";
    public String wifiPassword = "";
    public String domainId = "";
    public String eapUserName = "";
    public String eapPassword = "";
    public int verifyServer = 1;
    public String wifiCaPath = "";
    public String wifiCertPath = "";
    public String wifiKeyPath = "";
    public int dhcp = 1;
    public String ip = "";
    public String mask = "";
    public String gateway = "";
    public String dns = "";
    public String ntpServer = "";
    public int timezone = -10;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.host);
        dest.writeString(this.port);
        dest.writeString(this.clientId);
        dest.writeString(this.topicSubscribe);
        dest.writeString(this.topicPublish);
        dest.writeByte(this.cleanSession ? (byte) 1 : (byte) 0);
        dest.writeInt(this.qos);
        dest.writeInt(this.keepAlive);
        dest.writeString(this.username);
        dest.writeString(this.password);
        dest.writeInt(this.sslEnable);
        dest.writeString(this.caPath);
        dest.writeString(this.clientCertPath);
        dest.writeString(this.clientKeyPath);
        dest.writeInt(this.certType);
        dest.writeInt(this.security);
        dest.writeInt(this.eapType);
        dest.writeString(this.wifiSSID);
        dest.writeString(this.wifiPassword);
        dest.writeString(this.domainId);
        dest.writeString(this.eapUserName);
        dest.writeString(this.eapPassword);
        dest.writeInt(this.verifyServer);
        dest.writeString(this.wifiCaPath);
        dest.writeString(this.wifiCertPath);
        dest.writeString(this.wifiKeyPath);
        dest.writeInt(this.dhcp);
        dest.writeString(this.ip);
        dest.writeString(this.mask);
        dest.writeString(this.gateway);
        dest.writeString(this.dns);
        dest.writeString(this.ntpServer);
        dest.writeInt(this.timezone);
    }

    public void readFromParcel(Parcel source) {
        this.host = source.readString();
        this.port = source.readString();
        this.clientId = source.readString();
        this.topicSubscribe = source.readString();
        this.topicPublish = source.readString();
        this.cleanSession = source.readByte() != 0;
        this.qos = source.readInt();
        this.keepAlive = source.readInt();
        this.username = source.readString();
        this.password = source.readString();
        this.sslEnable = source.readInt();
        this.caPath = source.readString();
        this.clientCertPath = source.readString();
        this.clientKeyPath = source.readString();
        this.certType = source.readInt();
        this.security = source.readInt();
        this.eapType = source.readInt();
        this.wifiSSID = source.readString();
        this.wifiPassword = source.readString();
        this.domainId = source.readString();
        this.eapUserName = source.readString();
        this.eapPassword = source.readString();
        this.verifyServer = source.readInt();
        this.wifiCaPath = source.readString();
        this.wifiCertPath = source.readString();
        this.wifiKeyPath = source.readString();
        this.dhcp = source.readInt();
        this.ip = source.readString();
        this.mask = source.readString();
        this.gateway = source.readString();
        this.dns = source.readString();
        this.ntpServer = source.readString();
        this.timezone = source.readInt();
    }

    public GatewayConfig() {
    }

    protected GatewayConfig(Parcel in) {
        this.host = in.readString();
        this.port = in.readString();
        this.clientId = in.readString();
        this.topicSubscribe = in.readString();
        this.topicPublish = in.readString();
        this.cleanSession = in.readByte() != 0;
        this.qos = in.readInt();
        this.keepAlive = in.readInt();
        this.username = in.readString();
        this.password = in.readString();
        this.sslEnable = in.readInt();
        this.caPath = in.readString();
        this.clientCertPath = in.readString();
        this.clientKeyPath = in.readString();
        this.certType = in.readInt();
        this.security = in.readInt();
        this.eapType = in.readInt();
        this.wifiSSID = in.readString();
        this.wifiPassword = in.readString();
        this.domainId = in.readString();
        this.eapUserName = in.readString();
        this.eapPassword = in.readString();
        this.verifyServer = in.readInt();
        this.wifiCaPath = in.readString();
        this.wifiCertPath = in.readString();
        this.wifiKeyPath = in.readString();
        this.dhcp = in.readInt();
        this.ip = in.readString();
        this.mask = in.readString();
        this.gateway = in.readString();
        this.dns = in.readString();
        this.ntpServer = in.readString();
        this.timezone = in.readInt();
    }

    public static final Parcelable.Creator<GatewayConfig> CREATOR = new Parcelable.Creator<GatewayConfig>() {
        @Override
        public GatewayConfig createFromParcel(Parcel source) {
            return new GatewayConfig(source);
        }

        @Override
        public GatewayConfig[] newArray(int size) {
            return new GatewayConfig[size];
        }
    };
}
