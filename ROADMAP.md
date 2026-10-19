# Tr3ack — Roadmap / Feature Ideas

Living list of candidate features to choose from. Add ideas here, tick them off when built.

_Last updated: 2026-10-18_

## Current state snapshot (for context)
- Kotlin + Jetpack Compose (M3, always-dark), Room **v8**, MVVM + manual DI (`Tr3ackApplication.repository`).
- 6 tabs: Home, Log, Weight, History, Stats, Goals.
- **5 hard-coded exercises** (ids 1,2,3,4,6): Weighted Pull-Ups, Dips, Chin-Ups, Bicep Curls, Lateral Raises. No custom-exercise UI.
- Materialized stats cache: `exercise_stats`, `exercise_daily_stats`, `workout_days`. Set edits refresh only the touched exercise/day rows incrementally; body-weight edits and version bumps do a full rebuild (`StatsCalculator.CACHE_VERSION`).
- E1RM math centralized in `MovementFactors` / `StatsCalculator` (no SQL computes E1RM).
- Charts are custom `Canvas` (E1RM line + trend + PR rings, Session Tonnage bars, Belt Load vs BW). Date labels rotated 90° with adaptive thinning.
- Achievements/XP/streaks computed retroactively (`AchievementRules`), not persisted.
- Backup: **JSON export/import** (Dashboard ⋮) + **CSV export-only** (History). Import whitelists the 5 built-in exercise ids.
- Units are **hard-coded kg**. No settings, notifications, widgets, rest timer, or cloud sync.
- Tests: `AchievementRulesTest`, `GoalRulesTest`, `MovementFactorsTest`, `StatsCalculatorTest`, `Tr3ackDatabaseMigrationTest`, `StatsCacheRoundTripTest`, `StatsCacheIncrementalParityTest`.

## Top picks (highest value, least new plumbing)
- [x] **Goal targets with progress** — real goal-setting in the Goals tab: target E1RM / working weight / BW-multiplier per exercise, with live progress bars. Working-weight goals also take a rep target ("40 kg × 8") and score progress as the average of the weight% and reps%. Backed by the `goals` table (v6, `targetReps` v8) and `GoalRules` over `exercise_stats` + the exercise's sets.
- [x] **Consistency calendar heatmap** — GitHub-style training calendar on Home, built from cached `workout_days` (`setCount`, `tonnage`). No new persistence.

## Quick wins (data already available)
- [ ] **Recent-PR feed / PR timeline** — chronological feed of when each all-time best was set. Reuse `ProgressViewModel.isAllTimePr` logic and `exercise_stats.bestE1RMDate`.
- [x] **Week-over-week volume** — compare this week's tonnage vs last (total + per exercise), using `workout_days` / `exercise_daily_stats`. Dashboard card (`WeekVolume` / `buildWeekVolume`), ISO weeks (Mon-Sun).
- [ ] **CSV import** — round-trip the existing CSV export (parse the same 7-column layout, resolve exercise by name).
- [ ] **Session summary card** — after logging, show sets / tonnage / PRs for the day; optionally share as an image.

## Quality of life (fills obvious gaps)
- [ ] **Settings screen** — none exists today. Options: kg/lb toggle, week start, theme (light/AMOLED), default rest duration, backup shortcuts. The kg/lb work touches every screen + repository (store canonical kg, convert on display/input).
- [ ] **Rest timer** on the Log screen — countdown that starts after saving a set, survives backgrounding via a notification. Adds `POST_NOTIFICATIONS`.
- [ ] **Custom exercises CRUD** — `ExerciseDao.insert` exists but is unused; add an exercise-management screen. Must also update the JSON import whitelist (currently `knownExerciseIds`) and `ExerciseIds`.
- [ ] **Workout reminders / streak nudges** — WorkManager + notifications (e.g. "no session in 4 days", weekly streak reminder).

## Bigger bets
- [ ] **Routine / program templates** — save exercise+weight+reps templates and replay them; "repeat last workout" button on Log.
- [ ] **Muscle-group tags** — add a category to exercises, then volume-distribution charts (needs a DB migration + UI for tagging).
- [ ] **Home-screen widget (Glance)** — streak, today's tonnage, or quick-log.
- [ ] **Body measurements & progress photos** — beyond body weight (waist, arms, etc.).
- [ ] **Health Connect sync** — push workouts / pull body weight.
- [ ] **Light / AMOLED theme** — theme currently forced dark.

## Done
- [x] Rotate chart date labels vertically + adaptive thinning (`d96bb32`).
- [x] E1RM accuracy work: >10-rep extrapolation cap, free-weight single-rep fix, trend line + PR markers, expanded tests.
- [x] Free-weight E1RM switched from the Epley/Brzycki blend to **Mayhew et al. (1992)**: exponential, validated on 1–15-rep sets (so sets above 10 reps keep a trustworthy estimate instead of drifting like the linear formulas), not significantly different from measured 1RM in LeSuer et al. (1997), and slightly conservative (biceps 19 kg × 9: 24.4 instead of 24.7). Rep cap 12 → 15, `CACHE_VERSION` 2 → 3.
- [x] Bodyweight E1RM moved onto the same **Mayhew equation** applied to the total system weight, replacing the hand-tuned dips/pull-ups/chin-ups coefficient tables (which sat ~2–3% below Mayhew on logged data): cap 30 → 15 (`MAX_BODYWEIGHT_REPS`), `CACHE_VERSION` 3 → 4.
- [x] Remove RIR; revert DB to v5 and key cache rebuild on a formula version (`94a1f10`).
