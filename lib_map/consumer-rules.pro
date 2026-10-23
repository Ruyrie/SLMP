# lib_map 消费者规则（03 号文档 12.2 节）：高德地图、定位、搜索 SDK（官方说明）
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**
-dontwarn com.loc.**
# SDK 11 的精简定位模式不启用软件 RTK，此可选实现未随合包提供。
-dontwarn com.amap.ams.gnss.GnssSoftLocator
-keep class com.amap.api.maps.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.amap.api.trace.** { *; }
-keep class com.amap.api.location.** { *; }
# SDK 11 的原生定位库通过 JNI 按名称访问新的定位类及其成员。
-keep class com.amap.location.** { *; }
-keep class com.amap.api.fence.** { *; }
-keep class com.loc.** { *; }
-keep class com.amap.api.services.** { *; }

# 地图本地模型（PickLocationContract 经 Intent 传递）
-keep class com.zhimu.slmp.map.model.vo.** { *; }
