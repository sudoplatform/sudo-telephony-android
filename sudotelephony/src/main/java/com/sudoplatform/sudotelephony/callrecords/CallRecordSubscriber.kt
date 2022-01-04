package com.sudoplatform.sudotelephony.callrecords

import com.sudoplatform.sudotelephony.telephony.TelephonySubscriber

/**
 * Subscriber for receiving notifications about new `CallRecord` objects.
 */
interface CallRecordSubscriber : TelephonySubscriber {

    /**
     * Notifies the subscriber of a new 'CallRecord'.
     *
     * @param callRecord new `CallRecord`.
     */
    fun callRecordReceived(callRecord: CallRecord)
}
