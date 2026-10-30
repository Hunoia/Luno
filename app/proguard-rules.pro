-keep class hunoia.luno.shizuku.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }



# Keep serialization entry points while allowing model obfuscation/shrinking.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class hunoia.luno.config.model.** { *; }

# 有了verbose这句话，混淆后就会生成映射文件
# 包含有类名->混淆后类名的映射关系
# 然后使用printmapping指定映射文件的名称
-verbose
-printmapping mapping.txt

# houbb pinyin：heaven 的 InstanceFactory 走反射/ServiceLoader 拿单例，
# 混淆会断 dict 加载，整体保留（3MB 级 jar，R8 仍会做未引用方法裁剪之外的压缩）。
-keep class com.github.houbb.pinyin.** { *; }
-keep class com.github.houbb.heaven.** { *; }
-keep class com.github.houbb.nlp.common.** { *; }
# heaven 引用了桌面 JDK 专属类（beans/management/sun.misc），拼音路径走不到，
# 仅消 R8 缺类报错；真机用 yinyue 搜索做运行时验证。
-dontwarn java.beans.**
-dontwarn java.lang.management.**
-dontwarn sun.misc.**
-dontwarn sun.reflect.**

# 抛出异常时保留代码行号，源文件名不保留以减少元数据体积
-keepattributes LineNumberTable

# release 移除 Log.v/d/i（含参数拼接）：多数日志已有 BuildConfig.DEBUG 守卫
# （R8 常量折叠即消），这里兜底无守卫的漏网调用；w/e 保留。
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ML Kit（unbundled 文本识别）：release 下 R8 会优化组件注册链，
# 实测崩在 InputImage.fromBitmap → MlKitContext.get(SharedPrefManager) 返回 null 的 NPE。
# 官方 consumer 规则只保 ComponentRegistrar 类名，不足以保住注册用的静态字段/方法体。
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled_common.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**

# Room：room-runtime:2.5.0 的 consumer 规则只保留 RoomDatabase 子类本身，未保留无参构造。
# R8 full mode 会把 WorkDatabase_Impl 的 <init>() 裁掉（LiteRT → play:asset-delivery →
# work-runtime 传递引入），导致 WorkManager/AssetDelivery 初始化时反射实例化失败、进程启动即崩。
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# PP-OCRv5 NPU：Qualcomm QNN runtime 与 LiteRT NPU dispatch 走 native/JNI 注册，
# 不能由 R8 裁剪入口类。
-keep class com.qualcomm.qti.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn com.qualcomm.qti.**
-dontwarn com.google.ai.edge.litert.**

