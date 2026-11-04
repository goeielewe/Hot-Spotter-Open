import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Names that must not appear in this public tree or in the APK.
 * Pieces are joined at runtime so this file does not contain them whole.
 */
/** Substrings banned from our source. The public README may contain the first one once. */
val sourceTokens: List<String> = listOf(
    "fl" + "ir",
    "at" + "las",
    "tele" + "dyne",
    "viv" + "idir",
    "thermal" + "sdk",
    "Fli" + "rHo" + "oks",
    "lib" + "at" + "las",
)

/**
 * APK scan uses SDK class and library names. Short fragments also occur inside
 * Android framework resources and an icon-name list, so those fragments are
 * not searched in the packaged file.
 */
val apkTokens: List<String> = listOf(
    "com/" + "fl" + "ir",
    "com." + "fl" + "ir",
    "lib" + "at" + "las",
    "thermal" + "sdk",
    "Fli" + "rHo" + "oks",
    "tele" + "dyne",
    "viv" + "idir",
)

fun textHits(text: String, tokens: List<String>): List<String> =
    tokens.filter { token -> text.contains(token, ignoreCase = true) }

android {
    // Kotlin package stays here. The installed id is applicationId.
    namespace = "com.benchfirewatch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hotspotter.open"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "1.8.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.register("checkNoVendorSdkInSource") {
    group = "verification"
    description = "Fails if a blocked vendor SDK name is present in source."
    doLast {
        val root = rootProject.projectDir
        val skipDirs = setOf(".git", ".gradle", "build", ".idea")
        val textExt = setOf(
            "kt", "kts", "xml", "txt", "md", "pro", "properties",
            "java", "gradle", "scad", "py", "sh", "yml", "yaml",
        )
        val hits = mutableListOf<String>()
        val primary = sourceTokens.first()
        root.walkTopDown().onEnter { dir -> dir.name !in skipDirs }.forEach { file ->
            if (!file.isFile) return@forEach
            if (file.extension.lowercase() !in textExt) return@forEach
            val rel = file.relativeTo(root).invariantSeparatorsPath
            val text = runCatching { file.readText() }.getOrElse { return@forEach }
            val found = textHits(text, sourceTokens)
            if (found.isEmpty()) return@forEach
            if (rel == "README.md") {
                val count = Regex(Regex.escape(primary), RegexOption.IGNORE_CASE).findAll(text).count()
                val others = found.filter { !it.equals(primary, ignoreCase = true) }
                if (count <= 1 && others.isEmpty()) return@forEach
            }
            hits += "$rel (${found.distinct().joinToString()})"
        }
        if (hits.isNotEmpty()) {
            error("Blocked vendor SDK material in source:\n" + hits.joinToString("\n"))
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("checkNoVendorSdkInSource")
}

tasks.named("check").configure {
    dependsOn("checkNoVendorSdkInSource")
}

tasks.configureEach {
    if (name != "assembleDebug") return@configureEach
    doLast {
        val apkDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val apks = apkDir.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
        if (apks.isEmpty()) error("Debug APK was not produced")
        apks.forEach { apk ->
            val hits = mutableListOf<String>()
            ZipFile(apk).use { zip ->
                val entries = zip.entries().asSequence().toList()
                for (entry in entries) {
                    for (token in apkTokens) {
                        if (entry.name.contains(token, ignoreCase = true)) {
                            hits += "name ${entry.name} ($token)"
                        }
                    }
                    val scan = !entry.isDirectory && (
                        entry.name.endsWith(".dex") ||
                            entry.name.endsWith(".so") ||
                            entry.name.endsWith(".arsc") ||
                            entry.name.endsWith(".xml") ||
                            entry.name.endsWith(".txt") ||
                            entry.name.endsWith(".properties") ||
                            entry.name.startsWith("assets/")
                        )
                    if (!scan) continue
                    val text = zip.getInputStream(entry).use { String(it.readBytes(), Charsets.ISO_8859_1) }
                    for (token in apkTokens) {
                        if (text.contains(token, ignoreCase = true)) hits += "${entry.name} ($token)"
                    }
                }
            }
            if (hits.isNotEmpty()) {
                error("Debug APK ${apk.name} contains blocked vendor SDK material: ${hits.distinct().take(12)}")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
