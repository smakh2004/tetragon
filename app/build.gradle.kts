plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)

    id("kotlin-parcelize")
}

android {
    namespace = "com.tetragon.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tetragon.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            ndk.debugSymbolLevel = "FULL"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }

    // RESOLVES THE DUPLICATE .so FILE CONFLICT
    packaging {
        jniLibs {
            // This is the specific fix for .so files in newer Gradle versions
            pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
            pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
            pickFirsts.add("lib/x86/libc++_shared.so")
            pickFirsts.add("lib/x86_64/libc++_shared.so")
        }
        resources {
            excludes.add("/META-INF/*.kotlin_module")
        }
    }

    @Suppress("UnstableApiUsage")
    androidResources {
        // Added .ptl to ensure PyTorch models aren't compressed
        noCompress += listOf("tflite", "ptl")
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(libs.firebase.database)
    implementation(libs.androidx.lifecycle.process)
    val nav_version = "2.9.7"

    implementation(libs.firebase.firestore)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Views/Fragments Integration
    implementation("androidx.navigation:navigation-fragment:${nav_version}")
    implementation("androidx.navigation:navigation-ui:${nav_version}")

    // Rive
    implementation("app.rive:rive-android:10.5.3")
    implementation("androidx.startup:startup-runtime:1.2.0")

    // TensorFlow Lite dependencies (Keep if still needed elsewhere, otherwise remove)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // PyTorch Mobile Lite dependencies
    implementation("org.pytorch:pytorch_android_lite:1.13.1")
    implementation("org.pytorch:pytorch_android_torchvision_lite:1.13.1")

    // API Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Add this line to fix the GoogleSignInClient error
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    implementation("com.android.billingclient:billing-ktx:7.1.1")
}