plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // Jetpack Compose 화면
}

android {
    namespace = "com.kumhoht.todaysost"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kumhoht.todaysost"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 공모전 제출용: 디버그 키로 서명해 바로 설치·실행되게 한다 (붕붕멍과 동일).
            signingConfig = signingConfigs.getByName("debug")
            // R8 최적화는 끈다: 붕붕멍에서 켰을 때 kotlin 라이브러리 IllegalAccessError 로 실행 직후 종료됨.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Android Automotive 표준 차량 API (CarPropertyManager, CarUxRestrictionsManager)
    useLibrary("android.car")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)

    // 화면 (Jetpack Compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Pleos Connect SDK (차량: 기어·깜빡이 등 / 내비: 제한속도·도착)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)

    testImplementation(libs.junit)
}
