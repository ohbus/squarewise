# OPS-06: Reusable CI caching

Centralize cache configuration in the reusable workflow. Gradle dependency and
build outputs must use branch-safe keys with restore fallbacks; Docker jobs use
shared BuildKit GitHub Actions caches. Cache failures must never compromise
correctness, and the policy must remain independent of trigger wrappers.
