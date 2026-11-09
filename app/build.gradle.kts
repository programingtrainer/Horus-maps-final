plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.horusmaps.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.horusmaps.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "HORUS_API_BASE_URL",
                "\"https://ui-horusmaps-map-attribution-horus-maps-final.horus-egypt-company.workers.dev\""
            )
        }
        release {
            buildConfigField(
                "String",
                "HORUS_API_BASE_URL",
                "\"https://horus-maps-final.horus-egypt-company.workers.dev\""
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.maplibre.gl:android-sdk:2026.11.2-globe")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.43")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
