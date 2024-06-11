package com.moko.commuregw.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.commuregw.R;
import com.moko.support.commuregw.entity.BleTag;

import androidx.core.content.ContextCompat;

public class BatchBeaconAdapter extends BaseQuickAdapter<BleTag, BaseViewHolder> {
    public BatchBeaconAdapter() {
        super(R.layout.item_beacon);
    }

    @Override
    protected void convert(BaseViewHolder helper, BleTag item) {
        helper.setText(R.id.tv_mac, item.mac);
        helper.setGone(R.id.tv_retry, item.status > 2);
        if (item.status == 0)
            helper.setText(R.id.tv_status, "Wait");
        else if (item.status == 1)
            helper.setText(R.id.tv_status, "Upgrading");
        else if (item.status == 2)
            helper.setText(R.id.tv_status, "Success");
        else if (item.status == 3)
            helper.setText(R.id.tv_status, "Failed");
        else if (item.status == 4)
            helper.setText(R.id.tv_status, "Timeout");
        if (item.status < 2)
            helper.setTextColor(R.id.tv_status, ContextCompat.getColor(mContext, R.color.black_333333));
        else if (item.status > 2)
            helper.setTextColor(R.id.tv_status, ContextCompat.getColor(mContext, R.color.red_ff0000));
        else
            helper.setTextColor(R.id.tv_status, ContextCompat.getColor(mContext, R.color.green_95f204));
        helper.addOnClickListener(R.id.iv_del);
        helper.addOnClickListener(R.id.tv_retry);
    }
}
