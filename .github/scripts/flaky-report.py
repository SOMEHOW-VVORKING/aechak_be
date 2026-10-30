#!/usr/bin/env python3
"""재시도로 통과한 테스트(플레이크)를 찾아 잡 요약에 올린다.

왜 필요한가:
  org.gradle.test-retry 는 failOnPassedAfterRetry=false 로 두면 플레이크를 '통과'로
  만들어 머지를 풀어준다. 그대로 두면 플레이크가 조용히 사라져 아무도 고치지 않는다.
  이 스크립트가 그것을 매 run 마다 이름까지 적어 드러내는 역할을 한다.

판정 근거(실측):
  test-retry 는 Maven Surefire 의 <flakyFailure> 를 쓰지 않는다. 대신 같은 이름의
  <testcase> 를 여러 개 남긴다 — 실패한 시도에는 <failure>/<error> 자식이 붙고,
  재시도가 통과하면 자식 없는 <testcase> 가 하나 더 생긴다. 그래서
    '실패한 실행 ≥ 1' AND '깨끗한 실행 ≥ 1'  = 플레이크
    모든 실행이 실패                        = 진짜 실패
"""

import glob
import os
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

FAIL_TAGS = ("failure", "error")


def collect(root_dir):
    runs = defaultdict(lambda: {"fail": 0, "pass": 0, "messages": []})
    pattern = os.path.join(root_dir, "**", "build", "test-results", "**", "*.xml")
    for path in glob.glob(pattern, recursive=True):
        try:
            tree = ET.parse(path)
        except ET.ParseError:
            # 잡이 중간에 죽으면 XML 이 잘린 채 남는다 — 그 파일만 건너뛴다
            print(f"::warning::테스트 결과 XML 파싱 실패, 건너뜀: {path}", file=sys.stderr)
            continue
        for case in tree.getroot().iter("testcase"):
            key = (case.get("classname") or "?", case.get("name") or "?")
            failures = [c for c in case if c.tag in FAIL_TAGS]
            if failures:
                runs[key]["fail"] += 1
                msg = (failures[0].get("message") or failures[0].tag).strip()
                runs[key]["messages"].append(" ".join(msg.split())[:300])
            else:
                runs[key]["pass"] += 1
    return runs


def main():
    root_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    runs = collect(root_dir)

    flaky = {k: v for k, v in runs.items() if v["fail"] and v["pass"]}
    hard = {k: v for k, v in runs.items() if v["fail"] and not v["pass"]}

    lines = []
    if flaky:
        lines.append("## 재시도로 통과한 테스트 (플레이크)")
        lines.append("")
        lines.append("머지는 막지 않았지만 **실제로 깨진 테스트다.** 방치하면 계속 재발한다.")
        lines.append("")
        lines.append("| 테스트 | 실패/통과 | 첫 실패 메시지 |")
        lines.append("|---|---|---|")
        for (cls, name), v in sorted(flaky.items()):
            msg = (v["messages"][0] if v["messages"] else "").replace("|", "\\|")
            lines.append(f"| `{cls}.{name}` | {v['fail']}/{v['pass']} | {msg} |")
        lines.append("")

    if hard:
        lines.append("## 재시도 후에도 실패한 테스트")
        lines.append("")
        for (cls, name), v in sorted(hard.items()):
            msg = (v["messages"][0] if v["messages"] else "").replace("|", "\\|")
            lines.append(f"- `{cls}.{name}` — {msg}")
        lines.append("")

    if not lines:
        return 0

    report = "\n".join(lines)
    print(report)

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as fh:
            fh.write(report + "\n")

    # 플레이크는 잡을 깨지 않는다 — 드러내는 것이 목적이다.
    # 다만 로그에서 눈에 띄도록 GitHub 주석으로도 올린다.
    for (cls, name), v in sorted(flaky.items()):
        print(f"::warning title=플레이크::{cls}.{name} — 재시도로 통과(실패 {v['fail']}회)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
