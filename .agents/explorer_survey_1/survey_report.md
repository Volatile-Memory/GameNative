# Mid-Level Singletons Refactoring Survey Report: Groups 1 & 2

**Author**: Explorer Survey Agent (`explorer_survey_1`)  
**Date**: 2026-09-02  
**Target Scope**: 
- **Group 1: Metadata & HowLongToBeat** (`HltbService`, `HltbCache`, `SteamGridDB`)
- **Group 2: Hardware & Compatibility Caches** (`DeviceGameStatsCache`, `GpuGameStatsCache`, `GameCompatibilityCache`)

---

## 1. Executive Summary

This survey provides a comprehensive investigation of the 6 targeted mid-level singletons across Groups 1 and 2. All 6 classes are currently Kotlin `object` declarations that store mutable state in static memory and rely on transient backward-compatibility hooks (`@Volatile var preferences: DomainPreferences? = null`) or static cross-singleton coupling.

### Key Architectural Findings:
1. **Escape Hatch Removal**: None of the 6 classes directly use `PreferencesEntryPoint` or `Context` in their core logic, but all 6 maintain `@Volatile var preferences` fields and optional parameter defaults (`prefs: DomainPreferences? = preferences`) that were introduced in earlier migration milestones as backward-compatibility bridges. Converting these to `@Singleton class` components with constructor-injected domain preferences (`GeneralPreferences`, `DownloadPreferences`) completely eliminates these static mutable variables.
2. **Internal Coupling in Group 1**: `HltbService` directly couples to `HltbCache` statically via `HltbCache.get()` and `HltbCache.put()`. `SteamGridDB` invokes `GameMetadataManager.read()` and `GameMetadataManager.update()` directly on local folders.
3. **Caller Categorization**:
   - **Hilt-Managed ViewModels**: `LibraryViewModel` and `GogRecommendationsViewModel` can directly receive all required singletons via `@Inject constructor(...)`.
   - **Composable / UI Trees**: `BaseAppScreen` and `CustomGameAppScreen` currently invoke `HltbService.getStats()`, `GameCompatibilityCache.getCached()`, and `SteamGridDB.fetchGameImages()` statically. For non-Hilt Composable hierarchies, a dedicated `@EntryPoint` (`AppUtilsEntryPoint`) provides clean access via `remember(context) { AppUtilsEntryPoint.get(context) }` without prop-drilling or context pollution.
   - **Unit Tests**: `HltbCacheTest`, `HltbServiceIntegrationTest`, and `HltbServiceTest` will no longer require static `.reset()` or `.resetForTesting()` methods; tests can simply instantiate the class under test with mock constructor arguments.

---

## 2. Group 1: Metadata & HowLongToBeat

### 2.1. `HltbService`

#### Exact Location & Current Declaration
- **File**: `app/src/main/java/app/gamenative/utils/HltbService.kt`
- **Current Declaration**: `object HltbService` (lines 33–273)

#### State, Fields & Lifecycle Requirements
- **Fields & State**:
  - `private const val DEFAULT_API_BASE_URL = "https://howlongtobeat.com"`
  - `private const val SEARCH_PATH = "/api/bleed"`
  - `private const val INIT_PATH = "$SEARCH_PATH/init"`
  - `private const val UA = "Mozilla/5.0 ..."`
  - `const val GAME_URL = "https://howlongtobeat.com/game/"` (Used by UI to construct external browser intent)
  - `const val UNKNOWN_HOURS = "--"` (Used by UI for missing/unknown stats)
  - `@Serializable data class Stats(...)` (Public data model)
  - `private data class Auth(val token: String, val hpKey: String, val hpVal: String)`
  - `private sealed class SearchResult`
  - `@Volatile private var auth: Auth? = null` (Session auth tokens cached in memory)
  - `@Volatile private var apiBaseUrl = DEFAULT_API_BASE_URL` (For mock testing)
  - `private val httpClient = Net.http.newBuilder().protocols(listOf(Protocol.HTTP_1_1)).build()` (Forced HTTP/1.1 client)
- **Lifecycle & Scoping**: `@Singleton` scope bound to `SingletonComponent`.

#### Escape Hatches & Usages
- No direct `Context` or `PreferencesEntryPoint` usage.
- Statically references `HltbCache.get(name)` and `HltbCache.put(name, stats)` (lines 105, 120).

#### Required Hilt Dependencies
- `private val hltbCache: HltbCache`
- `@IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO`

#### All Call Sites & References
| File Path | Line(s) | Usage Type | Current Access Pattern |
|---|---|---|---|
| `app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt` | 1189, 1194 | Method call & type | `mutableStateOf<HltbService.Stats?>`, `HltbService.getStats(displayInfoBase.name)` |
| `app/src/main/java/app/gamenative/ui/screen/library/LibraryAppScreen.kt` | 421, 441, 487–488 | Types & constants | `stats: HltbService.Stats`, `HltbService.GAME_URL`, `HltbService.UNKNOWN_HOURS` |
| `app/src/main/java/app/gamenative/ui/data/GameDisplayInfo.kt` | 25 | Type reference | `val hltbStats: app.gamenative.utils.HltbService.Stats? = null` |
| `app/src/test/java/app/gamenative/utils/HltbCacheTest.kt` | 17 | Type reference | `HltbService.Stats(...)` |
| `app/src/test/java/app/gamenative/utils/HltbServiceTest.kt` | 18, 31, 46, 54, 59–61 | Pure static methods | `HltbService.formatHours`, `HltbService.normalize`, `HltbService.levenshtein` |
| `app/src/test/java/app/gamenative/utils/HltbServiceIntegrationTest.kt` | 35, 40, 53, 90, 92, 104, 113, 125, 143, 146 | Test hooks & API call | `HltbService.setApiBaseUrlForTesting`, `HltbService.resetForTesting`, `HltbService.getStats` |

#### Proposed Signature & Migration Plan
```kotlin
@Singleton
class HltbService @Inject constructor(
    private val hltbCache: HltbCache,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val GAME_URL = "https://howlongtobeat.com/game/"
        const val UNKNOWN_HOURS = "--"

        internal fun formatHours(seconds: Long) = if (seconds <= 0) UNKNOWN_HOURS else "%.1f".format(seconds / 3600.0)
        internal fun normalize(input: String) = normalizedKey(input)
        internal fun levenshtein(left: String, right: String): Int { ... }
    }

    suspend fun getStats(name: String): Stats? = withContext(ioDispatcher) {
        if (name.isBlank()) return@withContext null
        hltbCache.get(name)?.let { return@withContext it }
        ...
        hltbCache.put(name, stats)
        stats
    }
}
```

---

### 2.2. `HltbCache`

#### Exact Location & Current Declaration
- **File**: `app/src/main/java/app/gamenative/utils/HltbService.kt` (lines 276–353) *(Can optionally be moved to its own `HltbCache.kt` file)*
- **Current Declaration**: `object HltbCache`

#### State, Fields & Lifecycle Requirements
- **Fields & State**:
  - `private const val TTL = 12 * 3_600_000L` (12 hours)
  - `internal const val MAX_ENTRIES = 200`
  - `@Volatile var preferences: GeneralPreferences? = null` *(To be removed)*
  - `private val mem = mutableMapOf<String, HltbService.Stats>()`
  - `private val stamps = mutableMapOf<String, Long>()`
  - `private var loaded = false`
  - `private val json = Json { ignoreUnknownKeys = true }`
  - `@Serializable data class Entry(val stats: HltbService.Stats, val ts: Long)`
- **Thread Safety**: Methods are annotated with `@Synchronized`.
- **Lifecycle**: `@Singleton` tied to application lifetime.

#### Escape Hatches & Usages
- Reads/writes `GeneralPreferences.hltbCache` for JSON persistence.
- Uses `@Volatile var preferences: GeneralPreferences?` as a fallback locator.

#### Required Hilt Dependencies
- `private val generalPreferences: GeneralPreferences`

#### All Call Sites & References
| File Path | Line(s) | Usage Type | Current Access Pattern |
|---|---|---|---|
| `app/src/main/java/app/gamenative/utils/HltbService.kt` | 105, 120 | Service cache read/write | `HltbCache.get(name)`, `HltbCache.put(name, stats)` |
| `app/src/test/java/app/gamenative/utils/HltbCacheTest.kt` | 32, 37, 42, 46, 47, 53, 58, 59, 61, 63, 65, 66, 71, 72, 73 | Unit test executions | `HltbCache.reset()`, `HltbCache.put()`, `HltbCache.get()` |
| `app/src/test/java/app/gamenative/utils/HltbServiceIntegrationTest.kt` | 34, 41 | Reset between tests | `HltbCache.reset()` |

#### Proposed Signature & Migration Plan
```kotlin
@Singleton
class HltbCache @Inject constructor(
    private val generalPreferences: GeneralPreferences,
) {
    companion object {
        private const val TTL = 12 * 3_600_000L
        internal const val MAX_ENTRIES = 200
        internal fun key(name: String) = normalizedKey(name)
    }

    private val mem = mutableMapOf<String, HltbService.Stats>()
    private val stamps = mutableMapOf<String, Long>()
    private var loaded = false
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Entry(val stats: HltbService.Stats, val ts: Long)

    @Synchronized
    private fun load() {
        if (loaded) return
        try {
            val raw = generalPreferences.hltbCache
            if (raw.isNotEmpty() && raw != "{}") {
                val now = System.currentTimeMillis()
                json.decodeFromString<Map<String, Entry>>(raw)
                    .asSequence()
                    .filter { (_, entry) -> now - entry.ts < TTL }
                    .sortedByDescending { (_, entry) -> entry.ts }
                    .take(MAX_ENTRIES)
                    .forEach { (k, entry) ->
                        mem[k] = entry.stats
                        stamps[k] = entry.ts
                    }
            }
        } catch (_: Exception) {
        } finally {
            loaded = true
        }
    }

    @Synchronized
    private fun save() {
        try {
            val now = System.currentTimeMillis()
            generalPreferences.hltbCache = json.encodeToString(
                mem.mapValues { Entry(it.value, stamps[it.key] ?: now) }
            )
        } catch (_: Exception) {}
    }

    @Synchronized
    fun get(name: String): HltbService.Stats? {
        load()
        val k = key(name)
        val ts = stamps[k] ?: return null
        if (System.currentTimeMillis() - ts >= TTL) {
            mem.remove(k)
            stamps.remove(k)
            return null
        }
        return mem[k]
    }

    @Synchronized
    fun put(name: String, stats: HltbService.Stats) {
        load()
        val k = key(name)
        if (mem.size >= MAX_ENTRIES && !mem.containsKey(k)) {
            stamps.minByOrNull { it.value }?.key?.let { oldest ->
                mem.remove(oldest)
                stamps.remove(oldest)
            }
        }
        mem[k] = stats
        stamps[k] = System.currentTimeMillis()
        save()
    }

    @Synchronized
    internal fun reset() {
        mem.clear()
        stamps.clear()
        loaded = false
    }
}
```

---

### 2.3. `SteamGridDB`

#### Exact Location & Current Declaration
- **File**: `app/src/main/java/app/gamenative/utils/SteamGridDB.kt`
- **Current Declaration**: `object SteamGridDB` (lines 24–575)

#### State, Fields & Lifecycle Requirements
- **Fields & State**:
  - `private const val API_BASE_URL = "https://www.steamgriddb.com/api/v2"`
  - `private const val SEARCH_ENDPOINT = "/search/autocomplete"`
  - `private const val GRIDS_ENDPOINT = "/grids/game"`
  - `private const val HEROES_ENDPOINT = "/heroes/game"`
  - `private const val LOGOS_ENDPOINT = "/logos/game"`
  - `@Volatile var preferences: DownloadPreferences? = null` *(To be removed)*
  - `private val httpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()`
  - Data classes: `GameSearchResult(val gameId: Int, val name: String, val releaseDate: Long)`, `ImageFetchResult(...)`
- **Functions**: `searchGame(gameName: String)`, `fetchGameImages(gameName: String, gameFolderPath: String)`, and private orientation / download routines.

#### Escape Hatches & Usages
- Uses `DownloadPreferences.fetchSteamGridDBImages` via `@Volatile var preferences` and optional function parameter default.
- Reads and updates metadata using `GameMetadataManager.read(gameFolder)` and `GameMetadataManager.update(...)`.

#### Required Hilt Dependencies
- `private val downloadPreferences: DownloadPreferences`
- `@IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO`

#### All Call Sites & References
| File Path | Line(s) | Usage Type | Current Access Pattern |
|---|---|---|---|
| `app/src/main/java/app/gamenative/ui/screen/library/appscreen/CustomGameAppScreen.kt` | 417 | Method invocation | `SteamGridDB.fetchGameImages(gameName, gameFolderPath)` inside `getFetchImagesOption()` |
| *(Note: UI files `CustomGameAppScreen.kt:73, 90, 101, 124` and `LibraryGridCard.kt:672, 697, 702` have private helper functions named `findSteamGridDBImage` that check files on disk, not calling the class)* | N/A | N/A | Local file inspection only |

#### Proposed Signature & Migration Plan
```kotlin
@Singleton
class SteamGridDB @Inject constructor(
    private val downloadPreferences: DownloadPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        private const val API_BASE_URL = "https://www.steamgriddb.com/api/v2"
        private const val SEARCH_ENDPOINT = "/search/autocomplete"
        private const val GRIDS_ENDPOINT = "/grids/game"
        private const val HEROES_ENDPOINT = "/heroes/game"
        private const val LOGOS_ENDPOINT = "/logos/game"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(): String? {
        val apiKey = app.gamenative.BuildConfig.STEAMGRIDDB_API_KEY
        return if (apiKey.isNotEmpty()) apiKey else null
    }

    suspend fun searchGame(gameName: String): GameSearchResult? = withContext(ioDispatcher) {
        val apiKey = getApiKey() ?: return@withContext null
        if (!downloadPreferences.fetchSteamGridDBImages) {
            Timber.tag("SteamGridDB").d("Image fetching is disabled in settings")
            return@withContext null
        }
        ...
    }

    suspend fun fetchGameImages(
        gameName: String,
        gameFolderPath: String,
    ): ImageFetchResult = withContext(ioDispatcher) {
        val apiKey = getApiKey() ?: return@withContext ImageFetchResult(null, null, null, null, null)
        if (!downloadPreferences.fetchSteamGridDBImages) {
            Timber.tag("SteamGridDB").d("Image fetching is disabled in settings")
            return@withContext ImageFetchResult(null, null, null, null, null)
        }
        ...
    }
}
```

---

## 3. Group 2: Hardware & Compatibility Caches

### 3.1. `DeviceGameStatsCache`

#### Exact Location & Current Declaration
- **File**: `app/src/main/java/app/gamenative/utils/DeviceGameStatsCache.kt`
- **Current Declaration**: `object DeviceGameStatsCache` (lines 18–133)

#### State, Fields & Lifecycle Requirements
- **Fields & State**:
  - `private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L` (6 hours)
  - `@Volatile var preferences: GeneralPreferences? = null` *(To be removed)*
  - `private var inMemory: Map<GameSource, Map<String, DeviceGameStats>> = emptyMap()`
  - `private var loadedTimestamp: Long = 0L`
  - `private var cacheLoaded = false`
  - `@Serializable private data class CachedStats(...)`
  - `@Serializable private data class DeviceGameStatsData(...)`
- **Thread Safety**: `loadCache()` is `@Synchronized`. `refreshIfStale()`, `saveCache()`, `getStats()`, `getAll()`, `clear()` access cache state. Should be fully thread-safe.

#### Escape Hatches & Usages
- Reads/writes `GeneralPreferences.deviceGameStatsCache`.
- Calls `DeviceGameStatsService.fetchForDevice(deviceModel, gpuName, modernBuild)`.

#### Required Hilt Dependencies
- `private val generalPreferences: GeneralPreferences`

#### All Call Sites & References
| File Path | Line(s) | Usage Type | Current Access Pattern |
|---|---|---|---|
| `app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt` | 116 | Cache read | `val deviceAll = DeviceGameStatsCache.getAll()` |
| `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt` | 195, 209 | Initial refresh & state population | `DeviceGameStatsCache.refreshIfStale(...)`, `DeviceGameStatsCache.getAll()` |
| `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt` | 523, 551, 564 | Manual refresh | `DeviceGameStatsCache.clear()`, `DeviceGameStatsCache.refreshIfStale(...)`, `DeviceGameStatsCache.getAll()` |

#### Proposed Signature & Migration Plan
```kotlin
@Singleton
class DeviceGameStatsCache @Inject constructor(
    private val generalPreferences: GeneralPreferences,
) {
    companion object {
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
    }

    private var inMemory: Map<GameSource, Map<String, DeviceGameStats>> = emptyMap()
    private var loadedTimestamp: Long = 0L
    private var cacheLoaded = false

    @Synchronized
    private fun loadCache() {
        if (cacheLoaded) return
        try {
            val cacheJson = generalPreferences.deviceGameStatsCache
            if (cacheJson.isNotEmpty() && cacheJson != "{}") {
                val cached = Json.decodeFromString<CachedStats>(cacheJson)
                inMemory = cached.stats.mapNotNull { (platform, games) ->
                    val source = runCatching { GameSource.valueOf(platform) }.getOrNull()
                        ?: return@mapNotNull null
                    source to games.mapValues { it.value.toStats() }
                }.toMap()
                loadedTimestamp = cached.timestamp
                Timber.tag("DeviceGameStatsCache").d("Loaded ${inMemory.values.sumOf { it.size }} cached game stats")
            }
        } catch (e: Exception) {
            Timber.tag("DeviceGameStatsCache").e(e, "Failed to load cache from persistent storage")
        }
        cacheLoaded = true
    }

    @Synchronized
    private fun saveCache(data: Map<GameSource, Map<String, DeviceGameStats>>, timestamp: Long) {
        try {
            val serializable = CachedStats(
                stats = data.entries.associate { (source, games) ->
                    source.name to games.mapValues { it.value.toData() }
                },
                timestamp = timestamp,
            )
            generalPreferences.deviceGameStatsCache = Json.encodeToString(serializable)
            Timber.tag("DeviceGameStatsCache").d("Saved ${data.values.sumOf { it.size }} game stats to persistent storage")
        } catch (e: Exception) {
            Timber.tag("DeviceGameStatsCache").e(e, "Failed to save cache to persistent storage")
        }
    }

    suspend fun refreshIfStale(deviceModel: String, gpuName: String, modernBuild: Boolean) {
        loadCache()
        val now = System.currentTimeMillis()
        if (loadedTimestamp != 0L && now - loadedTimestamp < CACHE_TTL_MS) {
            Timber.tag("DeviceGameStatsCache").d("Cache is fresh, skipping fetch")
            return
        }

        val fetched = DeviceGameStatsService.fetchForDevice(deviceModel, gpuName, modernBuild)
        if (fetched != null) {
            synchronized(this) {
                inMemory = fetched
                loadedTimestamp = now
                saveCache(fetched, now)
            }
        }
    }

    @Synchronized
    fun getStats(source: GameSource, gameName: String): DeviceGameStats? {
        loadCache()
        return inMemory[source]?.get(gameName)
    }

    @Synchronized
    fun getAll(): Map<GameSource, Map<String, DeviceGameStats>> {
        loadCache()
        return inMemory
    }

    @Synchronized
    fun clear() {
        inMemory = emptyMap()
        loadedTimestamp = 0L
        generalPreferences.deviceGameStatsCache = "{}"
        Timber.tag("DeviceGameStatsCache").d("Cache cleared")
    }
}
```

---

### 3.2. `GpuGameStatsCache`

#### Exact Location & Current Declaration
- **File**: `app/src/main/java/app/gamenative/utils/GpuGameStatsCache.kt`
- **Current Declaration**: `object GpuGameStatsCache` (lines 18–133)

#### State, Fields & Lifecycle Requirements
- **Fields & State**:
  - `private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L` (6 hours)
  - `@Volatile var preferences: GeneralPreferences? = null` *(To be removed)*
  - `private var inMemory: Map<GameSource, Map<String, DeviceGameStats>> = emptyMap()`
  - `private var loadedTimestamp: Long = 0L`
  - `private var cacheLoaded = false`
- **Thread Safety**: Same synchronization requirements as `DeviceGameStatsCache`.

#### Escape Hatches & Usages
- Reads/writes `GeneralPreferences.gpuGameStatsCache`.
- Calls `DeviceGameStatsService.fetchForGpu(gpuName, modernBuild)`.

#### Required Hilt Dependencies
- `private val generalPreferences: GeneralPreferences`

#### All Call Sites & References
| File Path | Line(s) | Usage Type | Current Access Pattern |
|---|---|---|---|
| `app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt` | 117 | Cache read | `val gpuAll = GpuGameStatsCache.getAll()` |
| `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt` | 200, 210 | Initial refresh & state population | `GpuGameStatsCache.refreshIfStale(...)`, `GpuGameStatsCache.getAll()` |
| `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt` | 524, 556, 565 | Manual refresh | `GpuGameStatsCache.clear()`, `GpuGameStatsCache.refreshIfStale(...)`, `GpuGameStatsCache.getAll()` |

#### Proposed Signature & Migration Plan
```kotlin
@Singleton
class GpuGameStatsCache @Inject constructor(
    private val generalPreferences: GeneralPreferences,
) {
    companion object {
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
    }

    private var inMemory: Map<GameSource, Map<String, DeviceGameStats>> = emptyMap()
    private var loadedTimestamp: Long = 0L
    private var cacheLoaded = false

    @Synchronized
    private fun loadCache() {
        if (cacheLoaded) return
        try {
            val cacheJson = generalPreferences.gpuGameStatsCache
            if (cacheJson.isNotEmpty() && cacheJson != "{}") {
                val cached = Json.decodeFromString<CachedStats>(cacheJson)
                inMemory = cached.stats.mapNotNull { (platform, games) ->
                    val source = runCatching { GameSource.valueOf(platform) }.getOrNull()
                        ?: return@mapNotNull null
                    source to games.mapValues { it.value.toStats() }
                }.toMap()
                loadedTimestamp = cached.timestamp
                Timber.tag("GpuGameStatsCache").d("Loaded ${inMemory.values.sumOf { it.size }} cached game stats")
            }
        } catch (e: Exception) {
            Timber.tag("GpuGameStatsCache").e(e, "Failed to load cache from persistent storage")
        }
        cacheLoaded = true
    }

    @Synchronized
    private fun saveCache(data: Map<GameSource, Map<String, DeviceGameStats>>, timestamp: Long) {
        try {
            val serializable = CachedStats(
                stats = data.entries.associate { (source, games) ->
                    source.name to games.mapValues { it.value.toData() }
                },
                timestamp = timestamp,
            )
            generalPreferences.gpuGameStatsCache = Json.encodeToString(serializable)
            Timber.tag("GpuGameStatsCache").d("Saved ${data.values.sumOf { it.size }} game stats to persistent storage")
        } catch (e: Exception) {
            Timber.tag("GpuGameStatsCache").e(e, "Failed to save cache to persistent storage")
        }
    }

    suspend fun refreshIfStale(gpuName: String, modernBuild: Boolean) {
        loadCache()
        val now = System.currentTimeMillis()
        if (loadedTimestamp != 0L && now - loadedTimestamp < CACHE_TTL_MS) {
            Timber.tag("GpuGameStatsCache").d("Cache is fresh, skipping fetch")
            return
        }

        val fetched = DeviceGameStatsService.fetchForGpu(gpuName, modernBuild)
        if (fetched != null) {
            synchronized(this) {
                inMemory = fetched
                loadedTimestamp = now
                saveCache(fetched, now)
            }
        }
    }

    @Synchronized
    fun getStats(source: GameSource, gameName: String): DeviceGameStats? {
        loadCache()
        return inMemory[source]?.get(gameName)
    }

    @Synchronized
    fun getAll(): Map<GameSource, Map<String, DeviceGameStats>> {
        loadCache()
        return inMemory
    }

    @Synchronized
    fun clear() {
        inMemory = emptyMap()
        loadedTimestamp = 0L
        generalPreferences.gpuGameStatsCache = "{}"
        Timber.tag("GpuGameStatsCache").d("Cache cleared")
    }
}
```

---

### 3.3. `GameCompatibilityCache`

#### Exact Location & Current Declaration
- **File**: `app/src/main/java/app/gamenative/utils/GameCompatibilityCache.kt`
- **Current Declaration**: `object GameCompatibilityCache` (lines 14–194)

#### State, Fields & Lifecycle Requirements
- **Fields & State**:
  - `private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L` (6 hours)
  - `@Volatile var preferences: GeneralPreferences? = null` *(To be removed)*
  - `private val inMemoryCache = mutableMapOf<String, GameCompatibilityService.GameCompatibilityResponse>()`
  - `private val timestamps = mutableMapOf<String, Long>()`
  - `private var cacheLoaded = false`
  - `@Serializable data class CachedCompatibilityResponse(...)`
  - `@Serializable data class GameCompatibilityResponseData(...)`
- **Thread Safety**: Add `@Synchronized` to `loadCache`, `saveCache`, `getCached`, `cache`, `cacheAll`, `isCached`, `clear`, and `size`.

#### Escape Hatches & Usages
- Reads/writes `GeneralPreferences.gameCompatibilityCache`.

#### Required Hilt Dependencies
- `private val generalPreferences: GeneralPreferences`

#### All Call Sites & References
| File Path | Line(s) | Usage Type | Current Access Pattern |
|---|---|---|---|
| `app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt` | 101, 107 | Cache query & batch cache update | `GameCompatibilityCache.getCached(name)`, `GameCompatibilityCache.cacheAll(it)` |
| `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt` | 522 | Clear cache on refresh | `GameCompatibilityCache.clear()` |
| `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt` | 681 | Filter evaluation | `GameCompatibilityCache.getCached(gameName)` in `passesCompatibleFilter()` |
| `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt` | 1215, 1249 | Batch page fetch & cache | `GameCompatibilityCache.getCached(gameName)`, `GameCompatibilityCache.cacheAll(batchResults)` |
| `app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt` | 304 | UI display info | `val cachedResponse = GameCompatibilityCache.getCached(gameName)` in `rememberCompatibilityInfo()` |

#### Proposed Signature & Migration Plan
```kotlin
@Singleton
class GameCompatibilityCache @Inject constructor(
    private val generalPreferences: GeneralPreferences,
) {
    companion object {
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
    }

    private val inMemoryCache = mutableMapOf<String, GameCompatibilityService.GameCompatibilityResponse>()
    private val timestamps = mutableMapOf<String, Long>()
    private var cacheLoaded = false

    @Synchronized
    private fun loadCache() {
        if (cacheLoaded) return
        try {
            val cacheJson = generalPreferences.gameCompatibilityCache
            if (cacheJson.isEmpty() || cacheJson == "{}") {
                cacheLoaded = true
                return
            }

            val cacheMap = Json.decodeFromString<Map<String, CachedCompatibilityResponse>>(cacheJson)
            cacheMap.forEach { (gameName, cached) ->
                inMemoryCache[gameName] = cached.response.toResponse()
                timestamps[gameName] = cached.timestamp
            }
            Timber.tag("GameCompatibilityCache").d("Loaded ${inMemoryCache.size} cached entries from persistent storage")
            cacheLoaded = true
        } catch (e: Exception) {
            Timber.tag("GameCompatibilityCache").e(e, "Failed to load cache from persistent storage")
            cacheLoaded = true
        }
    }

    @Synchronized
    private fun saveCache() {
        try {
            val now = System.currentTimeMillis()
            val cacheMap = inMemoryCache.mapValues { (gameName, response) ->
                val timestamp = timestamps[gameName] ?: now
                CachedCompatibilityResponse(response.toData(), timestamp)
            }
            generalPreferences.gameCompatibilityCache = Json.encodeToString(cacheMap)
            Timber.tag("GameCompatibilityCache").d("Saved ${cacheMap.size} entries to persistent storage")
        } catch (e: Exception) {
            Timber.tag("GameCompatibilityCache").e(e, "Failed to save cache to persistent storage")
        }
    }

    @Synchronized
    fun getCached(gameName: String): GameCompatibilityService.GameCompatibilityResponse? {
        loadCache()
        val cached = inMemoryCache[gameName] ?: return null
        val timestamp = timestamps[gameName] ?: return null
        val now = System.currentTimeMillis()
        if (now - timestamp >= CACHE_TTL_MS) {
            inMemoryCache.remove(gameName)
            timestamps.remove(gameName)
            Timber.tag("GameCompatibilityCache").d("Removed expired cache entry for: $gameName")
            return null
        }
        return cached
    }

    @Synchronized
    fun cache(gameName: String, response: GameCompatibilityService.GameCompatibilityResponse) {
        loadCache()
        val now = System.currentTimeMillis()
        inMemoryCache[gameName] = response
        timestamps[gameName] = now
        saveCache()
        Timber.tag("GameCompatibilityCache").d("Cached compatibility for: $gameName")
    }

    @Synchronized
    fun cacheAll(responses: Map<String, GameCompatibilityService.GameCompatibilityResponse>) {
        loadCache()
        val now = System.currentTimeMillis()
        inMemoryCache.putAll(responses)
        responses.keys.forEach { gameName ->
            timestamps[gameName] = now
        }
        saveCache()
        Timber.tag("GameCompatibilityCache").d("Cached ${responses.size} compatibility entries")
    }

    @Synchronized
    fun isCached(gameName: String): Boolean {
        loadCache()
        return getCached(gameName) != null
    }

    @Synchronized
    fun clear() {
        inMemoryCache.clear()
        timestamps.clear()
        generalPreferences.gameCompatibilityCache = "{}"
        Timber.tag("GameCompatibilityCache").d("Cache cleared")
    }

    @Synchronized
    fun size(): Int {
        loadCache()
        return inMemoryCache.size
    }
}
```

---

## 4. Cross-Cutting Strategy & Common Patterns

### 4.1. UI / Composable Access Strategy
For ViewModels, Hilt automatically injects the singletons. For non-ViewModel classes in the Compose tree (`BaseAppScreen`, `CustomGameAppScreen`), we introduce `AppUtilsEntryPoint` in `app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt`:

```kotlin
package app.gamenative.di

import android.content.Context
import app.gamenative.utils.DeviceGameStatsCache
import app.gamenative.utils.GameCompatibilityCache
import app.gamenative.utils.GpuGameStatsCache
import app.gamenative.utils.HltbCache
import app.gamenative.utils.HltbService
import app.gamenative.utils.SteamGridDB
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUtilsEntryPoint {
    fun hltbService(): HltbService
    fun hltbCache(): HltbCache
    fun steamGridDb(): SteamGridDB
    fun deviceGameStatsCache(): DeviceGameStatsCache
    fun gpuGameStatsCache(): GpuGameStatsCache
    fun gameCompatibilityCache(): GameCompatibilityCache

    companion object {
        fun get(context: Context): AppUtilsEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext ?: context,
                AppUtilsEntryPoint::class.java,
            )
    }
}

fun Context.appUtilsEntryPoint(): AppUtilsEntryPoint = AppUtilsEntryPoint.get(this)
```

In `BaseAppScreen.kt`:
```kotlin
val hltbService = remember(context) { context.appUtilsEntryPoint().hltbService() }
val gameCompatibilityCache = remember(context) { context.appUtilsEntryPoint().gameCompatibilityCache() }
```

In `CustomGameAppScreen.kt`:
```kotlin
val steamGridDb = remember(context) { context.appUtilsEntryPoint().steamGridDb() }
```

### 4.2. Elimination of `@Volatile var preferences`
Each class currently contains:
- Group 1:
  - `HltbCache`: `@Volatile var preferences: GeneralPreferences? = null`
  - `SteamGridDB`: `@Volatile var preferences: DownloadPreferences? = null`
- Group 2:
  - `DeviceGameStatsCache`: `@Volatile var preferences: GeneralPreferences? = null`
  - `GpuGameStatsCache`: `@Volatile var preferences: GeneralPreferences? = null`
  - `GameCompatibilityCache`: `@Volatile var preferences: GeneralPreferences? = null`

All 5 instances of `@Volatile var preferences` and default arguments `(prefs: ...? = preferences)` will be deleted during the refactoring. The domain preference repositories will be injected directly as `private val` properties.

---

## 5. File Change Manifest & Caller Summary

### Core Target Files to Convert:
1. `app/src/main/java/app/gamenative/utils/HltbService.kt`
   - Convert `HltbService` to `@Singleton class ... @Inject constructor(hltbCache, ioDispatcher)`
   - Convert `HltbCache` to `@Singleton class ... @Inject constructor(generalPreferences)`
2. `app/src/main/java/app/gamenative/utils/SteamGridDB.kt`
   - Convert `SteamGridDB` to `@Singleton class ... @Inject constructor(downloadPreferences, ioDispatcher)`
3. `app/src/main/java/app/gamenative/utils/DeviceGameStatsCache.kt`
   - Convert `DeviceGameStatsCache` to `@Singleton class ... @Inject constructor(generalPreferences)`
4. `app/src/main/java/app/gamenative/utils/GpuGameStatsCache.kt`
   - Convert `GpuGameStatsCache` to `@Singleton class ... @Inject constructor(generalPreferences)`
5. `app/src/main/java/app/gamenative/utils/GameCompatibilityCache.kt`
   - Convert `GameCompatibilityCache` to `@Singleton class ... @Inject constructor(generalPreferences)`

### New DI Entry Point:
6. `app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt`
   - Entry point for UI Composables needing singleton utilities.

### Downstream Callers to Refactor:
7. `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt`
   - Add `private val deviceGameStatsCache: DeviceGameStatsCache`, `private val gpuGameStatsCache: GpuGameStatsCache`, `private val gameCompatibilityCache: GameCompatibilityCache` to `@Inject constructor(...)`.
   - Update static calls to instance calls.
8. `app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt`
   - Add `private val deviceGameStatsCache: DeviceGameStatsCache`, `private val gpuGameStatsCache: GpuGameStatsCache`, `private val gameCompatibilityCache: GameCompatibilityCache` to `@Inject constructor(...)`.
   - Update static calls to instance calls.
9. `app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt`
   - Obtain `HltbService` and `GameCompatibilityCache` via `context.appUtilsEntryPoint()`.
10. `app/src/main/java/app/gamenative/ui/screen/library/appscreen/CustomGameAppScreen.kt`
    - Obtain `SteamGridDB` via `context.appUtilsEntryPoint()`.

### Unit Tests to Refactor:
11. `app/src/test/java/app/gamenative/utils/HltbCacheTest.kt`
    - Instantiate `HltbCache(generalPreferences)` directly instead of static object calls.
12. `app/src/test/java/app/gamenative/utils/HltbServiceIntegrationTest.kt`
    - Instantiate `HltbCache(generalPreferences)` and `HltbService(hltbCache)` directly.
13. `app/src/test/java/app/gamenative/utils/HltbServiceTest.kt`
    - Calls to companion object pure functions `HltbService.formatHours`, `HltbService.normalize`, `HltbService.levenshtein` remain unchanged.
