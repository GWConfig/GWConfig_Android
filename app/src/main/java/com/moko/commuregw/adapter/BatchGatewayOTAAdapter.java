package com.moko.commuregw.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.commuregw.R;
import com.moko.support.commuregw.entity.BatchGateway;

public class BatchGatewayOTAAdapter extends BaseQuickAdapter<BatchGateway, BaseViewHolder> {
    public BatchGatewayOTAAdapter() {
        super(R.layout.item_gateway);
    }

    @Override
    protected void convert(BaseViewHolder helper, BatchGateway item) {
        helper.setText(R.id.tv_mac, item.mac);
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
        helper.addOnClickListener(R.id.iv_del);
    }
}
