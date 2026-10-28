import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// `-ParmUniversal` builds one APK for every ARM phone (64- and 32-bit) instead of per-ABI splits.
val armUniversal = providers.gradleProperty("armUniversal").isPresent

// Release signing key stays outside the repository: ~/.belgekasa/signing.properties (or the file
// named by -PsigningProperties=...) with storeFile, storePassword, keyAlias, keyPassword.
// Without it, release builds are produced unsigned (forks and CI still build).
val signingFile = providers.gradleProperty("signingProperties").orNull?.let(::File)
    ?: File(System.getProperty("user.home"), ".belgekasa/signing.properties")
val signingProps = Properties().apply { if (signingFile.isFile) signingFile.inputStream().use(::load) }

android {
    namespace = "com.belgetarayici"
    compileSdk = 36

    defaultConfig {
        // Code package stays com.belgetarayici; the installed app is BelgeKasa.
        applicationId = "com.belgekasa"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (armUniversal) ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (signingProps.isNotEmpty()) create("release") {
            storeFile = File(signingFile.parentFile, signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            // Lets developers take screenshots of debug builds; release builds always block them.
            buildConfigField("boolean", "ALLOW_SCREENSHOTS", "true")
        }
        release {
            buildConfigField("boolean", "ALLOW_SCREENSHOTS", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // One APK per CPU architecture keeps downloads small (OpenCV/Tesseract are native).
    splits {
        abi {
            isEnable = !armUniversal
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
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

    androidResources { noCompress += "traineddata" }

    packaging {
        resources.excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        jniLibs.useLegacyPackaging = false
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.biometric)
    // biometric pulls fragment 1.2.x, which rejects the request codes of current activity-result APIs
    implementation(libs.androidx.fragment)
    implementation(libs.opencv)
    implementation(libs.tesseract)
    implementation(libs.pdfbox)
    implementation(libs.tink)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    implementation(libs.zxing)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
