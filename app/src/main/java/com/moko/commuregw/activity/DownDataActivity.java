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
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.BaseApplication;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityDownDataBinding;
import com.moko.commuregw.entity.GatewayConfig;
import com.moko.commuregw.utils.ToastUtils;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;

public class DownDataActivity extends BaseActivity<ActivityDownDataBinding> {
    private GatewayConfig mGatewayConfig;
    private boolean isFileError;
    private int mIndex = 1;

    @Override
    protected void onCreate() {
        mGatewayConfig = new GatewayConfig();
        mBind.tvImportConfig.setOnClickListener(v -> {
            String fileUrl = mBind.etConfigFileUrl.getText().toString();
            if (TextUtils.isEmpty(fileUrl)) {
                ToastUtils.showToast(this, "URL error");
                return;
            }
            downloadNewConfigFile(fileUrl);
        });
        mBind.tvNext.setOnClickListener(v -> {
            Intent intent = new Intent(this, BatchConfigGatewayActivity.class);
            intent.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
            startActivity(intent);
        });
    }

    private void downloadNewConfigFile(String apkUrl) {
        String fileName = "Settings_for_Device.xlsx";
        String filePath = BaseApplication.PATH_LOGCAT + File.separator;
        File file = new File(filePath);
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
                        XLog.i(("Progress:" + (int) (progress.fraction * 100)));
                    }


                    @Override
                    public void onSuccess(Response<File> response) {
                        if (response.isSuccessful()) {
                            mBind.tvNext.setVisibility(View.VISIBLE);
                            File apkFile = response.body();
                            mIndex = 1;
                            new Thread(() -> {
                                try {
                                    Workbook workbook = WorkbookFactory.create(apkFile);
                                    Sheet sheet = workbook.getSheetAt(0);
                                    int rows = sheet.getPhysicalNumberOfRows();
                                    int columns = sheet.getRow(0).getPhysicalNumberOfCells();
                                    // 从第二行开始
                                    if (rows < 33 || columns < 3) {
                                        runOnUiThread(() -> {
                                            dismissLoadingProgressDialog();
                                            ToastUtils.showToast(DownDataActivity.this, "Please select the correct file!");
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
                                            ToastUtils.showToast(DownDataActivity.this, "Import failed!");
                                            return;
                                        }
                                        ToastUtils.showToast(DownDataActivity.this, "Import success!");
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
                        ToastUtils.showToast(DownDataActivity.this, "Download error");
                    }

                    @Override
                    public void onFinish() {
                    }
                });
    }

    @Override
    protected ActivityDownDataBinding getViewBinding() {
        return ActivityDownDataBinding.inflate(getLayoutInflater());
    }


    public void onBack(View view) {
        finish();
    }

}
