package app.storyscout.android.data.local

import android.content.Context

data class StoredSession(val accessToken: String, val displayName: String, val expiresAtMillis: Long)

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("temporary_access_session", Context.MODE_PRIVATE)

    fun save(session: StoredSession) = preferences.edit()
        .putString("token", session.accessToken)
        .putString("name", session.displayName)
        .putLong("expires", session.expiresAtMillis)
        .apply()

    fun current(nowMillis: Long = System.currentTimeMillis()): StoredSession? {
        val token = preferences.getString("token", null) ?: return null
        val name = preferences.getString("name", null) ?: return null
        val expires = preferences.getLong("expires", 0)
        return if (expires > nowMillis) StoredSession(token, name, expires) else null
    }

    fun clear() = preferences.edit().clear().apply()
}
