plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.iknalos.warpgo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iknalos.warpgo.tv"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.3-tv"

        // Pre-registered WARP account injected from CI secrets (empty for local
        // builds, in which case the app registers its own account at runtime).
        buildConfigField("String", "WARP_PRIVATE_KEY", "\"${System.getenv("WARP_PRIVATE_KEY") ?: ""}\"")
        buildConfigField("String", "WARP_ADDRESS_V6", "\"${System.getenv("WARP_ADDRESS_V6") ?: ""}\"")
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
        viewBinding = true
        buildConfig = true
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
