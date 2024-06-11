package com.moko.commuregw.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import com.github.lzyzsd.circleprogress.DonutProgress;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityDeviceConfigBinding;
import com.moko.commuregw.db.DBTools;
import com.moko.commuregw.dialog.CustomDialog;
import com.moko.commuregw.entity.GatewayConfig;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.MokoSupport;
import com.moko.support.commuregw.OrderTaskAssembler;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.entity.OrderCHAR;
import com.moko.support.commuregw.entity.ParamsKeyEnum;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class DeviceConfigActivity extends BaseActivity<ActivityDeviceConfigBinding> {

    private MQTTConfig mAppMqttConfig;
    private MQTTConfig mDeviceMqttConfig;

    private Handler mHandler;

    private int mSelectedDeviceType;

    private boolean mIsMQTTConfigFinished;
    private boolean mIsWIFIConfigFinished;

    private CustomDialog mqttConnDialog;
    private DonutProgress donutProgress;
    private boolean isSettingSuccess;
    private boolean isDeviceConnectSuccess;
    private GatewayConfig mGatewayConfig;

    private boolean mIsRetainParams;

    @Override
    protected void onCreate() {
        mSelectedDeviceType = getIntent().getIntExtra(AppConstants.EXTRA_KEY_SELECTED_DEVICE_TYPE, -1);
        mGatewayConfig = getIntent().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
        mIsRetainParams = mGatewayConfig != null;
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        mAppMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mHandler = new Handler(Looper.getMainLooper());
        mBind.tvConnect.setVisibility(mIsRetainParams ? View.GONE : View.VISIBLE);
        mBind.tvRetainParams.setVisibility(mIsRetainParams ? View.VISIBLE : View.GONE);
        if (mIsRetainParams) {
            showLoadingProgressDialog();
            mBind.tvName.postDelayed(() -> {
                // 线上clientId都一样，这里读取设备自己的
                MokoSupport.getInstance().sendOrder(OrderTaskAssembler.getMQTTClientId());
            }, 500);
        }
    }

    @Override
    protected ActivityDeviceConfigBinding getViewBinding() {
        return ActivityDeviceConfigBinding.inflate(getLayoutInflater());
    }


    @Subscribe(threadMode = ThreadMode.POSTING, priority = 50)
    public void onConnectStatusEvent(ConnectStatusEvent event) {
        String action = event.getAction();
        EventBus.getDefault().cancelEventDelivery(event);
        if (isSettingSuccess)
            return;
        if (MokoConstants.ACTION_DISCONNECTED.equals(action)) {
            runOnUiThread(() -> {
                Intent intent = new Intent();
                setResult(RESULT_OK, intent);
                finish();
            });
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onOrderTaskResponseEvent(OrderTaskResponseEvent event) {
        final String action = event.getAction();
        if (MokoConstants.ACTION_ORDER_FINISH.equals(action)) {
            dismissLoadingProgressDialog();
        }
        if (MokoConstants.ACTION_ORDER_RESULT.equals(action)) {
            OrderTaskResponse response = event.getResponse();
            OrderCHAR orderCHAR = (OrderCHAR) response.orderCHAR;
            int responseType = response.responseType;
            byte[] value = response.responseValue;
            switch (orderCHAR) {
                case CHAR_PARAMS:
                    if (value.length >= 4) {
                        int header = value[0] & 0xFF;// 0xED
                        int flag = value[1] & 0xFF;// read or write
                        int cmd = value[2] & 0xFF;
                        if (header == 0xED) {
                            ParamsKeyEnum configKeyEnum = ParamsKeyEnum.fromParamKey(cmd);
                            if (configKeyEnum == null) {
                                return;
                            }
                            int length = value[3] & 0xFF;
                            if (flag == 0x01) {
                                // write
                                int result = value[4] & 0xFF;
                                if (configKeyEnum == ParamsKeyEnum.KEY_EXIT_CONFIG_MODE) {
                                    if (result != 1) {
                                        ToastUtils.showToast(this, "Setup failed！");
                                    } else {
                                        if (mIsRetainParams) {
                                            ToastUtils.showToast(this, "Setup succeed！");
                                            return;
                                        }
                                        isSettingSuccess = true;
                                        showConnMqttDialog();
                                        subscribeTopic();
                                    }
                                }
                            }
                            if (flag == 0x00) {
                                if (length == 0) return;
                                if (configKeyEnum == ParamsKeyEnum.KEY_MQTT_CLIENT_ID) {
                                    mGatewayConfig.clientId = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                }
                            }
                        }
                    }
                    break;
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        final String topic = event.getTopic();
        final String message = event.getMessage();
        if (TextUtils.isEmpty(topic) || isDeviceConnectSuccess) {
            return;
        }
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
        if (msg_id != MQTTConstants.NOTIFY_MSG_ID_NETWORKING_STATUS)
            return;
        Type type = new TypeToken<MsgNotify<Object>>() {
        }.getType();
        MsgNotify<Object> msgNotify = new Gson().fromJson(message, type);
        final String mac = msgNotify.device_info.mac;
        if (!mDeviceMqttConfig.staMac.equals(mac)) {
            return;
        }
        if (donutProgress == null)
            return;
        if (!isDeviceConnectSuccess) {
            isDeviceConnectSuccess = true;
            donutProgress.setProgress(100);
            donutProgress.setText(100 + "%");
            // 关闭进度条弹框，保存数据，跳转修改设备名称页面
            mBind.tvName.postDelayed(() -> {
                dismissConnMqttDialog();
                MokoDevice mokoDevice = DBTools.getInstance(DeviceConfigActivity.this).selectDeviceByMac(mDeviceMqttConfig.staMac);
                String mqttConfigStr = new Gson().toJson(mDeviceMqttConfig, MQTTConfig.class);
                if (mokoDevice == null) {
                    mokoDevice = new MokoDevice();
                    mokoDevice.name = mDeviceMqttConfig.deviceName;
                    mokoDevice.mac = mDeviceMqttConfig.staMac;
                    mokoDevice.mqttInfo = mqttConfigStr;
                    mokoDevice.topicSubscribe = mDeviceMqttConfig.topicSubscribe;
                    mokoDevice.topicPublish = mDeviceMqttConfig.topicPublish;
                    mokoDevice.deviceType = mSelectedDeviceType;
                    DBTools.getInstance(DeviceConfigActivity.this).insertDevice(mokoDevice);
                } else {
                    mokoDevice.name = mDeviceMqttConfig.deviceName;
                    mokoDevice.mac = mDeviceMqttConfig.staMac;
                    mokoDevice.mqttInfo = mqttConfigStr;
                    mokoDevice.topicSubscribe = mDeviceMqttConfig.topicSubscribe;
                    mokoDevice.topicPublish = mDeviceMqttConfig.topicPublish;
                    mokoDevice.deviceType = mSelectedDeviceType;
                    DBTools.getInstance(DeviceConfigActivity.this).updateDevice(mokoDevice);
                }
                Intent modifyIntent = new Intent(DeviceConfigActivity.this, ModifyNameActivity.class);
                modifyIntent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mokoDevice);
                startActivity(modifyIntent);
            }, 1000);
        }
    }

    public void onBack(View view) {
        if (isWindowLocked()) return;
        back();
    }

    @Override
    public void onBackPressed() {
        if (isWindowLocked()) return;
        back();
    }

    private void back() {
        MokoSupport.getInstance().disConnectBle();
    }


    public void onWifiSettings(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, WifiSettingsActivity.class);
        if (mIsRetainParams)
            intent.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
        startWIFISettings.launch(intent);
    }

    public void onMqttSettings(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, MqttSettingsActivity.class);
        if (mIsRetainParams)
            intent.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
        startMQTTSettings.launch(intent);
    }

    public void onNetworkSettings(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, NetworkSettingsActivity.class);
        if (mIsRetainParams)
            intent.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
        startNetworkSettings.launch(intent);
    }

    public void onNtpSettings(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, NtpSettingsActivity.class);
        if (mIsRetainParams)
            intent.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
        startNetworkSettings.launch(intent);
    }

    public void onScannerFilter(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, ScannerFilterActivity.class);
        startActivity(intent);
    }

    public void onDeviceInfo(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, DeviceInformationActivity.class);
        startActivity(intent);
    }

    public void onConnect(View view) {
        if (isWindowLocked()) return;
        if (!mIsWIFIConfigFinished || !mIsMQTTConfigFinished) {
            ToastUtils.showToast(this, "Please configure WIFI and MQTT settings first!");
            return;
        }
        showLoadingProgressDialog();
        MokoSupport.getInstance().sendOrder(OrderTaskAssembler.exitConfigMode());
    }

    public void onRetainParams(View view) {
        if (isWindowLocked()) return;
        String wifiCaFile = SPUtiles.getStringValue(this, AppConstants.SP_KEY_WIFI_CA_FILE, "");
        String wifiCertFile = SPUtiles.getStringValue(this, AppConstants.SP_KEY_WIFI_CERT_FILE, "");
        String wifiKeyFile = SPUtiles.getStringValue(this, AppConstants.SP_KEY_WIFI_KEY_FILE, "");
        String mqttCaFile = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CA_FILE, "");
        String mqttCertFile = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CERT_FILE, "");
        String mqttKeyFile = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_KEY_FILE, "");
        try {
            showLoadingProgressDialog();
            // wifi
            List<OrderTask> orderTasks = new ArrayList<>();
            orderTasks.add(OrderTaskAssembler.setWifiSecurityType(mGatewayConfig.security));
            if (mGatewayConfig.security == 0) {
                orderTasks.add(OrderTaskAssembler.setWifiSSID(mGatewayConfig.wifiSSID));
                orderTasks.add(OrderTaskAssembler.setWifiPassword(mGatewayConfig.wifiPassword));
            } else {
                if (mGatewayConfig.eapType != 2) {
                    orderTasks.add(OrderTaskAssembler.setWifiSSID(mGatewayConfig.wifiSSID));
                    orderTasks.add(OrderTaskAssembler.setWifiEapUsername(mGatewayConfig.eapUserName));
                    orderTasks.add(OrderTaskAssembler.setWifiEapPassword(mGatewayConfig.eapPassword));
                    orderTasks.add(OrderTaskAssembler.setWifiEapVerifyServiceEnable(mGatewayConfig.verifyServer));
                    if (mGatewayConfig.verifyServer == 1) {
                        orderTasks.add(OrderTaskAssembler.setWifiCA(new File(wifiCaFile)));
                    }
                } else {
                    orderTasks.add(OrderTaskAssembler.setWifiSSID(mGatewayConfig.wifiSSID));
                    orderTasks.add(OrderTaskAssembler.setWifiEapDomainId(mGatewayConfig.domainId));
                    orderTasks.add(OrderTaskAssembler.getWifiEapVerifyServiceEnable());
                    orderTasks.add(OrderTaskAssembler.setWifiCA(new File(wifiCaFile)));
                    if (!TextUtils.isEmpty(wifiCertFile))
                        orderTasks.add(OrderTaskAssembler.setWifiClientCert(new File(wifiCertFile)));
                    if (!TextUtils.isEmpty(wifiKeyFile))
                        orderTasks.add(OrderTaskAssembler.setWifiClientKey(new File(wifiKeyFile)));

                }
            }
            orderTasks.add(OrderTaskAssembler.setWifiEapType(mGatewayConfig.eapType));
            // mqtt
            orderTasks.add(OrderTaskAssembler.setMqttHost(mGatewayConfig.host));
            orderTasks.add(OrderTaskAssembler.setMqttPort(Integer.parseInt(mGatewayConfig.port)));
            orderTasks.add(OrderTaskAssembler.setMqttClientId(mGatewayConfig.clientId));
            orderTasks.add(OrderTaskAssembler.setMqttCleanSession(mGatewayConfig.cleanSession ? 1 : 0));
            orderTasks.add(OrderTaskAssembler.setMqttQos(mGatewayConfig.qos));
            orderTasks.add(OrderTaskAssembler.setMqttKeepAlive(mGatewayConfig.keepAlive));
            orderTasks.add(OrderTaskAssembler.setMqttPublishTopic(mGatewayConfig.topicPublish));
            orderTasks.add(OrderTaskAssembler.setMqttSubscribeTopic(mGatewayConfig.topicSubscribe));
            orderTasks.add(OrderTaskAssembler.setMqttUserName(mGatewayConfig.username));
            orderTasks.add(OrderTaskAssembler.setMqttPassword(mGatewayConfig.password));
            orderTasks.add(OrderTaskAssembler.setMqttConnectMode(mGatewayConfig.sslEnable == 1 ? mGatewayConfig.certType : 0));
            if (mGatewayConfig.sslEnable == 1) {
                if (mGatewayConfig.certType == 2 && TextUtils.isEmpty(mqttCertFile)) {
                    File file = new File(mqttCaFile);
                    orderTasks.add(OrderTaskAssembler.setCA(file));
                } else if (mGatewayConfig.certType == 3) {
                    File clientKeyFile = new File(mqttKeyFile);
                    orderTasks.add(OrderTaskAssembler.setClientKey(clientKeyFile));
                    File clientCertFile = new File(mqttCertFile);
                    orderTasks.add(OrderTaskAssembler.setClientCert(clientCertFile));
                    File caFile = new File(mqttCaFile);
                    orderTasks.add(OrderTaskAssembler.setCA(caFile));
                }
            }
            // net
            if (mGatewayConfig.dhcp == 1) {
                String ip = mGatewayConfig.ip;
                String mask = mGatewayConfig.mask;
                String gateway = mGatewayConfig.gateway;
                String dns = mGatewayConfig.dns;
                String[] ipArray = ip.split("\\.");
                String ipHex = String.format("%s%s%s%s",
                        MokoUtils.int2HexString(Integer.parseInt(ipArray[0])),
                        MokoUtils.int2HexString(Integer.parseInt(ipArray[1])),
                        MokoUtils.int2HexString(Integer.parseInt(ipArray[2])),
                        MokoUtils.int2HexString(Integer.parseInt(ipArray[3])));
                String[] maskArray = mask.split("\\.");
                String maskHex = String.format("%s%s%s%s",
                        MokoUtils.int2HexString(Integer.parseInt(maskArray[0])),
                        MokoUtils.int2HexString(Integer.parseInt(maskArray[1])),
                        MokoUtils.int2HexString(Integer.parseInt(maskArray[2])),
                        MokoUtils.int2HexString(Integer.parseInt(maskArray[3])));
                String[] gatewayArray = gateway.split("\\.");
                String gatewayHex = String.format("%s%s%s%s",
                        MokoUtils.int2HexString(Integer.parseInt(gatewayArray[0])),
                        MokoUtils.int2HexString(Integer.parseInt(gatewayArray[1])),
                        MokoUtils.int2HexString(Integer.parseInt(gatewayArray[2])),
                        MokoUtils.int2HexString(Integer.parseInt(gatewayArray[3])));
                String[] dnsArray = dns.split("\\.");
                String dnsHex = String.format("%s%s%s%s",
                        MokoUtils.int2HexString(Integer.parseInt(dnsArray[0])),
                        MokoUtils.int2HexString(Integer.parseInt(dnsArray[1])),
                        MokoUtils.int2HexString(Integer.parseInt(dnsArray[2])),
                        MokoUtils.int2HexString(Integer.parseInt(dnsArray[3])));
                orderTasks.add(OrderTaskAssembler.setNetworkIPInfo(ipHex, maskHex, gatewayHex, dnsHex));
            }
            orderTasks.add(OrderTaskAssembler.setNetworkDHCP(mGatewayConfig.dhcp));
            // ntp
            orderTasks.add(OrderTaskAssembler.setNtpUrl(mGatewayConfig.ntpServer));
            orderTasks.add(OrderTaskAssembler.setTimezone(mGatewayConfig.timezone));
            // connect
            orderTasks.add(OrderTaskAssembler.exitConfigMode());
            MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
        } catch (Exception e) {
            ToastUtils.showToast(this, "File is missing");
        }
    }

    private final ActivityResultLauncher<Intent> startWIFISettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            mIsWIFIConfigFinished = true;
            if (mIsRetainParams) {
                mGatewayConfig = result.getData().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
            }
        }
    });
    private final ActivityResultLauncher<Intent> startMQTTSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            mIsMQTTConfigFinished = true;
            mDeviceMqttConfig = (MQTTConfig) result.getData().getSerializableExtra(AppConstants.EXTRA_KEY_MQTT_CONFIG_DEVICE);
            if (mIsRetainParams) {
                mGatewayConfig = result.getData().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
            }
        }
    });

    private final ActivityResultLauncher<Intent> startNetworkSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (mIsRetainParams) {
                mGatewayConfig = result.getData().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
            }
        }
    });
    private int progress;

    private void showConnMqttDialog() {
        isDeviceConnectSuccess = false;
        View view = LayoutInflater.from(this).inflate(R.layout.mqtt_conn_content, null);
        donutProgress = view.findViewById(R.id.dp_progress);
        mqttConnDialog = new CustomDialog.Builder(this)
                .setContentView(view)
                .create();
        mqttConnDialog.setCancelable(false);
        mqttConnDialog.show();
        new Thread(() -> {
            progress = 0;
            while (progress <= 100 && !isDeviceConnectSuccess) {
                runOnUiThread(() -> {
                    donutProgress.setProgress(progress);
                    donutProgress.setText(progress + "%");
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                progress++;
            }
        }).start();
        mHandler.postDelayed(() -> {
            if (!isDeviceConnectSuccess) {
                isDeviceConnectSuccess = true;
                isSettingSuccess = false;
                dismissConnMqttDialog();
                ToastUtils.showToast(DeviceConfigActivity.this, getString(R.string.mqtt_connecting_timeout));
                finish();
            }
        }, 90 * 1000);
    }

    private void dismissConnMqttDialog() {
        if (mqttConnDialog != null && !isFinishing() && mqttConnDialog.isShowing()) {
            isDeviceConnectSuccess = true;
            isSettingSuccess = false;
            mqttConnDialog.dismiss();
            mHandler.removeMessages(0);
        }
    }

    private void subscribeTopic() {
        // 订阅
        try {
            MQTTSupport.getInstance().subscribe(mDeviceMqttConfig.topicPublish, mAppMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
