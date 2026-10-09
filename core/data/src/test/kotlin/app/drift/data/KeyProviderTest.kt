package app.drift.data

import app.drift.data.crypto.AesGcmWrapper
import app.drift.data.crypto.BlobStore
import app.drift.data.crypto.WrappedPassphraseProvider
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import javax.crypto.KeyGenerator

class KeyProviderTest {
    private class MemoryStore : BlobStore {
        var blob: ByteArray? = null
        override fun read() = blob
        override fun write(bytes: ByteArray) { blob = bytes }
    }

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun generatesOnceAndReturnsSamePassphrase() {
        val store = MemoryStore()
        val first = WrappedPassphraseProvider(store, AesGcmWrapper { key }).passphrase()
        val second = WrappedPassphraseProvider(store, AesGcmWrapper { key }).passphrase()
        assertThat(first).hasLength(32)
        assertThat(second).isEqualTo(first)
    }

    @Test fun storedBlobDoesNotContainPassphrase() {
        val store = MemoryStore()
        val pass = WrappedPassphraseProvider(store, AesGcmWrapper { key }).passphrase()
        assertThat(String(store.blob!!, Charsets.ISO_8859_1)).doesNotContain(String(pass, Charsets.ISO_8859_1))
        assertThat(store.blob!!.size).isGreaterThan(pass.size)
    }

    @Test fun differentInstallsGetDifferentPassphrases() {
        val a = WrappedPassphraseProvider(MemoryStore(), AesGcmWrapper { key }, SecureRandom()).passphrase()
        val b = WrappedPassphraseProvider(MemoryStore(), AesGcmWrapper { key }, SecureRandom()).passphrase()
        assertThat(a).isNotEqualTo(b)
    }

    @Test fun wrongKeyCannotUnwrap() {
        val store = MemoryStore()
        WrappedPassphraseProvider(store, AesGcmWrapper { key }).passphrase()
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val result = runCatching { WrappedPassphraseProvider(store, AesGcmWrapper { other }).passphrase() }
        assertThat(result.isFailure).isTrue()
    }
}
