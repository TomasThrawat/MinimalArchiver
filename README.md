# MinimalArchiver

Minimal Android file archiver — a lightweight ZArchiver-style clone.

Package: `com.minimalarchiver.app`

## Features
- Browse device storage (requires All-files-access permission on Android 11+)
- View a `.zip` archive's entry list before extracting
- Long-press any file/folder for actions: compress to `.zip`, extract (zip files only), rename, copy to another path, delete
- Optional Shizuku mode (toggle button in the top bar): browse restricted paths like `/data` using shell-UID privileges via a bound Shizuku UserService — requires the Shizuku app installed and running, and its permission granted when prompted
  - In this mode copy (`cp -r`), rename (`mv`), and delete (`rm -rf`) are available — no zip/unzip, since AOSP's toybox ships `tar`/`cp`/`mv`/`rm` but no `zip`/`unzip` applet

## Scope (minimal, by design)
- Only `.zip` is supported for normal storage (via `java.util.zip`, no external native libraries). No `.rar` / `.7z` / `.tar.gz` support.
- No search, no thumbnails, no move across directories (rename within the same directory is supported; copy to another path covers moving between directories, combined with a manual delete of the source).
- Shizuku-spawned shell processes aren't explicitly force-killed on unbind in this build (no `destroy()` transaction wired up) — they exit on their own once their command finishes.

## Build
CI: `.github/workflows/build-apk.yml` runs `gradle assembleDebug` on every push to `main` and uploads `app-debug.apk` as a build artifact.
