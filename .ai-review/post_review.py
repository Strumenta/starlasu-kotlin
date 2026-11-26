#!/usr/bin/env python3
"""Posts the AI review on a GitHub pull request, as a single review with event COMMENT.

- Comments with severity medium or above whose lines are inside the diff become inline comments.
- The others (low severity, or lines outside the diff, where GitHub refuses inline comments)
  are listed in the review body, so nothing is lost.
- The verdict and the cost of the review are at the top of the body.

Usage: post_review.py <out dir> --repo DIR --base SHA --head SHA --pr owner/name#N [--dry-run]
Needs GITHUB_TOKEN unless --dry-run.
"""
import argparse, json, os, re, subprocess, sys, urllib.request

SEV_ORDER = {"critical": 0, "high": 1, "medium": 2, "low": 3}
MARK = "<!-- ai-review -->"
# Mistral Large 4 prices used for the estimate, USD per million tokens (input, cached input, output).
PRICE = (0.70, 0.07, 2.10)


def hunks(repo, base, head):
    """path -> list of (start, end) line ranges on the new side of the diff."""
    out, path = {}, None
    diff = subprocess.run(["git", "-C", repo, "diff", "-U3", base, head], capture_output=True, text=True).stdout
    for line in diff.splitlines():
        if line.startswith("+++ "):
            path = line[6:] if line.startswith("+++ b/") else None
        elif line.startswith("@@") and path:
            m = re.match(r"@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", line)
            a, n = int(m.group(1)), int(m.group(2) or 1)
            if n:
                out.setdefault(path, []).append((a, a + n - 1))
    return out


def inline_ok(c, h):
    s, e = int(c.get("start_line") or 0), int(c.get("end_line") or c.get("start_line") or 0)
    return any(a <= s and e <= b for a, b in h.get(c.get("path"), []))


def cost(r):
    u = r.get("summary") or {}
    inp, cached, outp = u.get("input_tokens", 0), u.get("cache_read_tokens", 0), u.get("output_tokens", 0)
    return ((inp - cached) * PRICE[0] + cached * PRICE[1] + outp * PRICE[2]) / 1e6


def body_of(c):
    b = f"**{c.get('severity', '?')}** · {c.get('category', '')}\n\n{c.get('content', '')}"
    if c.get("suggestion_code"):
        b += "\n\n```suggestion\n" + c["suggestion_code"].rstrip("\n") + "\n```" if inline_c(c) else "\n\nSuggested code:\n```\n" + c["suggestion_code"] + "\n```"
    return b


def inline_c(c):
    return c.get("_inline", False)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--repo", required=True)
    ap.add_argument("--base", required=True)
    ap.add_argument("--head", required=True)
    ap.add_argument("--pr", required=True, help="owner/name#number")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    owner_name, number = a.pr.split("#")
    verdict, _, reasons = open(os.path.join(a.out, "verdict.txt")).read().strip().partition("\t")
    rev_p = os.path.join(a.out, "review.json")
    final = json.load(open(rev_p)) if os.path.exists(rev_p) else {"comments": []}
    mb = subprocess.run(["git", "-C", a.repo, "merge-base", a.base, a.head], capture_output=True, text=True).stdout.strip()
    h = hunks(a.repo, mb or a.base, a.head)
    comments = sorted(final.get("comments") or [], key=lambda c: SEV_ORDER.get(c.get("severity"), 9))
    inline, listed = [], []
    for c in comments:
        if SEV_ORDER.get(c.get("severity"), 9) <= 2 and inline_ok(c, h):
            c["_inline"] = True
            s, e = int(c["start_line"]), int(c.get("end_line") or c["start_line"])
            x = {"path": c["path"], "line": e, "side": "RIGHT", "body": body_of(c)}
            if s < e:
                x.update(start_line=s, start_side="RIGHT")
            inline.append(x)
        else:
            listed.append(c)

    icon = {"READY": "✅", "HUMAN": "👀", "INCOMPLETE": "⚠️"}.get(verdict, "")
    parts = [MARK, f"## AI review: {icon} {verdict}", f"{reasons}"]
    usd = cost(final) if final.get("summary") else 0.0
    parts.append(f"Mistral Large 4 at low effort · {len(comments)} comment(s), {len(inline)} inline · estimated cost ${usd:.2f}")
    if verdict == "INCOMPLETE":
        parts.append("The review did not cover the whole change, so it cannot be used to merge without a human look.")
    if listed:
        rows = [f"- **{c.get('severity')}** `{c.get('path')}:{c.get('start_line')}`: {c.get('content', '').strip()}" for c in listed]
        parts.append(f"<details><summary>{len(listed)} more comment(s) (low severity or outside the diff)</summary>\n\n" + "\n".join(rows) + "\n</details>")
    parts.append("<sub>Automated review by [Open Code Review](https://github.com/alibaba/open-code-review) with "
                 "[Mistral](https://mistral.ai/). It can be wrong: treat comments as suggestions.</sub>")
    payload = {"commit_id": a.head, "event": "COMMENT", "body": "\n\n".join(parts), "comments": inline}

    summ = os.environ.get("GITHUB_STEP_SUMMARY")
    if summ:
        open(summ, "a").write(payload["body"] + "\n")
    if a.dry_run:
        json.dump(payload, sys.stdout, ensure_ascii=False, indent=1); return
    url = f"https://api.github.com/repos/{owner_name}/pulls/{number}/reviews"
    hdr = {"Authorization": "Bearer " + os.environ["GITHUB_TOKEN"], "Accept": "application/vnd.github+json",
           "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json"}
    try:
        urllib.request.urlopen(urllib.request.Request(url, json.dumps(payload).encode(), hdr)).read()
    except urllib.error.HTTPError as e:  # one bad inline position makes GitHub refuse the whole review
        print(f"inline review refused ({e.code}: {e.read()[:300]!r}), posting everything in the body", file=sys.stderr)
        extra = "\n\n".join(f"`{c['path']}:{c['line']}`\n\n{c['body'].replace('```suggestion', '```')}" for c in inline)
        payload = {"commit_id": a.head, "event": "COMMENT", "body": payload["body"] + "\n\n---\n\n" + extra}
        urllib.request.urlopen(urllib.request.Request(url, json.dumps(payload).encode(), hdr)).read()
    print(f"posted review: {verdict}, {len(inline)} inline, {len(listed)} in the body", file=sys.stderr)


if __name__ == "__main__":
    main()
