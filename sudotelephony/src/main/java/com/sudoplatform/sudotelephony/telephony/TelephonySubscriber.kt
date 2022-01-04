package com.sudoplatform.sudotelephony.telephony

import androidx.annotation.Keep

/**
 * Interface to implement in order to subscribe to Telephony events
 */
interface TelephonySubscriber {
    /**
     * Connection state of the subscription.
     */
    @Keep
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
     * Notifies the subscriber that the subscription connection state has changed. The subscriber won't be
     * notified until the connection status changes to [ConnectionState.CONNECTED]. The subscriber will
     * stop receiving notifications when the connection state changes to [ConnectionState.DISCONNECTED].
     * @param state connection state.
     */
    fun connectionStatusChanged(state: ConnectionState)
}
