CREATE TABLE IF NOT EXISTS categories
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT       NULL,
    depth      INT          NOT NULL,
    name       VARCHAR(100) NOT NULL,
    icon_url   VARCHAR(512) NULL,
    status     VARCHAR(30)  NOT NULL,
    sort_order INT          NOT NULL,
    version    BIGINT       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 시드: 3단계 마스터(대>중>소). id를 자릿수로 고정해 계층을 인코딩한다 —
-- 대분류 n, 중분류 n0+m, 소분류 (n0+m)0+k. parent_id는 앞자리에서 자명하게 도출된다.
-- 카테고리는 런타임에 추가되지 않는 시드 전용 데이터라 명시 id 고정이 안전하다.
-- sort_order는 같은 부모 아래 형제 노출 순서다.
INSERT INTO categories (id, parent_id, depth, name, icon_url, status, sort_order, version, created_at, updated_at)
VALUES
-- ── 대분류 (depth 1) ──
(1, NULL, 1, '강아지', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(2, NULL, 1, '고양이', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(3, NULL, 1, '공통', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),

-- ── 강아지 중분류 (depth 2) ──
(11, 1, 2, '사료', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(12, 1, 2, '간식', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(13, 1, 2, '영양제', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
(14, 1, 2, '용품', NULL, 'ACTIVE', 4, 0, NOW(6), NOW(6)),
(15, 1, 2, '산책/외출', NULL, 'ACTIVE', 5, 0, NOW(6), NOW(6)),
(16, 1, 2, '의류/패션', NULL, 'ACTIVE', 6, 0, NOW(6), NOW(6)),
-- 강아지 > 사료
(111, 11, 3, '건식사료', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(112, 11, 3, '습식/소프트사료', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(113, 11, 3, '동결건조/생식', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
(114, 11, 3, '처방식사료', NULL, 'ACTIVE', 4, 0, NOW(6), NOW(6)),
-- 강아지 > 간식
(121, 12, 3, '껌/덴탈', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(122, 12, 3, '건조/수제간식', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(123, 12, 3, '캔/파우치', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
(124, 12, 3, '트릿/져키', NULL, 'ACTIVE', 4, 0, NOW(6), NOW(6)),
-- 강아지 > 영양제
(131, 13, 3, '종합영양제', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(132, 13, 3, '관절/뼈', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(133, 13, 3, '피부/모질', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
(134, 13, 3, '유산균/장건강', NULL, 'ACTIVE', 4, 0, NOW(6), NOW(6)),
-- 강아지 > 용품
(141, 14, 3, '장난감', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(142, 14, 3, '하우스/방석', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(143, 14, 3, '훈련용품', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
-- 강아지 > 산책/외출
(151, 15, 3, '하네스/가슴줄', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(152, 15, 3, '목줄/리드줄', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(153, 15, 3, '산책가방', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
(154, 15, 3, '이동장/유모차', NULL, 'ACTIVE', 4, 0, NOW(6), NOW(6)),
-- 강아지 > 의류/패션
(161, 16, 3, '상의/아우터', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(162, 16, 3, '신발/양말', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(163, 16, 3, '악세서리', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),

-- ── 고양이 중분류 (depth 2) ──
(21, 2, 2, '사료', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(22, 2, 2, '간식', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(23, 2, 2, '영양제', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
(24, 2, 2, '용품', NULL, 'ACTIVE', 4, 0, NOW(6), NOW(6)),
(25, 2, 2, '모래/화장실', NULL, 'ACTIVE', 5, 0, NOW(6), NOW(6)),
-- 고양이 > 사료
(211, 21, 3, '건식사료', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(212, 21, 3, '습식/파우치', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(213, 21, 3, '처방식사료', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
-- 고양이 > 간식
(221, 22, 3, '츄르/캔', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(222, 22, 3, '건조간식', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(223, 22, 3, '캣닢/트릿', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
-- 고양이 > 영양제
(231, 23, 3, '종합영양제', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(232, 23, 3, '헤어볼/모질', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(233, 23, 3, '신장/비뇨기', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
(234, 23, 3, '유산균/장건강', NULL, 'ACTIVE', 4, 0, NOW(6), NOW(6)),
-- 고양이 > 용품
(241, 24, 3, '장난감', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(242, 24, 3, '스크래처/캣타워', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(243, 24, 3, '하우스/방석', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
-- 고양이 > 모래/화장실
(251, 25, 3, '고양이모래', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(252, 25, 3, '화장실', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(253, 25, 3, '모래삽/기타', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),

-- ── 공통 중분류 (depth 2) ──
(31, 3, 2, '미용/목욕', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(32, 3, 2, '위생용품', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(33, 3, 2, '리빙/가전', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
-- 공통 > 미용/목욕
(311, 31, 3, '샴푸/린스', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(312, 31, 3, '미용도구/이발기', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(313, 31, 3, '구강/덴탈케어', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
-- 공통 > 위생용품
(321, 32, 3, '배변패드/배변판', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(322, 32, 3, '탈취제/소독제', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(323, 32, 3, '물티슈/티슈', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
(324, 32, 3, '청소/세정', NULL, 'ACTIVE', 4, 0, NOW(6), NOW(6)),
-- 공통 > 리빙/가전
(331, 33, 3, '식기/급식기', NULL, 'ACTIVE', 1, 0, NOW(6), NOW(6)),
(332, 33, 3, '자동급식기', NULL, 'ACTIVE', 2, 0, NOW(6), NOW(6)),
(333, 33, 3, '정수기/급수기', NULL, 'ACTIVE', 3, 0, NOW(6), NOW(6)),
(334, 33, 3, '펫드라이룸', NULL, 'ACTIVE', 4, 0, NOW(6), NOW(6)),
(335, 33, 3, '홈카메라', NULL, 'ACTIVE', 5, 0, NOW(6), NOW(6));
