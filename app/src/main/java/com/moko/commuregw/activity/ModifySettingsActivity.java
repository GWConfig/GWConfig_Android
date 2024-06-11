package com.moko.commuregw.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityModifySettingsBinding;
import com.moko.commuregw.db.DBTools;
import com.moko.commuregw.dialog.AlertMessageDialog;
import com.moko.commuregw.entity.GatewayConfig;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.MsgConfigResult;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.entity.MsgReadResult;
import com.moko.support.commuregw.event.DeviceOnlineEvent;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class ModifySettingsActivity extends BaseActivity<ActivityModifySettingsBinding> {
    public static String TAG = ModifySettingsActivity.class.getSimpleName();
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    public Handler mHandler;

    private MQTTConfig mqttDeviceConfig;
    private GatewayConfig mGatewayConfig;

    private boolean mIsRetainParams;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        mGatewayConfig = getIntent().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
        mIsRetainParams = mGatewayConfig != null;
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mqttDeviceConfig = new MQTTConfig();
        mHandler = new Handler(Looper.getMainLooper());
        if (mIsRetainParams) {
            mqttDeviceConfig.host = mGatewayConfig.host;
            mqttDeviceConfig.port = mGatewayConfig.port;
            mqttDeviceConfig.clientId = mMokoDevice.mac;
            mqttDeviceConfig.username = mGatewayConfig.username;
            mqttDeviceConfig.password = mGatewayConfig.password;
            mqttDeviceConfig.topicSubscribe = mGatewayConfig.topicSubscribe;
            mqttDeviceConfig.topicPublish = mGatewayConfig.topicPublish;
            mqttDeviceConfig.qos = mGatewayConfig.qos;
            mqttDeviceConfig.cleanSession = mGatewayConfig.cleanSession;
            mqttDeviceConfig.connectMode = mGatewayConfig.sslEnable != 0 ? mGatewayConfig.certType : 0;
            mqttDeviceConfig.keepAlive = mGatewayConfig.keepAlive;
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        mBind.tvName.postDelayed(() -> getMqttSettings(), 1000);
    }

    @Override
    protected ActivityModifySettingsBinding getViewBinding() {
        return ActivityModifySettingsBinding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String topic = event.getTopic();
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message))
            return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_DEVICE_STATUS) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int status = result.data.get("status").getAsInt();
            if (status == 1) {
                ToastUtils.showToast(this, "Device is OTA, please wait");
                return;
            }
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            setWifiSettings();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_WIFI_SETTINGS) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "WIFI set up succeed");
                if (mGatewayConfig.security == 0) {
                    modifyMQTTSettings();
                    return;
                }
                String caFileUrl = mGatewayConfig.wifiCaPath;
                String certFileUrl = mGatewayConfig.wifiCertPath;
                String keyFileUrl = mGatewayConfig.wifiKeyPath;
                // 若EAP类型不是TLS且CA证书为空，不发送证书更新指令
                if (mGatewayConfig.eapType != 2
                        && TextUtils.isEmpty(caFileUrl)) {
                    modifyMQTTSettings();
                    return;
                }
                // 若EAP类型是TLS且所有证书都为空，不发送证书更新指令
                if (mGatewayConfig.eapType == 2
                        && TextUtils.isEmpty(caFileUrl)
                        && TextUtils.isEmpty(certFileUrl)
                        && TextUtils.isEmpty(keyFileUrl)) {
                    modifyMQTTSettings();
                    return;
                }
                XLog.i("升级Wifi证书");
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    finish();
                }, 50 * 1000);
                showLoadingProgressDialog();
                setWifiCertFile();
            } else {
                ToastUtils.showToast(this, "WIFI set up failed");
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_WIFI_CERT_RESULT) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int resultCode = result.data.get("result_code").getAsInt();
            if (resultCode == 1) {
                ToastUtils.showToast(this, "WIFI cert update succeed");
            } else {
                ToastUtils.showToast(this, "WIFI cert update failed");
            }
            modifyMQTTSettings();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_MQTT_SETTINGS) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "MQTT set up succeed");
                if (mGatewayConfig.sslEnable == 0 || mGatewayConfig.certType < 2) {
                    modifyNetworkSettings();
                    return;
                }
                String caFileUrl = mGatewayConfig.caPath;
                String certFileUrl = mGatewayConfig.clientCertPath;
                String keyFileUrl = mGatewayConfig.clientKeyPath;
                // 若证书类型是CA certificate file且CA证书为空，不发送证书更新指令
                if (mGatewayConfig.certType == 2
                        && TextUtils.isEmpty(caFileUrl)) {
                    modifyNetworkSettings();
                    return;
                }
                // 若证书类型是Self signed certificates且所有证书都为空，不发送证书更新指令
                if (mGatewayConfig.certType == 3
                        && TextUtils.isEmpty(caFileUrl)
                        && TextUtils.isEmpty(certFileUrl)
                        && TextUtils.isEmpty(keyFileUrl)) {
                    modifyNetworkSettings();
                    return;
                }
                XLog.i("升级Mqtt证书");
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    finish();
                }, 50 * 1000);
                showLoadingProgressDialog();
                setMqttCertFile();
            } else {
                ToastUtils.showToast(this, "MQTT set up failed");
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_MQTT_CERT_RESULT) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int resultCode = result.data.get("result_code").getAsInt();
            if (resultCode == 1) {
                ToastUtils.showToast(this, "MQTT cert update succeed");
            } else {
                ToastUtils.showToast(this, "MQTT cert update failed");
            }
            modifyNetworkSettings();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_NETWORK_SETTINGS) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Network set up succeed");
            } else {
                ToastUtils.showToast(this, "Network set up failed");
            }
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            rebootDevice();
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_MQTT_SETTINGS) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mqttDeviceConfig.host = result.data.get("host").getAsString();
            mqttDeviceConfig.port = String.valueOf(result.data.get("port").getAsInt());
            mqttDeviceConfig.clientId = result.data.get("client_id").getAsString();
            mqttDeviceConfig.username = result.data.get("username").getAsString();
            mqttDeviceConfig.password = result.data.get("passwd").getAsString();
            mqttDeviceConfig.topicSubscribe = result.data.get("sub_topic").getAsString();
            mqttDeviceConfig.topicPublish = result.data.get("pub_topic").getAsString();
            mqttDeviceConfig.qos = result.data.get("qos").getAsInt();
            mqttDeviceConfig.cleanSession = result.data.get("clean_session").getAsInt() == 1;
            mqttDeviceConfig.connectMode = result.data.get("security_type").getAsInt();
            mqttDeviceConfig.keepAlive = result.data.get("keepalive").getAsInt();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_REBOOT) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            if (result.result_code == 0) {
                mMokoDevice.topicPublish = mqttDeviceConfig.topicPublish;
                mMokoDevice.topicSubscribe = mqttDeviceConfig.topicSubscribe;
                MQTTConfig mqttConfig;
                if (!TextUtils.isEmpty(mMokoDevice.mqttInfo)) {
                    mqttConfig = new Gson().fromJson(mMokoDevice.mqttInfo, MQTTConfig.class);
                } else {
                    mqttConfig = new MQTTConfig();
                }
                mqttConfig.host = mqttDeviceConfig.host;
                mqttConfig.port = mqttDeviceConfig.port;
                mqttConfig.clientId = mqttDeviceConfig.clientId;
                mqttConfig.username = mqttDeviceConfig.username;
                mqttConfig.password = mqttDeviceConfig.password;
                mqttConfig.topicSubscribe = mqttDeviceConfig.topicSubscribe;
                mqttConfig.topicPublish = mqttDeviceConfig.topicPublish;
                mqttConfig.qos = mqttDeviceConfig.qos;
                mqttConfig.cleanSession = mqttDeviceConfig.cleanSession;
                mqttConfig.connectMode = mqttDeviceConfig.connectMode;
                mqttConfig.keepAlive = mqttDeviceConfig.keepAlive;
                mMokoDevice.mqttInfo = new Gson().toJson(mqttConfig, MQTTConfig.class);
                DBTools.getInstance(this).updateDevice(mMokoDevice);
                mBind.tvName.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    mHandler.removeMessages(0);
                    ToastUtils.showToast(ModifySettingsActivity.this, "Set up succeed");
                    // 跳转首页，刷新数据
                    Intent intent = new Intent(ModifySettingsActivity.this, MainActivity.class);
                    intent.putExtra(AppConstants.EXTRA_KEY_FROM_ACTIVITY, TAG);
                    intent.putExtra(AppConstants.EXTRA_KEY_MAC, mMokoDevice.mac);
                    startActivity(intent);
                }, 1000);
            } else {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Set up failed");
            }
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    public void onBack(View view) {
        finish();
    }


    public void onWifiSettings(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, ModifyWifiSettingsActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        i.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
        startWIFISettings.launch(i);
    }

    private final ActivityResultLauncher<Intent> startWIFISettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> onWIFISettingsResult(result));

    private void onWIFISettingsResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            if (mIsRetainParams) {
                mGatewayConfig = result.getData().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
            }
        }
    }

    public void onMqttSettings(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, ModifyMQTTSettingsActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        i.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
        startMQTTSettings.launch(i);
    }

    private final ActivityResultLauncher<Intent> startMQTTSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> onMQTTSettingsResult(result));

    private void onMQTTSettingsResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            mqttDeviceConfig = (MQTTConfig) result.getData().getSerializableExtra(AppConstants.EXTRA_KEY_MQTT_CONFIG_DEVICE);
            if (mIsRetainParams) {
                mGatewayConfig = result.getData().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
            }
        }
    }

    public void onNetworkSettings(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, ModifyNetworkSettingsActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        i.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
        startNetworkSettings.launch(i);
    }

    private final ActivityResultLauncher<Intent> startNetworkSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> onNetworkSettingsResult(result));

    private void onNetworkSettingsResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            if (mIsRetainParams) {
                mGatewayConfig = result.getData().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
            }
        }
    }

    private void getMqttSettings() {
        int msgId = MQTTConstants.READ_MSG_ID_MQTT_SETTINGS;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onConnect(View view) {
        if (isWindowLocked()) return;
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setMessage("If confirm, device will reboot and use new settings to reconnect");
        dialog.setOnAlertConfirmListener(() -> {
            if (!MQTTSupport.getInstance().isConnected()) {
                ToastUtils.showToast(this, R.string.network_error);
                return;
            }
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            if (mIsRetainParams) {
                XLog.i("查询设备当前状态");
                getDeviceStatus();
                return;
            }
            rebootDevice();
        });
        dialog.show(getSupportFragmentManager());
    }

    private void getDeviceStatus() {
        int msgId = MQTTConstants.READ_MSG_ID_DEVICE_STATUS;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setWifiSettings() {
        String ssid = mGatewayConfig.wifiSSID;
        String username = mGatewayConfig.eapUserName;
        String password = mGatewayConfig.wifiPassword;
        String eapPassword = mGatewayConfig.eapPassword;
        String domainId = mGatewayConfig.domainId;
        int msgId = MQTTConstants.CONFIG_MSG_ID_WIFI_SETTINGS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("security_type", mGatewayConfig.security);
        jsonObject.addProperty("ssid", ssid);
        jsonObject.addProperty("passwd", mGatewayConfig.security == 0 ? password : "");
        jsonObject.addProperty("eap_type", mGatewayConfig.eapType);
        jsonObject.addProperty("eap_id", mGatewayConfig.eapType == 2 ? domainId : "");
        jsonObject.addProperty("eap_username", mGatewayConfig.security != 0 ? username : "");
        jsonObject.addProperty("eap_passwd", mGatewayConfig.security != 0 ? eapPassword : "");
        jsonObject.addProperty("eap_verify_server", mGatewayConfig.verifyServer);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setWifiCertFile() {
        String caFileUrl = mGatewayConfig.wifiCaPath;
        String certFileUrl = mGatewayConfig.wifiCertPath;
        String keyFileUrl = mGatewayConfig.wifiKeyPath;
        int msgId = MQTTConstants.CONFIG_MSG_ID_WIFI_CERT_FILE;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("ca_url", caFileUrl);
        jsonObject.addProperty("client_cert_url", certFileUrl);
        jsonObject.addProperty("client_key_url", keyFileUrl);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void modifyMQTTSettings() {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setMqttSettings();
    }

    private void setMqttSettings() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_MQTT_SETTINGS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("security_type", mGatewayConfig.sslEnable != 0 ? mGatewayConfig.certType : 0);
        jsonObject.addProperty("host", mGatewayConfig.host);
        jsonObject.addProperty("port", Integer.parseInt(mGatewayConfig.port));
        jsonObject.addProperty("client_id", mMokoDevice.mac);
        jsonObject.addProperty("username", mGatewayConfig.username);
        jsonObject.addProperty("passwd", mGatewayConfig.password);
        jsonObject.addProperty("sub_topic", mGatewayConfig.topicSubscribe);
        jsonObject.addProperty("pub_topic", mGatewayConfig.topicPublish);
        jsonObject.addProperty("qos", mGatewayConfig.qos);
        jsonObject.addProperty("clean_session", mGatewayConfig.cleanSession ? 1 : 0);
        jsonObject.addProperty("keepalive", mGatewayConfig.keepAlive);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setMqttCertFile() {
        String caFileUrl = mGatewayConfig.caPath;
        String certFileUrl = mGatewayConfig.clientCertPath;
        String keyFileUrl = mGatewayConfig.clientKeyPath;
        int msgId = MQTTConstants.CONFIG_MSG_ID_MQTT_CERT_FILE;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("ca_url", caFileUrl);
        jsonObject.addProperty("client_cert_url", certFileUrl);
        jsonObject.addProperty("client_key_url", keyFileUrl);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void modifyNetworkSettings() {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setNetworkSettings();
    }

    private void setNetworkSettings() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_NETWORK_SETTINGS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("dhcp_en", mGatewayConfig.dhcp);
        jsonObject.addProperty("ip", mGatewayConfig.ip);
        jsonObject.addProperty("netmask", mGatewayConfig.mask);
        jsonObject.addProperty("gw", mGatewayConfig.gateway);
        jsonObject.addProperty("dns", mGatewayConfig.dns);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void rebootDevice() {
        XLog.i("重启设备");
        int msgId = MQTTConstants.CONFIG_MSG_ID_REBOOT;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("reset", 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
