plugins {
    id("com.android.application")
    kotlin("android")
    // Remove kotlin("android.extensions") if you don't use deprecated Kotlin Android Extensions
}


android {
    namespace = "com.example.donoapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.donoapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core library
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    val cameraXVersion = "1.3.2" // Replace with the latest stable version if available

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.text.recognition)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.constraintlayout)
    // To use constraintlayout in compose
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.material)
    implementation(libs.androidx.material3.v101)


    // Testing Libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Compose
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material.v101)
    implementation(libs.androidx.ui.tooling.preview)

    // Navigation for Compose
    implementation(libs.androidx.navigation.compose.v240alpha10)

    // CameraX
    implementation(libs.androidx.camera.core.v133)
    implementation(libs.androidx.camera.camera2.v133)
    implementation(libs.androidx.camera.lifecycle.v133)
    implementation(libs.androidx.camera.view.v133)

    // ML Kit
    implementation(libs.text.recognition.v1700) // Ensure version matches

    // Core and other libraries
    implementation(libs.androidx.core.ktx.v160)
    implementation(libs.androidx.appcompat.v130)
    implementation(libs.androidx.activity.compose.v131)

    // Material Design
    implementation(libs.material)

    implementation(libs.androidx.navigation.compose)





}