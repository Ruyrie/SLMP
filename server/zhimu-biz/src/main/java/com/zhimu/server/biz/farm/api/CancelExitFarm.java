package com.zhimu.server.biz.farm.api;

/** 注销时将退出的养殖场（本人为场长或员工），对应 CancelExitFarmVO */
public record CancelExitFarm(Long farmId, String name, String role, String roleText) {
}