package com.sudoplatform.sudotelephony.calllisteners

import com.sudoplatform.sudotelephony.calling.IncomingCall
import com.sudoplatform.sudotelephony.telephony.TelephonySubscriber

/**
 * Listener for receiving notifications about new `IncomingCall` events.
 */
interface IncomingCallNotificationListener : TelephonySubscriber {
    /**
     * Notifies the listener that the call was received
     * @param call The `IncomingCall`
     */
    fun incomingCallReceived(call: IncomingCall)

    /**
     * Notifies the listener that the call was canceled
     * @param call The `IncomingCall`
     */
    fun incomingCallCanceled(call: IncomingCall, error: Throwable?)
}
