package nl.shadowlink.tools.shadowlib.utils.encryption

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * The img cipher is requested under the name "Rijndael/ECB/NoPadding". The JCE bundled with the
 * JDK only ships "AES", so without a provider that knows the Rijndael name
 * Cipher.getInstance() throws NoSuchAlgorithmException and no GTA: IV image can be opened.
 */
object CryptoProvider {

    @JvmStatic
    fun ensureRegistered() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }
}
