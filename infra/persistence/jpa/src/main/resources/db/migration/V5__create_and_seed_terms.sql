CREATE TABLE IF NOT EXISTS terms
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    type         ENUM ('AGE_OVER_14','LOCATION','MARKETING','PRIVACY','SERVICE') NOT NULL,
    is_required  BIT          NOT NULL,
    version      VARCHAR(20)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    body         TEXT         NOT NULL,
    effective_at DATETIME(6) NOT NULL,
    is_active    BIT          NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_terms_type_version (type, version)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 시드: 테이블이 비어있을 때만 전체 삽입
-- 본문은 MVP 임시 문안 — 정책 확정 시 새 버전 행으로 교체(약관은 append-only, UPDATE 금지)
INSERT INTO terms (type, is_required, version, title, body, effective_at, is_active, created_at, updated_at)
SELECT s.type, s.is_required, s.version, s.title, s.body, s.effective_at, s.is_active, NOW(6), NOW(6)
FROM (
    SELECT 1 AS ord, 'SERVICE' AS type, 1 AS is_required, '1.0' AS version,
           '서비스 이용약관' AS title,
           '애착 서비스의 이용 조건과 회원의 권리·의무를 규정합니다. 회원은 관계 법령과 본 약관을 준수하여야 하며, 서비스의 상세 이용 정책은 앱 내 공지를 따릅니다.' AS body,
           '2026-07-01 00:00:00' AS effective_at, 1 AS is_active
    UNION ALL SELECT 2, 'PRIVACY', 1, '1.0',
           '개인정보 수집·이용 동의',
           '회원 식별, 서비스 제공, 고객 문의 대응을 위해 필요한 최소한의 개인정보를 수집·이용합니다. 수집 항목·보유 기간 등 상세 내용은 개인정보 처리방침을 따릅니다.',
           '2026-07-01 00:00:00', 1
    UNION ALL SELECT 3, 'LOCATION', 0, '1.0',
           '위치기반서비스 이용약관',
           '위치 기반 기능(주변 매장 탐색 등) 제공을 위해 위치정보를 수집·이용합니다. 동의하지 않아도 서비스 이용은 가능하나 위치 기반 기능은 제한됩니다.',
           '2026-07-01 00:00:00', 1
    UNION ALL SELECT 4, 'MARKETING', 0, '1.0',
           '마케팅 정보 수신 동의',
           '이벤트·혜택 등 광고성 정보를 앱 푸시 등으로 받아보는 것에 동의합니다. 동의하지 않아도 서비스 이용에는 제한이 없으며, 언제든지 철회할 수 있습니다.',
           '2026-07-01 00:00:00', 1
) s
WHERE NOT EXISTS (SELECT 1 FROM terms)
ORDER BY s.ord;
