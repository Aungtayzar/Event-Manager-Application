import java.net.URI

plugins {
    alias(libs.plugins.android.application)
}

val eventApiBaseUrl = providers.gradleProperty("eventApiBaseUrl")
    .orElse("http://10.0.2.2/eventmanagementapi/").get().trimEnd('/') + "/"
val apiUri = URI(eventApiBaseUrl)
require(apiUri.scheme in listOf("http", "https") && apiUri.host != null &&
    apiUri.userInfo == null && apiUri.query == null && apiUri.fragment == null) {
    "eventApiBaseUrl must be an HTTP(S) base URL without credentials, query or fragment"
}

android {
    namespace = "com.example.eventmanagerapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.eventmanagerapplication"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "EVENT_API_BASE_URL", "\"${eventApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildTypes {
        release {
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
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        animationsDisabled = true
    }
}

dependencies {
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
