# DroidPulse

**A local-first Android console for personal telemetry.**

DroidPulse brings call, connectivity, location and folder monitoring together in one app. Its goal is to make it easier to keep a searchable record of activity on your own device, export it, and optionally send selected files to a remote destination.

> **Project status:** DroidPulse is under development. The repository contains code for the modules described below, but functionality, reliability and compatibility have not all been independently verified. Treat this README as a working guide, not a guarantee that every feature is complete.

## What DroidPulse is designed to do

| Module | Purpose | Intended output |
| --- | --- | --- |
| **Calls and VoIP** | Record cellular call details and detect supported VoIP calls from notifications. | Local call records, calendar events and CSV export. |
| **Connectivity** | Monitor network changes and capture Wi-Fi or mobile connectivity information. | Local records and CSV export. |
| **GPS** | Capture passive location updates and, when enabled, request active location updates. | Local records, CSV and GPX tracks. |
| **Folder watcher** | Detect new files in a selected folder and queue them for a configured destination. | File history and uploads to Google Drive, Dropbox or FTP/FTPS. |

Each module is intended to be controlled independently from a single console. Depending on the module and device, background operation may use a foreground service, scheduled work or system events. The app also includes code for restoring enabled work after a reboot and for importing declarative JSON recipes.

## Getting started

### Requirements

- An Android device running **Android 7.0 (API 24) or newer**, as specified in the app's build configuration. Individual background features can behave differently across Android versions and devices.
- Android Studio with the Android SDK required by the project. The current build configuration specifies **compile SDK 36.1** and **target SDK 36**.
- The permissions and device settings needed for whichever modules you enable.

### Build from source

1. Clone or download [this repository](https://github.com/vincentdeklerk/DroidPulse) and open it in Android Studio.
2. Install any SDK components Android Studio requests and allow Gradle to sync.
3. Review `.env.example` and configure any needed local values in `.env`. Never commit real credentials.
4. Check the signing configuration in `app/build.gradle.kts`: the current debug build expects a `debug.keystore` in the project root. Release signing also requires a keystore and environment variables. Supply your own signing setup before building.
5. Build and run on a test device. The repository snapshot reviewed for this draft does not include the Gradle wrapper scripts, so a `./gradlew` command is not assumed here.

Build instructions may need adjustment as the project develops. Cloud integrations also require their own account and credential setup.

### Configure the app

1. Open the console and enable only the modules you want to use.
2. Grant the permissions requested for those modules. For call logging, choose a writable device calendar if you want calendar events.
3. For GPS, select a suitable location mode and interval. For the folder watcher, select a folder and configure a destination.
4. Check the app's logs and exports after a test call, location update or file addition. Recheck after a reboot and after changing battery settings.

## Privacy and security

DroidPulse can handle sensitive information, including call details, notification content, locations, file names and files. Use it on devices and data you are authorised to monitor, and consider the privacy of other people whose information may appear in records.

- The design uses an on-device Room/SQLite database for telemetry. Calendar records may sync through the calendar account you choose. Files sent to a configured destination leave the device.
- VoIP detection requires notification access. Android grants notification listeners broad access to notifications, not just call notifications. DroidPulse's call listener examines notification titles and text for matching apps; notification-based detection is not guaranteed to be complete or accurate.
- Protect exported CSV/GPX files, calendar entries, cloud accounts and backups. Do not publish logs containing personal information.
- Treat imported recipes as configuration from an external source. Only import from sources you trust, preferably over HTTPS, and review any endpoints they contain.
- Prefer FTPS to unencrypted FTP when available. Do not put passwords, access tokens, API keys or signing keys in the repository. Review credentials and their restrictions before publishing a build.

## Android limitations

- **Background work is not exact.** Android may delay or stop work because of battery optimisation, permissions, device-vendor restrictions or system state. Periodic WorkManager jobs have a minimum repeat interval of 15 minutes, so shorter intervals need a different mechanism and are not guaranteed.
- **Foreground services have restrictions.** They display a notification, require the appropriate permissions and service types, and cannot always be started from the background or after reboot. Location has additional restrictions.
- **VoIP detection is best-effort.** It relies on notification content and matching rules, which can change between apps, languages and versions. Direction, caller and duration may be incomplete or inaccurate. It is not a recording of call audio.
- **Passive location is opportunistic.** Updates depend on location fixes being available and the app having suitable permissions. Location results and battery use vary; active tracking consumes more power.
- **Folder monitoring is subject to Android storage restrictions.** Folder access depends on the selected document provider and persisted permissions. Polling can miss files that appear and disappear between scans. A retry queue cannot guarantee that every file or event is captured or uploaded.
- **Call-log and background permissions may affect distribution.** Google Play applies policy restrictions to sensitive permissions and background behaviour. Check current requirements before publishing.

Records can be useful for personal reference, but gaps or inaccuracies are possible. Do not assume they are complete, tamper-proof or sufficient as legal evidence. Preserve original records and use an appropriate evidence-handling process when needed.

## Extensibility

The source includes an importer for declarative JSON recipes, including VoIP notification matchers and webhook push rules. Recipes are configuration, not native executable plugins. New Android permissions, services or other native capabilities require changes to the app and a new build. Recipe behaviour and security should be reviewed and tested before use.

A possible future direction is a read-only MCP bridge over the local repositories. It is a proposal, not a documented current feature.

## Technology

The repository uses Kotlin, Jetpack Compose and Material 3 for the Android interface, Room/SQLite for local data, and WorkManager and Android services for background work. Other dependencies include Android's document APIs, Google Play services location, OkHttp and Apache Commons Net.

## To do

- [ ] Finish testing the app on Android devices.
- [ ] Add an APK release.
- [ ] Make DroidPulse available through F-Droid.
- [ ] Set up distribution through the Google Play Store.

## Contributing

Issues, testing and improvements are welcome. When reporting a problem, include your Android version, device model, the module involved and steps to reproduce it. Remove personal data, credentials and sensitive logs before sharing.

## Licence

This project is licensed under the [GNU General Public License v3.0 (GPLv3)](LICENSE).
