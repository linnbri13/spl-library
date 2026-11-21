package com.linnbri13.spllibrary.data

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("spl_library_prefs", Context.MODE_PRIVATE)

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value.trim()).apply()

    var pin: String
        get() = prefs.getString(KEY_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PIN, value.trim()).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var sessionId: String?
        get() = prefs.getString(KEY_SESSION_ID, null)
        set(value) = prefs.edit().putString(KEY_SESSION_ID, value).apply()

    var accountId: Long
        get() = prefs.getLong(KEY_ACCOUNT_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_ACCOUNT_ID, value).apply()

    var defaultBranchCode: String?
        get() = prefs.getString(KEY_BRANCH_CODE, null)
        set(value) = prefs.edit().putString(KEY_BRANCH_CODE, value).apply()

    var defaultBranchName: String?
        get() = prefs.getString(KEY_BRANCH_NAME, null)
        set(value) = prefs.edit().putString(KEY_BRANCH_NAME, value).apply()

    val hasCredentials: Boolean
        get() = username.isNotBlank() && pin.isNotBlank()

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank() && !sessionId.isNullOrBlank() && accountId > 0L

    fun saveSession(token: String, sid: String, accId: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putString(KEY_SESSION_ID, sid)
            .putLong(KEY_ACCOUNT_ID, accId)
            .apply()
    }

    fun saveDefaultBranch(code: String, name: String) {
        prefs.edit()
            .putString(KEY_BRANCH_CODE, code)
            .putString(KEY_BRANCH_NAME, name)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PIN)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_SESSION_ID)
            .remove(KEY_ACCOUNT_ID)
            .apply()
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PIN = "pin"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_BRANCH_CODE = "default_branch_code"
        private const val KEY_BRANCH_NAME = "default_branch_name"
    }
}
