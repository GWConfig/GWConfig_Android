package com.moko.commuregw.activity;

import android.content.Intent;
import android.text.InputFilter;
import android.view.View;

import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.commuregw.AppConstants;
import com.moko.commuregw.base.BaseActivity;
import com.moko.commuregw.databinding.ActivityNtpSettingsBinding;
import com.moko.commuregw.dialog.BottomDialog;
import com.moko.commuregw.entity.GatewayConfig;
import com.moko.commuregw.utils.ToastUtils;
import com.moko.support.commuregw.MokoSupport;
import com.moko.support.commuregw.OrderTaskAssembler;
import com.moko.support.commuregw.entity.OrderCHAR;
import com.moko.support.commuregw.entity.ParamsKeyEnum;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NtpSettingsActivity extends BaseActivity<ActivityNtpSettingsBinding> {

    private final String FILTER_ASCII = "[ -~]*";
    private InputFilter filter;

    private ArrayList<String> mTimeZones;
    private int mSelected;
    private boolean mSavedParamsError;

    private GatewayConfig mGatewayConfig;

    private boolean mIsRetainParams;

    @Override
    protected void onCreate() {
        mGatewayConfig = getIntent().getParcelableExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG);
        mIsRetainParams = mGatewayConfig != null;
        mTimeZones = new ArrayList<>();
        for (int i = -24; i <= 28; i++) {
            if (i < 0) {
                if (i % 2 == 0) {
                    int j = Math.abs(i / 2);
                    mTimeZones.add(String.format("UTC-%02d:00", j));
                } else {
                    int j = Math.abs((i + 1) / 2);
                    mTimeZones.add(String.format("UTC-%02d:30", j));
                }
            } else if (i == 0) {
                mTimeZones.add("UTC");
            } else {
                if (i % 2 == 0) {
                    mTimeZones.add(String.format("UTC+%02d:00", i / 2));
                } else {
                    mTimeZones.add(String.format("UTC+%02d:30", (i - 1) / 2));
                }
            }
        }
        filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        mBind.etNtpServer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        if (mIsRetainParams) {
            mSelected = mGatewayConfig.timezone + 24;
            mBind.tvTimezone.setText(mTimeZones.get(mSelected));
            String url = mGatewayConfig.ntpServer;
            mBind.etNtpServer.setText(url);
            return;
        }
        showLoadingProgressDialog();
        mBind.tvTitle.postDelayed(() -> {
            List<OrderTask> orderTasks = new ArrayList<>();
            orderTasks.add(OrderTaskAssembler.getNtpUrl());
            orderTasks.add(OrderTaskAssembler.getTimezone());
            MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
        }, 500);
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
                                    case KEY_NTP_URL:
                                        if (result != 1) {
                                            mSavedParamsError = true;
                                        }
                                        break;
                                    case KEY_NTP_TIME_ZONE:
                                        if (result != 1) {
                                            mSavedParamsError = true;
                                        }
                                        if (mSavedParamsError) {
                                            ToastUtils.showToast(this, "Setup failed！");
                                        } else {
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
                                    case KEY_NTP_TIME_ZONE:
                                        mSelected = value[4] + 24;
                                        mBind.tvTimezone.setText(mTimeZones.get(mSelected));
                                        break;
                                    case KEY_NTP_URL:
                                        String url = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                        mBind.etNtpServer.setText(url);
                                        break;

                                }
                            }
                        }
                    }
                    break;
            }
        }
    }


    @Override
    protected ActivityNtpSettingsBinding getViewBinding() {
        return ActivityNtpSettingsBinding.inflate(getLayoutInflater());
    }

    public void onSelectTimeZone(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mTimeZones, mSelected);
        dialog.setListener(value -> {
            mSelected = value;
            mBind.tvTimezone.setText(mTimeZones.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }


    public void onSave(View view) {
        if (isWindowLocked()) return;
        String ntpServer = mBind.etNtpServer.getText().toString();
        if (mIsRetainParams) {
            mGatewayConfig.ntpServer = ntpServer;
            mGatewayConfig.timezone = mSelected - 24;
            ToastUtils.showToast(this, "Setup succeed！");
            Intent intent = new Intent();
            intent.putExtra(AppConstants.EXTRA_KEY_GATEWAY_CONFIG, mGatewayConfig);
            setResult(RESULT_OK, intent);
            finish();
            return;
        }
        showLoadingProgressDialog();
        List<OrderTask> orderTasks = new ArrayList<>();
        orderTasks.add(OrderTaskAssembler.setNtpUrl(ntpServer));
        orderTasks.add(OrderTaskAssembler.setTimezone(mSelected - 24));
        MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
    }

    public void onBack(View view) {
        finish();
    }
}
