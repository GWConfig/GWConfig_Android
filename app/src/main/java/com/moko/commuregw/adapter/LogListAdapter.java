package com.moko.commuregw.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.commuregw.R;

public class LogListAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    public LogListAdapter() {
        super(R.layout.item_button_log);
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tv_log, item);
    }
}
