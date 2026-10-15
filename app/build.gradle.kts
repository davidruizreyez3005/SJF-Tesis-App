plugins {
    id("com.android.application") version "8.2.2"
    id("org.jetbrains.kotlin.android") version "1.9.22"
}

// Firma de release. La llave nunca vive en el repositorio: se recibe por
// variables de entorno (CI las toma de los secretos de GitHub; ver SIGNING.md).
// Sin ellas, la APK de release se genera sin firmar.
fun signingEnv(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
val signingStoreFile = signingEnv("SIGNING_STORE_FILE")

android {
    namespace = "mx.sjf.tesis"
    compileSdk = 34

    defaultConfig {
        applicationId = "mx.sjf.tesis"
        minSdk = 26
        targetSdk = 34
        versionCode = 16
        versionName = "2.8.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (signingStoreFile != null) {
            create("release") {
                storeFile = file(signingStoreFile)
                storePassword = signingEnv("SIGNING_STORE_PASSWORD")
                keyAlias = signingEnv("SIGNING_KEY_ALIAS")
                keyPassword = signingEnv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: quita el código y los recursos que la app no usa (sobre todo
            // los miles de íconos de material-icons-extended). Ver proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    // Robolectric necesita los recursos de Android para las pruebas de navegación.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "/META-INF/DEPENDENCIES"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    // org.json real para pruebas JVM (el android.jar solo trae stubs).
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
