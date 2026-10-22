# 壳工程混淆规则（03 号文档 12.2 节）。各组件自己的保留规则在其 consumer-rules.pro 中，随依赖自动生效。

# ===== 崩溃定位：保留行号，源文件名统一替换 =====
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== 极光推送（官方说明；推送代码在 module_today，规则按 12.2 节放在壳工程） =====
-dontwarn cn.jpush.**
-keep class cn.jpush.** { *; }
-dontwarn cn.jiguang.**
-keep class cn.jiguang.** { *; }
-keep class * extends cn.jpush.android.service.JPushMessageReceiver { *; }
-keep class * extends cn.jpush.android.service.JCommonService { *; }
