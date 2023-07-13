package com.moko.commuregw.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.commuregw.R;
import com.moko.support.commuregw.entity.BleTag;

public class BatchBeaconAdapter extends BaseQuickAdapter<BleTag, BaseViewHolder> {
    public BatchBeaconAdapter() {
        super(R.layout.item_beacon);
    }

    @Override
    protected void convert(BaseViewHolder helper, BleTag item) {
        helper.setText(R.id.tv_mac, item.mac);
        helper.setText(R.id.tv_pwd, item.passwd);
        if (item.status == 0)
            helper.setText(R.id.tv_status, "Wait");
        else if (item.status == 1)
            helper.setText(R.id.tv_status, "Upgrading");
        else if (item.status == 2)
            helper.setText(R.id.tv_status, "Success");
        else if (item.status == 3)
            helper.setText(R.id.tv_status, "Failed");
    }
}
