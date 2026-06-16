plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.wlsreminderapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.wlsreminderapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.5.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
}