package com.moko.commuregw.activity;

import android.content.Intent;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.RadioGroup;

import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.adapter.MQTTFragmentAdapter;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityMqttDeviceRemoteBinding;
import com.moko.commuregw.entity.GatewayConfig;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.fragment.GeneralDeviceFragment;
import com.moko.commuregw.fragment.SSLDeviceFragment;
import com.moko.commuregw.fragment.UserDeviceFragment;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MokoSupport;
import com.moko.support.commuregw.OrderTaskAssembler;
import com.moko.support.commuregw.entity.OrderCHAR;
import com.moko.support.commuregw.entity.ParamsKeyEnum;
import com.moko.support.commuregw.entity.ParamsLongKeyEnum;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

public class MqttSettingsActivity extends BaseActivity<ActivityMqttDeviceRemoteBinding> implements RadioGroup.OnCheckedChangeListener {
    private final String FILTER_ASCII = "[ -~]*";

    private GeneralDeviceFragment generalFragment;
    private UserDeviceFragment userFragment;
    private SSLDeviceFragment sslFragment;
    private MQTTFragmentAdapter adapter;
    private ArrayList<Fragment> fragments;

    private MQTTConfig mqttDeviceConfig;

    private boolean mSavedParamsError;
    private boolean mIsSaved;

    private InputFilter filter;

    private GatewayConfig mGatewayConfig;

    private boolean mIsRetainParams;


    @Override
    protected void onCreate() {
        mGatewayConfig = getIntent().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
        mIsRetainParams = mGatewayConfig != null;
        filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        mBind.etMqttHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etMqttClientId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etMqttSubscribeTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        mBind.etMqttPublishTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        mqttDeviceConfig = new MQTTConfig();
        if (mIsRetainParams) {
            mqttDeviceConfig.connectMode = mGatewayConfig.sslEnable != 0 ? mGatewayConfig.certType : 0;
            sslFragment.setConnectMode(mqttDeviceConfig.connectMode);

            mqttDeviceConfig.host = mGatewayConfig.host;
            mBind.etMqttHost.setText(mqttDeviceConfig.host);

            mqttDeviceConfig.port = mGatewayConfig.port;
            mBind.etMqttPort.setText(mqttDeviceConfig.port);

            mqttDeviceConfig.cleanSession = mGatewayConfig.cleanSession;
            generalFragment.setCleanSession(mqttDeviceConfig.cleanSession);

            mqttDeviceConfig.keepAlive = mGatewayConfig.keepAlive;
            generalFragment.setKeepAlive(mqttDeviceConfig.keepAlive);

            mqttDeviceConfig.qos = mGatewayConfig.qos;
            generalFragment.setQos(mqttDeviceConfig.qos);

            mqttDeviceConfig.clientId = mGatewayConfig.clientId;
            mBind.etMqttClientId.setText(mqttDeviceConfig.clientId);

            mqttDeviceConfig.topicSubscribe = mGatewayConfig.topicSubscribe;
            mBind.etMqttSubscribeTopic.setText(mqttDeviceConfig.topicSubscribe);

            mqttDeviceConfig.topicPublish = mGatewayConfig.topicSubscribe;
            mBind.etMqttPublishTopic.setText(mqttDeviceConfig.topicPublish);

            mqttDeviceConfig.username = mGatewayConfig.username;
            userFragment.setUserName(mqttDeviceConfig.username);
            mqttDeviceConfig.password = mGatewayConfig.password;
            userFragment.setPassword(mqttDeviceConfig.password);

            mqttDeviceConfig.caPath = mGatewayConfig.caPath;
            mqttDeviceConfig.clientCertPath = mGatewayConfig.clientCertPath;
            mqttDeviceConfig.clientKeyPath = mGatewayConfig.clientKeyPath;
        }
        createFragment();
        initData();
        adapter = new MQTTFragmentAdapter(this);
        adapter.setFragmentList(fragments);
        mBind.vpMqtt.setAdapter(adapter);
        mBind.vpMqtt.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    mBind.rbGeneral.setChecked(true);
                } else if (position == 1) {
                    mBind.rbUser.setChecked(true);
                } else if (position == 2) {
                    mBind.rbSsl.setChecked(true);
                }
            }
        });
        mBind.vpMqtt.setOffscreenPageLimit(4);
        mBind.rgMqtt.setOnCheckedChangeListener(this);
        if (mIsRetainParams) return;
        showLoadingProgressDialog();
        mBind.title.postDelayed(() -> {
            ArrayList<OrderTask> orderTasks = new ArrayList<>();
            orderTasks.add(OrderTaskAssembler.getDeviceName());
            orderTasks.add(OrderTaskAssembler.getWifiMac());
            orderTasks.add(OrderTaskAssembler.getMQTTConnectMode());
            orderTasks.add(OrderTaskAssembler.getMQTTHost());
            orderTasks.add(OrderTaskAssembler.getMQTTPort());
            orderTasks.add(OrderTaskAssembler.getMQTTCleanSession());
            orderTasks.add(OrderTaskAssembler.getMQTTKeepAlive());
            orderTasks.add(OrderTaskAssembler.getMQTTQos());
            orderTasks.add(OrderTaskAssembler.getMQTTClientId());
            orderTasks.add(OrderTaskAssembler.getMQTTSubscribeTopic());
            orderTasks.add(OrderTaskAssembler.getMQTTPublishTopic());
            orderTasks.add(OrderTaskAssembler.getMQTTUsername());
            orderTasks.add(OrderTaskAssembler.getMQTTPassword());
            ;
            MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
        }, 500);

    }

    @Override
    protected ActivityMqttDeviceRemoteBinding getViewBinding() {
        return ActivityMqttDeviceRemoteBinding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.POSTING, priority = 100)
    public void onConnectStatusEvent(ConnectStatusEvent event) {
        String action = event.getAction();
        if (MokoConstants.ACTION_DISCONNECTED.equals(action)) {
            runOnUiThread(() -> {
                dismissLoadingProgressDialog();
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
                        if (header == 0xEE) {
                            ParamsLongKeyEnum configKeyEnum = ParamsLongKeyEnum.fromParamKey(cmd);
                            if (configKeyEnum == null) {
                                return;
                            }
                            if (flag == 0x01) {
                                // write
                                int result = value[4] & 0xFF;
                                switch (configKeyEnum) {
                                    case KEY_MQTT_USERNAME:
                                    case KEY_MQTT_PASSWORD:
                                    case KEY_MQTT_CLIENT_KEY:
                                    case KEY_MQTT_CLIENT_CERT:
                                        if (result != 1) {
                                            mSavedParamsError = true;
                                        }
                                        break;
                                    case KEY_MQTT_CA:
                                        if (mSavedParamsError) {
                                            ToastUtils.showToast(this, "Setup failed！");
                                        } else {
                                            mIsSaved = true;
                                            ToastUtils.showToast(this, "Setup succeed！");
                                        }
                                        break;
                                }
                            }
                            if (flag == 0x00) {
                                int length = MokoUtils.toInt(Arrays.copyOfRange(value, 3, 5));
                                // read
                                switch (configKeyEnum) {
                                    case KEY_MQTT_USERNAME:
                                        mqttDeviceConfig.username = new String(Arrays.copyOfRange(value, 5, 5 + length));
                                        userFragment.setUserName(mqttDeviceConfig.username);
                                        break;
                                    case KEY_MQTT_PASSWORD:
                                        mqttDeviceConfig.password = new String(Arrays.copyOfRange(value, 5, 5 + length));
                                        userFragment.setPassword(mqttDeviceConfig.password);
                                        break;
                                }
                            }
                        }
                        if (header == 0xED) {
                            ParamsKeyEnum configKeyEnum = ParamsKeyEnum.fromParamKey(cmd);
                            if (configKeyEnum == null) {
                                return;
                            }
                            int length = value[3] & 0xFF;
                            if (flag == 0x01) {
                                // write
                                int result = value[4] & 0xFF;
                                switch (configKeyEnum) {
                                    case KEY_MQTT_HOST:
                                    case KEY_MQTT_PORT:
                                    case KEY_MQTT_CLIENT_ID:
                                    case KEY_MQTT_SUBSCRIBE_TOPIC:
                                    case KEY_MQTT_PUBLISH_TOPIC:
                                    case KEY_MQTT_CLEAN_SESSION:
                                    case KEY_MQTT_QOS:
                                    case KEY_MQTT_KEEP_ALIVE:
                                    case KEY_MQTT_LWT_ENABLE:
                                    case KEY_MQTT_LWT_RETAIN:
                                    case KEY_MQTT_LWT_QOS:
                                    case KEY_MQTT_LWT_TOPIC:
                                    case KEY_MQTT_LWT_PAYLOAD:
                                        if (result != 1) {
                                            mSavedParamsError = true;
                                        }
                                        break;
                                    case KEY_MQTT_CONNECT_MODE:
                                        if (result != 1) {
                                            mSavedParamsError = true;
                                        }
                                        if (mSavedParamsError) {
                                            ToastUtils.showToast(this, "Setup failed！");
                                        } else {
                                            mIsSaved = true;
                                            ToastUtils.showToast(this, "Setup succeed！");
                                        }
                                        break;
                                }
                            }
                            if (flag == 0x00) {
                                if (length == 0)
                                    return;
                                // read
                                switch (configKeyEnum) {
                                    case KEY_MQTT_CONNECT_MODE:
                                        mqttDeviceConfig.connectMode = value[4];
                                        sslFragment.setConnectMode(mqttDeviceConfig.connectMode);
                                        break;
                                    case KEY_MQTT_HOST:
                                        mqttDeviceConfig.host = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                        mBind.etMqttHost.setText(mqttDeviceConfig.host);
                                        break;
                                    case KEY_MQTT_PORT:
                                        mqttDeviceConfig.port = String.valueOf(MokoUtils.toInt(Arrays.copyOfRange(value, 4, 4 + length)));
                                        mBind.etMqttPort.setText(mqttDeviceConfig.port);
                                        break;
                                    case KEY_MQTT_CLEAN_SESSION:
                                        mqttDeviceConfig.cleanSession = value[4] == 1;
                                        generalFragment.setCleanSession(mqttDeviceConfig.cleanSession);
                                        break;
                                    case KEY_MQTT_KEEP_ALIVE:
                                        mqttDeviceConfig.keepAlive = value[4] & 0xFF;
                                        generalFragment.setKeepAlive(mqttDeviceConfig.keepAlive);
                                        break;
                                    case KEY_MQTT_QOS:
                                        mqttDeviceConfig.qos = value[4] & 0xFF;
                                        generalFragment.setQos(mqttDeviceConfig.qos);
                                        break;
                                    case KEY_MQTT_CLIENT_ID:
                                        mqttDeviceConfig.clientId = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                        mBind.etMqttClientId.setText(mqttDeviceConfig.clientId);
                                        break;
                                    case KEY_MQTT_SUBSCRIBE_TOPIC:
                                        mqttDeviceConfig.topicSubscribe = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                        mBind.etMqttSubscribeTopic.setText(mqttDeviceConfig.topicSubscribe);
                                        break;
                                    case KEY_MQTT_PUBLISH_TOPIC:
                                        mqttDeviceConfig.topicPublish = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                        mBind.etMqttPublishTopic.setText(mqttDeviceConfig.topicPublish);
                                        break;
                                    case KEY_DEVICE_NAME:
                                        String name = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                        mqttDeviceConfig.deviceName = name;
                                        break;
                                    case KEY_WIFI_MAC:
                                        String mac = MokoUtils.bytesToHexString(Arrays.copyOfRange(value, 4, 4 + length));
                                        mqttDeviceConfig.staMac = mac;
                                        break;
                                }
                            }
                        }
                    }
                    break;
            }
        }
    }

    private void createFragment() {
        fragments = new ArrayList<>();
        generalFragment = GeneralDeviceFragment.newInstance();
        userFragment = UserDeviceFragment.newInstance();
        sslFragment = SSLDeviceFragment.newInstance();
        fragments.add(generalFragment);
        fragments.add(userFragment);
        fragments.add(sslFragment);
    }

    private void initData() {
        mBind.etMqttHost.setText(mqttDeviceConfig.host);
        mBind.etMqttPort.setText(mqttDeviceConfig.port);
        mBind.etMqttClientId.setText(mqttDeviceConfig.clientId);
        mBind.etMqttSubscribeTopic.setText(mqttDeviceConfig.topicSubscribe);
        mBind.etMqttPublishTopic.setText(mqttDeviceConfig.topicPublish);
        generalFragment.setCleanSession(mqttDeviceConfig.cleanSession);
        generalFragment.setQos(mqttDeviceConfig.qos);
        generalFragment.setKeepAlive(mqttDeviceConfig.keepAlive);
        userFragment.setUserName(mqttDeviceConfig.username);
        userFragment.setPassword(mqttDeviceConfig.password);
        sslFragment.setCAPath(mqttDeviceConfig.caPath);
        sslFragment.setClientKeyPath(mqttDeviceConfig.clientKeyPath);
        sslFragment.setClientCertPath(mqttDeviceConfig.clientCertPath);
        sslFragment.setConnectMode(mqttDeviceConfig.connectMode);
    }

    public void onBack(View view) {
        back();
    }

    @Override
    public void onBackPressed() {
        back();
    }

    private void back() {
        if (mIsSaved) {
            Intent intent = new Intent();
            if (mIsRetainParams) {
                intent.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
            }
            intent.putExtra(AppConstants.EXTRA_KEY_MQTT_CONFIG_DEVICE, mqttDeviceConfig);
            setResult(RESULT_OK, intent);
        }
        finish();
    }

    @Override
    public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
        if (checkedId == R.id.rb_general) {
            mBind.vpMqtt.setCurrentItem(0);
        } else if (checkedId == R.id.rb_user) {
            mBind.vpMqtt.setCurrentItem(1);
        } else if (checkedId == R.id.rb_ssl) {
            mBind.vpMqtt.setCurrentItem(2);
        }
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (isParaError()) return;
        if (mIsRetainParams) {
            mGatewayConfig.host = mqttDeviceConfig.host;
            mGatewayConfig.port = mqttDeviceConfig.port;
            mGatewayConfig.clientId = mqttDeviceConfig.clientId;
            mGatewayConfig.cleanSession = mqttDeviceConfig.cleanSession;
            mGatewayConfig.qos = mqttDeviceConfig.qos;
            mGatewayConfig.keepAlive = mqttDeviceConfig.keepAlive;
            mGatewayConfig.topicSubscribe = mqttDeviceConfig.topicSubscribe;
            mGatewayConfig.topicPublish = mqttDeviceConfig.topicPublish;
            mGatewayConfig.username = mqttDeviceConfig.username;
            mGatewayConfig.password = mqttDeviceConfig.password;
            mGatewayConfig.sslEnable = mqttDeviceConfig.connectMode > 0 ? 1 : 0;
            if (mqttDeviceConfig.connectMode > 0) {
                mGatewayConfig.certType = mqttDeviceConfig.connectMode;
            }
            mGatewayConfig.caPath = mqttDeviceConfig.caPath;
            mGatewayConfig.clientKeyPath = mqttDeviceConfig.clientKeyPath;
            mGatewayConfig.clientCertPath = mqttDeviceConfig.clientCertPath;
            mIsSaved = true;
            ToastUtils.showToast(this, "Setup succeed！");
            return;
        }
        setMQTTDeviceConfig();
    }

    private boolean isParaError() {
        String host = mBind.etMqttHost.getText().toString().replaceAll(" ", "");
        String port = mBind.etMqttPort.getText().toString();
        String clientId = mBind.etMqttClientId.getText().toString().replaceAll(" ", "");
        String topicSubscribe = mBind.etMqttSubscribeTopic.getText().toString();
        String topicPublish = mBind.etMqttPublishTopic.getText().toString();

        if (TextUtils.isEmpty(host)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_host));
            return true;
        }
        if (TextUtils.isEmpty(port)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_port_empty));
            return true;
        }
        if (Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_port));
            return true;
        }
        if (TextUtils.isEmpty(clientId)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_client_id_empty));
            return true;
        }
        if (TextUtils.isEmpty(topicSubscribe)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_topic_subscribe));
            return true;
        }
        if (TextUtils.isEmpty(topicPublish)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_topic_publish));
            return true;
        }

        if (topicPublish.equals(topicSubscribe)) {
            ToastUtils.showToast(this, "Subscribed and published topic can't be same !");
            return true;
        }
        if (!generalFragment.isValid() || !sslFragment.isValid())
            return true;
        mqttDeviceConfig.host = host;
        mqttDeviceConfig.port = port;
        mqttDeviceConfig.clientId = clientId;
        mqttDeviceConfig.cleanSession = generalFragment.isCleanSession();
        mqttDeviceConfig.qos = generalFragment.getQos();
        mqttDeviceConfig.keepAlive = generalFragment.getKeepAlive();
        mqttDeviceConfig.topicSubscribe = topicSubscribe;
        mqttDeviceConfig.topicPublish = topicPublish;
        mqttDeviceConfig.username = userFragment.getUsername();
        mqttDeviceConfig.password = userFragment.getPassword();
        mqttDeviceConfig.connectMode = sslFragment.getConnectMode();
        mqttDeviceConfig.caPath = sslFragment.getCaPath();
        mqttDeviceConfig.clientKeyPath = sslFragment.getClientKeyPath();
        mqttDeviceConfig.clientCertPath = sslFragment.getClientCertPath();
        return false;
    }

    private void setMQTTDeviceConfig() {
        try {
            showLoadingProgressDialog();
            ArrayList<OrderTask> orderTasks = new ArrayList<>();
            orderTasks.add(OrderTaskAssembler.setMqttHost(mqttDeviceConfig.host));
            orderTasks.add(OrderTaskAssembler.setMqttPort(Integer.parseInt(mqttDeviceConfig.port)));
            orderTasks.add(OrderTaskAssembler.setMqttClientId(mqttDeviceConfig.clientId));
            orderTasks.add(OrderTaskAssembler.setMqttCleanSession(mqttDeviceConfig.cleanSession ? 1 : 0));
            orderTasks.add(OrderTaskAssembler.setMqttQos(mqttDeviceConfig.qos));
            orderTasks.add(OrderTaskAssembler.setMqttKeepAlive(mqttDeviceConfig.keepAlive));
            orderTasks.add(OrderTaskAssembler.setMqttPublishTopic(mqttDeviceConfig.topicPublish));
            orderTasks.add(OrderTaskAssembler.setMqttSubscribeTopic(mqttDeviceConfig.topicSubscribe));
            orderTasks.add(OrderTaskAssembler.setMqttUserName(mqttDeviceConfig.username));
            orderTasks.add(OrderTaskAssembler.setMqttPassword(mqttDeviceConfig.password));
            orderTasks.add(OrderTaskAssembler.setMqttConnectMode(mqttDeviceConfig.connectMode));
            if (mqttDeviceConfig.connectMode == 2) {
                File file = new File(mqttDeviceConfig.caPath);
                orderTasks.add(OrderTaskAssembler.setCA(file));
            } else if (mqttDeviceConfig.connectMode == 3) {
                File clientKeyFile = new File(mqttDeviceConfig.clientKeyPath);
                orderTasks.add(OrderTaskAssembler.setClientKey(clientKeyFile));
                File clientCertFile = new File(mqttDeviceConfig.clientCertPath);
                orderTasks.add(OrderTaskAssembler.setClientCert(clientCertFile));
                File caFile = new File(mqttDeviceConfig.caPath);
                orderTasks.add(OrderTaskAssembler.setCA(caFile));
            }
            MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
        } catch (Exception e) {
            ToastUtils.showToast(this, "File is missing");
        }
    }

    public void selectCertificate(View view) {
        if (isWindowLocked())
            return;
        sslFragment.selectCertificate();
    }

    public void selectCAFile(View view) {
        if (isWindowLocked())
            return;
        sslFragment.selectCAFile();
    }

    public void selectKeyFile(View view) {
        if (isWindowLocked())
            return;
        sslFragment.selectKeyFile();
    }

    public void selectCertFile(View view) {
        if (isWindowLocked())
            return;
        sslFragment.selectCertFile();
    }
}
