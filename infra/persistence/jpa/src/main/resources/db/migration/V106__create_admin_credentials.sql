-- 어드민 비밀번호 로그인 자격. develop이 V105까지 적용된 뒤의 번호다.
--
-- users에 컬럼을 더하지 않고 별 테이블로 두는 이유:
--   User는 적립금·전화 HMAC·제재·PII 파기를 지는 애그리거트 루트라, 거기에 운영자 비밀번호를 넣으면
--   PII 파기 배치와 탈퇴 경로가 비밀번호를 어떻게 다뤄야 하는지가 전부 새 질문이 된다.
--   그리고 dev의 api가 ddl-auto=update라 Hibernate가 컬럼을 먼저 만들면 이 파일의 변경이
--   조용히 건너뛰어져 엔티티와 스키마가 갈린다(통합 테스트는 ddl-auto=create라 그래도 녹색이다).
--
-- 소셜 로그인과 병존한다. 한 계정이 둘 다 가질 수 있고(user_id UNIQUE), 어느 쪽으로 들어와도
-- 발급되는 토큰은 같다 — 로그인 채널과 ADMIN 자격(users.role)은 분리된 관심사다.
CREATE TABLE admin_credentials
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    login_id      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 해시. 60자이나 알고리즘 교체 여지를 둔다',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_credentials_login_id UNIQUE (login_id),
    -- 한 계정에 자격 하나. 같은 사람이 아이디를 여러 개 갖는 것을 막아 감사 로그의 actor가 갈리지 않게 한다.
    CONSTRAINT uk_admin_credentials_user_id UNIQUE (user_id)
    -- users FK를 두지 않는다. 레포 규약이 "FK 없음. BC 밖 참조도 같은 BC 참조도 인덱스만 둔다"이고
    -- (V104 머리 주석), 여기서는 그것이 규약을 넘어 동작 문제다 — users는 Flyway가 만드는 테이블이
    -- 아니라 ddl-auto 소유다. Flyway는 Hibernate보다 먼저 돌므로 빈 DB에서 이 파일이 실행될 때
    -- users가 아직 없고, FK가 있으면 MySQL 1824 "Failed to open the referenced table 'users'"로
    -- 마이그레이션 전체가 멈춘다(실측: CI test 잡이 그 오류로 실패했다).
    -- user_id는 값 참조이고 위 UNIQUE가 인덱스를 겸한다.
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
