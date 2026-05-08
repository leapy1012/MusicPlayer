# AGENTS.md — Offline Music Player Project Rules

## Project goal

This is an Android offline music player project for an AOSP/OEM-style system app.

The app should follow a modern Kotlin Android architecture using:

- Kotlin
- XML layouts + ViewBinding
- MVVM
- Clean Architecture style
- Media3 for playback
- Room for local database
- Hilt for dependency injection
- Coroutines and Flow
- Navigation Component
- BottomSheetBehavior for the player panel
- MediaStore for local audio scanning
- OEM-style dynamic theme/background system

Do not rewrite the whole project unless explicitly requested. Work incrementally and preserve existing behavior.

---

## Required package structure

Use this target package structure when creating or moving files:

```text
com/android/music/
├── MusicApplication.kt
├── MainActivity.kt
│
├── core/
│   ├── constants/
│   ├── dispatcher/
│   ├── extension/
│   ├── permission/
│   ├── result/
│   ├── theme/
│   ├── artwork/
│   ├── audio/
│   └── view/
│
├── data/
│   ├── db/
│   │   ├── MusicDatabase.kt
│   │   ├── dao/
│   │   └── entity/
│   ├── datastore/
│   ├── mapper/
│   ├── media/
│   ├── metadata/
│   └── repository/
│
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
│       ├── song/
│       ├── album/
│       ├── artist/
│       ├── folder/
│       ├── playlist/
│       ├── favorite/
│       ├── history/
│       ├── queue/
│       ├── playback/
│       ├── scanner/
│       └── theme/
│
├── playback/
│   ├── service/
│   ├── session/
│   ├── controller/
│   ├── notification/
│   ├── queue/
│   ├── state/
│   └── mapper/
│
├── scanner/
│   ├── MusicScanner.kt
│   ├── MusicScanManager.kt
│   ├── MusicScanWorker.kt
│   ├── ScanState.kt
│   └── ScanScheduler.kt
│
├── ui/
│   ├── common/
│   ├── main/
│   ├── home/
│   ├── songs/
│   ├── albums/
│   ├── artists/
│   ├── folders/
│   ├── playlists/
│   ├── favorites/
│   ├── recent/
│   ├── mostplayed/
│   ├── player/
│   ├── queue/
│   ├── search/
│   ├── settings/
│   ├── theme/
│   └── equalizer/
│
├── di/
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   ├── RepositoryModule.kt
│   ├── PlaybackModule.kt
│   ├── ScannerModule.kt
│   ├── ThemeModule.kt
│   └── DispatcherModule.kt
│
├── receiver/
│   ├── BootCompletedReceiver.kt
│   ├── HeadsetReceiver.kt
│   └── BluetoothReceiver.kt
│
└── util/
    ├── TimeFormatter.kt
    ├── ColorUtils.kt
    ├── BitmapUtils.kt
    └── LogUtils.kt