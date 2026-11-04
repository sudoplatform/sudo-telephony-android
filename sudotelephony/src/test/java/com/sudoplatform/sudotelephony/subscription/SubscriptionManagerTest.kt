/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.subscription

import com.amplifyframework.api.graphql.GraphQLOperation
import com.sudoplatform.sudotelephony.BaseTests
import com.sudoplatform.sudotelephony.data.EntityDataFactory
import com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers.PhoneNumberTransformer
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SubscriptionManager] through the concrete
 * [PhoneNumberSubscriptionManager]: subscribers are multiplexed onto one watcher, which is
 * cancelled once the last subscriber is gone.
 */
@RunWith(RobolectricTestRunner::class)
class SubscriptionManagerTest : BaseTests() {
    private val instanceUnderTest by before {
        PhoneNumberSubscriptionManager<Any>()
    }

    private val phoneNumber by before {
        PhoneNumberTransformer.fromEntityToApi(EntityDataFactory.phoneNumberEntity())
    }

    @Test
    fun `phoneNumberUpdated() should notify every registered subscriber`() {
        val first = mock<PhoneNumberSubscriber>()
        val second = mock<PhoneNumberSubscriber>()
        instanceUnderTest.replaceSubscriber("sub-1", first)
        instanceUnderTest.replaceSubscriber("sub-2", second)

        instanceUnderTest.phoneNumberUpdated(phoneNumber)

        verify(first).phoneNumberUpdated(phoneNumber)
        verify(second).phoneNumberUpdated(phoneNumber)
    }

    @Test
    fun `replaceSubscriber() should replace a subscriber registered under the same id`() {
        val original = mock<PhoneNumberSubscriber>()
        val replacement = mock<PhoneNumberSubscriber>()
        instanceUnderTest.replaceSubscriber("sub-1", original)
        instanceUnderTest.replaceSubscriber("sub-1", replacement)

        instanceUnderTest.phoneNumberUpdated(phoneNumber)

        verify(replacement).phoneNumberUpdated(phoneNumber)
        verify(original, never()).phoneNumberUpdated(phoneNumber)
    }

    @Test
    fun `connectionStatusChanged() should notify subscribers of CONNECTED and keep them`() {
        val subscriber = mock<PhoneNumberSubscriber>()
        val watcher = mock<GraphQLOperation<Any>>()
        instanceUnderTest.replaceSubscriber("sub-1", subscriber)
        instanceUnderTest.watcher = watcher

        instanceUnderTest.connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)

        verify(subscriber).connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)
        // A healthy connection leaves the watcher in place.
        instanceUnderTest.watcher shouldNotBe null
    }

    @Test
    fun `connectionStatusChanged() should drop every subscriber on DISCONNECTED`() {
        val subscriber = mock<PhoneNumberSubscriber>()
        val watcher = mock<GraphQLOperation<Any>>()
        instanceUnderTest.replaceSubscriber("sub-1", subscriber)
        instanceUnderTest.watcher = watcher

        instanceUnderTest.connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)

        // The subscriber is told, then dropped, so the caller must subscribe again.
        verify(subscriber).connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        verify(watcher).cancel()
        instanceUnderTest.watcher shouldBe null

        instanceUnderTest.phoneNumberUpdated(phoneNumber)
        verify(subscriber, never()).phoneNumberUpdated(phoneNumber)
    }

    @Test
    fun `removeSubscriber() should keep the watcher while other subscribers remain`() {
        val first = mock<PhoneNumberSubscriber>()
        val second = mock<PhoneNumberSubscriber>()
        val watcher = mock<GraphQLOperation<Any>>()
        instanceUnderTest.replaceSubscriber("sub-1", first)
        instanceUnderTest.replaceSubscriber("sub-2", second)
        instanceUnderTest.watcher = watcher

        instanceUnderTest.removeSubscriber("sub-1")

        verify(watcher, never()).cancel()
        instanceUnderTest.watcher shouldNotBe null

        instanceUnderTest.phoneNumberUpdated(phoneNumber)
        verify(second).phoneNumberUpdated(phoneNumber)
        verify(first, never()).phoneNumberUpdated(phoneNumber)
    }

    @Test
    fun `removeSubscriber() should cancel the watcher when the last subscriber goes`() {
        val subscriber = mock<PhoneNumberSubscriber>()
        val watcher = mock<GraphQLOperation<Any>>()
        instanceUnderTest.replaceSubscriber("sub-1", subscriber)
        instanceUnderTest.watcher = watcher

        instanceUnderTest.removeSubscriber("sub-1")

        verify(watcher).cancel()
        instanceUnderTest.watcher shouldBe null
    }

    @Test
    fun `removeSubscriber() should tolerate an unknown id`() {
        val subscriber = mock<PhoneNumberSubscriber>()
        val watcher = mock<GraphQLOperation<Any>>()
        instanceUnderTest.replaceSubscriber("sub-1", subscriber)
        instanceUnderTest.watcher = watcher

        instanceUnderTest.removeSubscriber("never-registered")

        // The registered subscriber is untouched.
        verify(watcher, never()).cancel()
        instanceUnderTest.phoneNumberUpdated(phoneNumber)
        verify(subscriber).phoneNumberUpdated(phoneNumber)
    }

    @Test
    fun `removeAllSubscribers() should cancel the watcher and drop everyone`() {
        val first = mock<PhoneNumberSubscriber>()
        val second = mock<PhoneNumberSubscriber>()
        val watcher = mock<GraphQLOperation<Any>>()
        instanceUnderTest.replaceSubscriber("sub-1", first)
        instanceUnderTest.replaceSubscriber("sub-2", second)
        instanceUnderTest.watcher = watcher

        instanceUnderTest.removeAllSubscribers()

        verify(watcher).cancel()
        instanceUnderTest.watcher shouldBe null

        instanceUnderTest.phoneNumberUpdated(phoneNumber)
        verify(first, never()).phoneNumberUpdated(phoneNumber)
        verify(second, never()).phoneNumberUpdated(phoneNumber)
    }

    @Test
    fun `phoneMessageEvent() should notify every registered message subscriber`() {
        val manager = PhoneMessageSubscriptionManager<Any>()
        val subscriber = mock<PhoneMessageSubscriber>()
        val event = EntityDataFactory.phoneMessageEvent()
        manager.replaceSubscriber("sub-1", subscriber)

        manager.phoneMessageEvent(event)

        verify(subscriber).phoneMessageEvent(event)
    }

    @Test
    fun `replaceSubscriber() should tell a subscriber joining an open subscription it is connected`() {
        val first = mock<PhoneNumberSubscriber>()
        instanceUnderTest.replaceSubscriber("sub-1", first)
        instanceUnderTest.watcher = mock<GraphQLOperation<Any>>()
        instanceUnderTest.connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)

        // The service reports the connection opening once per watcher, so a later subscriber
        // would otherwise never be told and would wait forever.
        val late = mock<PhoneNumberSubscriber>()
        instanceUnderTest.replaceSubscriber("sub-2", late)

        verify(late).connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)
    }

    @Test
    fun `replaceSubscriber() should not claim connected before the subscription opens`() {
        val subscriber = mock<PhoneNumberSubscriber>()

        instanceUnderTest.replaceSubscriber("sub-1", subscriber)

        verify(subscriber, never()).connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)
    }

    @Test
    fun `replaceSubscriber() should not claim connected after a disconnect`() {
        val first = mock<PhoneNumberSubscriber>()
        instanceUnderTest.replaceSubscriber("sub-1", first)
        instanceUnderTest.connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)
        instanceUnderTest.connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)

        val late = mock<PhoneNumberSubscriber>()
        instanceUnderTest.replaceSubscriber("sub-2", late)

        verify(late, never()).connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)
    }

    @Test
    fun `replaceSubscriber() should not claim connected after the last subscriber left`() {
        val first = mock<PhoneNumberSubscriber>()
        instanceUnderTest.replaceSubscriber("sub-1", first)
        instanceUnderTest.watcher = mock<GraphQLOperation<Any>>()
        instanceUnderTest.connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)

        // Removing the last subscriber cancels the watcher, so the next subscriber has to
        // wait for a fresh one to open.
        instanceUnderTest.removeSubscriber("sub-1")

        val late = mock<PhoneNumberSubscriber>()
        instanceUnderTest.replaceSubscriber("sub-2", late)

        verify(late, never()).connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)
    }

    @Test
    fun `removeAllSubscribers() should clear the connected state`() {
        val first = mock<PhoneNumberSubscriber>()
        instanceUnderTest.replaceSubscriber("sub-1", first)
        instanceUnderTest.watcher = mock<GraphQLOperation<Any>>()
        instanceUnderTest.connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)

        instanceUnderTest.removeAllSubscribers()

        val late = mock<PhoneNumberSubscriber>()
        instanceUnderTest.replaceSubscriber("sub-2", late)
        verify(late, never()).connectionStatusChanged(Subscriber.ConnectionState.CONNECTED)
    }
}
