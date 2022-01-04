package com.sudoplatform.sudotelephony.calling

import com.sudoplatform.sudotelephony.calllisteners.ActiveCallListener

/**
 * An incoming call that can be accepted or declined
 */
class IncomingCall() {
    /**
     * The phone number receiving the call
     */
    lateinit var localNumber: String

    /**
     * The phone number the call is coming from
     */
    lateinit var remoteNumber: String

    /**
     * The unique string identifying the call invite
     */
    lateinit var callSid: String

    private var acceptCallback: ((IncomingCall, ActiveCallListener) -> Unit)? = null
    private var declineCallback: ((IncomingCall) -> Unit)? = null

    /**
     * Accepts the call
     * @param listener A listener to receive call lifecycle updates
     */
    fun acceptWithListener(listener: ActiveCallListener) {
        acceptCallback?.let { it(this, listener) }
    }

    /**
     * Declines the call
     */
    fun decline() {
        declineCallback?.let { it(this) }
    }

    constructor(
        localNumber: String,
        remoteNumber: String,
        callSid: String,
        acceptCallback: ((IncomingCall, ActiveCallListener) -> Unit)?,
        declineCallback: ((IncomingCall) -> Unit)?
    ) : this() {
        this.localNumber = localNumber
        this.remoteNumber = remoteNumber
        this.callSid = callSid
        this.acceptCallback = acceptCallback
        this.declineCallback = declineCallback
    }
}
