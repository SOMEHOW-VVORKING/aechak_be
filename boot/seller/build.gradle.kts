// 셀러센터 실행 모듈이자 조립 지점. api와 같은 층위 — 셀러 웹이 쓰는 것만 조립한다.
// social-client가 없는 이유: 셀러 EP가 스캔하는 패키지가 소셜 토큰 검증 포트를 쓰지 않는다.
// kafka는 상품 등록·수정이 검수 요청을 발행하면서 들어왔다. 발행 쪽(publisher)만 조립하고
// 컨슈머는 조립하지 않는다 — 판정 수신은 api가 소유한다.
plugins { id("aechak.spring-boot-app") }
dependencies {
    implementation(project(":web-common"))
    implementation(project(":web-security"))            // JWT 디코더·상태 필터 — 정책 조립(SecurityConfig)은 이 모듈 소유
    implementation(project(":pii"))                     // PII 암호화 엔진+키 조립 — 스캔(com.aechak.pii)으로 활성화
    implementation(project(":application"))
    implementation(project(":jpa-persistence"))         // 도메인 포트의 구현체 조립
    implementation(project(":sms-client"))              // application.user 스캔이 전화 인증(SmsSender)을 포함
    implementation(project(":redis"))                   // 인증 코드 저장소 어댑터
    implementation(project(":s3-client"))               // 서류 승격 어댑터
    implementation(project(":ai-client"))               // AI 폼 초안 호출 어댑터
    implementation(project(":kafka"))                   // 검수 요청 발행 — 아웃박스 경유(MessagePublisher 어댑터)
    implementation(project(":message"))                 // 검수 요청 메시지 계약
    implementation(libs.spring.boot.starter.kafka)      // KafkaTemplate 자동 구성. 테스트는 api testFixtures가 끌고 와 빠져도 통과함
    implementation(libs.jackson.databind)               // 엔벨로프 매퍼(tools.jackson) 타입을 조립에서 지목
    implementation(kotlin("reflect"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.spring.boot.starter.actuator)
    implementation(platform(libs.spring.cloud.aws.bom)) // Spring Cloud AWS 계열 버전 정렬
    implementation(libs.spring.cloud.aws.starter.parameter.store) // dev 설정을 부팅 시 SSM에서 로드 — import 미선언 프로파일에선 비활성
    // flyway 미포함 — 스키마 관리는 api가 유일 소유자. seller 단독 배포 시 새 마이그레이션은 api 선배포가 전제.
    runtimeOnly(libs.mysql.connector.j)
    testImplementation(testFixtures(project(":api")))   // 통합 테스트 공용 지원(IntegrationTestBase 등) — docs/70 §9
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.awaitility)                 // 품절 전환 리스너가 별도 스레드라 비동기 단언 필요
}
