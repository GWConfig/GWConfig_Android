package com.moko.commuregw.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.adapter.BleDeviceAdapter;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityBleDevicesBinding;
import com.moko.commuregw.db.DBTools;
import com.moko.commuregw.dialog.PasswordRemoteBleDialog;
import com.moko.commuregw.dialog.ScanFilterDialog;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.MokoSupport;
import com.moko.support.commuregw.entity.BXPButtonInfo;
import com.moko.support.commuregw.entity.BleDevice;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.event.DeviceModifyNameEvent;
import com.moko.support.commuregw.event.DeviceOnlineEvent;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import androidx.recyclerview.widget.LinearLayoutManager;

public class BleManagerActivity extends BaseActivity<ActivityBleDevicesBinding> implements BaseQuickAdapter.OnItemChildClickListener {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    private BleDeviceAdapter mAdapter;
    private ArrayList<BleDevice> mBleDevices;
    private ConcurrentHashMap<String, BleDevice> mBleDevicesMap;
    private Handler mHandler;
    private int mIndex;
    private BXPButtonInfo mBXPButtonInfo;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());

        mBind.tvDeviceName.setText(mMokoDevice.name);
        mBleDevices = new ArrayList<>();
        mBleDevicesMap = new ConcurrentHashMap<>();
        mAdapter = new BleDeviceAdapter();
        mAdapter.openLoadAnimation();
        mAdapter.replaceData(mBleDevices);
        mAdapter.setOnItemChildClickListener(this);
        mBind.rvDevices.setLayoutManager(new LinearLayoutManager(this));
        mBind.rvDevices.setAdapter(mAdapter);
        refreshList();
    }

    private void refreshList() {
        new Thread(() -> {
            while (refreshFlag) {
                runOnUiThread(() -> {
                    mAdapter.replaceData(mBleDevices);
                });
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                updateDevices();
            }
        }).start();
    }

    @Override
    protected ActivityBleDevicesBinding getViewBinding() {
        return ActivityBleDevicesBinding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.POSTING, priority = 100)
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_SCAN_RESULT) {
            EventBus.getDefault().cancelEventDelivery(event);
            runOnUiThread(() -> {
                Type type = new TypeToken<MsgNotify<List<BleDevice>>>() {
                }.getType();
                MsgNotify<List<BleDevice>> result = new Gson().fromJson(message, type);
                if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                    return;
                List<BleDevice> bleDevices = result.data;
                for (BleDevice device : bleDevices) {
                    if (device.rssi < filterRssi)
                        continue;
                    if (device.type_code == 1)
                        continue;
                    if (!mBleDevicesMap.containsKey(device.mac)) {
                        device.index = mIndex++;
                        mBleDevicesMap.put(device.mac, device);
                    } else {
                        BleDevice existDevice = mBleDevicesMap.get(device.mac);
                        existDevice.rssi = device.rssi;
                        existDevice.adv_name = device.adv_name;
                        existDevice.type_code = device.type_code;
                        existDevice.connectable = device.connectable;
                    }
                }
            });
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_CONNECT_RESULT) {
            runOnUiThread(() -> {
                Type type = new TypeToken<MsgNotify<BXPButtonInfo>>() {
                }.getType();
                MsgNotify<BXPButtonInfo> result = new Gson().fromJson(message, type);
                if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                    return;
                mBXPButtonInfo = result.data;
                if (mBXPButtonInfo.result_code != 0) {
                    dismissLoadingProgressDialog();
                    mHandler.removeMessages(0);
                    ToastUtils.showToast(this, mBXPButtonInfo.result_msg);
                    return;
                }
                getBXPButtonStatus();
            });
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_ALARM_STATUS) {
            EventBus.getDefault().cancelEventDelivery(event);
            runOnUiThread(() -> {
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
                mBXPButtonInfo.alarm_status = alarm_status;
                getBXPButtonTagId();
            });
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_GET_TAG_ID) {
            EventBus.getDefault().cancelEventDelivery(event);
            runOnUiThread(() -> {
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
                String tag_id = result.data.get("tag_id").getAsString();
                mBXPButtonInfo.tag_id = tag_id;
                getBXPButtonPasswordVerify();
            });
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_GET_PASSWORD_VERIFY) {
            EventBus.getDefault().cancelEventDelivery(event);
            runOnUiThread(() -> {
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
                int switch_value = result.data.get("switch_value").getAsInt();
                mBXPButtonInfo.switch_value = switch_value;
                ToastUtils.showToast(this, "Setup succeed");
                Intent intent = new Intent(this, BXPButtonInfoActivity.class);
                intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
                intent.putExtra(AppConstants.EXTRA_KEY_BXP_BUTTON_INFO, mBXPButtonInfo);
                startActivity(intent);
            });
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceModifyNameEvent(DeviceModifyNameEvent event) {
        // 修改了设备名称
        MokoDevice device = DBTools.getInstance(BleManagerActivity.this).selectDevice(mMokoDevice.mac);
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

    private boolean refreshFlag = true;
    public String filterMac;
    public int filterRssi = -127;

    private void updateDevices() {
        mBleDevices.clear();
        if (!TextUtils.isEmpty(filterMac) || filterRssi != -127) {
            ArrayList<BleDevice> bleDevices = new ArrayList<>(mBleDevicesMap.values());
            Iterator<BleDevice> iterator = bleDevices.iterator();
            while (iterator.hasNext()) {
                BleDevice bleDevice = iterator.next();
                if (bleDevice.rssi > filterRssi) {
                    if (TextUtils.isEmpty(filterMac)) {
                        continue;
                    } else {
                        if (!TextUtils.isEmpty(filterMac) && TextUtils.isEmpty(bleDevice.mac)) {
                            iterator.remove();
                        } else if (!TextUtils.isEmpty(filterMac) && bleDevice.mac.toLowerCase().replaceAll(":", "").contains(filterMac.toLowerCase())) {
                            continue;
                        } else {
                            iterator.remove();
                        }
                    }
                } else {
                    iterator.remove();
                }
            }
            mBleDevices.addAll(bleDevices);
        } else {
            mBleDevices.addAll(mBleDevicesMap.values());
        }
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
        Collections.sort(mBleDevices, (lhs, rhs) -> {
            if (lhs.index > rhs.index) {
                return 1;
            } else if (lhs.index < rhs.index) {
                return -1;
            }
            return 0;
        });
    }

    public void onFilter(View view) {
        if (isWindowLocked())
            return;
        ScanFilterDialog scanFilterDialog = new ScanFilterDialog();
        scanFilterDialog.setFilterMac(filterMac);
        scanFilterDialog.setFilterRssi(filterRssi);
        scanFilterDialog.setOnScanFilterListener((filterMac, filterRssi) -> {
            BleManagerActivity.this.filterMac = filterMac;
            BleManagerActivity.this.filterRssi = filterRssi;
            if (!TextUtils.isEmpty(filterMac) || filterRssi != -127) {
                mBind.rlFilter.setVisibility(View.VISIBLE);
                mBind.tvEditFilter.setVisibility(View.GONE);
                StringBuilder stringBuilder = new StringBuilder();
                if (!TextUtils.isEmpty(filterMac)) {
                    stringBuilder.append(filterMac);
                    stringBuilder.append(";");
                }
                if (filterRssi != -127) {
                    stringBuilder.append(String.format("%sdBm", filterRssi + ""));
                    stringBuilder.append(";");
                }
                mBind.tvFilter.setText(stringBuilder.toString());
            } else {
                mBind.rlFilter.setVisibility(View.GONE);
                mBind.tvEditFilter.setVisibility(View.VISIBLE);
            }
            mBleDevicesMap.clear();
            mIndex = 0;
        });
        scanFilterDialog.show(getSupportFragmentManager());
    }

    public void onFilterDelete(View view) {
        if (isWindowLocked())
            return;
        mBind.rlFilter.setVisibility(View.GONE);
        mBind.tvEditFilter.setVisibility(View.VISIBLE);
        filterMac = "";
        filterRssi = -127;
        mBleDevicesMap.clear();
        mIndex = 0;
    }

    @Override
    public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
        if (isWindowLocked()) return;
        BleDevice bleDevice = (BleDevice) adapter.getItem(position);
        if (bleDevice == null) return;
        // BXP-Button
        // show password
        final PasswordRemoteBleDialog dialog = new PasswordRemoteBleDialog();
        dialog.setOnPasswordClicked(password -> {
            if (!MokoSupport.getInstance().isBluetoothOpen()) {
                MokoSupport.getInstance().enableBluetooth();
                return;
            }
            XLog.i(password);
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(BleManagerActivity.this, "Setup failed");
            }, 50 * 1000);
            showLoadingProgressDialog();
            getBleDeviceInfo(bleDevice, password);
        });
        dialog.show(getSupportFragmentManager());
    }

    private void getBleDeviceInfo(BleDevice bleDevice, String password) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_CONNECT;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", bleDevice.mac);
        jsonObject.addProperty("passwd", password);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getBXPButtonStatus() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_ALARM_STATUS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getBXPButtonTagId() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_GET_TAG_ID;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getBXPButtonPasswordVerify() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_GET_PASSWORD_VERIFY;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        if (isWindowLocked()) return;
        back();
    }

    public void onBack(View view) {
        if (isWindowLocked()) return;
        back();
    }

    private void back() {
        if (refreshFlag)
            refreshFlag = false;
        finish();
    }
}
