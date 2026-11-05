import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 개발용 도구: 사운드폰트에서 필요한 프리셋만 추출 (앱 APK 에는 들어가지 않음)
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
    mainClass.set("com.kumhoht.todaysost.tools.sf2.MainKt")
    // 원본 SF2(약 141MB)를 통째로 메모리에 올린다
    applicationDefaultJvmArgs = listOf("-Xmx2g", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

// 상대 경로를 프로젝트 루트 기준으로 쓰기 위해
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
