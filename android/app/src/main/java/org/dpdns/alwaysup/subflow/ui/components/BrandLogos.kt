package org.dpdns.alwaysup.subflow.ui.components

import androidx.annotation.DrawableRes
import org.dpdns.alwaysup.subflow.R

/**
 * Bundled brand marks for the built-in preset catalogue.
 *
 * These are bundled rather than fetched, and that is a privacy decision, not a
 * performance one. Loading a logo from the brand's own server — which is what
 * the preset catalogue's `icon_url` fields point at — would tell Netflix,
 * OpenAI and everyone else on this list which subscriptions the user tracks.
 * Spread across a dozen services that is the user's financial profile, and it
 * contradicts the promise that what they enter never leaves the device.
 *
 * The glyphs come from simple-icons: single-path, monochrome, drawn white on
 * the existing brand-coloured tile. They sit exactly where the initial letter
 * used to, so nothing about the badge's layout changes. Being vectors, they
 * stay crisp at any size — the free favicon services top out between 32 and
 * 96 pixels and vary per brand, which would have looked soft next to the text
 * beside them.
 *
 * A preset with no entry here keeps its initial-letter tile. That is a
 * supported outcome, not a gap to be filled.
 *
 * A mark is only used when it identifies the product itself. Google One and
 * Microsoft 365 briefly showed Google's "G" and the old Office icon — the
 * parent company and a sibling product. Both are recognisable and both are
 * wrong, which is worse than showing nothing, because a wrong mark still reads
 * as an answer. Their real logos are multi-colour and do not survive being
 * reduced to a white silhouette, so they keep the letter tile until the badge
 * can carry full-colour artwork. PlayStation Plus and Nintendo Switch Online do
 * use the platform mark, because their own logos are built from it.
 *
 * Disney+ is absent from simple-icons entirely. Its wordmark is a single
 * colour, so it is fitted from the published SVG instead — see
 * brand_disney.xml.
 */
object BrandLogos {

    private val byPresetId: Map<String, Int> = mapOf(
        "1password" to R.drawable.brand_1password,
        "adobe_cc" to R.drawable.brand_adobe_cc,
        "amazon_prime" to R.drawable.brand_amazon_prime,
        "applemusic" to R.drawable.brand_applemusic,
        "appletv" to R.drawable.brand_appletv,
        "backblaze" to R.drawable.brand_backblaze,
        "chatgpt" to R.drawable.brand_chatgpt,
        "claude" to R.drawable.brand_claude,
        "crunchyroll" to R.drawable.brand_crunchyroll,
        "disney" to R.drawable.brand_disney,
        "dropbox" to R.drawable.brand_dropbox,
        "duolingo" to R.drawable.brand_duolingo,
        "figma" to R.drawable.brand_figma,
        "github_copilot" to R.drawable.brand_github_copilot,
        "headspace" to R.drawable.brand_headspace,
        "hulu" to R.drawable.brand_hulu,
        "icloud" to R.drawable.brand_icloud,
        "max" to R.drawable.brand_max,
        "medium" to R.drawable.brand_medium,
        "netflix" to R.drawable.brand_netflix,
        "nintendo_online" to R.drawable.brand_nintendo_online,
        "nordvpn" to R.drawable.brand_nordvpn,
        "notion" to R.drawable.brand_notion,
        "nytimes" to R.drawable.brand_nytimes,
        "primevideo" to R.drawable.brand_primevideo,
        "psplus" to R.drawable.brand_psplus,
        "slack" to R.drawable.brand_slack,
        "spotify" to R.drawable.brand_spotify,
        "strava" to R.drawable.brand_strava,
        "xbox_gamepass" to R.drawable.brand_xbox_gamepass,
        "youtube" to R.drawable.brand_youtube,
    )

    /**
     * Names normalised for lookup, so a subscription saved before this existed
     * still finds its mark.
     *
     * Subscriptions store a name, not a preset id — the id is only used while
     * the add screen is open. Without this, every row already in someone's
     * vault would keep its letter tile forever, which is the majority of rows
     * on any existing install.
     */
    private val byNormalisedName: Map<String, Int> = byPresetId.entries
        .associate { (id, res) -> id.replace("_", "") to res } +
        mapOf(
            "youtubepremium" to R.drawable.brand_youtube,
            "disneyplus" to R.drawable.brand_disney,
            "hbomax" to R.drawable.brand_max,
            "appletvplus" to R.drawable.brand_appletv,
            "appletv" to R.drawable.brand_appletv,
            "chatgptplus" to R.drawable.brand_chatgpt,
            "claudepro" to R.drawable.brand_claude,
            "githubcopilot" to R.drawable.brand_github_copilot,
            "adobecreativecloud" to R.drawable.brand_adobe_cc,
            "icloudplus" to R.drawable.brand_icloud,
            "amazonprime" to R.drawable.brand_amazon_prime,
            "primevideo" to R.drawable.brand_primevideo,
            "playstationplus" to R.drawable.brand_psplus,
            "psplus" to R.drawable.brand_psplus,
            "xboxgamepass" to R.drawable.brand_xbox_gamepass,
            "nintendoswitchonline" to R.drawable.brand_nintendo_online,
            "duolingosuper" to R.drawable.brand_duolingo,
            "thenewyorktimes" to R.drawable.brand_nytimes,
            "newyorktimes" to R.drawable.brand_nytimes,
            "nytimes" to R.drawable.brand_nytimes,
            "applemusic" to R.drawable.brand_applemusic,
        )

    /** The mark for a preset id, or null when there is none. */
    @DrawableRes
    fun forPresetId(presetId: String?): Int? =
        presetId?.let { byPresetId[it.lowercase()] }

    /**
     * The mark for a subscription name, matched loosely.
     *
     * Case, spaces and punctuation are ignored so "Apple TV+", "apple tv plus"
     * and "AppleTV+" all land on the same glyph. A user who renames a row to
     * "Netflix (family)" still gets Netflix, because the lookup falls back to a
     * prefix match once the exact one misses.
     */
    @DrawableRes
    fun forName(name: String?): Int? {
        val key = normalise(name ?: return null)
        if (key.isEmpty()) return null
        byNormalisedName[key]?.let { return it }
        // Longest key first, so "applemusic" is preferred over "apple" if both
        // were ever present.
        return byNormalisedName.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { key.startsWith(it.key) }
            ?.value
    }

    private fun normalise(raw: String): String = raw
        .lowercase()
        .replace("+", "plus")
        .filter { it.isLetterOrDigit() }
}
