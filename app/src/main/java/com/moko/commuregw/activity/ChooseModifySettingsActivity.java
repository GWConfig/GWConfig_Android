package com.moko.commuregw.activity;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
import com.lzy.okgo.request.base.Request;
import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.BaseApplication;
import com.moko.commuregw.R;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityImportConfigFileBinding;
import com.moko.commuregw.entity.GatewayConfig;
import com.moko.commuregw.entity.MokoDevice;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MokoSupport;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

public class ChooseModifySettingsActivity extends BaseActivity<ActivityImportConfigFileBinding> {
    private GatewayConfig mGatewayConfig;
    private boolean isFileError;
    private int mIndex = 1;
    private MokoDevice mMokoDevice;

    private boolean mIsManual;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String url = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_GATEWAY_SETTINGS_URL, "");
        mBind.etConfigFileUrl.setText(url);
        mGatewayConfig = new GatewayConfig();
        mBind.tvNext.setOnClickListener(v -> {
            String fileUrl = mBind.etConfigFileUrl.getText().toString();
            if (!mIsManual) {
                if (TextUtils.isEmpty(fileUrl)) {
                    ToastUtils.showToast(this, "URL error");
                    return;
                }
                downloadNewConfigFile(fileUrl);
            } else {
                SPUtiles.setStringValue(ChooseModifySettingsActivity.this, AppConstants.SP_KEY_MQTT_GATEWAY_SETTINGS_URL, fileUrl);
                Intent intent = new Intent(ChooseModifySettingsActivity.this, ModifySettingsActivity.class);
                intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
                startLauncher.launch(intent);
            }
        });
        mBind.llImportConfig.setOnClickListener(v -> {
            mIsManual = false;
            mBind.ivImportConfig.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_selected));
            mBind.ivManualSettings.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_unselected));
            mBind.etConfigFileUrl.setVisibility(View.VISIBLE);
        });
        mBind.llManualSettings.setOnClickListener(v -> {
            mIsManual = true;
            mBind.ivImportConfig.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_unselected));
            mBind.ivManualSettings.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_selected));
            mBind.etConfigFileUrl.setVisibility(View.GONE);
        });
    }

    private void downloadNewConfigFile(String apkUrl) {
        String fileName = "Settings_for_Device.xlsx";
        String filePath = BaseApplication.PATH_LOGCAT + File.separator;
        File file = new File(filePath + fileName);
        if (file.exists()) {
            file.delete();
        }
        OkGo.<File>get(apkUrl)
                .execute(new FileCallback(filePath, fileName) {

                    @Override
                    public void onStart(Request<File, ? extends Request> request) {
                        showLoadingProgressDialog();
                    }

                    @Override
                    public void downloadProgress(Progress progress) {
                        XLog.i(("Config file Progress:" + (int) (progress.fraction * 100)));
                    }


                    @Override
                    public void onSuccess(Response<File> response) {
                        if (response.isSuccessful()) {
                            File downloadFile = response.body();
                            mIndex = 1;
                            new Thread(() -> {
                                try {
                                    Workbook workbook = WorkbookFactory.create(downloadFile);
                                    Sheet sheet = workbook.getSheetAt(0);
                                    int rows = sheet.getPhysicalNumberOfRows();
                                    int columns = sheet.getRow(0).getPhysicalNumberOfCells();
                                    // 从第二行开始
                                    if (rows < 33 || columns < 3) {
                                        runOnUiThread(() -> {
                                            dismissLoadingProgressDialog();
                                            ToastUtils.showToast(ChooseModifySettingsActivity.this, "Please select the correct file!");
                                        });
                                        return;
                                    }
                                    Cell hostCell = sheet.getRow(mIndex++).getCell(1);
                                    if (hostCell != null)
                                        mGatewayConfig.host = hostCell.getStringCellValue().replaceAll("value:", "");
                                    Cell postCell = sheet.getRow(mIndex++).getCell(1);
                                    if (postCell != null)
                                        mGatewayConfig.port = postCell.getStringCellValue().replaceAll("value:", "");
                                    Cell clientCell = sheet.getRow(mIndex++).getCell(1);
                                    if (clientCell != null)
                                        mGatewayConfig.clientId = clientCell.getStringCellValue().replaceAll("value:", "");
                                    Cell topicSubscribeCell = sheet.getRow(mIndex++).getCell(1);
                                    if (topicSubscribeCell != null) {
                                        mGatewayConfig.topicSubscribe = topicSubscribeCell.getStringCellValue().replaceAll("value:", "");
                                    }
                                    Cell topicPublishCell = sheet.getRow(mIndex++).getCell(1);
                                    if (topicPublishCell != null) {
                                        mGatewayConfig.topicPublish = topicPublishCell.getStringCellValue().replaceAll("value:", "");
                                    }
                                    Cell cleanSessionCell = sheet.getRow(mIndex++).getCell(1);
                                    if (cleanSessionCell != null)
                                        mGatewayConfig.cleanSession = "1".equals(cleanSessionCell.getStringCellValue().replaceAll("value:", ""));
                                    Cell qosCell = sheet.getRow(mIndex++).getCell(1);
                                    if (qosCell != null)
                                        mGatewayConfig.qos = Integer.parseInt(qosCell.getStringCellValue().replaceAll("value:", ""));
                                    Cell keepAliveCell = sheet.getRow(mIndex++).getCell(1);
                                    if (keepAliveCell != null)
                                        mGatewayConfig.keepAlive = Integer.parseInt(keepAliveCell.getStringCellValue().replaceAll("value:", ""));
                                    Cell usernameCell = sheet.getRow(mIndex++).getCell(1);
                                    if (usernameCell != null) {
                                        mGatewayConfig.username = usernameCell.getStringCellValue().replaceAll("value:", "");
                                    }
                                    Cell passwordCell = sheet.getRow(mIndex++).getCell(1);
                                    if (passwordCell != null) {
                                        mGatewayConfig.password = passwordCell.getStringCellValue().replaceAll("value:", "");
                                    }
                                    Cell connectModeCell = sheet.getRow(mIndex++).getCell(1);
                                    if (connectModeCell != null) {
                                        // 0/1
                                        mGatewayConfig.sslEnable = Integer.parseInt(connectModeCell.getStringCellValue().replaceAll("value:", ""));
                                    }
                                    Cell caCertCell = sheet.getRow(mIndex++).getCell(1);
                                    if (caCertCell != null)
                                        mGatewayConfig.caPath = caCertCell.getStringCellValue().replaceAll("value:", "");
                                    Cell clientCertCell = sheet.getRow(mIndex++).getCell(1);
                                    if (clientCertCell != null)
                                        mGatewayConfig.clientCertPath = clientCertCell.getStringCellValue().replaceAll("value:", "");
                                    Cell clientKeyCell = sheet.getRow(mIndex++).getCell(1);
                                    if (clientKeyCell != null)
                                        mGatewayConfig.clientKeyPath = clientKeyCell.getStringCellValue().replaceAll("value:", "");
                                    Cell certTypeCell = sheet.getRow(mIndex++).getCell(1);
                                    if (certTypeCell != null)
                                        mGatewayConfig.certType = Integer.parseInt(certTypeCell.getStringCellValue().replaceAll("value:", ""));
                                    Cell securityCell = sheet.getRow(mIndex++).getCell(1);
                                    if (securityCell != null)
                                        mGatewayConfig.security = Integer.parseInt(securityCell.getStringCellValue().replaceAll("value:", ""));
                                    Cell eapTypeCell = sheet.getRow(mIndex++).getCell(1);
                                    if (eapTypeCell != null)
                                        mGatewayConfig.eapType = Integer.parseInt(eapTypeCell.getStringCellValue().replaceAll("value:", ""));
                                    Cell wifiSSIDCell = sheet.getRow(mIndex++).getCell(1);
                                    if (wifiSSIDCell != null)
                                        mGatewayConfig.wifiSSID = wifiSSIDCell.getStringCellValue().replaceAll("value:", "");
                                    Cell wifiPasswordCell = sheet.getRow(mIndex++).getCell(1);
                                    if (wifiPasswordCell != null)
                                        mGatewayConfig.wifiPassword = wifiPasswordCell.getStringCellValue().replaceAll("value:", "");
                                    Cell domainIdCell = sheet.getRow(mIndex++).getCell(1);
                                    if (domainIdCell != null)
                                        mGatewayConfig.domainId = domainIdCell.getStringCellValue().replaceAll("value:", "");
                                    Cell eapUserNameCell = sheet.getRow(mIndex++).getCell(1);
                                    if (eapUserNameCell != null)
                                        mGatewayConfig.eapUserName = eapUserNameCell.getStringCellValue().replaceAll("value:", "");
                                    Cell eapPasswordCell = sheet.getRow(mIndex++).getCell(1);
                                    if (eapPasswordCell != null)
                                        mGatewayConfig.eapPassword = eapPasswordCell.getStringCellValue().replaceAll("value:", "");
                                    Cell verifyServerCell = sheet.getRow(mIndex++).getCell(1);
                                    if (verifyServerCell != null)
                                        mGatewayConfig.verifyServer = Integer.parseInt(verifyServerCell.getStringCellValue().replaceAll("value:", ""));
                                    Cell wifiCaPathCell = sheet.getRow(mIndex++).getCell(1);
                                    if (wifiCaPathCell != null)
                                        mGatewayConfig.wifiCaPath = wifiCaPathCell.getStringCellValue().replaceAll("value:", "");
                                    Cell wifiCertPathCell = sheet.getRow(mIndex++).getCell(1);
                                    if (wifiCertPathCell != null)
                                        mGatewayConfig.wifiCertPath = wifiCertPathCell.getStringCellValue().replaceAll("value:", "");
                                    Cell wifiKeyPathCell = sheet.getRow(mIndex++).getCell(1);
                                    if (wifiKeyPathCell != null)
                                        mGatewayConfig.wifiKeyPath = wifiKeyPathCell.getStringCellValue().replaceAll("value:", "");
                                    Cell dhcpCell = sheet.getRow(mIndex++).getCell(1);
                                    if (dhcpCell != null)
                                        mGatewayConfig.dhcp = Integer.parseInt(dhcpCell.getStringCellValue().replaceAll("value:", ""));
                                    Cell ipCell = sheet.getRow(mIndex++).getCell(1);
                                    if (ipCell != null)
                                        mGatewayConfig.ip = ipCell.getStringCellValue().replaceAll("value:", "");
                                    Cell maskCell = sheet.getRow(mIndex++).getCell(1);
                                    if (maskCell != null)
                                        mGatewayConfig.mask = maskCell.getStringCellValue().replaceAll("value:", "");
                                    Cell gatewayCell = sheet.getRow(mIndex++).getCell(1);
                                    if (gatewayCell != null)
                                        mGatewayConfig.gateway = gatewayCell.getStringCellValue().replaceAll("value:", "");
                                    Cell dnsCell = sheet.getRow(mIndex++).getCell(1);
                                    if (dnsCell != null)
                                        mGatewayConfig.dns = dnsCell.getStringCellValue().replaceAll("value:", "");
                                    Cell ntpServerCell = sheet.getRow(mIndex++).getCell(1);
                                    if (ntpServerCell != null)
                                        mGatewayConfig.ntpServer = ntpServerCell.getStringCellValue().replaceAll("value:", "");
                                    Cell timezoneCell = sheet.getRow(mIndex++).getCell(1);
                                    if (timezoneCell != null)
                                        mGatewayConfig.timezone = Integer.parseInt(timezoneCell.getStringCellValue().replaceAll("value:", ""));
                                    runOnUiThread(() -> {
                                        dismissLoadingProgressDialog();
                                        if (isFileError) {
                                            ToastUtils.showToast(ChooseModifySettingsActivity.this, "Import failed!");
                                            return;
                                        }
                                        ToastUtils.showToast(ChooseModifySettingsActivity.this, "Import success!");
                                        String fileUrl = mBind.etConfigFileUrl.getText().toString();
                                        SPUtiles.setStringValue(ChooseModifySettingsActivity.this, AppConstants.SP_KEY_MQTT_GATEWAY_SETTINGS_URL, fileUrl);
                                        Intent intent = new Intent(ChooseModifySettingsActivity.this, ModifySettingsActivity.class);
                                        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
                                        intent.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
                                        startLauncher.launch(intent);
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    isFileError = true;
                                }
                            }).start();
                        }
                    }

                    @Override
                    public void onError(Response<File> response) {
                        ToastUtils.showToast(ChooseModifySettingsActivity.this, "Download error");
                        dismissLoadingProgressDialog();
                    }

                    @Override
                    public void onFinish() {
                        dismissLoadingProgressDialog();
                    }
                });
    }

    @Override
    protected ActivityImportConfigFileBinding getViewBinding() {
        return ActivityImportConfigFileBinding.inflate(getLayoutInflater());
    }


    public void onBack(View view) {
        if (isWindowLocked()) return;
        finish();
    }

    private final ActivityResultLauncher<Intent> startLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            Intent intent = new Intent();
            setResult(RESULT_OK, intent);
            finish();
        }
    });

    @Subscribe(threadMode = ThreadMode.POSTING, priority = 25)
    public void onConnectStatusEvent(ConnectStatusEvent event) {
        String action = event.getAction();
        EventBus.getDefault().cancelEventDelivery(event);
        if (MokoConstants.ACTION_DISCONNECTED.equals(action)) {
            runOnUiThread(() -> {
                Intent intent = new Intent();
                setResult(RESULT_OK, intent);
                finish();
            });
        }
    }
}
