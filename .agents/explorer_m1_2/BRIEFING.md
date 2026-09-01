# BRIEFING — 2026-08-31T11:32:00Z

## Mission
Investigate and design the exact Hilt DI module setup and non-Hilt EntryPoint pattern for Milestone 1 (PreferencesDataStoreModule/PreferencesModule, PreferencesEntryPoint, package structure, annotations, qualifiers, and compilation requirements).

## 🔒 My Identity
- Archetype: explorer
- Roles: investigator, architect
- Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m1_2
- Original parent: 39631fec-37ca-4d7d-9fe3-fdb7c715ad76
- Milestone: Milestone 1 (Hilt DI & EntryPoint Infrastructure)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement project source code
- Produce self-contained 5-component handoff report in handoff.md
- Adhere strictly to project conventions, package structure, and Hilt best practices

## Current Parent
- Conversation ID: 39631fec-37ca-4d7d-9fe3-fdb7c715ad76
- Updated: not yet

## Investigation State
- **Explored paths**:
  - `app/src/main/java/app/gamenative/PrefManager.kt`
  - `app/src/main/java/app/gamenative/di/` (`RepositoryModule.kt`, `DatabaseModule.kt`, `AppThemeModule.kt`)
  - `app/src/main/java/app/gamenative/core/coroutines/CoroutinesModule.kt`
  - `app/src/main/java/app/gamenative/core/runtime/GameSessionEntryPoint.kt`
  - `app/src/main/java/app/gamenative/mods/NexusModManager.kt`
  - `app/src/main/java/app/gamenative/utils/ContainerStorageManager.kt`
  - `app/src/main/java/com/winlator/core/WineUtils.java`
  - `app/src/main/java/com/winlator/xenvironment/components/BionicProgramLauncherComponent.java`
  - `app/src/main/java/app/gamenative/MainActivity.kt` & `SteamService.kt` (`attachBaseContext`)
  - `app/build.gradle.kts` & `gradle/libs.versions.toml`
- **Key findings**:
  - Single central DataStore `"PluviaPreferences"` qualified with `@PluviaDataStore` in `PreferencesDataStoreModule`.
  - 7 domain interface bindings in `PreferencesBindingModule` via `@Binds @Singleton`.
  - `PreferencesEntryPoint` in `app.gamenative.preferences` exposing 7 domain getters + static `get(context)` helper.
  - Complete mapping for non-Hilt callers: Java (`WineUtils.java`, `BionicProgramLauncherComponent.java`), early lifecycle hooks (`MainActivity.attachBaseContext`, `SteamService.attachBaseContext`), and Kotlin objects (`ContainerStorageManager`, `FrontendSyncManager`, `NexusModManager`, `CrashHandler`).
- **Unexplored areas**: None for M1 DI/EntryPoint scope.

## Key Decisions Made
- Consolidate Hilt DI definitions in `app/src/main/java/app/gamenative/di/PreferencesModule.kt` containing `@PluviaDataStore`, `PreferencesDataStoreModule`, and `PreferencesBindingModule`.
- Place `PreferencesEntryPoint` in `app/src/main/java/app/gamenative/preferences/PreferencesEntryPoint.kt` with `@JvmStatic fun get(context: Context)` in `companion object` and Kotlin extension function `Context.preferencesEntryPoint()`.

## Artifact Index
- `handoff.md` — Full 5-component handoff report detailing exact Hilt DI module setup, entrypoint pattern, package layout, code snippets, and verification.
