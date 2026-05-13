import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 读取项目根目录 keystore.properties（git-ignore）以获取签名信息。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}
val hasKeystore: Boolean = keystoreProperties.getProperty("storeFile")?.isNotBlank() == true

android {
    namespace = "cn.modificator.launcher"
    compileSdk = 36
    enableKotlin = false

    defaultConfig {
        applicationId = "cn.modificator.launcher"
        minSdk = 21
        targetSdk = 36
        versionCode = 36
        versionName = "0.2.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Baseline profiles speed up cold start by precompiling key paths.
        // To regenerate: ./gradlew :app:generateBaselineProfile (needs benchmark module).
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 两个发布渠道：generic（任何 Android 设备）/ supernote（针对 Chauvet OS 额外过滤厂商内部包）
    flavorDimensions += "device"
    productFlavors {
        create("generic") {
            dimension = "device"
            versionNameSuffix = "-generic"
        }
        create("supernote") {
            dimension = "device"
            versionNameSuffix = "-supernote"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                // Resolve relative to project root (where keystore.properties lives) so that
                // a path like "eink-launcher.jks" finds the file next to the properties.
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt"
            )
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.splashscreen)

    // Enables AGP to package baseline profiles into the APK so the runtime can install them.
    // A real profile can later be generated with the androidx.benchmark plugin.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}
