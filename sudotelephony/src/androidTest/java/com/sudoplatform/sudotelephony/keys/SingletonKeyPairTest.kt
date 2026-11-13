/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.keys

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PublicKeyFormatEntity
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Test the singleton key pair that every phone number shares when the client enforces one,
 * against a real Android key store.
 *
 * No backend is needed, so these never skip.
 */
@RunWith(AndroidJUnit4::class)
class SingletonKeyPairTest {
    /** Records what is logged, so a test can tell which events were reported. */
    private class CapturingLogDriver : LogDriverInterface {
        override var logLevel: LogLevel = LogLevel.VERBOSE
        val messages: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val errors: MutableList<Pair<Error, Map<String, Any>?>> = Collections.synchronizedList(mutableListOf())

        override fun processDetails(details: com.sudoplatform.sudologging.LogDetails): String = processDetailsDefault(details)

        override fun outputMessage(message: String) {
            messages.add(message)
        }

        override fun outputError(
            error: Error,
            userInfo: Map<String, Any>?,
        ) {
            errors.add(error to userInfo)
        }

        fun messagesAt(level: LogLevel): List<String> = messages.filter { it.contains("[$level]") }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val userClient =
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "mockUserId"
        }

    private lateinit var logDriver: CapturingLogDriver
    private lateinit var sourceKeyManager: KeyManagerInterface
    private lateinit var targetKeyManager: KeyManagerInterface
    private lateinit var source: DefaultServiceKeyManager
    private lateinit var target: DefaultServiceKeyManager

    private fun serviceKeyManager(keyManager: KeyManagerInterface) =
        DefaultServiceKeyManager(
            keyRingServiceName = "sudo-telephony",
            userClient = userClient,
            keyManager = keyManager,
            logger = Logger("tels-singleton-key-test", logDriver),
        )

    private fun DefaultServiceKeyManager.createSingleton(): KeyPair = createSingletonKeyPairIfAbsent().keyPair

    private fun recordedSingletonKeyId(keyManager: KeyManagerInterface): String? =
        keyManager.getPassword(DefaultServiceKeyManager.SINGLETON_KEY_ID_NAME)?.toString(Charsets.UTF_8)

    @Before
    fun setUp() {
        logDriver = CapturingLogDriver()
        sourceKeyManager = KeyManagerFactory(context).createAndroidKeyManager("tels-singleton-source-${UUID.randomUUID()}")
        targetKeyManager = KeyManagerFactory(context).createAndroidKeyManager("tels-singleton-target-${UUID.randomUUID()}")
        source = serviceKeyManager(sourceKeyManager)
        target = serviceKeyManager(targetKeyManager)
    }

    @After
    fun tearDown() {
        runCatching { sourceKeyManager.removeAllKeys() }
        runCatching { targetKeyManager.removeAllKeys() }
    }

    /** Seals [plaintext] for [keyId] the way the telephony service seals message content. */
    private fun seal(
        keyManager: KeyManagerInterface,
        keyId: String,
        plaintext: String,
    ): String {
        val aesKey = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(Unsealer.IV_SIZE_AES_GCM) { (it + 1).toByte() }
        val encryptedKey =
            keyManager.encryptWithPublicKey(
                keyId,
                aesKey,
                KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
            )
        val encryptedData =
            keyManager.encryptWithSymmetricKey(
                aesKey,
                plaintext.toByteArray(Charsets.UTF_8),
                nonce,
                KeyManagerInterface.SymmetricEncryptionAlgorithm.AES_GCM_256,
            )
        return String(
            com.amazonaws.util.Base64
                .encode(encryptedKey + nonce + encryptedData),
            Charsets.UTF_8,
        )
    }

    @Test
    fun getSingletonKeyPairShouldReturnNullAndCreateNothingWhenAbsent() {
        source.getSingletonKeyPair() shouldBe null

        recordedSingletonKeyId(sourceKeyManager) shouldBe null
        logDriver.messages shouldBe emptyList()
    }

    @Test
    fun createSingletonKeyPairIfAbsentShouldCreateOnceAndThenReturnTheSameKey() {
        val first = source.createSingletonKeyPairIfAbsent()
        val second = source.createSingletonKeyPairIfAbsent()

        first.created shouldBe true
        second.created shouldBe false
        second.keyPair shouldBe first.keyPair
        source.getSingletonKeyPair() shouldBe first.keyPair
        recordedSingletonKeyId(sourceKeyManager) shouldBe first.keyPair.keyId
        first.keyPair.getKeyFormat() shouldBe PublicKeyFormatEntity.RSA_PUBLIC_KEY
        logDriver.messagesAt(LogLevel.INFO).count { it.contains("Created singleton key pair ${first.keyPair.keyId}") } shouldBe 1
    }

    @Test
    fun createSingletonKeyPairIfAbsentShouldBeDistinctFromGeneratedKeys() {
        val generated = source.generateKeyPair()
        val singleton = source.createSingleton()

        singleton.keyId shouldNotBe generated.keyId
        source.createSingleton().keyId shouldBe singleton.keyId
    }

    @Test
    fun createSingletonKeyPairIfAbsentShouldCreateOnlyOneKeyUnderConcurrentFirstUse() {
        val threads = 8
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            val results =
                (1..threads).map {
                    executor.submit<String> {
                        start.await()
                        source.createSingleton().keyId
                    }
                }
            start.countDown()
            val keyIds = results.map { it.get(60, TimeUnit.SECONDS) }.toSet()

            keyIds.size shouldBe 1
            recordedSingletonKeyId(sourceKeyManager) shouldBe keyIds.single()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun importKeysShouldRestoreTheSingletonPublicKey() {
        val singleton = source.createSingleton()
        val sealed = seal(sourceKeyManager, singleton.keyId, "sealed to the shared key")

        target.importKeys(source.exportKeys())

        // The archive leaves public keys out, so the key store regenerates the public key from
        // the private key, and it must match the one that was registered with the service.
        val restored = target.getSingletonKeyPair()!!
        target.createSingletonKeyPairIfAbsent().created shouldBe false
        restored.keyId shouldBe singleton.keyId
        restored.publicKey.contentEquals(singleton.publicKey) shouldBe true
        restored.getKeyFormat() shouldBe PublicKeyFormatEntity.RSA_PUBLIC_KEY
        Unsealer(target).unseal(singleton.keyId, sealed) shouldBe "sealed to the shared key"
        logDriver.errors shouldBe emptyList()
    }

    @Test
    fun importKeysShouldRestoreNothingWhenTheArchiveHasNoSingleton() {
        val generated = source.generateKeyPair()

        target.importKeys(source.exportKeys())

        recordedSingletonKeyId(targetKeyManager) shouldBe null
        target.privateKeyExists(generated.keyId) shouldBe true
    }

    @Test
    fun singletonKeyPairWithAPrivateKeyButNoPublicKeyShouldFailWithoutBeingReplaced() {
        val singleton = source.createSingleton()
        sourceKeyManager.deletePublicKey(singleton.keyId)

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException> {
            source.getSingletonKeyPair()
        }
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException> {
            source.createSingleton()
        }

        recordedSingletonKeyId(sourceKeyManager) shouldBe singleton.keyId
        source.privateKeyExists(singleton.keyId) shouldBe true
        logDriver.errors shouldBe emptyList()
    }

    @Test
    fun createSingletonKeyPairIfAbsentShouldReplaceAMissingSingletonAndReportIt() {
        val missing = source.createSingleton()
        sourceKeyManager.deleteKeyPair(missing.keyId)
        source.getSingletonKeyPair() shouldBe null

        val result = source.createSingletonKeyPairIfAbsent()
        val replacement = result.keyPair

        result.created shouldBe true
        replacement.keyId shouldNotBe missing.keyId
        recordedSingletonKeyId(sourceKeyManager) shouldBe replacement.keyId
        logDriver.messagesAt(LogLevel.ERROR).any { it.contains(missing.keyId) && it.contains(replacement.keyId) } shouldBe true
        logDriver.errors.size shouldBe 1
        logDriver.errors.single().second shouldBe
            mapOf("missingKeyId" to missing.keyId, "replacementKeyId" to replacement.keyId)
    }

    @Test
    fun removeAllKeysShouldClearTheSingleton() {
        val singleton = source.createSingleton()

        source.removeAllKeys()

        recordedSingletonKeyId(sourceKeyManager) shouldBe null
        source.getSingletonKeyPair() shouldBe null
        source.createSingleton().keyId shouldNotBe singleton.keyId
        logDriver.errors shouldBe emptyList()
        logDriver.messagesAt(LogLevel.ERROR) shouldBe emptyList()
    }

    @Test
    fun removeKeyPairShouldRemoveBothHalves() {
        val keyPair = source.generateKeyPair()

        source.removeKeyPair(keyPair.keyId)

        source.privateKeyExists(keyPair.keyId) shouldBe false
        source.getPublicKeyWithId(keyPair.keyId) shouldBe null
    }

    @Test
    fun removeKeyPairShouldSucceedForAMissingKey() {
        source.removeKeyPair(UUID.randomUUID().toString())
    }

    @Test
    fun removeKeyPairShouldKeepTheSingleton() {
        val singleton = source.createSingleton()
        val other = source.generateKeyPair()

        source.removeKeyPair(singleton.keyId)
        source.removeKeyPair(other.keyId)

        source.getKeyPairWithId(singleton.keyId) shouldBe singleton
        source.privateKeyExists(other.keyId) shouldBe false
        source.createSingleton().keyId shouldBe singleton.keyId
    }
}
