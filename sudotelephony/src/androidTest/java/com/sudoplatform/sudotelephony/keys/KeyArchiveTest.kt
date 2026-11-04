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
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
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
import java.util.UUID

/**
 * Test that a key archive exported from one device key store can be imported into another and
 * still unseal content sealed for the original key.
 *
 * This needs a real Android key store, so it runs on a device rather than as a JVM test. It is
 * the operation that makes a phone number's messages readable on a second device, so the
 * assertion is about recovering sealed content rather than merely about the archive bytes.
 */
@RunWith(AndroidJUnit4::class)
class KeyArchiveTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val logger = Logger("tels-key-archive-test", AndroidUtilsLogDriver(LogLevel.INFO))

    /** Stands in for the signed-in user so a key ring id can be derived. */
    private val userClient =
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "mockUserId"
        }

    private lateinit var sourceNamespace: String
    private lateinit var targetNamespace: String
    private lateinit var sourceKeyManager: KeyManagerInterface
    private lateinit var targetKeyManager: KeyManagerInterface
    private lateinit var source: DefaultServiceKeyManager
    private lateinit var target: DefaultServiceKeyManager

    private fun serviceKeyManager(keyManager: KeyManagerInterface) =
        DefaultServiceKeyManager(
            keyRingServiceName = "sudo-telephony",
            userClient = userClient,
            keyManager = keyManager,
            logger = logger,
        )

    @Before
    fun setUp() {
        // Distinct namespaces so the two stores are genuinely separate, standing in for two
        // devices.
        sourceNamespace = "tels-archive-source-${UUID.randomUUID()}"
        targetNamespace = "tels-archive-target-${UUID.randomUUID()}"
        sourceKeyManager = KeyManagerFactory(context).createAndroidKeyManager(sourceNamespace)
        targetKeyManager = KeyManagerFactory(context).createAndroidKeyManager(targetNamespace)
        source = serviceKeyManager(sourceKeyManager)
        target = serviceKeyManager(targetKeyManager)
    }

    @After
    fun tearDown() {
        runCatching { sourceKeyManager.removeAllKeys() }
        runCatching { targetKeyManager.removeAllKeys() }
    }

    /**
     * Seals [plaintext] for [keyId] the way the telephony service seals message content.
     *
     * The encryption goes through the key store rather than the JCE directly, because the
     * store encodes a public key as PKCS#1 while the JCE expects X.509, and the subject here
     * is the archive rather than key encodings.
     */
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
    fun exportKeysShouldProduceAnArchive() {
        source.generateKeyPair()

        val archive = source.exportKeys()

        archive shouldNotBe null
        archive.isNotEmpty() shouldBe true
    }

    @Test
    fun importKeysShouldMakeSealedContentReadableOnAnotherStore() {
        val keyPair = source.generateKeyPair()
        val body = "Hello from the telephony service"
        val sealed = seal(sourceKeyManager, keyPair.keyId, body)

        // The target store has never seen the key, so it cannot read the content.
        target.privateKeyExists(keyPair.keyId) shouldBe false

        target.importKeys(source.exportKeys())

        target.privateKeyExists(keyPair.keyId) shouldBe true
        Unsealer(target).unseal(keyPair.keyId, sealed) shouldBe body
    }

    @Test
    fun importKeysShouldReplaceRatherThanMergeTheExistingKeys() {
        val sourceKeyPair = source.generateKeyPair()
        val targetOwnKeyPair = target.generateKeyPair()

        target.importKeys(source.exportKeys())

        // The archive's key arrives and the key the target held beforehand is gone, so the
        // store matches the archive.
        target.privateKeyExists(sourceKeyPair.keyId) shouldBe true
        target.privateKeyExists(targetOwnKeyPair.keyId) shouldBe false
    }

    @Test
    fun importKeysShouldCarryEveryKeyInTheArchive() {
        val first = source.generateKeyPair()
        val second = source.generateKeyPair()

        target.importKeys(source.exportKeys())

        target.privateKeyExists(first.keyId) shouldBe true
        target.privateKeyExists(second.keyId) shouldBe true
    }

    @Test
    fun generatedKeysShouldReportTheEncodingTheKeyStoreActuallyProduces() {
        val keyPair = source.generateKeyPair()

        // The device key store encodes a public key as PKCS#1, not as the X.509
        // SubjectPublicKeyInfo the JCE produces, and this is the format declared to the
        // service when a number is provisioned.
        keyPair.getKeyFormat() shouldBe PublicKeyFormatEntity.RSA_PUBLIC_KEY
    }

    @Test
    fun importKeysShouldRejectDataThatIsNotAnArchive() {
        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException> {
            target.importKeys("this is not a key archive".toByteArray())
        }
    }

    @Test
    fun exportedKeysShouldSurviveARoundTripThroughTheSourceStore() {
        val keyPair = source.generateKeyPair()
        val sealed = seal(sourceKeyManager, keyPair.keyId, "round trip")
        val archive = source.exportKeys()

        // Losing the keys and restoring them from the archive recovers the ability to read.
        sourceKeyManager.removeAllKeys()
        source.privateKeyExists(keyPair.keyId) shouldBe false

        source.importKeys(archive)

        Unsealer(source).unseal(keyPair.keyId, sealed) shouldBe "round trip"
    }
}
