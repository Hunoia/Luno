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

# LunoLog 是自建包装器，R8 不会把它当成 android.util.Log 处理，必须单独声明；
# 否则 release 里 LunoLog.i 仍会执行字符串拼接、logcat 输出与落盘。
# 只裁 v/d/i：w/e/wtf 是线上诊断底线，且 e/wtf 还负责写故障记录。
-assumenosideeffects class hunoia.luno.core.LunoLog {
    public *** v(...);
    public *** d(...);
    public *** i(...);
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


# Compose Styles API（foundation 1.12 的 Modifier.style / Style.applyStyle）：
# R8 full mode 会把 androidx.compose.foundation.style 里的类做类合并，material3 1.4.0
# 的 SAM lambda 到运行时已不再实现 Style，release 首帧就在 ResolvedStyle.build 抛
# ClassCastException（debug 不裁剪所以正常）。整包保留并保住实现类。
-keep class androidx.compose.foundation.style.** { *; }
-keep class * implements androidx.compose.foundation.style.Style { *; }
-keep class * implements androidx.compose.foundation.style.CustomStyleScope { *; }

# WorkManager：Glance 部件更新经 WorkManager 反射实例化 InputMerger（OverwritingInputMerger）
# 与 ListenableWorker 子类；R8 会裁掉无参构造导致部件更新失败、桌面部件空白
# （debug 不裁剪所以正常）。work-runtime 官方 consumer 规则不足以覆盖
# LiteRT→play:asset-delivery→work-runtime 传递引入的场景，显式补 keep。
-keep class androidx.work.OverwritingInputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.impl.** { *; }

# Glance ActionCallback：部件点击回调（TodoToggleCallback / HabitCheckCallback）经反射
# 实例化，需保公开无参构造；R8 会裁掉导致 NoSuchMethodException、部件点击无响应。
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Glance 部件 provider 类：Glance 用 `Class.canonicalName` 建立「provider 类 → receiver」
# 映射（并持久化到 DataStore）。R8 full mode 会把 HabitWidget 与 TodoWidget 做垂直类合并：
# release mapping 里两个类的 `provideGlance` 被折进同一个混淆类（`h12`），两个 Receiver
# 拿到的其实是同一个类实例——实测表现为习惯部件自己变成待办、待办部件显示习惯内容。
# 必须整类保留（禁止合并/改名），只保构造或只保 ActionCallback 都不够。
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }

# Receiver 由清单声明、框架回调，类名也参与上述映射；保住类名与入口方法。
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
