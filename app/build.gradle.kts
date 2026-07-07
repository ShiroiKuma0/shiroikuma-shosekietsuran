@file:Suppress("UnstableApiUsage")

import java.util.Properties
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Copy
import org.xml.sax.InputSource

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    id("com.diffplug.spotless") version "8.2.1"
    alias(libs.plugins.kover)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

// --- shiroikuma fork: per-build version tail ---
// forkVersionName = "<upstream>+N", forkVersionCode = <upstream> * 10000 + N,
// where N = BUILD_NUMBER from gradle.properties (bumped by buildApk, reset to 1 on upstream sync).
val forkBuildNumber = (project.findProperty("BUILD_NUMBER") as String?)?.trim()?.toIntOrNull() ?: 1

// --- shiroikuma fork: release signing from a gitignored keystore.properties ---
// Maps our standard keystore.properties keys onto the upstream MYAPP_RELEASE_* entries
// (which upstream reads from local.properties), so the upstream signing blocks stay untouched.
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    val keystoreProperties = Properties()
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    localProperties.setProperty("MYAPP_RELEASE_STORE_FILE", keystoreProperties.getProperty("storeFile"))
    localProperties.setProperty("MYAPP_RELEASE_STORE_PASSWORD", keystoreProperties.getProperty("storePassword"))
    localProperties.setProperty("MYAPP_RELEASE_KEY_ALIAS", keystoreProperties.getProperty("keyAlias"))
    localProperties.setProperty("MYAPP_RELEASE_KEY_PASSWORD", keystoreProperties.getProperty("keyPassword"))
}

fun configuredAppLocaleTags(): Set<String> {
    val localesConfigXml = providers
        .fileContents(layout.projectDirectory.file("src/main/res/xml/locales_config.xml"))
        .asText
        .get()
    val androidNamespace = "http://schemas.android.com/apk/res/android"
    val document = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(InputSource(StringReader(localesConfigXml)))
    val localeNodes = document.getElementsByTagName("locale")

    return buildSet {
        for (index in 0 until localeNodes.length) {
            val name = localeNodes.item(index)
                .attributes
                ?.getNamedItemNS(androidNamespace, "name")
                ?.nodeValue
                ?.takeIf { it.isNotBlank() }
            if (name != null) add(name)
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

android {
    namespace = "com.aryan.reader"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "shiroikuma.shosekietsuran"
        minSdk = 26
        targetSdk = 36
        versionCode = 59
        versionName = "1.0.54"

        // shiroikuma fork: keep upstream's literals above untouched (rebase-friendly);
        // derive our real version from them: "<upstream>+N" / <upstream> * 10000 + N.
        versionName = "$versionName+$forkBuildNumber"
        versionCode = versionCode!! * 10000 + forkBuildNumber

        // shiroikuma fork: single-ABI build (matches the shiroikuma-shosekietsuran_*_arm64-v8a.apk name).
        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }
        buildConfigField("boolean", "IS_PRO", "false")
        buildConfigField("boolean", "IS_OFFLINE", "false")
    }

    flavorDimensions += "version"
    productFlavors {
        create("oss") {
            dimension = "version"
            // shiroikuma fork: no ".oss" appId / "-oss" versionName suffix — the installed
            // id must be exactly shiroikuma.shosekietsuran, the version exactly "<upstream>+N".
            buildConfigField("String", "AI_WORKER_URL", "\"\"")
            buildConfigField("String", "VERIFIER_WORKER_URL", "\"\"")
            buildConfigField("String", "FEEDBACK_WORKER_URL", "\"\"")
            buildConfigField("boolean", "IS_PRO", "false")
            buildConfigField("String", "TTS_WORKER_URL", "\"\"")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            excludes += "**/libbrotlicommon.so"
            excludes += "**/libbrotlidec.so"
            excludes += "**/libbrotlienc.so"
        }
    }

    signingConfigs {
        create("release") {
            val storePath = localProperties.getProperty("MYAPP_RELEASE_STORE_FILE")
            if (!storePath.isNullOrEmpty()) {
                storeFile = file(storePath)
                storePassword = localProperties.getProperty("MYAPP_RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("MYAPP_RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("MYAPP_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            val storePath = localProperties.getProperty("MYAPP_RELEASE_STORE_FILE")
            if (!storePath.isNullOrEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "whitebear-rules.pro"
            )
        }

        create("releaseOffline") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "IS_OFFLINE", "true")
            buildConfigField("String", "TTS_WORKER_URL", "\"\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    androidResources {
        localeFilters += configuredAppLocaleTags()
            .map { it.toAndroidResourceConfiguration() }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    publishing {
        singleVariant("release") {
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.maxHeapSize = "4g"
            it.jvmArgs("-Xss2m")
        }
    }
    configurations {
        named("testImplementation") {
            exclude(group = "org.slf4j", module = "slf4j-android")
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val flavor = variant.productFlavors.firstOrNull()?.second.orEmpty()
        val buildType = variant.buildType.orEmpty()
        val versionName = variant.outputs.single().versionName
        val taskName = "copy${variant.name.replaceFirstChar { it.uppercase() }}Apk"

        val copyApk = tasks.register<Copy>(taskName) {
            from(variant.artifacts.get(SingleArtifact.APK))
            include("*.apk")
            into(layout.buildDirectory.dir("outputs/renamed-apk/${variant.name}"))
            rename {
                "Episteme-$flavor-v${versionName.get()}-$buildType.apk"
            }
        }

        val assembleTaskName = "assemble${variant.name.replaceFirstChar { it.uppercase() }}"
        tasks.configureEach {
            if (name == assembleTaskName) {
                finalizedBy(copyApk)
            }
        }
    }
}

fun String.toAndroidResourceConfiguration(): String {
    if (this == "id") return "in"

    val languageTagParts = split("-")
    return if (languageTagParts.size == 2 && languageTagParts[1].length == 2) {
        "${languageTagParts[0]}-r${languageTagParts[1].uppercase()}"
    } else {
        this
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.BuildConfig",
                    "*.ComposableSingletons*",
                    "*_Impl",
                    "*Database_Impl",
                    "*Dao_Impl"
                )
            }
        }
    }
}
//noinspection UseTomlInstead
dependencies {

    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size.class1.android)
    implementation(libs.androidx.credentials)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-web:3.7.0")
    androidTestImplementation("com.google.truth:truth:1.4.2")
    androidTestImplementation("androidx.navigation:navigation-testing:2.10.0")
    androidTestImplementation("io.mockk:mockk-android:1.13.11") {
        exclude(group = "org.junit.jupiter")
    }
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    ksp(libs.androidx.room.compiler)

    implementation("androidx.appcompat:appcompat:1.7.1")

    //noinspection GradleDependency (Updating these might cause the custom toolbox in pagination to break)
    implementation("androidx.navigation:navigation-compose:2.10.0")
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    //noinspection GradleDependency
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0-alpha11")

    implementation("org.jsoup:jsoup:1.17.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.3")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.6.0")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")

    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.compose.runtime:runtime-livedata:1.9.3")

    implementation("org.slf4j:slf4j-android:1.7.36")

    implementation("org.commonmark:commonmark:0.22.0")

    implementation("com.jakewharton.timber:timber:5.0.1")

    implementation("me.zhanghai.android.libarchive:library:1.1.6")

    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")
    implementation("androidx.room:room-paging:2.7.1")

    // Flexmark for Markdown parsing (MD -> HTML)
    implementation("com.vladsch.flexmark:flexmark:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-tasklist:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-autolink:0.64.8")

    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.browser:browser:1.8.0")

    implementation("io.legere:pdfiumandroid:2.0.0")
    // 白い熊 UI: PDF metadata WRITING (document info dictionary); pdfium is read-only.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.zwobble.mammoth:mammoth:1.4.2")

    implementation("com.materialkolor:material-kolor:5.0.0-alpha07")

    debugImplementation("org.tensorflow:tensorflow-lite:2.17.0")
    debugImplementation("org.tensorflow:tensorflow-lite-support:0.5.0")
    debugImplementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
    debugImplementation("org.tensorflow:tensorflow-lite-gpu-api:2.17.0")

    implementation("androidx.core:core-splashscreen:1.2.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("io.mockk:mockk-android:1.14.9")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20251224")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.slf4j:slf4j-nop:2.0.17")
}

spotless {
    kotlin {
        target("src/main/java/**/*.kt", "src/main/kotlin/**/*.kt")
        licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
    }
    cpp {
        target("src/main/cpp/mobi_jni_bridge.c", "src/main/cpp/Woff2Converter.cpp")
        licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
    }
}

// --- shiroikuma fork: build the signed oss release APK, copy to ~/tmp, bump BUILD_NUMBER ---
tasks.register("buildApk") {
    description = "Build the signed oss release APK, copy it to ~/tmp, and bump BUILD_NUMBER for next time."
    dependsOn("assembleOssRelease")
    // Capture project state at configuration time so the action is configuration-cache compatible.
    val fvName = android.defaultConfig.versionName
    val fvCode = android.defaultConfig.versionCode
    val releaseApkDir = layout.buildDirectory.dir("outputs/apk/oss/release")
    val userHome = providers.systemProperty("user.home")
    val propsFile = rootProject.file("gradle.properties")
    val currentBuildNumber = forkBuildNumber
    doLast {
        val apkName = "shiroikuma-shosekietsuran_${fvName}_arm64-v8a.apk"
        val outputDir = releaseApkDir.get().asFile
        val targetDir = File(userHome.get(), "tmp")
        targetDir.mkdirs()
        outputDir.listFiles { _, name -> name.endsWith(".apk") }?.firstOrNull()?.let { apk ->
            val targetFile = File(targetDir, apkName)
            apk.copyTo(targetFile, overwrite = true)
            println("\u001B[1;36m>>> ${targetFile.absolutePath}\u001B[0m")
            println("\u001B[1;36m>>> versionCode $fvCode\u001B[0m")
        } ?: throw GradleException("No APK found in $outputDir")

        // Auto-increment BUILD_NUMBER for the next build.
        val nextBuildNumber = currentBuildNumber + 1
        propsFile.writeText(
            propsFile.readText().replace(
                "BUILD_NUMBER=$currentBuildNumber",
                "BUILD_NUMBER=$nextBuildNumber"
            )
        )
        println("\u001B[1;36m>>> BUILD_NUMBER bumped to $nextBuildNumber\u001B[0m")
    }
}
