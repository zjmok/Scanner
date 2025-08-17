import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun getLocalProperty(key: String): String {
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
        return localProperties.getProperty(key)
            ?: throw GradleException("属性 '$key' 未在 local.properties 中定义")
    } else {
        throw GradleException("local.properties 文件未找到")
    }
}

// environment variables or local properties
fun getEnvOrLocal(key: String): String {
//    return System.getenv(key) ?: project.findProperty(key)?.toString() ?: ""
    return System.getenv(key) ?: getLocalProperty(key)
}

android {
    namespace = "com.example.scanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.scanner"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("zjmok.jks")
            storePassword = getEnvOrLocal("KEYSTORE_PASSWORD")
            keyAlias = getEnvOrLocal("KEY_ALIAS")
            keyPassword = getEnvOrLocal("KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
//            signingConfig = signingConfigs.getByName("debug")
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
//            signingConfig = signingConfigs.getByName("debug")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    sourceSets {
        getByName("main") {
            res.srcDirs("src/main/res-language")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.blankj:utilcodex:1.31.1")

    implementation("com.guolindev.permissionx:permissionx:1.8.1")

    // https://github.com/journeyapps/zxing-android-embedded
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }
    // https://github.com/zxing/zxing/releases
    implementation("com.google.zxing:core:3.5.3")
//    implementation("com.google.zxing:core:3.4.1")
//    implementation("com.google.zxing:core:3.3.0")

}