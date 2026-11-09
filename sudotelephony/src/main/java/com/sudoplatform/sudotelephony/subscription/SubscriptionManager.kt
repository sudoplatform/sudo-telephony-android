/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.subscription

import com.amplifyframework.api.graphql.GraphQLOperation

/**
 * Manages subscribers for a specific GraphQL subscription.
 *
 * A single AppSync watcher is multiplexed to every subscriber registered with the manager:
 * it is established when the first subscriber is added and cancelled when the last one is
 * removed.
 */
internal open class SubscriptionManager<T, S : Subscriber> {
    /** Subscribers keyed by subscription identifier. */
    private val subscribers: MutableMap<String, S> = mutableMapOf()

    /** The GraphQL subscription watcher. `null` when there is no active subscription. */
    internal var watcher: GraphQLOperation<T>? = null

    /**
     * Whether the watcher has reported the subscription established.
     *
     * The service reports the connection opening once per watcher, so this is remembered in
     * order to tell a subscriber that joins an already-open subscription.
     */
    private var connected: Boolean = false

    /** The registered subscribers. */
    protected fun getSubscribers(): MutableMap<String, S> = this.subscribers

    /**
     * Adds or replaces the subscriber registered under [id].
     *
     * A subscriber that joins while the subscription is already open is told so immediately,
     * because the watcher only reports the connection opening once and would otherwise leave
     * this subscriber waiting for a notification that never comes.
     *
     * @param id [String] Identifier of the subscriber.
     * @param subscriber [S] Subscriber to register.
     */
    internal fun replaceSubscriber(
        id: String,
        subscriber: S,
    ) {
        val alreadyConnected: Boolean
        synchronized(this) {
            subscribers[id] = subscriber
            alreadyConnected = connected
        }

        if (alreadyConnected) {
            subscriber.connectionStatusChanged(Subscriber.ConnectionState.Connected)
        }
    }

    /**
     * Removes the subscriber registered under [id], cancelling the watcher once the last
     * subscriber is gone.
     *
     * @param id [String] Identifier of the subscriber.
     */
    internal fun removeSubscriber(id: String) {
        synchronized(this) {
            subscribers.remove(id)

            if (subscribers.isEmpty()) {
                watcher?.cancel()
                watcher = null
                connected = false
            }
        }
    }

    /** Removes every subscriber and cancels the watcher. */
    internal fun removeAllSubscribers() {
        synchronized(this) {
            subscribers.clear()
            watcher?.cancel()
            watcher = null
            connected = false
        }
    }

    /**
     * Handles a connection state change, dropping every subscriber on disconnect and
     * notifying them all.
     *
     * @param state [Subscriber.ConnectionState] The connection state.
     */
    internal fun connectionStatusChanged(state: Subscriber.ConnectionState) {
        val subscribersToNotify: ArrayList<S>
        synchronized(this) {
            // Copy inside the lock but notify outside it, so a subscriber callback that
            // re-enters the manager cannot deadlock.
            subscribersToNotify = ArrayList(subscribers.values)

            connected = state == Subscriber.ConnectionState.Connected

            if (state is Subscriber.ConnectionState.Disconnected) {
                subscribers.clear()
                watcher?.cancel()
                watcher = null
            }
        }

        for (subscriber in subscribersToNotify) {
            subscriber.connectionStatusChanged(state)
        }
    }
}
