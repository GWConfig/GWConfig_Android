package com.moko.commuregw.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityNetworkSettingsBinding;
import com.moko.commuregw.entity.GatewayConfig;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.MsgConfigResult;
import com.moko.support.commuregw.entity.MsgReadResult;
import com.moko.support.commuregw.event.DeviceOnlineEvent;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModifyNetworkSettingsActivity extends BaseActivity<ActivityNetworkSettingsBinding> {
    private final String IP_REGEX = "^((25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))\\.){3}(25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))$";

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    public Handler mHandler;

    private Pattern pattern;
    private GatewayConfig mGatewayConfig;
    private boolean mIsRetainParams;

    @Override
    protected void onCreate() {
        mGatewayConfig = getIntent().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
        mIsRetainParams = mGatewayConfig != null;
        pattern = Pattern.compile(IP_REGEX);
        mBind.cbDhcp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mBind.clIp.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        });
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        if (mIsRetainParams) {
            mBind.cbDhcp.setChecked(mGatewayConfig.dhcp == 1);
            mBind.clIp.setVisibility(mGatewayConfig.dhcp == 1 ? View.GONE : View.VISIBLE);

            String ip = mGatewayConfig.ip;
            String mask = mGatewayConfig.mask;
            String gateway = mGatewayConfig.gateway;
            String dns = mGatewayConfig.dns;
            mBind.etIp.setText(ip);
            mBind.etMask.setText(mask);
            mBind.etGateway.setText(gateway);
            mBind.etDns.setText(dns);
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getNetworkSettings();
    }

    @Override
    protected ActivityNetworkSettingsBinding getViewBinding() {
        return ActivityNetworkSettingsBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.READ_MSG_ID_NETWORK_SETTINGS) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int enable = result.data.get("dhcp_en").getAsInt();
            mBind.cbDhcp.setChecked(enable == 1);
            mBind.clIp.setVisibility(enable == 1 ? View.GONE : View.VISIBLE);

            mBind.etIp.setText( result.data.get("ip").getAsString());
            mBind.etMask.setText(result.data.get("netmask").getAsString());
            mBind.etGateway.setText(result.data.get("gw").getAsString());
            mBind.etDns.setText(result.data.get("dns").getAsString());
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
                ToastUtils.showToast(this, "Set up succeed");
            } else {
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

    private void setNetworkSettings() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_NETWORK_SETTINGS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("dhcp_en", mBind.cbDhcp.isChecked() ? 1 : 0);
        jsonObject.addProperty("ip", mBind.etIp.getText().toString());
        jsonObject.addProperty("netmask", mBind.etMask.getText().toString());
        jsonObject.addProperty("gw", mBind.etGateway.getText().toString());
        jsonObject.addProperty("dns", mBind.etDns.getText().toString());
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getNetworkSettings() {
        int msgId = MQTTConstants.READ_MSG_ID_NETWORK_SETTINGS;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (!isParaError()) {
            if (mIsRetainParams) {
                String ip = mBind.etIp.getText().toString();
                String mask = mBind.etMask.getText().toString();
                String gateway = mBind.etGateway.getText().toString();
                String dns = mBind.etDns.getText().toString();
                mGatewayConfig.dhcp = mBind.cbDhcp.isChecked() ? 1 : 0;
                mGatewayConfig.ip = ip;
                mGatewayConfig.mask = mask;
                mGatewayConfig.gateway = gateway;
                mGatewayConfig.dns = dns;
                ToastUtils.showToast(this, "Setup succeed！");
                Intent intent = new Intent();
                intent.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
                setResult(RESULT_OK, intent);
                finish();
                return;
            }
            if (!MQTTSupport.getInstance().isConnected()) {
                ToastUtils.showToast(this, R.string.network_error);
                return;
            }
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            setNetworkSettings();
        } else {
            ToastUtils.showToast(this, "Para Error");
        }
    }

    private boolean isParaError() {
        if (!mBind.cbDhcp.isChecked()) {
            String ip = mBind.etIp.getText().toString();
            String mask = mBind.etMask.getText().toString();
            String gateway = mBind.etGateway.getText().toString();
            String dns = mBind.etDns.getText().toString();
            Matcher matcherIp = pattern.matcher(ip);
            Matcher matcherMask = pattern.matcher(mask);
            Matcher matcherGateway = pattern.matcher(gateway);
            Matcher matcherDns = pattern.matcher(dns);
            if (!matcherIp.matches()
                    || !matcherMask.matches()
                    || !matcherGateway.matches()
                    || !matcherDns.matches())
                return true;
        }
        return false;
    }
}
