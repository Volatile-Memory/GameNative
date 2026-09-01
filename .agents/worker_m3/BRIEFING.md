# BRIEFING — 2026-08-31T12:32:45Z

## Mission
Execute Milestone 3: Services & Background Layer Migration. Migrate 17 service/background layer files from PrefManager to domain preference interfaces.

## 🔒 My Identity
- Archetype: worker
- Roles: implementer, qa, specialist
- Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m3
- Original parent: dacc0236-7f70-4e9f-a26d-6b6f0e7ae794
- Milestone: M3 (Services & Background Layer Migration)

## 🔒 Key Constraints
- Only edit the 17 assigned files in write ownership:
  1. `app/src/main/java/app/gamenative/CrashHandler.kt`
  2. `app/src/main/java/app/gamenative/service/AchievementWatcher.kt`
  3. `app/src/main/java/app/gamenative/service/DownloadService.kt`
  4. `app/src/main/java/app/gamenative/service/NexusModImportService.kt`
  5. `app/src/main/java/app/gamenative/service/NotificationHelper.kt`
  6. `app/src/main/java/app/gamenative/service/SteamAutoCloud.kt`
  7. `app/src/main/java/app/gamenative/service/SteamService.kt`
  8. `app/src/main/java/app/gamenative/service/SteamWishlistService.kt`
  9. `app/src/main/java/app/gamenative/service/amazon/AmazonConstants.kt`
  10. `app/src/main/java/app/gamenative/service/amazon/AmazonService.kt`
  11. `app/src/main/java/app/gamenative/service/epic/EpicConstants.kt`
  12. `app/src/main/java/app/gamenative/service/epic/EpicDownloadManager.kt`
  13. `app/src/main/java/app/gamenative/service/epic/EpicManager.kt`
  14. `app/src/main/java/app/gamenative/service/epic/EpicService.kt`
  15. `app/src/main/java/app/gamenative/service/gog/GOGConstants.kt`
  16. `app/src/main/java/app/gamenative/service/gog/GOGService.kt`
  17. `app/src/main/java/app/gamenative/workshop/WorkshopManager.kt`
- Remove all `import app.gamenative.PrefManager` from these files.
- Migrate to domain preference interfaces via `@Inject` or `PreferencesEntryPoint.get(context)` / `context.preferencesEntryPoint()`.
- Do not cheat, genuine migration only.
- Run build/tests and verify clean compilation.

## Current Parent
- Conversation ID: dacc0236-7f70-4e9f-a26d-6b6f0e7ae794
- Updated: 2026-08-31T12:32:45Z

## Task Summary
- **What to build**: Refactor 17 files in the services and background layer to use granular domain preferences (`AuthPreferences`, `ContainerPreferences`, `InputPreferences`, `HudPreferences`, `LibraryPreferences`, `DownloadPreferences`, `GeneralPreferences`) instead of monolithic `PrefManager`.
- **Success criteria**: All 17 files compile cleanly, zero references to `PrefManager` in these files, unit tests pass.

## Change Tracker
- **Files modified**: [TBD]
- **Build status**: [TBD]
- **Pending issues**: None

## Quality Status
- **Build/test result**: [TBD]
- **Lint status**: [TBD]
- **Tests added/modified**: [TBD]

## Key Decisions Made
- [TBD]

## Artifact Index
- [TBD]
