# Playback Timing Report

This project emits startup and runtime playback timing logs with tags:
- `PlaybackSetupTiming`
- `PlaybackRuntimeTiming`

## Collect logs

```powershell
adb logcat -c
adb logcat | Tee-Object -FilePath playback_timing.log
```

Run playback actions in the app, then stop log capture.

## Generate report

```powershell
powershell -ExecutionPolicy Bypass -File tools/perf/Parse-PlaybackTiming.ps1 -LogFile .\playback_timing.log
```

Optional: limit to a single tag:

```powershell
powershell -ExecutionPolicy Bypass -File tools/perf/Parse-PlaybackTiming.ps1 -LogFile .\playback_timing.log -Tags PlaybackRuntimeTiming
```

## Output

The report prints one row per phase with:
- `count`
- `avg_ms`
- `p50_ms`
- `p95_ms`
- `min_ms`
- `max_ms`

Rows are sorted by highest `p95_ms` first to highlight bottlenecks.
