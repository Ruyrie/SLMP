package com.zhimu.slmp.finance.util;

import androidx.core.content.FileProvider;

/**
 * 经营报表打开与分享用的 FileProvider（authorities 为 ${applicationId}.finance.fileprovider，只暴露 Download/reports/）。
 * 单独子类避免与其他组件的 androidx FileProvider 声明在清单合并时冲突。
 */
public class FinanceFileProvider extends FileProvider {
}
