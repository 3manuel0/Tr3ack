# Tr3ack

A workout tracker Android app for logging strength-training sessions, tracking body weight, and visualizing progress over time.

## Screenshots

<p align="center">
  <img src="screenshots/dashboard.png" alt="Dashboard" width="250"/>
  &nbsp;&nbsp;
  <img src="screenshots/log_workout.png" alt="Log Workout" width="250"/>
  &nbsp;&nbsp;
  <img src="screenshots/body_weight.png" alt="Body Weight" width="250"/>
</p>
<p align="center">
  <img src="screenshots/history.png" alt="History" width="250"/>
  &nbsp;&nbsp;
  <img src="screenshots/stats.png" alt="Stats" width="250"/>
</p>

## Features

- **Dashboard** — At-a-glance view of today's body weight, personal bests, and recent activity
- **Log Workouts** — Select exercise, set reps/weight, pick any date for backfilling; weight persists between sets for fast logging
- **Body Weight Tracking** — One entry per day, editable, with fallback lookup for workout days without a log
- **Personal Best Cards** — Separate PBs for Weighted Pull-Ups and Weighted Dips (highest system weight + reps)
- **Progress Chart** — Dual-axis line chart showing total system weight and reps per workout day (5/10 day toggle)
- **History** — Browse all logged sessions by date, edit or delete any past set
- **CSV Export** — Export all workout data as a formatted CSV file from the History screen
- **Splash Screen** — Custom branded launch screen with app icon
- **Dark Mode** — Always-on dark theme

## Exercises

| Exercise          | Type                | Tracked Metrics                   |
| ----------------- | ------------------- | --------------------------------- |
| Weighted Pull-Ups | Weighted bodyweight | System weight, % BW, daily volume |
| Weighted Dips     | Weighted bodyweight | System weight, % BW, daily volume |
| Bicep Curls       | Free weight         | Weight x reps                     |
| Hammer Curls      | Free weight         | Weight x reps                     |
| Lateral Raises    | Free weight         | Weight x reps                     |

## CSV Export

Export your data from the History screen (download icon in the top bar). The CSV includes:

| Column | Description |
| --- | --- |
| Date | Workout date |
| Exercise | Exercise name |
| Added Weight (kg) | Average weight added across sets |
| Total System Weight (kg) | Body Weight + Added Weight |
| % of Body Weight | Total System Weight / Body Weight x 100 |
| Reps | Reps per set (e.g. 8-7-6) |
| Volume (kg) | Sum of (Total System Weight x Reps) |

## Key Metrics

- **Total System Weight** = Body Weight + Added Weight
- **% Body Weight** = (Total System Weight / Body Weight) x 100
- **Daily Volume** = Sum of (Total System Weight x Reps) per day (pull-ups & dips only)

## Tech Stack

- Kotlin + Jetpack Compose (Material 3)
- MVVM + Repository pattern
- Room (SQLite) for local persistence
- Navigation Compose
- AndroidX SplashScreen API
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
