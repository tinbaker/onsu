# Onsu

A small Android music player built with Kotlin and Jetpack Compose. Onsu builds a
personalised queue from an online catalogue, keeps a library that is isolated per
account, and can also play audio already stored on the device — but it **never mixes
local files into recommendations**.

> This is a **private** repository, shared only with invited collaborators.

## Highlights

- Recommendations come only from the online catalogue (Audius discovery API, with a
  small SoundHelix fallback). Local files are reachable exclusively from the
  "Music on this device" screen, Search, or a manually built queue.
- Pull down at the top of the home screen to reload recommendations.
- The queue starts with 20 tracks; scrolling to the end appends 10 more.
- Likes, playlists, taste profile and playback session are isolated per account
  (`ownerId`; guests use `"guest"`), stored in Room v4 with exported schemas and
  explicit migrations.
- Google Sign-In through Credential Manager, with a Guest mode always available.
- English UI by default, Vietnamese switchable in Settings; Dark / Light / System
  theme persisted in `AppPreferences`.
- Playback via Media3 / ExoPlayer behind a `MediaSessionService`.
- 56 unit tests covering queue scoring, account isolation, DB migration and downloads.

## Requirements

| | |
|---|---|
| IDE | A recent Android Studio supporting AGP 9.1.1 / Gradle 9.3.1 |
| JDK | 23 |
| Android SDK | Platform 36.1 (`compileSdk 36.1`), Build Tools 36.1.0 |
| App | `minSdk 24`, `targetSdk 36`, `applicationId com.onsu.music` |

A device or emulator running Android 7.0+ is needed to run the app.

## Getting started

1. Clone the repository (you must be invited as a collaborator first).
2. Create your local environment file from the template:

   ```sh
   cp .env.example .env
   ```

   `.env` is git-ignored and is **not** part of this repository. Without it the app
   still builds and runs in Guest mode; only Google Sign-In is disabled. See
   [README-AUTH.md](README-AUTH.md) (Vietnamese) for the full OAuth setup.
3. Build the debug APK with the bundled Gradle wrapper (no separate Gradle install):

   ```sh
   ./gradlew :app:assembleDebug          # Windows: gradlew.bat :app:assembleDebug
   ```

   Output: `app/build/outputs/apk/debug/app-debug.apk`.
   Alternatively, open the project in Android Studio and press **Run**.
4. Set `JAVA_HOME` to JDK 23 and point `ANDROID_HOME` (or `local.properties`) at your
   Android SDK. `local.properties` is ignored by git.

`debug.keystore` is committed on purpose: every clone then produces the same debug
SHA-1 (`21:99:BA:E4:B3:CC:B2:30:55:15:AF:10:6B:B8:20:E8:A1:66:59:65`), which
matches the Android OAuth client documented in README-AUTH.md. It is a **debug-only**
key with the public password `android`; never use it to sign a release build.

## Configuration

The Secrets Gradle Plugin reads `.env` (falling back to `.env.example`) and exposes
the values through `BuildConfig`.

| Key | Purpose |
|---|---|
| `GOOGLE_WEB_CLIENT_ID` | OAuth "Web application" Client ID used by Credential Manager |
| `AUTH_REQUIRE_OTP` | `false` by default (Google Sign-In only) |
| `OTP_PROVIDER`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `OTP_FUNCTION_URL` | Only used when OTP is enabled |

The `release` build type additionally needs `KEYSTORE_PATH`, `STORE_PASSWORD` and
`KEY_PASSWORD`; debug builds need none of them. `google-services.json` is likewise
local-only — the Google Services plugin is configured to warn instead of failing
when it is missing.

## Tests

```sh
./gradlew :app:testDebugUnitTest
```

Room schemas are exported to `app/schemas/`. Keep those JSON files committed:
`MigrationTest` opens a real version-1 database and replays the migrations against
them.

## Project layout

| Path | Contents |
|---|---|
| `app/src/main/java/com/onsu/MainActivity.kt` | Single Activity + Compose navigation |
| `data/` | Room entities/DAOs, repository, Audius API, MediaStore scanner, downloads, auth |
| `scoring/` | Taste scoring and `QueueManager` (paging + refresh) |
| `player/` | MediaSessionService, ExoPlayer bridge, queue reconciler |
| `ui/`, `i18n/`, `settings/`, `ui/theme/` | Compose screens, strings, preferences, palette |
| `firebase/functions/` | Optional Cloud Function for email OTP (unused by default) |

## Data sources & credits

- Track metadata and streams: [Audius](https://audius.co) discovery API
  (`/v1/tracks/trending`).
- Fallback demo audio: [SoundHelix](https://www.soundhelix.com/audio-examples)
  Creative Commons sample MP3s.
- Fallback artwork: [Picsum Photos](https://picsum.photos).
- Profile avatars: loaded with Coil from the Google ID token.

**No audio files are bundled in this repository.** Tracks remain the property of
their respective owners and usage must follow each provider's terms. Onsu is a
student/demo project, not a commercial music service.

## Other documents

- `README-AUTH.md` — Google Sign-In / OAuth setup and troubleshooting (Vietnamese)
- `PLAN.md`, `implementation_plan.md` — historical planning notes

## License

[MIT](LICENSE) — see [LICENSE](LICENSE) for the full text.
