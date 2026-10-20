# Changelog

## [1.7.4] - 2026-10-20

### Improvements

- Ready for Android 17 — the app now targets API level 37, so it runs with Android 17's latest behavior and optimizations
- Playback keeps running in the background under Android 17's stricter audio rules (handled by the media session service)
- Sharing a recording and installing an in-app update already hand the target app temporary read access, matching Android 17's tighter URI-grant rules

### Notes

- No functional changes: the recorder already compiled against API 37, so this is a compatibility target update plus documentation
