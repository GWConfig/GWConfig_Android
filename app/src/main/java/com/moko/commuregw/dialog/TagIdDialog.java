package com.moko.commuregw.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import com.moko.commuregw.R;
import com.moko.commuregw.databinding.DialogModifyTagIdBinding;
import com.moko.commuregw.utils.ToastUtils;

public class TagIdDialog extends MokoBaseDialog<DialogModifyTagIdBinding> {
    public static final String TAG = TagIdDialog.class.getSimpleName();

    @Override
    protected DialogModifyTagIdBinding getViewBind(LayoutInflater inflater, ViewGroup container) {
        return DialogModifyTagIdBinding.inflate(inflater, container, false);
    }

    @Override
    protected void onCreateView() {
        mBind.tvCancel.setOnClickListener(v -> {
            dismiss();
        });
        mBind.tvEnsure.setOnClickListener(v -> {
            String tagId = mBind.etTagId.getText().toString();
            if (TextUtils.isEmpty(tagId) || tagId.length() != 6) {
                ToastUtils.showToast(getContext(),"Para Error");
                return;
            }
            dismiss();
            if (tagIdClickListener != null)
                tagIdClickListener.onEnsureClicked(tagId);
        });
        mBind.etTagId.postDelayed(() -> {
            //设置可获得焦点
            mBind.etTagId.setFocusable(true);
            mBind.etTagId.setFocusableInTouchMode(true);
            //请求获得焦点
            mBind.etTagId.requestFocus();
            //调用系统输入法
            InputMethodManager inputManager = (InputMethodManager) mBind.etTagId
                    .getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.showSoftInput(mBind.etTagId, 0);
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

    private TagIdClickListener tagIdClickListener;

    public void setOnTagIdClicked(TagIdClickListener tagIdClickListener) {
        this.tagIdClickListener = tagIdClickListener;
    }

    public interface TagIdClickListener {

        void onEnsureClicked(String tagId);
    }
}
