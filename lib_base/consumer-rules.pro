# lib_base 消费者规则（03 号文档 12.2 节）

# Glide：自定义 AppGlideModule 与生成的 GeneratedAppGlideModuleImpl 通过反射加载
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
