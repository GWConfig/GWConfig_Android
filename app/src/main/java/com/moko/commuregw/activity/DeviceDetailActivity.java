package com.moko.commuregw.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.adapter.ScanDeviceAdapter;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityDetailRemoteBinding;
import com.moko.commuregw.db.DBTools;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.BXPButtonInfo;
import com.moko.support.commuregw.entity.MsgConfigResult;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.entity.MsgReadResult;
import com.moko.support.commuregw.entity.OtherDeviceInfo;
import com.moko.support.commuregw.event.DeviceModifyNameEvent;
import com.moko.support.commuregw.event.DeviceOnlineEvent;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;

public class DeviceDetailActivity extends BaseActivity<ActivityDetailRemoteBinding> {
    public static final String TAG = DeviceDetailActivity.class.getSimpleName();

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    private boolean mScanSwitch;
    private ScanDeviceAdapter mAdapter;
    private ArrayList<String> mScanDevices;
    private Handler mHandler;
    private BXPButtonInfo mConnectedBXPButtonInfo;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());

        mBind.tvDeviceName.setText(mMokoDevice.name);
        mScanDevices = new ArrayList<>();
        mAdapter = new ScanDeviceAdapter();
        mAdapter.openLoadAnimation();
        mAdapter.replaceData(mScanDevices);
        mBind.rvDevices.setLayoutManager(new LinearLayoutManager(this));
        mBind.rvDevices.setAdapter(mAdapter);

        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getScanConfig();
    }

    @Override
    protected ActivityDetailRemoteBinding getViewBinding() {
        return ActivityDetailRemoteBinding.inflate(getLayoutInflater());
    }

    private void changeView() {
        mBind.ivScanSwitch.setImageResource(mScanSwitch ? R.drawable.checkbox_open : R.drawable.checkbox_close);
        mBind.tvScanDeviceTotal.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
        mBind.tvScanDeviceTotal.setText(getString(R.string.scan_device_total, mScanDevices.size()));
        mBind.tvManageDevices.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
        mBind.rvDevices.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        XLog.i(TAG + "-->onNewIntent...");
        setIntent(intent);
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
        if (msg_id == MQTTConstants.READ_MSG_ID_SCAN_CONFIG) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mScanSwitch = result.data.get("scan_switch").getAsInt() == 1;
            changeView();
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_SCAN_RESULT) {
            Type type = new TypeToken<MsgNotify<List<JsonObject>>>() {
            }.getType();
            MsgNotify<List<JsonObject>> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            for (JsonObject jsonObject : result.data) {
                mScanDevices.add(0, jsonObject.toString());
            }
            mBind.tvScanDeviceTotal.setText(getString(R.string.scan_device_total, mScanDevices.size()));
            mAdapter.replaceData(mScanDevices);
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_SCAN_CONFIG) {
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
        if (msg_id == MQTTConstants.READ_MSG_ID_BLE_CONNECTED_LIST) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            JsonObject data = result.data;
            JsonArray list = data.get("mac").getAsJsonArray();
            if (list != null && !list.isEmpty()) {
                mConnectedBXPButtonInfo = new BXPButtonInfo();
                readBXPButtonInfo(list.get(0).getAsString());
            } else {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                Intent intent = new Intent(this, BleManagerActivity.class);
                intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
                startActivity(intent);
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_INFO) {
            Type type = new TypeToken<MsgNotify<BXPButtonInfo>>() {
            }.getType();
            MsgNotify<BXPButtonInfo> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            BXPButtonInfo bxpButtonInfo = result.data;
            if (bxpButtonInfo.result_code != 0) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            mConnectedBXPButtonInfo.mac = bxpButtonInfo.mac;
            mConnectedBXPButtonInfo.product_model = bxpButtonInfo.product_model;
            mConnectedBXPButtonInfo.company_name = bxpButtonInfo.company_name;
            mConnectedBXPButtonInfo.hardware_version = bxpButtonInfo.hardware_version;
            mConnectedBXPButtonInfo.software_version = bxpButtonInfo.software_version;
            mConnectedBXPButtonInfo.firmware_version = bxpButtonInfo.firmware_version;
            readBXPButtonStatus(bxpButtonInfo.mac);
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_ALARM_STATUS) {
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
            int alarm_status = result.data.get("alarm_status").getAsInt();
            mConnectedBXPButtonInfo.alarm_status = alarm_status;
            readBXPButtonBattery(mConnectedBXPButtonInfo.mac);
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_BATTERY) {
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
            int battery_v = result.data.get("battery_v").getAsInt();
            mConnectedBXPButtonInfo.battery_v = battery_v;
            readBXPButtonTagId(mConnectedBXPButtonInfo.mac);
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_GET_TAG_ID) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int result_code = result.data.get("result_code").getAsInt();
            if (result_code != 0) {
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            String tag_id = result.data.get("tag_id").getAsString();
            mConnectedBXPButtonInfo.tag_id = tag_id;
            ToastUtils.showToast(this, "Setup succeed");
            Intent intent = new Intent(this, BXPButtonInfoActivity.class);
            intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
            intent.putExtra(AppConstants.EXTRA_KEY_BXP_BUTTON_INFO, mConnectedBXPButtonInfo);
            startActivity(intent);
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_OTHER_INFO) {
            Type type = new TypeToken<MsgNotify<OtherDeviceInfo>>() {
            }.getType();
            MsgNotify<OtherDeviceInfo> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            OtherDeviceInfo otherDeviceInfo = result.data;
            if (otherDeviceInfo.result_code != 0) {
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            ToastUtils.showToast(this, "Setup succeed");
            Intent intent = new Intent(this, BleOtherInfoActivity.class);
            intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
            intent.putExtra(AppConstants.EXTRA_KEY_OTHER_DEVICE_INFO, otherDeviceInfo);
            startActivity(intent);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceModifyNameEvent(DeviceModifyNameEvent event) {
        // 修改了设备名称
        MokoDevice device = DBTools.getInstance(DeviceDetailActivity.this).selectDevice(mMokoDevice.mac);
        mMokoDevice.name = device.name;
        mBind.tvDeviceName.setText(mMokoDevice.name);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        String mac = event.getMac();
        if (!mMokoDevice.mac.equals(mac))
            return;
        boolean online = event.isOnline();
        if (!online) {
            ToastUtils.showToast(this, "device is off-line");
            finish();
        }
    }

    public void onBack(View view) {
        finish();
    }

    public void onDeviceSetting(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent intent = new Intent(this, DeviceSettingActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    public void onScannerOptionSetting(View view) {
        if (isWindowLocked())
            return;
        // 获取扫描过滤
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, ScannerUploadOptionActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }

    public void onScanSwitch(View view) {
        if (isWindowLocked())
            return;
        // 切换扫描开关
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        mScanSwitch = !mScanSwitch;
        mBind.ivScanSwitch.setImageResource(mScanSwitch ? R.drawable.checkbox_open : R.drawable.checkbox_close);
        mBind.tvManageDevices.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
        mBind.tvScanDeviceTotal.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
        mBind.tvScanDeviceTotal.setText(getString(R.string.scan_device_total, 0));
        mBind.rvDevices.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
        mScanDevices.clear();
        mAdapter.replaceData(mScanDevices);
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setScanConfig();
    }

    public void onManageBleDevices(View view) {
        if (isWindowLocked())
            return;
        // 设置扫描间隔
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getBleConnectedList();
    }

    private void getBleConnectedList() {
        int msgId = MQTTConstants.READ_MSG_ID_BLE_CONNECTED_LIST;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getScanConfig() {
        int msgId = MQTTConstants.READ_MSG_ID_SCAN_CONFIG;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setScanConfig() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_SCAN_CONFIG;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("scan_switch", mScanSwitch ? 1 : 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    private void readOtherInfo(String mac) {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getOtherInfo(mac);
    }

    private void getOtherInfo(String mac) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_OTHER_INFO;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void readBXPButtonInfo(String mac) {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getBXPButtonInfo(mac);
    }

    private void getBXPButtonInfo(String mac) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_INFO;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void readBXPButtonStatus(String mac) {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getBXPButtonStatus(mac);
    }

    private void getBXPButtonStatus(String mac) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_ALARM_STATUS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void readBXPButtonBattery(String mac) {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getBXPButtonBattery(mac);
    }

    private void getBXPButtonBattery(String mac) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_BATTERY;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void readBXPButtonTagId(String mac) {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getBXPButtonTagId(mac);
    }

    private void getBXPButtonTagId(String mac) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_GET_TAG_ID;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
