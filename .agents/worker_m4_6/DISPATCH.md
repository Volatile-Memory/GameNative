## 2026-08-31T19:12:00Z
You are Worker M4 Replacement (`worker_m4_6`) responsible for completing Milestone 4: ViewModels & UI/Screens Layer Migration.
Working Directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m4_6
Project Root: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
Authoritative Request: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
Master Project Plan: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md
Domain Interfaces Reference: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\app\src\main\java\app\gamenative\preferences\
Survey details: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_2_replacement\handoff.md

Your Exclusive Write Ownership (42 files):
1. `app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt`
2. `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt`
3. `app/src/main/java/app/gamenative/ui/model/MainViewModel.kt`
4. `app/src/main/java/app/gamenative/ui/model/UserLoginViewModel.kt`
5. `app/src/main/java/app/gamenative/ui/data/HomeState.kt`
6. `app/src/main/java/app/gamenative/ui/data/LibraryState.kt`
7. `app/src/main/java/app/gamenative/ui/enums/LibraryTab.kt`
8. `app/src/main/java/app/gamenative/MainActivity.kt`
9. `app/src/main/java/app/gamenative/PluviaApp.kt` (migrate preference usages to PreferencesEntryPoint / GeneralPreferences / LibraryPreferences; keep PrefManager.init(this) for now until Milestone 6)
10. `app/src/main/java/app/gamenative/ui/PluviaMain.kt`
11. `app/src/main/java/app/gamenative/ui/component/AchievementOverlay.kt`
12. `app/src/main/java/app/gamenative/ui/component/GamepadActionBar.kt`
13. `app/src/main/java/app/gamenative/ui/component/QuickMenu.kt`
14. `app/src/main/java/app/gamenative/ui/component/dialog/ControllerTab.kt`
15. `app/src/main/java/app/gamenative/ui/component/dialog/OrientationDialog.kt`
16. `app/src/main/java/app/gamenative/ui/component/dialog/SingleChoiceDialog.kt`
17. `app/src/main/java/app/gamenative/ui/screen/library/FeaturedCtaButton.kt`
18. `app/src/main/java/app/gamenative/ui/screen/library/LibraryAppScreen.kt`
19. `app/src/main/java/app/gamenative/ui/screen/library/LibraryScreen.kt`
20. `app/src/main/java/app/gamenative/ui/screen/library/RecommendedGameScreen.kt`
21. `app/src/main/java/app/gamenative/ui/screen/library/RecommendedTabPane.kt`
22. `app/src/main/java/app/gamenative/ui/screen/library/appscreen/CustomGameAppScreen.kt`
23. `app/src/main/java/app/gamenative/ui/screen/library/appscreen/SteamAppScreen.kt`
24. `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryAppItem.kt`
25. `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryCarouselPane.kt`
26. `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryDetailPane.kt`
27. `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryListPane.kt`
28. `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryOptionsPanel.kt`
29. `app/src/main/java/app/gamenative/ui/screen/library/components/LibrarySearchBar.kt`
30. `app/src/main/java/app/gamenative/ui/screen/library/components/RecommendationDisclosure.kt`
31. `app/src/main/java/app/gamenative/ui/screen/library/components/SystemMenu.kt`
32. `app/src/main/java/app/gamenative/ui/screen/settings/FrontendSyncDialog.kt`
33. `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupDebug.kt`
34. `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupEmulation.kt`
35. `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInfo.kt`
36. `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInterface.kt`
37. `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupPerformance.kt`
38. `app/src/main/java/app/gamenative/ui/screen/settings/SettingsScreen.kt`
39. `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`
40. `app/src/main/java/app/gamenative/ui/util/SteamSaveTransfer.kt`
41. `app/src/main/java/app/gamenative/ui/util/WindowSize.kt`
42. `app/src/main/java/app/gamenative/ui/widget/PerformanceHudView.kt`
