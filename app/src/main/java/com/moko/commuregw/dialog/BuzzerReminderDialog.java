package com.moko.commuregw.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import com.moko.commuregw.AppConstants;
import com.moko.commuregw.R;
import com.moko.commuregw.databinding.DialogModifyBuzzerReminderBinding;
import com.moko.commuregw.utils.SPUtiles;
import com.moko.commuregw.utils.ToastUtils;

public class BuzzerReminderDialog extends MokoBaseDialog<DialogModifyBuzzerReminderBinding> {
    public static final String TAG = BuzzerReminderDialog.class.getSimpleName();

    @Override
    protected DialogModifyBuzzerReminderBinding getViewBind(LayoutInflater inflater, ViewGroup container) {
        return DialogModifyBuzzerReminderBinding.inflate(inflater, container, false);
    }

    @Override
    protected void onCreateView() {
        String buzzerInterval = SPUtiles.getStringValue(getContext(), AppConstants.SP_KEY_BUZZER_INTERVAL, "");
        String buzzerDuration = SPUtiles.getStringValue(getContext(), AppConstants.SP_KEY_BUZZER_DURATION, "");
        mBind.etInterval.setText(buzzerInterval);
        mBind.etDuration.setText(buzzerDuration);
        mBind.tvCancel.setOnClickListener(v -> {
            dismiss();
        });
        mBind.tvEnsure.setOnClickListener(v -> {
            String intervalStr = mBind.etInterval.getText().toString();
            String durationStr = mBind.etDuration.getText().toString();
            if (TextUtils.isEmpty(intervalStr)
                    || TextUtils.isEmpty(durationStr)) {
                ToastUtils.showToast(getContext(), "Para Error");
                return;
            }
            int interval = Integer.parseInt(intervalStr);
            if (interval > 100) {
                ToastUtils.showToast(getContext(), "Para Error");
                return;
            }
            int duration = Integer.parseInt(durationStr);
            if (duration < 1 || duration > 255) {
                ToastUtils.showToast(getContext(), "Para Error");
                return;
            }
            dismiss();
            SPUtiles.setStringValue(getContext(), AppConstants.SP_KEY_BUZZER_INTERVAL, String.valueOf(interval));
            SPUtiles.setStringValue(getContext(), AppConstants.SP_KEY_BUZZER_DURATION, String.valueOf(duration));
            if (dialogClickListener != null)
                dialogClickListener.onEnsureClicked(interval, duration);
        });
        mBind.etInterval.postDelayed(() -> {
            //设置可获得焦点
            mBind.etInterval.setFocusable(true);
            mBind.etInterval.setFocusableInTouchMode(true);
            //请求获得焦点
            mBind.etInterval.requestFocus();
            //调用系统输入法
            InputMethodManager inputManager = (InputMethodManager) mBind.etInterval
                    .getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.showSoftInput(mBind.etInterval, 0);
        }, 200);
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
        return false;
    }

    @Override
    public boolean getCancellable() {
        return true;
    }

    private DialogClickListener dialogClickListener;

    public void setOnDialogClicked(DialogClickListener dialogClickListener) {
        this.dialogClickListener = dialogClickListener;
    }

    public interface DialogClickListener {

        void onEnsureClicked(int interval, int duration);
    }
}
