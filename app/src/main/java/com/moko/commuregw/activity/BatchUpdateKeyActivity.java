package com.moko.commuregw.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
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
import com.moko.commuregw.adapter.BatchBeaconAdapter;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityBatchUpdateKeyBinding;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.FileUtils;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.BatchUpdateKey;
import com.moko.support.commuregw.entity.BleTag;
import com.moko.support.commuregw.entity.MsgConfigResult;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.event.DeviceOnlineEvent;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;

public class BatchUpdateKeyActivity extends BaseActivity<ActivityBatchUpdateKeyBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    private Handler mHandler;
    private ArrayList<BleTag> mBeaconList;
    private BatchBeaconAdapter mAdapter;

    private boolean mIsStart;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        mBeaconList = new ArrayList<>();
        mAdapter = new BatchBeaconAdapter();
        mAdapter.openLoadAnimation();
        mAdapter.replaceData(mBeaconList);
        mBind.rvBeaconList.setLayoutManager(new LinearLayoutManager(this));
        mBind.rvBeaconList.setAdapter(mAdapter);
    }

    @Override
    protected ActivityBatchUpdateKeyBinding getViewBinding() {
        return ActivityBatchUpdateKeyBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_BATCH_UPDATE_KEY) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "setup succeed");
            } else {
                mIsStart = false;
                ToastUtils.showToast(this, "setup failed");
                mIsStart = false;
                for (int i = 0, size = mBeaconList.size(); i < size; i++) {
                    BleTag bleDevice = mBeaconList.get(i);
                    bleDevice.status = 3;
                }
                mAdapter.replaceData(mBeaconList);
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_ENCRYPTION_KEY_RESULT) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            String mac = result.data.get("mac").getAsString();
            int result_code = result.data.get("result_code").getAsInt();
            for (int i = 0, size = mBeaconList.size(); i < size; i++) {
                BleTag bleDevice = mBeaconList.get(i);
                if (mac.equalsIgnoreCase(bleDevice.mac)) {
                    bleDevice.status = result_code + 2;
                    mAdapter.replaceData(mBeaconList);
                    break;
                }
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_ENCRYPTION_KEY_RESULT_FINALE) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            mIsStart = false;
            dismissLoadingMessageDialog();
            JsonArray array = result.data.get("fail_dev").getAsJsonArray();
            if (!array.isEmpty()) {
                for (int i = 0, size = mBeaconList.size(); i < size; i++) {
                    BleTag bleDevice = mBeaconList.get(i);
                    if (bleDevice.status != 2) {
                        bleDevice.status = 3;
                    }
                }
                mAdapter.replaceData(mBeaconList);
                ToastUtils.showToast(this, "Beacon DFU failed!");
                return;
            }
            ToastUtils.showToast(this, "Beacon DFU successfully!");
            setResult(RESULT_OK);
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!isFinish())
                return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isFinish() {
        if (mIsStart) {
            ToastUtils.showToast(this, " Please do not leave this page until the batch upgrade is complete!");
            return false;
        }
        return true;
    }

    public void onBack(View view) {
        if (isFinish()) {
            finish();
        }
    }

    public void onSelectBeaconList(View view) {
        if (isWindowLocked()) return;
        if (mIsStart) return;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");//设置类型，我这里是任意类型，任意后缀的可以这样写。
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "select file first!"), AppConstants.REQUEST_CODE_OPEN_BEACON_LIST);
        } catch (ActivityNotFoundException ex) {
            ToastUtils.showToast(this, "install file manager app");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppConstants.REQUEST_CODE_OPEN_BEACON_LIST) {
            if (resultCode == RESULT_OK) {
                //得到uri，后面就是将uri转化成file的过程。
                Uri uri = data.getData();
                String paramFilePath = FileUtils.getPath(this, uri);
                if (TextUtils.isEmpty(paramFilePath)) {
                    return;
                }
                if (!paramFilePath.endsWith(".xlsx")) {
                    ToastUtils.showToast(this, "Please select the correct file!");
                    return;
                }
                final File paramFile = new File(paramFilePath);
                if (paramFile.exists()) {
                    mBeaconList.clear();
                    mBind.tvBeaconList.setText(paramFilePath);
                    showLoadingProgressDialog();
                    new Thread(() -> {
                        try {
                            Workbook workbook = WorkbookFactory.create(paramFile);
                            Sheet sheet = workbook.getSheetAt(0);
                            int rows = sheet.getPhysicalNumberOfRows();
                            int columns = sheet.getRow(0).getPhysicalNumberOfCells();
                            if (columns != 2)
                                throw new Exception();
                            for (int i = 1; i < rows; i++) {
                                Row row = sheet.getRow(i);
                                Cell cellMac = row.getCell(0);
                                Cell cellPassword = row.getCell(1);
                                XLog.i("------Row:" + i + "------");
                                String mac;
                                if (cellMac.getCellType() != Cell.CELL_TYPE_STRING) {
                                    cellMac.setCellType(Cell.CELL_TYPE_STRING);
                                }
                                mac = cellMac.getStringCellValue();
                                String password;
                                if (cellPassword.getCellType() != Cell.CELL_TYPE_STRING) {
                                    cellPassword.setCellType(Cell.CELL_TYPE_STRING);
                                }
                                password = cellPassword.getStringCellValue();
                                if (TextUtils.isEmpty(mac))
                                    break;
                                BleTag bleDevice = new BleTag();
                                bleDevice.mac = mac;
                                bleDevice.passwd = password;
                                mBeaconList.add(bleDevice);
                            }
                            runOnUiThread(() -> {
                                mAdapter.replaceData(mBeaconList);
                                dismissLoadingProgressDialog();
                                ToastUtils.showToast(BatchUpdateKeyActivity.this, "Import success!");
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> {
                                dismissLoadingProgressDialog();
                                ToastUtils.showToast(BatchUpdateKeyActivity.this, "Import failed!");
                            });
                        }
                    }).start();
                } else {
                    ToastUtils.showToast(this, "File is not exists!");
                }
            }
        }
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        String encryptionKeyStr = mBind.etEncryptionKey.getText().toString();
        if (TextUtils.isEmpty(encryptionKeyStr)) {
            ToastUtils.showToast(this, R.string.encryption_key_error);
            return;
        }
        if (mIsStart) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (mBeaconList.isEmpty()) {
            ToastUtils.showToast(this, R.string.cannot_be_empty);
            return;
        }
        for (BleTag device : mBeaconList) {
            if (device.mac.length() != 12) {
                ToastUtils.showToast(this, R.string.beacon_list_mac_error);
                return;
            }
        }
        mIsStart = true;
        XLog.i("批量升级");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "setup failed");
            mIsStart = false;
            for (int i = 0, size = mBeaconList.size(); i < size; i++) {
                BleTag bleDevice = mBeaconList.get(i);
                bleDevice.status = 3;
            }
            mAdapter.replaceData(mBeaconList);
        }, 1000 * 1000);
        showLoadingProgressDialog();
        setBatchUpdateKey();
    }

    private void setBatchUpdateKey() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BATCH_UPDATE_KEY;
        BatchUpdateKey batchDFUBeacon = new BatchUpdateKey();
        batchDFUBeacon.key = mBind.etEncryptionKey.getText().toString();
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
