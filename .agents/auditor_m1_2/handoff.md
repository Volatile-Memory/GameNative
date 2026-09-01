# Forensic Audit Report — Milestone 1: Preference Repositories & DI Infrastructure

**Work Product**: 15 files in `app/src/main/java/app/gamenative/preferences/` + `app/src/main/java/app/gamenative/di/PreferencesModule.kt`  
**Profile**: General Project (Integrity Forensics)  
**Integrity Mode**: Development (from `ORIGINAL_REQUEST.md`)  
**Auditor**: Forensic Auditor M1 (`auditor_m1_2`)  
**Verdict**: **CLEAN**

---

## 1. Observation

Direct forensic inspection of all 16 Milestone 1 files against `app/src/main/java/app/gamenative/PrefManager.kt`:

### 1.1 Preference Keys & Parity Cross-Verification (~90 Keys)
Every preference key in `PrefManager.kt` was cross-referenced 1:1 against the new domain preference implementations:

1. **`AuthPreferences` / `DefaultAuthPreferences`**:
   - `USER_NAME` (`"user_name"`): `String`, default `""`
   - `ACCESS_TOKEN_ENC` (`"access_token_enc"`): AES encrypted byte array via `Crypto.encrypt`/`Crypto.decrypt`, default `""`
   - `REFRESH_TOKEN_ENC` (`"refresh_token_enc"`): AES encrypted byte array via `Crypto.encrypt`/`Crypto.decrypt`, default `""`
   - `CLIENT_ID` (`"client_id"`): `Long?`, nullable, default `null`, removes key when set to `null`
   - `CELL_ID` (`"cell_id"`): `Int`, default `0`, resets `cellIdManuallySet = false` when set to `0`
   - `CELL_ID_MANUALLY_SET` (`"cell_id_manually_set"`): `Boolean`, default `false`
   - `PERSONA_STATE` (`"persona_state"`): `EPersonaState`, default `EPersonaState.Online`
   - `STEAM_USER_ACCOUNT_ID` (`"steam_user_account_id"`): `Int`, default `0`
   - `STEAM_USER_STEAM_ID_64` (`"steam_user_steam_id_64"`): `Long`, default `0L`
   - `STEAM_USER_AVATAR_HASH` (`"steam_user_avatar_hash"`): `String`, default `""`
   - `STEAM_USER_NAME` (`"steam_user_name"`): `String`, default `""`
   - `LAST_PICS_CHANGE_NUMBER` (`"last_pics_change_number"`): `Int`, default `0`
   - `STEAM_OFFLINE_MODE` (`"steam_offline_mode"`): `Boolean`, default `false`
   - `EPIC_OFFLINE_MODE` (`"epic_offline_mode"`): `Boolean`, default `false`
   - `FRIENDS_LIST_HEADER` (`"friends_list_header"`): `Set<String>`, default `emptySet()`
   - `ACK_CHAT_PREVIEW` (`"ack_chat_preview"`): `Boolean`, default `false`
   - `clearSteamSession()`: removes all 11 Steam keys (`USER_NAME`, `ACCESS_TOKEN_ENC`, `REFRESH_TOKEN_ENC`, `CLIENT_ID`, `PERSONA_STATE`, `STEAM_USER_ACCOUNT_ID`, `STEAM_USER_STEAM_ID_64`, `STEAM_USER_AVATAR_HASH`, `STEAM_USER_NAME`, `LAST_PICS_CHANGE_NUMBER`, `STEAM_GAMES_COUNT`)
   - `clearAll()`: clears the entire DataStore

2. **`ContainerPreferences` / `DefaultContainerPreferences`**:
   - Preserved all 55 container settings, including legacy special casing: `"dxwrapperConfig"`, `"videoPciDeviceID"`, `"offScreenRenderingMode"`, `"strictShaderMath"`, `"useDRI3"`, `"videoMemorySize"`, `"mouseWarpOverride"`.
   - `displayRendererMode` fallback: returns stored value or `if (useLegacyRenderer) "gl" else "vulkan"` when empty.
   - `suspendPolicy`: wrapped with `Container.normalizeSuspendPolicy(...)`.
   - `sharpnessLevel` & `sharpnessDenoise`: clamped with `.coerceIn(0, 100)`.
   - `startupSelection`: default `Container.STARTUP_SELECTION_ESSENTIAL.toInt()`.

3. **`InputPreferences` / `DefaultInputPreferences`**:
   - `useSteamInput`, `xinputEnabled`, `dinputEnabled`, `dinputMapperType`, `externalDisplayInputMode`, `externalDisplaySwap`, `disableMouseInput`, `swapFaceButtons`, `showGamepadHints`, `showControllerDebugMenu`.
   - Proactively included `capturePointerOnExternalMouse` (`true`), `moveCursorToTouchpoint` (`false`), `controlsOpacity` (`InputControlsView.DEFAULT_OVERLAY_OPACITY`) previously used via raw `PrefManager.getBoolean`/`getFloat` in `XServerScreen.kt`.

4. **`HudPreferences` / `DefaultHudPreferences`**:
   - All 26 performance HUD properties, opacity/color intensity clamps (`.coerceIn(0f, 1f)`), fraction clamps (`.coerceIn(-1f, 1f)`), `quickMenuLastTab` (`.coerceIn(0, 6)`), `powerControlDefaultEnabled` (`DeviceGate.isDeviceSupported()`).
   - `PerformanceHudConfig` getter/setter and reactive Flow mapper.

5. **`LibraryPreferences` / `DefaultLibraryPreferences`**:
   - Layout (`PaneType`), filter (`EnumSet<AppFilter>` flags), sort (`library_sort_key` with legacy `library_sort` fallback), counts (`customGamesCount`, `steamGamesCount`, `gogGamesCount`, `epicGamesCount`, `gogInstalledGamesCount`, `epicInstalledGamesCount`, `amazonInstalledGamesCount`).
   - Collections: `library_steam_collections` using unit separator `" "` (`\u001f`).
   - Volatile in-memory consent cache: `@Volatile private var recDisclosureShownCache: Boolean?`.
   - Thread-safe versioned favorites queue: `favoritePersistenceLock` and `favoritePersistenceVersion`.

6. **`DownloadPreferences` / `DefaultDownloadPreferences`**:
   - `downloadOnWifiOnly`, `downloadSpeed`, `useExternalStorage`, `externalStoragePath`, `fetchSteamGridDBImages`, frontend sync dirs (Steam, Epic, GOG, Amazon, Custom).
   - Side effect: `useExternalStorage` setter resets `externalStoragePath` to `""`.
   - `getFrontendSyncDir(source: GameSource)` and `setFrontendSyncDir(source: GameSource, path: String)` helpers.

7. **`GeneralPreferences` / `DefaultGeneralPreferences`**:
   - App theme (`AppTheme`), palette (`PaletteStyle`), start screen (`"start screen"` with space preserved), allowed orientation (`EnumSet<Orientation>`), language, crash flag, attestation flags, manifest and compatibility caches.
   - Side effect: `nexusLastPlacementJson` removes key on blank or `"{}"`.

8. **`PreferencesEntryPoint` & `PreferencesModule`**:
   - Single central DataStore named `"PluviaPreferences"` qualified with `@PluviaDataStore`.
   - `ReplaceFileCorruptionHandler` configured to reset corrupted state cleanly.
   - `@Binds @Singleton` implementations bound to domain interfaces in `PreferencesBindingModule`.
   - `@EntryPoint @InstallIn(SingletonComponent::class)` with static `PreferencesEntryPoint.get(context)` and `Context.preferencesEntryPoint()` helper.

---

## 2. Logic Chain

1. **Zero Data Loss**: Because all preference key names, data types, and default values exactly replicate the legacy `PrefManager` implementation, DataStore reads and writes will seamlessly access the existing `"PluviaPreferences"` file without data corruption or loss.
2. **Security & Cryptographic Reliability**: `accessToken` and `refreshToken` use `Crypto.encrypt` / `Crypto.decrypt` with exception handlers (`try-catch (e: Exception)`) to prevent app crashes on KeyStore failure.
3. **Concurrency & Thread Safety**: Asynchronous DataStore writes for rapid toggle operations (such as `favoriteAppIds`) use synchronized version checks (`favoritePersistenceVersion`) to ensure out-of-order writes do not overwrite newer state.
4. **No Facades or Hardcoded Cheats**: Every property interacts directly with the injected `DataStore<Preferences>` instance using real reads (`runBlocking { dataStore.data.first()[key] }`), asynchronous writes (`scope.launch { dataStore.edit { ... } }`), and distinct reactive flows (`dataStore.data.map { ... }.distinctUntilChanged()`).
5. **No Singleton Leak**: The new preference classes are standard `@Singleton` injectable components provided via Hilt, fully fulfilling Requirements §R1 and §R2.

---

## 3. Caveats

- **JVM KeyStore Testing**: Executing JVM unit tests against encrypted tokens (`AuthPreferences`) requires mocking the `AndroidKeyStore` provider or running via Robolectric/instrumentation.
- **Legacy PrefManager Cleanup**: `PrefManager.kt` remains in the codebase as planned for Milestone 1; it will be eradicated in Milestone 6 once downstream callers (M2–M5) have migrated.

---

## 4. Conclusion

**Verdict: CLEAN**

Milestone 1 work product fulfills all architectural, behavioral, and forensic requirements:
- 0 hardcoded test values, 0 facade implementations, 0 pre-populated artifacts.
- 100% key parity (~90 keys) across all 7 domain interfaces.
- Full preservation of side effects, cryptographic operations, volatile caching, and concurrency synchronization.
- Dagger Hilt module and non-Hilt EntryPoint are properly structured.

---

## 5. Verification Method

To verify independently:
1. Inspect the 15 files in `app/src/main/java/app/gamenative/preferences/`
2. Inspect `app/src/main/java/app/gamenative/di/PreferencesModule.kt`
3. Cross-verify preference key names against `app/src/main/java/app/gamenative/PrefManager.kt`
4. Run project build and test suite:
   ```bash
   ./gradlew compileModernDebugKotlin
   ./gradlew :app:testModernDebugUnitTest
   ```
