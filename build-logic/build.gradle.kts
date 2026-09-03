plugins { `kotlin-dsl` }                     // precompiled script plugin(컨벤션) 작성용

// 컨벤션(src/main/kotlin/*.gradle.kts)이 적용할 플러그인의 구현체를 classpath에 올린다.
// precompiled script의 plugins 블록에는 버전을 적을 수 없으므로, 플러그인 버전은 이 목록(카탈로그)이 결정한다.
dependencies {
    implementation(libs.gradleplugin.kotlin)
    implementation(libs.gradleplugin.kotlin.allopen)
    implementation(libs.gradleplugin.kotlin.noarg)
    implementation(libs.gradleplugin.spring.boot)
    implementation(libs.gradleplugin.ktlint)
    implementation(libs.gradleplugin.spotless)          // java-common 컨벤션이 자바 포맷(palantir) 적용
    implementation(libs.gradleplugin.ksp)               // domain 컨벤션이 QueryDSL Q클래스 생성을 적용
}
