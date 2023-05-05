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
import com.moko.commuregw.databinding.ActivityBatteryTestParamsBinding;
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

public class BatteryTestParamsActivity extends BaseActivity<ActivityBatteryTestParamsBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    private BXPButtonInfo mBXPButtonInfo;
    public Handler mHandler;
    private ArrayList<String> mValues;
    private int mSelected;
    private int mMode = 0;

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
        mBind.cbLedSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mBind.clLedParams.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getBatteryTestParams();
    }

    @Override
    protected ActivityBatteryTestParamsBinding getViewBinding() {
        return ActivityBatteryTestParamsBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_GET_BATTERY_TEST_PARAMS) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            String mac = result.data.get("mac").getAsString();
            if (!mBXPButtonInfo.mac.equalsIgnoreCase(mac)) return;
            int result_code = result.data.get("result_code").getAsInt();
            if (result_code != 0) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            int threshold = result.data.get("batt_warn_threshold").getAsInt();
            int ledSwitch = result.data.get("led_warn_switch").getAsInt();
            mBind.cbLedSwitch.setChecked(ledSwitch == 1);
            mBind.etVoltageThreshold.setText(String.valueOf(threshold));
            getLEDParams(0);
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_GET_LED_PARAMS) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            String mac = result.data.get("mac").getAsString();
            if (!mBXPButtonInfo.mac.equalsIgnoreCase(mac)) return;
            int result_code = result.data.get("result_code").getAsInt();
            if (result_code != 0) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            int mode = result.data.get("batt_warn_mode").getAsInt();
            String ledColor = result.data.get("led_color").getAsString();
            if ("red".equalsIgnoreCase(ledColor))
                mSelected = 0;
            if ("blue".equalsIgnoreCase(ledColor))
                mSelected = 1;
            if ("green".equalsIgnoreCase(ledColor))
                mSelected = 2;
            mBind.tvLedColor.setText(mValues.get(mSelected));
            int ledInterval = result.data.get("led_off_time").getAsInt();
            int ledDuration = result.data.get("led_flash_time").getAsInt();
            if (mode == 0) {
                mBind.etLowerInterval.setText(String.valueOf(ledInterval));
                mBind.etLowerDuration.setText(String.valueOf(ledDuration));
                // higher
                getLEDParams(1);
            } else {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                mBind.etHigherInterval.setText(String.valueOf(ledInterval));
                mBind.etHigherDuration.setText(String.valueOf(ledDuration));
            }

        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_SET_BATTERY_TEST_PARAMS) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            String mac = result.data.get("mac").getAsString();
            if (!mBXPButtonInfo.mac.equalsIgnoreCase(mac)) return;
            int result_code = result.data.get("result_code").getAsInt();
            if (result_code == 0) {
                if (mBind.cbLedSwitch.isChecked()) {
                    mMode = 0;
                    setLEDParams(mMode);
                    return;
                }
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_SET_LED_PARAMS) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            String mac = result.data.get("mac").getAsString();
            if (!mBXPButtonInfo.mac.equalsIgnoreCase(mac)) return;
            int result_code = result.data.get("result_code").getAsInt();
            if (mMode == 1) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            if (result_code == 0) {
                if (mMode == 0) {
                    mMode = 1;
                    setLEDParams(mMode);
                    return;
                }
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Set up failed");
            }
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

    private void getBatteryTestParams() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_GET_BATTERY_TEST_PARAMS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getLEDParams(int mode) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_GET_LED_PARAMS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("batt_warn_mode", mode);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setBatteryTestParams(int threshold) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SET_BATTERY_TEST_PARAMS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("batt_warn_threshold", threshold);
        jsonObject.addProperty("led_warn_switch", mBind.cbLedSwitch.isChecked() ? 1 : 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setLEDParams(int mode) {
        String lowerIntervalStr;
        String lowerDurationStr;
        if (mode == 0) {
            lowerIntervalStr = mBind.etLowerInterval.getText().toString();
            lowerDurationStr = mBind.etLowerDuration.getText().toString();
        } else {
            lowerIntervalStr = mBind.etHigherInterval.getText().toString();
            lowerDurationStr = mBind.etHigherDuration.getText().toString();
        }
        int lowerInterval = Integer.parseInt(lowerIntervalStr);
        int lowerDuration = Integer.parseInt(lowerDurationStr);
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SET_LED_PARAMS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("led_color", mValues.get(mSelected).toLowerCase());
        jsonObject.addProperty("batt_warn_mode", mode);
        jsonObject.addProperty("led_off_time", lowerInterval);
        jsonObject.addProperty("led_flash_time", lowerDuration);
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
        String thresholdStr = mBind.etVoltageThreshold.getText().toString();
        if (TextUtils.isEmpty(thresholdStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int threshold = Integer.parseInt(thresholdStr);
        if (threshold < 2000 || threshold > 3600) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        if (mBind.cbLedSwitch.isChecked()) {
            String higherIntervalStr = mBind.etHigherInterval.getText().toString();
            String higherDurationStr = mBind.etHigherDuration.getText().toString();
            String lowerIntervalStr = mBind.etLowerInterval.getText().toString();
            String lowerDurationStr = mBind.etLowerDuration.getText().toString();
            if (TextUtils.isEmpty(higherIntervalStr)
                    || TextUtils.isEmpty(higherDurationStr)
                    || TextUtils.isEmpty(lowerIntervalStr)
                    || TextUtils.isEmpty(lowerDurationStr)) {
                ToastUtils.showToast(this, "Para Error");
                return;
            }
            int higherInterval = Integer.parseInt(higherIntervalStr);
            int lowerInterval = Integer.parseInt(lowerIntervalStr);
            if (higherInterval > 100 || lowerInterval > 100) {
                ToastUtils.showToast(this, "Para Error");
                return;
            }
            int higherDuration = Integer.parseInt(higherDurationStr);
            int lowerDuration = Integer.parseInt(lowerDurationStr);
            if (higherDuration < 1 || higherDuration > 255
                    || lowerDuration < 1 || lowerDuration > 255) {
                ToastUtils.showToast(this, "Para Error");
                return;
            }
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
        setBatteryTestParams(threshold);
    }
}
