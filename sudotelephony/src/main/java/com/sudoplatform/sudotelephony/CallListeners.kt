package com.sudoplatform.sudotelephony

/**
 * Listener for receiving notifications about new `ActiveVoiceCall` events.
 */
interface ActiveCallListener : TelephonySubscriber {
    /**
     * Notifies the listener that the call has connected
     * @param call The `ActiveVoiceCall`
     */
    fun activeVoiceCallDidConnect(call: ActiveVoiceCall)

    /**
     * Notifies the listener that the call failed to connect
     * @param exception An Exception that occurred
     */
    fun activeVoiceCallDidFailToConnect(exception: Exception)

    /**
     * Notifies the listener that the call has been disconnected
     * @param call The `ActiveVoiceCall`
     * @param exception An optional Exception that is the cause of the disconnect if not null
     */
    fun activeVoiceCallDidDisconnect(call: ActiveVoiceCall, exception: Exception?)

    /**
     * Notifies the listener that the call has been muted or un-muted
     * @param call The `ActiveVoiceCall`
     * @param isMuted Whether outgoing call audio is muted
     */
    fun activeVoiceCallDidChangeMuteState(call: ActiveVoiceCall, isMuted: Boolean)

    /**
     * Notifies the listener that the call audio routing through the speakers has changed
     * @param call The `ActiveVoiceCall`
     * @param isMuted Whether call audio is being routed through the speakers
     */
    fun activeVoiceCallDidChangeSpeakerState(call: ActiveVoiceCall, isOnSpeaker: Boolean)
}

/**
 * Listener for receiving notifications about new `IncomingCall` events.
 */
interface IncomingCallNotificationListener: TelephonySubscriber {
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