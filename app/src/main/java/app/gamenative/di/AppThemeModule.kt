package app.gamenative.di

import app.gamenative.enums.AppTheme
import app.gamenative.preferences.GeneralPreferences
import com.materialkolor.PaletteStyle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Referenced from https://github.com/fvilarino/App-Theme-Compose-Sample
 */

interface IAppTheme {
    val themeFlow: StateFlow<AppTheme>
    var currentTheme: AppTheme
    val paletteFlow: StateFlow<PaletteStyle>
    var currentPalette: PaletteStyle
}

class AppThemeImpl(
    private val generalPreferences: GeneralPreferences,
) : IAppTheme {

    override val themeFlow: MutableStateFlow<AppTheme> = MutableStateFlow(generalPreferences.appTheme)

    override var currentTheme: AppTheme by AppThemeDelegate()

    override val paletteFlow: MutableStateFlow<PaletteStyle> = MutableStateFlow(generalPreferences.appThemePalette)

    override var currentPalette: PaletteStyle by AppPaletteDelegate()

    inner class AppThemeDelegate : ReadWriteProperty<Any, AppTheme> {

        override fun getValue(thisRef: Any, property: KProperty<*>): AppTheme = generalPreferences.appTheme

        override fun setValue(thisRef: Any, property: KProperty<*>, value: AppTheme) {
            themeFlow.value = value
            generalPreferences.appTheme = value
        }
    }

    inner class AppPaletteDelegate : ReadWriteProperty<Any, PaletteStyle> {

        override fun getValue(thisRef: Any, property: KProperty<*>): PaletteStyle = generalPreferences.appThemePalette

        override fun setValue(thisRef: Any, property: KProperty<*>, value: PaletteStyle) {
            paletteFlow.value = value
            generalPreferences.appThemePalette = value
        }
    }
}

@InstallIn(SingletonComponent::class)
@Module
class AppThemeModule {
    @Provides
    @Singleton
    fun provideAppTheme(generalPreferences: GeneralPreferences): IAppTheme = AppThemeImpl(generalPreferences)
}
