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
import com.moko.commuregw.databinding.ActivityAccParamsBinding;
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

public class AccParamsActivity extends BaseActivity<ActivityAccParamsBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    private BXPButtonInfo mBXPButtonInfo;
    public Handler mHandler;
    private ArrayList<String> mSampleRateValues;
    private ArrayList<String> mFullScaleValues;
    private ArrayList<String> mFullScaleUnitValues;
    private int mSampleRateSelected = 1;
    private int mFullScaleSelected = 1;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        mBXPButtonInfo = (BXPButtonInfo) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_BXP_BUTTON_INFO);
        mSampleRateValues = new ArrayList<>();
        mSampleRateValues.add("1Hz");
        mSampleRateValues.add("10Hz");
        mSampleRateValues.add("25Hz");
        mSampleRateValues.add("50Hz");
        mSampleRateValues.add("100Hz");
        mFullScaleValues = new ArrayList<>();
        mFullScaleValues.add("±2g");
        mFullScaleValues.add("±4g");
        mFullScaleValues.add("±8g");
        mFullScaleValues.add("±16g");
        mFullScaleUnitValues = new ArrayList<>();
        mFullScaleUnitValues.add("x3.91mg");
        mFullScaleUnitValues.add("x7.81mg");
        mFullScaleUnitValues.add("x15.63mg");
        mFullScaleUnitValues.add("x31.25mg");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getAccParams();
    }

    @Override
    protected ActivityAccParamsBinding getViewBinding() {
        return ActivityAccParamsBinding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_GET_ACC_PARAMS) {
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
            mSampleRateSelected = result.data.get("sampling_rate").getAsInt();
            mFullScaleSelected = result.data.get("full_scale").getAsInt();
            int sensitivity = result.data.get("sensitivity").getAsInt();
            mBind.tvSampleRate.setText(mSampleRateValues.get(mSampleRateSelected));
            mBind.tvFullScale.setText(mFullScaleValues.get(mFullScaleSelected));
            mBind.tvScaleUnit.setText(mFullScaleUnitValues.get(mFullScaleSelected));
            mBind.etSensitivity.setText(String.valueOf(sensitivity));
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_SET_ACC_PARAMS) {
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
            if (result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
            } else {
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

    private void getAccParams() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_GET_ACC_PARAMS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setAccParams(int sensitivity) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SET_ACC_PARAMS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("sampling_rate", mSampleRateSelected);
        jsonObject.addProperty("full_scale", mFullScaleSelected);
        jsonObject.addProperty("sensitivity", sensitivity);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onSelectSampleRate(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mSampleRateValues, mSampleRateSelected);
        dialog.setListener(value -> {
            mSampleRateSelected = value;
            mBind.tvSampleRate.setText(mSampleRateValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSelectFullScale(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mFullScaleValues, mFullScaleSelected);
        dialog.setListener(value -> {
            mFullScaleSelected = value;
            mBind.tvFullScale.setText(mFullScaleValues.get(value));
            mBind.tvScaleUnit.setText(mFullScaleUnitValues.get(mFullScaleSelected));
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        String sensitivityStr = mBind.etSensitivity.getText().toString();
        if (TextUtils.isEmpty(sensitivityStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int sensitivity = Integer.parseInt(sensitivityStr);
        if (sensitivity > 255) {
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
        setAccParams(sensitivity);
    }
}
