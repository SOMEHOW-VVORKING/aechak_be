-- 리뷰 수 내림차순 인덱스. 인기순 정렬 키는 LEFT JOIN 위의 CASE 식이다
ALTER TABLE product_stats ADD INDEX ix_product_stats_review (review_count DESC, product_id DESC);
