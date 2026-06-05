import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.englishcar.voicecoach"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.englishcar.voicecoach"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

    val localProps = Properties().apply {
        val rootLocal = rootProject.file("local.properties")
        val backendEnv = rootProject.file("../backend/.env.local")
        if (rootLocal.exists()) rootLocal.inputStream().use { load(it) }
        if (backendEnv.exists()) {
            backendEnv.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
                .forEach {
                    val index = it.indexOf("=")
                    if (!containsKey(it.substring(0, index).trim())) {
                        put(it.substring(0, index).trim(), it.substring(index + 1).trim())
                    }
                }
        }
    }

    defaultConfig {
        buildConfigField("String", "GEMINI_API_KEY", "\"${(localProps["GEMINI_API_KEY"] ?: "").toString()}\"")
        buildConfigField("String", "GEMINI_LIVE_MODEL", "\"${(localProps["GEMINI_LIVE_MODEL"] ?: "gemini-3.1-flash-live-preview").toString()}\"")
        buildConfigField("String", "GEMINI_LIVE_VOICE", "\"${(localProps["GEMINI_LIVE_VOICE"] ?: "Kore").toString()}\"")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)
    implementation(libs.okhttp)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
