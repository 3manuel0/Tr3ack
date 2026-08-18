# Tr3ack

A workout tracker Android app for logging strength-training sessions, tracking body weight, and visualizing progress over time.

## Features

- **Log Workouts** — Select exercise, set reps/weight, pick any date for backfilling
- **Body Weight Tracking** — One entry per day, editable, with fallback lookup for workout days without a log
- **Personal Best Cards** — Separate PBs for Weighted Pull-Ups and Weighted Dips (highest system weight + reps)
- **Progress Chart** — Dual-axis line chart showing total system weight and reps per workout day (5/10 day toggle)
- **History** — Browse all logged sessions by date, edit or delete any past set
- **Dark Mode** — Always-on dark theme

## Exercises

| Exercise | Type | Tracked Metrics |
|---|---|---|
| Weighted Pull-Ups | Weighted bodyweight | System weight, % BW, daily volume |
| Weighted Dips | Weighted bodyweight | System weight, % BW, daily volume |
| Bicep Curls | Free weight | Weight × reps |
| Hammer Curls | Free weight | Weight × reps |
| Lateral Raises | Free weight | Weight × reps |

## Key Metrics

- **Total System Weight** = Body Weight + Added Weight
- **% Body Weight** = (Total System Weight / Body Weight) × 100
- **Daily Volume** = Σ(Total System Weight × Reps) per day (pull-ups & dips only)

## Tech Stack

- Kotlin + Jetpack Compose (Material 3)
- MVVM + Repository pattern
- Room (SQLite) for local persistence
- Navigation Compose
- Custom Canvas charts

## Install

**USB:**
```bash
./gradlew installDebug
```

**APK:**
Transfer `app/build/outputs/apk/debug/app-debug.apk` to your phone and open it.

> Data persists across updates. Never uninstall if you want to keep your logs — always install over the existing app.

## Min SDK

26 (Android 8.0)
