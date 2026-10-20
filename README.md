# EE Timeline

A night-friendly podcast companion for listening to *Everything Everywhere Daily* in AP World History curriculum order. Follow the journey through nine eras, browse episodes on a compressed historical timeline, save progress, download stories for offline listening, plan tonight’s listening, and use bedtime mode with a sleep rewind, voice boost, and silence skipping.

## Build and install

Requires JDK 17+ and Android SDK platform 37 or newer. Build a debug APK with:

```sh
./gradlew assembleDebug
```

Install on a connected device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Screenshots

- [Journey](docs/screenshots/journey.png)
- [Timeline](docs/screenshots/timeline.png)
- [Timeline — modern era](docs/screenshots/timeline-modern.png)
- [Units](docs/screenshots/units.png)
- [Unit detail](docs/screenshots/unit-detail.png)
- [Episode sheet](docs/screenshots/episode-sheet.png)
- [Now Playing with sleep timer](docs/screenshots/now-playing.png)

- [Bedtime player](docs/screenshots/bedtime-player.png)
- [Bedtime settings](docs/screenshots/bedtime-settings.png)
- [Bedtime audio settings](docs/screenshots/bedtime-settings-audio.png)
- [Sleep-session rewind card](docs/screenshots/bedtime-drift-off.png)
- [Sleep-session spot chooser](docs/screenshots/bedtime-sleep-session-sheet.png)
- [Planned queue stop](docs/screenshots/bedtime-plan-stop.png)
- [Bedtime player after shake extension](docs/screenshots/bedtime-player-shake.png)
- [Drift-off card at the top of Journey](docs/screenshots/bedtime-journey-priority.png)
- [Finite journey plan previews](docs/screenshots/bedtime-plan-labels.png)
- [Bedtime player with remembered timer and active plan](docs/screenshots/bedtime-now-playing-start.png)
- [Settings list above the mini player](docs/screenshots/bedtime-settings-with-mini-player.png)
- [Bedtime player with active timer](docs/screenshots/bedtime-now-playing-active-timer.png)


## Curriculum data

`app/src/main/assets/curriculum.json` is generated from the RSS feed and curriculum mapping. To regenerate it, see [`tools/dataset/`](tools/dataset/) for the data tooling and instructions.
