package com.sudoplatform.sudotelephony

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudotelephony.type.CreateVoiceCallInput
import com.twilio.voice.Call
import com.twilio.voice.CallException
import com.twilio.voice.ConnectOptions
import com.twilio.voice.Voice
import java.lang.AssertionError
import java.util.*

/**
 * Manages the vendor context for a call.
 */
interface CallingVendorCall {
    // Static variables for a vendor call
    companion object {
        /**
         * A Boolean value that indicates whether this vendor's calls can send DTMF (dual tone multifrequency) tones via hard pause digits or in-call keypad entries.
         * If false, `playDTMF` will never be called.
         */
        var supportsDTMF: Boolean = false

        /**
         * A Boolean value that indicates whether this vendor's calls can be placed on hold or removed from hold.
         * If false, `isOnHold` will never be accessed.
         */
        var supportsHolding: Boolean = false

        /**
         * A Boolean value that indicates whether this vendor's calls can be grouped with other calls.
         */
        var supportsGrouping: Boolean = false

        /**
         * A Boolean value that indicates whether this vendor's calls can be ungrouped from other calls.
         */
        var supportsUngrouping: Boolean = false
    }

    /**
     * Disconnects this call.
     */
    fun disconnect()

    /**
     * Indicates if this call's outgoing audio should be muted.
     */
    var isMuted: Boolean

    /**
     * Indicates if this call should be placed on hold.
     */
    var isOnHold: Boolean

    /**
     * Plays a string of DTMF tones over this call.
     */
    fun playDTMF(digits: String)
}

class TwilioVendorCall(
    val context: Context,
    val callRecord: CallRecord,
    val connected: (TwilioVendorCall) -> Unit,
    val connectionFailed: (Exception) -> Unit,
    val disconnected: (Exception?) -> Unit
): CallingVendorCall {
    private lateinit var call: Call

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
        set(value) { call.mute(value) }

    override var isOnHold: Boolean
        get() = call.isOnHold
        set(value) { call.hold(value) }

    override fun playDTMF(digits: String) {
        call.sendDigits(digits)
    }

    init {
        val params: Map<String, String> = mapOf("To" to callRecord.remotePhoneNumber, "From" to callRecord.localPhoneNumber)
        val connectOptions = ConnectOptions.Builder(callRecord.accessToken).params(params).build()
        call = Voice.connect(context, connectOptions, callListener(this))
    }
}

// TODO: This will eventually be the GraphQL response type from `createVoiceCall`.
data class CallRecord(val accessToken: String, val localPhoneNumber: String, val remotePhoneNumber: String)

/**
 * An object representing an active voice call. It is used to monitor call events as well as accept input for muting, disconnecting and placing the call on speaker.
 */
class ActiveVoiceCall(val context: Context, val callRecord: CallRecord, val callId: UUID, val vendorCall: CallingVendorCall) {
    /**
     * The E164-formatted local phone number participating in this voice call.
     */
    val localPhoneNumber = callRecord.localPhoneNumber

    /**
     * The E164-formatted remote phone number participating in this voice call.
     */
    val remotePhoneNumber = callRecord.remotePhoneNumber

    internal val subscribers: MutableList<ActiveCallSubscriber> = mutableListOf()

    /**
     * Whether outgoing call audio is muted
     */
    var isMuted: Boolean = false
        internal set(value) { subscribers.forEach { it.activeVoiceCallDidChangeMuteState(this, value) } }

    /**
     * Whether call audio is being routed through the speakers
     */
    var isOnSpeaker: Boolean = false
        internal set(value) { subscribers.forEach { it.activeVoiceCallDidChangeSpeakerState(this, value) } }

    /**
     * Disconnects the active call.
     */
    fun disconnect(exception: Exception?) {
        vendorCall.disconnect()
    }

    /**
     * Sets whether outgoing call audio should be muted.
     * @param muted If true, outgoing call audio should be muted.
     */
    fun setMuted(muted: Boolean) {
        vendorCall.isMuted = muted
        isMuted = muted
    }

    /**
     * Sets whether outgoing call audio should be routed through the speakers.
     * @param speaker If true, audio should be routed through the speakers.
     */
    fun setAudioOutputToSpeaker(speaker: Boolean) {
        // TODO implement
    }

    /**
     * Subscribe to active call events
     * @param subscriber An `ActiveCallSubscriber` object that will receive call events
     */
    fun subscribe(subscriber: ActiveCallSubscriber) {
        subscribers.add(subscriber)
    }
}

/**
 * Calling feature
 * @param context The application context
 * @param graphQLClient GraphQL client for communicating with the Sudo service.
 */
class SudoTelephonyCalling(val context: Context, val graphQLClient: AWSAppSyncClient) {
    private val calls: MutableMap<UUID, ActiveVoiceCall> = mutableMapOf()

    /**
     * Creates a call from a provisioned phone number to another number.
     * @param localNumber: PhoneNumber instance to call from.
     * @param remoteNumber: The E164 formatted phone number of the recipient. For example: "+14155552671".
     * @param: callback: Completion callback providing an interface to control the resulting voice call or an error if there was a failure.
     */
    fun createVoiceCall(localNumber: PhoneNumber, remoteNumber: String, callback: (Result<ActiveVoiceCall>) -> Unit) {
        val input = CreateVoiceCallInput.builder()
            .from(localNumber.phoneNumber)
            .to(remoteNumber)
            .build()
        val mutation = CreateVoiceCallMutation.builder()
            .input(input)
            .build()
        graphQLClient.mutate(mutation)
            .enqueue(object : GraphQLCall.Callback<CreateVoiceCallMutation.Data>() {
                override fun onResponse(response: Response<CreateVoiceCallMutation.Data>) {
                    val token = response.data()?.createVoiceCall()?.token()
                    token?.let { accessToken ->
                        val callId = UUID.randomUUID()
                        val callRecord = CallRecord(accessToken, localNumber.phoneNumber, remoteNumber)
                        val connected = { vendorCall: TwilioVendorCall ->
                            val activeCall = ActiveVoiceCall(context, callRecord, callId, vendorCall)
                            calls[callId] = activeCall
                            callback.runOnUiThread()(Result.Success(activeCall))
                        }

                        val connectionFailed: ((Exception) -> Unit) = { e: Exception ->
                            callback.runOnUiThread()(Result.Error(TelephonyCallingFailedToConnectException(e)))
                        }

                        val disconnected: ((Exception?) -> Unit) = { e: Exception? ->
                            calls.remove(callId)?.let { call ->
                                call.subscribers.forEach { subscriber ->
                                    subscriber.activeVoiceCallDidDisconnect(call, e?.let { TelephonyCallingDisconnectedException(it) })
                                    subscriber.connectionStatusChanged(TelephonySubscriber.ConnectionState.DISCONNECTED)
                                }
                            }
                        }
                        // instantiate the vendor call
                        // Twilio might throw an exception if the RECORD_AUDIO permission is not approved
                        try {
                            TwilioVendorCall(context, callRecord, connected, connectionFailed, disconnected)
                        } catch (e: Exception) {
                            callback.runOnUiThread()(Result.Error(TelephonyCallingFailedToStartOutgoingCallException(e)))
                        }
                    } ?: run {
                        callback.runOnUiThread()(Result.Error(TelephonyCallingFailedToAuthorizeOutgoingCallException()))
                    }
                }

                override fun onFailure(e: ApolloException) {
                    callback.runOnUiThread()(Result.Error(TelephonyCallingFailedToAuthorizeOutgoingCallException(e)))
                }
            })
    }
}

