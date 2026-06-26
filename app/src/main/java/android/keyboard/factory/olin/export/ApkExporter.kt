package android.keyboard.factory.olin.export

import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import java.io.File
import java.security.PrivateKey
import java.security.cert.X509Certificate

object ApkExporter {

    fun sign(
        unsignedApk: File,
        signedApk: File,
        privateKey: PrivateKey,
        certificate: X509Certificate,
        minSdkVersion: Int = 29,
    ) {
        val signerConfig = ApkSigner.SignerConfig.Builder(
            "keyboardfactory",
            KeyConfig.Jca(privateKey),
            listOf(certificate),
        ).build()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setMinSdkVersion(minSdkVersion)
            .build()
            .sign()
    }
}
