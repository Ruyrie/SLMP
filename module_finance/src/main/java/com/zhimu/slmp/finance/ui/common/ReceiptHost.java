package com.zhimu.slmp.finance.ui.common;

import androidx.annotation.NonNull;

import com.zhimu.slmp.finance.util.ReceiptFormDelegate;

/**
 * 登记收款底部面板的宿主（E07、E09 的 ViewModel）：面板只读写表单并回调确认，提交由宿主执行。
 */
public interface ReceiptHost {

    @NonNull
    ReceiptFormDelegate getReceipt();

    void confirmReceipt();
}
