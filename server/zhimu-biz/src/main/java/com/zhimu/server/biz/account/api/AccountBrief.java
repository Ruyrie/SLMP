package com.zhimu.server.biz.account.api;

import com.zhimu.server.common.util.Masks;

/**
 * 账号简要信息（供其他模块使用）。已注销账号 cancelled 为 true，name 为“已注销用户”。
 *
 * @param status 账号状态编码（由模块01 定义，例如 NORMAL、DISABLED、CANCELLED）
 */
public record AccountBrief(Long userId, String name, String phone, String status, boolean cancelled) {

    public String phoneMasked() {
        return Masks.phone(phone);
    }

    public String nameInitial() {
        return Masks.initial(name);
    }
}