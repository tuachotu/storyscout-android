# StoryScout Android Status

- Active approved plan: maintained in the parent StoryScout workspace planning records.
- Current phase: Implementation and emulator validation complete; physical-device reliability validation remains.
- Scope: Native Android app only. The iPhone, web, and backend projects remain reference-only.
- Minimum SDK: 26.
- Production API: `https://story-scout.app/api/v1/`.
- External writes, production calls, commits, deployments, credentials, and Play Console actions: not authorized.
- Validation: 12 JVM tests pass; 2 instrumentation tests pass on API 35 phone, API 35 tablet, and API 26 phone; debug/release builds and lint pass.
- Next: install the `deviceDebug` APK on physical hardware and perform the long-recording/reliability checklist.
- GitHub: published on `main` at `https://github.com/tuachotu/storyscout-android` after a clean public-file and secret audit.
