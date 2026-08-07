// 어드민 실행 모듈이자 조립 지점 — 팀 결정에 따라 Java(+Lombok)로 작성한다.
// api와 같은 웹 규격(web-common)을 쓰되, 어드민이 소비하는 조각만 조립한다.
plugins { id("aechak.java-spring-boot-app") }
dependencies {
    implementation(project(":web-common"))
    implementation(project(":web-security"))            // 토큰 검증·상태 필터 등 판단 부품 — 정책 조립(SecurityConfig)은 admin 소유
    implementation(project(":pii"))                     // PII 암호화 엔진+키 조립 — 스캔(com.aechak.pii)으로 활성화
    implementation(project(":application"))
    implementation(project(":jpa-persistence"))         // 리포지토리·UserStatusReader 어댑터 조립
    implementation(project(":s3-client"))               // 서류 다운로드 URL 발급 어댑터
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    runtimeOnly(libs.kotlin.reflect)                    // Spring(Data)·Jackson의 Kotlin 모듈 클래스 리플렉션 지원에 런타임 필수
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)          // Kotlin 모듈 DTO(에러 봉투 등) 필드명 보존 — admin 자체 DTO는 Java라 불필요하지만 공용 Kotlin DTO 직렬화에 필요
    implementation(libs.spring.boot.starter.validation) // Request dto의 @Valid 형식 검증
    implementation(libs.spring.boot.starter.data.jpa)   // JPA 자동 구성 — persistence 모듈의 리포지토리 활성화
    implementation(libs.spring.boot.starter.oauth2.resource.server) // api가 발급한 자체 RS256 토큰의 검증 필터
    implementation(libs.springdoc.webmvc.ui)            // OpenAPI 3 문서 + Swagger UI
    implementation(libs.spring.boot.starter.actuator)   // ALB 헬스체크 /actuator/health (health만 노출)
    implementation(platform(libs.spring.cloud.aws.bom)) // Spring Cloud AWS 계열 버전 정렬
    implementation(libs.spring.cloud.aws.starter.parameter.store) // dev 설정을 부팅 시 SSM에서 로드 — import 미선언 프로파일에선 비활성
    runtimeOnly(libs.mysql.connector.j)                 // DB 마이그레이션은 api만 수행 — admin은 접속만 한다
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers) // @ServiceConnection 없이 companion start 방식(70 §9) — api 결
    testImplementation(libs.testcontainers.mysql)       // 통합 테스트 DB는 실 MySQL 컨테이너. H2 폴백 금지
}
