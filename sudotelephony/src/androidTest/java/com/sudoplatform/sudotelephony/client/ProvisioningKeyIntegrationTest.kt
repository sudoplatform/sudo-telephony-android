/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.UUID

/**
 * Test which key pair a phone number is provisioned with, and when that key pair is removed
 * from the device, with and without [SudoTelephonyClient.Builder.setEnforceSingletonKeyPair],
 * against a deployed telephony service.
 */
@RunWith(AndroidJUnit4::class)
class ProvisioningKeyIntegrationTest : BaseIntegrationTest() {
    /** Records the key pairs the SDK generates, so a test can find the one it made. */
    private class RecordingKeyManager(
        private val keyManager: KeyManagerInterface,
    ) : KeyManagerInterface by keyManager {
        val generatedKeyIds: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun generateKeyPair(
            name: String,
            isExportable: Boolean,
        ) {
            keyManager.generateKeyPair(name, isExportable)
            generatedKeyIds.add(name)
        }
    }

    private val keyManager =
        RecordingKeyManager(
            KeyManagerFactory(context).createAndroidKeyManager("tels-provisioning-key-test-${UUID.randomUUID()}"),
        )

    private val clients = mutableListOf<SudoTelephonyClient>()

    /** A client over this test's key store, which every client the test builds shares. */
    private fun client(enforceSingletonKeyPair: Boolean): SudoTelephonyClient =
        SudoTelephonyClient
            .builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setLogger(logger)
            .setKeyManager(keyManager)
            .setEnforceSingletonKeyPair(enforceSingletonKeyPair)
            .build()
            .also { clients.add(it) }

    @After
    fun closeClients() {
        clients.forEach { runCatching { it.close() } }
        runCatching { keyManager.removeAllKeys() }
    }

    private fun hasKeyPair(keyId: String): Boolean = keyManager.getPrivateKeyData(keyId) != null

    private suspend fun releaseWhenActive(
        client: SudoTelephonyClient,
        id: String,
    ): PhoneNumber {
        awaitState(id) { it == PhoneNumberState.Active }
        return client.deprovisionPhoneNumber(id)
    }

    private suspend fun provisionWithUnknownSearch(client: SudoTelephonyClient) {
        shouldThrow<SudoTelephonyClient.PhoneNumberException.PhoneNumberSearchExpiredException> {
            client.provisionPhoneNumber(
                ProvisionPhoneNumberInput(
                    searchId = "tel-pns-${UUID.randomUUID()}",
                    phoneNumber = "+1${areaCode}5550199",
                    ownershipProofToken = newOwnershipProof(),
                ),
            )
        }
    }

    @Test
    fun perNumberKeysShouldGiveEachNumberItsOwnKeyAndRemoveItOnDeprovision() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = false)

            val provisioned = provisionANumber(client = client).number

            provisioned.keyIds shouldBe keyManager.generatedKeyIds.toList()
            val keyId = provisioned.keyIds.single()
            hasKeyPair(keyId) shouldBe true

            releaseWhenActive(client, provisioned.id).state shouldBe PhoneNumberState.Released

            hasKeyPair(keyId) shouldBe false
        }

    @Test
    fun perNumberKeysShouldRemoveTheKeyWhenTheServiceRejectsProvisioning() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = false)

            provisionWithUnknownSearch(client)

            val keyId = keyManager.generatedKeyIds.single()
            hasKeyPair(keyId) shouldBe false
        }

    @Test
    fun createSingletonKeysIfAbsentShouldCreateTheKeyOnceAndReportWhetherItDid() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = true)

            val first = client.createSingletonKeysIfAbsent()
            val second = client.createSingletonKeysIfAbsent()

            first.keyPair.created shouldBe true
            second.keyPair.created shouldBe false
            second.keyPair.keyId shouldBe first.keyPair.keyId
            keyManager.generatedKeyIds.toList() shouldBe listOf(first.keyPair.keyId)
        }

    @Test
    fun createSingletonKeysIfAbsentShouldBeRejectedWithoutTheSingletonKeyMode() =
        runBlocking {
            shouldThrow<SudoTelephonyClient.CryptographicKeysException.InvalidConfigurationException> {
                client(enforceSingletonKeyPair = false).createSingletonKeysIfAbsent()
            }

            keyManager.generatedKeyIds shouldBe emptyList<String>()
        }

    @Test
    fun singletonKeyModeShouldNotProvisionOrCreateAKeyBeforeTheKeyIsCreated() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = true)

            shouldThrow<SudoTelephonyClient.PhoneNumberException.SingletonKeyPairNotFoundException> {
                client.provisionPhoneNumber(
                    ProvisionPhoneNumberInput(
                        searchId = "tel-pns-${UUID.randomUUID()}",
                        phoneNumber = "+1${areaCode}5550199",
                    ),
                )
            }

            keyManager.generatedKeyIds shouldBe emptyList<String>()
        }

    @Test
    fun singletonKeyShouldBeSharedByEveryNumberAndSurviveDeprovision() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = true)
            client.createSingletonKeysIfAbsent()

            val first = provisionANumber(client = client).number
            val second = provisionANumber(client = client).number

            // Only the singleton key pair was made, and both numbers were registered with it.
            val singletonKeyId = keyManager.generatedKeyIds.single()
            first.keyIds shouldBe listOf(singletonKeyId)
            second.keyIds shouldBe listOf(singletonKeyId)

            // Releasing a number through a client that no longer enforces the singleton must
            // still leave the key the other number is sealed to.
            releaseWhenActive(client(enforceSingletonKeyPair = false), first.id).state shouldBe
                PhoneNumberState.Released

            hasKeyPair(singletonKeyId) shouldBe true
            client.getPhoneNumber(second.id)?.keyIds shouldBe listOf(singletonKeyId)
        }

    @Test
    fun singletonKeyShouldSurviveARejectedProvision() =
        runBlocking {
            val client = client(enforceSingletonKeyPair = true)
            client.createSingletonKeysIfAbsent()

            provisionWithUnknownSearch(client)

            val singletonKeyId = keyManager.generatedKeyIds.single()
            hasKeyPair(singletonKeyId) shouldBe true

            val provisioned = provisionANumber(client = client).number

            provisioned.keyIds shouldBe listOf(singletonKeyId)
            keyManager.generatedKeyIds.size shouldBe 1
        }
}
