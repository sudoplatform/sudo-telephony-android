package com.sudoplatform.sudotelephony

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