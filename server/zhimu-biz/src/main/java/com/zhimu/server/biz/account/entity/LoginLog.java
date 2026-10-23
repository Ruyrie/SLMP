package com.zhimu.server.biz.account.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 登录日志（t_login_log）：成功与失败都写，只追加。 */
@Getter
@Setter
@TableName("t_login_log")
public class LoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String phone;
    private String loginType;
    private String clientType;
    private String deviceId;
    private String deviceModel;
    private String osVersion;
    private String appVersion;
    private String userAgent;
    private String ip;
    private String ipRegion;
    private String result;
    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
