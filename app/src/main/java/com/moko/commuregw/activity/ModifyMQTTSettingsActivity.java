package com.moko.commuregw.activity;


import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.RadioGroup;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.adapter.MQTTFragmentAdapter;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityMqttDeviceModifyRemoteBinding;
import com.moko.commuregw.entity.GatewayConfig;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.fragment.GeneralDeviceFragment;
import com.moko.commuregw.fragment.SSLDeviceUrlFragment;
import com.moko.commuregw.fragment.UserDeviceFragment;
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
import java.util.ArrayList;

import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

public class ModifyMQTTSettingsActivity extends BaseActivity<ActivityMqttDeviceModifyRemoteBinding> implements RadioGroup.OnCheckedChangeListener {
    public static String TAG = ModifyMQTTSettingsActivity.class.getSimpleName();
    private final String FILTER_ASCII = "[ -~]*";


    private GeneralDeviceFragment generalFragment;
    private UserDeviceFragment userFragment;
    private SSLDeviceUrlFragment sslFragment;
    private MQTTFragmentAdapter adapter;
    private ArrayList<Fragment> fragments;

    private MQTTConfig mqttDeviceConfig;

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    public Handler mHandler;

    public InputFilter filter;

    private boolean mIsConfigFinish;
    private GatewayConfig mGatewayConfig;

    private boolean mIsRetainParams;

    @Override
    protected void onCreate() {
        mqttDeviceConfig = new MQTTConfig();
        mGatewayConfig = getIntent().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
        mIsRetainParams = mGatewayConfig != null;
        filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        mBind.etMqttHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etMqttClientId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etMqttSubscribeTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        mBind.etMqttPublishTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        createFragment();
        initData();
        adapter = new MQTTFragmentAdapter(this);
        adapter.setFragmentList(fragments);
        mBind.vpMqtt.setAdapter(adapter);
        mBind.vpMqtt.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    mBind.rbGeneral.setChecked(true);
                } else if (position == 1) {
                    mBind.rbUser.setChecked(true);
                } else if (position == 2) {
                    mBind.rbSsl.setChecked(true);
                }
            }
        });
        mBind.vpMqtt.setOffscreenPageLimit(4);
        mBind.rgMqtt.setOnCheckedChangeListener(this);
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        if (mIsRetainParams) {
            mqttDeviceConfig.connectMode = mGatewayConfig.sslEnable != 0 ? mGatewayConfig.certType : 0;
            sslFragment.setConnectMode(mqttDeviceConfig.connectMode);

            mqttDeviceConfig.host = mGatewayConfig.host;
            mBind.etMqttHost.setText(mqttDeviceConfig.host);

            mqttDeviceConfig.port = mGatewayConfig.port;
            mBind.etMqttPort.setText(mqttDeviceConfig.port);

            mqttDeviceConfig.cleanSession = mGatewayConfig.cleanSession;
            generalFragment.setCleanSession(mqttDeviceConfig.cleanSession);

            mqttDeviceConfig.keepAlive = mGatewayConfig.keepAlive;
            generalFragment.setKeepAlive(mqttDeviceConfig.keepAlive);

            mqttDeviceConfig.qos = mGatewayConfig.qos;
            generalFragment.setQos(mqttDeviceConfig.qos);

            mqttDeviceConfig.clientId = mGatewayConfig.clientId;
            mBind.etMqttClientId.setText(mqttDeviceConfig.clientId);

            mqttDeviceConfig.topicSubscribe = mGatewayConfig.topicSubscribe;
            mBind.etMqttSubscribeTopic.setText(mqttDeviceConfig.topicSubscribe);

            mqttDeviceConfig.topicPublish = mGatewayConfig.topicPublish;
            mBind.etMqttPublishTopic.setText(mqttDeviceConfig.topicPublish);

            mqttDeviceConfig.username = mGatewayConfig.username;
            userFragment.setUserName(mqttDeviceConfig.username);
            mqttDeviceConfig.password = mGatewayConfig.password;
            userFragment.setPassword(mqttDeviceConfig.password);

            mqttDeviceConfig.caPath = mGatewayConfig.caPath;
            sslFragment.setCAUrl(mqttDeviceConfig.caPath);

            mqttDeviceConfig.clientKeyPath = mGatewayConfig.clientKeyPath;
            sslFragment.setClientKeyUrl(mqttDeviceConfig.clientKeyPath);

            mqttDeviceConfig.clientCertPath = mGatewayConfig.clientCertPath;
            sslFragment.setClientCertUrl(mqttDeviceConfig.clientCertPath);
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        mBind.etMqttHost.postDelayed(() -> getMqttSettings(), 1000);
    }


    @Override
    protected ActivityMqttDeviceModifyRemoteBinding getViewBinding() {
        return ActivityMqttDeviceModifyRemoteBinding.inflate(getLayoutInflater());
    }

    private void createFragment() {
        fragments = new ArrayList<>();
        generalFragment = GeneralDeviceFragment.newInstance();
        userFragment = UserDeviceFragment.newInstance();
        sslFragment = SSLDeviceUrlFragment.newInstance();
        fragments.add(generalFragment);
        fragments.add(userFragment);
        fragments.add(sslFragment);
    }

    private void initData() {
        mBind.etMqttHost.setText(mqttDeviceConfig.host);
        mBind.etMqttPort.setText(mqttDeviceConfig.port);
        mBind.etMqttClientId.setText(mqttDeviceConfig.clientId);
        mBind.etMqttSubscribeTopic.setText(mqttDeviceConfig.topicSubscribe);
        mBind.etMqttPublishTopic.setText(mqttDeviceConfig.topicPublish);
        generalFragment.setCleanSession(mqttDeviceConfig.cleanSession);
        generalFragment.setQos(mqttDeviceConfig.qos);
        generalFragment.setKeepAlive(mqttDeviceConfig.keepAlive);
        userFragment.setUserName(mqttDeviceConfig.username);
        userFragment.setPassword(mqttDeviceConfig.password);
        sslFragment.setCAUrl(mqttDeviceConfig.caPath);
        sslFragment.setClientKeyUrl(mqttDeviceConfig.clientKeyPath);
        sslFragment.setClientCertUrl(mqttDeviceConfig.clientCertPath);
        sslFragment.setConnectMode(mqttDeviceConfig.connectMode);
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
            setMqttSettings();
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_MQTT_SETTINGS) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            sslFragment.setConnectMode(result.data.get("security_type").getAsInt());
            mBind.etMqttHost.setText(result.data.get("host").getAsString());
            mBind.etMqttPort.setText(String.valueOf(result.data.get("port").getAsInt()));
            mBind.etMqttClientId.setText(result.data.get("client_id").getAsString());
            userFragment.setUserName(result.data.get("username").getAsString());
            userFragment.setPassword(result.data.get("passwd").getAsString());
            mBind.etMqttSubscribeTopic.setText(result.data.get("sub_topic").getAsString());
            mBind.etMqttPublishTopic.setText(result.data.get("pub_topic").getAsString());
            generalFragment.setQos(result.data.get("qos").getAsInt());
            generalFragment.setCleanSession(result.data.get("clean_session").getAsInt() == 1);
            generalFragment.setKeepAlive(result.data.get("keepalive").getAsInt());
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
                ToastUtils.showToast(this, "Set up succeed");
                mIsConfigFinish = true;
                mqttDeviceConfig.connectMode = sslFragment.getConnectMode();
                mqttDeviceConfig.host = mBind.etMqttHost.getText().toString();
                mqttDeviceConfig.port = mBind.etMqttPort.getText().toString();
                mqttDeviceConfig.clientId = mBind.etMqttClientId.getText().toString();
                mqttDeviceConfig.username = userFragment.getUsername();
                mqttDeviceConfig.password = userFragment.getPassword();
                mqttDeviceConfig.topicSubscribe = mBind.etMqttSubscribeTopic.getText().toString();
                mqttDeviceConfig.topicPublish = mBind.etMqttPublishTopic.getText().toString();
                mqttDeviceConfig.qos = generalFragment.getQos();
                mqttDeviceConfig.cleanSession = generalFragment.isCleanSession();
                mqttDeviceConfig.keepAlive = generalFragment.getKeepAlive();
                if (sslFragment.getConnectMode() < 2)
                    return;
                String caFileUrl = sslFragment.getCAUrl();
                String certFileUrl = sslFragment.getClientCertUrl();
                String keyFileUrl = sslFragment.getClientKeyUrl();
                // 若证书类型是CA certificate file且CA证书为空，不发送证书更新指令
                if (sslFragment.getConnectMode() == 2
                        && TextUtils.isEmpty(caFileUrl))
                    return;
                // 若证书类型是Self signed certificates且所有证书都为空，不发送证书更新指令
                if (sslFragment.getConnectMode() == 3
                        && TextUtils.isEmpty(caFileUrl)
                        && TextUtils.isEmpty(certFileUrl)
                        && TextUtils.isEmpty(keyFileUrl))
                    return;
                XLog.i("升级Mqtt证书");
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    finish();
                }, 50 * 1000);
                showLoadingProgressDialog();
                setMqttCertFile();
            } else {
                ToastUtils.showToast(this, "Set up failed");
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
                ToastUtils.showToast(this, R.string.update_success);
            } else {
                ToastUtils.showToast(this, R.string.update_failed);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    public void onBack(View view) {
        back();
    }

    @Override
    public void onBackPressed() {
        back();
    }

    private void back() {
        if (mIsConfigFinish) {
            Intent intent = new Intent();
            intent.putExtra(AppConstants.EXTRA_KEY_MQTT_CONFIG_DEVICE, mqttDeviceConfig);
            setResult(RESULT_OK, intent);
        }
        finish();
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (isParaError()) return;
        saveParams();
    }


    public void onSelectCertificate(View view) {
        if (isWindowLocked())
            return;
        sslFragment.selectCertificate();
    }


    private void saveParams() {
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (mIsRetainParams) {
            mGatewayConfig.host = mqttDeviceConfig.host;
            mGatewayConfig.port = mqttDeviceConfig.port;
            mGatewayConfig.clientId = mqttDeviceConfig.clientId;
            mGatewayConfig.cleanSession = mqttDeviceConfig.cleanSession;
            mGatewayConfig.qos = mqttDeviceConfig.qos;
            mGatewayConfig.keepAlive = mqttDeviceConfig.keepAlive;
            mGatewayConfig.topicSubscribe = mqttDeviceConfig.topicSubscribe;
            mGatewayConfig.topicPublish = mqttDeviceConfig.topicPublish;
            mGatewayConfig.username = mqttDeviceConfig.username;
            mGatewayConfig.password = mqttDeviceConfig.password;
            mGatewayConfig.sslEnable = mqttDeviceConfig.connectMode > 0 ? 1 : 0;
            if (mqttDeviceConfig.connectMode > 0) {
                mGatewayConfig.certType = mqttDeviceConfig.connectMode;
            }
            mGatewayConfig.caPath = mqttDeviceConfig.caPath;
            mGatewayConfig.clientKeyPath = mqttDeviceConfig.clientKeyPath;
            mGatewayConfig.clientCertPath = mqttDeviceConfig.clientCertPath;
            ToastUtils.showToast(this, "Setup succeed！");
            Intent intent = new Intent();
            intent.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
            intent.putExtra(AppConstants.EXTRA_KEY_MQTT_CONFIG_DEVICE, mqttDeviceConfig);
            setResult(RESULT_OK, intent);
            finish();
            return;
        }
        XLog.i("查询设备当前状态");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 50 * 1000);
        showLoadingProgressDialog();
        getDeviceStatus();
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

    private void setMqttSettings() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_MQTT_SETTINGS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("security_type", sslFragment.getConnectMode());
        jsonObject.addProperty("host", mBind.etMqttHost.getText().toString());
        jsonObject.addProperty("port", Integer.parseInt(mBind.etMqttPort.getText().toString()));
        jsonObject.addProperty("client_id", mBind.etMqttClientId.getText().toString());
        jsonObject.addProperty("username", userFragment.getUsername());
        jsonObject.addProperty("passwd", userFragment.getPassword());
        jsonObject.addProperty("sub_topic", mBind.etMqttSubscribeTopic.getText().toString());
        jsonObject.addProperty("pub_topic", mBind.etMqttPublishTopic.getText().toString());
        jsonObject.addProperty("qos", generalFragment.getQos());
        jsonObject.addProperty("clean_session", generalFragment.isCleanSession() ? 1 : 0);
        jsonObject.addProperty("keepalive", generalFragment.getKeepAlive());
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setMqttCertFile() {
        String caFileUrl = sslFragment.getCAUrl();
        String certFileUrl = sslFragment.getClientCertUrl();
        String keyFileUrl = sslFragment.getClientKeyUrl();
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

    private boolean isParaError() {
        String host = mBind.etMqttHost.getText().toString().trim();
        String port = mBind.etMqttPort.getText().toString().trim();
        String clientId = mBind.etMqttClientId.getText().toString().trim();
        String topicSubscribe = mBind.etMqttSubscribeTopic.getText().toString().trim();
        String topicPublish = mBind.etMqttPublishTopic.getText().toString().trim();

        if (TextUtils.isEmpty(host)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_host));
            return true;
        }
        if (TextUtils.isEmpty(port)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_port_empty));
            return true;
        }
        if (Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_port));
            return true;
        }
        if (TextUtils.isEmpty(clientId)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_client_id_empty));
            return true;
        }
        if (TextUtils.isEmpty(topicSubscribe)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_topic_subscribe));
            return true;
        }
        if (TextUtils.isEmpty(topicPublish)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_topic_publish));
            return true;
        }
        if (topicPublish.equals(topicSubscribe)) {
            ToastUtils.showToast(this, "Subscribed and published topic can't be same !");
            return true;
        }
        if (!generalFragment.isValid() || !sslFragment.isValid())
            return true;
        mqttDeviceConfig.host = host;
        mqttDeviceConfig.port = port;
        mqttDeviceConfig.clientId = clientId;
        mqttDeviceConfig.cleanSession = generalFragment.isCleanSession();
        mqttDeviceConfig.qos = generalFragment.getQos();
        mqttDeviceConfig.keepAlive = generalFragment.getKeepAlive();
        mqttDeviceConfig.topicSubscribe = topicSubscribe;
        mqttDeviceConfig.topicPublish = topicPublish;
        mqttDeviceConfig.username = userFragment.getUsername();
        mqttDeviceConfig.password = userFragment.getPassword();
        mqttDeviceConfig.connectMode = sslFragment.getConnectMode();
        mqttDeviceConfig.caPath = sslFragment.getCAUrl();
        mqttDeviceConfig.clientKeyPath = sslFragment.getClientKeyUrl();
        mqttDeviceConfig.clientCertPath = sslFragment.getClientCertUrl();
        return false;
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
        if (checkedId == R.id.rb_general) {
            mBind.vpMqtt.setCurrentItem(0);
        } else if (checkedId == R.id.rb_user) {
            mBind.vpMqtt.setCurrentItem(1);
        } else if (checkedId == R.id.rb_ssl) {
            mBind.vpMqtt.setCurrentItem(2);
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
}
