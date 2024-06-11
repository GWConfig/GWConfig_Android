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
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.adapter.AddConfiguredGatewayAdapter;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityAddConfiguredGatewayBinding;
import com.moko.commuregw.db.DBTools;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.FileUtils;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.BatchGateway;
import com.moko.support.commuregw.entity.ConfiguredGateway;
import com.moko.support.commuregw.entity.MsgNotify;
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
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.recyclerview.widget.LinearLayoutManager;

public class AddConfiguredGatewayActivity extends BaseActivity<ActivityAddConfiguredGatewayBinding> {

    public static String TAG = AddConfiguredGatewayActivity.class.getSimpleName();

    private final String FILTER_ASCII = "[ -~]*";

    private MQTTConfig appMqttConfig;
    //    private String mAppTopic;
    private String mGatewayMac;
    private String mGatewayPublicTopic;
    private String mGatewaySubscribeTopic;

    private Handler mHandler;
    private ArrayList<ConfiguredGateway> mGatewayList;
    private AddConfiguredGatewayAdapter mAdapter;

    // 开始升级标志位
    private boolean mIsStart;
    // 升级序号
    private int mIndex = 0;

    @Override
    protected void onCreate() {
//        InputFilter inputFilter = (source, start, end, dest, dstart, dend) -> {
//            if (!(source + "").matches(FILTER_ASCII)) {
//                return "";
//            }
//
//            return null;
//        };
//        mBind.etGatewayPublishTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), inputFilter});
//        mBind.etGatewaySubscribeTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), inputFilter});
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        String deviceSubscribeTopic = SPUtiles.getStringValue(this, AppConstants.SP_KEY_ADD_DEVICE_SUBSCRIBE, "/provision/gateway/cmds");
        String devicePublishTopic = SPUtiles.getStringValue(this, AppConstants.SP_KEY_ADD_DEVICE_PUBLISH, "/provision/gateway/data");
        mBind.etGatewayPublishTopic.setText(devicePublishTopic);
        mBind.etGatewaySubscribeTopic.setText(deviceSubscribeTopic);
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mHandler = new Handler(Looper.getMainLooper());
        mGatewayList = new ArrayList<>();
        mAdapter = new AddConfiguredGatewayAdapter();
        mAdapter.openLoadAnimation();
        mAdapter.replaceData(mGatewayList);
        mBind.rvGatewayList.setLayoutManager(new LinearLayoutManager(this));
        mBind.rvGatewayList.setAdapter(mAdapter);
    }

    @Override
    protected ActivityAddConfiguredGatewayBinding getViewBinding() {
        return ActivityAddConfiguredGatewayBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.READ_MSG_ID_DEVICE_INFO) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mGatewayMac.equalsIgnoreCase(result.device_info.mac))
                return;
//            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mGatewayList.get(mIndex).status = 1;
            MokoDevice mokoDevice = DBTools.getInstance(AddConfiguredGatewayActivity.this).selectDeviceByMac(mGatewayMac);
            if (mokoDevice == null) {
                mokoDevice = new MokoDevice();
                mokoDevice.name = String.format("MK110-%s", mGatewayMac.substring(mGatewayMac.length() - 4)).toUpperCase(Locale.ROOT);
                mokoDevice.mac = mGatewayMac;
                mokoDevice.mqttInfo = "";
                mokoDevice.topicSubscribe = mGatewaySubscribeTopic;
                mokoDevice.topicPublish = mGatewayPublicTopic;
                mokoDevice.deviceType = 1;
                DBTools.getInstance(AddConfiguredGatewayActivity.this).insertDevice(mokoDevice);
            } else {
                mokoDevice.name = String.format("MK110-%s", mGatewayMac.substring(mGatewayMac.length() - 4)).toUpperCase(Locale.ROOT);
                mokoDevice.topicSubscribe = mGatewaySubscribeTopic;
                mokoDevice.topicPublish = mGatewayPublicTopic;
                mokoDevice.deviceType = 1;
                DBTools.getInstance(AddConfiguredGatewayActivity.this).updateDevice(mokoDevice);
            }
            // 添加成功
            mAdapter.replaceData(mGatewayList);
            mIndex++;
            if (mIndex < mGatewayList.size()) {
                mHandler.postDelayed(() -> {
                    mGatewayList.get(mIndex).status = 4;
                    mAdapter.replaceData(mGatewayList);
                    mIndex++;
                    if (mIndex < mGatewayList.size()) {
                        mGatewayMac = mGatewayList.get(mIndex).mac;
                        getDeviceInfo();
                    } else {
                        mIsStart = false;
                        ToastUtils.showToast(this, "Add finish");
                        mBind.tvDone.setVisibility(View.VISIBLE);
                    }
                }, 30 * 1000);
                mGatewayMac = mGatewayList.get(mIndex).mac;
                getDeviceInfo();
            } else {
                mIsStart = false;
                ToastUtils.showToast(this, "Add finish");
                mBind.tvDone.setVisibility(View.VISIBLE);
            }
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
            ToastUtils.showToast(this, "Please do not leave this page until the add is complete!");
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
        mGatewayPublicTopic = mBind.etGatewayPublishTopic.getText().toString();
        mGatewaySubscribeTopic = mBind.etGatewaySubscribeTopic.getText().toString();
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
        mGatewayPublicTopic = mBind.etGatewayPublishTopic.getText().toString();
        mGatewaySubscribeTopic = mBind.etGatewaySubscribeTopic.getText().toString();
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
                            // 只取前50条数据
                            for (int i = 1; i < Math.min(rows, 51); i++) {
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
                                ConfiguredGateway gateway = new ConfiguredGateway();
                                gateway.mac = mac;
                                mGatewayList.add(gateway);
                            }
                            runOnUiThread(() -> {
                                mBind.tvStart.setVisibility(View.VISIBLE);
                                mBind.tvDone.setVisibility(View.GONE);
                                mAdapter.replaceData(mGatewayList);
                                dismissLoadingProgressDialog();
                                ToastUtils.showToast(AddConfiguredGatewayActivity.this, "Import success!");
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> {
                                dismissLoadingProgressDialog();
                                ToastUtils.showToast(AddConfiguredGatewayActivity.this, "Import failed!");
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
                for (ConfiguredGateway gateway : mGatewayList) {
                    if (contents.equalsIgnoreCase(gateway.mac)) {
                        ToastUtils.showToast(this, R.string.mac_repeat);
                        return;
                    }
                }
                if (mGatewayList.size() >= 50) {
                    ToastUtils.showToast(this, R.string.size_error_50);
                    return;
                }
                ConfiguredGateway gateway = new ConfiguredGateway();
                gateway.mac = contents;
                mGatewayList.add(gateway);
                mAdapter.replaceData(mGatewayList);
                mBind.tvStart.setVisibility(View.VISIBLE);
                mBind.tvDone.setVisibility(View.GONE);
            }
        }
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (mIsStart) return;
        mGatewaySubscribeTopic = mBind.etGatewaySubscribeTopic.getText().toString();
        mGatewayPublicTopic = mBind.etGatewayPublishTopic.getText().toString();
        if (TextUtils.isEmpty(mGatewayPublicTopic) || TextUtils.isEmpty(mGatewaySubscribeTopic)) {
            ToastUtils.showToast(this, R.string.mqtt_verify_gateway_topic);
            return;
        }
        SPUtiles.setStringValue(this, AppConstants.SP_KEY_ADD_DEVICE_SUBSCRIBE, mGatewaySubscribeTopic);
        SPUtiles.setStringValue(this, AppConstants.SP_KEY_ADD_DEVICE_PUBLISH, mGatewayPublicTopic);
        ToastUtils.showToast(this, R.string.save_success);
    }

    public void onStart(View view) {
        if (isWindowLocked()) return;
        mBind.tvStart.setVisibility(View.GONE);
        Iterator<ConfiguredGateway> gatewayIterator = mGatewayList.iterator();
        while (gatewayIterator.hasNext()) {
            ConfiguredGateway gateway = gatewayIterator.next();
            if (gateway.status == 1)
                gatewayIterator.remove();
        }
        mAdapter.replaceData(mGatewayList);
        mIndex = 0;
        XLog.i("批量添加");
        mGatewayMac = mGatewayList.get(mIndex).mac;
        try {
            MQTTSupport.getInstance().subscribe(mGatewayPublicTopic, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
        mIsStart = true;
        getDeviceInfo();
    }

    private void getDeviceInfo() {
        mHandler.postDelayed(() -> {
            mGatewayList.get(mIndex).status = 2;
            mAdapter.replaceData(mGatewayList);
            mIndex++;
            if (mIndex < mGatewayList.size()) {
                mGatewayMac = mGatewayList.get(mIndex).mac;
                getDeviceInfo();
            } else {
                mIsStart = false;
                ToastUtils.showToast(this, "Add finish");
                mBind.tvDone.setVisibility(View.VISIBLE);
            }
        }, 30 * 1000);
        int msgId = MQTTConstants.READ_MSG_ID_DEVICE_INFO;
        String message = assembleReadCommon(msgId, mGatewayMac);
        try {
            MQTTSupport.getInstance().publish(mGatewaySubscribeTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onDone(View view) {
        if (isWindowLocked()) return;
        // 跳转首页，刷新数据
        Intent intent = new Intent(AddConfiguredGatewayActivity.this, MainActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_FROM_ACTIVITY, TAG);
        startActivity(intent);
    }
}
