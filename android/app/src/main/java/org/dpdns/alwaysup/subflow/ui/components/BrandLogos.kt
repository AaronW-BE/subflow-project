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
        "figma" to R.drawable.brand_figma,
        "github_copilot" to R.drawable.brand_github_copilot,
        "gym" to R.drawable.brand_gym,
        "headspace" to R.drawable.brand_headspace,
        "hulu" to R.drawable.brand_hulu,
        "icloud" to R.drawable.brand_icloud,
        "nintendo_online" to R.drawable.brand_nintendo_online,
        "nordvpn" to R.drawable.brand_nordvpn,
        "notion" to R.drawable.brand_notion,
        "nytimes" to R.drawable.brand_nytimes,
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
            "gymmembership" to R.drawable.brand_gym,
            "disneyplus" to R.drawable.brand_disney,
            "appletvplus" to R.drawable.brand_appletv,
            "appletv" to R.drawable.brand_appletv,
            "chatgptplus" to R.drawable.brand_chatgpt,
            "claudepro" to R.drawable.brand_claude,
            "githubcopilot" to R.drawable.brand_github_copilot,
            "adobecreativecloud" to R.drawable.brand_adobe_cc,
            "icloudplus" to R.drawable.brand_icloud,
            "amazonprime" to R.drawable.brand_amazon_prime,
                "playstationplus" to R.drawable.brand_psplus,
            "psplus" to R.drawable.brand_psplus,
            "xboxgamepass" to R.drawable.brand_xbox_gamepass,
            "nintendoswitchonline" to R.drawable.brand_nintendo_online,
            "thenewyorktimes" to R.drawable.brand_nytimes,
            "newyorktimes" to R.drawable.brand_nytimes,
            "nytimes" to R.drawable.brand_nytimes,
            "applemusic" to R.drawable.brand_applemusic,
        )

    /**
     * Marks simple-icons draws as a whole app icon — the rounded container
     * *and* the symbol as one filled shape — rather than as a bare glyph.
     *
     * Painted white on a coloured tile these invert: the container becomes a
     * white square and the symbol is knocked out of it in the brand colour,
     * the opposite of the real icon. The badge paints them the other way round.
     *
     * Apple Music is the only one. An earlier version listed 1Password,
     * Duolingo, Medium and Nintendo Switch Online too, which was wrong — those
     * read correctly as plain white glyphs, and inverting them broke four
     * working icons to fix one. They were misjudged from a rendering that was
     * itself corrupted by the arc-flag parsing bug; see tools/normalise_arcs.py.
     *
     * A list rather than a heuristic on purpose. "Fills most of its viewBox"
     * also matches legitimate full-bleed glyphs like the Netflix N.
     */
    private val containerMarks = setOf("applemusic")

    /**
     * Whether [presetId] or [name] resolves to a mark that must be inverted.
     */
    fun isContainerMark(presetId: String?, name: String?): Boolean {
        presetId?.lowercase()?.let { if (it in containerMarks) return true }
        val key = normalise(name ?: return false)
        return containerMarks.any { key.startsWith(it.replace("_", "")) }
    }

    /**
     * Full-colour marks, for logos that cannot survive being a white glyph.
     *
     * Google One's logo is a four-colour ribbon; reduced to a silhouette it is
     * an unreadable blob, and the "G" it fell back to is Google's corporate
     * mark rather than this product's. This is the official app icon, bundled
     * for the same privacy reason as every other mark here.
     *
     * Vector where the artwork allows it, a bitmap only where it does not.
     * Duolingo is the vector case and is worth reading as the argument for
     * preferring one: both app stores were serving a seasonal novelty icon, so
     * scraping either would have frozen a promotion into the app. A vector of
     * the standard mark cannot go stale that way, stays crisp at any size, and
     * costs 4.6 KB.
     *
     * Deliberately a short list either way. These carry the brand's own artwork
     * rather than simple-icons' redrawing of it.
     *
     * Microsoft 365's squares were nearly missed: the published SVG holds them
     * as <rect> elements, so a path-only look at the file finds nothing but the
     * grey wordmark and concludes there is no symbol in it. Medium still keeps
     * a letter tile, because a white "M" on black already is its icon.
     */
    private val colourMarks: Map<String, Int> = mapOf(
        "duolingo" to R.drawable.brand_colour_duolingo,
        "google_one" to R.drawable.brand_colour_google_one,
        "max" to R.drawable.brand_colour_max,
        "microsoft365" to R.drawable.brand_colour_microsoft365,
        "netflix" to R.drawable.brand_colour_netflix,
        "primevideo" to R.drawable.brand_colour_primevideo,
    )

    /** A full-colour bitmap mark, drawn edge to edge with no tint. */
    @DrawableRes
    /**
     * Names that resolve to a full-colour mark whose preset id does not spell
     * them.
     *
     * Subscriptions store a name, not an id, so a row saved as "HBO Max" has to
     * find the mark filed under the preset id "max" — and it will not, because
     * "hbomax" does not start with "max". Getting this wrong is silent: the row
     * simply keeps a letter tile forever.
     */
    private val colourMarksByName: Map<String, Int> = mapOf(
        "hbomax" to R.drawable.brand_colour_max,
        "max" to R.drawable.brand_colour_max,
        "amazonprimevideo" to R.drawable.brand_colour_primevideo,
        "primevideo" to R.drawable.brand_colour_primevideo,
        "duolingo" to R.drawable.brand_colour_duolingo,
        "duolingosuper" to R.drawable.brand_colour_duolingo,
        "googleone" to R.drawable.brand_colour_google_one,
        "microsoft365" to R.drawable.brand_colour_microsoft365,
        "office365" to R.drawable.brand_colour_microsoft365,
        "netflix" to R.drawable.brand_colour_netflix,
    )

    fun colourMarkFor(presetId: String?, name: String?): Int? {
        presetId?.lowercase()?.let { colourMarks[it]?.let { res -> return res } }
        val key = normalise(name ?: return null)
        if (key.isEmpty()) return null
        colourMarksByName[key]?.let { return it }
        // Longest alias first, so "primevideo" is preferred over "max" for a
        // name that happens to contain both.
        return colourMarksByName.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { key.startsWith(it.key) }
            ?.value
    }

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
