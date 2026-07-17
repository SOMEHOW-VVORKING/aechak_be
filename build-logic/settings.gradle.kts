dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }   // 루트 카탈로그를 build-logic에서도 공유
    }
}
rootProject.name = "build-logic"
