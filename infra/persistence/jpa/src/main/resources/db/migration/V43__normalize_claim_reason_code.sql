-- 클레임 요청 사유를 자유 문자열에서 고정 어휘로 좁힌다.
-- 어휘에 없는 값이 남으면 엔티티를 enum으로 읽는 순간 조회가 터지므로, 컬럼을 좁히기 전에 기존 값을 전부 어휘 안으로 옮긴다.

-- 1) 어휘·옛 표기와 뜻이 같은 값의 앞뒤 공백과 대소문자를 글자까지 맞춘다.
--    claims 콜레이션(utf8mb4_0900_ai_ci)은 대소문자를 무시해 'change_of_mind'도 아래 어휘 비교를 통과한다 —
--    글자를 맞추지 않으면 그 값이 소문자로 남아 enum 변환에서 터진다.
UPDATE claims
SET reason_code = UPPER(TRIM(reason_code))
WHERE reason_code IS NOT NULL
  AND TRIM(reason_code) IN ('CHANGE_OF_MIND', 'WRONG_OPTION', 'PRICE_MISMATCH', 'DELIVERY_DELAY',
                            'DEFECTIVE', 'WRONG_ITEM', 'NOT_AS_DESCRIBED', 'OTHER',
                            'DEFECT', 'CHANGE_MIND');

-- 2) 뜻이 하나로 읽히는 옛 표기를 새 코드로 옮긴다.
UPDATE claims
SET reason_code = 'DEFECTIVE'
WHERE reason_code = 'DEFECT';

UPDATE claims
SET reason_code = 'CHANGE_OF_MIND'
WHERE reason_code = 'CHANGE_MIND';

-- 3) 남은 값과 유형이 받지 못하는 조합을 OTHER로 접는다. 유형에 없는 코드가 남으면 셀러가 코드로 가르는
--    반품비 부담 판단이 어긋난다. 비교는 바이트 단위(utf8mb4_bin)로 한다 — 어휘와 '같다'고만 판정된 변형이
--    원래 모양으로 남으면 1)과 같은 이유로 조회가 터진다.
--    원래 문자열은 상세가 비어 있을 때만 상세로 옮겨 판단 근거를 남긴다. 공백만 있는 상세도 비어 있는 것으로 본다.
--    MySQL은 한 UPDATE의 SET을 왼쪽부터 적용하므로 reason_detail 대입은 아직 옛 reason_code를 본다.
UPDATE claims
SET reason_detail = CASE
                        WHEN TRIM(COALESCE(reason_detail, '')) = '' THEN reason_code
                        ELSE reason_detail
                    END,
    reason_code   = 'OTHER'
WHERE reason_code IS NOT NULL
  AND NOT (
    (claim_type = 'CANCEL' AND reason_code COLLATE utf8mb4_bin IN
                               ('CHANGE_OF_MIND', 'WRONG_OPTION', 'PRICE_MISMATCH', 'DELIVERY_DELAY', 'OTHER')) OR
    (claim_type = 'RETURN' AND reason_code COLLATE utf8mb4_bin IN
                               ('CHANGE_OF_MIND', 'DEFECTIVE', 'WRONG_ITEM', 'NOT_AS_DESCRIBED', 'OTHER')) OR
    (claim_type = 'EXCHANGE' AND reason_code COLLATE utf8mb4_bin IN
                                 ('DEFECTIVE', 'WRONG_ITEM', 'WRONG_OPTION', 'OTHER'))
    );

-- 4) 다른 enum 컬럼과 같은 폭으로 맞춘다. ddl-auto가 네이티브 ENUM 타입으로 만들어 둔 환경도
--    이 문장으로 VARCHAR로 되돌아간다.
ALTER TABLE claims
    MODIFY COLUMN reason_code VARCHAR(30) NULL;
