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
import com.moko.commuregw.databinding.ActivityDeviceSelfTestParamsBinding;
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

public class DeviceSelfTestParamsActivity extends BaseActivity<ActivityDeviceSelfTestParamsBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    private BXPButtonInfo mBXPButtonInfo;
    public Handler mHandler;
    private ArrayList<String> mValues;
    private int mHigherSelected;
    private int mLowerSelected;
    private int mMode = 0;

    private int mThreshold;

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
        getDeviceTestParams(MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_GET_LED_BUZZER_TEST_PARAMS);
    }

    @Override
    protected ActivityDeviceSelfTestParamsBinding getViewBinding() {
        return ActivityDeviceSelfTestParamsBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_GET_LED_BUZZER_TEST_PARAMS) {
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
            int ledFlashTime = result.data.get("led_flash_time").getAsInt();
            int buzzerOffTime = result.data.get("buzzer_off_time").getAsInt();
            int buzzerWorkTime = result.data.get("buzzer_work_time").getAsInt();
            mBind.etBlinkingDuration.setText(String.valueOf(ledFlashTime));
            mBind.etBeepInterval.setText(String.valueOf(buzzerOffTime / 100));
            mBind.etBeepDuration.setText(String.valueOf(buzzerWorkTime / 100));
            getDeviceTestParams(MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_GET_BATTERY_TEST_PARAMS);
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
            int ledInterval = result.data.get("led_off_time").getAsInt();
            int ledDuration = result.data.get("led_flash_time").getAsInt();
            if (mode == 0) {
                if ("red".equalsIgnoreCase(ledColor))
                    mLowerSelected = 0;
                if ("blue".equalsIgnoreCase(ledColor))
                    mLowerSelected = 1;
                if ("green".equalsIgnoreCase(ledColor))
                    mLowerSelected = 2;
                mBind.tvLowerLedColor.setText(mValues.get(mLowerSelected));
                mBind.etLowerInterval.setText(String.valueOf(ledInterval / 100));
                mBind.etLowerDuration.setText(String.valueOf(ledDuration));
                // higher
                getLEDParams(1);
            } else {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                if ("red".equalsIgnoreCase(ledColor))
                    mHigherSelected = 0;
                if ("blue".equalsIgnoreCase(ledColor))
                    mHigherSelected = 1;
                if ("green".equalsIgnoreCase(ledColor))
                    mHigherSelected = 2;
                mBind.tvHigherLedColor.setText(mValues.get(mHigherSelected));
                mBind.etHigherInterval.setText(String.valueOf(ledInterval / 100));
                mBind.etHigherDuration.setText(String.valueOf(ledDuration));
            }

        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_SET_LED_BUZZER_TEST_PARAMS) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            String mac = result.data.get("mac").getAsString();
            if (!mBXPButtonInfo.mac.equalsIgnoreCase(mac)) return;
            int result_code = result.data.get("result_code").getAsInt();
            if (result_code == 0) {
                setBatteryTestParams(mThreshold);
                return;
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
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

    private void getDeviceTestParams(int msgId) {
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

    private void setLEDBuzzerTestParams(int ledFlashTime, int interval, int duration) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SET_LED_BUZZER_TEST_PARAMS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("led_flash_time", ledFlashTime);
        jsonObject.addProperty("buzzer_off_time", interval * 100);
        jsonObject.addProperty("buzzer_work_time", duration * 100);
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
        String selectedStr;
        if (mode == 0) {
            lowerIntervalStr = mBind.etLowerInterval.getText().toString();
            lowerDurationStr = mBind.etLowerDuration.getText().toString();
            selectedStr = mValues.get(mLowerSelected).toLowerCase();
        } else {
            lowerIntervalStr = mBind.etHigherInterval.getText().toString();
            lowerDurationStr = mBind.etHigherDuration.getText().toString();
            selectedStr = mValues.get(mHigherSelected).toLowerCase();
        }
        int lowerInterval = Integer.parseInt(lowerIntervalStr) * 100;
        int lowerDuration = Integer.parseInt(lowerDurationStr);
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SET_LED_PARAMS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("led_color", selectedStr);
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

    public void onSelectLEDHigherColor(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mValues, mHigherSelected);
        dialog.setListener(value -> {
            mHigherSelected = value;
            mBind.tvHigherLedColor.setText(mValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSelectLEDLowerColor(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mValues, mLowerSelected);
        dialog.setListener(value -> {
            mLowerSelected = value;
            mBind.tvLowerLedColor.setText(mValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        String ledFlashTimeStr = mBind.etBlinkingDuration.getText().toString();
        if (TextUtils.isEmpty(ledFlashTimeStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int ledFlashTime = Integer.parseInt(ledFlashTimeStr);
        if (ledFlashTime < 1 || ledFlashTime > 255) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        String beepIntervalStr = mBind.etBeepInterval.getText().toString();
        if (TextUtils.isEmpty(beepIntervalStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int beepInterval = Integer.parseInt(beepIntervalStr);
        if (beepInterval > 100) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        String beepDurationStr = mBind.etBeepDuration.getText().toString();
        if (TextUtils.isEmpty(beepDurationStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int beepDuration = Integer.parseInt(beepDurationStr);
        if (beepDuration > 655) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        String thresholdStr = mBind.etVoltageThreshold.getText().toString();
        if (TextUtils.isEmpty(thresholdStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        mThreshold = Integer.parseInt(thresholdStr);
        if (mThreshold < 2000 || mThreshold > 4200) {
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
        setLEDBuzzerTestParams(ledFlashTime, beepInterval, beepDuration);
    }
}
