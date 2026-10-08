-- 아웃박스 파티션 키 컬럼 개명 (SCRUM-201)
-- 이름은 애그리거트 id로 읽히는데 실제로 담기는 값은 Kafka 파티션 키다.
-- 리뷰 작성 메시지는 aggregate_type이 review이면서 파티션 키로는 product_id를 넣는다.
ALTER TABLE outbox_message
    RENAME COLUMN aggregate_id TO ordering_key;

-- 아직 발행되지 않은 행의 엔벨로프 필드명도 함께 맞춘다.
-- 옛 이름이 남은 채로 재발행되면 Envelope 역직렬화에서 필드가 없어 컨슈머가 읽지 못한다.
UPDATE outbox_message
SET payload = JSON_REMOVE(
        JSON_SET(payload, '$.orderingKey', JSON_UNQUOTE(JSON_EXTRACT(payload, '$.aggregateId'))),
        '$.aggregateId'
              )
WHERE JSON_CONTAINS_PATH(payload, 'one', '$.aggregateId');
