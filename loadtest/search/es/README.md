SEARCH-T08 시연에서 nori 형태소 분석기를 비교할 때만 쓰는 이미지입니다.
측정 계약 경로는 ngram 분석기를 쓰는 기본 이미지(`docker.elastic.co/elasticsearch/elasticsearch:9.2.6`)이고 이 이미지는 그 경로에 들어가지 않습니다.
빌드는 `docker build -t aechak-perf-es-nori:9.2.6 loadtest/search/es`로 합니다.
