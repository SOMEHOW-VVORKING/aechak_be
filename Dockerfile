# API 서버 이미지 — 멀티스테이지: JDK로 bootJar를 빌드하고 런타임엔 JRE만 담는다.
# 빌드: docker build -t aechak-api .   (테스트는 CI 소관이라 이미지 빌드에선 제외)
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY . .
# --mount=cache: 의존성·래퍼 재다운로드 방지(로컬 반복 빌드용, CI에선 무시돼도 무해)
RUN --mount=type=cache,target=/root/.gradle ./gradlew :api:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Seoul
# bootJar 산출물은 api.jar 고정(api-plain.jar는 실행 불가한 라이브러리 jar라 제외)
COPY --from=builder /workspace/boot/api/build/libs/api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
