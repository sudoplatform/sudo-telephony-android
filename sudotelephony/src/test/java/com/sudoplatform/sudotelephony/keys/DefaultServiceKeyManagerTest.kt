/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.keys

import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PublicKeyFormatEntity
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
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

    private val mockKeyManagerLogger by before { mock<Logger>() }

    /** An instance whose logger is a mock, so what it logs can be verified. */
    private val loggingInstanceUnderTest by before {
        DefaultServiceKeyManager(
            keyRingServiceName = "sudo-telephony",
            userClient = mockUserClient,
            keyManager = mockKeyManager,
            logger = mockKeyManagerLogger,
        )
    }

    /** Makes the key store record and return the singleton key pair identifier. */
    private fun givenSingletonRecordIsStored(initialKeyId: String? = null) {
        var recorded: String? = initialKeyId
        mockKeyManager.stub {
            on { getPassword(eq(DefaultServiceKeyManager.SINGLETON_KEY_ID_NAME)) } doAnswer {
                recorded?.toByteArray(Charsets.UTF_8)
            }
            on { addPassword(any(), eq(DefaultServiceKeyManager.SINGLETON_KEY_ID_NAME)) } doAnswer {
                recorded = String(it.getArgument<ByteArray>(0), Charsets.UTF_8)
                Unit
            }
        }
    }

    private fun givenSingletonKeyId(keyId: String) {
        mockKeyManager.stub {
            on { getPassword(eq(DefaultServiceKeyManager.SINGLETON_KEY_ID_NAME)) } doReturn
                keyId.toByteArray(Charsets.UTF_8)
        }
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

    @Test
    fun `getSingletonKeyPair() should return null and create nothing when none is recorded`() {
        instanceUnderTest.getSingletonKeyPair() shouldBe null

        verify(mockKeyManager, never()).generateKeyPair(any(), any())
        verify(mockKeyManager, never()).addPassword(any(), any())
    }

    @Test
    fun `getSingletonKeyPair() should return the recorded key pair`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }
        givenSingletonKeyId("singletonKeyId")

        val keyPair = instanceUnderTest.getSingletonKeyPair()

        keyPair?.keyId shouldBe "singletonKeyId"
        keyPair?.publicKey shouldBe generatedKeyPair.public.encoded
        verify(mockKeyManager, never()).generateKeyPair(any(), any())
        verify(mockKeyManager, never()).addPassword(any(), any())
    }

    @Test
    fun `getSingletonKeyPair() should return null and create nothing when its private key is missing`() {
        givenSingletonKeyId("missingKeyId")
        mockKeyManager.stub { on { getPrivateKeyData(eq("missingKeyId")) } doReturn null }

        instanceUnderTest.getSingletonKeyPair() shouldBe null

        verify(mockKeyManager, never()).generateKeyPair(any(), any())
        verify(mockKeyManager, never()).addPassword(any(), any())
    }

    @Test
    fun `getSingletonKeyPair() should throw for a key pair missing its public key`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }
        givenSingletonKeyId("singletonKeyId")
        mockKeyManager.stub { on { getPublicKeyData(eq("singletonKeyId")) } doReturn null }

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException> {
            instanceUnderTest.getSingletonKeyPair()
        }
    }

    @Test
    fun `createSingletonKeyPairIfAbsent() should create the key pair once and then reuse it`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }
        givenSingletonRecordIsStored()

        val first = loggingInstanceUnderTest.createSingletonKeyPairIfAbsent()
        val second = loggingInstanceUnderTest.createSingletonKeyPairIfAbsent()

        first.created shouldBe true
        second.created shouldBe false
        second.keyPair.keyId shouldBe first.keyPair.keyId
        loggingInstanceUnderTest.getSingletonKeyPair()?.keyId shouldBe first.keyPair.keyId
        verify(mockKeyManager, times(1)).generateKeyPair(eq(first.keyPair.keyId), eq(true))
        verify(mockKeyManager).addPassword(
            argThat { contentEquals(first.keyPair.keyId.toByteArray(Charsets.UTF_8)) },
            eq(DefaultServiceKeyManager.SINGLETON_KEY_ID_NAME),
        )
        verify(mockKeyManagerLogger).info(eq("Created singleton key pair ${first.keyPair.keyId}"))
        verify(mockKeyManagerLogger, never()).outputError(any(), any())
    }

    @Test
    fun `createSingletonKeyPairIfAbsent() should return the recorded key pair without creating one`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }
        givenSingletonKeyId("singletonKeyId")

        val result = instanceUnderTest.createSingletonKeyPairIfAbsent()

        result.created shouldBe false
        result.keyPair.keyId shouldBe "singletonKeyId"
        verify(mockKeyManager, never()).generateKeyPair(any(), any())
        verify(mockKeyManager, never()).addPassword(any(), any())
    }

    @Test
    fun `createSingletonKeyPairIfAbsent() should throw rather than replace a key pair missing its public key`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }
        givenSingletonKeyId("singletonKeyId")
        mockKeyManager.stub { on { getPublicKeyData(eq("singletonKeyId")) } doReturn null }

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException> {
            instanceUnderTest.createSingletonKeyPairIfAbsent()
        }

        verify(mockKeyManager, never()).generateKeyPair(any(), any())
        verify(mockKeyManager, never()).addPassword(any(), any())
    }

    @Test
    fun `createSingletonKeyPairIfAbsent() should replace a key pair missing its private key and report it`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }
        givenSingletonRecordIsStored(initialKeyId = "missingKeyId")
        mockKeyManager.stub { on { getPrivateKeyData(eq("missingKeyId")) } doReturn null }

        val replacement = loggingInstanceUnderTest.createSingletonKeyPairIfAbsent()

        replacement.created shouldBe true
        replacement.keyPair.keyId shouldNotBe "missingKeyId"
        loggingInstanceUnderTest.getSingletonKeyPair()?.keyId shouldBe replacement.keyPair.keyId
        verify(mockKeyManager, times(1)).generateKeyPair(any(), eq(true))
        verify(mockKeyManager).deletePassword(eq(DefaultServiceKeyManager.SINGLETON_KEY_ID_NAME))
        verify(mockKeyManagerLogger).error(
            argThat<String> { contains("missingKeyId") && contains(replacement.keyPair.keyId) },
        )
        verify(mockKeyManagerLogger).outputError(
            any(),
            eq(mapOf("missingKeyId" to "missingKeyId", "replacementKeyId" to replacement.keyPair.keyId)),
        )
    }

    @Test
    fun `createSingletonKeyPairIfAbsent() should delete the new key pair when its identifier cannot be recorded`() {
        mockUserClient.stub { on { getSubject() } doReturn "mockUserId" }
        mockKeyManager.stub {
            on { addPassword(any(), eq(DefaultServiceKeyManager.SINGLETON_KEY_ID_NAME)) } doAnswer {
                throw KeyManagerException("store full")
            }
        }

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException> {
            instanceUnderTest.createSingletonKeyPairIfAbsent()
        }

        val keyId = argumentCaptor<String>()
        verify(mockKeyManager).generateKeyPair(keyId.capture(), eq(true))
        verify(mockKeyManager).deleteKeyPair(eq(keyId.firstValue))
    }

    @Test
    fun `removeKeyPair() should keep the singleton key pair`() {
        givenSingletonKeyId("singletonKeyId")

        instanceUnderTest.removeKeyPair("singletonKeyId")

        verify(mockKeyManager, never()).deleteKeyPair(any())
    }

    @Test
    fun `removeKeyPair() should delete any other key pair`() {
        givenSingletonKeyId("singletonKeyId")

        instanceUnderTest.removeKeyPair("otherKeyId")

        verify(mockKeyManager).deleteKeyPair(eq("otherKeyId"))
    }

    @Test
    fun `removeKeyPair() should throw KeyOperationFailedException when the key store fails`() {
        mockKeyManager.stub {
            on { deleteKeyPair(any()) } doAnswer { throw KeyManagerException("locked") }
        }

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException> {
            instanceUnderTest.removeKeyPair("otherKeyId")
        }
    }
}
