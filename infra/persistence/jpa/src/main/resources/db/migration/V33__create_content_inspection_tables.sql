-- 콘텐츠 검수 기록. 상품(노출 전 관문)과 리뷰(사후 제재)가 같은 테이블을 쓰고 정책만 갈린다.
-- 번호 주의: develop이 V32까지 올라가 있어 그 다음 번호를 받았다. V30은 비어 있지만 쓰지 않는다 --
-- out-of-order가 꺼져 있어서, 이미 적용된 V31·V32보다 낮은 번호를 뒤늦게 올리면 부팅 검증이 거부한다.
-- 머지 순서가 바뀌면 다시 따야 한다. 머지된 뒤에는 번호도 내용도 고칠 수 없다.
-- target_ref·product_id는 값 참조라 FK 없음(레포 규약). created_at/updated_at은 BaseEntity가 채움.
--
-- 설계 핵심 셋
--   (1) 처리 상태(processing_state)와 판정 값(judgment)을 분리한다. 하나로 묶으면 FLAG·FAILED 행이
--       타임아웃 조회에서 빠져 영구 보류가 된다.
--   (2) 같은 내용을 두 번 판정하지 않는다 — (target_type, target_ref, content_hash, policy_version)이 유일키다.
--       정책을 고치면 policy_version이 올라가 옛 판정을 재사용하지 않는다.
--   (3) 임차(lease)로 다중 인스턴스 재시도를 직렬화한다. 최초 요청 시각은 갱신하지 않는다 —
--       requested_at을 재시도마다 밀면 타임아웃이 영원히 오지 않는다.
CREATE TABLE IF NOT EXISTS content_inspections
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    target_type        VARCHAR(20)  NOT NULL,              -- PRODUCT / REVIEW
    target_ref         BIGINT       NOT NULL,              -- 상품 id 또는 리뷰 id
    target_version_no  INT          NULL,                  -- 상품만. 늦게 온 판정이 최신 버전을 덮지 않게 하는 가드
    content_hash       CHAR(64)     NOT NULL,              -- 판정 대상 정규화 본문의 SHA-256
    policy_version     VARCHAR(40)  NOT NULL,              -- 정책 문구 버전. 재사용 키의 일부
    prompt_version     VARCHAR(40)  NULL,                  -- 판정 시 쓴 프롬프트 버전
    processing_state   VARCHAR(20)  NOT NULL,              -- QUEUED / IN_FLIGHT / SETTLED
    judgment           VARCHAR(20)  NULL,                  -- PASS / FLAG / UNJUDGED / FAILED. NULL이면 아직 판정 없음
    reason_code        VARCHAR(60)  NULL,
    reason_summary     VARCHAR(500) NULL,
    seller_notice      TEXT         NULL,                  -- 셀러에게 보여 줄 수정 제안. 리뷰는 NULL
    attempt            INT          NOT NULL,              -- 실제 재요청 횟수
    lease_owner        VARCHAR(64)  NULL,                  -- 선점한 인스턴스 식별자
    lease_expires_at   DATETIME(6)  NULL,                  -- 선점 만료. 죽은 인스턴스의 건을 되찾는 수단
    next_retry_at      DATETIME(6)  NULL,                  -- 재요청 예정 시각. requested_at과 분리한다
    requested_at       DATETIME(6)  NOT NULL,              -- 최초 요청 시각. 재시도로 갱신하지 않는다
    settled_at         DATETIME(6)  NULL,
    version            INT          NOT NULL,              -- 낙관적 락. 판정 적용과 타임아웃 승격의 경쟁 심판
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_content_inspections_target_hash
        UNIQUE (target_type, target_ref, content_hash, policy_version)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 타임아웃 승격과 재요청 스윕이 쓰는 조회 경로
CREATE INDEX ix_content_inspections_state_retry
    ON content_inspections (processing_state, next_retry_at);

-- 셀러·구매자 화면이 대상으로 최신 검수를 찾는 경로
CREATE INDEX ix_content_inspections_target
    ON content_inspections (target_type, target_ref, id);

-- 관점별 결과. 한 검수에 관점마다 1행이고 재전달 시 누적이 아니라 갱신한다.
-- 통과와 미검사를 outcome으로 구분하는 것이 이 테이블의 존재 이유다 —
-- 둘을 뭉개면 모델이 보지 못한 것이 문제 없음으로 기록된다.
CREATE TABLE IF NOT EXISTS inspection_findings
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    inspection_id BIGINT       NOT NULL,
    perspective   VARCHAR(30)  NOT NULL,              -- ALL / DISPLAY_ADS / CONDUCT_TRADE / RULE
    outcome       VARCHAR(20)  NOT NULL,              -- VIOLATION / CLEAR / UNCHECKED
    skip_reason   VARCHAR(60)  NULL,                  -- UNCHECKED일 때의 이유
    violations    JSON         NULL,                  -- [{category, spanStart, spanEnd, quote, suggestion, confidence}]
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inspection_findings_perspective UNIQUE (inspection_id, perspective)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 모델 호출 원장. 판정 1건이 여러 호출을 낳는다(관점 팬아웃, 재시도, 등급 승격).
-- call_id로 유일성을 걸고 request_id는 따로 둔다 — 논리 요청 하나로 묶어 버리면
-- 재시도와 승격 비용이 한 행에 묻혀 실제 지출을 되살릴 수 없다.
-- 입력 토큰을 셋으로 나눠 적는 이유도 같다. 전체 입력이 세 값의 합이다.
CREATE TABLE IF NOT EXISTS ai_call_ledger
(
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    request_id                  VARCHAR(100) NOT NULL,         -- 논리 요청(검수 1건)
    call_id                     VARCHAR(100) NOT NULL,         -- 실제 호출 1회
    inspection_id               BIGINT       NULL,
    purpose                     VARCHAR(40)  NOT NULL,         -- PRODUCT_INSPECTION / REVIEW_INSPECTION / CRITIC / REWRITE
    perspective                 VARCHAR(30)  NULL,
    model_id                    VARCHAR(100) NOT NULL,
    prompt_version              VARCHAR(40)  NOT NULL,
    input_tokens                INT          NOT NULL,
    cache_read_input_tokens     INT          NOT NULL,
    cache_creation_input_tokens INT          NOT NULL,
    output_tokens               INT          NOT NULL,
    cost_micro_usd              BIGINT       NOT NULL,         -- 백만분의 1 USD. 센트 단위로는 건당 비용이 0으로 눌린다
    latency_ms                  INT          NOT NULL,
    outcome                     VARCHAR(30)  NOT NULL,         -- SUCCESS / TIMEOUT / RATE_LIMITED / BUDGET_EXHAUSTED / REFUSED / SCHEMA_INVALID / ERROR
    created_at                  DATETIME(6)  NOT NULL,
    updated_at                  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_call_ledger_call UNIQUE (call_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 일일 비용 집계와 예산 경보가 쓰는 경로
CREATE INDEX ix_ai_call_ledger_created ON ai_call_ledger (created_at, model_id);
