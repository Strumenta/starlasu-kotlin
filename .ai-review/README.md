# AI review

Pull requests are reviewed by [Open Code Review](https://github.com/alibaba/open-code-review) (OCR)
with Mistral Large 4 at low effort. The workflow is `.github/workflows/ai-review.yml`.

- **When**: a pull request is opened or marked ready by one of the authors in the repository
  variable `AI_REVIEW_USERS` (default in the workflow), or anybody with write access adds the label
  `ai-review` (also for forks; remove and add it again to review after new pushes).
- **What you get**: one review on the pull request with a verdict, `READY` (no critical or high
  comment), `HUMAN` (something serious, needs a person) or `INCOMPLETE` (the review did not cover
  the whole change). Comments of medium severity or above are inline; the others are in the body.
- **Setup**: secret `MISTRAL_API_KEY`. Optional variable `AI_REVIEW_GATE=true` makes the check
  fail unless the verdict is `READY`.

Files: `run.sh` runs the review, `rule.json` holds the review rules and exclusions (test files are
hidden from the model, so it is told not to complain about missing tests), `verdict.py` computes the
verdict, `post_review.py` posts it. The raw review JSON is kept as a workflow artifact for 30 days.

Run locally, without posting: `MISTRAL_API_KEY=... .ai-review/run.sh "$PWD" origin/main HEAD /tmp/out`
