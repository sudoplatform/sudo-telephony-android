/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.samples

import android.content.Context
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import com.sudoplatform.sudotelephony.subscription.PhoneMessageSubscriber
import com.sudoplatform.sudotelephony.subscription.PhoneNumberSubscriber
import com.sudoplatform.sudotelephony.subscription.Subscriber
import com.sudoplatform.sudotelephony.types.ListAPIResult
import com.sudoplatform.sudotelephony.types.PhoneMessageEvent
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.types.inputs.DeletePhoneMessagesInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesForConversationIdInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesForPhoneNumberIdInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersForSudoIdInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersInput
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
import com.sudoplatform.sudotelephony.types.inputs.SearchAvailablePhoneNumbersInput
import com.sudoplatform.sudotelephony.types.inputs.UpdatePhoneMessagesInput
import com.sudoplatform.sudouser.SudoPlatformSignInCallback
import com.sudoplatform.sudouser.SudoUserClient
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

/**
 * These are sample snippets of code that are included in the generated documentation. They
 * are placed here in the test code so that at least we know they will compile.
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("UNUSED_VARIABLE")
class Samples : BaseTests() {
    private val context by before { mock<Context>() }

    @Test
    fun mockTest() {
        // Just to keep junit happy
    }

    fun sudoTelephonyClient(sudoUserClient: SudoUserClient) {
        val telephonyClient =
            SudoTelephonyClient
                .builder()
                .setContext(context)
                .setSudoUserClient(sudoUserClient)
                .build()
    }

    // The client holds the callback weakly, so keep it in a field that outlives the client.
    private lateinit var signInCallback: SudoPlatformSignInCallback

    fun setSignInCallback(
        telephonyClient: SudoTelephonyClient,
        sudoUserClient: SudoUserClient,
    ) {
        signInCallback =
            object : SudoPlatformSignInCallback {
                override suspend fun signIn() {
                    // Throwing here stops the operation that needed the sign in.
                    sudoUserClient.signInWithKey()
                }
            }
        telephonyClient.setSignInCallback(signInCallback)
    }

    suspend fun getSupportedCountries(telephonyClient: SudoTelephonyClient) {
        val supportedCountries = telephonyClient.getSupportedCountries()
        // "US", "CA", ... Pass one of these as the country code of a phone number search.
    }

    suspend fun getConfigurationData(telephonyClient: SudoTelephonyClient) {
        val config = telephonyClient.getConfigurationData()
        if (config.sudoOwnershipProofRequired) {
            // Provisioning must pass a Sudo ownership proof, so every number belongs to a Sudo.
        }
        val pageSize = config.listMaxLimit
    }

    suspend fun searchAvailablePhoneNumbers(telephonyClient: SudoTelephonyClient) {
        val search =
            telephonyClient.searchAvailablePhoneNumbers(
                SearchAvailablePhoneNumbersInput(
                    countryCode = "US",
                    areaCode = "415",
                    limit = 5,
                ),
            )
        // Pass search.id as the search id when provisioning one of search.results.
        val firstAvailable = search.results.firstOrNull()?.phoneNumber
    }

    suspend fun provisionPhoneNumber(
        telephonyClient: SudoTelephonyClient,
        getOwnershipProof: suspend (audience: String) -> String,
    ) {
        val search =
            telephonyClient.searchAvailablePhoneNumbers(
                SearchAvailablePhoneNumbersInput(countryCode = "US", areaCode = "415"),
            )
        val available = search.results.first()

        // A proof makes the number belong to a Sudo. Request it for the telephony audience,
        // for example with sudoProfilesClient.getOwnershipProof(sudo, audience).
        val ownershipProof = getOwnershipProof(SudoTelephonyClient.OWNERSHIP_PROOF_AUDIENCE)

        // The SDK generates the key pair that seals the number's messages and keeps the
        // private key in the device key store.
        val phoneNumber =
            telephonyClient.provisionPhoneNumber(
                ProvisionPhoneNumberInput(
                    searchId = search.id,
                    phoneNumber = available.phoneNumber,
                    ownershipProofToken = ownershipProof,
                ),
            )
        // phoneNumber.sudoId is the Sudo the proof was issued for.
        // phoneNumber.state is PROVISIONING until the order completes with the provider.
    }

    suspend fun deprovisionPhoneNumber(
        telephonyClient: SudoTelephonyClient,
        phoneNumberId: String,
    ) {
        // Only an ACTIVE or SUSPENDED number can be released; the allowance comes back at
        // once and the number returns to the carrier in the background.
        val released = telephonyClient.deprovisionPhoneNumber(phoneNumberId)
        // released.state is RELEASED.
    }

    suspend fun getPhoneNumber(
        telephonyClient: SudoTelephonyClient,
        phoneNumberId: String,
    ) {
        // Null when the number does not belong to the caller. Poll this to follow a number
        // out of PROVISIONING.
        val phoneNumber = telephonyClient.getPhoneNumber(phoneNumberId)
        val state = phoneNumber?.state
    }

    suspend fun listPhoneNumbers(telephonyClient: SudoTelephonyClient) {
        var nextToken: String? = null
        val allNumbers = mutableListOf<com.sudoplatform.sudotelephony.types.PhoneNumber>()
        do {
            val page = telephonyClient.listPhoneNumbers(ListPhoneNumbersInput(nextToken = nextToken))
            allNumbers.addAll(page.items)
            nextToken = page.nextToken
        } while (nextToken != null)
    }

    suspend fun listPhoneNumbersForSudoId(
        telephonyClient: SudoTelephonyClient,
        sudoId: String,
    ) {
        var nextToken: String? = null
        val sudoNumbers = mutableListOf<PhoneNumber>()
        do {
            val page =
                telephonyClient.listPhoneNumbersForSudoId(
                    ListPhoneNumbersForSudoIdInput(sudoId = sudoId, nextToken = nextToken),
                )
            sudoNumbers.addAll(page.items)
            nextToken = page.nextToken
        } while (nextToken != null)
    }

    suspend fun getPhoneMessage(
        telephonyClient: SudoTelephonyClient,
        messageId: String,
    ) {
        // The other party's number and the text are unsealed with the private key held on
        // this device, so body and remotePhoneNumber are readable here.
        val message = telephonyClient.getPhoneMessage(messageId)
        val body = message?.body
    }

    suspend fun listPhoneMessagesForPhoneNumberId(
        telephonyClient: SudoTelephonyClient,
        phoneNumberId: String,
    ) {
        // Newest first across every conversation of the number.
        when (
            val result =
                telephonyClient.listPhoneMessagesForPhoneNumberId(
                    ListPhoneMessagesForPhoneNumberIdInput(phoneNumberId = phoneNumberId),
                )
        ) {
            is ListAPIResult.Success ->
                result.result.items.forEach { message ->
                    println("${message.remotePhoneNumber}: ${message.body}")
                }
            is ListAPIResult.Partial -> {
                result.result.items.forEach { message ->
                    println("${message.remotePhoneNumber}: ${message.body}")
                }
                // These could not be unsealed, so they have no text or remote number.
                result.result.failed.forEach { failed ->
                    println("${failed.partial.id} unreadable: ${failed.cause.message}")
                }
            }
        }
    }

    suspend fun listPhoneMessagesForConversationId(
        telephonyClient: SudoTelephonyClient,
        conversationId: String,
    ) {
        // One conversation, newest first. Messages carry their conversationId.
        when (
            val result =
                telephonyClient.listPhoneMessagesForConversationId(
                    ListPhoneMessagesForConversationIdInput(conversationId = conversationId),
                )
        ) {
            is ListAPIResult.Success ->
                result.result.items.forEach { message ->
                    println("${message.remotePhoneNumber}: ${message.body}")
                }
            is ListAPIResult.Partial -> {
                result.result.items.forEach { message ->
                    println("${message.remotePhoneNumber}: ${message.body}")
                }
                result.result.failed.forEach { failed ->
                    println("${failed.partial.id} unreadable: ${failed.cause.message}")
                }
            }
        }
    }

    suspend fun deletePhoneMessages(
        telephonyClient: SudoTelephonyClient,
        messageIds: List<String>,
    ) {
        val result = telephonyClient.deletePhoneMessages(DeletePhoneMessagesInput(ids = messageIds))
        // The batch can partially succeed, so check both lists.
        val notDeleted = result.failedIds
    }

    suspend fun updatePhoneMessages(
        telephonyClient: SudoTelephonyClient,
        messageIds: List<String>,
    ) {
        // Mark a conversation as read.
        val result =
            telephonyClient.updatePhoneMessages(
                UpdatePhoneMessagesInput(ids = messageIds, seen = true),
            )
        val notUpdated = result.failedIds
    }

    suspend fun subscribeToPhoneNumbers(telephonyClient: SudoTelephonyClient) {
        telephonyClient.subscribeToPhoneNumbers(
            subscriptionId = "my-subscription",
            subscriber =
                object : PhoneNumberSubscriber {
                    override fun phoneNumberUpdated(phoneNumber: PhoneNumber) {
                        // Reports a provisioning number becoming ACTIVE or FAILED.
                        println("${phoneNumber.phoneNumber} is now ${phoneNumber.state}")
                    }

                    override fun connectionStatusChanged(state: Subscriber.ConnectionState) {
                        // On DISCONNECTED every subscriber is dropped, so subscribe again.
                        println("phone number subscription $state")
                    }
                },
        )
    }

    suspend fun subscribeToPhoneMessages(telephonyClient: SudoTelephonyClient) {
        telephonyClient.subscribeToPhoneMessages(
            subscriptionId = "my-subscription",
            subscriber =
                object : PhoneMessageSubscriber {
                    override fun phoneMessageEvent(event: PhoneMessageEvent) {
                        // The event carries no text; fetch the message to read it.
                        println("message ${event.id} of Sudo ${event.sudoId} was ${event.changeType}")
                    }

                    override fun connectionStatusChanged(state: Subscriber.ConnectionState) {
                        println("phone message subscription $state")
                    }
                },
        )
    }

    suspend fun exportKeys(telephonyClient: SudoTelephonyClient) {
        // The archive holds the private keys that unseal message content, so treat it as a
        // secret: store it encrypted and transfer it over a channel you trust.
        val archive: ByteArray = telephonyClient.exportKeys()
    }

    suspend fun importKeys(
        telephonyClient: SudoTelephonyClient,
        archive: ByteArray,
    ) {
        // Replaces every key on this device, making the numbers' messages readable here.
        telephonyClient.importKeys(archive)
    }
}
