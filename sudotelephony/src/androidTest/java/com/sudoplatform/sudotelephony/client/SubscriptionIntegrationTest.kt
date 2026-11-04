/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudotelephony.BaseIntegrationTest
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import com.sudoplatform.sudotelephony.subscription.PhoneMessageSubscriber
import com.sudoplatform.sudotelephony.subscription.PhoneNumberSubscriber
import com.sudoplatform.sudotelephony.subscription.Subscriber
import com.sudoplatform.sudotelephony.types.PhoneMessageEvent
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
import com.sudoplatform.sudotelephony.types.inputs.SearchAvailablePhoneNumbersInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test the operation of the subscription APIs against a deployed telephony service.
 *
 * Provisioning a number is an observable change, so the phone number subscription is covered
 * end to end: the subscription is established, a number is provisioned, and the update that
 * reports it becoming ACTIVE is received on the subscriber.
 *
 * The subscription is established once for the whole class rather than per test. Amplify
 * closes the web socket as soon as the last subscriber of a connection is removed, and a
 * subscription re-established while that socket is still closing can be reported as open yet
 * deliver nothing. Subscribing once mirrors how a consumer uses the API for a session and
 * keeps the tests off that race.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionIntegrationTest : BaseIntegrationTest() {
    private val countryCode = optionalArgument("COUNTRY_CODE", "US")
    private val areaCode = optionalArgument("AREA_CODE", "415")

    /** Records what a phone number subscriber is told. */
    class RecordingPhoneNumberSubscriber : PhoneNumberSubscriber {
        val connected = CountDownLatch(1)
        val updates = CopyOnWriteArrayList<PhoneNumber>()
        val states = CopyOnWriteArrayList<Subscriber.ConnectionState>()

        override fun phoneNumberUpdated(phoneNumber: PhoneNumber) {
            updates.add(phoneNumber)
        }

        override fun connectionStatusChanged(state: Subscriber.ConnectionState) {
            states.add(state)
            if (state == Subscriber.ConnectionState.CONNECTED) {
                connected.countDown()
            }
        }

        /**
         * The first recorded update matching [predicate], waiting up to [timeoutSeconds].
         *
         * The service publishes an update per change, so a number's terminal state arrives in
         * a later update than its first; a caller must wait for the state it wants rather
         * than reading the first update.
         */
        fun awaitUpdate(
            timeoutSeconds: Long,
            predicate: (PhoneNumber) -> Boolean,
        ): PhoneNumber? {
            val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
            while (System.currentTimeMillis() < deadline) {
                updates.firstOrNull(predicate)?.let { return it }
                Thread.sleep(500)
            }
            return updates.firstOrNull(predicate)
        }
    }

    /** Records what a phone message subscriber is told. */
    class RecordingPhoneMessageSubscriber : PhoneMessageSubscriber {
        val connected = CountDownLatch(1)
        val events = CopyOnWriteArrayList<PhoneMessageEvent>()

        override fun phoneMessageEvent(event: PhoneMessageEvent) {
            events.add(event)
        }

        override fun connectionStatusChanged(state: Subscriber.ConnectionState) {
            if (state == Subscriber.ConnectionState.CONNECTED) {
                connected.countDown()
            }
        }
    }

    companion object {
        /** How long to wait for the service to deliver a subscription update. */
        private const val UPDATE_TIMEOUT_SECONDS = 90L

        private const val NUMBER_SUBSCRIPTION_ID = "phone-number-subscription"
        private const val MESSAGE_SUBSCRIPTION_ID = "phone-message-subscription"

        /** The subscriber kept for the whole class, so the web socket is never torn down. */
        @JvmStatic
        lateinit var numberSubscriber: RecordingPhoneNumberSubscriber

        @JvmStatic
        lateinit var messageSubscriber: RecordingPhoneMessageSubscriber

        @BeforeClass
        @JvmStatic
        fun subscribeOnce() =
            runBlocking {
                numberSubscriber = RecordingPhoneNumberSubscriber()
                messageSubscriber = RecordingPhoneMessageSubscriber()
                telephonyClient.subscribeToPhoneNumbers(NUMBER_SUBSCRIPTION_ID, numberSubscriber)
                telephonyClient.subscribeToPhoneMessages(MESSAGE_SUBSCRIPTION_ID, messageSubscriber)
            }

        @AfterClass
        @JvmStatic
        fun unsubscribeOnce() {
            telephonyClient.unsubscribeAllFromPhoneNumbers()
            telephonyClient.unsubscribeAllFromPhoneMessages()
        }
    }

    /** Provisions a number, skipping the test when the provider offers none. */
    private suspend fun provisionANumber(): PhoneNumber {
        val search =
            telephonyClient.searchAvailablePhoneNumbers(
                SearchAvailablePhoneNumbersInput(
                    countryCode = countryCode,
                    areaCode = areaCode,
                ),
            )
        assumeTrue(
            "No numbers offered in $countryCode-$areaCode on this instance",
            search.results.isNotEmpty(),
        )

        for (available in search.results.shuffled()) {
            try {
                val number =
                    telephonyClient.provisionPhoneNumber(
                        ProvisionPhoneNumberInput(
                            searchId = search.id,
                            phoneNumber = available.phoneNumber,
                        ),
                    )
                trackForRelease(number.id)
                return number
            } catch (e: SudoTelephonyClient.PhoneNumberException.PhoneNumberUnavailableException) {
                logger.info("${available.phoneNumber} was taken after the search, trying the next")
            }
        }
        throw AssertionError("Every number offered in $countryCode-$areaCode was already taken")
    }

    @Test
    fun subscribeToPhoneNumbersShouldReportTheConnectionOpening() {
        numberSubscriber.connected.await(UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
        numberSubscriber.states.contains(Subscriber.ConnectionState.CONNECTED) shouldBe true
    }

    @Test
    fun subscribeToPhoneMessagesShouldReportTheConnectionOpening() {
        // A test cannot cause an inbound SMS, so only the connection is observable here.
        messageSubscriber.connected.await(UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
        messageSubscriber.events shouldBe emptyList()
    }

    @Test
    fun subscribeToPhoneNumbersShouldDeliverTheNumberBecomingActive() =
        runBlocking {
            // Provisioning completes asynchronously, which is exactly what the subscription
            // exists to report.
            val provisioned = provisionANumber()
            provisioned.state shouldBe PhoneNumberState.PROVISIONING

            // Wait for the update that reports the terminal state, not merely the first one.
            val update =
                numberSubscriber.awaitUpdate(UPDATE_TIMEOUT_SECONDS) {
                    it.id == provisioned.id && it.state == PhoneNumberState.ACTIVE
                }

            update shouldNotBe null
            update!!.phoneNumber shouldBe provisioned.phoneNumber
            update.state shouldBe PhoneNumberState.ACTIVE
            (update.version > provisioned.version) shouldBe true
        }

    @Test
    fun subscribeToPhoneNumbersShouldDeliverToASubscriberJoiningAnOpenSubscription() =
        runBlocking {
            numberSubscriber.connected.await(UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true

            // Joining a subscription that is already open must still report the connection,
            // because the service announces it opening only once per web socket.
            val late = RecordingPhoneNumberSubscriber()
            telephonyClient.subscribeToPhoneNumbers("late-subscriber", late)
            late.connected.await(UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true

            val provisioned = provisionANumber()

            // Both the original and the late subscriber receive the same update.
            numberSubscriber.awaitUpdate(UPDATE_TIMEOUT_SECONDS) { it.id == provisioned.id } shouldNotBe null
            late.awaitUpdate(UPDATE_TIMEOUT_SECONDS) { it.id == provisioned.id } shouldNotBe null

            // Removing one subscriber leaves the subscription up for the others.
            telephonyClient.unsubscribeFromPhoneNumbers("late-subscriber")
        }

    @Test
    fun unsubscribeFromPhoneNumbersShouldStopDeliveryToThatSubscriber() =
        runBlocking {
            val transient = RecordingPhoneNumberSubscriber()
            val id = "transient-${UUID.randomUUID()}"
            telephonyClient.subscribeToPhoneNumbers(id, transient)
            transient.connected.await(UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true

            telephonyClient.unsubscribeFromPhoneNumbers(id)

            // The class-wide subscriber keeps the subscription open, so delivery continues
            // for it while the unsubscribed one hears nothing more.
            val provisioned = provisionANumber()
            numberSubscriber.awaitUpdate(UPDATE_TIMEOUT_SECONDS) { it.id == provisioned.id } shouldNotBe null

            transient.updates.none { it.id == provisioned.id } shouldBe true
        }
}
