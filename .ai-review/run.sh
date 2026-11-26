#!/usr/bin/env bash
# Runs the AI review of one pull request, without posting anything:
# OCR with Mistral Large 4 at low effort, the rule about tests, then verdict.py
# turns the review into READY / HUMAN / INCOMPLETE.
# Usage: run.sh <repo dir> <base sha> <head sha> <out dir>
# Needs: ocr on PATH, MISTRAL_API_KEY in the environment, python3, git.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$1"; BASE="$2"; HEAD="$3"; OUT="$4"
BUDGET="${AI_REVIEW_BUDGET:-6000000}"; TIMEOUT="${AI_REVIEW_TIMEOUT:-30}"
MODEL="${AI_REVIEW_MODEL:-mistral-large-4-0}"
mkdir -p "$OUT"
MB=$(git -C "$REPO" merge-base "$BASE" "$HEAD") || { echo "no merge base" >&2; exit 2; }
echo "range $MB..$HEAD"

(cd "$REPO" && ocr review --from "$MB" --to "$HEAD" --rule "$HERE/rule.json" --preview --format json) > "$OUT/preview.json" 2>/dev/null
if ! python3 -c 'import json,sys;sys.exit(0 if any(f.get("will_review") for f in json.load(open(sys.argv[1])).get("files",[])) else 1)' "$OUT/preview.json"; then
  printf 'READY\tno reviewable files\n' > "$OUT/verdict.txt"; cat "$OUT/verdict.txt"; exit 0
fi

(cd "$REPO" && ocr review --from "$MB" --to "$HEAD" --provider mistral --model "$MODEL" --effort low \
    --rule "$HERE/rule.json" --max-tokens-budget "$BUDGET" --timeout "$TIMEOUT" \
    --format json --audience agent) > "$OUT/review.json" 2> "$OUT/review.err" < /dev/null
echo "ocr exit $?"

python3 "$HERE/verdict.py" "$OUT/review.json" > "$OUT/verdict.txt"
cat "$OUT/verdict.txt"
exit 0
