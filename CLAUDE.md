# Working notes for Claude Code in this repo

## Scratch/work directory

Use `tmp/` at the repo root for build logs and other throwaway scratch files instead of
`/tmp`. This machine's WSL instance can crash under heavy Gradle builds, and `/tmp` gets wiped
on restart — `tmp/` survives because it's on the repo's filesystem. It's gitignored.

Run `./clean_tmp.sh` any time to wipe and recreate it.

Read `./README.md` to get summary of this product(please edit if needed)

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

## 2026-06-27: post-MVP real-device polish round

Full write-up: `docs/2026/06/post-mvp-real-device-polish.md`.

- **Do not run `adb install` / `./gradlew installDebug` yourself.** It has failed when Claude
  ran it (USB passthrough flakiness on this machine — see the adb section above). The user
  installs and tests on the real device themselves now; Claude should stop at a successful
  `:app:assembleDebug` and let them know it's ready.
- **Targeting API 35+ forces edge-to-edge everywhere, including IME windows.** This is *why*
  the generated keyboard's bottom row was getting hidden under the nav/gesture bar even after
  the `FixedHeightContainer` height fix: the container's height was forced correctly, but nothing
  accounted for the nav bar inset eating into it. Fixed by reading
  `WindowInsetsCompat.Type.navigationBars()` inside `FixedHeightContainer` and shrinking the
  *content* (not the container) by that inset, pinned to the top. The same forced edge-to-edge
  also affects the factory app's own Activities (`ProjectListActivity`/`EditorActivity`) — they
  need their own `ViewCompat.setOnApplyWindowInsetsListener` → `setPadding(systemBars)` on the
  root view, which the original Android-Studio-generated `MainActivity.java` had and which got
  dropped when those screens were rewritten. If a new Activity is added, give it the same
  treatment or it'll draw under the status/nav bar.
- **`androidx.activity.EdgeToEdge.enable(this)` fails to resolve under this project's AGP9
  built-in-Kotlin setup**, even though the dependency resolves fine and the class genuinely
  exists in the AAR (confirmed via `javap`). Root cause not found; not worth re-investigating
  casually. Workaround: skip it — it's cosmetic (status bar icon color) on API 35+, where
  edge-to-edge is forced regardless. The `WindowInsets` padding listener above is what actually
  matters and works fine.

## 2026-06-28: shared-storage output paths are duplicated across two modules

Generated APKs and dictionary CSV exports both land in `Download/keyboard/` (a subfolder, not
directly under `Download/`, to avoid cluttering the shared space) via
`MediaStore.Downloads.RELATIVE_PATH`. This is implemented in **two separate places** that must be
kept in sync by hand:
- `:app`'s `DownloadsWriter.kt` (writes the exported keyboard APK)
- `:keyboard-template`'s `DictionaryCsvIo.kt` (writes dictionary CSV exports from the *generated*
  keyboard app)

`:keyboard-template` can't depend on `:app`, so `DictionaryCsvIo` is a from-scratch equivalent of
`DownloadsWriter`, not a shared call — there's no single source of truth. If the output directory
(or any other shared-storage behavior) changes again, grep for `MediaStore.Downloads` and update
both, plus the matching `export_success_format` string in both modules' `strings.xml`.
