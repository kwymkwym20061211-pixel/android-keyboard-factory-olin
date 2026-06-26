# Working notes for Claude Code in this repo

## Scratch/work directory

Use `tmp/` at the repo root for build logs and other throwaway scratch files instead of
`/tmp`. This machine's WSL instance can crash under heavy Gradle builds, and `/tmp` gets wiped
on restart — `tmp/` survives because it's on the repo's filesystem. It's gitignored.

Run `./clean_tmp.sh` any time to wipe and recreate it.

## Building

This is a multi-module Gradle project: `:app` (factory app), `:keyboard-engine` (shared
library), `:keyboard-template` (the IME template bundled into `:app`'s assets at build time).

This machine is memory-constrained (~3.7GB RAM) and the full aggregate `./gradlew build` task
(lint + instrumentation tests + everything across all modules at once) has crashed WSL via OOM.
Prefer targeted tasks instead, e.g.:
- `./gradlew :app:assembleDebug`
- `./gradlew :keyboard-engine:testDebugUnitTest`

Redirect long-running build output to a file under `tmp/` and run in the background rather than
waiting on it synchronously in the foreground.
