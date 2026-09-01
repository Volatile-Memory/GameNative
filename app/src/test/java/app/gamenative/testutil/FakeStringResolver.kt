package app.gamenative.testutil

import androidx.annotation.StringRes
import app.gamenative.core.appinfo.StringResolver

/**
 * In-memory [StringResolver] for unit testing without Android resource loading.
 */
class FakeStringResolver(
    private val stringMap: MutableMap<Int, String> = mutableMapOf(),
) : StringResolver {

    fun setString(@StringRes resId: Int, value: String) {
        stringMap[resId] = value
    }

    override fun getString(resId: Int): String {
        return stringMap[resId] ?: "StringRes_$resId"
    }

    override fun getString(resId: Int, vararg formatArgs: Any): String {
        val template = stringMap[resId] ?: "StringRes_$resId"
        return String.format(template, *formatArgs)
    }
}
