package com.zhimu.slmp.finance.ui.common;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.R;

/**
 * 收支分类图标（E04、E05）：服务端 icon 为 Material Symbols 名称（inventory_2 饲料采购、vaccines 兽药疫苗、bolt 水电、
 * engineering 人工、add_box 引种、build 设备维修、category 其他；收入 payments 出栏销售、storefront 副产品、
 * account_balance 补贴）。
 */
public final class FinanceIcons {

    private FinanceIcons() {
    }

    @DrawableRes
    public static int of(@Nullable String icon) {
        if (icon == null) {
            return R.drawable.ic_category;
        }
        switch (icon) {
            case "inventory_2":
                return R.drawable.ic_inventory_2;
            case "vaccines":
                return R.drawable.ic_vaccines;
            case "payments":
                return R.drawable.ic_payments;
            case "add_box":
                return R.drawable.ic_add_box;
            case "engineering":
                return com.zhimu.slmp.finance.R.drawable.finance_ic_engineering;
            case "bolt":
                return com.zhimu.slmp.finance.R.drawable.finance_ic_bolt;
            case "build":
                return com.zhimu.slmp.finance.R.drawable.finance_ic_build;
            case "account_balance":
                return com.zhimu.slmp.finance.R.drawable.finance_ic_account_balance;
            case "storefront":
                return com.zhimu.slmp.finance.R.drawable.finance_ic_storefront;
            case "local_shipping":
                return R.drawable.ic_local_shipping;
            default:
                return R.drawable.ic_category;
        }
    }
}
