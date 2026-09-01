# Original User Request

## Initial Request — 2026-08-31T10:28:38Z

Refactor the massive `PrefManager` singleton object into logical, Dagger Hilt injected preference repositories (`AuthPreferences`, `ContainerPreferences`, etc.). Migrate the ~100 files using `PrefManager` to use these new injected interfaces in a phased approach, eliminating the singleton soup. The team should divide the work across different app layers concurrently.

Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
Integrity mode: development

## Requirements

### R1. Preference Repositories
Define separate logical preference interfaces for distinct domains (e.g., Auth, Container, Performance HUD) and implement them. The implementations must delegate to the existing `PluviaPreferences` DataStore keys to ensure zero data loss during migration.

### R2. Dependency Injection
Provide these new interfaces via Dagger Hilt modules so they can be injected into ViewModels, Services, and core classes.

### R3. Widespread Migration
Migrate all existing usages of the `PrefManager` singleton object across the codebase to use the injected dependencies instead.

### R4. Eradicate Singleton
Once all usages have been migrated, delete the `PrefManager` singleton object entirely.

### R5. Build Cache Efficiency
You must use the Gradle build cache efficiently. The `GRADLE_USER_HOME` is now configured on the `D:\` drive. Do not use the `--no-build-cache` flag during verification unless strictly required to bypass corruption, as it will drastically slow down parallel team builds.

## Acceptance Criteria

### Compilation & Tests
- [ ] The project successfully compiles via `./gradlew compileModernDebugKotlin`.
- [ ] Existing unit tests pass via `./gradlew :app:testModernDebugUnitTest`.

### Code Integrity
- [ ] A recursive text search for `PrefManager.getInstance()` or `PrefManager.` returns 0 results across the codebase.
- [ ] The `PrefManager.kt` file no longer contains the `object PrefManager` singleton.

## Follow-up — 2026-08-31T12:28:51Z

Your quota has reset. Please resume the migration process.

## Follow-up — 2026-08-31T17:32:28Z

The quota limit has refreshed (over 5 hours have passed) and the server restarted. Please resume the migration process for Milestones 2, 3, 4, and 6.

