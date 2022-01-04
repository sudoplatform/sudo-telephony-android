package com.sudoplatform.sudotelephony

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sudoplatform.sudotelephony.exceptions.SudoTelephonyException
import com.sudoplatform.sudotelephony.messages.PhoneMessage
import com.sudoplatform.sudotelephony.messages.PhoneMessageSubscriber
import com.sudoplatform.sudotelephony.telephony.TelephonySubscriber
import com.sudoplatform.sudotelephony.graphql.type.Direction
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberState
import com.sudoplatform.sudotelephony.phonenumbers.PhoneNumber
import com.sudoplatform.sudotelephony.telephony.SudoTelephonyClient
import junit.framework.Assert.fail
import junit.framework.AssertionFailedError
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MySubscriber : PhoneMessageSubscriber {
    val messageReceivedLatch = CountDownLatch(1)
    val connectionStatusChangedLatch = CountDownLatch(1)

    var connectionState: TelephonySubscriber.ConnectionState? = null
    var phoneMessage: PhoneMessage? = null

    override fun connectionStatusChanged(state: TelephonySubscriber.ConnectionState) {
        this.connectionState = state
        this.connectionStatusChangedLatch.countDown()
    }

    override fun phoneMessageReceived(phoneMessage: PhoneMessage) {
        this.phoneMessage = phoneMessage
        this.messageReceivedLatch.countDown()
    }
}

@RunWith(AndroidJUnit4::class)
class TelephonySDKInstrumentedIntegrationTest : BaseIntegrationTest() {

    private lateinit var telephony: SudoTelephonyClient
    private var destinationPhoneNumber: String? = null
    private var destinationPhoneNumber2: String? = null

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private val provisionedNumbers: MutableList<PhoneNumber> = mutableListOf()

    @Before
    fun setup() = runBlocking {
        telephony = SudoTelephonyClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setSudoProfilesClient(sudoClient)
            .build()

        if (userClient.isRegistered()) {
            deleteAllSudos()
            telephony.reset()
            userClient.reset()
            sudoClient.reset()
        }
    }

    @After
    fun teardown() = runBlocking {
        telephony.unsubscribeFromPhoneMessages(null)

        provisionedNumbers.forEach {
            telephony.deletePhoneNumber(it.phoneNumber)
        }

        destinationPhoneNumber = null
        destinationPhoneNumber2 = null

        deleteAllSudos()
        telephony.reset()
        userClient.reset()
        sudoClient.reset()
        keyManager.removeAllKeys()
    }

    @Test
    fun testTelephonySearchCanGetNumbers() = runBlocking<Unit> {
        try {
            signInAndRegisterUser()
        } catch (error: AssertionError) {
            Assert.fail("Sign in failed.")
        }

        try {
            val availableNumbers = telephony.searchAvailablePhoneNumbers("US")
            assertThat(availableNumbers.numbers.size).isGreaterThan(1)
        } catch (e: Throwable) {
            fail("Should have succeeded")
        }
    }

    @Test
    fun testSearchByAreaCode() = runBlocking<Unit> {
        try {
            signInAndRegisterUser()
        } catch (error: AssertionError) {
            Assert.fail("Sign in failed.")
        }

        val areaCode = "415"

        try {
            val availableNumbers = telephony.searchAvailablePhoneNumbers("US", areaCode)
            assertThat(availableNumbers.numbers.size).isAtLeast(0)

            availableNumbers.numbers
                .forEach { assertThat(it).startsWith("+1$areaCode") }
        } catch (e: Throwable) {
            fail("Should have succeeded")
        }
    }

    @Test
    fun testSearchEmptyPrefix() = runBlocking<Unit> {
        try {
            signInAndRegisterUser()
        } catch (error: AssertionError) {
            Assert.fail("Sign in failed.")
        }

        try {
            val availableNumbers = telephony.searchAvailablePhoneNumbers("US", "")
            assertThat(availableNumbers.numbers.size).isAtLeast(1)
        } catch (e: Throwable) {
            fail("Should have succeeded")
        }
    }

    @Test
    fun testSearchByGPS() = runBlocking<Unit> {
        try {
            signInAndRegisterUser()
        } catch (error: AssertionError) {
            Assert.fail("Sign in failed.")
        }

        // san francisco
        val latitude = "37.23"
        val longitude = "-122.0"

        try {
            val availableNumbers = telephony.searchAvailablePhoneNumbers("US", latitude, longitude)
            assertThat(availableNumbers.numbers.count()).isAtLeast(0)
            availableNumbers.numbers
                .forEach { assertThat(it).startsWith("+1") }
        } catch (e: Throwable) {
            fail("Should have succeeded")
        }
    }

    @Test
    fun testThatSearchWithInvalidCountryReturnsAnError() = runBlocking<Unit> {
        try {
            signInAndRegisterUser()
        } catch (error: AssertionError) {
            Assert.fail("Sign in failed: $error")
        }

        try {
            val availableNumbers = telephony.searchAvailablePhoneNumbers("")
            fail("Should fail to search for available numbers")
        } catch (e: Throwable) {
            assertThat(e is SudoTelephonyException.InvalidCountryCodeException)
        }
    }

    @Test
    fun testThatSearchWithUnsupportedCountryReturnsAnError() = runBlocking<Unit> {
        try {
            signInAndRegisterUser()
        } catch (error: AssertionError) {
            Assert.fail("Sign in failed.")
        }

        try {
            telephony.searchAvailablePhoneNumbers("MX")
            fail("Should fail to search for available numbers")
        } catch (e: Throwable) {
            assertThat(e is SudoTelephonyException.UnsupportedCountryCodeException)
        }
    }

    @Test
    fun testGetSupportedCountries() = runBlocking<Unit> {
        try {
            signInAndRegisterUser()
        } catch (error: AssertionError) {
            Assert.fail("Sign in failed.")
        }

        val supportedCountries = telephony.getSupportedCountries()
        assertThat(supportedCountries.countries.size).isGreaterThan(0)
    }

    @Test
    fun testPhoneNumberProvision() = runBlocking<Unit> {
        try {
            signInAndRegisterUser()
            createSudo()
        } catch (error: AssertionError) {
            Assert.fail("Create Sudo Failed: $error")
        }

        val countryCode = "ZZ"

        // first, search for some numbers
        try {
            val availableNumbers = telephony.searchAvailablePhoneNumbers(countryCode)
            assertThat(availableNumbers.numbers.size).isGreaterThan(0)
            val phoneNumber = telephony.provisionPhoneNumber(
                countryCode,
                availableNumbers.numbers.first(),
                createdSudoId!!
            )
            if (countryCode == "ZZ") {
                destinationPhoneNumber = "+11111111111"
                destinationPhoneNumber2 = "+14444444444"
            } else {
                destinationPhoneNumber = "+13857070154"
                destinationPhoneNumber2 = "+14444444444"
            }
            provisionedNumbers += phoneNumber
            assertEquals(phoneNumber.state, PhoneNumberState.COMPLETE)
        } catch (e: Throwable) {
            throw AssertionFailedError("Provision should have succeeded: $e")
        }
    }

    @Test
    fun testPhoneNumberDeletion() = runBlocking<Unit> {
        try {
            testPhoneNumberProvision()
        } catch (error: AssertionError) {
            Assert.fail("Provisioning failed")
        }

        val provisionedNumber = provisionedNumbers.first()
        val deletedNumber = telephony.deletePhoneNumber(provisionedNumber.phoneNumber)
        assertTrue(provisionedNumbers.contains(deletedNumber))
        provisionedNumbers.remove(deletedNumber)
        assertFalse(provisionedNumbers.contains(deletedNumber))
    }

    @Test
    fun testGetAllPhoneNumbers_emptyListWhenNoNumbersHaveBeenProvisioned() = runBlocking {
        try {
            signInAndRegisterUser()
        } catch (error: AssertionError) {
            Assert.fail("Sign in failed: $error")
        }

        val phoneNumbers = telephony.listPhoneNumbers(null, null, null)
        assertThat(phoneNumbers.items).isEmpty()
    }

    @Test
    fun testGetAllPhoneNumbers_hasTheProvisionedNumber() = runBlocking<Unit> {
        try {
            testPhoneNumberProvision()
        } catch (error: AssertionError) {
            Assert.fail("Provisioning failed: $error")
        }

        val provisionedNumber = provisionedNumbers.first()

        val phoneNumbers = telephony.listPhoneNumbers(null, null, null)
        assertThat(phoneNumbers.items).hasSize(1)

        val number = phoneNumbers.items.first()
        assertThat(number.phoneNumber).isEqualTo(provisionedNumber.phoneNumber)
    }

    @Test
    fun testListPhoneNumbers_filterBySudo() = runBlocking<Unit> {
        try {
            testPhoneNumberProvision()
        } catch (error: AssertionError) {
            Assert.fail("Provisioning failed: $error")
        }

        val provisionedNumber = provisionedNumbers.first()

        val phoneNumbers = telephony.listPhoneNumbers(createdSudoId, null, null)
        assertThat(phoneNumbers.items).hasSize(1)

        val number = phoneNumbers.items.first()
        assertThat(number.phoneNumber).isEqualTo(provisionedNumber.phoneNumber)
    }

    @Test
    fun testListPhoneNumbers_emptyListWithInvalidSudo() = runBlocking<Unit> {
        try {
            testPhoneNumberProvision()
        } catch (error: AssertionError) {
            Assert.fail("Provisioning failed: $error")
        }

        val phoneNumbers = telephony.listPhoneNumbers("wrongSudoId", null, null)
        assertThat(phoneNumbers.items).isEmpty()
    }

    @Test
    fun testGetPhoneNumber_errorWhenNone() = runBlocking {
        try {
            signInAndRegisterUser()
        } catch (error: AssertionError) {
            Assert.fail("Sign in failed: $error")
        }

        try {
            telephony.getPhoneNumber("+11231234")
            fail("getPhoneNumber should fail")
        } catch (e: Throwable) {
            assertTrue(e is SudoTelephonyException.GetPhoneNumberException)
        }
    }

    @Test
    fun testGetPhoneNumber_returnsThePhoneNumber() = runBlocking<Unit> {
        try {
            testPhoneNumberProvision()
        } catch (error: AssertionError) {
            Assert.fail("Provisioning failed: $error")
        }

        val provisioned = provisionedNumbers.first()

        val phoneNumber = telephony.getPhoneNumber(provisioned.id)
        assertThat(phoneNumber).isEqualTo(provisioned)
    }

    @Test
    fun testSendSMSMessage() = runBlocking<Unit> {
        try {
            testPhoneNumberProvision()
        } catch (error: AssertionError) {
            Assert.fail("Provisioning failed: $error")
        }

        val subscriber = MySubscriber()
        telephony.subscribeToMessages(subscriber, null)
        // Wait a bit more for the subscription to fully initialize since it seems AppSync
        // actually returns control to us before the subscription is fully initialized.
        Thread.sleep(5000)
        // Wait for subscription to connect
        subscriber.connectionStatusChangedLatch.await(30, TimeUnit.SECONDS)
        assertEquals(TelephonySubscriber.ConnectionState.CONNECTED, subscriber.connectionState)

        val provisionedNumber = provisionedNumbers.first()
        assertEquals(provisionedNumber.state, PhoneNumberState.COMPLETE)
        if (destinationPhoneNumber == null) {
            fail("Destination phone number not configured")
        }

        val sentMessage = telephony.sendSMSMessage(
            provisionedNumber,
            destinationPhoneNumber!!,
            "Test Message"
        )

        assertEquals(sentMessage.direction, Direction.OUTBOUND)

        subscriber.messageReceivedLatch.await(60, TimeUnit.SECONDS)
        assertFalse(subscriber.phoneMessage?.id.isNullOrEmpty())
        assertEquals(subscriber.phoneMessage?.body, "Test Message")
    }

    @Test
    fun testSendMMSMessage() = runBlocking<Unit> {
        try {
            testPhoneNumberProvision()
        } catch (error: AssertionError) {
            Assert.fail("Provisioning failed: $error")
        }
        val provisionedNumber = this@TelephonySDKInstrumentedIntegrationTest
            .provisionedNumbers.first()

        assertEquals(provisionedNumber.state, PhoneNumberState.COMPLETE)
        if (destinationPhoneNumber == null) {
            fail("Destination phone number not configured")
        }

        val subscriber = MySubscriber()
        telephony.subscribeToMessages(subscriber, null)

        // Wait a bit more for the subscription to fully initialize since it seems AppSync
        // actually returns control to us before the subscription is fully initialized.
        Thread.sleep(5000)

        // Wait for subscription to connect
        subscriber.connectionStatusChangedLatch.await(30, TimeUnit.SECONDS)
        assertEquals(TelephonySubscriber.ConnectionState.CONNECTED, subscriber.connectionState)

        val testImageData = application.assets.open("test_image.png").readBytes()

        val file = File("test_image")
        val tmpFile = File.createTempFile(file.name, ".png")
        val fos = FileOutputStream(tmpFile)
        fos.write(testImageData)
        val url = tmpFile.toURL()

        val sentMessage = telephony.sendMMSMessage(
            provisionedNumber,
            destinationPhoneNumber!!,
            "Test Message",
            url
        )
        assertEquals(sentMessage.direction, Direction.OUTBOUND)
        val downloadResult = telephony.downloadData(sentMessage.media.first())

        assertNotNull(downloadResult)

        subscriber.messageReceivedLatch.await(60, TimeUnit.SECONDS)
        assertFalse(subscriber.phoneMessage?.id.isNullOrEmpty())
        assertEquals(subscriber.phoneMessage?.body, "Test Message")
    }

    @Test
    fun testGetSentMessage() = runBlocking<Unit> {
        try {
            testPhoneNumberProvision()
        } catch (error: AssertionError) {
            Assert.fail("Provisioning failed: $error")
        }

        val provisionedNumber = provisionedNumbers.first()
        assertEquals(provisionedNumber.state, PhoneNumberState.COMPLETE)
        if (destinationPhoneNumber == null) {
            fail("Destination phone number not configured")
        }

        val sentMessage = telephony.sendSMSMessage(
            provisionedNumber,
            destinationPhoneNumber!!,
            "Test Message"
        )

        assertEquals(sentMessage.direction, Direction.OUTBOUND)
        assertEquals(sentMessage.body, "Test Message")
        assertEquals(sentMessage.local, provisionedNumber.phoneNumber)
        assertEquals(sentMessage.remote, destinationPhoneNumber)
    }

    @Test
    fun testGetMessages() = runBlocking<Unit> {
        try {
            testPhoneNumberProvision()
        } catch (error: AssertionError) {
            Assert.fail("Provisioning failed: $error")
        }

        val provisionedNumber = this@TelephonySDKInstrumentedIntegrationTest
            .provisionedNumbers.first()

        assertEquals(provisionedNumber.state, PhoneNumberState.COMPLETE)
        if (destinationPhoneNumber == null) {
            fail("Destination phone number not configured")
        }

        val sentMessage = telephony.sendSMSMessage(
            provisionedNumber,
            destinationPhoneNumber!!,
            "Test Message"
        )

        assertEquals(sentMessage.direction, Direction.OUTBOUND)

        val sentMessage2 = telephony.sendSMSMessage(
            provisionedNumber,
            destinationPhoneNumber!!,
            "Test Message 2"
        )
        assertEquals(sentMessage2.direction, Direction.OUTBOUND)

        // Test getting the list of messages
        val recipient = destinationPhoneNumber!!
        val listToken = telephony.getMessages(
            provisionedNumber,
            recipient,
            null,
            null
        )

        val messages = listToken.items
        assertEquals(messages.size, 2)
        // check for contains instead of checking that the order is correct
        // because it isn't guaranteed that they'll send in order
        val messageIds = messages.map { it.id }
        assertTrue(messageIds.contains(sentMessage.id))
        assertTrue(messageIds.contains(sentMessage2.id))
    }

    @Test
    fun testGetMessagesPaging() = runBlocking<Unit> {
        try {
            testPhoneNumberProvision()
        } catch (error: AssertionError) {
            Assert.fail("Provisioning failed: $error")
        }

        val provisionedNumber = this@TelephonySDKInstrumentedIntegrationTest
            .provisionedNumbers.first()
        assertEquals(provisionedNumber.state, PhoneNumberState.COMPLETE)
        if (destinationPhoneNumber == null) {
            fail("Destination phone number not configured")
        }
        val testMessageOne = "Test Message"
        val testMessageTwo = "Test Message 2"
        val sentMessage = telephony.sendSMSMessage(
            provisionedNumber,
            destinationPhoneNumber!!,
            testMessageOne
        )
        assertEquals(sentMessage.direction, Direction.OUTBOUND)

        val sentMessage2 = telephony.sendSMSMessage(
            provisionedNumber,
            destinationPhoneNumber!!,
            testMessageTwo
        )
        assertEquals(sentMessage2.direction, Direction.OUTBOUND)

        // Test getting the list of messages
        val recipient = destinationPhoneNumber!!
        val listToken = telephony.getMessages(
            provisionedNumber,
            recipient,
            1,
            null
        )
        val messages = listToken.items
        val nextToken = listToken.nextToken

        assertEquals(messages.size, 1)

        val firstMessage = messages[0]

        // use the nextToken to get the next message
        val listToken2 = telephony.getMessages(
            provisionedNumber,
            recipient,
            1,
            nextToken
        )
        assertEquals(listToken2.items.size, 1)
        val secondMessage = listToken2.items[0]

        assertNotEquals(firstMessage, secondMessage)
    }

    @Test
    fun tetGetConversation() = runBlocking<Unit> {
        try {
            // send first message
            testSendSMSMessage()
        } catch (error: AssertionError) {
            Assert.fail("Send SMS Message failed: $error")
        }

        val provisionedNumber = provisionedNumbers.first()
        assertEquals(provisionedNumber.state, PhoneNumberState.COMPLETE)
        if (destinationPhoneNumber == null) {
            fail("Destination phone number not configured")
        }

        val conversation = telephony.getConversation(provisionedNumbers.first(), destinationPhoneNumber!!)
        assertNotNull(conversation.latestPhoneMessage)
    }

    @Test
    fun testGetConversations() = runBlocking<Unit> {
        // send first message
        testSendSMSMessage()
        // send second message
        assertEquals(provisionedNumbers.first().state, PhoneNumberState.COMPLETE)
        if (destinationPhoneNumber2 == null) {
            fail("Destination phone number not configured")
        }
        val sentMessage = telephony.sendSMSMessage(
            provisionedNumbers.first(),
            destinationPhoneNumber2!!,
            "Test message 2"
        )
        assertEquals(sentMessage.direction, Direction.OUTBOUND)

        val conversationsListToken = telephony.getConversations(provisionedNumbers.first(), null, null)
        assertNotNull(conversationsListToken.items)
        assertEquals(conversationsListToken.items.size, 2)
        assertEquals(
            conversationsListToken.items.first().latestPhoneMessage?.local,
            provisionedNumbers.first().phoneNumber
        )
    }
}
