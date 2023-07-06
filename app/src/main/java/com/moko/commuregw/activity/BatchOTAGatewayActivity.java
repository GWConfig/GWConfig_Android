package com.moko.commuregw.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
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
import com.moko.commuregw.databinding.ActivityBatchOtaGatewayBinding;
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

public class BatchOTAGatewayActivity extends BaseActivity<ActivityBatchOtaGatewayBinding> implements BaseQuickAdapter.OnItemChildClickListener {
    private final String FILTER_ASCII = "[ -~]*";

    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    private String mGatewayMac;
    private String mFirmwareFileUrl;
    private String mGatewayTopic;

    private Handler mHandler;
    private ArrayList<BatchGateway> mGatewayList;
    private BatchGatewayOTAAdapter mAdapter;

    private boolean mIsStart;
    private int mIndex = 0;

    @Override
    protected void onCreate() {
        InputFilter inputFilter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        mBind.etFirmwareFileUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256), inputFilter});
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mHandler = new Handler(Looper.getMainLooper());
        mGatewayList = new ArrayList<>();
        mAdapter = new BatchGatewayOTAAdapter();
        mAdapter.openLoadAnimation();
        mAdapter.replaceData(mGatewayList);
        mAdapter.setOnItemChildClickListener(this);
        mBind.rvBeaconList.setLayoutManager(new LinearLayoutManager(this));
        mBind.rvBeaconList.setAdapter(mAdapter);
    }

    @Override
    protected ActivityBatchOtaGatewayBinding getViewBinding() {
        return ActivityBatchOtaGatewayBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_OTA_RESULT) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mGatewayMac.equalsIgnoreCase(result.device_info.mac))
                return;
//            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int resultCode = result.data.get("result_code").getAsInt();
            if (resultCode == 1) {
                ToastUtils.showToast(this, R.string.update_success);
                mGatewayList.get(mIndex).status = 2;
            } else {
                ToastUtils.showToast(this, R.string.update_failed);
                mGatewayList.get(mIndex).status = 3;
            }
            mAdapter.replaceData(mGatewayList);
            mIndex++;
            if (mIndex < mGatewayList.size()) {
//                mHandler.postDelayed(() -> {
//                    mGatewayList.get(mIndex).status = 4;
//                    mAdapter.replaceData(mGatewayList);
//                    mIndex++;
//                    if (mIndex < mGatewayList.size()) {
//                        mGatewayMac = mGatewayList.get(mIndex).mac;
//                        if (mGatewayTopic.contains("/gateway/provision/#"))
//                            mAppTopic = mGatewayList.get(mIndex).topic;
//                        else
//                            mAppTopic = mGatewayTopic;
//                        setOTA();
//                    } else {
//                        mIsStart = false;
//                        ToastUtils.showToast(this, "Batch ota finish");
//                    }
//                }, 30 * 1000);
                mGatewayMac = mGatewayList.get(mIndex).mac;
                if (mGatewayTopic.contains("/gateway/provision/#"))
                    mAppTopic = mGatewayList.get(mIndex).topic;
                else
                    mAppTopic = mGatewayTopic;
                setOTA();
            } else {
                mIsStart = false;
                ToastUtils.showToast(this, "Batch ota finish");
            }
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_OTA) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mGatewayMac.equalsIgnoreCase(result.device_info.mac))
                return;
//            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
                mGatewayList.get(mIndex).status = 1;
                mAdapter.replaceData(mGatewayList);
            } else {
                ToastUtils.showToast(this, "Set up failed");
                mGatewayList.get(mIndex).status = 3;
                mAdapter.replaceData(mGatewayList);
                mIndex++;
                if (mIndex < mGatewayList.size()) {
//                    mHandler.postDelayed(() -> {
//                        mGatewayList.get(mIndex).status = 4;
//                        mAdapter.replaceData(mGatewayList);
//                        mIndex++;
//                        if (mIndex < mGatewayList.size()) {
//                            mGatewayMac = mGatewayList.get(mIndex).mac;
//                            if (mGatewayTopic.contains("/gateway/provision/#"))
//                                mAppTopic = mGatewayList.get(mIndex).topic;
//                            else
//                                mAppTopic = mGatewayTopic;
//                            setOTA();
//                        } else {
//                            mIsStart = false;
//                            ToastUtils.showToast(this, "Batch ota finish");
//                        }
//                    }, 30 * 1000);
                    mGatewayMac = mGatewayList.get(mIndex).mac;
                    if (mGatewayTopic.contains("/gateway/provision/#"))
                        mAppTopic = mGatewayList.get(mIndex).topic;
                    else
                        mAppTopic = mGatewayTopic;
                    setOTA();
                } else {
                    mIsStart = false;
                    ToastUtils.showToast(this, "Batch ota finish");
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTPublishFailureEvent(MQTTPublishFailureEvent event) {
        // 更新所有设备的网络状态
        final String topic = event.getTopic();
        final int msg_id = event.getMsgId();
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_OTA) {
            ToastUtils.showToast(this, "Set up failed");
            mGatewayList.get(mIndex).status = 3;
            mAdapter.replaceData(mGatewayList);
            mIndex++;
            if (mIndex < mGatewayList.size()) {
                mGatewayMac = mGatewayList.get(mIndex).mac;
                if (mGatewayTopic.contains("/gateway/provision/#"))
                    mAppTopic = mGatewayList.get(mIndex).topic;
                else
                    mAppTopic = mGatewayTopic;
                setOTA();
            } else {
                mIsStart = false;
                ToastUtils.showToast(this, "Batch ota finish");
            }
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return isFinish();
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
                                gateway.topic = String.format("/gateway/provision/%s", mac);
                                mGatewayList.add(gateway);
                            }
                            runOnUiThread(() -> {
                                mAdapter.replaceData(mGatewayList);
                                dismissLoadingProgressDialog();
                                ToastUtils.showToast(BatchOTAGatewayActivity.this, "Import success!");
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> {
                                dismissLoadingProgressDialog();
                                ToastUtils.showToast(BatchOTAGatewayActivity.this, "Import failed!");
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
                gateway.topic = String.format("/gateway/provision/%s", contents);
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
        mGatewayList.remove(position);
        mAdapter.replaceData(mGatewayList);
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        String firmwareFileUrlStr = mBind.etFirmwareFileUrl.getText().toString();
        String gatewayTopicStr = mBind.etGatewayTopic.getText().toString();
        if (TextUtils.isEmpty(firmwareFileUrlStr)) {
            ToastUtils.showToast(this, R.string.mqtt_verify_firmware_file_url);
            return;
        }
        if (TextUtils.isEmpty(gatewayTopicStr)) {
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
        mIsStart = true;
        mIndex = 0;
        mFirmwareFileUrl = firmwareFileUrlStr;
        mGatewayTopic = gatewayTopicStr;
        XLog.i("批量升级");
//        mHandler.postDelayed(() -> {
//            mGatewayList.get(mIndex).status = 4;
//            mAdapter.replaceData(mGatewayList);
//            mIndex++;
//            if (mIndex < mGatewayList.size()) {
//                mGatewayMac = mGatewayList.get(mIndex).mac;
//                if (mGatewayTopic.contains("/gateway/provision/#"))
//                    mAppTopic = mGatewayList.get(mIndex).topic;
//                else
//                    mAppTopic = mGatewayTopic;
//                setOTA();
//            } else {
//                mIsStart = false;
//                ToastUtils.showToast(this, "Batch ota finish");
//            }
//        }, 30 * 1000);
//        showLoadingProgressDialog();
        mGatewayMac = mGatewayList.get(mIndex).mac;
        if (mGatewayTopic.contains("/gateway/provision/#"))
            mAppTopic = mGatewayList.get(mIndex).topic;
        else
            mAppTopic = mGatewayTopic;
        setOTA();
    }

    private void setOTA() {
        mHandler.postDelayed(() -> {
            mGatewayList.get(mIndex).status = 4;
            mAdapter.replaceData(mGatewayList);
            mIndex++;
            if (mIndex < mGatewayList.size()) {
                mGatewayMac = mGatewayList.get(mIndex).mac;
                if (mGatewayTopic.contains("/gateway/provision/#"))
                    mAppTopic = mGatewayList.get(mIndex).topic;
                else
                    mAppTopic = mGatewayTopic;
                setOTA();
            } else {
                mIsStart = false;
                ToastUtils.showToast(this, "Batch ota finish");
            }
        }, 30 * 1000);
        int msgId = MQTTConstants.CONFIG_MSG_ID_OTA;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("firmware_url", mFirmwareFileUrl);
        String message = assembleWriteCommonData(msgId, mGatewayMac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
