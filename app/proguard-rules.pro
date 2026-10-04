# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

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
