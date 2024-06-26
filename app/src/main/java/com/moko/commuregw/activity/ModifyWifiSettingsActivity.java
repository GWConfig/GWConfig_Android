package com.moko.commuregw.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityModifyWifiSettingsBinding;
import com.moko.commuregw.dialog.BottomDialog;
import com.moko.commuregw.entity.GatewayConfig;
import com.moko.commuregw.entity.MQTTConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MQTTConstants;
import com.moko.support.commuregw.MQTTSupport;
import com.moko.support.commuregw.entity.MsgConfigResult;
import com.moko.support.commuregw.entity.MsgNotify;
import com.moko.support.commuregw.entity.MsgReadResult;
import com.moko.support.commuregw.event.DeviceOnlineEvent;
import com.moko.support.commuregw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class ModifyWifiSettingsActivity extends BaseActivity<ActivityModifyWifiSettingsBinding> {
    private final String FILTER_ASCII = "[ -~]*";
    private InputFilter filter;

    private ArrayList<String> mSecurityValues;
    private int mSecuritySelected;
    private ArrayList<String> mEAPTypeValues;
    private int mEAPTypeSelected;

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    public Handler mHandler;
    private GatewayConfig mGatewayConfig;
    private boolean mIsRetainParams;

    @Override
    protected void onCreate() {
        mGatewayConfig = getIntent().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
        mIsRetainParams = mGatewayConfig != null;
        mBind.cbVerifyServer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mSecuritySelected != 0 && mEAPTypeSelected != 2)
                mBind.clCa.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        mSecurityValues = new ArrayList<>();
        mSecurityValues.add("Personal");
        mSecurityValues.add("Enterprise");
        mEAPTypeValues = new ArrayList<>();
        mEAPTypeValues.add("PEAP-MSCHAPV2");
        mEAPTypeValues.add("TTLS-MSCHAPV2");
        mEAPTypeValues.add("TLS");
        filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        mBind.etUsername.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32), filter});
        mBind.etPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etEapPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etSsid.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32), filter});
        mBind.etDomainId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etCaFileUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256), filter});
        mBind.etCertFileUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256), filter});
        mBind.etKeyFileUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256), filter});

        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        if (mIsRetainParams) {
            mSecuritySelected = mGatewayConfig.security;
            mBind.tvSecurity.setText(mSecurityValues.get(mSecuritySelected));
            mBind.clEapType.setVisibility(mSecuritySelected != 0 ? View.VISIBLE : View.GONE);
            mBind.clPassword.setVisibility(mSecuritySelected != 0 ? View.GONE : View.VISIBLE);
            if (mSecuritySelected == 0) {
                mBind.clCa.setVisibility(View.GONE);
            } else {
                if (mEAPTypeSelected != 2) {
                    mBind.clCa.setVisibility(mBind.cbVerifyServer.isChecked() ? View.VISIBLE : View.GONE);
                } else {
                    mBind.clCa.setVisibility(View.VISIBLE);
                }
            }

            mBind.etSsid.setText(mGatewayConfig.wifiSSID);
            mBind.etPassword.setText(mGatewayConfig.wifiPassword);
            mBind.etEapPassword.setText(mGatewayConfig.eapPassword);
            mEAPTypeSelected = mGatewayConfig.eapType;
            mBind.tvEapType.setText(mEAPTypeValues.get(mEAPTypeSelected));
            if (mSecuritySelected == 0) {
                mBind.clCa.setVisibility(View.GONE);
                mBind.clUsername.setVisibility(View.GONE);
                mBind.clEapPassword.setVisibility(View.GONE);
                mBind.cbVerifyServer.setVisibility(View.GONE);
                mBind.clDomainId.setVisibility(View.GONE);
                mBind.clCert.setVisibility(View.GONE);
                mBind.clKey.setVisibility(View.GONE);
            } else {
                if (mEAPTypeSelected != 2)
                    mBind.clCa.setVisibility(mBind.cbVerifyServer.isChecked() ? View.VISIBLE : View.GONE);
                else
                    mBind.clCa.setVisibility(View.VISIBLE);
                mBind.clUsername.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
                mBind.clEapPassword.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
                mBind.cbVerifyServer.setVisibility(mEAPTypeSelected == 2 ? View.INVISIBLE : View.VISIBLE);
                mBind.clDomainId.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
                mBind.clCert.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
                mBind.clKey.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);

                mBind.etCaFileUrl.setText(mGatewayConfig.wifiCaPath);
                mBind.etCertFileUrl.setText(mGatewayConfig.wifiCertPath);
                mBind.etKeyFileUrl.setText(mGatewayConfig.wifiKeyPath);
            }
            mBind.etUsername.setText(mGatewayConfig.eapUserName);
            mBind.etDomainId.setText(mGatewayConfig.domainId);
            mBind.cbVerifyServer.setChecked(mGatewayConfig.verifyServer == 1);
            if (mSecuritySelected != 0 && mEAPTypeSelected != 2)
                mBind.clCa.setVisibility(mBind.cbVerifyServer.isChecked() ? View.VISIBLE : View.GONE);
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getWifiSettings();
    }

    @Override
    protected ActivityModifyWifiSettingsBinding getViewBinding() {
        return ActivityModifyWifiSettingsBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.READ_MSG_ID_WIFI_SETTINGS) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mSecuritySelected = result.data.get("security_type").getAsInt();
            mBind.etSsid.setText(result.data.get("ssid").getAsString());
            mBind.etPassword.setText(result.data.get("passwd").getAsString());
            mBind.etDomainId.setText(result.data.get("eap_id").getAsString());
            mBind.tvSecurity.setText(mSecurityValues.get(mSecuritySelected));
            mBind.clEapType.setVisibility(mSecuritySelected != 0 ? View.VISIBLE : View.GONE);
            mBind.clPassword.setVisibility(mSecuritySelected != 0 ? View.GONE : View.VISIBLE);

            mEAPTypeSelected = result.data.get("eap_type").getAsInt();
            mBind.tvEapType.setText(mEAPTypeValues.get(mEAPTypeSelected));
            if (mSecuritySelected != 0) {
                mBind.clUsername.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
                mBind.etUsername.setText(result.data.get("eap_username").getAsString());
                mBind.clEapPassword.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
                mBind.etEapPassword.setText(result.data.get("eap_passwd").getAsString());
                mBind.cbVerifyServer.setVisibility(mEAPTypeSelected == 2 ? View.INVISIBLE : View.VISIBLE);
                mBind.cbVerifyServer.setChecked(result.data.get("eap_verify_server").getAsInt() == 1);
                if (mEAPTypeSelected != 2) {
                    mBind.clCa.setVisibility(mBind.cbVerifyServer.isChecked() ? View.VISIBLE : View.GONE);
                } else {
                    mBind.clCa.setVisibility(View.VISIBLE);
                }
                mBind.clDomainId.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
                mBind.clCert.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
                mBind.clKey.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
            }
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_DEVICE_STATUS) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int status = result.data.get("status").getAsInt();
            if (status == 1) {
                ToastUtils.showToast(this, "Device is OTA, please wait");
                return;
            }
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            setWifiSettings();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_WIFI_SETTINGS) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
                if (mSecuritySelected == 0)
                    return;
                String caFileUrl = mBind.etCaFileUrl.getText().toString();
                String certFileUrl = mBind.etCertFileUrl.getText().toString();
                String keyFileUrl = mBind.etKeyFileUrl.getText().toString();
                // 若EAP类型不是TLS且CA证书为空，不发送证书更新指令
                if (mEAPTypeSelected != 2
                        && TextUtils.isEmpty(caFileUrl))
                    return;
                // 若EAP类型是TLS且所有证书都为空，不发送证书更新指令
                if (mEAPTypeSelected == 2
                        && TextUtils.isEmpty(caFileUrl)
                        && TextUtils.isEmpty(certFileUrl)
                        && TextUtils.isEmpty(keyFileUrl))
                    return;
                XLog.i("升级Wifi证书");
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    finish();
                }, 50 * 1000);
                showLoadingProgressDialog();
                setWifiCertFile();
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_WIFI_CERT_RESULT) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int resultCode = result.data.get("result_code").getAsInt();
            if (resultCode == 1) {
                ToastUtils.showToast(this, R.string.update_success);
            } else {
                ToastUtils.showToast(this, R.string.update_failed);
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

    private void setWifiSettings() {
        String ssid = mBind.etSsid.getText().toString();
        String username = mBind.etUsername.getText().toString();
        String password = mBind.etPassword.getText().toString();
        String eapPassword = mBind.etEapPassword.getText().toString();
        String domainId = mBind.etDomainId.getText().toString();
        int msgId = MQTTConstants.CONFIG_MSG_ID_WIFI_SETTINGS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("security_type", mSecuritySelected);
        jsonObject.addProperty("ssid", ssid);
        jsonObject.addProperty("passwd", mSecuritySelected == 0 ? password : "");
        jsonObject.addProperty("eap_type", mEAPTypeSelected);
        jsonObject.addProperty("eap_id", mEAPTypeSelected == 2 ? domainId : "");
        jsonObject.addProperty("eap_username", mSecuritySelected != 0 ? username : "");
        jsonObject.addProperty("eap_passwd", mSecuritySelected != 0 ? eapPassword : "");
        jsonObject.addProperty("eap_verify_server", mBind.cbVerifyServer.isChecked() ? 1 : 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setWifiCertFile() {
        String caFileUrl = mBind.etCaFileUrl.getText().toString();
        String certFileUrl = mBind.etCertFileUrl.getText().toString();
        String keyFileUrl = mBind.etKeyFileUrl.getText().toString();
        int msgId = MQTTConstants.CONFIG_MSG_ID_WIFI_CERT_FILE;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("ca_url", caFileUrl);
        jsonObject.addProperty("client_cert_url", certFileUrl);
        jsonObject.addProperty("client_key_url", keyFileUrl);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getWifiSettings() {
        int msgId = MQTTConstants.READ_MSG_ID_WIFI_SETTINGS;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onSelectSecurity(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mSecurityValues, mSecuritySelected);
        dialog.setListener(value -> {
            mSecuritySelected = value;
            mBind.tvSecurity.setText(mSecurityValues.get(value));
            mBind.clEapType.setVisibility(mSecuritySelected != 0 ? View.VISIBLE : View.GONE);
            mBind.clPassword.setVisibility(mSecuritySelected != 0 ? View.GONE : View.VISIBLE);
            if (mSecuritySelected == 0) {
                mBind.clCa.setVisibility(View.GONE);
                mBind.clUsername.setVisibility(View.GONE);
                mBind.clEapPassword.setVisibility(View.GONE);
                mBind.cbVerifyServer.setVisibility(View.GONE);
                mBind.clDomainId.setVisibility(View.GONE);
                mBind.clCert.setVisibility(View.GONE);
                mBind.clKey.setVisibility(View.GONE);
            } else {
                if (mEAPTypeSelected != 2) {
                    mBind.clCa.setVisibility(mBind.cbVerifyServer.isChecked() ? View.VISIBLE : View.GONE);
                } else {
                    mBind.clCa.setVisibility(View.VISIBLE);
                }
                mBind.clUsername.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
                mBind.clEapPassword.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
                mBind.cbVerifyServer.setVisibility(mEAPTypeSelected == 2 ? View.INVISIBLE : View.VISIBLE);
                mBind.clDomainId.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
                mBind.clCert.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
                mBind.clKey.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
            }
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSelectEAPType(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mEAPTypeValues, mEAPTypeSelected);
        dialog.setListener(value -> {
            mEAPTypeSelected = value;
            mBind.tvEapType.setText(mEAPTypeValues.get(value));
            mBind.clUsername.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
            mBind.clEapPassword.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
            mBind.cbVerifyServer.setVisibility(mEAPTypeSelected == 2 ? View.INVISIBLE : View.VISIBLE);
            mBind.clDomainId.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
            if (mEAPTypeSelected != 2) {
                mBind.clCa.setVisibility(mBind.cbVerifyServer.isChecked() ? View.VISIBLE : View.GONE);
            } else {
                mBind.clCa.setVisibility(View.VISIBLE);
            }
            mBind.clCert.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
            mBind.clKey.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (!isParaError()) {
            if (mIsRetainParams) {
                String ssid = mBind.etSsid.getText().toString();
                String username = mBind.etUsername.getText().toString();
                String password = mBind.etPassword.getText().toString();
                String eapPassword = mBind.etEapPassword.getText().toString();
                String domainId = mBind.etDomainId.getText().toString();
                String caPath = mBind.etCaFileUrl.getText().toString();
                String certPath = mBind.etCertFileUrl.getText().toString();
                String keyPath = mBind.etKeyFileUrl.getText().toString();
                mGatewayConfig.security = mSecuritySelected;
                if (mSecuritySelected == 0) {
                    mGatewayConfig.wifiSSID = ssid;
                    mGatewayConfig.wifiPassword = password;
                } else {
                    if (mEAPTypeSelected != 2) {
                        mGatewayConfig.wifiSSID = ssid;
                        mGatewayConfig.eapUserName = username;
                        mGatewayConfig.eapPassword = eapPassword;
                        mGatewayConfig.verifyServer = mBind.cbVerifyServer.isChecked() ? 1 : 0;
                        if (mGatewayConfig.verifyServer == 1)
                            mGatewayConfig.wifiCaPath = caPath;
                    } else {
                        mGatewayConfig.wifiSSID = ssid;
                        mGatewayConfig.domainId = domainId;
                        mGatewayConfig.wifiCaPath = caPath;
                        if (!TextUtils.isEmpty(certPath))
                            mGatewayConfig.wifiCertPath = certPath;
                        if (!TextUtils.isEmpty(keyPath))
                            mGatewayConfig.wifiKeyPath = keyPath;
                    }
                }
                mGatewayConfig.eapType = mEAPTypeSelected;
                ToastUtils.showToast(this, "Setup succeed！");
                Intent intent = new Intent();
                intent.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
                setResult(RESULT_OK, intent);
                finish();
                return;
            }
            saveParams();
        } else {
            ToastUtils.showToast(this, "Para Error");
        }
    }

    private boolean isParaError() {
        String ssid = mBind.etSsid.getText().toString();
        if (TextUtils.isEmpty(ssid))
            return true;
//        if (mSecuritySelected != 0) {
//            if (mEAPTypeSelected != 2 && !mBind.cbVerifyServer.isChecked()) {
//                return false;
//            }
//            String caFileUrl = mBind.etCaFileUrl.getText().toString();
//            if (TextUtils.isEmpty(caFileUrl))
//                return true;
//        }
        return false;
    }

    private void saveParams() {
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        XLog.i("查询设备当前状态");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 50 * 1000);
        showLoadingProgressDialog();
        getDeviceStatus();
    }

    private void getDeviceStatus() {
        int msgId = MQTTConstants.READ_MSG_ID_DEVICE_STATUS;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
