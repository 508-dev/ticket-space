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

// A complete local file wins over CI environment variables. With neither
// configured, assembleRelease produces an unsigned APK. See docs/deployment.md.
val releaseSigning: Map<String, String>? =
    run {
        val fromFile =
            rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
                val props = Properties().apply { file.inputStream().use(::load) }
                listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                    .associateWith { props.getProperty(it) }
            }
        // Providers keep changes to signing inputs visible to the configuration cache.
        val fromEnv =
            mapOf(
                "storeFile" to "TICKETSPACE_KEYSTORE_FILE",
                "storePassword" to "TICKETSPACE_KEYSTORE_PASSWORD",
                "keyAlias" to "TICKETSPACE_KEY_ALIAS",
                "keyPassword" to "TICKETSPACE_KEY_PASSWORD",
            ).mapValues { (_, name) -> providers.environmentVariable(name).orNull }

        listOfNotNull(fromFile, fromEnv)
            .firstOrNull { material -> material.values.none { it.isNullOrBlank() } }
            ?.mapValues { (_, value) -> value!! }
    }

// Beta signing is independent of the release key. Local builds without beta
// credentials fall back to the debug key so assembleBeta remains a compile check.
val betaSigning: Map<String, String>? =
    mapOf(
        "storeFile" to "TICKETSPACE_BETA_KEYSTORE_FILE",
        "storePassword" to "TICKETSPACE_BETA_KEYSTORE_PASSWORD",
        "keyAlias" to "TICKETSPACE_BETA_KEY_ALIAS",
        "keyPassword" to "TICKETSPACE_BETA_KEY_PASSWORD",
    ).mapValues { (_, name) -> providers.environmentVariable(name).orNull }
        .takeIf { material -> material.values.none { it.isNullOrBlank() } }
        ?.mapValues { (_, value) -> value!! }

val betaVersionCode = providers.environmentVariable("TICKETSPACE_BETA_VERSION_CODE").orNull?.toInt()
val betaVersionName = providers.environmentVariable("TICKETSPACE_BETA_VERSION_NAME").orNull

fun quoted(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "dev.co508.ticketspace"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.co508.ticketspace"
        minSdk = 31
        targetSdk = 36
        // Derived from root version.txt by scripts/sync-version.sh; keep literals.
        versionCode = 1000
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BASE_URL", quoted(baseUrl))
    }
    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", quoted(local.getProperty("ticketspace.baseUrl", baseUrl)))
        }
        release {
            isMinifyEnabled = false
            // Keep rules required by ARCore keyless authorization (see proguard-rules.pro).
            // Applied once minification is enabled; harmless while it stays disabled.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            releaseSigning?.let { material ->
                signingConfig =
                    signingConfigs.create("release") {
                        storeFile = rootProject.file(material.getValue("storeFile"))
                        storePassword = material.getValue("storePassword")
                        keyAlias = material.getValue("keyAlias")
                        keyPassword = material.getValue("keyPassword")
                    }
            }
        }
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            signingConfig =
                betaSigning?.let { material ->
                    signingConfigs.create("beta") {
                        storeFile = rootProject.file(material.getValue("storeFile"))
                        storePassword = material.getValue("storePassword")
                        keyAlias = material.getValue("keyAlias")
                        keyPassword = material.getValue("keyPassword")
                    }
                } ?: signingConfigs.getByName("debug")
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

// Keep the generated production literals untouched. CI overrides only beta
// outputs with its monotonically increasing workflow run number.
androidComponents {
    onVariants(selector().withBuildType("beta")) { variant ->
        variant.outputs.forEach { output ->
            betaVersionCode?.let { output.versionCode.set(it) }
            betaVersionName?.let { output.versionName.set(it) }
        }
    }
}

ktlint { android.set(true) }

dependencyLocking { lockAllConfigurations() }

dependencies {
    implementation(libs.sceneview.ar)
    implementation(libs.arcore)
    implementation(libs.play.services.auth)
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
    implementation(libs.compose.material.icons.extended)
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
