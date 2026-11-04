/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.keys

import com.amazonaws.util.Base64
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PublicKeyFormatEntity
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

/**
 * Key pair created and/or retrieved from a [DeviceKeyManager].
 *
 * The public key is registered with the telephony service when a phone number is
 * provisioned; the private key never leaves the device and is what later unseals the
 * number's messages.
 *
 * @property keyId [String] Unique identifier of the key pair.
 * @property keyRingId [String] Identifier of the key ring that contains the key pair.
 * @property publicKey [ByteArray] Bytes of the public key.
 * @property privateKey [ByteArray] Bytes of the private key.
 */
internal data class KeyPair(
    val keyId: String,
    val keyRingId: String,
    val publicKey: ByteArray,
    val privateKey: ByteArray,
) {
    /** Deliberately omits the key material so it cannot be logged. */
    override fun toString(): String {
        val clz = this@KeyPair.javaClass.simpleName
        return "$clz[keyId=$keyId keyRingId=$keyRingId publicKey.size=${publicKey.size} privateKey.size=${privateKey.size}]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyPair

        if (keyId != other.keyId) return false
        if (keyRingId != other.keyRingId) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!privateKey.contentEquals(other.privateKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyId.hashCode()
        result = 31 * result + keyRingId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }

    /**
     * The encoding of [publicKey], which the service needs in order to read it.
     *
     * @return The detected [PublicKeyFormatEntity].
     * @throws IllegalStateException if the encoding cannot be determined.
     */
    fun getKeyFormat(): PublicKeyFormatEntity =
        PublicKeyFormatDetector.detectKeyFormat(publicKey)
            ?: throw IllegalStateException("Unable to determine public key format for key ID: $keyId")
}

/**
 * Determines the encoding of a public key by examining its structure, so the correct format
 * is declared when registering the key with the service.
 */
internal object PublicKeyFormatDetector {
    /**
     * Determine the format of a public key.
     *
     * @param keyBytes [ByteArray] The public key bytes.
     * @return The detected format, or `null` when it cannot be determined.
     */
    fun detectKeyFormat(keyBytes: ByteArray): PublicKeyFormatEntity? =
        try {
            when {
                // SPKI carries an algorithm identifier, so X509EncodedKeySpec can parse it.
                isSpkiFormat(keyBytes) -> PublicKeyFormatEntity.SPKI
                isRsaPublicKeyFormat(keyBytes) -> PublicKeyFormatEntity.RSA_PUBLIC_KEY
                else -> null
            }
        } catch (e: Exception) {
            null
        }

    /**
     * Determine the format of a base64-encoded public key.
     *
     * @param base64Key [String] The base64-encoded public key.
     * @return The detected format, or `null` when it cannot be determined.
     */
    fun detectKeyFormat(base64Key: String): PublicKeyFormatEntity? =
        try {
            detectKeyFormat(Base64.decode(base64Key.toByteArray()))
        } catch (e: Exception) {
            null
        }

    private fun isSpkiFormat(keyBytes: ByteArray): Boolean =
        try {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            true
        } catch (e: Exception) {
            false
        }

    private fun isRsaPublicKeyFormat(keyBytes: ByteArray): Boolean =
        try {
            // PKCS#1 starts with an ASN.1 SEQUENCE tag but carries no algorithm identifier.
            keyBytes.isNotEmpty() &&
                keyBytes[0] == 0x30.toByte() &&
                !isSpkiFormat(keyBytes)
        } catch (e: Exception) {
            false
        }
}
