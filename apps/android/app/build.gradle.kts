import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

val local =
    Properties().apply {
        rootProject
            .file("local.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use(::load)
    }
val baseUrl = providers.gradleProperty("ticketspace.baseUrl").getOrElse("https://ticketspace.508.dev")
val arcoreAuth = providers.gradleProperty("ticketspace.arcoreAuth").getOrElse("apikey")
require(arcoreAuth in listOf("apikey", "keyless")) { "ticketspace.arcoreAuth must be apikey or keyless" }

fun quoted(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "dev.co508.ticketspace"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.co508.ticketspace"
        minSdk = 31
        targetSdk = 36
        versionCode = 1000
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BASE_URL", quoted(baseUrl))
        buildConfigField("String", "ARCORE_AUTH", quoted(arcoreAuth))
        manifestPlaceholders["arcoreApiKey"] =
            if (arcoreAuth == "apikey") local.getProperty("ARCORE_API_KEY", "") else ""
    }
    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", quoted(local.getProperty("ticketspace.baseUrl", baseUrl)))
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

ktlint { android.set(true) }

dependencyLocking { lockAllConfigurations() }

dependencies {
    implementation(libs.sceneview.ar)
    implementation(libs.arcore)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
