package com.moko.commuregw.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.adapter.BatchGatewayOTAAdapter;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityBatchModifyGatewayBinding;
import com.moko.commuregw.entity.GatewayConfig;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.utils.FileUtils;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.BatchGateway;
import com.moko.support.commuregw.entity.MsgConfigResult;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;
import com.moko.support.commuregw.event.MQTTPublishFailureEvent;

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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.recyclerview.widget.LinearLayoutManager;

public class BatchConfigGatewayActivity extends BaseActivity<ActivityBatchModifyGatewayBinding> implements BaseQuickAdapter.OnItemChildClickListener {
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    private String mGatewayMac;
    private Handler mHandler;
    private ArrayList<BatchGateway> mGatewayList;
    private BatchGatewayOTAAdapter mAdapter;
    private ArrayList<Integer> mRetryList;

    // 开始配置标志位
    private boolean mIsStart;
    // 配置序号
    private int mIndex = 0;
    // 是否有重试网关
    private boolean mIsRetry;
    // 重试配置序号
    private int mRetryIndex = 0;

    private GatewayConfig mGatewayConfig;

    @Override
    protected void onCreate() {
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mGatewayConfig = getIntent().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
        mHandler = new Handler(Looper.getMainLooper());
        mGatewayList = new ArrayList<>();
        mRetryList = new ArrayList<>();
        mAdapter = new BatchGatewayOTAAdapter(this);
        mAdapter.openLoadAnimation();
        mAdapter.replaceData(mGatewayList);
        mAdapter.setOnItemChildClickListener(this);
        mBind.rvGatewayList.setLayoutManager(new LinearLayoutManager(this));
        mBind.rvGatewayList.setAdapter(mAdapter);
        mBind.etGatewayPublishTopic.setText(mGatewayConfig.topicPublish);
        mBind.etGatewaySubscribeTopic.setText(mGatewayConfig.topicSubscribe);
    }

    @Override
    protected ActivityBatchModifyGatewayBinding getViewBinding() {
        return ActivityBatchModifyGatewayBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_WIFI_SETTINGS) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mGatewayMac.equalsIgnoreCase(result.device_info.mac))
                return;
            if (result.result_code == 0) {
                mGatewayList.get(mIndex).status = 1;
                mAdapter.replaceData(mGatewayList);
                if (mGatewayConfig.eapType != 0)
                    modifyWIFICertFile();
                else
                    modifyMQTTSettings();
            } else {
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Set up failed");
                mGatewayList.get(mIndex).status = 3;
                refreshListAndNext();
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_WIFI_CERT_RESULT) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mGatewayMac.equalsIgnoreCase(result.device_info.mac))
                return;
            int resultCode = result.data.get("result_code").getAsInt();
            if (resultCode == 1) {
                modifyMQTTSettings();
            } else {
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, R.string.update_failed);
                mGatewayList.get(mIndex).status = 3;
                refreshListAndNext();
            }
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_MQTT_SETTINGS) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mGatewayMac.equalsIgnoreCase(result.device_info.mac))
                return;
            if (result.result_code == 0) {
                if (mGatewayConfig.sslEnable != 0)
                    modifyMQTTCertFile();
                else
                    modifyNetworkSettings();
            } else {
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Set up failed");
                mGatewayList.get(mIndex).status = 3;
                refreshListAndNext();
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_MQTT_CERT_RESULT) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mGatewayMac.equalsIgnoreCase(result.device_info.mac))
                return;
            int resultCode = result.data.get("result_code").getAsInt();
            if (resultCode == 1) {
                modifyNetworkSettings();
            } else {
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, R.string.update_failed);
                mGatewayList.get(mIndex).status = 3;
                refreshListAndNext();
            }
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_NETWORK_SETTINGS) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mGatewayMac.equalsIgnoreCase(result.device_info.mac))
                return;
            if (result.result_code == 0) {
                rebootDevice();
            } else {
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Set up failed");
                mGatewayList.get(mIndex).status = 3;
                refreshListAndNext();
            }
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_REBOOT) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mGatewayMac.equalsIgnoreCase(result.device_info.mac))
                return;
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
                mGatewayList.get(mIndex).status = 2;
            } else {
                ToastUtils.showToast(this, "Set up failed");
                mGatewayList.get(mIndex).status = 3;
            }
            refreshListAndNext();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishFailureEvent(MQTTPublishFailureEvent event) {
        // 更新所有设备的网络状态
        final String topic = event.getTopic();
        final int msg_id = event.getMsgId();
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_WIFI_SETTINGS
                || msg_id == MQTTConstants.CONFIG_MSG_ID_WIFI_CERT_FILE
                || msg_id == MQTTConstants.CONFIG_MSG_ID_MQTT_SETTINGS
                || msg_id == MQTTConstants.CONFIG_MSG_ID_MQTT_CERT_FILE
                || msg_id == MQTTConstants.CONFIG_MSG_ID_NETWORK_SETTINGS
                || msg_id == MQTTConstants.CONFIG_MSG_ID_REBOOT) {
            ToastUtils.showToast(this, "Set up failed");
            mGatewayList.get(mIndex).status = 3;
            refreshListAndNext();
        }
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

    public void onSelectGatewayList(View view) {
        if (isWindowLocked()) return;
        if (mIsStart) return;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");//设置类型，我这里是任意类型，任意后缀的可以这样写。
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "select file first!"), AppConstants.REQUEST_CODE_OPEN_GATEWAY_LIST);
        } catch (ActivityNotFoundException ex) {
            ToastUtils.showToast(this, "install file manager app");
        }
    }

    public void onScanGatewayQrcode(View view) {
        if (isWindowLocked()) return;
        if (mIsStart) return;
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setOrientationLocked(false);
        integrator.setCaptureActivity(ScanActivity.class);
        integrator.setRequestCode(AppConstants.REQUEST_CODE_SCAN_GATEWAY_MAC);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppConstants.REQUEST_CODE_OPEN_GATEWAY_LIST) {
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
                    mGatewayList.clear();
                    mBind.tvGatewayList.setText(paramFilePath);
                    showLoadingProgressDialog();
                    new Thread(() -> {
                        try {
                            Workbook workbook = WorkbookFactory.create(paramFile);
                            Sheet sheet = workbook.getSheetAt(0);
                            int rows = sheet.getPhysicalNumberOfRows();
                            int columns = sheet.getRow(0).getPhysicalNumberOfCells();
                            if (columns != 1)
                                throw new Exception();
                            // 只取前20条数据
                            for (int i = 1; i < Math.min(rows, 21); i++) {
                                Row row = sheet.getRow(i);
                                Cell cellMac = row.getCell(0);
                                XLog.i("------Row:" + i + "------");
                                String mac;
                                if (cellMac.getCellType() != Cell.CELL_TYPE_STRING) {
                                    cellMac.setCellType(Cell.CELL_TYPE_STRING);
                                }
                                mac = cellMac.getStringCellValue().toLowerCase(Locale.ROOT);
                                if (TextUtils.isEmpty(mac))
                                    continue;
                                // 判断扫描内容是否符合MAC
                                Pattern r = Pattern.compile(AppConstants.PATTERN_MAC_CODE);
                                Matcher m = r.matcher(mac);
                                if (!m.matches()) {
                                    XLog.e(String.format("error mac:%s", mac));
                                    throw new Exception();
                                }
                                BatchGateway gateway = new BatchGateway();
                                gateway.mac = mac;
                                mGatewayList.add(gateway);
                            }
                            runOnUiThread(() -> {
                                mAdapter.replaceData(mGatewayList);
                                dismissLoadingProgressDialog();
                                ToastUtils.showToast(BatchConfigGatewayActivity.this, "Import success!");
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> {
                                dismissLoadingProgressDialog();
                                ToastUtils.showToast(BatchConfigGatewayActivity.this, "Import failed!");
                            });
                        }
                    }).start();
                } else {
                    ToastUtils.showToast(this, "File is not exists!");
                }
            }
        }
        if (requestCode == AppConstants.REQUEST_CODE_SCAN_GATEWAY_MAC) {
            IntentResult result = IntentIntegrator.parseActivityResult(resultCode, data);
            if (result.getContents() != null) {
//                ToastUtils.showToast(this, R.string.scan_failed);
                final String contents = result.getContents().toLowerCase(Locale.ROOT);
                // 判断扫描内容是否符合MAC
                Pattern r = Pattern.compile(AppConstants.PATTERN_MAC_CODE);
                Matcher m = r.matcher(contents);
                if (!m.matches()) {
                    ToastUtils.showToast(this, R.string.mac_error);
                    return;
                }
                if (mGatewayList.size() >= 20) {
                    ToastUtils.showToast(this, R.string.size_error);
                    return;
                }
                BatchGateway gateway = new BatchGateway();
                gateway.mac = contents;
                mGatewayList.add(gateway);
                mAdapter.replaceData(mGatewayList);
            }
        }
    }


    @Override
    public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
        BatchGateway info = (BatchGateway) adapter.getItem(position);
        if (info == null) return;
        if (mIsStart) return;
        if (view.getId() == R.id.iv_del) {
            mGatewayList.remove(position);
            mAdapter.replaceData(mGatewayList);
        }
        if (view.getId() == R.id.tv_retry) {
            mIsRetry = true;
            mGatewayList.get(position).status = 0;
            mAdapter.replaceData(mGatewayList);
        }
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        String gatewaySubscribeTopicStr = mBind.etGatewaySubscribeTopic.getText().toString();
        String gatewayPublishTopicStr = mBind.etGatewayPublishTopic.getText().toString();
        if (TextUtils.isEmpty(gatewayPublishTopicStr) || TextUtils.isEmpty(gatewaySubscribeTopicStr)) {
            ToastUtils.showToast(this, R.string.mqtt_verify_gateway_topic);
            return;
        }
        if (mIsStart) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (mGatewayList.isEmpty()) {
            ToastUtils.showToast(this, R.string.cannot_be_empty);
            return;
        }
        if (mIsRetry) {
            for (int i = 0, size = mGatewayList.size(); i < size; i++) {
                BatchGateway gateway = mGatewayList.get(i);
                if (gateway.status == 0)
                    mRetryList.add(i);
            }
        }
        mRetryIndex = 0;
        mIsStart = true;
        if (mRetryList.isEmpty())
            mIndex = 0;
        else
            mIndex = mRetryList.get(mRetryIndex);
        mAppTopic = gatewaySubscribeTopicStr;
        XLog.i("批量配置");
        mGatewayMac = mGatewayList.get(mIndex).mac;
        try {
            MQTTSupport.getInstance().subscribe(gatewayPublishTopicStr, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
        ToastUtils.showToast(this, "Start batch modify");
        mHandler.postDelayed(() -> {
            modifyWIFISettings();
        }, 3000);
    }

    private void modifyWIFISettings() {
        mHandler.postDelayed(() -> {
            mGatewayList.get(mIndex).status = 4;
            refreshListAndNext();
        }, 60 * 1000);
        int msgId = MQTTConstants.CONFIG_MSG_ID_WIFI_SETTINGS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("security_type", mGatewayConfig.security);
        jsonObject.addProperty("ssid", mGatewayConfig.wifiSSID);
        jsonObject.addProperty("passwd", mGatewayConfig.security == 0 ? mGatewayConfig.wifiPassword : "");
        jsonObject.addProperty("eap_type", mGatewayConfig.eapType);
        jsonObject.addProperty("eap_id", mGatewayConfig.eapType == 2 ? mGatewayConfig.domainId : "");
        jsonObject.addProperty("eap_username", mGatewayConfig.security != 0 ? mGatewayConfig.eapUserName : "");
        jsonObject.addProperty("eap_passwd", mGatewayConfig.security != 0 ? mGatewayConfig.eapPassword : "");
        jsonObject.addProperty("eap_verify_server", mGatewayConfig.verifyServer);
        String message = assembleWriteCommonData(msgId, mGatewayMac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void modifyWIFICertFile() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_WIFI_CERT_FILE;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("ca_url", mGatewayConfig.wifiCaPath);
        jsonObject.addProperty("client_cert_url", mGatewayConfig.wifiCertPath);
        jsonObject.addProperty("client_key_url", mGatewayConfig.wifiKeyPath);
        String message = assembleWriteCommonData(msgId, mGatewayMac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void modifyMQTTSettings() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_MQTT_SETTINGS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("security_type", mGatewayConfig.sslEnable);
        jsonObject.addProperty("host", mGatewayConfig.host);
        jsonObject.addProperty("port", Integer.parseInt(mGatewayConfig.port));
        jsonObject.addProperty("client_id", mGatewayConfig.clientId);
        jsonObject.addProperty("username", mGatewayConfig.username);
        jsonObject.addProperty("passwd", mGatewayConfig.password);
        jsonObject.addProperty("sub_topic", mGatewayConfig.topicSubscribe);
        jsonObject.addProperty("pub_topic", mGatewayConfig.topicPublish);
        jsonObject.addProperty("qos", mGatewayConfig.qos);
        jsonObject.addProperty("clean_session", mGatewayConfig.cleanSession ? 1 : 0);
        jsonObject.addProperty("keepalive", mGatewayConfig.keepAlive);
        String message = assembleWriteCommonData(msgId, mGatewayMac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void modifyMQTTCertFile() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_MQTT_CERT_FILE;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("ca_url", mGatewayConfig.caPath);
        jsonObject.addProperty("client_cert_url", mGatewayConfig.clientCertPath);
        jsonObject.addProperty("client_key_url", mGatewayConfig.clientKeyPath);
        String message = assembleWriteCommonData(msgId, mGatewayMac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void modifyNetworkSettings() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_NETWORK_SETTINGS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("dhcp_en", mGatewayConfig.dhcp);
        jsonObject.addProperty("ip", mGatewayConfig.ip);
        jsonObject.addProperty("netmask", mGatewayConfig.mask);
        jsonObject.addProperty("gw", mGatewayConfig.gateway);
        jsonObject.addProperty("dns", mGatewayConfig.dns);
        String message = assembleWriteCommonData(msgId, mGatewayMac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void rebootDevice() {
        XLog.i("重启设备");
        int msgId = MQTTConstants.CONFIG_MSG_ID_REBOOT;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("reset", 0);
        String message = assembleWriteCommonData(msgId, mGatewayMac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    private void refreshListAndNext() {
        mAdapter.replaceData(mGatewayList);
        if (mRetryList.isEmpty()) {
            mIndex++;
        } else {
            mRetryIndex++;
            if (mRetryIndex < mRetryList.size()) {
                mIndex = mRetryList.get(mRetryIndex);
            } else {
                mIsStart = false;
                ToastUtils.showToast(this, "Batch modify finish");
                mRetryList.clear();
                return;
            }
        }
        if (mIndex < mGatewayList.size()) {
            mGatewayMac = mGatewayList.get(mIndex).mac;
            modifyWIFISettings();
        } else {
            mIsStart = false;
            ToastUtils.showToast(this, "Batch modify finish");
            mRetryList.clear();
        }
    }
}
