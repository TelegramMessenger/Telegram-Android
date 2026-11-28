package dev.moonbag.app.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A saved recipient. */
data class Contact(val name: String, val address: String, val chainId: String)

/** Contacts as JSON, kept apart from Android so it can be tested. */
object ContactsJson {
    fun toJson(list: List<Contact>): String {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("address", it.address).put("chain", it.chainId)) }
        return a.toString()
    }

    fun fromJson(text: String): List<Contact> = try {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val chain = Chains.get(o.optString("chain")) ?: return@mapNotNull null
            val address = o.optString("address")
            val name = o.optString("name").trim()
            if (name.isEmpty() || !Wallet.isAddress(chain, address)) null else Contact(name, Wallet.canonical(address), chain.id)
        }
    } catch (_: org.json.JSONException) {
        emptyList()
    }
}

/** The player's wallets as JSON, kept apart from Android so it can be tested. */
object WalletsJson {
    fun toJson(list: List<WalletEntry>): String {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("words", it.words.joinToString(" ")).put("backedUp", it.backedUp))
        }
        return a.toString()
    }

    fun fromJson(text: String): List<WalletEntry> = try {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val words = Wallet.normalize(o.optString("words"))
            val id = o.optString("id")
            if (id.isEmpty() || !Wallet.isValid(words)) null
            else WalletEntry(id, o.optString("name").ifBlank { "Wallet" }, o.optLong("color", WalletEntry.COLORS[0]), words, o.optBoolean("backedUp"))
        }.distinctBy { it.id }
    } catch (_: org.json.JSONException) {
        emptyList()
    }
}

/**
 * Keeps the game wallets (their twelve words, names and colours), which one is active and the contacts. The words
 * stay in this app's private storage. They are a game feature: they control no real money.
 */
class WalletStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("moonbag_wallet", Context.MODE_PRIVATE)

    /** All wallets. A save from the single-wallet version is read as "Wallet 1". */
    var wallets: List<WalletEntry>
        get() {
            val saved = WalletsJson.fromJson(prefs.getString("wallets", "[]") ?: "[]")
            if (saved.isNotEmpty()) return saved
            val old = Wallet.normalize(prefs.getString("words", "") ?: "").takeIf { Wallet.isValid(it) } ?: return emptyList()
            return listOf(WalletEntry("w1", "Wallet 1", WalletEntry.COLORS[0], old, prefs.getBoolean("backed_up", false)))
        }
        set(v) = prefs.edit().putString("wallets", WalletsJson.toJson(v)).putString("words", "").apply()

    var activeId: String
        get() = prefs.getString("active", "") ?: ""
        set(v) = prefs.edit().putString("active", v).apply()

    var contacts: List<Contact>
        get() = ContactsJson.fromJson(prefs.getString("contacts", "[]") ?: "[]")
        set(v) = prefs.edit().putString("contacts", ContactsJson.toJson(v)).apply()
}
