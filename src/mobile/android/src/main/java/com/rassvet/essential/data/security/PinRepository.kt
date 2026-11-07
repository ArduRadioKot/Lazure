package com.rassvet.essential.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.pinStore by preferencesDataStore("lazure_security")

/** The PIN is never stored. Its verifier is keyed by a non-exportable Android Keystore key. */
class PinRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val signer: (String, ByteArray) -> ByteArray,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(context.applicationContext.pinStore, ::signWithKeystore)
    val enabled = store.data.map { it[VERIFIER] != null }
    // Null means a PIN created before length metadata was introduced.
    val digitCount = store.data.map { it[LENGTH]?.takeIf { count -> count == 4 || count == 6 } }

    suspend fun enable(pin: String) = withContext(Dispatchers.IO) {
        require(isValidPin(pin))
        mutex.withLock {
            check(store.data.first()[VERIFIER] == null)
            val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val verifier = signer(pin, salt)
            store.edit { it[SALT] = encode(salt); it[VERIFIER] = encode(verifier); it[LENGTH] = pin.length; it[FAILURES] = 0; it[UNTIL] = 0 }
        }
    }

    suspend fun verify(pin: String, disable: Boolean = false): Result = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = store.data.first()
            val expected = prefs[VERIFIER] ?: return@withLock Result.Success
            if ((prefs[UNTIL] ?: 0) > now()) return@withLock Result.Locked
            val matches = isValidPin(pin) && MessageDigest.isEqual(
                Base64.decode(expected, Base64.NO_WRAP), signer(pin, Base64.decode(prefs[SALT] ?: error("Missing PIN salt"), Base64.NO_WRAP)),
            )
            if (matches) {
                store.edit {
                    if (disable) { it.remove(VERIFIER); it.remove(SALT); it.remove(LENGTH) }
                    else it[LENGTH] = pin.length
                    it[FAILURES] = 0; it[UNTIL] = 0
                }
                Result.Success
            } else {
                val attempts = (prefs[FAILURES] ?: 0) + 1
                store.edit {
                    it[FAILURES] = if (attempts >= 5) 0 else attempts
                    it[UNTIL] = if (attempts >= 5) now() + 30_000 else 0
                }
                if (attempts >= 5) Result.Locked else Result.Incorrect
            }
        }
    }

    enum class Result { Success, Incorrect, Locked }
    companion object {
        private fun signWithKeystore(pin: String, salt: ByteArray): ByteArray {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).setDigests(KeyProperties.DIGEST_SHA256).build())
            }.generateKey()
            return Mac.getInstance("HmacSHA256").apply { init(key); update(salt) }.doFinal(pin.toByteArray(Charsets.US_ASCII))
        }

        fun isValidPin(pin: String) = (pin.length == 4 || pin.length == 6) && pin.all { it in '0'..'9' }
        private const val ALIAS = "lazure_pin_verifier_v1"
        private val mutex = Mutex()
        private val VERIFIER = stringPreferencesKey("verifier")
        private val SALT = stringPreferencesKey("salt")
        private val LENGTH = intPreferencesKey("digit_count")
        private val FAILURES = intPreferencesKey("failures")
        private val UNTIL = longPreferencesKey("retry_after")
        private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
