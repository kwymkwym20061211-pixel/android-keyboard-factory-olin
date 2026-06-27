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

## Connecting the physical test device over adb

This machine has no working Android emulator (no `/dev/kvm` access), so testing happens on a
real device via USB, passed through WSL with `usbipd-win`. The device will *not* show up in
`adb devices` until it's attached for this WSL session — run:

```
~/adb_connect_usb.sh
```

This finds the phone (model FCG02) via `usbipd.exe list` on the Windows side and attaches its
bus to WSL. Needed again every time the device is reconnected/replugged or after a WSL
restart — if `adb devices` comes up empty, run this first before assuming something else is
wrong (e.g. an `EOFException`/`InstallException` mid-`adb install` usually just means the USB
passthrough dropped mid-transfer; re-run the script and retry rather than debugging the build).

## 2026-06-27: MVP reached — gotchas worth knowing before touching this code again

Full write-up: `docs/2026/06/mvp-completion-and-troubleshooting.md`. The short version, for future
update work:

- **Only install `:app`.** `./gradlew installDebug` with no module prefix installs *every*
  application module, including `:keyboard-template` (an internal-only IME template that's
  bundled into `:app`'s assets, never meant to be installed standalone). Always use
  `:app:installDebug` / `:app:assembleDebug` (see `build.sh`). If a mystery "Generated Keyboard"
  app shows up on the test device, that's this.
- **A custom IME's input view ignores `LayoutParams` height.** `InputMethodService` hands the
  view returned from `onCreateInputView()` a full-screen-ish `MeasureSpec` regardless of what
  `LayoutParams` you set on it. The only reliable fix found was overriding `onMeasure()` to force
  the height directly (see `FixedHeightContainer` in `:keyboard-template`) — don't re-attempt the
  `LayoutParams`-only approach, it was tried and confirmed not to work on this device.
  Also: don't put an `android:theme` on the IME's `<application>` tag (e.g. `Theme.DeviceDefault`)
  — it's an Activity theme and doesn't belong on an IME service window.
  Also: don't give the `<service>` its own `android:label` — leave it unset so it inherits the
  (patched) `<application>` label, otherwise the IME picker shows the template's original name
  instead of the user's project name.
- **Never collapse non-ASCII project names to a fixed placeholder when deriving the
  applicationId.** `PackageNameGenerator` hex-encodes the UTF-8 bytes of the name instead —
  lossy ASCII-only sanitizing (e.g. mapping every non-ASCII char to `_`) both produces a useless
  package name and was the direct cause of an install-time "parse error" for Japanese project
  names. The project's row-id is still appended for the actual uniqueness guarantee.
- **When a UI/rendering bug seems to defy a fix, verify on-device via adb before guessing
  again.** `adb shell ime set <id>`, `adb shell input tap <x> <y>`, `adb exec-out screencap -p`
  let Claude confirm a fix empirically without needing the user to manually retest every
  iteration — this is what found that the `LayoutParams` fix silently wasn't working.
