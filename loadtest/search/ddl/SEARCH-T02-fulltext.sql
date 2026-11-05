-- 상품명 FULLTEXT ngram 인덱스. 처음 만들 때 테이블을 다시 만들고 숨은 FTS_DOC_ID 열을 더한다
ALTER TABLE products ADD FULLTEXT INDEX ft_products_name (name) WITH PARSER ngram;
