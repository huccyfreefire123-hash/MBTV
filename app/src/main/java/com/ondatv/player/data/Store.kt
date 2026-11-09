package com.ondatv.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** First PIN for the adult channels, until the user changes it in Controle Parental. */
private const val DEFAULT_PARENTAL_PIN = "1234"

/** Small local storage: sources, favourites and watch progress. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("ondatv", Context.MODE_PRIVATE)

    fun sources(): List<Source> {
        val arr = JSONArray(prefs.getString("sources", "[]"))
        val out = ArrayList<Source>()
        arr.forEachObject { o ->
            out += Source(
                id = o.optString("id"),
                name = o.optString("name"),
                type = runCatching { SourceType.valueOf(o.optString("type")) }.getOrDefault(SourceType.M3U),
                url = o.optString("url"),
                epgUrl = o.optString("epgUrl"),
                server = o.optString("server"),
                username = o.optString("username"),
                password = o.optString("password"),
                expiresAt = o.optLong("expiresAt", 0L).takeIf { it > 0 },
            )
        }
        return out
    }

    fun saveSources(list: List<Source>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id).put("name", s.name).put("type", s.type.name)
                    .put("url", s.url).put("epgUrl", s.epgUrl)
                    .put("server", s.server).put("username", s.username).put("password", s.password)
                    .apply { s.expiresAt?.let { put("expiresAt", it) } }
            )
        }
        prefs.edit().putString("sources", arr.toString()).apply()
    }

    // ---------- Parental control ----------
    // The PIN is never stored: only a salted PBKDF2 hash of it.

    fun parentalEnabled(): Boolean = prefs.getBoolean("parental_on", true)

    fun setParentalEnabled(on: Boolean) {
        prefs.edit().putBoolean("parental_on", on).apply()
    }

    fun checkParentalPin(pin: String): Boolean {
        ensureParentalPin()
        val salt = prefs.getString("parental_salt", null) ?: return false
        return hashPin(pin, salt) == prefs.getString("parental_hash", null)
    }

    /** Saves a PIN the user chose (create or change). */
    fun setParentalPin(pin: String) {
        writePin(pin)
        prefs.edit().putBoolean("parental_created", true).apply()
    }

    /** Whether the user created a PIN; until then the app asks to create one instead of asking for it. */
    fun parentalPinCreated(): Boolean = prefs.getBoolean("parental_created", false)

    private fun writePin(pin: String) {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = Base64.encodeToString(bytes, Base64.NO_WRAP)
        prefs.edit().putString("parental_salt", salt).putString("parental_hash", hashPin(pin, salt)).apply()
    }

    private fun ensureParentalPin() {
        if (!prefs.contains("parental_hash")) writePin(DEFAULT_PARENTAL_PIN)
    }

    private fun hashPin(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), 20_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    /**
     * Removes everything that belongs to one list (favourites, progress, recently watched, in
     * every profile), so a list added later starts clean.
     */
    fun purgeSourceData(sourceId: String) {
        val prefix = "$sourceId:"
        val edit = prefs.edit()
        prefs.all.forEach { (key, value) ->
            when {
                key.startsWith("favorites") && value is Set<*> ->
                    edit.putStringSet(key, value.filterIsInstance<String>().filterNot { it.startsWith(prefix) }.toSet())
                key.startsWith("recentLive") && value is String ->
                    edit.putString(key, value.split('\n').filter { it.isNotEmpty() && !it.startsWith(prefix) }.joinToString("\n"))
                key.startsWith("progress") && value is String -> {
                    val obj = JSONObject(value)
                    obj.keys().asSequence().toList().filter { it.startsWith(prefix) }.forEach { obj.remove(it) }
                    edit.putString(key, obj.toString())
                }
            }
        }
        edit.apply()
    }

    // ---------- Profiles ----------
    // The first profile keeps the original key names, so data saved before profiles existed stays.

    private fun k(name: String): String {
        val p = currentProfileId()
        return if (p == DEFAULT_PROFILE) name else "${name}_$p"
    }

    fun currentProfileId(): String = prefs.getString("currentProfile", DEFAULT_PROFILE)!!

    fun setCurrentProfile(id: String) {
        prefs.edit().putString("currentProfile", id).apply()
    }

    fun profiles(): List<Profile> {
        val arr = JSONArray(prefs.getString("profiles", "[]"))
        val out = ArrayList<Profile>()
        arr.forEachObject { o ->
            out += Profile(o.optString("id"), o.optString("name"), o.optInt("color"), o.optString("pin").ifEmpty { null }, o.optInt("avatar", 0))
        }
        return out.ifEmpty { listOf(Profile(DEFAULT_PROFILE, "Eu", 0, null)) }
    }

    fun saveProfiles(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("color", p.color).put("pin", p.pin ?: "").put("avatar", p.avatar))
        }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }

    /**
     * Removes every profile and everything they saved, leaving only the default profile, empty.
     * The parental PIN stays.
     */
    fun resetProfiles() {
        val perProfile = listOf("favorites", "recentLive", "hidden", "progress", "teams")
        val edit = prefs.edit().remove("profiles").remove("currentProfile")
        prefs.all.keys.filter { key -> perProfile.any { key == it || key.startsWith(it + "_") } }.forEach { edit.remove(it) }
        edit.apply()
    }

    /** Removes everything a profile saved (not its entry in the list). */
    fun clearProfileData(id: String) {
        if (id == DEFAULT_PROFILE) return
        prefs.edit().apply {
            listOf("favorites", "recentLive", "hidden", "progress", "teams").forEach { remove("${it}_$id") }
        }.apply()
    }

    fun favorites(): Set<String> = prefs.getStringSet(k("favorites"), emptySet())!!.toSet()

    fun saveFavorites(set: Set<String>) {
        prefs.edit().putStringSet(k("favorites"), set).apply()
    }

    /** Live channels watched, most recent first. */
    fun recentLive(): List<String> =
        prefs.getString(k("recentLive"), "")!!.split('\n').filter { it.isNotEmpty() }

    fun addRecentLive(key: String) {
        val list = (listOf(key) + recentLive().filter { it != key }).take(20)
        prefs.edit().putString(k("recentLive"), list.joinToString("\n")).apply()
    }

    /** Teams this profile follows in Esportes. */
    fun teams(): Set<String> = prefs.getStringSet(k("teams"), emptySet())!!.toSet()

    fun saveTeams(set: Set<String>) {
        prefs.edit().putStringSet(k("teams"), set).apply()
    }

    /** Rows the user turned off on the Home screen. */
    fun hiddenSections(): Set<String> = prefs.getStringSet(k("hidden"), emptySet())!!.toSet()

    fun saveHiddenSections(set: Set<String>) {
        prefs.edit().putStringSet(k("hidden"), set).apply()
    }

    fun progress(): Map<String, Progress> {
        val obj = JSONObject(prefs.getString(k("progress"), "{}")!!)
        val out = HashMap<String, Progress>()
        obj.keys().forEach { k ->
            val o = obj.getJSONObject(k)
            out[k] = Progress(
                key = k,
                title = o.optString("title"),
                logo = o.optString("logo").ifEmpty { null },
                url = o.optString("url"),
                position = o.optLong("position"),
                duration = o.optLong("duration"),
                updated = o.optLong("updated"),
            )
        }
        return out
    }

    fun saveProgress(p: Progress) {
        val obj = JSONObject(prefs.getString(k("progress"), "{}")!!)
        obj.put(
            p.key,
            JSONObject()
                .put("title", p.title).put("logo", p.logo ?: "").put("url", p.url)
                .put("position", p.position).put("duration", p.duration).put("updated", p.updated)
        )
        // Keep the store small: only the most recent entries.
        if (obj.length() > MAX_PROGRESS) {
            val old = obj.keys().asSequence().toList()
                .sortedBy { obj.getJSONObject(it).optLong("updated") }
                .take(obj.length() - MAX_PROGRESS)
            old.forEach { obj.remove(it) }
        }
        prefs.edit().putString(k("progress"), obj.toString()).apply()
    }
}

private const val MAX_PROGRESS = 300
