import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

/**
 * Fails the build if a network permission reaches the merged manifest, including
 * one pulled in transitively by a dependency. NoNetworkPermissionTest covers the
 * hand-written manifest; this covers everything merged into it.
 */
abstract class CheckNoNetworkPermission : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    /** The app's own package, whose signature-level permissions are self-scoped. */
    @get:Input
    abstract val appNamespace: Property<String>

    /** The only permissions outside the app's namespace allowed in the merged manifest. */
    @get:Input
    abstract val allowed: SetProperty<String>

    @get:OutputFile
    abstract val receipt: RegularFileProperty

    @TaskAction
    fun check() {
        // Matches on the element rather than a denylist of permission strings, so a
        // permission nobody anticipated still fails, and a comment that merely mentions
        // one does not.
        //
        // AndroidX adds <appId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION, a signature
        // permission namespaced to this app and granted to nobody else. Anything under
        // our own namespace is allowed; anything else — every android.permission.* in
        // particular — is not, apart from the named allowlist.
        //
        // The allowlist holds the two the weekly reminder needs (logging-reminder):
        // POST_NOTIFICATIONS to show it and RECEIVE_BOOT_COMPLETED to re-arm it after a
        // restart. Neither reaches the network, and each is named one by one, so a
        // network permission from any dependency still fails here.
        val own = appNamespace.get()
        val permitted = allowed.get()
        val foreign = Regex("""<uses-permission[^>]*android:name\s*=\s*"([^"]+)"""")
            .findAll(mergedManifest.get().asFile.readText())
            .map { it.groupValues[1] }
            .filterNot { it.startsWith("$own.") }
            .filterNot { it in permitted }
            .toList()

        if (foreign.isNotEmpty()) {
            throw GradleException(
                "Merged manifest declares ${foreign.joinToString()}. Saku ships with no " +
                    "permissions outside its own namespace beyond ${permitted.joinToString()}. " +
                    "Find the dependency that " +
                    "added it and drop it. If one is genuinely needed, change this task " +
                    "deliberately rather than deleting it.",
            )
        }
        receipt.get().asFile.writeText("no permission outside the allowlist in merged manifest\n")
    }
}

android {
    namespace = "com.dvnnfrr.saku"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dvnnfrr.saku"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    /**
     * Release signing, from the environment.
     *
     * Configured only when all four variables are present, so `assembleRelease` still
     * works on a machine with no keystore — it just produces an unsigned APK, exactly
     * as it did before. Silently falling back matters because CI builds release on
     * every pull request, including from a fork, where secrets are not exposed.
     *
     * The keystore is never read from a path inside the repo. CI writes it to a
     * runner-local file outside the checkout and deletes it afterwards; locally you
     * point SAKU_KEYSTORE at wherever you actually keep it.
     */
    val signingEnv = listOf(
        "SAKU_KEYSTORE",
        "SAKU_KEYSTORE_PASSWORD",
        "SAKU_KEY_ALIAS",
        "SAKU_KEY_PASSWORD",
    ).associateWith { System.getenv(it) }
    val canSign = signingEnv.values.all { !it.isNullOrBlank() }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = file(signingEnv.getValue("SAKU_KEYSTORE")!!)
                storePassword = signingEnv.getValue("SAKU_KEYSTORE_PASSWORD")
                keyAlias = signingEnv.getValue("SAKU_KEY_ALIAS")
                keyPassword = signingEnv.getValue("SAKU_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Left unset when the environment is incomplete. AGP would otherwise fall
            // back to the debug key, which produces something that looks signed,
            // installs fine, and cannot be updated by a real release later.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room needs its generated-schema directory declared so migrations can be diffed later.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register<CheckNoNetworkPermission>("check${name}NoNetworkPermission") {
            group = "verification"
            description = "Fails if the merged $name manifest declares a network permission."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            appNamespace.set(android.namespace)
            allowed.set(
                setOf(
                    "android.permission.POST_NOTIFICATIONS",
                    "android.permission.RECEIVE_BOOT_COMPLETED",
                ),
            )
            receipt.set(layout.buildDirectory.file("reports/no-network-permission/$name.txt"))
        }
        // Bind to assemble so a release build cannot ship network access unnoticed.
        // `matching` is a live view: the variant's assemble task does not exist yet
        // at onVariants time, so tasks.named would fail here.
        tasks.matching { it.name == "assemble$name" }.configureEach { dependsOn(check) }
    }
}

tasks.named("check").configure { dependsOn("checkDebugNoNetworkPermission") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.datetime)

    implementation(libs.fastexcel.writer)
    implementation(libs.fastexcel.reader)
    implementation(libs.stax.api)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    // Room DAO tests run on the JVM so the balance query is covered by CI, which has
    // no emulator. Robolectric supplies the SQLite and Context they need.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
