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

To avoid the USB passthrough flakiness entirely, there's also:

```
~/adb_connect_wifi.sh
```

This runs `~/adb_connect_usb.sh` to get USB adb working first, then reads the phone's current
`wlan0` IP over that USB connection and switches it to wireless adb (`adb tcpip 5555` +
`adb connect <ip>:5555`). Once it's run, `adb`/`installDebug` traffic goes over WiFi instead of
the USB passthrough — re-run it any time the phone reboots (wireless adb mode doesn't survive a
reboot) or its WiFi IP changes (DHCP lease renewal).

## 2026-06-29: `build.sh` installs via `adb push` + `pm install`, not `adb install`

`adb devices` succeeding doesn't mean a subsequent install will survive — device enumeration is a
tiny handshake, while an install streams the whole APK over the same flaky USB passthrough
(usbipd-win), so that's where mid-transfer drops actually show up (symptom: `installDebug` hangs
indefinitely instead of erroring, e.g. stuck at "99% EXECUTING" for minutes). `./gradlew
installDebug`/`adb install` stream the APK and install in one step, so a hiccup mid-stream kills
the whole thing. `build.sh`'s install step now does `adb push <apk> /data/local/tmp/` (adb's
ordinary, hiccup-tolerant sync protocol) followed by a separate `adb shell pm install -r
<path>` — splitting transfer from install made installs over USB noticeably more reliable on this
machine. If editing the install step again, keep that split rather than going back to a single
`adb install`/`./gradlew installDebug` call.

## 2026-06-27: MVP reached — gotchas worth knowing before touching this code again

Full write-up: `docs/2026/06/mvp-completion-and-troubleshooting.md`. The short version, for future
update work:

- **Only install `:app`.** `./gradlew installDebug` with no module prefix installs *every*
  application module, including `:keyboard-template` (an internal-only IME template that's
  bundled into `:app`'s assets, never meant to be installed standalone). Always build
  `:app:assembleDebug`/`:app:assembleRelease` specifically (see `build.sh`, which also installs
  the resulting APK itself rather than via a Gradle install task — see the adb section above). If
  a mystery "Generated Keyboard" app shows up on the test device, that's this.
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

## 2026-06-29: keep the editor preview and the generated keyboard's *size* in sync too

The factory app's editor preview (`activity_editor.xml`'s `gridView`) and the actual generated
keyboard (`GeneratedKeyboardService`) both render the same `KeyboardGridView`, but as two
separate Views in two separate processes — nothing forces their on-screen *dimensions* to match.
They had drifted apart: the preview used to fill whatever vertical space was left in
`EditorActivity` (`layout_height="0dp"` + weight), while the generated keyboard is pinned to a
fixed `@dimen/keyboard_height` via `FixedHeightContainer`. Since `KeyboardGridView` divides its
own measured width/height by `cols`/`rows` to size cells, any mismatch between those two heights
directly distorts the preview's aspect ratio relative to the real output.

Fix: `keyboard_height`/`candidate_strip_height` now live in **`:keyboard-engine`'s**
`res/values/dimens.xml` (the one module both `:app` and `:keyboard-template` already depend on)
instead of being defined separately in each. `activity_editor.xml` now sizes `gridView` to
`@dimen/keyboard_height` directly instead of filling remaining space, so its aspect ratio matches
the real keyboard's.

- XML resource references (`@dimen/...`) resolve fine across module boundaries through normal
  Gradle resource merging — no special handling needed there.
- **Kotlin/Java code references do not**, because this project uses AGP's default
  `nonTransitiveRClass = true`: a module's generated `R` class only contains resources declared
  directly in that module, not ones inherited from a dependency. `GeneratedKeyboardService.kt`
  (in `:keyboard-template`) has to import the defining module's R class explicitly —
  `import android.keyboard.engine.R as EngineR` — rather than using its own local `R`.
- If a keyboard-sizing constant needs to change, or a new one is added, put it in
  `:keyboard-engine`'s `dimens.xml` (not `:app`'s or `:keyboard-template`'s) so the preview can't
  drift from the real output again.

**Follow-up bug from the same root cause:** fixing the aspect ratio above exposed a second,
previously-masked mismatch — CHAR key labels came out stretched tall in the generated keyboard
even though cell shapes now matched. Cause: the editor preview draws CHAR labels live via
`KeyboardGridView`'s `drawText` (font rendering, always proportionally correct regardless of cell
shape), but the generated keyboard instead draws a pre-baked square PNG (`GlyphRenderer`, in
`:app`, runs at export time so the generated app doesn't have to bundle the whole font) — and
`KeyboardGridView` was stretching that square bitmap to fill the *whole*, generally non-square,
cell. Fixed by inscribing a centered square (sized to the cell's smaller dimension) instead of
stretching to the full cell bounds. Also unified the two independent "how big is the glyph
relative to its drawing surface" constants (`KeyboardGridView`'s live-text sizing vs.
`GlyphRenderer`'s baked-bitmap sizing) into one — `KeyboardGridView.CHAR_GLYPH_FILL_RATIO` — which
`:app`'s `GlyphRenderer` now imports directly (`:app` can depend on `:keyboard-engine` symbols in
code, unlike the reverse `:keyboard-template`→`:keyboard-engine` `R`-class case above, since
there's no `nonTransitiveRClass` restriction on plain Kotlin `const val`s). If either rendering
path's sizing changes again, change `CHAR_GLYPH_FILL_RATIO` rather than reintroducing a second
local constant.
