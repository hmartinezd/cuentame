import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun requiredLocalProperty(name: String): String =
    localProperties.getProperty(name)?.trim()?.takeIf(String::isNotEmpty)
        ?: throw GradleException(
            "Missing required $name in root local.properties. " +
                "Add the Supabase mobile development value locally; do not commit credentials."
        )

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val supabaseUrl = requiredLocalProperty("SUPABASE_URL")
val supabasePublishableKey = requiredLocalProperty("SUPABASE_PUBLISHABLE_KEY")

require(supabaseUrl.startsWith("https://") || supabaseUrl.startsWith("http://")) {
    "SUPABASE_URL in root local.properties must be an HTTP(S) project URL."
}
require(supabasePublishableKey.startsWith("sb_publishable_")) {
    "SUPABASE_PUBLISHABLE_KEY in root local.properties must be a low-privilege sb_publishable_ key."
}

android {
    namespace = "com.venkoi.restaurantops"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.venkoi.restaurantops"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.venkoi.restaurantops.CustomTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "SUPABASE_URL", supabaseUrl.asBuildConfigString())
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            supabasePublishableKey.asBuildConfigString()
        )
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }

    testOptions {
        animationsDisabled = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests.all { testTask ->
            testTask.jvmArgs("-Xmx4096m", "-XX:MaxMetaspaceSize=1024m")
            testTask.maxParallelForks = 1
            testTask.systemProperty("venkoi.repoRoot", rootProject.projectDir.absolutePath)
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Cloud infrastructure
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    
    // ML Kit
    implementation(libs.google.mlkit.document.scanner)
    implementation(libs.google.mlkit.text.recognition)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // CSV
    implementation(libs.kotlin.csv)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.room.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.mockkAndroid)
    androidTestUtil(libs.androidx.orchestrator)
    kspAndroidTest(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Room Schema Export
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
