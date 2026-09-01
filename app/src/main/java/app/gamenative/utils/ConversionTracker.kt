package app.gamenative.utils

import app.gamenative.preferences.GeneralPreferences
import com.posthog.PostHog
import java.util.UUID

object ConversionTracker {

    @Volatile
    var preferences: GeneralPreferences? = null

    fun featuredConversion(
        campaignId: String,
        actionType: String,
        appId: Int?,
        source: String,
        prefs: GeneralPreferences? = preferences,
    ) {
        val properties = mutableMapOf<String, Any>(
            "campaign_id" to campaignId,
            "action_type" to actionType,
            "source" to source,
        )
        appId?.let { properties["app_id"] = it }

        val isUsageAnalyticsEnabled = (prefs ?: preferences)?.usageAnalyticsEnabled ?: false
        if (isUsageAnalyticsEnabled) {
            PostHog.capture(event = "featured_conversion", properties = properties)
        } else {
            properties["\$process_person_profile"] = false
            PostHog.capture(
                event = "featured_conversion",
                distinctId = UUID.randomUUID().toString(),
                properties = properties,
            )
        }
    }
}
