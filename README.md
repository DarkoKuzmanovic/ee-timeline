# EE Timeline

A night-friendly podcast companion for listening to *Everything Everywhere Daily* in AP World History curriculum order. Follow the journey through nine eras, browse episodes on a compressed historical timeline, save progress, download stories for offline listening, and set a gentle sleep timer.

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


## Curriculum data

`app/src/main/assets/curriculum.json` is generated from the RSS feed and curriculum mapping. To regenerate it, see [`tools/dataset/`](tools/dataset/) for the data tooling and instructions.
