package com.sudoplatform.sudotelephony.voicemail

import com.sudoplatform.sudotelephony.telephony.TelephonySubscriber

/**
 * Subscriber for receiving notifications about new `Voicemail` updates.
 */
interface VoicemailSubscriber : TelephonySubscriber {

    /**
     * Notifies the subscriber of a new 'Voicemail' update.
     *
     * @param voicemail new `Voicemail`.
     */
    fun voicemailUpdated(voicemail: Voicemail)
}
