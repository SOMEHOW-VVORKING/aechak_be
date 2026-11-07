#!/usr/bin/env python3
"""Flyway 번호 사고를 PR에서 막는다.

셋 다 테스트는 통과하고 배포에서만 터진다 — 테스트는 빈 DB에 전체를 순서대로
적용하므로 어느 것도 재현되지 않는다.

    번호 역전    Detected resolved migration not applied    → api가 부팅을 거부
    번호 중복    Found more than one migration with version → 같음
    적용분 수정  Migration checksum mismatch                → 같음

판정 기준은 base 브랜치의 최고 번호다. 그것이 배포 시점에 적용돼 있을 번호이고
AWS 자격 없이 PR 단계에서 읽을 수 있다. dev 적용분을 직접 읽는 것보다 보수적이다 —
마지막 배포가 실패했으면 실제 적용분은 그보다 낮고, 그때는 막는 쪽으로 틀린다.

반복 마이그레이션(R__)은 세 검사 모두에서 제외한다. 버전이 없어 순서·중복과 무관하고,
체크섬이 바뀔 때마다 다시 적용되는 것이 설계된 동작이라 내용을 고치는 것이 정상이다.

수정·삭제 판정은 merge-base와 대조한다. base tip과 바로 비교하면 base를 아직
병합하지 않은 PR이 "파일을 지웠다"로 잘못 걸린다 — 그 PR은 지운 것이 아니라 아직
받지 않은 것이다.

    사용: flyway_order_check.py [base-ref] [검사할-리비전]
    기본값은 origin/$GITHUB_BASE_REF 와 HEAD 다.
"""

import os
import re
import subprocess
import sys

MIG_DIR = "infra/persistence/jpa/src/main/resources/db/migration"
NAME = re.compile(r"^V(\d+)__")
REPEATABLE = re.compile(r"^R__")


def git(*args):
    return subprocess.run(
        ["git", *args], capture_output=True, text=True, check=False
    )


def git_out(*args):
    r = git(*args)
    if r.returncode != 0:
        sys.exit(f"git {' '.join(args)} 실패: {r.stderr.strip()}")
    return r.stdout


def version_of(path):
    m = NAME.match(path.rsplit("/", 1)[-1])
    return int(m.group(1)) if m else None


def is_repeatable(path):
    return bool(REPEATABLE.match(path.rsplit("/", 1)[-1]))


def main():
    base_ref = sys.argv[1] if len(sys.argv) > 1 else (
        "origin/" + os.environ.get("GITHUB_BASE_REF", "develop")
    )
    head_ref = sys.argv[2] if len(sys.argv) > 2 else "HEAD"

    for ref in (base_ref, head_ref):
        if git("rev-parse", "--verify", "-q", ref).returncode != 0:
            sys.exit(f"리비전을 찾을 수 없다: {ref} — fetch-depth와 ref 이름을 확인하라")

    base_files = git_out("ls-tree", "-r", "--name-only", base_ref, "--", MIG_DIR).split()
    if not base_files:
        print(f"base({base_ref})에 {MIG_DIR} 가 없다 — 검사 생략")
        return 0

    merge_base = git_out("merge-base", base_ref, head_ref).strip()

    # base tip의 번호 지도 — "이미 있는 번호"와 "최고 번호"의 근거다.
    base_version = {}
    base_file_of = {}
    base_repeatable = 0
    for f in base_files:
        if is_repeatable(f):
            base_repeatable += 1
            continue
        n = version_of(f)
        if n is None:
            continue
        base_version[f] = n
        base_file_of[n] = f
    base_max = max(base_file_of, default=0)

    print(f"base({base_ref}) 최고 번호: V{base_max} ({len(base_version)}건)")
    if base_repeatable:
        print(f"반복 마이그레이션 {base_repeatable}건 — 버전이 없어 검사 대상이 아니다")
    print(f"merge-base: {merge_base[:12]}")

    problems = []
    added = {}

    # 이 브랜치가 merge-base 이후 마이그레이션에 한 일만 본다.
    raw = git_out(
        "diff", "--name-status", "-M", "--diff-filter=AMDR",
        merge_base, head_ref, "--", MIG_DIR,
    )
    skipped_repeatable = 0
    for line in raw.splitlines():
        parts = line.split("\t")
        status, path = parts[0], parts[1]

        # 반복 마이그레이션은 버전이 없어 순서·중복과 무관하고, 내용이 바뀌면 다시 적용되는
        # 것이 설계된 동작이다. 추가·수정·삭제 어느 쪽도 사고가 아니다.
        if all(is_repeatable(x) for x in parts[1:] if x):
            skipped_repeatable += 1
            continue

        if status.startswith("R"):
            old, new = parts[1], parts[2]
            if old in base_version:
                problems.append(
                    f"V{base_version[old]} 파일의 이름을 바꿨다: "
                    f"{old.rsplit('/', 1)[-1]} → {new.rsplit('/', 1)[-1]}\n"
                    "       base에 이미 있는 마이그레이션이다. 이름이 바뀌면 Flyway가 "
                    "적용 기록과 짝을 잃는다.\n"
                    "       되돌려라. 아직 머지되지 않은 파일만 번호를 바꿀 수 있다."
                )
                continue
            # merge-base에만 있던 내 파일의 번호 변경 — 정상적인 재채번이다
            status, path = "A", new
        elif status == "D":
            if path in base_version:
                problems.append(
                    f"V{base_version[path]} 파일을 지웠다: {path.rsplit('/', 1)[-1]}\n"
                    "       이미 적용된 마이그레이션이 사라지면 Flyway가 "
                    "'applied but not resolved'로 멈춘다."
                )
            continue
        elif status == "M":
            if path in base_version:
                problems.append(
                    f"V{base_version[path]} 파일 내용을 고쳤다: {path.rsplit('/', 1)[-1]}\n"
                    "       Flyway는 파일 내용으로 체크섬을 계산하므로 주석 한 줄만 고쳐도 "
                    "배포가 멈춘다.\n"
                    "       되돌리고, 바꿀 것이 있으면 새 번호로 추가하라."
                )
            continue

        if status != "A":
            continue

        name = path.rsplit("/", 1)[-1]
        n = version_of(path)
        if n is None:
            problems.append(
                f"파일명이 Flyway 규칙에 맞지 않는다: {name}\n"
                "       버전 마이그레이션은 V<번호>__<설명>.sql, 반복은 R__<설명>.sql 이다.\n"
                "       반복 마이그레이션은 이 검사가 건드리지 않으므로 이름만 맞으면 통과한다."
            )
            continue

        if n in base_file_of:
            problems.append(
                f"V{n} 이 base에 이미 있다 — 번호 중복이다\n"
                f"       이 PR: {name}\n"
                f"       base  : {base_file_of[n].rsplit('/', 1)[-1]}\n"
                f"       Flyway가 'Found more than one migration with version {n}'으로 멈춘다."
            )
        elif n <= base_max:
            problems.append(
                f"V{n} 이 base 최고 번호 V{base_max} 보다 낮다 — 번호 역전이다\n"
                f"       파일: {name}\n"
                "       먼저 머지된 PR들이 더 높은 번호를 적용해 둔 뒤 이 번호가 도착하면\n"
                "       Flyway가 'Detected resolved migration not applied'로 api 부팅을 거부한다.\n"
                f"       V{base_max + 1} 이상으로 옮겨라. 중간에 빈 번호가 있어도 쓰지 마라."
            )

        if n in added:
            problems.append(
                f"V{n} 을 이 PR 안에서 두 파일이 쓴다\n"
                f"       {added[n].rsplit('/', 1)[-1]}\n"
                f"       {name}"
            )
        else:
            added[n] = path

    if skipped_repeatable:
        print(f"이 브랜치가 건드린 반복 마이그레이션 {skipped_repeatable}건 — 검사하지 않았다")

    if added:
        print(f"이 브랜치가 추가한 마이그레이션 {len(added)}건:")
        for n in sorted(added):
            print(f"  V{n}  {added[n].rsplit('/', 1)[-1]}")
    elif not problems:
        print("이 브랜치가 추가한 마이그레이션 없음 — 통과")
        return 0

    if problems:
        print()
        for p in problems:
            print(f"  ✗ {p}")
        print(f"""
번호를 고치는 방법: 파일명만 바꾼다(git mv). 내용은 그대로 둔다.
다음에 쓸 번호는 V{base_max + 1} 부터다.

이 검사는 base만 본다 — 같은 번호를 든 다른 열린 PR은 잡지 못한다.
번호를 고를 때와 PR을 올릴 때 각각 _plans/tools/flyway-claim.sh 로 점유를 실측하라.""")
        return 1

    print("통과 — 역전·중복·수정 없음")
    return 0


if __name__ == "__main__":
    sys.exit(main())
