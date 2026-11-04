/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.keys

import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PublicKeyFormatEntity
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator

/**
 * Test the correct operation of [DefaultServiceKeyManager]: the key ring identifier is
 * namespaced per user, key pairs are generated through the device key store, and key store
 * failures surface as [DeviceKeyManager.DeviceKeyManagerException].
 */
@RunWith(RobolectricTestRunner::class)
class DefaultServiceKeyManagerTest : BaseTests() {
    private val generatedKeyPair by before {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { getPublicKeyData(any()) } doReturn generatedKeyPair.public.encoded
            on { getPrivateKeyData(any()) } doReturn generatedKeyPair.private.encoded
        }
    }

    private val instanceUnderTest by before {
        DefaultServiceKeyManager(
            keyRingServiceName = "sudo-telephony",
            userClient = mockUserClient,
            keyManager = mockKeyManager,
            logger = mockLogger,
        )
    }

    @Test
    fun `getKeyRingId() should namespace the key ring by the signed-in user`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }

        instanceUnderTest.getKeyRingId() shouldBe "sudo-telephony.mockUserId"
    }

    @Test
    fun `getKeyRingId() should throw UserIdNotFoundException when there is no subject`() {
        mockUserClient.stub { on { getSubject() } doReturn null }

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException> {
            instanceUnderTest.getKeyRingId()
        }
    }

    @Test
    fun `generateKeyPair() should store the pair and return both halves`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }

        val keyPair = instanceUnderTest.generateKeyPair()

        keyPair.keyId shouldNotBe ""
        keyPair.keyRingId shouldBe "sudo-telephony.mockUserId"
        keyPair.publicKey shouldBe generatedKeyPair.public.encoded
        keyPair.privateKey shouldBe generatedKeyPair.private.encoded

        // The pair is persisted under the generated id so it can seal messages later.
        verify(mockKeyManager).generateKeyPair(eq(keyPair.keyId), eq(true))
    }

    @Test
    fun `generateKeyPair() should detect an SPKI encoded public key`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }

        // The key store is mocked here with JCE bytes, which are X.509 SubjectPublicKeyInfo.
        // The real device key store encodes a public key as PKCS#1 instead, which is
        // asserted on a device by KeyArchiveTest.
        instanceUnderTest.generateKeyPair().getKeyFormat() shouldBe PublicKeyFormatEntity.SPKI
    }

    @Test
    fun `generateKeyPair() should throw KeyGenerationException when the key store fails`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }
        mockKeyManager.stub {
            on { generateKeyPair(any(), any()) } doThrow KeyManagerException("no entropy")
        }

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException> {
            instanceUnderTest.generateKeyPair()
        }
    }

    @Test
    fun `getKeyPairWithId() should return the stored pair`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }

        val keyPair = instanceUnderTest.getKeyPairWithId("mockKeyId")

        keyPair shouldNotBe null
        keyPair!!.keyId shouldBe "mockKeyId"
        keyPair.keyRingId shouldBe "sudo-telephony.mockUserId"
    }

    @Test
    fun `getKeyPairWithId() should return null when the public key is absent`() {
        mockKeyManager.stub { on { getPublicKeyData(any()) } doReturn null }

        instanceUnderTest.getKeyPairWithId("mockKeyId") shouldBe null
    }

    @Test
    fun `getKeyPairWithId() should return null when the private key is absent`() {
        mockKeyManager.stub { on { getPrivateKeyData(any()) } doReturn null }

        instanceUnderTest.getKeyPairWithId("mockKeyId") shouldBe null
    }

    @Test
    fun `privateKeyExists() should reflect the key store`() {
        instanceUnderTest.privateKeyExists("mockKeyId") shouldBe true

        mockKeyManager.stub { on { getPrivateKeyData(any()) } doReturn null }
        instanceUnderTest.privateKeyExists("mockKeyId") shouldBe false
    }

    @Test
    fun `toString() should not leak key material`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }

        val rendered = instanceUnderTest.generateKeyPair().toString()

        rendered.contains("privateKey.size=") shouldBe true
        rendered.contains(String(generatedKeyPair.private.encoded, Charsets.ISO_8859_1)) shouldBe false
    }
}
