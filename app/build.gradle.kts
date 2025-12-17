plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // ✅ 추가
}

android {
    namespace = "com.example.painter"   // 패키지명과 일치
    compileSdk = 34                     // ✅ 이 줄이 없어서 에러 발생

    defaultConfig {
        applicationId = "com.example.painter"
        minSdk = 26
        targetSdk = 34

        versionCode = 1
        versionName = "1.0"
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(platform("androidx.compose:compose-bom:2024.10.00"))

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    dependencies { implementation("com.google.code.gson:gson:2.11.0") }
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("com.google.code.gson:gson:2.11.0")
}
