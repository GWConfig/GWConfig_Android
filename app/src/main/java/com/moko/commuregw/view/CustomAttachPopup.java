package com.moko.commuregw.view;

import android.content.Context;

import com.lxj.xpopup.core.AttachPopupView;
import com.moko.commuregw.R;

import androidx.annotation.NonNull;


public class CustomAttachPopup extends AttachPopupView {

    public CustomAttachPopup(@NonNull Context context) {
        super(context);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.popup_more;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        findViewById(R.id.tvAbout).setOnClickListener(v -> {
            if (onPopupClickListener != null) {
                onPopupClickListener.onAboutClick();
                dismiss();
            }
        });
        findViewById(R.id.tvServer).setOnClickListener(v -> {
            if (onPopupClickListener != null) {
                onPopupClickListener.onServerClick();
                dismiss();
            }
        });
        findViewById(R.id.tvBatchOTA).setOnClickListener(v -> {
            if (onPopupClickListener != null) {
                onPopupClickListener.onBatchOTAClick();
                dismiss();
            }
        });
        findViewById(R.id.tvBatchModify).setOnClickListener(v -> {
            if (onPopupClickListener != null) {
                onPopupClickListener.onBatchModifyClick();
                dismiss();
            }
        });
    }

    private OnPopupClickListener onPopupClickListener;

    public void setOnPopupClickListener(OnPopupClickListener onPopupClickListener) {
        this.onPopupClickListener = onPopupClickListener;
    }

    public interface OnPopupClickListener {
        void onAboutClick();

        void onServerClick();

        void onBatchOTAClick();

        void onBatchModifyClick();
    }
}
