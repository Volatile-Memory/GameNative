package app.gamenative

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.StrictMode
import android.util.DisplayMetrics
import android.view.Display
import android.os.SystemClock
import android.os.Trace
import app.gamenative.core.runtime.DefaultGameSessionManager
import app.gamenative.core.runtime.GameSessionRuntime
import app.gamenative.di.appUtilsEntryPoint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import app.gamenative.db.dao.AmazonGameDao
import app.gamenative.db.dao.GOGGameDao
import app.gamenative.events.EventDispatcher
import app.gamenative.powercontrol.PowerManager
import app.gamenative.service.ActiveGameRegistry
import app.gamenative.service.DownloadService
import app.gamenative.service.SteamService
import app.gamenative.ui.screen.xserver.RadialMenuCoordinator
import app.gamenative.utils.ContainerMigrator
import app.gamenative.utils.IntentLaunchManager
import app.gamenative.utils.PlayIntegrity
import app.gamenative.utils.downloader.ContainerFilesDownloader
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import com.google.android.play.core.splitcompat.SplitCompatApplication
import com.posthog.PersonProfiles

// Add PostHog imports
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.winlator.container.Container
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.widget.InputControlsView
import com.winlator.widget.TouchpadView
import com.winlator.widget.XServerRendererView
import com.winlator.xenvironment.XEnvironment
import timber.log.Timber
import app.gamenative.preferences.PreferencesEntryPoint
import dagger.hilt.android.HiltAndroidApp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

typealias NavChangedListener = NavController.OnDestinationChangedListener

@HiltAndroidApp
class PluviaApp : SplitCompatApplication() {

    @Inject lateinit var gogGameDao: GOGGameDao
    @Inject lateinit var amazonGameDao: AmazonGameDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private inline fun <T> traceStartupStep(sectionName: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        Trace.beginSection(sectionName)
        try {
            return block()
        } finally {
            Trace.endSection()
            val duration = SystemClock.elapsedRealtime() - start
            Timber.d("[StartupInit] %s took %d ms", sectionName, duration)
        }
    }

    override fun onCreate() {
        val appCreateStart = SystemClock.elapsedRealtime()
        super.onCreate()
        instance = this

        traceStartupStep("preloadSystemLibraries") { preloadSystemLibraries() }

        // Allows to find resource streams not closed within GameNative and JavaSteam
        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build(),
            )

            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        traceStartupStep("NetworkMonitor.init") { NetworkMonitor.init(this) }

        // Init our custom crash handler.
        traceStartupStep("CrashHandler.initialize") { CrashHandler.initialize(this) }

        // Initialize GOGConstants
        traceStartupStep("GOGConstants.init") { app.gamenative.service.gog.GOGConstants.init(this) }

        traceStartupStep("DownloadService.populateDownloadService") { DownloadService.populateDownloadService(this) }

        traceStartupStep("migrateGogAmazonPaths") { migrateGogAmazonPaths() }

        appScope.launch {
            ContainerMigrator.migrateLegacyContainersIfNeeded(
                context = applicationContext,
                onProgressUpdate = null,
                onComplete = null
            )
        }

        // Preload all container files in the background
        appScope.launch {
            ContainerFilesDownloader.preloadAllContainerFiles(applicationContext)
        }

        // Clear any stale temporary config overrides from previous app sessions
        try {
            IntentLaunchManager.clearAllTemporaryOverrides()
            Timber.d("[PluviaApp]: Cleared temporary config overrides from previous session")
        } catch (e: Exception) {
            Timber.e(e, "[PluviaApp]: Failed to clear temporary config overrides")
        }

        // Initialize PostHog Analytics
        val postHogConfig = PostHogAndroidConfig(
            apiKey = BuildConfig.POSTHOG_API_KEY,
            host = BuildConfig.POSTHOG_HOST,
        ).apply {
            /* turn every event into an identified one */
            personProfiles = PersonProfiles.ALWAYS
        }
        traceStartupStep("PostHogAndroid.setup") {
            PostHogAndroid.setup(this, postHogConfig)
            com.posthog.PostHog.register("build_flavor", BuildConfig.FLAVOR)
        }

        val entryPoint = PreferencesEntryPoint.get(this)
        val generalPrefs = entryPoint.generalPreferences()
        val libraryPrefs = entryPoint.libraryPreferences()
        if (generalPrefs.usageAnalyticsEnabled) {
            com.posthog.PostHog.capture(
                event = "\$set",
                properties = mapOf(
                    "\$set" to mapOf("recommendation_enabled" to libraryPrefs.showRecommendations),
                ),
            )
        }

        traceStartupStep("PlayIntegrity.warmUp") { PlayIntegrity.warmUp(this) }

        traceStartupStep("PowerManager.initialize") { PowerManager.initialize(this) }

        Timber.i("[StartupInit] Total PluviaApp.onCreate took %d ms", SystemClock.elapsedRealtime() - appCreateStart)
    }

    /**
     * One-time migration: moves GOG/Amazon game directories from
     * {filesDir}/ to {dataDir}/ to match Steam/Epic, and updates DB paths.
     */
    private fun migrateGogAmazonPaths() {
        val libraryPrefs = PreferencesEntryPoint.get(this).libraryPreferences()
        if (libraryPrefs.gogAmazonPathMigrated) return

        val dataDir = dataDir.path
        val filesDir = filesDir.absolutePath
        Timber.i("[Migration] Migrating GOG/Amazon install paths from $filesDir to $dataDir")

        val migrations = listOf(
            File(filesDir, "GOG") to File(dataDir, "GOG"),
            File(filesDir, "Amazon") to File(dataDir, "Amazon"),
        )

        for ((oldDir, newDir) in migrations) {
            if (!oldDir.exists()) continue
            if (newDir.exists()) {
                Timber.w("[Migration] Target already exists, skipping rename: ${newDir.path}")
                continue
            }
            val renamed = oldDir.renameTo(newDir)
            if (renamed) {
                Timber.i("[Migration] Renamed ${oldDir.path} -> ${newDir.path}")
            } else {
                Timber.w("[Migration] Failed to rename ${oldDir.path} -> ${newDir.path}")
            }
        }

        val oldPrefix = "$filesDir/"
        val newPrefix = "$dataDir/"

        runBlocking(Dispatchers.IO) {
            try {
                val gogGames = gogGameDao.getAllAsList()
                for (game in gogGames) {
                    if (game.installPath.isNotEmpty() && game.installPath.contains(oldPrefix)) {
                        val updated = game.copy(installPath = game.installPath.replace(oldPrefix, newPrefix))
                        gogGameDao.update(updated)
                    }
                }
                Timber.i("[Migration] Updated ${gogGames.count { it.installPath.contains(oldPrefix) }} GOG install paths")
            } catch (e: Exception) {
                Timber.e(e, "[Migration] Failed to update GOG DB paths")
            }

            try {
                val amazonGames = amazonGameDao.getAllAsList()
                for (game in amazonGames) {
                    if (game.installPath.isNotEmpty() && game.installPath.contains(oldPrefix)) {
                        val newPath = game.installPath.replace(oldPrefix, newPrefix)
                        amazonGameDao.markAsInstalled(game.productId, newPath, game.installSize, game.versionId)
                    }
                }
                Timber.i("[Migration] Updated ${amazonGames.count { it.installPath.contains(oldPrefix) }} Amazon install paths")
            } catch (e: Exception) {
                Timber.e(e, "[Migration] Failed to update Amazon DB paths")
            }
        }

        libraryPrefs.gogAmazonPathMigrated = true
        Timber.i("[Migration] GOG/Amazon path migration complete")
    }

    companion object {
        @JvmField
        val events: EventDispatcher = EventDispatcher()
        internal var onDestinationChangedListener: NavChangedListener? = null

        lateinit var instance: PluviaApp

        private fun currentRuntime(createIfMissing: Boolean = false): GameSessionRuntime? {
            val ctx = if (::instance.isInitialized) instance.applicationContext else null
            val manager = (ctx as? Context)?.let {
                runCatching { it.appUtilsEntryPoint().gameSessionManager() }.getOrNull()
            } ?: return null
            return if (createIfMissing) {
                manager.currentRuntime ?: manager.getOrCreateRuntime()
            } else {
                manager.currentRuntime
            }
        }

        internal var xEnvironment: XEnvironment?
            get() = currentRuntime()?.xEnvironment
            set(value) {
                if (value != null) {
                    currentRuntime(createIfMissing = true)?.xEnvironment = value
                } else {
                    currentRuntime()?.xEnvironment = null
                }
            }

        internal var xServerView: XServerRendererView?
            get() = currentRuntime()?.xServerView
            set(value) {
                if (value != null) {
                    currentRuntime(createIfMissing = true)?.xServerView = value
                } else {
                    currentRuntime()?.xServerView = null
                }
            }

        var inputControlsView: InputControlsView?
            get() = currentRuntime()?.inputControlsView
            set(value) {
                if (value != null) {
                    currentRuntime(createIfMissing = true)?.inputControlsView = value
                } else {
                    currentRuntime()?.inputControlsView = null
                }
            }

        var inputControlsManager: InputControlsManager?
            get() = currentRuntime()?.inputControlsManager
            set(value) {
                if (value != null) {
                    currentRuntime(createIfMissing = true)?.inputControlsManager = value
                } else {
                    currentRuntime()?.inputControlsManager = null
                }
            }

        var touchpadView: TouchpadView?
            get() = currentRuntime()?.touchpadView
            set(value) {
                if (value != null) {
                    currentRuntime(createIfMissing = true)?.touchpadView = value
                } else {
                    currentRuntime()?.touchpadView = null
                }
            }

        var radialMenuCoordinator: RadialMenuCoordinator?
            get() = currentRuntime()?.radialMenuCoordinator
            set(value) {
                if (value != null) {
                    currentRuntime(createIfMissing = true)?.radialMenuCoordinator = value
                } else {
                    currentRuntime()?.radialMenuCoordinator = null
                }
            }

        var achievementWatcher: app.gamenative.service.AchievementWatcher?
            get() = currentRuntime()?.achievementWatcher
            set(value) {
                if (value != null) {
                    currentRuntime(createIfMissing = true)?.achievementWatcher = value
                } else {
                    currentRuntime()?.achievementWatcher = null
                }
            }

        var isOverlayPaused: Boolean
            get() = currentRuntime()?.isOverlayPaused ?: false
            set(value) {
                currentRuntime()?.isOverlayPaused = value
            }

        var isActivityInForeground: Boolean
            get() = currentRuntime()?.isActivityInForeground ?: true
            set(value) {
                currentRuntime()?.isActivityInForeground = value
            }

        val activeSuspendPolicy: String
            get() = currentRuntime()?.activeSuspendPolicy ?: Container.SUSPEND_POLICY_MANUAL

        fun setActiveSuspendPolicy(policy: String) {
            currentRuntime(createIfMissing = true)?.setActiveSuspendPolicy(policy)
        }

        fun shutdownEnvironment() {
            val ctx = if (::instance.isInitialized) instance.applicationContext else null
            val manager = (ctx as? Context)?.let {
                runCatching { it.appUtilsEntryPoint().gameSessionManager() }.getOrNull()
            }
            if (manager != null) {
                (manager as? DefaultGameSessionManager)?.endSessionSync() ?: manager.currentRuntime?.shutdownEnvironment()
            }
        }

        fun clearActiveSuspendState() {
            currentRuntime()?.clearActiveSuspendState()
        }

        fun hasValidSuspendPolicyState(): Boolean =
            currentRuntime()?.hasValidSuspendPolicyState() ?: false

        fun isNeverSuspendMode(): Boolean =
            currentRuntime()?.isNeverSuspendMode() ?: false

        fun isManualSuspendMode(): Boolean =
            currentRuntime()?.isManualSuspendMode() ?: false

        fun getDefaultScreenSize(): String {
            val ctx = if (::instance.isInitialized) instance.applicationContext else null
            return (ctx as? Context)?.let {
                runCatching { it.appUtilsEntryPoint().screenSizeResolver().getDefaultScreenSize() }.getOrNull()
            } ?: Container.DEFAULT_SCREEN_SIZE_16_9
        }
    }

    /**
     * Some native libraries we dlopen at runtime (libsteamclient.so via SteamBootstrap,
     * the lsfg-vk layer, etc.) depend on `libjpeg.so`, which isn't on every device's
     * dynamic linker search path. Pre-load the system copy here with RTLD_GLOBAL
     * semantics (System.load is global) so all subsequent dlopens find its symbols.
     *
     * Single place for all: runs once in Application.onCreate before any other
     * native lib is loaded by this process. Failures are non-fatal — devices that
     * don't have the file (or have it elsewhere) just fall through.
     */
    private fun preloadSystemLibraries() {
        val is64 = android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        val candidates = if (is64) {
            listOf("/system/lib64/libjpeg.so", "/system/lib/libjpeg.so")
        } else {
            listOf("/system/lib/libjpeg.so", "/system/lib64/libjpeg.so")
        }
        for (path in candidates) {
            if (!File(path).exists()) continue
            try {
                System.load(path)
                Timber.i("[PluviaApp]: Preloaded $path")
                return
            } catch (e: Throwable) {
                Timber.w(e, "[PluviaApp]: System.load($path) failed")
            }
        }
        Timber.w("[PluviaApp]: Could not preload system libjpeg.so (none of the candidate paths worked)")
    }
}
