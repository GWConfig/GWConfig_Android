package com.moko.commuregw.activity;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.adapter.LogListAdapter;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityButtonLogBinding;
import com.moko.commuregw.dialog.AlertMessageDialog;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.commuregw.utils.Utils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.BXPButtonInfo;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.event.DeviceOnlineEvent;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import androidx.recyclerview.widget.LinearLayoutManager;

public class ButtonLogActivity extends BaseActivity<ActivityButtonLogBinding> {

    private static final String LOG_FILE = "ButtonLog.txt";

    private static String PATH_LOGCAT;

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    private BXPButtonInfo mBXPButtonInfo;
    public Handler mHandler;


    private StringBuilder mLogString;
    private ArrayList<String> mLogList;
    private LogListAdapter mAdapter;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        mBXPButtonInfo = (BXPButtonInfo) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_BXP_BUTTON_INFO);

        PATH_LOGCAT = MainActivity.PATH_LOGCAT + File.separator + LOG_FILE;

        mAdapter = new LogListAdapter();
        mLogList = new ArrayList<>();
        mLogString = new StringBuilder();

        mAdapter.replaceData(mLogList);
        mBind.rvAccData.setLayoutManager(new LinearLayoutManager(this));
        mBind.rvAccData.setAdapter(mAdapter);

        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        setOpenLog();
    }

    @Override
    protected ActivityButtonLogBinding getViewBinding() {
        return ActivityButtonLogBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_BUTTON_LOG) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            String mac = result.data.get("mac").getAsString();
            if (!mBXPButtonInfo.mac.equalsIgnoreCase(mac)) return;
            Drawable top = getResources().getDrawable(R.drawable.ic_download_checked);
            mBind.tvExport.setCompoundDrawablesWithIntrinsicBounds(null, top, null, null);
            int seq = result.data.get("seq").getAsInt();
            long timestamp = result.data.get("timestamp").getAsLong();
            int keyStatus = result.data.get("key_status").getAsInt();

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timestamp);
            TimeZone timeZone = TimeZone.getTimeZone("UTC");
            SimpleDateFormat sdf = new SimpleDateFormat(AppConstants.PATTERN_YYYY_MM_DD_HH_MM_SS, Locale.US);
            sdf.setTimeZone(timeZone);
            String log = String.format("%d %s %d", seq, sdf.format(calendar.getTime()), keyStatus);
            mLogList.add(log);
            mAdapter.replaceData(mLogList);
            mLogString.append(log);
            mLogString.append("\n");
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_SET_OPEN_LOG) {
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
            if (result_code == 0) {
                Animation animation = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh);
                mBind.ivSync.startAnimation(animation);
                mBind.tvSync.setText("Stop");
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_SET_CLEAR_LOG) {
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
            if (result_code == 0) {
                mLogString = new StringBuilder();
                writeTHFile("");
                mLogList.clear();
                mAdapter.replaceData(mLogList);
                Drawable top = getResources().getDrawable(R.drawable.ic_download);
                mBind.tvExport.setCompoundDrawablesWithIntrinsicBounds(null, top, null, null);
                ToastUtils.showToast(this, "Set up succeed");
            } else {
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

    private void setOpenLog() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SET_OPEN_LOG;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onEmpty(View view) {
        if (isWindowLocked()) return;
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Warning!");
        dialog.setMessage("Are you sure to erase all the saved acc data?");
        dialog.setConfirm(R.string.permission_open);
        dialog.setOnAlertConfirmListener(() -> {
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                finish();
            }, 30 * 1000);
            showLoadingProgressDialog();
            setClearLog();
        });
        dialog.show(getSupportFragmentManager());

    }

    public void onExport(View view) {
        if (isWindowLocked()) return;
        if (mLogList.isEmpty()) return;
        showLoadingProgressDialog();
        writeTHFile("");
        mBind.tvExport.postDelayed(new Runnable() {
            @Override
            public void run() {
                dismissLoadingProgressDialog();
                String log = mLogString.toString();
                if (!TextUtils.isEmpty(log)) {
                    writeTHFile(log);
                    File file = getTHFile();
                    // 发送邮件
                    String address = "Development@mokotechnology.com";
                    String title = "Button Log";
                    String content = title;
                    Utils.sendEmail(ButtonLogActivity.this, address, content, title, "Choose Email Client", file);
                }
            }
        }, 500);
    }

    private void setClearLog() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SET_CLEAR_LOG;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public static void writeTHFile(String thLog) {
        File file = new File(PATH_LOGCAT);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(thLog);
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static File getTHFile() {
        File file = new File(PATH_LOGCAT);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }
}
