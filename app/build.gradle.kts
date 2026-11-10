import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // Jetpack Compose 화면
    alias(libs.plugins.ksp)            // Room DB 코드 자동 생성
}

// ───────────── FluidSynth (LGPL-2.1, 동적 링크 .so) ─────────────
// 공식 안드로이드 빌드를 빌드할 때 받아서 필요한 .so 와 헤더만 꺼낸다 (저장소에는 넣지 않음, ABI 당 약 15MB).
// 버전을 바꿀 때는 URL 과 SHA-256 (GitHub 릴리스의 digest) 을 함께 바꾼다.
val fluidSynthVersion = "2.6.2"
val fluidSynthSha256 = "f29d1fe9c46c8528c65d22b2bfcd15997960f908370531a5a9427a73ab9e5313"
/** arm64-v8a: 실차(Pleos), x86_64: Pleos Connect 에뮬레이터 */
val targetAbis = listOf("arm64-v8a", "x86_64")
/** libfluidsynth.so 와 그 의존성 (SF3 = libsndfile + Ogg Vorbis). assetloader, vorbisfile 은 쓰지 않음 */
val fluidSynthLibs = listOf(
    "libfluidsynth.so", "liboboe.so", "libsndfile.so", "libFLAC.so",
    "libogg.so", "libopus.so", "libvorbis.so", "libvorbisenc.so",
)
val fluidSynthDir = layout.buildDirectory.dir("fluidsynth")

abstract class FetchFluidSynth : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Input abstract val abis: ListProperty<String>
    @get:Input abstract val libs: ListProperty<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val zip = File(temporaryDir, "fluidsynth.zip")
        URI(url.get()).toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
        val digest = MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == sha256.get()) { "FluidSynth zip SHA-256 불일치: $digest" }

        val out = outputDir.get().asFile
        out.deleteRecursively()
        ZipFile(zip).use { z ->
            for (entry in z.entries()) {
                if (entry.isDirectory) continue
                val parts = entry.name.split('/')
                val target = when {
                    parts[0] == "include" -> File(out, entry.name)
                    parts.size == 3 && parts[0] == "lib" && parts[1] in abis.get() && parts[2] in libs.get() ->
                        File(out, "jniLibs/${parts[1]}/${parts[2]}")
                    else -> null
                } ?: continue
                target.parentFile.mkdirs()
                z.getInputStream(entry).use { i -> target.outputStream().use { i.copyTo(it) } }
            }
        }
        zip.delete()
    }
}

val fetchFluidSynth = tasks.register<FetchFluidSynth>("fetchFluidSynth") {
    url.set("https://github.com/FluidSynth/fluidsynth/releases/download/v$fluidSynthVersion/fluidsynth-v$fluidSynthVersion-android24.zip")
    sha256.set(fluidSynthSha256)
    abis.set(targetAbis)
    libs.set(fluidSynthLibs)
    outputDir.set(fluidSynthDir)
}

android {
    namespace = "com.kumhoht.chagokchagok"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kumhoht.chagokchagok"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk { abiFilters += targetAbis }
        externalNativeBuild {
            cmake {
                // FluidSynth 가 libc++_shared 를 쓰므로 우리 JNI 래퍼도 같은 STL 로 맞추고 APK 에 함께 넣는다
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DFLUIDSYNTH_DIR=" + fluidSynthDir.get().asFile.absolutePath.replace('\\', '/'),
                )
            }
        }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    // SF3 는 이미 압축된 파일이라 APK 안에서 다시 압축하지 않는다 (assets.openFd 로 크기를 읽기 위해서도 필요)
    androidResources {
        noCompress += listOf("sf3", "mp3")
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add(fluidSynthDir.get().dir("jniLibs").asFile.path)
        }
    }

    buildTypes {
        release {
            // 공모전 제출용: 디버그 키로 서명해 바로 설치·실행되게 한다 (붕붕냥과 동일).
            signingConfig = signingConfigs.getByName("debug")
            // R8 최적화는 끈다: 붕붕냥에서 켰을 때 kotlin 라이브러리 IllegalAccessError 로 실행 직후 종료됨.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Android Automotive 표준 차량 API (CarPropertyManager, CarUxRestrictionsManager)
    useLibrary("android.car")
}

// CMake 구성·빌드와 .so 병합 전에 FluidSynth 를 먼저 받는다
tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake") ||
        (name.startsWith("merge") && name.endsWith("JniLibFolders"))
    ) dependsOn(fetchFluidSynth)
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)

    // 화면 (Jetpack Compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 로컬 DB (Room): 트랙·하루 앨범
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Pleos Connect SDK (차량: 기어·깜빡이 등 / 내비: 제한속도·도착)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)

    testImplementation(libs.junit)
}
