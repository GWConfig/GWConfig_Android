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
import com.moko.commuregw.databinding.ActivityAdvParamsBinding;
import com.moko.commuregw.dialog.BottomDialog;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.entity.TxPowerEnum;
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

public class AdvParamsActivity extends BaseActivity<ActivityAdvParamsBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    private BXPButtonInfo mBXPButtonInfo;
    public Handler mHandler;
    private ArrayList<String> mValues;
    private int mConfigSelected;
    private int mNormalSelected;
    private int mTriggerSelected;
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
        mValues.add("-40 dBm");
        mValues.add("-20 dBm");
        mValues.add("-16 dBm");
        mValues.add("-12 dBm");
        mValues.add("-8 dBm");
        mValues.add("-4 dBm");
        mValues.add("0 dBm");
        mValues.add("3 dBm");
        mValues.add("4 dBm");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getAdvParams(0);
    }

    @Override
    protected ActivityAdvParamsBinding getViewBinding() {
        return ActivityAdvParamsBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_GET_ADV_PARAMS) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            int result_code = result.data.get("result_code").getAsInt();
            if (result_code != 0) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            int mode = result.data.get("adv_mode").getAsInt();
            int interval = result.data.get("adv_interval").getAsInt();
            int duration = result.data.get("adv_time").getAsInt();
            int txPower = result.data.get("tx_power").getAsInt();
            if (mode == 0) {
                mConfigSelected = TxPowerEnum.fromTxPower(txPower).ordinal();
                mBind.etConfigAdvInterval.setText(String.valueOf(interval));
                mBind.etConfigAdvDuration.setText(String.valueOf(duration));
                mBind.tvConfigTxPower.setText(mValues.get(mConfigSelected));
                getAdvParams(1);
            } else if (mode == 1) {
                mNormalSelected = TxPowerEnum.fromTxPower(txPower).ordinal();
                mBind.etNormalAdvInterval.setText(String.valueOf(interval));
                mBind.tvNormalTxPower.setText(mValues.get(mNormalSelected));
                getAdvParams(2);
            } else if (mode == 2) {
                mTriggerSelected = TxPowerEnum.fromTxPower(txPower).ordinal();
                mBind.etTriggerAdvInterval.setText(String.valueOf(interval));
                mBind.etTriggerAdvDuration.setText(String.valueOf(duration));
                mBind.tvTriggerTxPower.setText(mValues.get(mTriggerSelected));
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_SET_ADV_PARAMS) {
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
            if (mMode == 2) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            if (result_code == 0) {
                if (mMode == 0) {
                    mMode = 1;
                    setAdvParams(mMode);
                    return;
                } else if (mMode == 1) {
                    mMode = 2;
                    setAdvParams(mMode);
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

    private void getAdvParams(int mode) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_GET_ADV_PARAMS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("adv_mode", mode);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setAdvParams(int mode) {
        String intervalStr;
        String durationStr = "1";
        int txPower;
        if (mode == 0) {
            intervalStr = mBind.etConfigAdvInterval.getText().toString();
            durationStr = mBind.etConfigAdvDuration.getText().toString();
            txPower = TxPowerEnum.fromOrdinal(mConfigSelected).getTxPower();
        } else if (mode == 1) {
            intervalStr = mBind.etNormalAdvInterval.getText().toString();
            txPower = TxPowerEnum.fromOrdinal(mNormalSelected).getTxPower();
        } else {
            intervalStr = mBind.etTriggerAdvInterval.getText().toString();
            durationStr = mBind.etTriggerAdvDuration.getText().toString();
            txPower = TxPowerEnum.fromOrdinal(mTriggerSelected).getTxPower();
        }
        int interval = Integer.parseInt(intervalStr);
        int duration = Integer.parseInt(durationStr);
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SET_ADV_PARAMS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("adv_mode", mode);
        jsonObject.addProperty("adv_interval", interval);
        jsonObject.addProperty("tx_power", txPower);
        jsonObject.addProperty("adv_time", duration);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onSelectConfigTxPower(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mValues, mConfigSelected);
        dialog.setListener(value -> {
            mConfigSelected = value;
            mBind.tvConfigTxPower.setText(mValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSelectNormalTxPower(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mValues, mNormalSelected);
        dialog.setListener(value -> {
            mNormalSelected = value;
            mBind.tvNormalTxPower.setText(mValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSelectTriggerTxPower(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mValues, mTriggerSelected);
        dialog.setListener(value -> {
            mTriggerSelected = value;
            mBind.tvTriggerTxPower.setText(mValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;

        String configIntervalStr = mBind.etConfigAdvInterval.getText().toString();
        String configDurationStr = mBind.etConfigAdvDuration.getText().toString();
        String normalIntervalStr = mBind.etNormalAdvInterval.getText().toString();
        String triggerIntervalStr = mBind.etTriggerAdvInterval.getText().toString();
        String triggerDurationStr = mBind.etTriggerAdvDuration.getText().toString();
        if (TextUtils.isEmpty(configIntervalStr)
                || TextUtils.isEmpty(configDurationStr)
                || TextUtils.isEmpty(normalIntervalStr)
                || TextUtils.isEmpty(triggerIntervalStr)
                || TextUtils.isEmpty(triggerDurationStr)) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int configInterval = Integer.parseInt(configIntervalStr);
        int normalInterval = Integer.parseInt(normalIntervalStr);
        int triggerInterval = Integer.parseInt(triggerIntervalStr);
        if (configInterval < 100 || normalInterval < 100 || triggerInterval < 100
                || configInterval > 65535 || normalInterval > 65535 || triggerInterval > 65535) {
            ToastUtils.showToast(this, "Para Error");
            return;
        }
        int configDuration = Integer.parseInt(configDurationStr);
        int triggerDuration = Integer.parseInt(triggerDurationStr);
        if (configDuration < 1 || configDuration > 65535
                || triggerDuration < 1 || triggerDuration > 65535) {
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
        mMode = 0;
        setAdvParams(mMode);
    }
}
