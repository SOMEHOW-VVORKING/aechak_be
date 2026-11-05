# 검색 부하 측정 kit의 노트북 진입점. 동작은 lib/laptop.sh에 있고 변수는 환경변수로 넘긴다
# 예: make variant STEP=SEARCH-T01 DATASET=100k VARIANT=base REV=<rev>, 명령만 보려면 DRY_RUN=1
KIT_DIR := $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
AWS_PROFILE ?= aechak
export AWS_PROFILE DATASET REV STEP VARIANT DDL ENV CELLS LOCAL QUICK SKIP_TESTS ALLOW_DIRTY \
	BEFORE_REV AFTER_REV BEFORE_DDL AFTER_DDL ENGINE LABEL RUN DRY_RUN \
	BASE_RATE FORCE MAX_VUS_CAP IDLE_RDS_CPU_MAX RAMP_FLOOR_RATES RAMP_RATES WARMUP_RATE

TARGETS := outputs jar sync up down init seed reseed manifest variant step explain equiv \
	reindex graphs grafana prom shell-runner shell-api fetch

.DEFAULT_GOAL := help
.PHONY: help $(TARGETS)

help:
	@echo "targets: $(TARGETS)"

$(TARGETS):
	@bash "$(KIT_DIR)/lib/laptop.sh" $@
