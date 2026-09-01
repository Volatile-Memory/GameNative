# Progress — Worker M2 (Data, Core & Utilities Layer Migration)

Last visited: 2026-08-31T17:35:00+05:00

- [x] Initialized DISPATCH.md, BRIEFING.md, and progress.md.
- [ ] Inspect and migrate Batch 1 (Data & Mod layer):
  - [ ] `app/src/main/java/app/gamenative/data/DefaultFavoritesRepository.kt`
  - [ ] `app/src/main/java/app/gamenative/data/FavoritesManager.kt`
  - [ ] `app/src/main/java/app/gamenative/data/RecommendationRepository.kt`
  - [ ] `app/src/main/java/app/gamenative/data/SteamCollectionRepository.kt`
  - [ ] `app/src/main/java/app/gamenative/data/gog/GogSeedCollector.kt`
  - [ ] `app/src/main/java/app/gamenative/di/AppThemeModule.kt`
  - [ ] `app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt`
  - [ ] `app/src/main/java/app/gamenative/mods/NexusModManager.kt`
- [ ] Inspect and migrate Batch 2 (Utils Layer 1):
  - [ ] `app/src/main/java/app/gamenative/utils/BestConfigService.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/ContainerStorageManager.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/ContainerUtils.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/ConversionTracker.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/CustomGameScanner.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/DeviceGameStatsCache.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/DownloadSpeedConfig.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/GameCompatibilityCache.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/GpuGameStatsCache.kt`
- [ ] Inspect and migrate Batch 3 (Utils Layer 2):
  - [ ] `app/src/main/java/app/gamenative/utils/HltbService.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/IntentLaunchManager.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/KeyAttestationHelper.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/ManifestRepository.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/PaddingUtils.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/PlayIntegrity.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/SteamGridDB.kt`
  - [ ] `app/src/main/java/app/gamenative/utils/SteamUtils.kt`
- [ ] Compilation & Verification (`./gradlew compileModernDebugKotlin`).
- [ ] Write handoff report (`handoff.md`).
