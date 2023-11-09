package com.moko.commuregw.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.commuregw.R;
import com.moko.support.commuregw.entity.ConfiguredGateway;

public class AddConfiguredGatewayAdapter extends BaseQuickAdapter<ConfiguredGateway, BaseViewHolder> {

    public AddConfiguredGatewayAdapter() {
        super(R.layout.item_gateway);
    }

    @Override
    protected void convert(BaseViewHolder helper, ConfiguredGateway item) {
        helper.setText(R.id.tv_mac, item.mac);
        helper.setGone(R.id.tv_retry, item.status > 2);
        if (item.status == 0)
            helper.setText(R.id.tv_status, "Wait");
        else if (item.status == 1)
            helper.setText(R.id.tv_status, "Added");
        else if (item.status == 2)
            helper.setText(R.id.tv_status, "Timeout");
    }
}
