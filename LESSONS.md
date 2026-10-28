# Lessons from reviewed work

Record reusable rules only after actual owner review, an incident, or rework. Each lesson must
name the rule, the concrete failure, and evidence such as a commit or review comment. Promote
rules that recur across separate changes to [code standards](docs/code-standards.md).

## Keep agent changes in their own commit

**Rule:** Put agent-authored changes in a separate commit unless the owner explicitly asks to combine
them.

**Failure:** An agent amended reviewed commit `a9af0ea0` with documentation and a locking fix,
folding subsequent work into commit `369d8c97`.

**Evidence:** owner review, 2026-10-27.
