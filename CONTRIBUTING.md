# 기여 가이드

## 코드 스타일 (ktlint)

이 프로젝트는 [ktlint](https://pinterest.github.io/ktlint/)로 코틀린 코드 스타일을 강제한다.
룰 설정은 `.editorconfig`에 작성되어 있다.

### 1. CI (강제 게이트) — 자동

`.github/workflows/ktlint.yml`이 PR마다 `./gradlew ktlintCheck`를 실행한다.
위반이 있으면 체크가 실패한다. 별도 설정 없이 리포지토리에 있는 것만으로 동작한다.

### 2. Git pre-commit hook — 최초 1회 활성화 필요

커밋 순간 스테이징된 코틀린 파일을 자동으로 `ktlintFormat`한다.
hook 스크립트는 리포에 커밋돼 있지만(`.githooks/`), 활성화를 위해 각자 1회 설정이 필요하다.

```bash
git config core.hooksPath .githooks
```

> `.git/hooks/`는 git이 추적하지 않아 공유가 안 되므로, 추적되는 `.githooks/`에 두고
> `core.hooksPath`로 연결하는 방식을 쓴다.

해제: `git config --unset core.hooksPath`

### 수동 실행

```bash
./gradlew ktlintCheck    # 검사만
./gradlew ktlintFormat   # 자동 수정
```
