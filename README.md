# TrainCue

A Wear OS training-plan companion for Galaxy Watch.

TrainCue combines the ideas from the other two watch apps:

- **PaceCue** is for interval run/walk sessions with beeps, vibration, and spoken phase cues.
- **LiftCue** is for strength routines with exercise, sets, reps, and tap-to-complete rows.
- **TrainCue** is the combined plan app: it can show a training day containing runs, strength work, rest, cross-training, or any other plan item.

## First version

- Manual GitHub update from `routines.json`
- Local cached plan when GitHub is unavailable
- Tap-to-complete plan items
- Delete a day from the local watch plan after finishing it
- Strength items can include exercise sets and reps
- Individual strength exercises can be ticked off separately
- Run items are identified by `"type": "run"`
- Outdoor run tracking uses watch location when available
- Spoken run cues announce each kilometre and completion
- Treadmill/manual completion option for indoor runs

## JSON format

TrainCue does not need to know a specific training plan. Add whatever plan you want to `routines.json`.

The important run identifier is:

```json
"type": "run"
```

Run items can use either `distanceMiles` or `distanceKm`:

```json
{
  "id": "w1-tue-run",
  "type": "run",
  "label": "2 mi run",
  "distanceMiles": 2
}
```

Strength items can include workouts:

```json
{
  "id": "w1-thu-strength",
  "type": "strength",
  "label": "Upper body strength",
  "workouts": [
    { "name": "Bench Press", "sets": 3, "reps": "10" },
    { "name": "Shoulder Press", "sets": 3, "reps": "8-10" }
  ]
}
```

Full day example:

```json
{
  "id": "week1-thursday",
  "title": "Week 1 Thursday",
  "subtitle": "Run + strength",
  "items": [
    {
      "id": "w1-thu-run",
      "type": "run",
      "label": "2 mi run",
      "distanceMiles": 2
    },
    {
      "id": "w1-thu-strength",
      "type": "strength",
      "label": "Upper body strength",
      "workouts": [
        { "name": "Bench Press", "sets": 3, "reps": "10" }
      ]
    }
  ]
}
```

## GitHub Routine Import

The app can import routines from a public GitHub-hosted JSON file when you tap `Update` on the watch.

1. Create `routines.json` in a public GitHub repository.
2. Use the raw file URL, for example `https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/routines.json`.
3. Paste that URL into `ROUTINE_FEED_URL` near the top of `app/src/main/java/com/jongrady/galaxywatchsplits/MainActivity.kt`.

If GitHub can be reached, the watch replaces its saved plan with the latest JSON. If GitHub cannot be reached, it keeps the saved watch copy. Locally deleted days will come back the next time you manually update from GitHub.

## Open in Android Studio

Open this folder in Android Studio, let Gradle sync, then run the `app` configuration on a Wear OS emulator or paired Galaxy Watch.
