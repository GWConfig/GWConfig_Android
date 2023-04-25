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
import com.moko.commuregw.databinding.ActivityBxpButtonInfoBinding;
import com.moko.commuregw.db.DBTools;
import com.moko.commuregw.dialog.AlertMessageDialog;
import com.moko.commuregw.dialog.BuzzerReminderDialog;
import com.moko.commuregw.dialog.EncryptionKeyDialog;
import com.moko.commuregw.dialog.LEDReminderDialog;
import com.moko.commuregw.dialog.TagIdDialog;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.BXPButtonInfo;
import com.moko.support.commuregw.entity.BatchDFUBeacon;
import com.moko.support.commuregw.entity.BatchUpdateKey;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.event.DeviceModifyNameEvent;
import com.moko.support.commuregw.event.DeviceOnlineEvent;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class BXPButtonInfoActivity extends BaseActivity<ActivityBxpButtonInfoBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    private BXPButtonInfo mBXPButtonInfo;
    private Handler mHandler;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());

        mBXPButtonInfo = (BXPButtonInfo) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_BXP_BUTTON_INFO);
        mBind.tvDeviceName.setText(mMokoDevice.name);
        mBind.tvProductModel.setText(mBXPButtonInfo.product_model);
        mBind.tvDeviceMac.setText(mBXPButtonInfo.mac);
        mBind.tvTagId.setText(mBXPButtonInfo.tag_id);
        mBind.tvDeviceFirmwareVersion.setText(mBXPButtonInfo.firmware_version);
        mBind.tvBatteryVoltage.setText(String.format("%dmV", mBXPButtonInfo.battery_v));
        String alarmStatusStr = "";
        if (mBXPButtonInfo.alarm_status == 0) {
            alarmStatusStr = "Not triggered";
        } else if (mBXPButtonInfo.alarm_status == 1) {
            alarmStatusStr = "Triggered";
        } else if (mBXPButtonInfo.alarm_status == 2) {
            alarmStatusStr = "Triggered&Dismissed";
        } else if (mBXPButtonInfo.alarm_status == 3) {
            alarmStatusStr = "Self testing";
        }
        mBind.tvAlarmStatus.setText(alarmStatusStr);
        mBind.tvDismissAlarm.setVisibility(mBXPButtonInfo.alarm_status == 1 ? View.VISIBLE : View.GONE);
    }

    @Override
    protected ActivityBxpButtonInfoBinding getViewBinding() {
        return ActivityBxpButtonInfoBinding.inflate(getLayoutInflater());
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

        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_DISMISS_ALARM) {
            Type type = new TypeToken<MsgNotify<BXPButtonInfo>>() {
            }.getType();
            MsgNotify<BXPButtonInfo> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            BXPButtonInfo bxpButtonInfo = result.data;
            if (bxpButtonInfo.result_code != 0) {
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            ToastUtils.showToast(this, "Setup succeed!");
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Setup failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            getBXPButtonStatus();
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_ALARM_STATUS) {
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
            int alarm_status = result.data.get("alarm_status").getAsInt();
            String alarmStatusStr = "";
            if (alarm_status == 0) {
                alarmStatusStr = "Not triggered";
            } else if (alarm_status == 1) {
                alarmStatusStr = "Triggered";
            } else if (alarm_status == 2) {
                alarmStatusStr = "Triggered&Dismissed";
            } else if (alarm_status == 3) {
                alarmStatusStr = "Self testing";
            }
            mBind.tvAlarmStatus.setText(alarmStatusStr);
            mBind.tvDismissAlarm.setVisibility(alarm_status == 1 ? View.VISIBLE : View.GONE);
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_BATTERY) {
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
            ToastUtils.showToast(this, "Setup succeed!");
            int battery_v = result.data.get("battery_v").getAsInt();
            mBind.tvBatteryVoltage.setText(String.format("%dmV", battery_v));
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_SYSTEM_TIME
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_SET_TAG_ID
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_LED_REMINDER
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_BUZZER_REMINDER) {
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
            ToastUtils.showToast(this, "Setup succeed!");
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_ENCRYPTION_KEY_RESULT) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            String mac = result.data.get("mac").getAsString();
            if (!mBXPButtonInfo.mac.equalsIgnoreCase(mac)) return;
            dismissLoadingMessageDialog();
            int resultCode = result.data.get("result_code").getAsInt();
            if (resultCode != 0) {
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            ToastUtils.showToast(this, "Setup succeed!");
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_DISCONNECTED) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            ToastUtils.showToast(this, "Bluetooth disconnect");
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceModifyNameEvent(DeviceModifyNameEvent event) {
        // 修改了设备名称
        MokoDevice device = DBTools.getInstance(BXPButtonInfoActivity.this).selectDevice(mMokoDevice.mac);
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

    public void onDFU(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent intent = new Intent(this, BeaconDFUActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        intent.putExtra(AppConstants.EXTRA_KEY_MAC, mBXPButtonInfo.mac);
        startBeaconDFU.launch(intent);
    }

    private final ActivityResultLauncher<Intent> startBeaconDFU = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            ToastUtils.showToast(this, "Bluetooth disconnect");
            finish();
        }
    });

    public void onModifyTagId(View view) {
        if (isWindowLocked()) return;
        TagIdDialog dialog = new TagIdDialog();
        dialog.setOnTagIdClicked(tagId -> {
            if (isWindowLocked()) return;
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Setup failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            setBXPButtonTagId(tagId);
        });
        dialog.show(getSupportFragmentManager());

    }

    public void onReadBXPButtonBattery(View view) {
        if (isWindowLocked()) return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getBXPButtonBattery();
    }

    public void onDismissAlarmStatus(View view) {
        if (isWindowLocked()) return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        dismissAlarmStatus();
    }

    public void onLEDReminder(View view) {
        if (isWindowLocked()) return;
        LEDReminderDialog dialog = new LEDReminderDialog();
        dialog.setOnDialogClicked((color, interval, duration) -> {
            if (isWindowLocked()) return;
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Setup failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            setBXPButtonLEDReminder(color, interval, duration);
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onBuzzerReminder(View view) {
        if (isWindowLocked()) return;
        BuzzerReminderDialog dialog = new BuzzerReminderDialog();
        dialog.setOnDialogClicked((interval, duration) -> {
            if (isWindowLocked()) return;
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Setup failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            setBXPButtonBuzzerReminder(interval, duration);
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSyncTimeFromPhone(View view) {
        if (isWindowLocked())
            return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setSystemTime();
    }

    public void onUpdateEncryptionKey(View view) {
        if (isWindowLocked()) return;
        EncryptionKeyDialog dialog = new EncryptionKeyDialog();
        dialog.setOnDialogClicked(encryptionKey -> {
            if (isWindowLocked()) return;
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Setup failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            setBXPButtonEncryptionKey(encryptionKey);
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSOSTriggeredByButton(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, ButtonSOSActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        intent.putExtra(AppConstants.EXTRA_KEY_BXP_BUTTON_INFO, mBXPButtonInfo);
        startActivity(intent);
    }

    public void onSelfTestTriggeredByButton(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, ButtonSelfTestTriggeredActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        intent.putExtra(AppConstants.EXTRA_KEY_BXP_BUTTON_INFO, mBXPButtonInfo);
        startActivity(intent);
    }

    public void onDisconnect(View view) {
        if (isWindowLocked()) return;
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setMessage("Please confirm again whether to disconnect the gateway from BLE devices?");
        dialog.setOnAlertConfirmListener(() -> {
            if (!MQTTSupport.getInstance().isConnected()) {
                ToastUtils.showToast(this, R.string.network_error);
                return;
            }
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(BXPButtonInfoActivity.this, "Setup failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            disconnectDevice();
        });
        dialog.show(getSupportFragmentManager());
    }

    private void disconnectDevice() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_DISCONNECT;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setBXPButtonTagId(String tagId) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SET_TAG_ID;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("tag_id", tagId);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setBXPButtonLEDReminder(String color, int interval, int duration) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_LED_REMINDER;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("led_color", color);
        jsonObject.addProperty("flash_interval", interval);
        jsonObject.addProperty("flash_time", duration);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setBXPButtonBuzzerReminder(int interval, int duration) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_BUZZER_REMINDER;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("ring_interval", interval);
        jsonObject.addProperty("ring_time", duration);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setBXPButtonEncryptionKey(String key) {
        ArrayList<BatchDFUBeacon.BleDevice> mBeaconList = new ArrayList<>();
        BatchDFUBeacon.BleDevice bleDevice = new BatchDFUBeacon.BleDevice();
        bleDevice.mac = mBXPButtonInfo.mac;
        mBeaconList.add(bleDevice);
        int msgId = MQTTConstants.CONFIG_MSG_ID_BATCH_UPDATE_KEY;
        BatchUpdateKey batchDFUBeacon = new BatchUpdateKey();
        batchDFUBeacon.key = key;
        batchDFUBeacon.ble_dev = mBeaconList;
        JsonElement jsonElement = new Gson().toJsonTree(batchDFUBeacon);
        JsonObject jsonObject = (JsonObject) jsonElement;
        // property removal
        jsonObject.remove("property");
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

    private void getBXPButtonBattery() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_BATTERY;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void dismissAlarmStatus() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_DISMISS_ALARM;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setSystemTime() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SYSTEM_TIME;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("timestamp", Calendar.getInstance().getTimeInMillis());
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
        backToDetail();
    }

    public void onBack(View view) {
        if (isWindowLocked()) return;
        backToDetail();
    }

    private void backToDetail() {
        Intent intent = new Intent(this, DeviceDetailActivity.class);
        startActivity(intent);
    }
}
