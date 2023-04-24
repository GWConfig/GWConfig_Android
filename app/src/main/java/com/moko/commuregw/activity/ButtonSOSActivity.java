package com.moko.commuregw.activity;

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
import com.moko.commuregw.databinding.ActivityButtonSosBinding;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.BXPButtonInfo;
import com.moko.support.commuregw.entity.MsgConfigResult;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.event.DeviceOnlineEvent;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;


public class ButtonSOSActivity extends BaseActivity<ActivityButtonSosBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    private BXPButtonInfo mBXPButtonInfo;
    public Handler mHandler;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        mBXPButtonInfo = (BXPButtonInfo) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_BXP_BUTTON_INFO);

        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getButtonSos();
    }

    @Override
    protected ActivityButtonSosBinding getViewBinding() {
        return ActivityButtonSosBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_GET_BUTTON_SOS) {
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
            int mode = result.data.get("mode").getAsInt();
            if (mode == 0) {
                mBind.rbSingle.setChecked(true);
            } else if (mode == 1) {
                mBind.rbDouble.setChecked(true);
            } else if (mode == 2) {
                mBind.rbTriple.setChecked(true);
            }
            mBind.rgButtonSos.setOnCheckedChangeListener((group, checkedId) -> {
                int value = 0;
                if (checkedId == R.id.rb_single) {
                    value = 0;
                } else if (checkedId == R.id.rb_double) {
                    value = 1;
                } else if (checkedId == R.id.rb_triple) {
                    value = 2;
                }
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    ToastUtils.showToast(this, "Set up failed");
                }, 30 * 1000);
                showLoadingProgressDialog();
                setButtonSos(value);
            });

        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_BUTTON_SET_BUTTON_SOS) {
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
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    public void onBack(View view) {
        finish();
    }

    private void getButtonSos() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_GET_BUTTON_SOS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setButtonSos(int value) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_SET_BUTTON_SOS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBXPButtonInfo.mac);
        jsonObject.addProperty("mode", value);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

}
