plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    kotlin("kapt")
}


android {
    namespace = "com.example.expensetracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.expensetracker"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:preferences"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:di"))
    implementation(project(":feature:home"))
    implementation(project(":feature:addtransaction"))
    implementation(project(":feature:transactions"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:budgets"))
    implementation(project(":feature:accounts"))
    implementation(project(":feature:categories"))
    implementation(project(":feature:recurring"))
    implementation(project(":feature:statistics"))
    implementation(project(":feature:export"))
    implementation(project(":feature:security"))
    implementation(project(":core:notifications"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:backup"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
