# MinimalArchiver

Minimal Android file archiver — a lightweight ZArchiver-style clone.

Package: `com.minimalarchiver.app`

## Features
- Browse device storage (requires All-files-access permission on Android 11+)
- View a `.zip` archive's entry list before extracting
- Extract a `.zip` archive into a folder next to it
- Select one or more files/folders and create a `.zip` archive from them

## Scope (minimal, by design)
- Only `.zip` is supported (via `java.util.zip`, no external native libraries). No `.rar` / `.7z` / `.tar.gz` support.
- No file rename/move/delete, no search, no thumbnails — pure browse + zip in/out.

## Build
CI: `.github/workflows/build-apk.yml` runs `gradle assembleDebug` on every push to `main` and uploads `app-debug.apk` as a build artifact.
