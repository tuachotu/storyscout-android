# StoryScout for Android

Native Android application for recording and uploading StoryScout stories. The app is built with Kotlin, Jetpack Compose, and Material 3 and supports Android 8.0 (API 26) and newer phones and tablets.

## User flow

1. Enter a participant access code (GUID) to create a six-hour access session.
2. Record, pause, resume, and stop a story.
3. Review the protected local recording with Play/Stop playback.
4. Upload through the StoryScout create-recording, presigned-upload, and completion API flow.
5. Receive the completed recording ID while retaining local Play/Stop playback.
6. Fetch and display the completed recording's transcription on demand.

The participant GUID is never persisted. An active recording is not stopped or deleted when its access session expires.

## Reliability design

- AAC-LC audio in an M4A/MP4 container, mono at 44.1 kHz and approximately 96 kbps.
- Audio streams directly to an app-private file and is not held in memory.
- A microphone foreground service and persistent notification keep recording active while the app is backgrounded or the screen is locked.
- Pause, Resume, and Stop notification actions are available during capture.
- A partial wake lock supports long locked-screen recordings.
- Room persists recording metadata and recovery state across process and device restarts.
- Unique WorkManager jobs stream files to presigned URLs and prevent duplicate create-recording requests.
- Files remain on the device until the backend acknowledges completion.
- Audio interruptions and unexpected service termination are surfaced as possible recording gaps.
- Expired presigned upload URLs enter an explicit blocked state without silently creating duplicate backend records.

## Architecture

- `ui/`: Compose screens, theme, and lifecycle-aware application state.
- `recording/`: foreground recording service, runtime state, and local playback.
- `data/local/`: Room entities/DAO/database and temporary access-session storage.
- `data/remote/`: Retrofit API contract, OkHttp streaming upload, and API configuration validation.
- `data/RecordingRepository.kt`: recording persistence and create/upload/complete orchestration.
- `upload/UploadWorker.kt`: durable, network-constrained upload recovery.
- `domain/`: recording state machine, timer formatting, and session countdown rules.

## Requirements

- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 34/35
- Android Studio or the included Gradle 8.9 wrapper

The application compiles with SDK 35 and has `minSdk = 26`.

## API configuration

Build variants deliberately separate local development from production-connected device testing:

| Variant | API base URL | Installable | Purpose |
| --- | --- | --- | --- |
| `debug` | `http://10.0.2.2:3001/api/v1/` | Debug-signed | Emulator with a local backend |
| `deviceDebug` | `https://story-scout.app/api/v1/` | Debug-signed | Physical-device testing |
| `release` | `https://story-scout.app/api/v1/` | Unsigned | Production release preparation |

Production-connected variants reject localhost and loopback API URLs. Building or installing an app does not call the API, but entering an access code or uploading from `deviceDebug` writes to the production backend.

After an upload completes, **Fetch transcription** calls the authenticated
`GET /recordings/{recordingId}/transcription` endpoint. The response is displayed in the current screen and is not added to the local Room database. Restarting the flow therefore requires fetching it again.

## Build and test

Set your Android SDK location through `ANDROID_HOME` or Android Studio, then run:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleDeviceDebug
./gradlew assembleRelease
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
```

Generated APKs are written beneath `app/build/outputs/apk/` and are intentionally ignored by Git.

## Install on a physical device

Enable Developer options and USB debugging, connect the device, and verify it is authorized:

```bash
adb devices -l
```

Build and install the production-connected test variant:

```bash
./gradlew assembleDeviceDebug
adb install -r app/build/outputs/apk/deviceDebug/app-deviceDebug.apk
```

## Permissions

- `RECORD_AUDIO`: captures the story.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE`: keep capture active in the background.
- `POST_NOTIFICATIONS` on Android 13+: displays the persistent recording notification.
- `WAKE_LOCK`: supports reliable long-running locked-screen capture.
- `INTERNET`: validates access and uploads recordings.

The app uses private internal storage, disables application backup, and requests no broad storage permission.

## Access-session behavior

- Sessions last six hours according to the server-provided `expiresAt` value.
- No timer is shown while more than 30 minutes remain.
- A countdown appears during the final 30 minutes.
- The countdown uses the error color during the final five minutes.
- Expiration never stops an active recording.
- An expired session requires access-code re-entry before upload or transcription fetching; the local recording is preserved.

## Automated validation

The project includes coverage for:

- API URL construction and production loopback rejection.
- Session countdown thresholds and timer formatting beyond two hours.
- Recording state transitions.
- Room persistence and interrupted-capture recovery.
- Duplicate server-record prevention.
- Access-session, metadata, authorization, presigned upload method/headers, completion, and transcription requests using MockWebServer.
- Compose access-screen and persistence smoke tests.

The initial implementation was validated with 12 JVM tests and two instrumentation tests on an API 35 phone, API 35 tablet, and API 26 phone emulator. Debug, device-debug, and release builds pass; Android lint reports zero errors.

## Physical-device validation checklist

Complete these checks before treating the app as production-ready:

- Record for 30, 60, and 120 minutes with the screen locked.
- Background and reopen the app during recording.
- Repeat with Battery Saver enabled.
- Disconnect and restore networking before/during upload.
- Test an incoming call, alarm, and other audio-focus interruption.
- Connect/disconnect Bluetooth or a headset during capture.
- Terminate and restart the app process.
- Restart the device and verify completed local recordings remain available.
- Verify queued upload recovery.
- Confirm uploaded M4A playback and transcription through the backend.

Long recordings, real telephony/audio-route behavior, manufacturer battery management, and final backend audio compatibility require physical-device testing.

## Known backend constraint

The current API does not provide an endpoint to refresh an expired presigned URL for an existing recording. StoryScout preserves the local file and server recording ID and stops automatic retries rather than creating a duplicate server record. A backend URL-refresh contract would need separate approval and implementation.
