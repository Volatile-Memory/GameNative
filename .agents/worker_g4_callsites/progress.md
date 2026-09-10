# Progress — worker_g4_callsites

Last visited: 2026-09-04T19:55:00Z

## Status: Completed Group 4 Call Sites refactoring; compileModernDebugKotlin succeeded cleanly (code 0)

- [x] Initialized DISPATCH.md, BRIEFING.md, progress.md
- [x] Read referenced documents: ORIGINAL_REQUEST.md, PROJECT.md, worker_g4_steam progress/briefing, worker_g4 progress, explorer_g4 reports
- [x] Identified and fixed KSP2 IllegalStateException crash caused by nested typealiases in AmazonService and GOGService companion objects
- [x] Fix Manager/Service internal compilation errors:
  - [x] SteamManagerInput.kt syntax and KeyValue imports
  - [x] SteamManagerAutoCloud.kt nullability/type mismatches
  - [x] SteamManager.kt missing functions and type mismatches
  - [x] EpicDownloadManager.kt and GOGDownloadManager.kt references
  - [x] AmazonDownloadManager.kt references
- [x] Fix Call Sites in ViewModels:
  - [x] DownloadsViewModel.kt (injected SteamManager, EpicManager, GOGManager, AmazonManager)
  - [x] LibraryViewModel.kt
  - [x] UserLoginViewModel.kt (injected SteamManager)
  - [x] MainViewModel.kt (injected SteamManager)
- [x] Fix Call Sites in UI screens:
  - [x] SteamAppScreen.kt
  - [x] PluviaMain.kt
  - [x] ContainerConfigDialog.kt
  - [x] Settings dialogs (WineProtonManagerDialog, DriverManagerDialog, SettingsGroupDebug)
  - [x] XServerScreen.kt
- [x] Fix Call Sites in Launch / Storage / Utilities / Downloaders:
  - [x] Downloaders (ContainerFilesDownloader, CoreDriverDownloader, DXWrapperDownloader, GraphicsDriverDownloader, WinComponentDownloader, UpdateInstaller, ManifestInstaller)
  - [x] Dependencies (GogScriptInterpreterDependency, BionicDefaultProtonDependency, GOGDependencyFix)
  - [x] MainActivity.kt
  - [x] AchievementWatcher.kt
- [x] Run `./gradlew compileModernDebugKotlin` (BUILD SUCCESSFUL in 2m 21s)
- [x] Verify 0 EntryPointAccessors.fromApplication and PreferencesEntryPoint in targeted classes
- [x] Write handoff.md and send message to parent
