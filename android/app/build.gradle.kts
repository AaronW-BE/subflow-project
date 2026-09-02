import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing is read from android/keystore.properties (never committed).
// See android/keystore.properties.template. Falls back to the debug key locally
// so that `assembleRelease` still works for smoke testing without the real key.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile")?.isNotBlank() == true &&
    rootProject.file(keystoreProperties.getProperty("storeFile")).exists()

android {
    namespace = "org.dpdns.alwaysup.subflow"
    // Play requires new releases to target Android 16. Play Billing 9 needs
    // compileSdk 35 or higher, so both are satisfied by compiling against 36.
    compileSdk = 36

    defaultConfig {
        applicationId = "org.dpdns.alwaysup.subflow"
        minSdk = 26
        targetSdk = 36
        // 1 was the first upload to the org.dpdns.alwaysup.subflow entry and is
        // the bundle currently in review; 2 supersedes it with Play Billing 9,
        // targetSdk 36, ads 25.4.0 and the corrected AdMob banner unit. (The
        // 1..4 spent while this app was called com.subflow.app belong to that
        // other Console entry and never constrained this one - Play only
        // requires versionCode to increase within a single app.)
        //
        // versionName stays 1.0.0: nothing has been released publicly, so this
        // is still the 1.0.0 launch, just a corrected build of it.
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Ship only the languages the in-app picker offers; drops ~80 unused
        // AndroidX translations from the bundle.
        resourceConfigurations += listOf("en", "de", "fr", "es", "ja", "zh")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }

            // Production endpoints & live monetization identifiers.
            //
            // There is deliberately no public default for the sync API. The old
            // fallback pointed at api.subflow.app, a subdomain of a domain we do
            // not own - if its owner ever created that host, the app would have
            // begun posting install ids to a stranger. With no property set,
            // BACKEND_ENABLED is false and no backend call is attempted at all,
            // which is also what the published privacy policy promises.
            val apiBase = (properties["subflow.apiBaseUrl"] ?: "").toString()
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${apiBase.ifBlank { "https://localhost/api/v1/" }}\""
            )
            buildConfigField("boolean", "BACKEND_ENABLED", "${apiBase.isNotBlank()}")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"${properties["subflow.admobBannerUnitId"] ?: "ca-app-pub-3940256099942544/6300978111"}\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${properties["subflow.googleWebClientId"] ?: ""}\"")
            buildConfigField("boolean", "NETWORK_LOGGING", "false")
            manifestPlaceholders["admobAppId"] =
                (properties["subflow.admobAppId"] ?: "ca-app-pub-3940256099942544~3347511713").toString()

            // Declared so shared code compiles, hard-coded empty so the debug
            // device hash in ~/.gradle/gradle.properties can never reach a
            // shipped bundle. The only reader is guarded by BuildConfig.DEBUG
            // anyway, but the symbol still has to exist in both build types.
            buildConfigField("String", "UMP_TEST_DEVICE_HASH", "\"\"")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false

            // 10.0.2.2 is the host loopback as seen from the Android emulator.
            // A physical device cannot resolve it, so debugging against the
            // backend on real hardware needs an override:
            //
            //   adb reverse tcp:8085 tcp:8085
            //   ./gradlew :app:assembleDebug             //       -Psubflow.debugApiBaseUrl=http://127.0.0.1:8085/api/v1/
            //
            // 127.0.0.1 is already allowed cleartext by network_security_config.
            val debugApiBase = (properties["subflow.debugApiBaseUrl"]
                ?: "http://10.0.2.2:8085/api/v1/").toString()
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBase\"")
            buildConfigField("boolean", "BACKEND_ENABLED", "true")
            // Google's official AdMob test IDs - never serve live ads in debug.
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${properties["subflow.googleWebClientId"] ?: ""}\"")
            buildConfigField("boolean", "NETWORK_LOGGING", "true")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"

            // UMP ignores setDebugGeography unless the device is also registered
            // as a debug device, so without this the EEA form never appears.
            // The hash is device-specific; the SDK prints it on first run:
            //   I UserMessagingPlatform: Use ... addTestDeviceHashedId("<hash>")
            // Put it in ~/.gradle/gradle.properties as subflow.umpTestDeviceHash.
            // Debug only, deliberately: it must never reach a shipped bundle.
            buildConfigField(
                "String",
                "UMP_TEST_DEVICE_HASH",
                "\"${properties["subflow.umpTestDeviceHash"] ?: ""}\""
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "DebugProbesKt.bin"
            )
        }
    }
    bundle {
        language {
            // Keep every translation in the base APK so the in-app language
            // picker works without an on-demand split download.
            enableSplit = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Already on the classpath transitively via the ads SDK; declared
    // explicitly so openUrl() does not depend on that staying true.
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)

    // Identity & Play Monetization
    implementation(libs.play.billing)
    implementation(libs.play.services.ads)
    implementation(libs.play.services.ump)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // WorkManager & Coil
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
