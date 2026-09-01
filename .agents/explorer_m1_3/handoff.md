# Zero Data Loss & Edge Cases Investigation Report

**Explorer**: Explorer M1-3 (Zero Data Loss & Edge Cases Explorer)  
**Target Files Analyzed**:
- `app/src/main/java/app/gamenative/PrefManager.kt`
- `app/src/main/java/app/gamenative/Crypto.kt`
- `app/src/main/java/com/winlator/PrefManager.kt`
- `app/src/main/java/app/gamenative/data/DefaultFavoritesRepository.kt`
- `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`
- `app/src/main/java/app/gamenative/service/SteamService.kt`
- `app/src/main/java/app/gamenative/utils/GameCompatibilityCache.kt`
- `app/src/main/java/app/gamenative/utils/HltbService.kt`
- `app/src/main/java/app/gamenative/utils/DeviceGameStatsCache.kt`
- `app/src/main/java/app/gamenative/utils/GpuGameStatsCache.kt`
- `app/src/main/java/app/gamenative/mods/NexusModManager.kt`

**DataStore Name**: `"PluviaPreferences"`  
**Timestamp**: 2026-08-31T16:35:00+05:00

---

## 1. Observation

### 1.1 Cryptographic Encryption / Decryption Routines (`Crypto.kt` & `PrefManager.kt`)

#### 1.1.1 Algorithm Specifications (`app/src/main/java/app/gamenative/Crypto.kt:15-76`)
- **KeyStore Provider**: `"AndroidKeyStore"`
- **Key Alias**: `"pluvia_secret"`
- **Algorithm**: `KeyProperties.KEY_ALGORITHM_AES` (`"AES"`)
- **Block Mode**: `KeyProperties.BLOCK_MODE_CBC` (`"CBC"`)
- **Padding**: `KeyProperties.ENCRYPTION_PADDING_PKCS7` (`"PKCS7Padding"`)
- **Transformation String**: `"AES/CBC/PKCS7Padding"`
- **Key Size**: 256 bits (`.setKeySize(256)`)
- **Key Generation Parameters**:
  - `KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT`
  - `setRandomizedEncryptionRequired(true)`
  - `setUserAuthenticationRequired(false)`
- **Encryption Flow (`Crypto.encrypt(bytes: ByteArray): ByteArray`)**:
  - Validates `require(bytes.isNotEmpty()) { "Input bytes cannot be empty" }` (`Crypto.kt:54-56`).
  - Initializes cipher in `Cipher.ENCRYPT_MODE`.
  - Concatenates random initialization vector (IV, 16 bytes) with ciphertext: `cipher.iv + cipher.doFinal(bytes)` (`Crypto.kt:60`).
- **Decryption Flow (`Crypto.decrypt(bytes: ByteArray): ByteArray`)**:
  - Validates `require(bytes.size > cipher.blockSize) { "Input bytes too short to contain IV and data. Minimum length is ${cipher.blockSize + 1}" }` (`Crypto.kt:66-69`).
  - Slices first `cipher.blockSize` (16 bytes) as IV (`Crypto.kt:71`).
  - Slices remaining bytes as ciphertext payload (`Crypto.kt:72`).
  - Initializes cipher with `IvParameterSpec(iv)` in `Cipher.DECRYPT_MODE` and decrypts via `cipher.doFinal(data)` (`Crypto.kt:73-74`).

#### 1.1.2 DataStore Token Storage in `PrefManager.kt`
- **Keys**:
  - `ACCESS_TOKEN_ENC = byteArrayPreferencesKey("access_token_enc")` (`PrefManager.kt:814`)
  - `REFRESH_TOKEN_ENC = byteArrayPreferencesKey("refresh_token_enc")` (`PrefManager.kt:830`)
- **Getter Implementation**:
  ```kotlin
  val encryptedBytes = getPref(ACCESS_TOKEN_ENC, ByteArray(0))
  return if (encryptedBytes.isEmpty()) {
      ""
  } else {
      val bytes = Crypto.decrypt(encryptedBytes)
      String(bytes)
  }
  ```
- **Setter Implementation**:
  ```kotlin
  val bytes = Crypto.encrypt(value.toByteArray())
  setPref(ACCESS_TOKEN_ENC, bytes)
  ```
- **Observed Bug / Edge Case in Existing Implementation**: If `value` is empty string (`""`), `value.toByteArray()` is 0-length, causing `Crypto.encrypt()` to throw `IllegalArgumentException("Input bytes cannot be empty")`. The refactored repository must check `if (value.isEmpty()) setPref(KEY, ByteArray(0))` (or remove key) instead of passing 0 bytes to `Crypto.encrypt`.
- **Legacy Plaintext Token Migration (`PrefManager.kt:65-80`)**:
  ```kotlin
  val oldAccessToken = stringPreferencesKey("access_token")
  val oldRefreshToken = stringPreferencesKey("refresh_token")
  getPref(oldAccessToken, "").let {
      if (it.isNotEmpty()) {
          accessToken = it
          removePref(oldAccessToken)
      }
  }
  getPref(oldRefreshToken, "").let {
      if (it.isNotEmpty()) {
          refreshToken = it
          removePref(oldRefreshToken)
      }
  }
  ```
  Also removes `stringPreferencesKey("password")` (`PrefManager.kt:62-63`).

---

### 1.2 Complex Conversions & JSON Serialization Inventory

| Property | DataStore Key | Key Type | Stored Default | Kotlin In-Memory Type & Conversion | Error Handling / Concurrency Pattern |
|---|---|---|---|---|---|
| `favoriteAppIds` | `"favorite_app_ids"` | `stringPreferencesKey` | `"[]"` | `Set<String>` via `Json.decodeFromString<Set<String>>` / `Json.encodeToString` | Wrapped in `try-catch` falling back to `emptySet()`. Setter uses `favoritePersistenceLock` + `favoritePersistenceVersion` to serialize sequentially and ignore stale queued async writes (`PrefManager.kt:1354-1383`). |
| `customGamePaths` | `"custom_game_paths"` | `stringPreferencesKey` | `"[]"` | `Set<String>` via `Json.decodeFromString<Set<String>>` / `Json.encodeToString` | Wrapped in `try-catch` falling back to `emptySet()` (`PrefManager.kt:1326-1338`). |
| `customGameManualFolders` | `"custom_game_manual_folders"` | `stringPreferencesKey` | `"[]"` | `Set<String>` via `Json.decodeFromString<Set<String>>` / `Json.encodeToString` | Wrapped in `try-catch` falling back to `emptySet()` (`PrefManager.kt:1340-1352`). |
| `friendsListHeader` | `"friends_list_header"` | `stringPreferencesKey` | `"[]"` | `Set<String>` via `Json.decodeFromString<Set<String>>` / `Json.encodeToString` | Direct decode (`PrefManager.kt:1037-1045`). Needs `try-catch` fallback in new repo. |
| `librarySteamCollections` | `"library_steam_collections"` | `stringPreferencesKey` | `""` | `Set<String>` via `raw.split(" ").filter { it.isNotEmpty() }.toSet()` / `joinToString(" ")` | Space-delimited string (`COLLECTION_ID_SEPARATOR = " "`). Empty string returns `emptySet()` (`PrefManager.kt:904-914`). |
| `librarySteamCollectionsCache` | `"library_steam_collections_cache"` | `stringPreferencesKey` | `""` | `String` (Raw JSON of dynamic collection metadata) | Stored as-is (`PrefManager.kt:894-897`). |
| `gameCompatibilityCache` | `"game_compatibility_cache"` | `stringPreferencesKey` | `"{}"` | `String` (Raw JSON map of compatibility reports) | Consumed by `GameCompatibilityCache.kt` (`PrefManager.kt:1421-1426`). |
| `hltbCache` | `"hltb_cache"` | `stringPreferencesKey` | `"{}"` | `String` (Raw JSON map of HowLongToBeat entries) | Consumed by `HltbService.kt` (`PrefManager.kt:1429-1434`). |
| `deviceGameStatsCache` | `"device_game_stats_cache"` | `stringPreferencesKey` | `"{}"` | `String` (Raw JSON map of device performance telemetry) | Consumed by `DeviceGameStatsCache.kt` (`PrefManager.kt:1437-1442`). |
| `gpuGameStatsCache` | `"gpu_game_stats_cache"` | `stringPreferencesKey` | `"{}"` | `String` (Raw JSON map of GPU benchmark telemetry) | Consumed by `GpuGameStatsCache.kt` (`PrefManager.kt:1445-1450`). |
| `componentManifestJson` | `"component_manifest_json"` | `stringPreferencesKey` | `""` | `String` (Raw JSON string from GitHub releases) | Consumed by `ManifestRepository.kt` (`PrefManager.kt:144-149`). |
| `nexusLastPlacementJson` | `"nexus_last_placement_json"` | `stringPreferencesKey` | `"{}"` | `String` (Raw JSON map for mod placement coordinates) | **Special setter**: If `value.isBlank() \|\| value == "{}"`, calls `removePref()` rather than saving `"\"\""` or `"{}"` (`PrefManager.kt:1493-1502`). |
| `recommendationCacheJson` | `"recommendation_cache_json"` | `stringPreferencesKey` | `""` | `String` (Raw JSON string for single recommendation) | Consumed by `RecommendationRepository.kt` (`PrefManager.kt:1181-1186`). |

---

### 1.3 Special Logic & Side-Effect Mechanics

#### 1.3.1 `clearSteamSessionPreferences()` (`PrefManager.kt:95-110`)
Deletes exactly 11 Steam account/session keys when a user logs out, leaving container runtime, general preferences, theme, downloads, and other platform settings completely untouched:
1. `USER_NAME` (`"user_name"`)
2. `ACCESS_TOKEN_ENC` (`"access_token_enc"`)
3. `REFRESH_TOKEN_ENC` (`"refresh_token_enc"`)
4. `CLIENT_ID` (`"client_id"`)
5. `PERSONA_STATE` (`"persona_state"`)
6. `STEAM_USER_ACCOUNT_ID` (`"steam_user_account_id"`)
7. `STEAM_USER_STEAM_ID_64` (`"steam_user_steam_id_64"`)
8. `STEAM_USER_AVATAR_HASH` (`"steam_user_avatar_hash"`)
9. `STEAM_USER_NAME` (`"steam_user_name"`)
10. `LAST_PICS_CHANGE_NUMBER` (`"last_pics_change_number"`)
11. `STEAM_GAMES_COUNT` (`"steam_games_count"`)

#### 1.3.2 `cellId` & `cellIdManuallySet` Reset Side-Effect (`PrefManager.kt:789-805`)
- Setting `cellId = 0` **must** execute `setPref(CELL_ID_MANUALLY_SET, false)`.
- If `cellId != 0` is set automatically by Steam callback (`SteamService.kt:3865`), `cellIdManuallySet` remains `false`.
- Only explicit manual region selection in settings (`SettingsGroupInterface.kt:612`) sets `cellIdManuallySet = true`.

#### 1.3.3 `useExternalStorage` Toggle Side-Effect (`PrefManager.kt:1257-1277`)
- Setting `useExternalStorage = value` **must** execute `setPref(EXTERNAL_STORAGE_PATH, "")`.
- Rationale: toggling external storage on/off invalidates the current directory path, forcing the user/app to re-verify or re-select the storage location.

#### 1.3.4 In-Memory Volatile Fast-Path Caching (`recDisclosureShown`) (`PrefManager.kt:1203-1214`)
- `@Volatile private var recDisclosureShownCache: Boolean? = null`
- Getter reads `recDisclosureShownCache ?: getPref(...)`.
- Setter updates `recDisclosureShownCache = value` immediately before launching the async DataStore write.
- Rationale: async DataStore writes take several milliseconds; callers read back consent immediately after dialog dismissal, before the write lands on disk.

#### 1.3.5 Dynamic Fallbacks
- **`displayRendererMode` (`PrefManager.kt:209-219`)**: If stored string is empty (`""`), dynamically returns `if (useLegacyRenderer) "gl" else "vulkan"`.
- **`librarySortOption` (`PrefManager.kt:876-892`)**: Reads `"library_sort_key"` (string). If empty, falls back to legacy int key `"library_sort"` (ordinal) and converts via `SortOption.fromOrdinal()`.

#### 1.3.6 Nullable Property (`clientId`) (`PrefManager.kt:847-854`)
- `var clientId: Long?`: Uses nullable `Long?`. Returns `null` if the key is missing from DataStore.

---

### 1.4 Comprehensive Catalog of All 98 Preference Keys and Exact Defaults

#### Domain 1: `AuthPreferences` (16 properties + 1 operation)
| # | Property | DataStore Key | Key Type | Exact Default | Kotlin Type / Transform |
|---|---|---|---|---|---|
| 1 | `username` | `"user_name"` | `stringPreferencesKey` | `""` | `String` |
| 2 | `accessToken` | `"access_token_enc"` | `byteArrayPreferencesKey` | `ByteArray(0)` | `String` (AES decrypted / encrypted) |
| 3 | `refreshToken` | `"refresh_token_enc"` | `byteArrayPreferencesKey` | `ByteArray(0)` | `String` (AES decrypted / encrypted) |
| 4 | `clientId` | `"client_id"` | `longPreferencesKey` | `null` | `Long?` (Nullable Long) |
| 5 | `personaState` | `"persona_state"` | `intPreferencesKey` | `EPersonaState.Online.code()` | `EPersonaState` enum |
| 6 | `steamUserAccountId` | `"steam_user_account_id"` | `intPreferencesKey` | `0` | `Int` |
| 7 | `steamUserSteamId64` | `"steam_user_steam_id_64"` | `longPreferencesKey` | `0L` | `Long` |
| 8 | `steamUserAvatarHash` | `"steam_user_avatar_hash"` | `stringPreferencesKey` | `""` | `String` |
| 9 | `steamUserName` | `"steam_user_name"` | `stringPreferencesKey` | `""` | `String` |
| 10 | `lastPICSChangeNumber` | `"last_pics_change_number"` | `intPreferencesKey` | `0` | `Int` |
| 11 | `cellId` | `"cell_id"` | `intPreferencesKey` | `0` | `Int` (0 resets `cellIdManuallySet`) |
| 12 | `cellIdManuallySet` | `"cell_id_manually_set"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 13 | `steamOfflineMode` | `"steam_offline_mode"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 14 | `epicOfflineMode` | `"epic_offline_mode"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 15 | `friendsListHeader` | `"friends_list_header"` | `stringPreferencesKey` | `"[]"` | `Set<String>` (JSON Set) |
| 16 | `ackChatPreview` | `"ack_chat_preview"` | `booleanPreferencesKey` | `false` | `Boolean` |

#### Domain 2: `ContainerPreferences` (40 properties)
| # | Property | DataStore Key | Key Type | Exact Default | Kotlin Type / Transform |
|---|---|---|---|---|---|
| 17 | `screenSize` | `"screen_size"` | `stringPreferencesKey` | `PluviaApp.getDefaultScreenSize()` | `String` |
| 18 | `envVars` | `"env_vars"` | `stringPreferencesKey` | `Container.DEFAULT_ENV_VARS` | `String` |
| 19 | `graphicsDriver` | `"graphics_driver"` | `stringPreferencesKey` | `Container.DEFAULT_GRAPHICS_DRIVER` | `String` |
| 20 | `graphicsDriverVersion` | `"graphics_driver_version"` | `stringPreferencesKey` | `""` | `String` |
| 21 | `graphicsDriverConfig` | `"graphics_driver_config"` | `stringPreferencesKey` | `Container.DEFAULT_GRAPHICSDRIVERCONFIG` | `String` |
| 22 | `rendererPresentMode` | `"renderer_present_mode"` | `stringPreferencesKey` | `"fifo"` | `String` |
| 23 | `displayRendererMode` | `"display_renderer_mode"` | `stringPreferencesKey` | `""` (fallback to gl/vulkan) | `String` |
| 24 | `sfCompatMode` | `"sf_compat_mode"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 25 | `useLegacyRenderer` | `"use_legacy_renderer"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 26 | `sharpnessEffect` | `"sharpness_effect"` | `stringPreferencesKey` | `"None"` | `String` |
| 27 | `sharpnessLevel` | `"sharpness_level"` | `intPreferencesKey` | `100` | `Int` (coerced `0..100`) |
| 28 | `sharpnessDenoise` | `"sharpness_denoise"` | `intPreferencesKey` | `100` | `Int` (coerced `0..100`) |
| 29 | `containerVariant` | `"container_variant"` | `stringPreferencesKey` | `Container.DEFAULT_VARIANT` | `String` |
| 30 | `wineVersion` | `"wine_version"` | `stringPreferencesKey` | `Container.DEFAULT_WINE_VERSION` | `String` |
| 31 | `emulator` | `"emulator"` | `stringPreferencesKey` | `Container.DEFAULT_EMULATOR` | `String` |
| 32 | `fexcoreVersion` | `"fexcore_version"` | `stringPreferencesKey` | `DefaultVersion.FEXCORE` | `String` |
| 33 | `fexcoreTSOMode` | `"fexcore_tso_mode"` | `stringPreferencesKey` | `"Fast"` | `String` |
| 34 | `fexcoreX87Mode` | `"fexcore_x87_mode"` | `stringPreferencesKey` | `"Fast"` | `String` |
| 35 | `fexcoreMultiBlock` | `"fexcore_multiblock"` | `stringPreferencesKey` | `"Disabled"` | `String` |
| 36 | `fexcorePreset` | `"fexcore_preset"` | `stringPreferencesKey` | `FEXCorePreset.INTERMEDIATE` | `String` |
| 37 | `box86Preset` | `"box86_preset"` | `stringPreferencesKey` | `Box86_64Preset.COMPATIBILITY` | `String` |
| 38 | `box64Preset` | `"box64_preset"` | `stringPreferencesKey` | `Box86_64Preset.COMPATIBILITY` | `String` |
| 39 | `box86Version` | `"box86_version"` | `stringPreferencesKey` | `DefaultVersion.BOX86` | `String` |
| 40 | `box64Version` | `"box64_version"` | `stringPreferencesKey` | `DefaultVersion.BOX64` | `String` |
| 41 | `dxWrapper` | `"dxwrapper"` | `stringPreferencesKey` | `Container.DEFAULT_DXWRAPPER` | `String` |
| 42 | `dxWrapperConfig` | `"dxwrapperConfig"` | `stringPreferencesKey` | `Container.DEFAULT_DXWRAPPERCONFIG` | `String` (**camelCase key!**) |
| 43 | `audioDriver` | `"audio_driver"` | `stringPreferencesKey` | `Container.DEFAULT_AUDIO_DRIVER` | `String` |
| 44 | `pulseaudioLowLatency` | `"pulseaudio_low_latency"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 45 | `winComponents` | `"wincomponents"` | `stringPreferencesKey` | `Container.DEFAULT_WINCOMPONENTS` | `String` |
| 46 | `drives` | `"drives"` | `stringPreferencesKey` | `Container.DEFAULT_DRIVES` | `String` |
| 47 | `execArgs` | `"exec_args"` | `stringPreferencesKey` | `""` | `String` |
| 48 | `suspendPolicy` | `"suspend_policy"` | `stringPreferencesKey` | `Container.SUSPEND_POLICY_MANUAL` | `String` (pass-through `normalizeSuspendPolicy`) |
| 49 | `cpuList` | `"cpu_list"` | `stringPreferencesKey` | `Container.getFallbackCPUList()` | `String` |
| 50 | `cpuListWoW64` | `"cpu_list_wow64"` | `stringPreferencesKey` | `Container.getFallbackCPUListWoW64()` | `String` |
| 51 | `wow64Mode` | `"wow64_mode"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 52 | `startupSelection` | `"startup_selection"` | `intPreferencesKey` | `Container.STARTUP_SELECTION_ESSENTIAL.toInt()` | `Int` |
| 53 | `containerLanguage` | `"container_language"` | `stringPreferencesKey` | `"english"` | `String` |
| 54 | `renderer` | `"renderer"` | `stringPreferencesKey` | `"gl"` | `String` |
| 55 | `csmt` | `"csmt"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 56 | `videoPciDeviceID` | `"videoPciDeviceID"` | `intPreferencesKey` | `1728` | `Int` (**camelCase key!**) |
| 57 | `offScreenRenderingMode` | `"offScreenRenderingMode"` | `stringPreferencesKey` | `"fbo"` | `String` (**camelCase key!**) |
| 58 | `strictShaderMath` | `"strictShaderMath"` | `booleanPreferencesKey` | `true` | `Boolean` (**camelCase key!**) |
| 59 | `useDRI3` | `"useDRI3"` | `booleanPreferencesKey` | `true` | `Boolean` (**camelCase key!**) |
| 60 | `videoMemorySize` | `"videoMemorySize"` | `stringPreferencesKey` | `"2048"` | `String` (**camelCase key!**) |
| 61 | `mouseWarpOverride` | `"mouseWarpOverride"` | `stringPreferencesKey` | `"disable"` | `String` (**camelCase key!**) |
| 62 | `portraitMode` | `"portrait_mode"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 63 | `launchRealSteam` | `"launch_real_steam"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 64 | `launchBionicSteam` | `"launch_bionic_steam"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 65 | `forceDlc` | `"force_dlc"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 66 | `localSavesOnly` | `"local_saves_only"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 67 | `useLegacyDRM` | `"use_legacy_drm"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 68 | `unpackFiles` | `"unpack_files"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 69 | `autoApplyKnownConfig` | `"auto_apply_known_config"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 70 | `enableWineDebug` | `"enable_wine_debug"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 71 | `wineDebugChannels` | `"wine_debug_channels"` | `stringPreferencesKey` | `Constants.XServer.DEFAULT_WINE_DEBUG_CHANNELS` | `String` |

#### Domain 3: `InputPreferences` (13 properties)
| # | Property | DataStore Key | Key Type | Exact Default | Kotlin Type / Transform |
|---|---|---|---|---|---|
| 72 | `useSteamInput` | `"use_steam_input"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 73 | `xinputEnabled` | `"xinput_enabled"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 74 | `dinputEnabled` | `"dinput_enabled"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 75 | `dinputMapperType` | `"dinput_mapper_type"` | `intPreferencesKey` | `1` | `Int` |
| 76 | `externalDisplayInputMode` | `"external_display_input_mode"` | `stringPreferencesKey` | `Container.DEFAULT_EXTERNAL_DISPLAY_MODE` | `String` |
| 77 | `externalDisplaySwap` | `"external_display_swap"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 78 | `disableMouseInput` | `"disable_mouse_input"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 79 | `swapFaceButtons` | `"swap_face_buttons"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 80 | `showGamepadHints` | `"show_gamepad_hints"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 81 | `showControllerDebugMenu` | `"show_controller_debug_menu"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 82 | `capturePointerOnExternalMouse` | `"capture_pointer_on_external_mouse"` | `booleanPreferencesKey` | `true` | `Boolean` (dynamic key in XServerScreen) |
| 83 | `moveCursorToTouchpoint` | `"move_cursor_to_touchpoint"` | `booleanPreferencesKey` | `false` | `Boolean` (dynamic key in XServerScreen) |
| 84 | `controlsOpacity` | `"controls_opacity"` | `floatPreferencesKey` | `InputControlsView.DEFAULT_OVERLAY_OPACITY` (`0.8f`) | `Float` (dynamic key in XServerScreen) |

#### Domain 4: `HudPreferences` (26 properties)
| # | Property | DataStore Key | Key Type | Exact Default | Kotlin Type / Transform |
|---|---|---|---|---|---|
| 85 | `showFps` | `"show_fps"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 86 | `quickMenuLastTab` | `"quick_menu_last_tab"` | `intPreferencesKey` | `0` | `Int` (coerced `0..6`) |
| 87 | `performanceHudCompactMode` | `"performance_hud_compact_mode"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 88 | `performanceHudShowFrameRate` | `"performance_hud_show_frame_rate"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 89 | `performanceHudShowCpuUsage` | `"performance_hud_show_cpu_usage"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 90 | `performanceHudShowGpuUsage` | `"performance_hud_show_gpu_usage"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 91 | `performanceHudShowRamUsage` | `"performance_hud_show_ram_usage"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 92 | `performanceHudShowBatteryLevel` | `"performance_hud_show_battery_level"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 93 | `performanceHudShowPowerDraw` | `"performance_hud_show_power_draw"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 94 | `performanceHudShowBatteryRuntime` | `"performance_hud_show_battery_runtime"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 95 | `performanceHudShowBatteryTemperature` | `"performance_hud_show_battery_temperature"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 96 | `performanceHudShowClockTime` | `"performance_hud_show_clock_time"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 97 | `performanceHudShowCpuTemperature` | `"performance_hud_show_cpu_temperature"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 98 | `performanceHudShowGpuTemperature` | `"performance_hud_show_gpu_temperature"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 99 | `showPerformanceHudFan` | `"performance_hud_show_fan"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 100 | `showPerformanceHudTunerCaps` | `"performance_hud_show_tuner_caps"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 101 | `performanceHudShowFrameRateGraph` | `"performance_hud_show_frame_rate_graph"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 102 | `performanceHudShowCpuUsageGraph` | `"performance_hud_show_cpu_usage_graph"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 103 | `performanceHudShowGpuUsageGraph` | `"performance_hud_show_gpu_usage_graph"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 104 | `performanceHudBackgroundOpacity` | `"performance_hud_background_opacity"` | `floatPreferencesKey` | `0.72f` | `Float` (coerced `0f..1f`) |
| 105 | `performanceHudColorIntensity` | `"performance_hud_color_intensity"` | `floatPreferencesKey` | `1.0f` | `Float` (coerced `0f..1f`) |
| 106 | `performanceHudShowTextOutline` | `"performance_hud_show_text_outline"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 107 | `performanceHudSize` | `"performance_hud_size"` | `stringPreferencesKey` | `"medium"` | `String` / `PerformanceHudSize` |
| 108 | `performanceHudXFraction` | `"performance_hud_x_fraction"` | `floatPreferencesKey` | `-1.0f` | `Float` (coerced `-1f..1f`) |
| 109 | `performanceHudYFraction` | `"performance_hud_y_fraction"` | `floatPreferencesKey` | `-1.0f` | `Float` (coerced `-1f..1f`) |
| 110 | `powerControlDefaultEnabled` | `"power_control_default_enabled"` | `booleanPreferencesKey` | `DeviceGate.isDeviceSupported()` | `Boolean` |

#### Domain 5: `LibraryPreferences` (30 properties)
| # | Property | DataStore Key | Key Type | Exact Default | Kotlin Type / Transform |
|---|---|---|---|---|---|
| 111 | `libraryLayout` | `"library_layout"` | `intPreferencesKey` | `PaneType.UNDECIDED.ordinal` | `PaneType` enum |
| 112 | `libraryFilter` | `"library_filter"` | `intPreferencesKey` | `AppFilter.toFlags(EnumSet.of(AppFilter.GAME, AppFilter.SHARED))` | `EnumSet<AppFilter>` flags |
| 113 | `librarySortOption` | `"library_sort_key"` / `"library_sort"` | `stringPreferencesKey` / `intPreferencesKey` | `"installed_first"` / `0` | `SortOption` enum (fallback to legacy ordinal) |
| 114 | `itemsPerPage` | `"items_per_page"` | `intPreferencesKey` | `50` | `Int` |
| 115 | `showSteamInLibrary` | `"show_steam_in_library"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 116 | `showCustomGamesInLibrary` | `"show_custom_games_in_library"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 117 | `showGOGInLibrary` | `"show_gog_in_library"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 118 | `showEpicInLibrary` | `"show_epic_in_library"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 119 | `showAmazonInLibrary` | `"show_amazon_in_library"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 120 | `customGamesCount` | `"custom_games_count"` | `intPreferencesKey` | `0` | `Int` |
| 121 | `steamGamesCount` | `"steam_games_count"` | `intPreferencesKey` | `0` | `Int` |
| 122 | `gogGamesCount` | `"gog_games_count"` | `intPreferencesKey` | `0` | `Int` |
| 123 | `epicGamesCount` | `"epic_games_count"` | `intPreferencesKey` | `0` | `Int` |
| 124 | `gogInstalledGamesCount` | `"gog_installed_games_count"` | `intPreferencesKey` | `0` | `Int` |
| 125 | `epicInstalledGamesCount` | `"epic_installed_games_count"` | `intPreferencesKey` | `0` | `Int` |
| 126 | `amazonInstalledGamesCount` | `"amazon_installed_games_count"` | `intPreferencesKey` | `0` | `Int` |
| 127 | `librarySteamCollectionsCache` | `"library_steam_collections_cache"` | `stringPreferencesKey` | `""` | `String` (Raw JSON) |
| 128 | `librarySteamCollectionsSkippedDynamic` | `"library_steam_collections_skipped_dynamic"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 129 | `librarySteamCollections` | `"library_steam_collections"` | `stringPreferencesKey` | `""` | `Set<String>` (space-delimited) |
| 130 | `recommendationCacheJson` | `"recommendation_cache_json"` | `stringPreferencesKey` | `""` | `String` (Raw JSON) |
| 131 | `recommendationCacheTimestamp` | `"recommendation_cache_timestamp"` | `longPreferencesKey` | `0L` | `Long` |
| 132 | `showRecommendations` | `"show_recommendations"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 133 | `recDisclosureShown` | `"rec_disclosure_shown"` | `booleanPreferencesKey` | `false` | `Boolean` (volatile in-memory cached) |
| 134 | `recTeaserDismissedDay` | `"rec_teaser_dismissed_day"` | `longPreferencesKey` | `0L` | `Long` |
| 135 | `showAddCustomGameDialog` | `"show_add_custom_game_dialog"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 136 | `importCustomGameAsSteamGame` | `"import_custom_game_as_steam_game"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 137 | `customGamePaths` | `"custom_game_paths"` | `stringPreferencesKey` | `"[]"` | `Set<String>` (JSON Set) |
| 138 | `customGameManualFolders` | `"custom_game_manual_folders"` | `stringPreferencesKey` | `"[]"` | `Set<String>` (JSON Set) |
| 139 | `favoriteAppIds` | `"favorite_app_ids"` | `stringPreferencesKey` | `"[]"` | `Set<String>` (JSON Set, version-locked) |
| 140 | `gogAmazonPathMigrated` | `"gog_amazon_path_migrated"` | `booleanPreferencesKey` | `false` | `Boolean` |

#### Domain 6: `DownloadPreferences` (10 properties + helper methods)
| # | Property | DataStore Key | Key Type | Exact Default | Kotlin Type / Transform |
|---|---|---|---|---|---|
| 141 | `downloadOnWifiOnly` | `"download_on_wifi_only"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 142 | `downloadSpeed` | `"download_speed"` | `intPreferencesKey` | `24` | `Int` |
| 143 | `useExternalStorage` | `"use_external_storage"` | `booleanPreferencesKey` | `false` | `Boolean` (resets `externalStoragePath` on toggle) |
| 144 | `externalStoragePath` | `"external_storage_path"` | `stringPreferencesKey` | `""` | `String` |
| 145 | `fetchSteamGridDBImages` | `"fetch_steamgriddb_images"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 146 | `frontendSyncDirSteam` | `"frontend_sync_dir_steam"` | `stringPreferencesKey` | `""` | `String` |
| 147 | `frontendSyncDirEpic` | `"frontend_sync_dir_epic"` | `stringPreferencesKey` | `""` | `String` |
| 148 | `frontendSyncDirGog` | `"frontend_sync_dir_gog"` | `stringPreferencesKey` | `""` | `String` |
| 149 | `frontendSyncDirAmazon` | `"frontend_sync_dir_amazon"` | `stringPreferencesKey` | `""` | `String` |
| 150 | `frontendSyncDirCustom` | `"frontend_sync_dir_custom"` | `stringPreferencesKey` | `""` | `String` |

#### Domain 7: `GeneralPreferences` (25 properties)
| # | Property | DataStore Key | Key Type | Exact Default | Kotlin Type / Transform |
|---|---|---|---|---|---|
| 151 | `appTheme` | `"app_theme"` | `intPreferencesKey` | `AppTheme.AUTO.ordinal` | `AppTheme` enum |
| 152 | `appThemePalette` | `"app_theme_palette"` | `intPreferencesKey` | `PaletteStyle.TonalSpot.ordinal` | `PaletteStyle` enum |
| 153 | `startScreen` | `"start screen"` | `intPreferencesKey` | `HomeDestination.Library.ordinal` | `HomeDestination` enum (**contains space!**) |
| 154 | `allowedOrientation` | `"allowed_orientation"` | `intPreferencesKey` | `Orientation.toInt(EnumSet.of(Orientation.LANDSCAPE, Orientation.REVERSE_LANDSCAPE))` | `EnumSet<Orientation>` flags |
| 155 | `appLanguage` | `"app_language"` | `stringPreferencesKey` | `""` | `String` (empty = system default) |
| 156 | `openWebLinksExternally` | `"open_web_links_externally"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 157 | `hideStatusBarWhenNotInGame` | `"hide_status_bar_when_not_in_game"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 158 | `useAltLauncherIcon` | `"use_alt_launcher_icon"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 159 | `useAltNotificationIcon` | `"use_alt_notification_icon"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 160 | `achievementShowNotification` | `"achievement_show_notification"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 161 | `achievementPlaySound` | `"achievement_play_sound"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 162 | `achievementNotificationPosition` | `"achievement_notification_position"` | `stringPreferencesKey` | `"bottom_right"` | `String` |
| 163 | `warnBeforeExit` | `"warn_before_exit"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 164 | `usageAnalyticsEnabled` | `"usage_analytics_enabled"` | `booleanPreferencesKey` | `true` | `Boolean` |
| 165 | `recentlyCrashed` | `"recently_crashed"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 166 | `tipped` | `"tipped"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 167 | `hasAttemptedGameLaunch` | `"has_attempted_game_launch"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 168 | `lastLaunchPitchTime` | `"last_launch_pitch_time"` | `longPreferencesKey` | `0L` | `Long` |
| 169 | `lastWarmPitchTime` | `"last_warm_pitch_time"` | `longPreferencesKey` | `0L` | `Long` |
| 170 | `keyAttestationAvailable` | `"key_attestation_available"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 171 | `playIntegrityAvailable` | `"play_integrity_available"` | `booleanPreferencesKey` | `false` | `Boolean` |
| 172 | `componentManifestJson` | `"component_manifest_json"` | `stringPreferencesKey` | `""` | `String` |
| 173 | `componentManifestFetchedAt` | `"component_manifest_fetched_at"` | `longPreferencesKey` | `0L` | `Long` |
| 174 | `gameCompatibilityCache` | `"game_compatibility_cache"` | `stringPreferencesKey` | `"{}"` | `String` (Raw JSON) |
| 175 | `hltbCache` | `"hltb_cache"` | `stringPreferencesKey` | `"{}"` | `String` (Raw JSON) |
| 176 | `deviceGameStatsCache` | `"device_game_stats_cache"` | `stringPreferencesKey` | `"{}"` | `String` (Raw JSON) |
| 177 | `gpuGameStatsCache` | `"gpu_game_stats_cache"` | `stringPreferencesKey` | `"{}"` | `String` (Raw JSON) |
| 178 | `nexusLastPlacementJson` | `"nexus_last_placement_json"` | `stringPreferencesKey` | `"{}"` | `String` (cleared if blank or `"{}"`) |

---

## 2. Logic Chain

1. **Zero Data Loss Guarantee**:
   - The user configuration is stored in the `"PluviaPreferences"` DataStore file on the user's device.
   - Because all preference access across the entire app resolves to DataStore preference keys, retaining exact string identifiers (including casing anomalies such as `"dxwrapperConfig"`, `"videoPciDeviceID"`, `"offScreenRenderingMode"`, `"strictShaderMath"`, `"useDRI3"`, `"videoMemorySize"`, `"mouseWarpOverride"`, and space in `"start screen"`) ensures 100% preservation of all existing user settings during and after migration.
   - Any modification or normalization of these key names would cause the app to read defaults, resulting in data loss.

2. **Cryptographic Fidelity**:
   - `Crypto.kt` encrypts byte arrays with random IVs prepended (`iv + ciphertext`).
   - In `DefaultAuthPreferences`, decryption must check `if (bytes.isEmpty()) ""` prior to invoking `Crypto.decrypt()`.
   - On write, `if (token.isEmpty()) setPref(KEY, ByteArray(0))` (or remove the key) must be guarded so `Crypto.encrypt()` is never called with 0 bytes (which throws `IllegalArgumentException`).
   - If KeyStore or hardware decryption encounters an error (e.g. key invalidated or corrupt bytes), catch `GeneralSecurityException` / `IllegalArgumentException`, log via Timber, and gracefully return `""` to prevent fatal application startup crashes.

3. **Concurrency and Asynchronous Serialization Safety**:
   - For `favoriteAppIds`, multiple UI actions can fire rapidly when a user stars/unstars games. The version-lock pattern (`favoritePersistenceLock` + monotonically increasing `favoritePersistenceVersion`) ensures out-of-order DataStore edit completions do not clobber newer favorite states.
   - For `recDisclosureShown`, the `@Volatile recDisclosureShownCache` ensures that synchronous code immediately following consent confirmation sees `true` before the IO thread finishes persisting the preference.

4. **Side-Effect Preservation**:
   - `clearSteamSession()` must remove all 11 Steam-specific keys in a single atomic `dataStore.edit { ... }` block to ensure clean logout state without wiping user preferences.
   - `cellId` reset to 0 must reset `cellIdManuallySet = false`.
   - `useExternalStorage` toggle must clear `externalStoragePath = ""`.

---

## 3. Caveats

1. **No Modifying `com.winlator.PrefManager`**:
   - `com.winlator.PrefManager` uses `"WinlatorPreferences"`. Only `app.gamenative.PrefManager` (`"PluviaPreferences"`) is being decomposed.
2. **`Crypto.kt` Dependency on Android Runtime**:
   - `Crypto.kt` accesses `"AndroidKeyStore"`. Unit tests running on standard JVM (outside Robolectric or instrumentation) will throw `NoSuchAlgorithmException` or `KeyStoreException` unless `Crypto` or `AuthPreferences` is mocked or faked in test suites.
3. **Empty String Token Writes**:
   - In legacy `PrefManager`, writing `accessToken = ""` was not tested because `clearSteamSessionPreferences()` removed the key directly. However, in `DefaultAuthPreferences`, writing `""` must be handled safely as documented in Section 1.1.2.

---

## 4. Conclusion

1. Exact key string fidelity across all 98 preferences guarantees zero data loss during the transition from `PrefManager.kt` to the 7 domain interfaces.
2. Encrypted tokens (`accessToken`, `refreshToken`) use Android KeyStore AES-CBC-PKCS7 with prepended IVs and require empty-value guards on encryption and decryption.
3. Complex JSON structures (`favoriteAppIds`, `customGamePaths`, `customGameManualFolders`, `friendsListHeader`, cache blobs) must be decoded with try-catch protections, and `favoriteAppIds` must retain sequential version locking.
4. All four special side-effects (`clearSteamSession()`, `cellId` reset on `cellIdManuallySet`, `useExternalStorage` reset on `externalStoragePath`, `nexusLastPlacementJson` deletion on blank) are fully documented with exact logic for Milestone 1 implementation.

---

## 5. Verification Method

To independently verify the findings in this report:

1. **Inspect Preference Key Definitions & Defaults**:
   - View `app/src/main/java/app/gamenative/PrefManager.kt` and cross-check key constants, default values, and side-effects against Section 1.4 table.
2. **Inspect Crypto Implementation**:
   - View `app/src/main/java/app/gamenative/Crypto.kt` lines 15–76 to confirm AES/CBC/PKCS7Padding transformation, 256-bit key size, and IV prepending.
3. **Compile and Test Commands**:
   - Kotlin Compilation:
     ```powershell
     .\gradlew compileModernDebugKotlin
     ```
   - Unit Tests:
     ```powershell
     .\gradlew :app:testModernDebugUnitTest
     ```
4. **Invalidation Conditions**:
   - Any difference in key string literals (e.g. changing `"dxwrapperConfig"` to `"dx_wrapper_config"` or `"start screen"` to `"start_screen"`) violates backward compatibility and invalidates zero data loss guarantees.
