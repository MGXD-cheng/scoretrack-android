import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ===========================================================================
// 签名配置
//
// 正式签名用的密钥和密码不放在仓库里（keystore.properties 已被 .gitignore 忽略）。
// 这个文件不存在时 —— 比如别人 clone 下来第一次构建、或者 CI 上没有密钥 ——
// release 会自动退回 debug 签名，so `assembleRelease` 永远能跑通，
// 不会因为缺密钥直接报错卡住整条流水线。
//
// 代价是 debug 签名的 release 包**装不了正式版**（签名不一致），
// 所以构建时会打印一行警告提醒。
// ===========================================================================
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystorePropertiesFile.exists()

android {
    namespace = "com.lingxi.scoretrack"
    compileSdk = 36

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v1（JAR 签名）对 minSdk 27 已经没用了 —— Android 7 起就认 v2+，
                // 而我们的 minSdk 是 27（Android 8.1）。关掉能省一点签名时间。
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.lingxi.scoretrack"
        minSdk = 27
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // 之前一直是 false —— 意味着 release 包和 debug 包一样大、一样能被反编译。
            // 开启混淆 + 资源压缩后 APK 大约能小一半，但必须配合 proguard-rules.pro
            // 里那几条 keep：Retrofit 的接口方法是靠反射找的，serialization 的
            // serializer 也是靠反射找的，不 keep 会在运行时炸（而且只有 release 包炸）。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 签名：有正式 keystore 就用它，没有就退回 debug。
            //
            // 之前一直是「无脑用 debug 签名」，理由是混淆的坑几乎全是运行时的
            //（反射找不到类），光靠 assembleRelease 成功完全验证不了；
            // 不签名的话产物是 app-release-unsigned.apk，装不进手机，等于没法验证。
            //
            // 现在有了正式 keystore，验证混淆依然能用正式包 —— 而且更接近真实环境。
            // 注意：**换签名后必须卸载重装**，因为 Android 不允许用不同签名覆盖安装。
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "[ScoreTrack] 没找到 keystore.properties，release 包将使用 debug 签名 —— " +
                        "这样的包不能发布，也不能覆盖安装正式版。"
                )
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room schema 导出，为后续 Migration 铺路
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// Force use of ARM64 binaries for AAPT2 in Proot environment
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
            useTarget("com.android.tools.build:aapt2:${'$'}{requested.version}:linux-aarch64")
        }
    }
}

dependencies {
    // --- 基础 ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // --- Lifecycle / Activity ---
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // --- Compose ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)

    // --- Navigation ---
    implementation(libs.androidx.navigation.compose)

    // --- Room ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Hilt ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // --- 网络 / 序列化 ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // --- 图片 ---
    implementation(libs.coil.compose)

    // --- 图表 ---
    implementation(libs.mpandroidchart)

    // --- 加密存储 ---
    implementation(libs.androidx.security.crypto)

    // --- 局域网同步 ---
    implementation(libs.nanohttpd)

    // --- 测试 ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}