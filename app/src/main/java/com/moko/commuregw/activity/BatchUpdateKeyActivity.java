package com.moko.commuregw.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
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
import com.moko.support.commuregw.entity.BatchDFUBeacon;
import com.moko.support.commuregw.entity.BatchUpdateKey;
import com.moko.support.commuregw.entity.MsgConfigResult;
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
    private ArrayList<BatchDFUBeacon.BleDevice> mBeaconList;
    private BatchBeaconAdapter mAdapter;

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
                ToastUtils.showToast(this, "setup failed");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    public void onBack(View view) {
        finish();
    }

    public void onSelectBeaconList(View view) {
        if (isWindowLocked()) return;
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
                                String mac = cellMac.getStringCellValue();
                                String password = cellPassword.getStringCellValue();
                                if (TextUtils.isEmpty(cellMac.getStringCellValue()))
                                    break;
                                BatchDFUBeacon.BleDevice bleDevice = new BatchDFUBeacon.BleDevice();
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
                            ToastUtils.showToast(BatchUpdateKeyActivity.this, "Import failed!");
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
        for (BatchDFUBeacon.BleDevice device : mBeaconList) {
            if (device.mac.length() != 12) {
                ToastUtils.showToast(this, R.string.beacon_list_mac_error);
                return;
            }
        }
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        XLog.i("批量升级");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "setup failed");
        }, 50 * 1000);
        showLoadingProgressDialog();
        setBatchUpdateKey();
    }

    private void setBatchUpdateKey() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BATCH_UPDATE_KEY;
        BatchUpdateKey batchDFUBeacon = new BatchUpdateKey();
        batchDFUBeacon.key = mBind.etEncryptionKey.getText().toString();
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
}
