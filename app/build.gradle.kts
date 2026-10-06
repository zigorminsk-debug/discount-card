import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Номер сборки: приходит из GitHub Actions (-PappBuildNumber=${{ github.run_number }})
 * или из переменной окружения APP_BUILD_NUMBER. Локально = 0.
 */
val appBuildNumber: Int =
    (project.findProperty("appBuildNumber") as String?)?.toIntOrNull()
        ?: System.getenv("APP_BUILD_NUMBER")?.toIntOrNull()
        ?: 0

val appVersionBase: String = (project.findProperty("appVersionBase") as String?) ?: "1.0"
val appVersionName: String = "$appVersionBase.$appBuildNumber"

/**
 * Постоянный ключ подписи.
 * Приоритет: переменные окружения (CI) -> keystore.properties (локально) -> debug-ключ.
 */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

fun secret(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseStoreFilePath = secret("KEYSTORE_FILE", "storeFile")
val releaseStorePassword = secret("KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = secret("KEY_ALIAS", "keyAlias")
val releaseKeyPassword = secret("KEY_PASSWORD", "keyPassword")
val releaseStoreFile = releaseStoreFilePath?.let { path ->
    file(path).takeIf { it.exists() } ?: rootProject.file(path).takeIf { it.exists() }
}
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null

android {
    namespace = "com.zigor.discountcard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zigor.discountcard"
        minSdk = 24
        targetSdk = 35
        versionCode = appBuildNumber.coerceAtLeast(1)
        versionName = appVersionName
        resourceConfigurations += listOf("ru", "en")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword ?: releaseStorePassword
                storeType = "PKCS12"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("WARNING: release keystore не найден — APK будет подписан debug-ключом!")
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
            )
        }
    }


    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
