package com.moko.commuregw.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import com.moko.commuregw.R;
import com.moko.commuregw.databinding.DialogModifyEncryptionKeyBinding;
import com.moko.commuregw.databinding.DialogModifyLedReminderBinding;
import com.moko.commuregw.utils.ToastUtils;

public class EncryptionKeyDialog extends MokoBaseDialog<DialogModifyEncryptionKeyBinding> {
    public static final String TAG = EncryptionKeyDialog.class.getSimpleName();

    @Override
    protected DialogModifyEncryptionKeyBinding getViewBind(LayoutInflater inflater, ViewGroup container) {
        return DialogModifyEncryptionKeyBinding.inflate(inflater, container, false);
    }

    @Override
    protected void onCreateView() {
        mBind.tvCancel.setOnClickListener(v -> {
            dismiss();
        });
        mBind.tvEnsure.setOnClickListener(v -> {
            String encryptionKey = mBind.etEncryptionKey.getText().toString();
            if (TextUtils.isEmpty(encryptionKey) || encryptionKey.length() != 64) {
                ToastUtils.showToast(getContext(), "Para Error");
                return;
            }
            dismiss();
            if (dialogClickListener != null)
                dialogClickListener.onEnsureClicked(encryptionKey);
        });
        mBind.etEncryptionKey.postDelayed(() -> {
            //设置可获得焦点
            mBind.etEncryptionKey.setFocusable(true);
            mBind.etEncryptionKey.setFocusableInTouchMode(true);
            //请求获得焦点
            mBind.etEncryptionKey.requestFocus();
            //调用系统输入法
            InputMethodManager inputManager = (InputMethodManager) mBind.etEncryptionKey
                    .getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.showSoftInput(mBind.etEncryptionKey, 0);
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

        void onEnsureClicked(String encryptionKey);
    }
}
