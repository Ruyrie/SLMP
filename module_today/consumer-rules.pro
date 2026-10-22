# module_today 消费者规则（技术文档 2.7.5）
# model.vo（含 NoticePayload、TransferPayload、PushExtras）与 model.request 的字段已由 lib_common 的规则按包名保留。

# 极光：SDK 类与本组件的消息接收器
-dontwarn cn.jpush.**
-dontwarn cn.jiguang.**
-keep class cn.jpush.** { *; }
-keep class cn.jiguang.** { *; }
-keep class com.zhimu.slmp.today.push.ZhimuPushReceiver { *; }
