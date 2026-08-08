# 애착(aechak) 백엔드 — 팀 공통 에이전트 규칙

Kotlin + Spring Boot 멀티모듈 백엔드. 아키텍처·계층 규칙의 정본은 `docs/00-overview.md`~`70-testing.md` — 코드 작성 전 해당 계층 문서를 먼저 읽는다.

## PR·머지 가드레일 (필수 — 위반 금지)

- **PR 생성 전 반드시 사람 확인을 받는다.** 대상 브랜치·커밋 목록·PR 제목·본문을 먼저 보여주고, 명시적 승인이 있을 때만 `gh pr create` 등 PR 생성 도구를 실행한다. 승인 없이 PR을 만들지 않는다.
- **PR 머지는 절대 에이전트가 하지 않는다.** 머지는 리뷰(1인 이상) 후 사람이 직접 한다. `gh pr merge`·GitHub API 머지 호출 금지.
- **요청 범위만 수행한다.** "오류 잡아줘"는 코드 수정까지다 — 커밋·push·PR 생성은 각각 별도 지시가 있을 때만 진행한다.
- `develop`·`main`에 직접 push 금지. 커밋·PR에 Co-Authored-By 등 AI 흔적을 남기지 않는다.

## 레포 구성 (GitHub org: `SOMEHOW-VVORKING`)

| 레포 | 역할 |
|---|---|
| `aechak_be` | 백엔드 (이 레포) |
| `aechak_fe` | 프론트엔드 — API 연동 시 계약(contracts) 기준으로 맞춘다 |
| `team` | 명세·계약 SSOT — `spec/changes/{기능ID}/design.md`·`tasks.md`, `spec/contracts/{도메인}.yaml` |

계약·design 문서는 `team` 레포가 원천이다. 이 레포로 복사하지 말고(사본 드리프트) 원본을 읽는다.

## 브랜치·커밋 규칙

- `develop` 기반 `feature/SCRUM-{티켓번호}` 브랜치, PR은 `develop` 대상
- 커밋 메시지 `[SCRUM-n] type: 내용` (build성 변경은 chore)
- 에러코드는 도메인별 enum이 SSOT — 셀러 10000 · 인증/인가 20000 · 사용자 30000 · 파일 110000 · 서버 공통 90000
