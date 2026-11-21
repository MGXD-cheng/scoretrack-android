# ===========================================================================
# ScoreTrack 混淆规则
#
# 原则：**宁可多 keep，也不要 release 包在用户手机上崩**。
# 混淆导致的崩溃有个很坑的特点 —— debug 包完全正常，只有 release 包炸，
# 而且堆栈是被混淆过的，很难定位。所以这里对「靠反射找」的东西一律保名。
#
# 靠反射的三处：
#   1. Retrofit 的接口方法（注解 + 泛型签名）
#   2. kotlinx.serialization 的 serializer（Companion 里找）
#   3. MPAndroidChart 的自定义 View（XML 里的类名）
# ===========================================================================

# --- 保留崩溃栈的可读性 -----------------------------------------------------
# 混淆后崩溃堆栈会变成 a.b.c，加上这两行才能看到真实行号。
# 代价是 APK 大几十 KB —— 对这个体量的应用完全值得。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization --------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# 保留我们自己所有 @Serializable 类的 serializer
-keep,includedescriptorclasses class com.lingxi.scoretrack.**$$serializer { *; }
-keepclassmembers class com.lingxi.scoretrack.** {
    *** Companion;
}
-keepclasseswithmembers class com.lingxi.scoretrack.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit ---------------------------------------------------------------
# Retrofit 靠泛型签名 + 注解找接口方法，这两组属性必须保留
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# 保留所有带 retrofit2.http 注解的接口方法
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# R8 full mode 下如果只看到接口没有实现类，会把返回值替换成 null。
# 这条让实现接口（其实是动态代理）不被裁掉。
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Retrofit 内部靠这个类判断 Kotlin 的 suspend 方法
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# --- OkHttp -----------------------------------------------------------------
# OkHttp / Okio 自带 consumer rules，这里只压掉用不到的平台的警告
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Room -------------------------------------------------------------------
# Room 生成的实现类是通过名字加载的，保持 RoomDatabase 的子类
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ----------------------------------------------------------
# Hilt 生成的代码不会被裁，但注解处理器依赖的几个类会报警告
-dontwarn dagger.hilt.**

# --- MPAndroidChart ---------------------------------------------------------
# 图表里的自定义 View 可能在 XML 被引用，类名不能变
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# --- 其他常用警告抑制 -------------------------------------------------------
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Tink（androidx.security.crypto 的底层实现）引用了 errorprone 的**编译期**注解，
# 这些注解在运行时不存在。R8 找不到就会中断构建，但它们本来就不需要 ——
# 这是开启混淆后第一次构建必踩的坑。
-dontwarn com.google.errorprone.annotations.**

# 枚举的 values()/valueOf() 被反射调用（serialization 会用到）
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}