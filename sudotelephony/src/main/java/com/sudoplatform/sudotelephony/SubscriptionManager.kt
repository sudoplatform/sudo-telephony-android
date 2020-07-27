package com.sudoplatform.sudotelephony

import android.speech.tts.Voice
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall

/**
 * Manages subscriptions for a specific GraphQL subscription.
 */
internal class SubscriptionManager<T> {

    /**
     * Subscribers.
     */
    internal val subscribers: MutableMap<String, TelephonySubscriber> = mutableMapOf()

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
    internal fun replaceSubscriber(id: String, subscriber: TelephonySubscriber) {
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
                val watcher = this.watcher
                this.watcher = null
                watcher?.cancel()
            }
        }
    }

    /**
     * Removes all subscribers.
     */
    internal fun removeAllSubscribers() {
        synchronized(this) {
            this.subscribers.clear()
            val watcher = this.watcher
            this.watcher = null
            watcher?.cancel()
        }
    }

    /**
     * Notifies subscribers of a new `PhoneMessage` objects.
     *
     * @param phoneMessage the newly received `PhoneMessage`
     */
    internal fun phoneMessageReceived(phoneMessage: PhoneMessage) {
        var subscribersToNotify: ArrayList<PhoneMessageSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            val allSubscribers = ArrayList(this.subscribers.values)
            subscribersToNotify = ArrayList(allSubscribers.filterIsInstance<PhoneMessageSubscriber>())
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
    internal fun connectionStatusChanged(state: TelephonySubscriber.ConnectionState) {
        var subscribersToNotify: ArrayList<TelephonySubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            subscribersToNotify = ArrayList(this.subscribers.values)

            // If the subscription was disconnected then remove all subscribers.
            if (state == TelephonySubscriber.ConnectionState.DISCONNECTED) {
                this.subscribers.clear()
                val watcher = this.watcher
                this.watcher = null
                watcher?.cancel()
            }
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.connectionStatusChanged(state)
        }
    }

    /**
     * Notifies subscribers of a new `CallRecord` objects.
     *
     * @param callRecord the newly received `CallRecord`
     */
    internal fun callRecordReceived(callRecord: CallRecord) {
        var subscribersToNotify: ArrayList<CallRecordSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            val allSubscribers = ArrayList(this.subscribers.values)
            subscribersToNotify = ArrayList(allSubscribers.filterIsInstance<CallRecordSubscriber>())
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.callRecordReceived(callRecord)
        }
    }

    /**
     * Notifies subscribers of an updated `Voicemail` object.
     */
    internal fun voicemailUpdated(voicemail: Voicemail) {
        var subscribersToNotify: ArrayList<VoicemailSubscriber>
        synchronized(this) {
            // Take a copy of the subscribers to notify in synchronized block
            // but notify outside the block to avoid deadlock.
            val allSubscribers = ArrayList(this.subscribers.values)
            subscribersToNotify = ArrayList(allSubscribers.filterIsInstance<VoicemailSubscriber>())
        }

        // Notify subscribers.
        for (subscriber in subscribersToNotify) {
            subscriber.voicemailUpdated(voicemail)
        }
    }
}
