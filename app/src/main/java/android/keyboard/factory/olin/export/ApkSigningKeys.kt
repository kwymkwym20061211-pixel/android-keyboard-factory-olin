package android.keyboard.factory.olin.export

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import javax.security.auth.x500.X500Principal

private const val ANDROID_KEYSTORE = "AndroidKeyStore"

/** One signing identity per keyboard project, keyed by [alias]. Generated lazily on first
 * export and reused on every later export so re-exporting overwrite-installs the same app. */
object ApkSigningKeys {

    fun getOrCreate(alias: String): Pair<PrivateKey, X509Certificate> {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            generate(alias)
        }
        val privateKey = keyStore.getKey(alias, null) as PrivateKey
        val certificate = keyStore.getCertificate(alias) as X509Certificate
        return privateKey to certificate
    }

    private fun generate(alias: String) {
        val notBefore = Date()
        val notAfter = Calendar.getInstance().apply {
            time = notBefore
            add(Calendar.YEAR, 30)
        }.time

        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setKeySize(2048)
            .setCertificateSubject(X500Principal("CN=KeyboardFactory"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(notBefore)
            .setCertificateNotAfter(notAfter)
            .build()

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).apply {
            initialize(spec)
            generateKeyPair()
        }
    }
}
