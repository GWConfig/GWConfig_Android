package com.moko.commuregw.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.commuregw.R;
import com.moko.support.commuregw.entity.BatchDFUBeacon;

public class BatchBeaconAdapter extends BaseQuickAdapter<BatchDFUBeacon.BleDevice, BaseViewHolder> {
    public BatchBeaconAdapter() {
        super(R.layout.item_beacon);
    }

    @Override
    protected void convert(BaseViewHolder helper, BatchDFUBeacon.BleDevice item) {
        helper.setText(R.id.tv_mac, item.mac);
        helper.setText(R.id.tv_pwd, item.passwd);
    }
}
