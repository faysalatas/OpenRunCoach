plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.ksp)
  id("kotlin-parcelize")
}

// Renderer and provider are independent. No keys are stored in the repository.
// Blank URI disables network maps. Configure a reviewed provider for release builds.
val configuredRouteMapStyle = providers.gradleProperty("routeMapStyleUri").orNull
fun quotedBuildString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
configuredRouteMapStyle?.let {
    require(it.none(Char::isISOControl) && (it.isBlank() || it.startsWith("https://") || it.startsWith("asset://")))
}

android {
    namespace = "com.example.openruncoach"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.openruncoach"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "ROUTE_MAP_STYLE_URI", quotedBuildString(
                configuredRouteMapStyle ?: "https://tiles.openfreemap.org/styles/dark"))
        }
        release {
            buildConfigField("String", "ROUTE_MAP_STYLE_URI", quotedBuildString(configuredRouteMapStyle ?: ""))
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  val serializationBom = platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1")
  implementation(composeBom)
  implementation(serializationBom)
  androidTestImplementation(composeBom)
  androidTestImplementation(serializationBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.health.connect.client)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.maplibre.android)
  implementation(files("libs/samsung-health-data-api.aar"))
  implementation("com.google.code.gson:gson:2.13.2")
  ksp(libs.androidx.room.compiler)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.room.testing)

  // Navigation
  implementation(libs.androidx.navigation.compose)
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}
