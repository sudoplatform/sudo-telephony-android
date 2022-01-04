package com.sudoplatform.sudotelephony.messages

import com.sudoplatform.sudotelephony.telephony.TelephonySubscriber

/**
 * Subscriber for receiving notifications about new `PhoneMessage` objects.
 */
interface PhoneMessageSubscriber : TelephonySubscriber {

    /**
     * Notifies the subscriber of a new 'PhoneMessage'.
     *
     * @param phoneMessage new `PhoneMessage`.
     */
    fun phoneMessageReceived(phoneMessage: PhoneMessage)
}
