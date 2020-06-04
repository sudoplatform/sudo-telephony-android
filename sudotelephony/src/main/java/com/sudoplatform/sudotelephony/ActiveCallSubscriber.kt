package com.sudoplatform.sudotelephony

/**
 * Subscriber for receiving notifications about new `ActiveVoiceCall` events.
 */
interface ActiveCallSubscriber : TelephonySubscriber {
    /**
     * Notifies the subscriber that the call has been disconnected
     * @param call The `ActiveVoiceCall` that was disconnected
     */
    fun activeVoiceCallDidDisconnect(call: ActiveVoiceCall, exception: Exception?)

    /**
     * Notifies the subscriber that the call has been disconnected
     * @param call The `ActiveVoiceCall`
     * @param isMuted Whether outgoing call audio is muted
     */
    fun activeVoiceCallDidChangeMuteState(call: ActiveVoiceCall, isMuted: Boolean)

    /**
     * Notifies the subscriber that the call has been disconnected
     * @param call The `ActiveVoiceCall`
     * @param isMuted Whether call audio is being routed through the speakers
     */
    fun activeVoiceCallDidChangeSpeakerState(call: ActiveVoiceCall, isOnSpeaker: Boolean)
}
