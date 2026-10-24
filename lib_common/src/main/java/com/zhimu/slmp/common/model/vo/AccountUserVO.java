package com.zhimu.slmp.common.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 当前用户（模块01 2.3.2 节），保存在 zmt_session 的 user_json。
 */
public class AccountUserVO {

    public AccountUserVO() {
    }

    public AccountUserVO(long userId, @Nullable String name, @Nullable String nameInitial, @Nullable String phoneMasked) {
        this.userId = userId;
        this.name = name;
        this.nameInitial = nameInitial;
        this.phoneMasked = phoneMasked;
    }

    /** 修改姓名后得到新对象（保留头像） */
    @NonNull
    public AccountUserVO withName(@Nullable String newName, @Nullable String newInitial) {
        AccountUserVO user = new AccountUserVO(userId, newName, newInitial, phoneMasked);
        user.avatarUrl = avatarUrl;
        return user;
    }

    /** 修改头像后得到新对象（保留姓名、手机号） */
    @NonNull
    public AccountUserVO withAvatarUrl(@Nullable String newAvatarUrl) {
        AccountUserVO user = new AccountUserVO(userId, name, nameInitial, phoneMasked);
        user.avatarUrl = newAvatarUrl;
        return user;
    }

    private long userId;
    @Nullable
    private String name;
    /** 头像中的姓氏 */
    @Nullable
    private String nameInitial;
    @Nullable
    private String phoneMasked;
    /** 真实头像地址（账号接口字段 avatar_url）；为空时客户端回退显示姓名首字 */
    @Nullable
    private String avatarUrl;

    public long getUserId() {
        return userId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getNameInitial() {
        return nameInitial;
    }

    @Nullable
    public String getPhoneMasked() {
        return phoneMasked;
    }

    @Nullable
    public String getAvatarUrl() {
        return avatarUrl;
    }
}
