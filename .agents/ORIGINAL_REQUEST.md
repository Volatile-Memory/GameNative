# Original User Request

## Initial Request — 2026-09-02T01:25:55+05:00

You are the Project Orchestrator for the "Eradicate Mid-Level Singletons" refactoring initiative.

# Workspace & Request
- Working Directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
- Original Request File: C:\Users\VladK\.gemini\antigravity\brain\532fc074-4524-41e3-aef8-438b1d4d973e\ORIGINAL_REQUEST.md

# Mission
Convert mid-level `object` singletons into `@Singleton class` components with `@Inject` constructors to eliminate Dagger Hilt `EntryPoint` escape hatches and `Context` prop-drilling. Refactor all upstream callers.

## Requirements

### R1. Target Conversions
Locate and convert the following stateful singletons (and hidden Android Service singletons) into injected `@Singleton` classes. Assign workers to execute these in these exact Logical Domain Groups:

**Group 1: Metadata & HowLongToBeat**
- `HltbService`, `HltbCache`, `SteamGridDB`

**Group 2: Hardware & Compatibility Caches**
- `DeviceGameStatsCache`, `GpuGameStatsCache`, `GameCompatibilityCache`

**Group 3: User Library Managers**
- `FavoritesManager`, `FrontendSyncManager`, `CustomGameScanner`

**Group 4: The Storefront Services (Hidden Singletons)**
- `SteamService`, `EpicService`, `GOGService`, `AmazonService` 
*(Extract their business logic into injected `SteamManager`, `EpicManager`, etc. The Android Service classes should become thin shells).*

**Group 5: Advanced Subsystems**
- `BestConfigService`, `WorkshopManager`

**Group 6: PluviaApp (The Final Boss)**
- `PluviaApp.companion` 
*(Extract `xEnvironment` and static UI views into the `@GameSessionScoped` lifecycle component).*

### R2. Eradicate Escape Hatches
Inside these newly converted classes:
- Delete any usage of `PreferencesEntryPoint` or `EntryPointAccessors`. Inject the required domain preference repositories (e.g., `AuthPreferences`, `ContainerPreferences`) directly into the constructor instead.
- Eradicate `context: Context` from function signatures. Furthermore, strive to keep `Context` out of the `@Inject constructor` as well.
- If the class was using `Context` as a service locator (e.g., `context.getString()`, `context.getFilesDir()`, `context.getSystemService()`), inject the proper Hilt abstraction instead (e.g., `StringResolver`, `AppStoragePaths`, or the specific `NotificationManager` from `SystemServicesModule`).
- Be pragmatic: If the class genuinely requires `Context` to call a framework method directly on it (e.g., `context.startActivity()` or `context.registerReceiver()`), then injecting `@ApplicationContext context: Context` is permitted.

### R3. Refactor Call Sites
Update all downstream callers (ViewModels, Services, UI state holders) that previously invoked these objects statically (e.g., `FavoritesManager.doSomething()`). They must now receive these classes via `@Inject` and call them as instance methods.

### R4. Build Cache Efficiency
You must use the Gradle build cache efficiently. The `GRADLE_USER_HOME` is configured on the `D:\` drive. Do not use the `--no-build-cache` flag during verification unless strictly required to bypass corruption, as it will drastically slow down parallel team builds.

## Acceptance Criteria
- `./gradlew compileModernDebugKotlin` builds cleanly.
- `./gradlew :app:testModernDebugUnitTest` passes.
- All targeted classes are `@Singleton class ... @Inject constructor`.
- No targeted class contains `EntryPointAccessors.fromApplication`.

## Follow-up — 2026-09-04T06:38:18Z

# Teamwork Project Prompt: Eradicate Mid-Level Singletons

Convert mid-level `object` singletons into `@Singleton class` components with `@Inject` constructors to eliminate Dagger Hilt `EntryPoint` escape hatches and `Context` prop-drilling. The team should divide the work to systematically convert these objects and refactor all of their upstream callers.

Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
Integrity mode: development

## Requirements

### R1. Target Conversions
Locate and convert the following stateful singletons (and hidden Android Service singletons) into injected `@Singleton` classes. To prevent merge conflicts and avoid writing temporary bridge code, you MUST assign workers to execute these in these exact Logical Domain Groups:

*(Note: Groups 1, 2, and 3 were successfully completed in the previous run. You are starting from Group 4.)*

**Group 4: The Storefront Services (Hidden Singletons)**
- `SteamService`, `EpicService`, `GOGService`, `AmazonService` 
*(Extract their business logic into injected `SteamManager`, `EpicManager`, etc. The Android Service classes should become thin shells).*

**Group 5: Advanced Subsystems**
- `BestConfigService`, `WorkshopManager`

**Group 6: PluviaApp (The Final Boss)**
- `PluviaApp.companion` 
*(Extract `xEnvironment` and static UI views into the `@GameSessionScoped` lifecycle component).*

### R2. Eradicate Escape Hatches
Inside these newly converted classes:
- Delete any usage of `PreferencesEntryPoint` or `EntryPointAccessors`. Inject the required domain preference repositories (e.g., `AuthPreferences`, `ContainerPreferences`) directly into the constructor instead.
- Eradicate `context: Context` from function signatures. Furthermore, strive to keep `Context` out of the `@Inject constructor` as well.
- If the class was using `Context` as a service locator (e.g., `context.getString()`, `context.getFilesDir()`, `context.getSystemService()`), inject the proper Hilt abstraction instead (e.g., `StringResolver`, `AppStoragePaths`, or the specific `NotificationManager` from `SystemServicesModule`).
- Be pragmatic: If the class genuinely requires `Context` to call a framework method directly on it (e.g., `context.startActivity()` or `context.registerReceiver()`), then injecting `@ApplicationContext context: Context` is permitted.

### R3. Refactor Call Sites
Update all downstream callers (ViewModels, Services, UI state holders) that previously invoked these objects statically (e.g., `FavoritesManager.doSomething()`). They must now receive these classes via `@Inject` and call them as instance methods.

### R4. Build Cache Efficiency
You must use the Gradle build cache efficiently. The `GRADLE_USER_HOME` is configured on the `D:\` drive. Do not use the `--no-build-cache` flag during verification unless strictly required to bypass corruption, as it will drastically slow down parallel team builds.

## Acceptance Criteria

### Compilation & Tests
- [ ] The project successfully compiles via `./gradlew compileModernDebugKotlin`.
- [ ] Existing unit tests pass via `./gradlew :app:testModernDebugUnitTest`.

### Code Integrity
- [ ] The targeted classes are declared as `class` (not `object`) and annotated with `@Singleton` and `@Inject constructor`.
- [ ] No targeted class contains `EntryPointAccessors.fromApplication`.

## Follow-up — 2026-09-10T17:59:46Z

# Teamwork Project Prompt: Eradicate Mid-Level Singletons

Convert mid-level `object` singletons into `@Singleton class` components with `@Inject` constructors to eliminate Dagger Hilt `EntryPoint` escape hatches and `Context` prop-drilling. The team should divide the work to systematically convert these objects and refactor all of their upstream callers.

Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
Integrity mode: development

## Requirements

### R1. Target Conversions
Locate and convert the following stateful singletons (and hidden Android Service singletons) into injected `@Singleton` classes. To prevent merge conflicts and avoid writing temporary bridge code, you MUST assign workers to execute these in these exact Logical Domain Groups:

*(Note: Groups 1, 2, 3, and 4 were successfully completed in the previous runs. You are starting from Group 5. For Group 5, some work was already started, so continue where the previous team left off.)*

**Group 5: Advanced Subsystems**
- `BestConfigService`, `WorkshopManager`

**Group 6: PluviaApp (The Final Boss)**
- `PluviaApp.companion` 
*(Extract `xEnvironment` and static UI views into the `@GameSessionScoped` lifecycle component).*

### R2. Eradicate Escape Hatches
Inside these newly converted classes:
- Delete any usage of `PreferencesEntryPoint` or `EntryPointAccessors`. Inject the required domain preference repositories (e.g., `AuthPreferences`, `ContainerPreferences`) directly into the constructor instead.
- Eradicate `context: Context` from function signatures. Furthermore, strive to keep `Context` out of the `@Inject constructor` as well.
- If the class was using `Context` as a service locator (e.g., `context.getString()`, `context.getFilesDir()`, `context.getSystemService()`), inject the proper Hilt abstraction instead (e.g., `StringResolver`, `AppStoragePaths`, or the specific `NotificationManager` from `SystemServicesModule`).
- Be pragmatic: If the class genuinely requires `Context` to call a framework method directly on it (e.g., `context.startActivity()` or `context.registerReceiver()`), then injecting `@ApplicationContext context: Context` is permitted.

### R3. Refactor Call Sites
Update all downstream callers (ViewModels, Services, UI state holders) that previously invoked these objects statically (e.g., `FavoritesManager.doSomething()`). They must now receive these classes via `@Inject` and call them as instance methods.

### R4. Build Cache Efficiency
You must use the Gradle build cache efficiently. The `GRADLE_USER_HOME` is configured on the `D:\` drive. Do not use the `--no-build-cache` flag during verification unless strictly required to bypass corruption, as it will drastically slow down parallel team builds.

## Acceptance Criteria

### Compilation & Tests
- [ ] The project successfully compiles via `./gradlew compileModernDebugKotlin`.
- [ ] Existing unit tests pass via `./gradlew :app:testModernDebugUnitTest`.

### Code Integrity
- [ ] The targeted classes are declared as `class` (not `object`) and annotated with `@Singleton` and `@Inject constructor`.
- [ ] No targeted class contains `EntryPointAccessors.fromApplication`.


