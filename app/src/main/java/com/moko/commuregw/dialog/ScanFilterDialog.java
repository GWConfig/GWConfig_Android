package com.moko.commuregw.dialog;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.SeekBar;

import com.moko.commuregw.R;
import com.moko.commuregw.databinding.DialogScanFilterBinding;


public class ScanFilterDialog extends MokoBaseDialog<DialogScanFilterBinding> {
    public static final String TAG = ScanFilterDialog.class.getSimpleName();

    private int filterRssi;
    private String filterMac;

    @Override
    protected DialogScanFilterBinding getViewBind(LayoutInflater inflater, ViewGroup container) {
        return DialogScanFilterBinding.inflate(inflater, container, false);
    }

    @Override
    protected void onCreateView() {
        mBind.tvRssiFilterValue.setText(String.format("%sdBm", filterRssi + ""));
        mBind.tvRssiFilterTips.setText(getString(R.string.rssi_filter, filterRssi));
        mBind.sbRssiFilter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int rssi = progress - 127;
                mBind.tvRssiFilterValue.setText(String.format("%sdBm", rssi + ""));
                mBind.tvRssiFilterTips.setText(getString(R.string.rssi_filter, filterRssi));
                filterRssi = rssi;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        mBind.sbRssiFilter.setProgress(Math.abs(filterRssi));
        if (!TextUtils.isEmpty(filterMac)) {
            mBind.etFilterMac.setText(filterMac);
            mBind.etFilterMac.setSelection(filterMac.length());
        }
        mBind.ivFilterMacDelete.setOnClickListener(v -> mBind.etFilterMac.setText(""));
        mBind.tvDone.setOnClickListener(v -> {
            listener.onDone(mBind.etFilterMac.getText().toString(), filterRssi);
            dismiss();
        });
    }

    @Override
    public int getDialogStyle() {
        return R.style.CenterDialog;
    }

    @Override
    public int getGravity() {
        return Gravity.CENTER;
    }

    @Override
    public String getFragmentTag() {
        return TAG;
    }

    @Override
    public float getDimAmount() {
        return 0.7f;
    }

    @Override
    public boolean getCancelOutside() {
        return true;
    }

    @Override
    public boolean getCancellable() {
        return true;
    }

    private OnScanFilterListener listener;

    public void setOnScanFilterListener(OnScanFilterListener listener) {
        this.listener = listener;
    }

    public void setFilterMac(String filterMac) {
        this.filterMac = filterMac;
    }

    public void setFilterRssi(int filterRssi) {
        this.filterRssi = filterRssi;
    }

    public interface OnScanFilterListener {
        void onDone(String filterMac, int filterRssi);
    }
}
