package com.sudoplatform.sudotelephony.keys

import com.sudoplatform.sudotelephony.graphql.fragment.PublicKey
import com.sudoplatform.sudotelephony.results.Result
import java.security.KeyPair

/**
 * Interface encapsulating a set of methods for securely storing keys and performing cryptographic
 * operations.
 */
interface TelephonyKeyManager {
    /**
     * Removes all keys associated with this KeyManager.
     */
    fun removeAllKeys()

    /**
     * Generates and securely stores a key pair for public key cryptography.
     *
     * @param callback callback to attach results to
     */
    fun generateKeyPair(callback: (Result<PublicKey>) -> Unit)

    /**
     * Returns the subject of the user associated with this client.
     *
     * @return user subject.
     */
    fun getOwner(): String?

    /**
     * @return the public private key pair for the associated user or null if either of the keys are not found.
     */
    fun getKeyPair(): KeyPair?

    /**
     * Retrieves the KeyId of the associated identity
     *
     * @return keyId or null if it's not found.
     */
    fun getKeyId(): String?

    /**
     * Retrieves the keyRingId of the associated identity
     *
     * @return keyRingId or null if it's not found.
     */
    fun getKeyRingId(): String?

    /**
     * @param data encrypted data to be decrypted
     * @return decrypted data
     */
    fun decryptSealedData(data: ByteArray): ByteArray
}
