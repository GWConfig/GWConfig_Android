package com.moko.commuregw.activity;

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
import com.moko.commuregw.databinding.ActivitySosAlarmNotifyBinding;
import com.moko.commuregw.dialog.BottomDialog;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.BXPButtonInfo;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.event.DeviceOnlineEvent;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class SOSAlarmNotifyActivity extends BaseActivity<ActivitySosAlarmNotifyBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    private BXPButtonInfo mBXPButtonInfo;
    public Handler mHandler;
    private ArrayList<String> mValues;
    private int mSelected;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        mBXPButtonInfo = (BXPButtonInfo) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_BXP_BUTTON_INFO);
        mValues = new ArrayList<>();
        mValues.add("Red");
        mValues.add("Blue");
        mValues.add("Green");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getSOSAlarmNotify(MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_GET_SOS_ALARM);
    }

    @Override
    protected ActivitySosAlarmNotifyBinding getViewBinding() {
        return ActivitySosAlarmNotifyBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_GET_SOS_ALARM) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            String mac = result.data.get("mac").getAsString();
            if (!mBXPButtonInfo.mac.equalsIgnoreCase(mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int result_code = result.data.get("result_code").getAsInt();
            if (result_code != 0) {
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            String ledColor = result.data.get("led_color").getAsString();
            int ledInterval = result.data.get("led_off_time").getAsInt();
            int ledDuration = result.data.get("led_work_time").getAsInt();
            int buzzerOffTime = result.data.get("buzzer_off_time").getAsInt();
            int buzzerWorkTime = result.data.get("buzzer_work_time").getAsInt();
            if ("red".equalsIgnoreCase(ledColor))
                mSelected = 0;
            if ("blue".equalsIgnoreCase(ledColor))
                mSelected = 1;
            if ("green".equalsIgnoreCase(ledColor))
                mSelected = 2;
            mBind.tvLedColor.setText(mValues.get(mSelected));
            mBind.etLedInterval.setText(String.valueOf(ledInterval / 100));
            mBind.etLedDuration.setText(String.valueOf(ledDuration));
            mBind.etBuzzerInterval.setText(String.valueOf(buzzerOffTime / 100));
            mBind.etBuzzerDuration.setText(String.valueOf(buzzerWorkTime / 100));
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_SET_SOS_ALARM) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            String mac = result.data.get("mac").getAsString();
            if (!mBXPButtonInfo.mac.equalsIgnoreCase(mac)) return;
            int result_code = result.data.get("result_code").getAsInt();
            if (result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_DISCONNECTED) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    public void onBack(View view) {
        finish();
    }

    private void getSOSAlarmNotify(int msgId) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setSOSAlarmNotify(int ledInterval, int ledDuration, int buzzerInterval, int buzzerDuration) {
        String selectedStr = mValues.get(mSelected).toLowerCase();
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SET_SOS_ALARM;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("led_color", selectedStr);
        jsonObject.addProperty("led_off_time", ledInterval * 100);
        jsonObject.addProperty("led_work_time", ledDuration);
        jsonObject.addProperty("buzzer_off_time", buzzerInterval * 100);
        jsonObject.addProperty("buzzer_work_time", buzzerDuration * 100);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    public void onSelectLEDColor(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mValues, mSelected);
        dialog.setListener(value -> {
            mSelected = value;
            mBind.tvLedColor.setText(mValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        String ledIntervalStr = mBind.etLedInterval.getText().toString();
        if (TextUtils.isEmpty(ledIntervalStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int ledInterval = Integer.parseInt(ledIntervalStr);
        if (ledInterval > 100) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        String ledDurationStr = mBind.etLedDuration.getText().toString();
        if (TextUtils.isEmpty(ledDurationStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int ledDuration = Integer.parseInt(ledDurationStr);
        if (ledDuration < 1 || ledDuration > 255) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        String buzzerIntervalStr = mBind.etBuzzerInterval.getText().toString();
        if (TextUtils.isEmpty(buzzerIntervalStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int buzzerInterval = Integer.parseInt(buzzerIntervalStr);
        if (buzzerInterval > 100) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        String buzzerDurationStr = mBind.etBuzzerDuration.getText().toString();
        if (TextUtils.isEmpty(buzzerDurationStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int buzzerDuration = Integer.parseInt(buzzerDurationStr);
        if (buzzerDuration > 655) {
            ToastUtils.showToast(this, "Para Error");
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
        setSOSAlarmNotify(ledInterval, ledDuration, buzzerInterval, buzzerDuration);
    }
}
