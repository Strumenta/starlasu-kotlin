#!/usr/bin/env python3
"""Turns an OCR review JSON into a merge verdict: READY, HUMAN (needs a human look) or INCOMPLETE.

v0 uses only what `ocr review --format json` already produces:
  - INCOMPLETE if the review is not trustworthy as a review: status != complete,
    budget_exceeded, files in coverage.failed, or warnings that a group stopped mid-review.
  - HUMAN if any comment has severity in BLOCKING (default: critical, high),
    or if the review touched paths listed as architectural (--arch-globs).
  - READY otherwise.
Prints one line: VERDICT<TAB>reasons. Exit code 0 for READY, 1 for HUMAN, 2 for INCOMPLETE.

Usage: verdict.py review.json [--blocking critical,high] [--arch-globs 'api/**,**/build.gradle*']
"""
import argparse, fnmatch, json, sys

STOP_WARNINGS = {"review_limit_reached", "token_budget_reached"}


def verdict(r, blocking, arch_globs):
    reasons = []
    s = r.get("summary") or {}
    m = r.get("manifest") or {}
    failed = (m.get("coverage") or {}).get("failed") or []
    stops = [w for w in (r.get("warnings") or []) if w.get("type") in STOP_WARNINGS]
    if r.get("status") != "complete":
        reasons.append(f"status={r.get('status')}")
    if s.get("budget_exceeded"):
        reasons.append("budget_exceeded")
    if failed:
        reasons.append(f"{len(failed)} file(s) not covered")
    if stops:
        reasons.append(f"{len(stops)} group(s) stopped mid-review")
    if reasons:
        return "INCOMPLETE", reasons
    comments = r.get("comments") or []
    hits = [c for c in comments if (c.get("severity") or "").lower() in blocking]
    if hits:
        reasons.append(f"{len(hits)} comment(s) at {'/'.join(sorted({c['severity'] for c in hits}))}")
    if arch_globs:
        touched = {c.get("path") for c in comments} | {x.get("path") for x in (m.get("coverage") or {}).get("completed") or []}
        arch = sorted(p for p in touched if p and any(fnmatch.fnmatch(p, g) for g in arch_globs))
        if arch:
            reasons.append(f"architectural paths: {', '.join(arch[:3])}")
    if reasons:
        return "HUMAN", reasons
    low = len(comments)
    return "READY", [f"{low} minor comment(s)" if low else "no comments"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("review")
    ap.add_argument("--blocking", default="critical,high")
    ap.add_argument("--arch-globs", default="")
    a = ap.parse_args()
    r = json.load(open(a.review))
    v, reasons = verdict(r, {x.strip() for x in a.blocking.split(",") if x.strip()},
                         [g for g in a.arch_globs.split(",") if g])
    print(f"{v}\t{'; '.join(reasons)}")
    sys.exit({"READY": 0, "HUMAN": 1, "INCOMPLETE": 2}[v])


if __name__ == "__main__":
    main()
