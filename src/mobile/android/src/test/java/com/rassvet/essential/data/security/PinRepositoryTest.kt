package com.rassvet.essential.data.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.File
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PinRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var repo: PinRepository
    private var clock = 1_000L
    private lateinit var store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private val signer: (String, ByteArray) -> ByteArray = { pin, salt ->
        Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(ByteArray(32) { 42 }, "HmacSHA256")); update(salt)
        }.doFinal(pin.toByteArray())
    }
    @Before fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "pin.preferences_pb") })
        repo = PinRepository(store, signer) { clock }
    }
    @After fun cleanup() { scope.cancel() }

    @Test fun onlyFourOrSixAsciiDigitsAreAccepted() {
        assertTrue(PinRepository.isValidPin("0000")); assertTrue(PinRepository.isValidPin("123456"))
        listOf("", "123", "12345", "1234567", "12a4", "１２３４", " 1234").forEach { assertFalse(it, PinRepository.isValidPin(it)) }
    }
    @Test fun bothLengthsVerifyAndPersistWithoutPlaintext() = runTest {
        for (pin in listOf("1234", "012345")) {
            repo.enable(pin)
            assertTrue(repo.enabled.first())
            assertEquals(pin.length, PinRepository(store, signer) { clock }.digitCount.first())
            assertEquals(PinRepository.Result.Success, PinRepository(store, signer) { clock }.verify(pin))
            assertFalse(store.data.first().asMap().values.contains(pin))
            assertEquals(PinRepository.Result.Success, repo.verify(pin, disable = true))
            assertFalse(repo.enabled.first())
            assertNull(repo.digitCount.first())
        }
    }
    @Test fun incorrectCodeCannotDisableLock() = runTest {
        repo.enable("1234")
        assertEquals(PinRepository.Result.Incorrect, repo.verify("123456", disable = true))
        assertTrue(repo.enabled.first())
        assertEquals(PinRepository.Result.Success, repo.verify("1234"))
    }
    @Test fun fiveFailuresPersistCooldownAcrossRepositoryRecreation() = runTest {
        repo.enable("123456")
        repeat(4) { assertEquals(PinRepository.Result.Incorrect, repo.verify("000000")) }
        assertEquals(PinRepository.Result.Locked, repo.verify("000000"))
        val reopened = PinRepository(store, signer) { clock }
        assertEquals(PinRepository.Result.Locked, reopened.verify("123456"))
        clock += 30_001
        assertEquals(PinRepository.Result.Success, reopened.verify("123456"))
    }
    @Test fun successfulUnlockResetsFailureCount() = runTest {
        repo.enable("1234")
        repeat(4) { repo.verify("0000") }
        assertEquals(PinRepository.Result.Success, repo.verify("1234"))
        assertEquals(PinRepository.Result.Incorrect, repo.verify("0000"))
    }
    @Test fun enabledPinCannotBeOverwrittenWithoutDisabling() = runTest {
        repo.enable("1234")
        try { repo.enable("9999"); fail("Replacing enabled PIN must fail") } catch (_: IllegalStateException) { }
        assertEquals(PinRepository.Result.Success, repo.verify("1234"))
    }
    @Test fun legacyPinLengthMigratesOnlyAfterSuccessfulVerification() = runTest {
        for (pin in listOf("1234", "123456")) {
            repo.enable(pin)
            store.edit { it.remove(intPreferencesKey("digit_count")) }
            val reopened = PinRepository(store, signer) { clock }
            assertNull(reopened.digitCount.first())
            assertEquals(PinRepository.Result.Incorrect, reopened.verify("9999"))
            assertNull(reopened.digitCount.first())
            assertEquals(PinRepository.Result.Success, reopened.verify(pin))
            assertEquals(pin.length, reopened.digitCount.first())
            reopened.verify(pin, disable = true)
        }
    }

}
