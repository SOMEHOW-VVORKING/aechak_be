# 빌드 인프라 — 버전 카탈로그 + 컨벤션 플러그인 (50-build)

> 이 문서는 00 문서 §2 의존 매트릭스의 **집행 도구**다. 방법론과 최소 시작셋만 정의하며,
> 개별 의존성을 미리 나열하지 않는다.
>
> 역할 분담 (전역 불변):
> - **gradle/libs.versions.toml** = 좌표·버전의 단일 출처. 값만 존재, 로직 없음.
> - **build-logic 컨벤션 플러그인** = 플러그인 적용 + 공통 설정. 4종만 (§4).
>   컨벤션이 의존성을 제공하는 예외는 두 곳뿐: ①spring-boot BOM(platform) ②jpa-entity의 persistence-api.
> - **각 모듈 build.gradle.kts** = 컨벤션 id 1개 + 자기 의존성 목록(`libs.*`). **의존성은 여기 보여야 한다.**
> - **YAGNI**: 카탈로그는 최소로 시작한다. 새 의존성은 **그것을 사용하는 코드가 생기는 커밋에서** §6 절차로
>   함께 등록한다. 무엇이 허용되는지의 출처는 00 §2 매트릭스 — 매트릭스에 없는 의존은 등록 자체를 반려.
> - buildSrc 금지(수정 시 전 모듈 캐시 무효화) → **build-logic includeBuild** 채택.
>   allprojects/subprojects 블록 금지(적용 내역이 모듈 파일에서 안 보임).

---

## 1. 디렉토리 구조

```
aechak/
├── gradle/
│   └── libs.versions.toml           # §2
├── build-logic/                     # 컨벤션 플러그인 — includeBuild, 실행 코드 아님
│   ├── settings.gradle.kts          # 루트 카탈로그 공유 (§3)
│   ├── build.gradle.kts             # 적용할 플러그인 "구현체"를 classpath에 (§3)
│   └── src/main/kotlin/
│       ├── aechak.kotlin-common.gradle.kts     # 전 모듈 베이스
│       ├── aechak.jpa-entity.gradle.kts        # domain 전용
│       ├── aechak.spring-library.gradle.kts    # web-common / application / infra/*
│       └── aechak.spring-boot-app.gradle.kts   # 실행 모듈(api·seller-api·batch)
├── settings.gradle.kts              # §3
└── (모듈들 — §5)
```

## 2. gradle/libs.versions.toml — 최소 시작셋

빌드가 **성립**하는 데 필요한 것만 등록한다. 이것이 초기 카탈로그의 전부다.

```toml
# 규칙: 모듈 build.gradle.kts에 좌표/버전 문자열 하드코딩 금지. 반드시 여기 등록 후 libs.* 로 참조.
# 새 항목 추가는 §6 절차로 — 스캐폴딩 시점에 미리 채우지 않는다.

[versions]
kotlin = "2.2.0"                 # 확정: 2.2 계열 — Boot 4.0/Framework 7의 Kotlin baseline. TODO: 최신 2.2.x 패치
spring-boot = "4.0.0"            # 확정: Boot 4.0 (Framework 7 / Jakarta EE 11 / Servlet 6.1). TODO: 최신 4.0.x 패치
jakarta-persistence = "3.2.0"    # JPA 3.2 (Jakarta EE 11) — Boot 4 BOM이 고정하는 버전과 일치 유지 (domain은 BOM 밖이라 직접 관리)

[libraries]
spring-boot-bom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot" }
jakarta-persistence = { module = "jakarta.persistence:jakarta.persistence-api", version.ref = "jakarta-persistence" }

# --- build-logic 전용: 컨벤션이 적용할 플러그인의 "구현체" (§3 함정 ②의 이유로 [plugins] 대신 이 방식) ---
gradleplugin-kotlin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
gradleplugin-kotlin-allopen = { module = "org.jetbrains.kotlin:kotlin-allopen", version.ref = "kotlin" }
gradleplugin-kotlin-noarg = { module = "org.jetbrains.kotlin:kotlin-noarg", version.ref = "kotlin" }
gradleplugin-spring-boot = { module = "org.springframework.boot:spring-boot-gradle-plugin", version.ref = "spring-boot" }
```

이후 등록 형식 규칙: **Spring 계열은 버전 없이** 등록한다(BOM이 해석 — `spring-web = { module = "org.springframework:spring-web" }`).
**비 Spring은 [versions]에 버전을 명시**한다. 버전 없는 항목은 BOM platform이 적용된 모듈(spring-library 계열)에서만 해석된다(§7-⑤).

## 3. settings / build-logic 연결

```kotlin
// (루트) settings.gradle.kts
pluginManagement {
    includeBuild("build-logic")              // 컨벤션 플러그인 연결 — buildSrc 아님
    repositories { gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
    // gradle/libs.versions.toml은 관례 경로라 자동 인식 — 별도 등록 불필요
}
rootProject.name = "aechak"                  // A-4 확정
include(
    "common", "web-common",
    "domain", "application",
    // "message",                            // PENDING(A-2)
    "api", "seller-api", "batch",    // "admin" — A-5: MVP 제외
    "jpa-persistence", "pg-client",     // A-1 결정(L2). kafka·redis는 어댑터 생길 때 추가
)
// boot/·infra/{분류}는 모듈이 아닌 폴더 — 모듈 이름은 평평하게, projectDir로 위치만 매핑
project(":api").projectDir = file("boot/api")
project(":seller-api").projectDir = file("boot/seller")
project(":batch").projectDir = file("boot/batch")
project(":jpa-persistence").projectDir = file("infra/persistence/jpa")
project(":pg-client").projectDir = file("infra/client/pg-client")
```

```kotlin
// build-logic/settings.gradle.kts
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }   // 루트 카탈로그 공유
    }
}
rootProject.name = "build-logic"
```

```kotlin
// build-logic/build.gradle.kts
plugins { `kotlin-dsl` }                     // precompiled script plugin 작성용

// [함정 ①의 반대면] 여기는 "일반 빌드 스크립트"라 libs.* 타입세이프 접근자가 동작한다.
// 동작하지 않는 곳은 src/main/kotlin/*.gradle.kts(precompiled script) 내부다 — §4의 우회 참조.
dependencies {
    // [함정 ②] precompiled script의 plugins { id("...") }에는 버전 표기가 금지되어 있다.
    // 버전은 아래 "구현체" classpath가 결정한다 — 카탈로그 [plugins] alias 대신 이 방식을 쓰는 이유.
    implementation(libs.gradleplugin.kotlin)
    implementation(libs.gradleplugin.kotlin.allopen)
    implementation(libs.gradleplugin.kotlin.noarg)
    implementation(libs.gradleplugin.spring.boot)
}
```

## 4. 컨벤션 플러그인 4종

```kotlin
// aechak.kotlin-common.gradle.kts — 전 모듈 베이스
plugins {
    id("org.jetbrains.kotlin.jvm")                    // 버전 없음 — build-logic classpath가 결정 (§3)
}

kotlin {
    jvmToolchain(21)                                  // 확정: JDK 21 (Boot 4 요구는 17+) — mise/IntelliJ 툴체인과 일치시킬 것
    compilerOptions {
        // Boot 4 + Kotlin 2.2 공식 권장 플래그 — 어노테이션 기본 타겟 규칙 변경에 따른 경고 방지.
        // (Spring 7의 null 안전은 JSpecify로 전환됐고 Kotlin 2.1+가 네이티브 강제하므로 별도 플래그 불필요)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
        freeCompilerArgs.add("-Xjsr305=strict")       // JSR-305를 쓰는 서드파티 라이브러리 대응용으로 유지
    }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

dependencies {
    "testImplementation"(kotlin("test"))              // 카탈로그 불필요 — 버전이 kotlin 플러그인을 따라감
}
```

```kotlin
// aechak.jpa-entity.gradle.kts — domain 전용 (10 문서 §5의 빌드 구현)
plugins {
    id("aechak.kotlin-common")
    id("org.jetbrains.kotlin.plugin.jpa")             // @Entity 계열 no-arg 생성자 자동
    id("org.jetbrains.kotlin.plugin.allopen")         // [함정 ③] plugin.jpa는 open을 안 해준다 — 별도 적용
}

allOpen {                                             // lazy 프록시용 open 대상
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

// [함정 ①] precompiled script 안에서는 libs.* 접근자가 생성되지 않는다(알려진 제약) — 카탈로그 API로 우회.
// 이 우회가 지저분하므로 컨벤션의 카탈로그 참조는 예외 ①② 두 곳으로만 제한한다.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    // 예외 ②: 이 컨벤션의 정의 자체가 "persistence-api 위의 엔티티 모듈"이므로 여기서 제공
    // (api — 소비 모듈이 도메인 클래스의 어노테이션 타입을 볼 수 있어야 함)
    "api"(libs.findLibrary("jakarta-persistence").get())
}
```

```kotlin
// aechak.spring-library.gradle.kts — web-common / application / infra/*
plugins {
    id("aechak.kotlin-common")
    id("org.jetbrains.kotlin.plugin.spring")          // @Component/@Service/@Transactional 클래스 자동 open
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")   // 함정 ① 우회
dependencies {
    // 예외 ①: BOM은 전 Spring 모듈 공통 — 버전 없는 libs.spring.* 항목이 여기서 해석된다
    "implementation"(platform(libs.findLibrary("spring-boot-bom").get()))
    "testImplementation"(platform(libs.findLibrary("spring-boot-bom").get()))
}
```

```kotlin
// aechak.spring-boot-app.gradle.kts — 실행 모듈(api·batch)
plugins {
    id("aechak.spring-library")                   // 컨벤션은 컨벤션을 중첩 적용할 수 있다
    id("org.springframework.boot")                    // bootJar/bootRun
}
// io.spring.dependency-management는 쓰지 않는다 — BOM은 이미 platform()으로 적용(Gradle 네이티브 방식)
```

## 5. 모듈 → 컨벤션 매핑과 작성 규칙

| 모듈 | 컨벤션 | project 의존 (00 §2 매트릭스) |
| --- | --- | --- |
| common | aechak.kotlin-common | 없음 (규약) |
| domain | aechak.jpa-entity | api(":common") |
| web-common | aechak.spring-library | api(":common") |
| application | aechak.spring-library | api(":domain") |
| message (PENDING A-2) | aechak.kotlin-common | 없음 |
| infra/* | aechak.spring-library | implementation(":application") |
| api / admin | aechak.spring-boot-app | ":web-common", ":application", 필요한 infra 모듈(":jpa-persistence" 등) |
| batch | aechak.spring-boot-app | ":application", 필요한 infra 모듈 — **web-common 금지** |

대표 예시 (전 모듈이 이 꼴이다 — 컨벤션 1줄 + 의존 목록):

```kotlin
// common/build.gradle.kts — 의존성 없음이 규약 (00 §2)
plugins { id("aechak.kotlin-common") }
```

```kotlin
// domain/build.gradle.kts
plugins { id("aechak.jpa-entity") }
dependencies {
    api(project(":common"))                           // BusinessException/ErrorCode
}
```

```kotlin
// application/build.gradle.kts — "라이브러리 의존이 있는 모듈"의 대표 예시
plugins { id("aechak.spring-library") }
dependencies {
    api(project(":domain"))       // SearchQuery/Command가 domain 타입(OrderStatus 등)을 노출하므로 api
    implementation(libs.spring.context)               // ← 이 2개는 00 §2 허용 목록의 항목을
    implementation(libs.spring.tx)                    //    §6 절차로 toml에 등록한 결과다
}
```

> **나머지 모듈 작성 지시 (AI 에이전트용)**
> 위 표의 컨벤션 id와 project 의존을 그대로 적용한다. 라이브러리 의존은 미리 추가하지 말고,
> 해당 모듈에 그것을 사용하는 코드를 작성하는 시점에 00 §2 허용 목록 범위 안에서 §6 절차
> (toml 등록 → `libs.*` 참조)로 추가한다. 매트릭스에 없는 의존을 임의로 추가하지 않는다.

## 6. 운영 규칙

1. **스캐폴딩 시 카탈로그를 미리 채우지 않는다.** 의존성은 그것을 쓰는 코드가 생기는 커밋에서 함께 등록.
2. **새 라이브러리 추가 절차**: 00 §2 매트릭스 허용 여부 확인 → toml 등록(§2의 형식 규칙) → 모듈 파일에서 `libs.*` 참조. 좌표/버전 문자열 하드코딩은 리뷰 반려.
3. **버전 올리기 = toml 한 줄 수정.** 모듈 파일은 건드리지 않는다. (Renovate/Dependabot이 toml 지원 — 도입 검토 TODO)
4. **컨벤션 수정은 전 모듈에 영향** — ArchUnit 없는 이 팀에서 빌드판 common에 해당. PR 리뷰 필수, 임의 수정 금지.
5. 금지 목록: buildSrc 생성 / allprojects·subprojects 블록 / [plugins] alias로 컨벤션 내 플러그인 버전 관리 시도(함정 ②) / 컨벤션에 예외 ①② 외 의존성 추가.

## 7. 함정 노트 (구현 중 삽질 방지)

- ① precompiled script(*.gradle.kts 컨벤션) 안에서 `libs.*` 타입세이프 접근자 **불가** → `VersionCatalogsExtension.findLibrary()` 우회. build-logic/build.gradle.kts에서는 **가능**.
- ② precompiled script의 plugins 블록에 버전 표기 **금지** → 플러그인 버전은 build-logic classpath의 구현체 artifact가 결정.
- ③ plugin.jpa는 no-arg만 해결한다 — **allopen은 별도 적용**해야 lazy 프록시가 동작 (jpa-entity 컨벤션 참조).
- ④ toml 키의 `-`는 Kotlin 접근자에서 `.`으로 바뀐다 (`spring-boot-bom` → `libs.spring.boot.bom`).
- ⑤ 버전 없는 카탈로그 항목(Spring 계열)은 BOM platform이 적용된 모듈에서만 해석된다 — common/domain에서 참조하면 해석 실패.
