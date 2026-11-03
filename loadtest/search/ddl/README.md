# 측정용 DDL

`run.sh --ddl "id ..."`가 러너에서 적용하는 스키마 변경이다. 측정 중 api는 Flyway와 `ddl-auto`를 끄고 뜨므로 스키마는 이 파일로만 바꾼다.

- 파일 이름은 `<id>.sql`이다. id는 단계 ID로 시작하고, 한 단계에 변경이 여럿이면 `SEARCH-T02-a`처럼 뒤에 붙인다.
- 각 파일에는 같은 변경을 담은 Flyway 마이그레이션 파일과 똑같은 문장을 둔다. 한쪽을 고치면 다른 쪽도 같이 고친다.
- `run.sh`는 적용한 id와 파일 sha256을 `perf_applied_ddl`에 적는다. 적용한 뒤 파일이 바뀌거나 `--ddl`에 없는 id가 적용돼 있으면 종료 코드 4로 멈춘다.
- 되돌리는 문장은 `results/rollback/<id>.sql`에 두고, 마지막 문장으로 `perf_applied_ddl`에서 그 id 행을 지운다.
