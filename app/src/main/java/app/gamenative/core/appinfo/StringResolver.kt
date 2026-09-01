package app.gamenative.core.appinfo

import androidx.annotation.StringRes

/**
 * Pure abstraction for Android string resource resolution.
 * Allows components and ViewModels to resolve localized strings without holding an Android Context.
 */
interface StringResolver {
    fun getString(@StringRes resId: Int): String
    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String
}
