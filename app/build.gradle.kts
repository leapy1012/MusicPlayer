import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val localSigningFile = rootProject.file("keystore.properties")
val localSigning = Properties().apply {
    if (localSigningFile.isFile) localSigningFile.inputStream().use { load(it) }
}
if (localSigningFile.isFile) {
    require(listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all {
        !localSigning.getProperty(it).isNullOrBlank()
    }) { "keystore.properties must specify storeFile, storePassword, keyAlias and keyPassword" }
    require(rootProject.file(localSigning.getProperty("storeFile")).isFile) {
        "Signing keystore does not exist: ${localSigning.getProperty("storeFile")}"
    }
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
            // Local firmware signing overrides the bundled generic AOSP test key.
            // Resolve relative paths from the repository root, not app/.
            storeFile = rootProject.file(localSigning.getProperty("storeFile", "platform.keystore"))
            storePassword = localSigning.getProperty("storePassword", "123456")
            keyAlias = localSigning.getProperty("keyAlias", "platform")
            keyPassword = localSigning.getProperty("keyPassword", "123456")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("platform")
        }
        release {
            // Leapy modified begin: Original isMinifyEnabled = false.
            isMinifyEnabled = true
            // Leapy end

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
    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    // Leapy added begin: The locally imported uCrop AAR does not supply its HTTP dependency transitively.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Leapy end
    // COUI from DreamCOUI/dist (D960_Patch vendor/greendream/{coui,physicsengine}).
    // Local AARs do not pull Maven transitives; keep COUI deps below in sync
    // (DreamCOUI/dist/USAGE.txt). com.dream.* comes from dream-framework on the device.
    // Leapy modified begin: Use the R8 release AAR directly.
    // Original: implementation(files("libs/dream-coui-2.1.0.aar"))
    implementation(files("${rootDir}/libs/dream-coui-2.1.0.aar"))
    // Leapy end
    // Leapy modified begin: Use the R8 release AAR directly.
    // Original: implementation(files("libs/dream-physicsengine-2.1.0.aar"))
    implementation(files("${rootDir}/libs/dream-physicsengine-2.1.0.aar"))
    // Leapy end
    compileOnly(files("libs/dream-framework-stubs-2.1.0.jar"))
    // Leapy_tts added begin: Real API class files exported from the matching AOSP build.
    // Required at compile time; implementations remain in the device framework.
    compileOnly(files("libs/dream-tts-framework-api.jar"))
    // Leapy_tts end
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
    ksp(libs.glide.ksp)
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

// Leapy_tts added begin: Robolectric 4.12 needs this for real ParcelFileDescriptor stream cleanup on JDK 17.
// This applies only to JVM tests, not the application or device runtime.
tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
}
// Leapy_tts end

// Leapy added begin: Targeted release-only R8 policy; debug remains unchanged.
apply(from = rootProject.file("protection/release.gradle"))
// Leapy end
