package com.zhimu.server.biz.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.finance.entity.SaleReceipt;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** SaleReceipt 单表访问。 */
public interface SaleReceiptMapper extends BaseMapper<SaleReceipt> {

    /** 出栏单的有效收款（修改申请作废时同步作废收入） */
    @Select("SELECT * FROM t_sale_receipt WHERE sale_order_id = #{saleOrderId} AND status = 'NORMAL' "
            + "ORDER BY id")
    List<SaleReceipt> selectNormalByOrderId(@Param("saleOrderId") Long saleOrderId);
}
