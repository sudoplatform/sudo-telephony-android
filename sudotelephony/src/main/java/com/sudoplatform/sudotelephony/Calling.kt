package com.sudoplatform.sudotelephony

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudotelephony.type.CreateVoiceCallInput
import com.sudoplatform.sudotelephony.type.DeviceRegistrationInput
import com.sudoplatform.sudotelephony.type.PushNotificationService
import com.twilio.voice.*
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

internal class TwilioVendorCall(): CallingVendorCall {
    private lateinit var call: Call
    lateinit var context: Context
    lateinit var callRecord: CallRecord
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
        set(value) { call.mute(value) }

    override var isOnHold: Boolean
        get() = call.isOnHold
        set(value) { call.hold(value) }

    override fun playDTMF(digits: String) {
        call.sendDigits(digits)
    }

    // Start an outgoing call
    constructor(context: Context,
                callRecord: CallRecord,
                connected: (TwilioVendorCall) -> Unit,
                connectionFailed: (Exception) -> Unit,
                disconnected: (Exception?) -> Unit) : this() {
        this.context = context
        this.callRecord = callRecord
        this.connected = connected
        this.connectionFailed = connectionFailed
        this.disconnected = disconnected
        val params: Map<String, String> = mapOf("To" to callRecord.remotePhoneNumber, "From" to callRecord.localPhoneNumber)
        val connectOptions = ConnectOptions.Builder(callRecord.accessToken).params(params).build()
        call = Voice.connect(context, connectOptions, callListener(this))
    }

    // Accept an incoming call
    constructor(context: Context,
                callInvite: CallInvite,
                connected: (TwilioVendorCall) -> Unit,
                connectionFailed: (Exception) -> Unit,
                disconnected: (Exception?) -> Unit) : this() {
        this.context = context
        this.connected = connected
        this.connectionFailed = connectionFailed
        this.disconnected = disconnected
        call = callInvite.accept(context, callListener(this))
    }
}

// TODO: This will eventually be the GraphQL response type from `createVoiceCall`.
/**
 * An object representing a call record
 */
data class CallRecord(val accessToken: String, val localPhoneNumber: String, val remotePhoneNumber: String)

/**
 * An object representing an active voice call. It is used to monitor call events as well as accept input for muting, disconnecting and placing the call on speaker.
 */
class ActiveVoiceCall(val context: Context,
                      val callRecord: CallRecord,
                      val callId: UUID,
                      private val vendorCall: CallingVendorCall,
                      private val listener: ActiveCallListener) {
    /**
     * The E164-formatted local phone number participating in this voice call.
     */
    val localPhoneNumber = callRecord.localPhoneNumber

    /**
     * The E164-formatted remote phone number participating in this voice call.
     */
    val remotePhoneNumber = callRecord.remotePhoneNumber

    /**
     * Whether outgoing call audio is muted
     */
    var isMuted: Boolean = false
        internal set(value) { listener.activeVoiceCallDidChangeMuteState(this, value) }

    /**
     * Whether call audio is being routed through the speakers
     */
    var isOnSpeaker: Boolean = false
        internal set(value) { listener.activeVoiceCallDidChangeSpeakerState(this, value) }

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
}

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

    constructor(localNumber: String,
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

/**
 * Interface for calling features
 */
interface SudoTelephonyCalling {
    /**
     * Creates a call from a provisioned phone number to another number.
     * @param localNumber: PhoneNumber instance to call from.
     * @param remoteNumber: The E164 formatted phone number of the recipient. For example: "+14155552671".
     * @param listener: ActiveCallListener for monitoring voice call events.
     */
    fun createVoiceCall(localNumber: PhoneNumber,
                        remoteNumber: String,
                        listener: ActiveCallListener)

    /**
     * Registers for incoming call push notifications with the given token
     * @param token FCM token obtained from FirebaseInstanceId
     * @param callback Completion callback providing an empty successful result or an error if there was a failure.
     */
    fun registerForIncomingCalls(token: String, callback: (Result<Unit>) -> Unit)

    /**
     * De-registers from incoming call push notifications with the given token
     * @param token FCM token obtained from FirebaseInstanceId
     * @param callback Completion callback providing an empty successful result or an error if there was a failure.
     */
    fun deregisterForIncomingCalls(token: String, callback: (Result<Unit>) -> Unit)

    /**
     * Handles an incoming push notification with the given payload
     * @param payload The data of the push notification message
     * @param notificationListener IncomingCallNotificationListener for receiving incoming call events
     * @return Boolean indicating whether or not the notification was handled successfully
     */
    fun handleIncomingPushNotification(payload: Map<String, String>, notificationListener: IncomingCallNotificationListener): Boolean
}

class DefaultSudoTelephonyCalling(val context: Context, val graphQLClient: AWSAppSyncClient): SudoTelephonyCalling {
    private lateinit var activeVoiceCall: ActiveVoiceCall
    // TODO: find out if there can be multiple incoming calls and if we need to keep track of this access token differently
    private var incomingCallAccessToken = ""

    override fun createVoiceCall(localNumber: PhoneNumber, remoteNumber: String, listener: ActiveCallListener) {
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
                            activeVoiceCall = ActiveVoiceCall(context, callRecord, callId, vendorCall, listener)
                            listener.activeVoiceCallDidConnect(activeVoiceCall)
                        }

                        val connectionFailed: ((Exception) -> Unit) = { e: Exception ->
                            listener.activeVoiceCallDidFailToConnect(TelephonyCallingFailedToConnectException(e))
                        }

                        val disconnected: ((Exception?) -> Unit) = { e: Exception? ->
                            listener.activeVoiceCallDidDisconnect(activeVoiceCall, e?.let { TelephonyCallingDisconnectedException(it) })
                            listener.connectionStatusChanged(TelephonySubscriber.ConnectionState.DISCONNECTED)
                        }
                        // instantiate the vendor call
                        // Twilio might throw an exception if the RECORD_AUDIO permission is not approved
                        try {
                            TwilioVendorCall(context, callRecord, connected, connectionFailed, disconnected)
                        } catch (e: Exception) {
                            listener.activeVoiceCallDidFailToConnect(TelephonyCallingFailedToStartOutgoingCallException(e))
                        }
                    } ?: run {
                        listener.activeVoiceCallDidFailToConnect(TelephonyCallingFailedToAuthorizeOutgoingCallException())
                    }
                }

                override fun onFailure(e: ApolloException) {
                    listener.activeVoiceCallDidFailToConnect(TelephonyCallingFailedToAuthorizeOutgoingCallException(e))
                }
            })
    }

    private fun registrationListener(callback: (Result<Unit>) -> Unit) : RegistrationListener {
        return object : RegistrationListener {
            override fun onRegistered(accessToken: String,
                                      fcmToken: String) {
                callback.runOnUiThread()(Result.Success(Unit))
            }

            override fun onError(
                registrationException: RegistrationException,
                accessToken: String,
                fcmToken: String
            ) {
                callback.runOnUiThread()(Result.Error(registrationException))
            }
        }
    }

    private fun unregistrationListener(callback: (Result<Unit>) -> Unit) : UnregistrationListener {
        return object : UnregistrationListener {
            override fun onUnregistered(accessToken: String?, fcmToken: String?) {
                callback.runOnUiThread()(Result.Success(Unit))
            }

            override fun onError(
                registrationException: RegistrationException,
                accessToken: String,
                fcmToken: String
            ) {
                callback.runOnUiThread()(Result.Error(registrationException))
            }
        }
    }

    private fun twilioMessageListener(callInviteCallback: (CallInvite) -> Unit, cancelledCallInviteCallback: (CancelledCallInvite) -> Unit) : MessageListener {
        return object : MessageListener {
            override fun onCallInvite(callInvite: CallInvite) {
                callInviteCallback(callInvite)
            }

            override fun onCancelledCallInvite(cancelledCallInvite: CancelledCallInvite) {
                cancelledCallInviteCallback(cancelledCallInvite)
            }
        }
    }

    override fun registerForIncomingCalls(token: String, callback: (Result<Unit>) -> Unit) {
        val input = DeviceRegistrationInput.builder().pushNotificationService(
            PushNotificationService.FCM).build()
        val mutation = RegisterDeviceForIncomingCallsMutation.builder().input(input).build()
        graphQLClient.mutate(mutation)
            .enqueue(object : GraphQLCall.Callback<RegisterDeviceForIncomingCallsMutation.Data>() {
                override fun onResponse(response: Response<RegisterDeviceForIncomingCallsMutation.Data>) {
                    response.data()?.registerDeviceForIncomingCalls?.vendorAuthorizations?.forEach { vendorAuth ->
                        incomingCallAccessToken = vendorAuth.accessToken
                        if (vendorAuth.vendor == "Twilio") {
                            Voice.register(vendorAuth.accessToken, Voice.RegistrationChannel.FCM, token, registrationListener(callback))
                        } else {
                            // TODO - handle simulator
                        }
                    }
                }

                override fun onFailure(e: ApolloException) {
                    callback.runOnUiThread()(Result.Error(e))
                }
            })
    }

    override fun deregisterForIncomingCalls(token: String, callback: (Result<Unit>) -> Unit) {
        val input = DeviceRegistrationInput.builder().pushNotificationService(
            PushNotificationService.FCM).build()
        val mutation = UnregisterDeviceForIncomingCallsMutation.builder().input(input).build()
        graphQLClient.mutate(mutation)
            .enqueue(object : GraphQLCall.Callback<UnregisterDeviceForIncomingCallsMutation.Data>() {
                override fun onResponse(response: Response<UnregisterDeviceForIncomingCallsMutation.Data>) {
                    response.data()?.unregisterDeviceForIncomingCalls?.vendorAuthorizations?.forEach { vendorAuth ->
                        incomingCallAccessToken = vendorAuth.accessToken
                        if (vendorAuth.vendor == "Twilio") {
                            Voice.unregister(vendorAuth.accessToken, Voice.RegistrationChannel.FCM, token, unregistrationListener(callback))
                        } else {
                            // TODO - handle simulator
                        }
                    }
                }

                override fun onFailure(e: ApolloException) {
                    callback.runOnUiThread()(Result.Error(e))
                }
            })
    }

    override fun handleIncomingPushNotification(payload: Map<String, String>,
                                                       notificationListener: IncomingCallNotificationListener): Boolean {
        // set up Twilio MessageListener and pass on its callbacks to the notification listener
        val messageListener = twilioMessageListener(callInviteCallback = { callInvite ->
            val callAccepted: (IncomingCall, ActiveCallListener) -> Unit = { incomingCall, listener ->
                val callId = UUID.randomUUID()
                val connected = { vendorCall: TwilioVendorCall ->
                    activeVoiceCall = ActiveVoiceCall(context,
                        CallRecord(incomingCallAccessToken, incomingCall.localNumber, incomingCall.remoteNumber),
                        callId,
                        vendorCall, listener)
                    listener.activeVoiceCallDidConnect(activeVoiceCall)
                }

                val connectionFailed: ((Exception) -> Unit) = { e: Exception ->
                    listener.activeVoiceCallDidFailToConnect(TelephonyCallingFailedToConnectException(e))
                }

                val disconnected: ((Exception?) -> Unit) = { e: Exception? ->
                    listener.activeVoiceCallDidDisconnect(activeVoiceCall, e?.let { TelephonyCallingDisconnectedException(it) })
                    listener.connectionStatusChanged(TelephonySubscriber.ConnectionState.DISCONNECTED)
                }
                try {
                    TwilioVendorCall(context, callInvite, connected, connectionFailed, disconnected)
                } catch (e: Exception) {
                    listener.activeVoiceCallDidFailToConnect(TelephonyCallingFailedToAcceptIncomingCallException(e))
                }
            }
            val callDeclined: (IncomingCall) -> Unit = { incomingCall ->
                callInvite.reject(context)
            }
            val incomingCall = IncomingCall(callInvite.to, callInvite.from ?: "", callInvite.callSid, callAccepted, callDeclined)
            notificationListener.incomingCallReceived(incomingCall)
        }, cancelledCallInviteCallback = { cancelledCallInvite ->
            val incomingCall = IncomingCall(cancelledCallInvite.to, cancelledCallInvite.from ?: "", cancelledCallInvite.callSid, null, null)
            notificationListener.incomingCallCanceled(incomingCall, null)
        })
        return Voice.handleMessage(payload, messageListener)
    }
}

