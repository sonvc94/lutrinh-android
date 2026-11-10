import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Optional ArcGIS Location Platform key for the "Vệ tinh" basemap (docs/05 "Esri World Imagery"):
 * `-PesriApiKey=…`, `esriApiKey=…` in ~/.gradle/gradle.properties, or in local.properties (git-
 * ignored). Never commit a key. Without one the build uses Esri's keyless endpoint.
 */
val esriApiKey: String = (findProperty("esriApiKey") as String?)
    ?: rootProject.file("local.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } }.getProperty("esriApiKey") }
    ?: ""

/**
 * Base URL of the Lữ Trình route server (R40, docs/05 "Route server"): `lutrinh.routeServerUrl=…`
 * in local.properties (git-ignored) or `-Plutrinh.routeServerUrl=…`. Release builds accept only
 * https://; debug builds default to the emulator's view of the Mac's localhost and may also use
 * cleartext http to 10.0.2.2 / localhost (src/debug network security config). Read only by
 * catalog/Catalogs.kt (CatalogBoundaryTest).
 */
val routeServerUrl: String? = (findProperty("lutrinh.routeServerUrl") as String?)
    ?: rootProject.file("local.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } }.getProperty("lutrinh.routeServerUrl") }

android {
    namespace = "vn.lutrinh.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.lutrinh.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ESRI_API_KEY", "\"${esriApiKey.replace("\"", "")}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "ROUTE_SERVER_URL", "\"${(routeServerUrl ?: "http://10.0.2.2:8000").replace("\"", "")}\"")
        }
        release {
            isMinifyEnabled = false
            // https only: any other address leaves release builds without a server ("Nhập mã lộ trình" is hidden).
            val url = routeServerUrl?.takeIf { it.startsWith("https://") }.orEmpty()
            buildConfigField("String", "ROUTE_SERVER_URL", "\"${url.replace("\"", "")}\"")
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
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Images: Coil 3 core only (no coil-network module) -> it can load local files, never URLs.
    implementation(libs.coil.compose)
    // Video thumbnails (R29): a frame decoder over the platform MediaMetadataRetriever (no player).
    implementation(libs.coil.video)
    // EXIF orientation + removing GPS tags on photo import (data/PhotoStorage.kt).
    implementation(libs.androidx.exifinterface)

    // Map: MapLibre Native rendering OpenStreetMap raster tiles (see map/MapConfig.kt).
    implementation(libs.maplibre.android)
    // Only used to give MapLibre's tile requests a proper User-Agent (MapLibre already ships OkHttp).
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
