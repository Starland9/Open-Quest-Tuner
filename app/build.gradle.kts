import java.util.Properties
import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.openquesttuner"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "io.github.openquesttuner"
        minSdk = 29
        targetSdk = 34
        versionCode = 35
        versionName = "0.19.2-NativeAutoTune-ForegroundFix"

        // Quest = arm64-v8a ; x86_64 pour l'émulateur. Limite les binaires natifs de spake2-android.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Optional local development key. Otherwise use Android's default debug key.
    val developmentKey = rootProject.file("signing/oqt-debug.keystore")
    if (developmentKey.isFile) {
        signingConfigs.getByName("debug") {
            storeFile = developmentKey
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Optimisée par R8 : APK plus léger et interface Compose plus fluide dans le casque.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signée avec la clé de debug locale, en attendant une clé de publication : elle s'installe
            // par-dessus la version debug sans perdre la clé ADB autorisée ni les profils. Ne pas
            // distribuer cet APK.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            // bcprov et bcpkix embarquent tous deux ce manifeste OSGi.
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.khronos.openxr:openxr_loader_for_android:1.1.49")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ADB embarqué : TCP classique, appairage TLS du débogage sans fil, découverte mDNS (research.md R3).
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    // Requis pour exportKeyingMaterial pendant l'appairage TLS 1.3.
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    // Certificat X.509 auto-signé de l'identité ADB de l'appli.
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// Build the independent Quest arm64 HUD using the pinned Android NDK, on all desktop hosts.
val hudJni = layout.buildDirectory.dir("generated/hudJni")
android.sourceSets.getByName("main").jniLibs.srcDir(hudJni)
val compileImmersiveHud = tasks.register<Exec>("compileImmersiveHud") {
    inputs.dir("src/main/cpp")
    outputs.file(hudJni.map { it.file("arm64-v8a/liboqt_hud.so") })
    doFirst {
        val sdkPath = Properties().apply { rootProject.file("local.properties").let { if(it.exists()) it.inputStream().use { stream -> load(stream) } } }.getProperty("sdk.dir")
            ?: System.getenv("ANDROID_HOME") ?: error("Android SDK location required")
        val host = System.getProperty("os.name").lowercase()
        val tag = if (host.contains("win")) "windows-x86_64" else if (host.contains("mac")) "darwin-x86_64" else "linux-x86_64"
        val compiler = File(sdkPath,"ndk/27.2.12479018/toolchains/llvm/prebuilt/$tag/bin/aarch64-linux-android29-clang++" + if(host.contains("win")) ".cmd" else "")
        val output = hudJni.get().file("arm64-v8a/liboqt_hud.so").asFile.apply { parentFile.mkdirs() }
        val nativeArgs=listOf(compiler.absolutePath,"-std=c++17","-O2","-fPIC","-shared","-static-libstdc++","-Wl,-z,max-page-size=16384",file("src/main/cpp/immersive_hud.cpp").absolutePath,"-o",output.absolutePath,"-lEGL","-lGLESv3","-landroid","-llog","-ldl")
        commandLine(if(host.contains("win")) listOf("cmd","/c")+nativeArgs else nativeArgs)
    }
}
tasks.named("preBuild").configure { dependsOn(compileImmersiveHud) }
