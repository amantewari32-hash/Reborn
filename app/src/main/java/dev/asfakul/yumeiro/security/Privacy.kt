
/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.security

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Base64
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Privacy switches: incognito and a separate six-digit app PIN.
 * The PIN itself is never stored; only a salted PBKDF2 hash is persisted.
 */
object Privacy {
    private const val PREFS = "privacy"
    private const val PIN_LENGTH = 6
    private const val HASH_ITERATIONS = 150_000
    private const val HASH_BITS = 256

    private val _incognito = MutableStateFlow(false)
    val incognito: StateFlow<Boolean> = _incognito.asStateFlow()

    private val _lockEnabled = MutableStateFlow(false)
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    private val _lockAfter = MutableStateFlow(60)
    val lockAfter: StateFlow<Int> = _lockAfter.asStateFlow()

    private val _hideInRecents = MutableStateFlow(false)
    val hideInRecents: StateFlow<Boolean> = _hideInRecents.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile private var backgroundAt = 0L
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true

        val p = prefs(context)
        _incognito.value = p.getBoolean("incognito", false)
        _lockEnabled.value = p.getBoolean("lock", false)
        _lockAfter.value = p.getInt("lock_after", 60)
        _hideInRecents.value = p.getBoolean("hide_recents", false)

        // If an older installation had device authentication enabled, keep the lock active.
        // AppLockGate will require creation of a new app PIN before allowing access.
        _locked.value = _lockEnabled.value
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setIncognito(context: Context, on: Boolean) {
        _incognito.value = on
        prefs(context).edit().putBoolean("incognito", on).apply()
        dev.asfakul.yumeiro.widget.ContinueWidget.refresh(context)
    }

    fun setLockAfter(context: Context, seconds: Int) {
        _lockAfter.value = seconds
        prefs(context).edit().putInt("lock_after", seconds).apply()
    }

    fun hasPin(context: Context): Boolean {
        val p = prefs(context)
        return p.contains("pin_salt") && p.contains("pin_hash")
    }

    /**
     * Creates the first PIN, or changes an existing PIN after the current PIN is verified.
     * A six-digit PIN is required. No plaintext PIN is written to preferences.
     */
    fun setPin(context: Context, pin: String, currentPin: String? = null): Boolean {
        if (pin.length != PIN_LENGTH || pin.any { !it.isDigit() }) return false

        if (hasPin(context)) {
            if (currentPin == null || !verifyPinWithoutUnlock(context, currentPin)) {
                return false
            }
        }

        return runCatching {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val hash = derive(pin, salt)

            prefs(context).edit()
                .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                .putBoolean("lock", true)
                .apply()

            _lockEnabled.value = true
            _locked.value = false
            backgroundAt = SystemClock.elapsedRealtime()

            (context as? Activity)?.let(::applyWindow)
            dev.asfakul.yumeiro.widget.ContinueWidget.refresh(context)
            true
        }.getOrDefault(false)
    }

    /** Checks the PIN and unlocks the app only when the stored hash matches. */
    fun verifyPin(context: Context, pin: String): Boolean {
        if (!verifyPinWithoutUnlock(context, pin)) return false
        _locked.value = false
        return true
    }

    /** Disabling the lock requires the existing PIN. */
    fun disableLock(context: Context, currentPin: String): Boolean {
        if (!verifyPinWithoutUnlock(context, currentPin)) return false

        prefs(context).edit()
            .remove("pin_salt")
            .remove("pin_hash")
            .putBoolean("lock", false)
            .apply()

        _lockEnabled.value = false
        _locked.value = false
        (context as? Activity)?.let(::applyWindow)
        dev.asfakul.yumeiro.widget.ContinueWidget.refresh(context)
        return true
    }

    private fun verifyPinWithoutUnlock(context: Context, pin: String): Boolean {
        if (pin.length != PIN_LENGTH || pin.any { !it.isDigit() }) return false

        return runCatching {
            val p = prefs(context)
            val saltText = p.getString("pin_salt", null) ?: return false
            val hashText = p.getString("pin_hash", null) ?: return false
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            val actual = derive(pin, salt)
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            salt,
            HASH_ITERATIONS,
            HASH_BITS,
        )

        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun setHideInRecents(activity: Activity, on: Boolean) {
        _hideInRecents.value = on
        prefs(activity).edit().putBoolean("hide_recents", on).apply()
        applyWindow(activity)
    }

    fun onStop() {
        if (_lockEnabled.value) {
            backgroundAt = SystemClock.elapsedRealtime()
        }
    }

    fun onStart(activity: Activity) {
        load(activity)
        applyWindow(activity)

        if (!_lockEnabled.value) {
            _locked.value = false
            return
        }

        val away = if (backgroundAt == 0L) {
            Long.MAX_VALUE
        } else {
            SystemClock.elapsedRealtime() - backgroundAt
        }

        if (away >= _lockAfter.value * 1000L) {
            _locked.value = true
        }
    }

    private fun applyWindow(activity: Activity) {
        val secure = _hideInRecents.value && _lockEnabled.value

        if (secure) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
