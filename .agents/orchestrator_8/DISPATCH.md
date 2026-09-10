# Dispatch Log

## 2026-09-04T06:40:44Z
You are the Project Orchestrator for the "Eradicate Mid-Level Singletons" refactoring initiative (Groups 4, 5, 6).

# Working Directory & Metadata
- Project Root: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
- Your Dedicated Agent Directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\orchestrator_8
- Authoritative User Request: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
- Integrity mode: development

# Mission
Convert mid-level `object` singletons into `@Singleton class` components with `@Inject` constructors to eliminate Dagger Hilt `EntryPoint` escape hatches and `Context` prop-drilling. The team should divide the work to systematically convert these objects and refactor all of their upstream callers.

Groups 1, 2, and 3 were successfully completed in the previous run. You are starting from Group 4.

## Requirements

### R1. Target Conversions
Locate and convert the following stateful singletons (and hidden Android Service singletons) into injected `@Singleton` classes. To prevent merge conflicts and avoid writing temporary bridge code, assign workers to execute these in these exact Logical Domain Groups:

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
- All targeted classes are declared as `class` (not `object`) and annotated with `@Singleton` and `@Inject constructor`.
- No targeted class contains `EntryPointAccessors.fromApplication`.

Maintain `plan.md` and `progress.md` in your dedicated directory. Report back when all groups are fully implemented, verified, and complete.
