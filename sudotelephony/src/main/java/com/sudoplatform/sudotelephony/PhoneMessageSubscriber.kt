package com.sudoplatform.sudotelephony

/**
 * Subscriber for receiving notifications about new `PhoneMessage` objects.
 */
interface PhoneMessageSubscriber {
    /**
     * Connection state of the subscription.
     */
    enum class ConnectionState {

        /**
         * Connected and receiving updates.
         */
        CONNECTED,

        /**
         * Disconnected and won't receive any updates. When disconnected all subscribers will be
         * unsubscribed so the consumer must re-subscribe.
         */
        DISCONNECTED

    }

    /**
     * Notifies the subscriber of a new 'PhoneMessage'.
     *
     * @param phoneMessage new `PhoneMessage`.
     */
    fun phoneMessageReceived(phoneMessage: PhoneMessage)

    /**
     * Notifies the subscriber that the subscription connection state has changed. The subscriber won't be
     * notified of message changes until the connection status changes to [ConnectionState.CONNECTED]. The subscriber will
     * stop receiving message change notifications when the connection state changes to [ConnectionState.DISCONNECTED].
     * @param state connection state.
     */
    fun connectionStatusChanged(state: ConnectionState)

}