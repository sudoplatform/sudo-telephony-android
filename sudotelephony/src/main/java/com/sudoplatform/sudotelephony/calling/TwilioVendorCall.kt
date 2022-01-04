package com.sudoplatform.sudotelephony.calling

import android.content.Context
import com.twilio.voice.Call
import com.twilio.voice.CallException
import com.twilio.voice.CallInvite
import com.twilio.voice.ConnectOptions
import com.twilio.voice.Voice

internal class TwilioVendorCall() : CallingVendorCall {
    private lateinit var call: Call
    lateinit var context: Context
    lateinit var accessToken: String
    lateinit var localPhoneNumber: String
    lateinit var remotePhoneNumber: String
    lateinit var connected: (TwilioVendorCall) -> Unit
    lateinit var connectionFailed: (Exception) -> Unit
    lateinit var disconnected: (Exception?) -> Unit

    private fun callListener(vendorCall: TwilioVendorCall): Call.Listener {
        return object : Call.Listener {
            override fun onRinging(call: Call) {
                // TODO
            }

            override fun onConnectFailure(call: Call, error: CallException) {
                connectionFailed(error)
            }

            override fun onConnected(call: Call) {
                connected(vendorCall)
            }

            fun onReconnecting(call: Call, callException: CallException) {
                // TODO
            }

            fun onReconnected(call: Call) {
                // TODO
            }

            override fun onDisconnected(call: Call, error: CallException?) {
                disconnected(error)
            }
        }
    }

    companion object {
        var supportsDTMF: Boolean = true
        var supportsHolding: Boolean = true
        var supportsGrouping: Boolean = false
        var supportsUngrouping: Boolean = false
    }

    override fun disconnect() {
        call.disconnect()
    }

    override var isMuted: Boolean
        get() = call.isMuted
        set(value) {
            call.mute(value)
        }

    override var isOnHold: Boolean
        get() = call.isOnHold
        set(value) {
            call.hold(value)
        }

    override fun playDTMF(digits: String) {
        call.sendDigits(digits)
    }

    // Start an outgoing call
    constructor(
        context: Context,
        accessToken: String,
        localPhoneNumber: String,
        remotePhoneNumber: String,
        connected: (TwilioVendorCall) -> Unit,
        connectionFailed: (Exception) -> Unit,
        disconnected: (Exception?) -> Unit
    ) : this() {
        this.context = context
        this.connected = connected
        this.connectionFailed = connectionFailed
        this.disconnected = disconnected
        val params: Map<String, String> = mapOf(
            "To" to remotePhoneNumber,
            "From" to localPhoneNumber
        )
        val connectOptions = ConnectOptions.Builder(accessToken).params(params).build()
        call = Voice.connect(context, connectOptions, callListener(this))
    }

    // Accept an incoming call
    constructor(
        context: Context,
        callInvite: CallInvite,
        connected: (TwilioVendorCall) -> Unit,
        connectionFailed: (Exception) -> Unit,
        disconnected: (Exception?) -> Unit
    ) : this() {
        this.context = context
        this.connected = connected
        this.connectionFailed = connectionFailed
        this.disconnected = disconnected
        call = callInvite.accept(context, callListener(this))
    }
}
