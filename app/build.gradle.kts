plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "gd.app.musicplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "gd.app.musicplayer"
        // COUI AppCompat AAR requires API 28+.
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("platform") {
            // Keystore lives at repo root (not app/).
            storeFile = rootProject.file("platform.keystore")
            storePassword = "123456"
            keyAlias = "platform"
            keyPassword = "123456"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("platform")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("platform")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        viewBinding = true
    }
}

// Media3 1.5+ AARs declare minCompileSdk 35; product is AOSP 14 (API 34).
// Runtime target stays 34; disable AAR metadata gate so we can compile against SDK 34.
tasks.configureEach {
    if (name.contains("AarMetadata", ignoreCase = true)) {
        enabled = false
    }
}

// Offline Maven mirror often lacks Gradle .module metadata for KMP artifacts.
// Without it, both -android and -jvm variants can land on the classpath.
// collection 1.4+ ships former collection-ktx APIs inside collection-jvm — exclude
// the legacy artifact or checkDebugDuplicateClasses fails.
configurations.configureEach {
    exclude(group = "androidx.datastore", module = "datastore-core-jvm")
    exclude(group = "androidx.datastore", module = "datastore-jvm")
    exclude(group = "androidx.datastore", module = "datastore-preferences-jvm")
    exclude(group = "androidx.collection", module = "collection-ktx")
}

dependencies {
    // Local AAR — does not pull Maven transitives; keep COUI deps below in sync.
    implementation(files("libs/coui-1.0.0.aar"))
    // COUI spring overscroll writes View.mScrollY via reflection; blocked on
    // targetSdk 28+ for user apps (DuraSpeed is platform-exempt).
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation("androidx.collection:collection:1.3.0")
    implementation("androidx.fragment:fragment:1.6.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.viewpager:viewpager:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("com.airbnb.android:lottie:6.0.0")
    implementation("androidx.asynclayoutinflater:asynclayoutinflater:1.0.0")
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.material)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media)
    implementation(libs.hilt.android)
    implementation(libs.glide)
    implementation(libs.androidx.datastore.preferences)
    kapt(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(project(":lib"))
    implementation("com.pranavpandey.android:dynamic-support:6.4.1")
    implementation("com.github.yalantis:ucrop:2.2.11")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("jp.wasabeef:glide-transformations:4.3.0")
}
