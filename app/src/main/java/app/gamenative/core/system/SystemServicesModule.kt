package app.gamenative.core.system

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ShortcutManager
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Vibrator
import android.os.storage.StorageManager
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SystemServicesModule {

    @Provides
    @Singleton
    fun provideConnectivityManager(
        @ApplicationContext context: Context,
    ): ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
    ): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Provides
    @Singleton
    fun providePowerManager(
        @ApplicationContext context: Context,
    ): PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    @Provides
    @Singleton
    fun provideDisplayManager(
        @ApplicationContext context: Context,
    ): DisplayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    @Provides
    @Singleton
    fun provideBatteryManager(
        @ApplicationContext context: Context,
    ): BatteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    @Provides
    @Singleton
    fun provideActivityManager(
        @ApplicationContext context: Context,
    ): ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    @Provides
    @Singleton
    fun provideVibrator(
        @ApplicationContext context: Context,
    ): Vibrator =
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    @Provides
    @Singleton
    fun provideWindowManager(
        @ApplicationContext context: Context,
    ): WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
    @Provides
    @Singleton
    fun provideInputManager(
        @ApplicationContext context: Context,
    ): InputManager =
        context.getSystemService(Context.INPUT_SERVICE) as InputManager
        
    @Provides
    @Singleton
    fun provideInputMethodManager(
        @ApplicationContext context: Context,
    ): InputMethodManager =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        
    @Provides
    @Singleton
    fun provideStorageManager(
        @ApplicationContext context: Context,
    ): StorageManager =
        context.getSystemService(StorageManager::class.java) as StorageManager
        
    @Provides
    @Singleton
    fun provideSensorManager(
        @ApplicationContext context: Context,
    ): SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
    @Provides
    @Singleton
    fun provideShortcutManager(
        @ApplicationContext context: Context,
    ): ShortcutManager =
        context.getSystemService(ShortcutManager::class.java) as ShortcutManager
}
