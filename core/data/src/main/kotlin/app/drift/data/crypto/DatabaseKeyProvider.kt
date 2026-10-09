package app.drift.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Supplies the SQLCipher passphrase. The seam that lets tests run against a plain database. */
interface DatabaseKeyProvider {
    fun passphrase(): ByteArray
}

/** AES-GCM encryption with a key that never leaves its holder (Android Keystore in production). */
interface KeyWrapper {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/** AES-GCM over any [SecretKey]; blob layout is 12-byte IV followed by ciphertext+tag. */
class AesGcmWrapper(private val keySource: () -> SecretKey) : KeyWrapper {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, keySource())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "Encrypted blob too short" }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, keySource(), GCMParameterSpec(128, blob, 0, IV_BYTES))
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

/** Generates or fetches a non-exportable AES key in the Android Keystore under [alias]. */
class AndroidKeystoreKeySource(private val alias: String) : () -> SecretKey {
    override fun invoke(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object { const val PROVIDER = "AndroidKeyStore" }
}

/**
 * Generates a random 32-byte passphrase once, stores it wrapped by [wrapper] in app-private,
 * backup-excluded storage, and unwraps it on later launches.
 */
class WrappedPassphraseProvider(
    private val store: BlobStore,
    private val wrapper: KeyWrapper,
    private val random: SecureRandom = SecureRandom(),
) : DatabaseKeyProvider {
    @Synchronized
    override fun passphrase(): ByteArray {
        store.read()?.let { return wrapper.decrypt(it) }
        val fresh = ByteArray(32).also(random::nextBytes)
        store.write(wrapper.encrypt(fresh))
        return fresh
    }
}

/** Single-blob persistence. */
interface BlobStore {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}

/** Stores the blob in app-private SharedPreferences (excluded from cloud backup by the app manifest). */
class PrefsBlobStore(context: Context, name: String = "drift_keys", private val field: String = "db_passphrase") : BlobStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun read(): ByteArray? = prefs.getString(field, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
    override fun write(bytes: ByteArray) {
        check(prefs.edit().putString(field, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) { "Could not persist key blob" }
    }
}

object KeystoreDatabaseKeys {
    const val ALIAS = "drift_db_wrap_key"
    fun create(context: Context): DatabaseKeyProvider =
        WrappedPassphraseProvider(PrefsBlobStore(context), AesGcmWrapper(AndroidKeystoreKeySource(ALIAS)))
}
