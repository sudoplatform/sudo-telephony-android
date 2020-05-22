package com.sudoplatform.sudotelephony

import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall

/**
 * Manages subscriptions for a specific GraphQL subscription.
 */
internal class SubscriptionManager<T> {

    /**
     * Subscribers.
     */
    internal val subscribers: MutableMap<String, PhoneMessageSubscriber> = mutableMapOf()

    /**
     * AppSync subscription watcher.
     */
    internal var watcher: AppSyncSubscriptionCall<T>? = null

    /**
     * Adds or replaces a subscriber with the specified ID.
     *
     * @param id subscriber ID.
     * @param subscriber subscriber to subscribe.
     */
    internal fun replaceSubscriber(id: String, subscriber: PhoneMessageSubscriber) {
        synchronized(this) {
            this.subscribers[id] = subscriber
        }
    }

    /**
     * Removes the subscriber with the specified ID.
     *
     * @param id subscriber ID.
     */
    internal fun removeSubscriber(id: String) {
        synchronized(this) {
            this.subscribers.remove(id)

            if (this.subscribers.isEmpty()) {
                this.watcher?.cancel()
                this.watcher = null
            }
        }
    }

    /**
     * Removes all subscribers.
     */
    internal fun removeAllSubscribers() {
        synchronized(this) {
            this.subscribers.clear()
            this.watcher?.cancel()
            this.watcher = null
        }
    }

    /**
     * Notifies subscribers of a new `PhoneMessage` objects.
     *
     * @param phoneMessage new PhoneMessage.
     */
    internal fun phoneMessageReceived(phoneMessage: PhoneMessage) {
        var subscribersToNotify: ArrayList<PhoneMessageSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify = ArrayList(this.subscribers.values)
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.phoneMessageReceived(phoneMessage)
        }
    }

    /**
     * Processes AppSync subscription connection status change.
     *
     * @param state connection state.
     */
    internal fun connectionStatusChanged(state: PhoneMessageSubscriber.ConnectionState) {
        var subscribersToNotify: ArrayList<PhoneMessageSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify = ArrayList(this.subscribers.values)

            // If the subscription was disconnected then remove all subscribers.
            if (state == PhoneMessageSubscriber.ConnectionState.DISCONNECTED) {
                this.subscribers.clear()
                this.watcher?.cancel()
                this.watcher = null
            }
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.connectionStatusChanged(state)
        }
    }
}