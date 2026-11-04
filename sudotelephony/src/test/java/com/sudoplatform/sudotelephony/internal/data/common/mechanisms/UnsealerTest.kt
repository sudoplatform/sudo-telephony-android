/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.common.mechanisms

import com.amazonaws.util.Base64
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Test the correct operation of [Unsealer] against values sealed exactly the way the service
 * seals them.
 *
 * The service seals a field as
 * `base64(RSA-OAEP(aesKey) || nonce || AES-256-GCM(field, nonce) || tag)`, with the RSA block
 * occupying the first [Unsealer.BLOCK_SIZE_RSA] bytes and the nonce the
 * [Unsealer.IV_SIZE_AES_GCM] bytes after it. This test produces that layout with the JCE
 * directly, so a change to the unsealer that diverged from the service's format would fail
 * here rather than only against a deployed instance.
 */
@RunWith(RobolectricTestRunner::class)
class UnsealerTest : BaseTests() {
    private val keyPair by before {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    /** Length of the AES-GCM authentication tag, in bits, as the JCE wants it. */
    private val tagSizeBits = Unsealer.TAG_SIZE_AES_GCM * 8

    /**
     * The nonce sealed values carry. Deliberately not all zeroes, so an unsealer that
     * assumed a fixed nonce instead of reading the envelope's would fail.
     */
    private val nonce = ByteArray(Unsealer.IV_SIZE_AES_GCM) { (it + 1).toByte() }

    private val mockDeviceKeyManager by before {
        mock<DeviceKeyManager>().stub {
            // Stand in for the device key store: RSA-OAEP decrypt with the private key.
            on {
                decryptWithKeyPairId(any(), any(), any())
            } doAnswer { invocation ->
                Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding").run {
                    init(Cipher.DECRYPT_MODE, keyPair.private)
                    doFinal(invocation.getArgument<ByteArray>(0))
                }
            }
            on {
                decryptWithSymmetricKey(any(), any(), any(), any())
            } doAnswer { invocation ->
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(
                        Cipher.DECRYPT_MODE,
                        SecretKeySpec(invocation.getArgument<ByteArray>(0), "AES"),
                        GCMParameterSpec(tagSizeBits, invocation.getArgument<ByteArray>(2)),
                    )
                    doFinal(invocation.getArgument<ByteArray>(1))
                }
            }
        }
    }

    private val instanceUnderTest by before {
        Unsealer(mockDeviceKeyManager)
    }

    /** Seals [plaintext] the way the service does. */
    private fun seal(plaintext: String): String {
        val aesKey = ByteArray(32) { it.toByte() }

        val encryptedData =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(aesKey, "AES"),
                    GCMParameterSpec(tagSizeBits, nonce),
                )
                doFinal(plaintext.toByteArray(Charsets.UTF_8))
            }
        val encryptedKey =
            Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding").run {
                init(Cipher.ENCRYPT_MODE, keyPair.public)
                doFinal(aesKey)
            }

        return String(Base64.encode(encryptedKey + nonce + encryptedData), Charsets.UTF_8)
    }

    @Test
    fun `unseal() should recover a value sealed the way the service seals it`() {
        val body = "Hello from the telephony service"

        instanceUnderTest.unseal("mockKeyId", seal(body)) shouldBe body
    }

    @Test
    fun `unseal() should split the sealed value at the RSA block boundary`() {
        val sealed = seal("anything")

        instanceUnderTest.unseal("mockKeyId", sealed)

        // The encrypted AES key is exactly the RSA modulus size.
        verify(mockDeviceKeyManager).decryptWithKeyPairId(
            org.mockito.kotlin.check { it.size shouldBe Unsealer.BLOCK_SIZE_RSA },
            eq("mockKeyId"),
            eq(KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1),
        )
    }

    @Test
    fun `unseal() should decrypt with the nonce the sealed value carries`() {
        val sealed = seal("nonce comes from the envelope")
        val sealedBytes = Base64.decode(sealed)

        instanceUnderTest.unseal("mockKeyId", sealed)

        verify(mockDeviceKeyManager).decryptWithSymmetricKey(
            any(),
            org.mockito.kotlin.check {
                // The ciphertext and its tag, with the key and nonce prefixes removed.
                it.size shouldBe sealedBytes.size - Unsealer.BLOCK_SIZE_RSA - Unsealer.IV_SIZE_AES_GCM
            },
            org.mockito.kotlin.check { it.contentEquals(nonce) shouldBe true },
            eq(KeyManagerInterface.SymmetricEncryptionAlgorithm.AES_GCM_256),
        )
    }

    @Test
    fun `unseal() should recover an empty body`() {
        instanceUnderTest.unseal("mockKeyId", seal("")) shouldBe ""
    }

    @Test
    fun `unseal() should recover a value longer than one AES block`() {
        val body = "x".repeat(1000)

        instanceUnderTest.unseal("mockKeyId", seal(body)) shouldBe body
    }

    @Test
    fun `unseal() should recover non-ASCII text`() {
        val body = "Grüße 👋 日本語"

        instanceUnderTest.unseal("mockKeyId", seal(body)) shouldBe body
    }

    @Test
    fun `unseal() should recover an E164 phone number`() {
        instanceUnderTest.unseal("mockKeyId", seal("+14155550123")) shouldBe "+14155550123"
    }

    @Test
    fun `unseal() should reject a value too short to hold a key, a nonce and a tag`() {
        // An encrypted key and a nonce, but no room for the authentication tag.
        val tooShort =
            String(
                Base64.encode(ByteArray(Unsealer.BLOCK_SIZE_RSA + Unsealer.IV_SIZE_AES_GCM)),
                Charsets.UTF_8,
            )

        shouldThrow<Unsealer.UnsealerException.SealedDataTooShortException> {
            instanceUnderTest.unseal("mockKeyId", tooShort)
        }
    }

    @Test
    fun `unseal() should reject an algorithm it cannot read`() {
        shouldThrow<Unsealer.UnsealerException.UnsupportedAlgorithmException> {
            instanceUnderTest.unseal("mockKeyId", seal("body"), algorithm = "SomeFutureAlgorithm")
        }
    }

    @Test
    fun `unseal() should surface a key store decryption failure`() {
        mockDeviceKeyManager.stub {
            on {
                decryptWithKeyPairId(any(), any(), any())
            } doThrow DeviceKeyManager.DeviceKeyManagerException.DecryptionException("wrong key")
        }

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.DecryptionException> {
            instanceUnderTest.unseal("mockKeyId", seal("body"))
        }
    }
}
