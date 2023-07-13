package com.moko.commuregw.activity;

import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityBeaconDfuBinding;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.BatchDFUBeacon;
import com.moko.support.commuregw.entity.BleTag;
import com.moko.support.commuregw.entity.MsgConfigResult;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.event.DeviceOnlineEvent;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class BeaconDFUActivity extends BaseActivity<ActivityBeaconDfuBinding> {
    private final String FILTER_ASCII = "[ -~]*";

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    public Handler mHandler;
    private String mBeaconMac;

    @Override
    protected void onCreate() {
        InputFilter inputFilter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        mBind.etFirmwareFileUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256), inputFilter});
        mBind.etInitDataFileUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256), inputFilter});
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        mBeaconMac = getIntent().getStringExtra(AppConstants.EXTRA_KEY_MAC);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected ActivityBeaconDfuBinding getViewBinding() {
        return ActivityBeaconDfuBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_DFU_PERCENT) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            String mac = result.data.get("mac").getAsString();
            if (!mBeaconMac.equalsIgnoreCase(mac)) return;
            int percent = result.data.get("percent").getAsInt();
            if (!isFinishing() && mLoadingMessageDialog != null && mLoadingMessageDialog.isResumed())
                mLoadingMessageDialog.setMessage(String.format("Beacon DFU process: %d%%", percent));
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_DFU_BATCH_RESULT) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingMessageDialog();
            int multiDfuResultCode = result.data.get("multi_dfu_result_code").getAsInt();
            if (multiDfuResultCode == 0) {
                ToastUtils.showToast(this, "Beacon DFU failed!");
                return;
            }
            JsonArray array = result.data.get("fail_dev").getAsJsonArray();
            if (!array.isEmpty()) {
                ToastUtils.showToast(this, "Beacon DFU failed!");
                return;
            }
            ToastUtils.showToast(this, "Beacon DFU successfully!");
            setResult(RESULT_OK);
            finish();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_BATCH_DFU) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            showLoadingMessageDialog("Beacon DFU process: 0%", false);
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

    public void onStartUpdate(View view) {
        if (isWindowLocked()) return;
        String firmwareFileUrlStr = mBind.etFirmwareFileUrl.getText().toString();
        String initDataFileUrlStr = mBind.etInitDataFileUrl.getText().toString();
        if (TextUtils.isEmpty(firmwareFileUrlStr)
                || TextUtils.isEmpty(initDataFileUrlStr)) {
            ToastUtils.showToast(this, "File URL error");
            return;
        }
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        XLog.i("升级固件");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 50 * 1000);
        showLoadingProgressDialog();
        setDFU(firmwareFileUrlStr, initDataFileUrlStr);
    }


    private void setDFU(String firmwareFileUrlStr, String initDataFileUrlStr) {
        ArrayList<BleTag> mBeaconList = new ArrayList<>();
        BleTag bleDevice = new BleTag();
        bleDevice.mac = mBeaconMac;
        bleDevice.passwd = "";
        mBeaconList.add(bleDevice);
        int msgId = MQTTConstants.CONFIG_MSG_ID_BATCH_DFU;
        BatchDFUBeacon batchDFUBeacon = new BatchDFUBeacon();
        batchDFUBeacon.firmware_url = firmwareFileUrlStr;
        batchDFUBeacon.init_data_url = initDataFileUrlStr;
        batchDFUBeacon.ble_dev = mBeaconList;
        Gson gson = new GsonBuilder().setExclusionStrategies(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes f) {
                return f.getName().contains("status");
            }

            @Override
            public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }
        }).create();
        String jsonStr = gson.toJson(batchDFUBeacon);
        JsonObject jsonObject = gson.fromJson(jsonStr, JsonObject.class);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
