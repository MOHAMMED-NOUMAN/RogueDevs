plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.itantra.app"
    compileSdk = 36 // match whatever the wizard set; bump only if AS prompts you

    defaultConfig {
        applicationId = "com.itantra.app"
        minSdk = 26        // floor for low/mid-range devices — revisit once STT/TTS engine is picked, some need higher
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // The ONNX graphs are 42 MB per language; compressing them would make every
        // launch pay to inflate them again.
        noCompress += listOf("onnx")
    }

    buildTypes {
        release {
            isMinifyEnabled = false // flip on once the app is feature-complete, not for early boilerplate
        }
        // Debug build plus the "iTantra transport debug" test screen (src/transportDebug).
        // Build it only when testing the link: ./gradlew assembleTransportDebug
        create("transportDebug") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
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
    }
}

dependencies {
    // Compose (BOM keeps every Compose artifact on matching versions)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Hilt (DI)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room (local DB — messages, team roster, etc.)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore (settings/prefs — replaces SharedPreferences)
    implementation(libs.datastore.preferences)

    // Offline speech-to-text (Whisper ONNX graphs in assets)
    implementation(libs.onnxruntime.android)

    // Coroutines + Serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Debug-only Compose tooling (layout inspector, @Preview rendering)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // JVM unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // On-device tests (the STT benchmark runs here)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// ── Offline-only guard ───────────────────────────────────────────────────────
// The app must work with no network and may not use closed-source SDKs. INTERNET is
// declared only because Android needs it to open the local Wi-Fi Direct socket. This
// check runs before every build and fails it if an HTTP client or a Play Services /
// Firebase / ML Kit library shows up as a dependency, or if our source uses an HTTP API.
abstract class CheckOfflineOnly : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val runtimeGraphs: ListProperty<ResolvedComponentResult>

    @TaskAction
    fun check() {
        val bannedModulePrefixes = listOf(
            "com.squareup.okhttp", "com.squareup.retrofit", "io.ktor:ktor-client",
            "com.android.volley", "org.apache.httpcomponents", "com.google.net.cronet",
            "org.chromium.net", "com.google.android.gms", "com.google.firebase", "com.google.mlkit",
        )
        val bannedApis = listOf(
            "java.net.URL", "java.net.URLConnection", "java.net.HttpURLConnection",
            "javax.net.ssl.HttpsURLConnection", "android.net.http.", "android.webkit.WebView",
            "okhttp3.", "retrofit2.", "io.ktor.client.", "com.android.volley.",
        ).associateWith { api -> Regex(Regex.escape(api) + if (api.endsWith(".")) "" else "\\b") }

        val modules = runtimeGraphs.get().flatMap { root ->
            val seen = LinkedHashSet<ResolvedComponentResult>()
            val stack = ArrayDeque(listOf(root))
            while (stack.isNotEmpty()) {
                val component = stack.removeLast()
                if (!seen.add(component)) continue
                component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { stack.add(it.selected) }
            }
            seen.mapNotNull { c -> c.moduleVersion?.let { "${it.group}:${it.name}" } }
        }.toSortedSet()

        val problems = modules.filter { m -> bannedModulePrefixes.any { m.startsWith(it) } }
            .map { "dependency $it" } +
            sources.files.sortedBy { it.path }.flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    bannedApis.entries.firstOrNull { it.value.containsMatchIn(line) }
                        ?.let { "${file.name}:${i + 1} uses ${it.key}" }
                }
            }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Offline-only check failed:\n" + problems.joinToString("\n") { "  - $it" }
            )
        }
    }
}

val checkOfflineOnly = tasks.register<CheckOfflineOnly>("checkOfflineOnly") {
    group = "verification"
    description = "Fails if an HTTP client or closed-source Google SDK is used."
    sources.from(fileTree("src") {
        include("**/*.kt", "**/*.java")
        exclude("test/**", "androidTest/**")
    })
}

androidComponents {
    onVariants { variant ->
        checkOfflineOnly.configure {
            runtimeGraphs.add(variant.runtimeConfiguration.incoming.resolutionResult.rootComponent)
        }
    }
}

tasks.named("preBuild") { dependsOn(checkOfflineOnly) }
