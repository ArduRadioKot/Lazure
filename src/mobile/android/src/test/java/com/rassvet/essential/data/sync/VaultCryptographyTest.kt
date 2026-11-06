package com.rassvet.essential.data.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VaultCryptographyTest {
    @Test
    fun encryptDecrypt_roundTrip() {
        val plain = "vault snapshot payload".toByteArray(Charsets.UTF_8)
        val encrypted = VaultCryptography.encrypt(plain, "test-passphrase")
        val decrypted = VaultCryptography.decrypt(encrypted, "test-passphrase")
        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun encrypt_producesDifferentCiphertextEachTime() {
        val plain = "same content".toByteArray(Charsets.UTF_8)
        val first = VaultCryptography.encrypt(plain, "pass")
        val second = VaultCryptography.encrypt(plain, "pass")
        assertNotEquals(first.contentHashCode(), second.contentHashCode())
    }

    @Test(expected = Exception::class)
    fun decrypt_wrongPassphraseFails() {
        val plain = "secret".toByteArray(Charsets.UTF_8)
        val encrypted = VaultCryptography.encrypt(plain, "correct")
        VaultCryptography.decrypt(encrypted, "wrong")
    }

    @Test(expected = IllegalArgumentException::class)
    fun decrypt_tooSmallBlobFails() {
        VaultCryptography.decrypt(ByteArray(8), "pass")
    }
}
