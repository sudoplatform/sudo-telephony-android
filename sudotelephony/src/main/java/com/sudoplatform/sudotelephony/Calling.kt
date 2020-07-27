package com.sudoplatform.sudotelephony

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.type.*
import com.twilio.audioswitch.selection.AudioDevice
import com.twilio.audioswitch.selection.AudioDeviceSelector
import com.sudoplatform.sudotelephony.type.CreateVoiceCallInput
import com.sudoplatform.sudotelephony.type.DeviceRegistrationInput
import com.sudoplatform.sudotelephony.type.PushNotificationService
import com.sudoplatform.sudotelephony.type.VoicemailKeyInput
import com.twilio.voice.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
        set(value) { call.mute(value) }

    override var isOnHold: Boolean
        get() = call.isOnHold
        set(value) { call.hold(value) }

    override fun playDTMF(digits: String) {
        call.sendDigits(digits)
    }

    // Start an outgoing call
    constructor(context: Context,
                accessToken: String,
                localPhoneNumber: String,
                remotePhoneNumber: String,
                connected: (TwilioVendorCall) -> Unit,
                connectionFailed: (Exception) -> Unit,
                disconnected: (Exception?) -> Unit) : this() {
        this.context = context
        this.connected = connected
        this.connectionFailed = connectionFailed
        this.disconnected = disconnected
        val params: Map<String, String> = mapOf("To" to remotePhoneNumber, "From" to localPhoneNumber)
        val connectOptions = ConnectOptions.Builder(accessToken).params(params).build()
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

/**
 * An object representing an active voice call. It is used to monitor call events as well as accept input for muting, disconnecting and placing the call on speaker.
 */
class ActiveVoiceCall(val context: Context,
                      internal val accessToken: String,
                      /**
                       * The E164-formatted local phone number participating in this voice call.
                       */
                      val localPhoneNumber: String,
                      /**
                       * The E164-formatted remote phone number participating in this voice call.
                       */
                      val remotePhoneNumber: String,
                      val callId: UUID,
                      private val vendorCall: CallingVendorCall,
                      private val listener: ActiveCallListener) {

    private val audioDeviceSelector: AudioDeviceSelector = AudioDeviceSelector(context)

    init {
        audioDeviceSelector.start { _, selectedAudioDevice ->
            selectedAudioDevice?.let { selectedDevice ->
                listener.activeVoiceCallDidChangeAudioDevice(this, VoiceCallAudioDevice.fromInternalType(selectedDevice))
            }
        }
    }

    internal fun stopAudioDeviceListener() {
        audioDeviceSelector.stop()
    }

    /**
     * Whether outgoing call audio is muted
     */
    var isMuted: Boolean = false
        internal set(value) { listener.activeVoiceCallDidChangeMuteState(this, value) }

    /**
     * Whether call audio is being routed through the speakers
     */
    val isOnSpeaker: Boolean
        get() {
            return audioDeviceSelector.selectedAudioDevice is AudioDevice.Speakerphone
        }

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
        val devices: List<AudioDevice> = audioDeviceSelector.availableAudioDevices
        if (speaker) {
            devices.find { it is AudioDevice.Speakerphone }?.let {
                audioDeviceSelector.selectDevice(it)
                audioDeviceSelector.activate()
            }
        } else {
            // go back to prvious device
            audioDeviceSelector.deactivate()
        }
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
     * Retrieves a call record.
     * @param callRecordId The id of the call record to be retrieved
     * @param callback Completion callback providing the call record or an error if there was a failure.
     */
    fun getCallRecord(callRecordId: String, callback: (Result<CallRecord>) -> Unit)

    /**
     * Retrieves call records for a given phone number
     * @param localNumber The phone number to fetch phone records for
     * @param limit The limit of the batch to fetch. If none specified, all call records will be returned.
     * @param nextToken The token to use for pagination.
     * @param callback Completion callback providing a list of call records or an error if there was a failure.
     */
    fun getCallRecords(localNumber: PhoneNumber, limit: Int?, nextToken: String?, callback: (Result<TelephonyListToken<CallRecord>>) -> Unit)

    /**
     * Subscribes to be notified of new `CallRecord` objects. If no id is provided,
     * a default id will be used.
     *
     * @param subscriber The subscriber to notify.
     * @param id The unique ID for the subscriber.
     */
    fun subscribeToCallRecords(subscriber: CallRecordSubscriber, id: String)

    /**
     * Unsubscribes the specified subscriber so that it no longer receives notifications about
     * new 'CallRecord' objects. If no id is provided, all subscribers will be unsubscribed.
     *
     * @param id unique ID for the subscriber.
     */
    fun unsubscribeFromCallRecords(id: String?)

    /**
     * Deletes a call record matching the associated id.
     * @param id The id of the call record to delete.
     * @param callback Completion callback providing the id of the deleted call record or an error if the call record could not be deleted.
     */
    fun deleteCallRecord(id: String, callback: (Result<String>) -> Unit)

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

    /**
     * Retrieves a list of `Voicemail`s for the given `PhoneNumber`.
     * @param localNumber The `PhoneNumber` to fetch `Voicemail`s for.
     * @param limit The maximum number of `Voicemail`s to retrieve per page.
     * @param nextToken The token to use for pagination.
     * @param callback Completion callback providing a list of `Voicemail`s or an error if it could not be retrieved.
     */
    fun getVoicemails(localNumber: PhoneNumber, limit: Int?, nextToken: String?, callback: (Result<TelephonyListToken<Voicemail>>) -> Unit)

    /** Retrieves the voicemail with the specified `id`.
     * @param id The `id` of the `Voicemail` to retrieve.
     * @param callback Completion callback providing the `Voicemail` or an error if it could not be retrieved.
     */
    fun getVoicemail(id: String, callback: (Result<Voicemail>) -> Unit)

    /**
     * Deletes a voicemail matching the given `id`.
     * @param id The `id` of the `Voicemail` to delete.
     * @param callback Completion callback providing success or an error if the `Voicemail` could not be deleted.
     */
    fun deleteVoicemail(id: String, callback: (Result<String>) -> Unit)

    /**
     * Subscribe to new, updated, and deleted `Voicemail` records.
     * @param subscriber The subscriber to notify.
     * @param id The unique ID for the subscriber.
    */
    fun subscribeToVoicemails(subscriber: VoicemailSubscriber, id: String)

    /**
     * Unsubscribes the specified subscriber so that it no longer receives notifications about
     * new 'Voicemail' objects. If no id is provided, all subscribers will be unsubscribed.
     *
     * @param id unique ID for the subscriber.
     */
    fun unsubscribeFromVoicemails(id: String?)
}

class DefaultSudoTelephonyCalling(val context: Context,
                                  val graphQLClient: AWSAppSyncClient,
                                  val keyManager: TelephonyKeyManager,
                                  val logger: Logger): SudoTelephonyCalling {

    private lateinit var activeVoiceCall: ActiveVoiceCall
    // TODO: find out if there can be multiple incoming calls and if we need to keep track of this access token differently
    private var incomingCallAccessToken = ""
    private var callRecordSubscriptionManager: SubscriptionManager<OnCallRecordSubscription.Data>? = null
    private var voicemailSubscriptionManager: SubscriptionManager<OnVoicemailSubscription.Data>? = null

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
                    val token = response.data()?.createVoiceCall()?.vendorAuthorization?.accessToken
                    token?.let { accessToken ->
                        val callId = UUID.randomUUID()
                        val connected = { vendorCall: TwilioVendorCall ->
                            activeVoiceCall = ActiveVoiceCall(context, accessToken, localNumber.phoneNumber, remoteNumber, callId, vendorCall, listener)
                            listener.activeVoiceCallDidConnect(activeVoiceCall)
                        }

                        val connectionFailed: ((Exception) -> Unit) = { e: Exception ->
                            listener.activeVoiceCallDidFailToConnect(TelephonyCallingFailedToConnectException(e))
                        }

                        val disconnected: ((Exception?) -> Unit) = { e: Exception? ->
                            activeVoiceCall.stopAudioDeviceListener()
                            listener.activeVoiceCallDidDisconnect(activeVoiceCall, e?.let { TelephonyCallingDisconnectedException(it) })
                            listener.connectionStatusChanged(TelephonySubscriber.ConnectionState.DISCONNECTED)
                        }
                        // instantiate the vendor call
                        // Twilio might throw an exception if the RECORD_AUDIO permission is not approved
                        try {
                            TwilioVendorCall(context, accessToken, localNumber.phoneNumber, remoteNumber, connected, connectionFailed, disconnected)
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

    override fun getCallRecord(callRecordId: String, callback: (Result<CallRecord>) -> Unit) {
        val getCallRecordQuery = GetCallRecordQuery.builder()
            .id(callRecordId)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.query(getCallRecordQuery)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<GetCallRecordQuery.Data>() {
                    override fun onResponse(response: Response<GetCallRecordQuery.Data>) {
                        val sealedCallRecord = response.data()?.getCallRecord?.fragments()?.sealedCallRecord
                        if (sealedCallRecord == null) {
                            val error = TelephonyGetCallRecordException()
                            logger.error("Failed to retrieve call record for id $callRecordId")
                            callback.runOnUiThread()(Result.Error(error))
                            return
                        }

                        val callRecord = CallRecord.createFrom(sealedCallRecord, keyManager)

                        callback.runOnUiThread()(Result.Success(callRecord))
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyGetCallRecordException(e)
                        logger.error("Failed to retrieve call record for id $callRecordId")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }


    override fun getCallRecords(localNumber: PhoneNumber,
                                limit: Int?,
                                nextToken: String?,
                                callback: (Result<TelephonyListToken<CallRecord>>) -> Unit) {
        val callStateFilter = CallStateFilterInput.builder().
            `in`(listOf(CallState.COMPLETED, CallState.UNANSWERED))
            .build()
        val callRecordInput = CallRecordFilterInput.builder()
            .state(callStateFilter)
            .build()
        val keyInput = CallRecordKeyInput.builder()
            .phoneNumberId(localNumber.id)
            .build()

        val query = ListCallRecordsQuery.builder()
            .key(keyInput)
            .filter(callRecordInput)
            .limit(limit)
            .nextToken(nextToken)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<ListCallRecordsQuery.Data>() {
                    override fun onResponse(response: Response<ListCallRecordsQuery.Data>) {
                        val sealedCallRecords = response.data()?.listCallRecords?.items
                        val newNextToken = response.data()?.listCallRecords?.nextToken

                        if (sealedCallRecords == null) {
                            val error = TelephonyGetCallRecordException()
                            logger.error("Failed to retrieve call records for phone number ${localNumber.phoneNumber}")
                            callback.runOnUiThread()(Result.Error(error))
                            return
                        }

                        if (sealedCallRecords.size < 1) {
                            callback.runOnUiThread()(Result.Absent)
                            return
                        }

                        val allRecords = sealedCallRecords.map {
                            val record = it.fragments().sealedCallRecord
                            CallRecord.createFrom(record, keyManager)
                        }.sortedByDescending { it.created }

                        callback.runOnUiThread()(
                            Result.Success(
                                TelephonyListToken(
                                    allRecords, newNextToken
                                )
                            )
                        )
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyGetCallRecordException(e)
                        logger.error("Failed to retrieve call records for phone number ${localNumber.phoneNumber}")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun subscribeToCallRecords(subscriber: CallRecordSubscriber, id: String) {
        logger.debug("Subscribing to CallRecord notifications.")

        val owner = keyManager.getOwner()
        require(owner != null) {"Owner was null. The client may not be signed in."}

        if (this.callRecordSubscriptionManager == null) {
            this.callRecordSubscriptionManager = SubscriptionManager()
        }
        val subscriptionManager = this.callRecordSubscriptionManager
        subscriptionManager?.replaceSubscriber(id, subscriber)
        if (subscriptionManager?.watcher == null) {
            GlobalScope.launch(Dispatchers.IO) {
                val subscription = OnCallRecordSubscription.builder().owner(owner).build()
                val watcher = graphQLClient.subscribe(subscription)
                subscriptionManager?.watcher = watcher
                watcher.execute(object :
                    AppSyncSubscriptionCall.Callback<OnCallRecordSubscription.Data> {
                    override fun onCompleted() {
                        // Subscription was terminated. Notify the subscribers.
                        subscriptionManager?.connectionStatusChanged(
                            TelephonySubscriber.ConnectionState.DISCONNECTED
                        )
                    }

                    override fun onFailure(e: ApolloException) {
                        // Failed create a subscription. Notify the subscribers.
                        subscriptionManager?.connectionStatusChanged(
                            TelephonySubscriber.ConnectionState.DISCONNECTED
                        )
                    }

                    override fun onResponse(response: Response<OnCallRecordSubscription.Data>) {
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                val error = response.errors().firstOrNull()
                                if (error != null) {
                                    logger.error("Subscription response contained error: $error")
                                } else {
                                    val item = response.data()?.OnCallRecord()
                                    if (item != null) {
                                        val sealedCallRecord = item.fragments().sealedCallRecord()
                                        val callRecord = CallRecord.createFrom(sealedCallRecord, keyManager)

                                        // Notify subscribers
                                        subscriptionManager?.callRecordReceived(callRecord)
                                    }
                                }
                            } catch (e: Exception) {
                                logger.error("Failed to process the subscription response: $e")
                            }
                        }
                    }
                })

                subscriptionManager?.connectionStatusChanged(
                    TelephonySubscriber.ConnectionState.CONNECTED
                )
            }
        }
    }

    override fun unsubscribeFromCallRecords(id: String?) {
        val subscriptionManager = this.callRecordSubscriptionManager
        logger.debug("Unsubscribing from new call record notifications.")
        if (id == null) {
            subscriptionManager?.removeAllSubscribers()
        } else {
            subscriptionManager?.removeSubscriber(id)
        }
    }

    override fun deleteCallRecord(id: String, callback: (Result<String>) -> Unit) {
        val mutation = DeleteCallRecordMutation.builder()
            .id(id)
            .build()
        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.mutate(mutation)
                .enqueue(object : GraphQLCall.Callback<DeleteCallRecordMutation.Data>() {
                    override fun onResponse(response: Response<DeleteCallRecordMutation.Data>) {
                        val error = response.errors().firstOrNull()
                        if (error != null) {
                            logger.error("Delete call record response contained error: $error")
                            callback.runOnUiThread()(Result.Error(TelephonyDeleteCallRecordException()))
                        } else {
                            callback.runOnUiThread()(Result.Success(response.data()?.deleteCallRecord ?: ""))
                        }
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyDeleteCallRecordException(e)
                        logger.error("Failed to delete call record for id $id")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
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
                        incomingCallAccessToken,
                        incomingCall.localNumber,
                        incomingCall.remoteNumber,
                        callId,
                        vendorCall, listener)
                    listener.activeVoiceCallDidConnect(activeVoiceCall)
                }

                val connectionFailed: ((Exception) -> Unit) = { e: Exception ->
                    listener.activeVoiceCallDidFailToConnect(TelephonyCallingFailedToConnectException(e))
                }

                val disconnected: ((Exception?) -> Unit) = { e: Exception? ->
                    activeVoiceCall.stopAudioDeviceListener()
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

    // Voicemail

    override fun getVoicemails(localNumber: PhoneNumber, limit: Int?, nextToken: String?, callback: (Result<TelephonyListToken<Voicemail>>) -> Unit) {
        val keyInput = VoicemailKeyInput.builder()
            .phoneNumberId(localNumber.id)
            .build()
        val query = ListVoicemailsQuery.builder()
            .key(keyInput)
            .limit(limit)
            .nextToken(nextToken)
            .build()
        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<ListVoicemailsQuery.Data>() {
                    override fun onResponse(response: Response<ListVoicemailsQuery.Data>) {
                        val error = response.errors().firstOrNull()
                        if (error != null) {
                            logger.error("Get voicemails response contained error: $error")
                            callback.runOnUiThread()(Result.Error(TelephonyGetVoicemailException()))
                            return
                        }

                        val sealedVoicemails = response.data()?.listVoicemails?.items
                        val newNextToken = response.data()?.listVoicemails?.nextToken

                        if (sealedVoicemails == null) {
                            logger.error("Failed to retrieve voicemails for phone number ${localNumber.phoneNumber}")
                            callback.runOnUiThread()(Result.Error(TelephonyGetVoicemailException()))
                            return
                        }

                        if (sealedVoicemails.size < 1) {
                            callback.runOnUiThread()(Result.Absent)
                            return
                        }

                        val allVoicemails = sealedVoicemails.map {
                            val record = it.fragments().sealedVoicemail
                            Voicemail.createFrom(record, keyManager)
                        }

                        callback.runOnUiThread()(
                            Result.Success(
                                TelephonyListToken(
                                    allVoicemails, newNextToken
                                )
                            )
                        )
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyGetVoicemailException()
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun getVoicemail(id: String, callback: (Result<Voicemail>) -> Unit) {
        val query = GetVoicemailQuery.builder()
            .id(id)
            .build()
        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<GetVoicemailQuery.Data>() {
                    override fun onResponse(response: Response<GetVoicemailQuery.Data>) {
                        val error = response.errors().firstOrNull()
                        if (error != null) {
                            logger.error("Get voicemails response contained error: $error")
                            callback.runOnUiThread()(Result.Error(TelephonyGetVoicemailException()))
                            return
                        }

                        val sealedVoicemail = response.data()?.voicemail?.fragments()?.sealedVoicemail
                        if (sealedVoicemail == null) {
                            logger.error("Failed to retrieve voicemail for id $id")
                            callback.runOnUiThread()(Result.Error(TelephonyGetVoicemailException()))
                            return
                        }

                        val voicemail = Voicemail.createFrom(sealedVoicemail, keyManager)

                        callback.runOnUiThread()(Result.Success(voicemail))
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyGetVoicemailException()
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun deleteVoicemail(id: String, callback: (Result<String>) -> Unit) {
        val mutation = DeleteVoicemailMutation.builder()
            .id(id)
            .build()
        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.mutate(mutation)
                .enqueue(object : GraphQLCall.Callback<DeleteVoicemailMutation.Data>() {
                    override fun onResponse(response: Response<DeleteVoicemailMutation.Data>) {
                        val error = response.errors().firstOrNull()
                        if (error != null) {
                            logger.error("Delete voicemail response contained error: $error")
                            callback.runOnUiThread()(Result.Error(TelephonyDeleteVoicemailException()))
                        } else {
                            callback.runOnUiThread()(Result.Success(response.data()?.deleteVoicemail ?: ""))
                        }
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyDeleteVoicemailException(e)
                        logger.error("Failed to delete voicemail for id $id")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun subscribeToVoicemails(subscriber: VoicemailSubscriber, id: String) {
        logger.debug("Subscribing to Voicemail notifications.")

        val owner = keyManager.getOwner()
        require(owner != null) {"Owner was null. The client may not be signed in."}

        if (this.voicemailSubscriptionManager == null) {
            this.voicemailSubscriptionManager = SubscriptionManager()
        }
        val subscriptionManager = this.voicemailSubscriptionManager
        subscriptionManager?.replaceSubscriber(id, subscriber)
        if (subscriptionManager?.watcher == null) {
            GlobalScope.launch(Dispatchers.IO) {
                val subscription = OnVoicemailSubscription.builder().owner(owner).build()
                val watcher = graphQLClient.subscribe(subscription)
                subscriptionManager?.watcher = watcher
                watcher.execute(object :
                    AppSyncSubscriptionCall.Callback<OnVoicemailSubscription.Data> {
                    override fun onCompleted() {
                        // Subscription was terminated. Notify the subscribers.
                        subscriptionManager?.connectionStatusChanged(
                            TelephonySubscriber.ConnectionState.DISCONNECTED
                        )
                    }

                    override fun onFailure(e: ApolloException) {
                        // Failed create a subscription. Notify the subscribers.
                        subscriptionManager?.connectionStatusChanged(
                            TelephonySubscriber.ConnectionState.DISCONNECTED
                        )
                    }

                    override fun onResponse(response: Response<OnVoicemailSubscription.Data>) {
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                val error = response.errors().firstOrNull()
                                if (error != null) {
                                    logger.error("Subscription response contained error: $error")
                                } else {
                                    val item = response.data()?.OnVoicemail()
                                    if (item != null) {
                                        val sealedVoicemail = item.fragments().sealedVoicemail()
                                        val voicemail = Voicemail.createFrom(sealedVoicemail, keyManager)

                                        // Notify subscribers
                                        subscriptionManager?.voicemailUpdated(voicemail)
                                    }
                                }
                            } catch (e: Exception) {
                                logger.error("Failed to process the subscription response: $e")
                            }
                        }
                    }
                })

                subscriptionManager?.connectionStatusChanged(
                    TelephonySubscriber.ConnectionState.CONNECTED
                )
            }
        }
    }

    override fun unsubscribeFromVoicemails(id: String?) {
        val subscriptionManager = this.voicemailSubscriptionManager
        logger.debug("Unsubscribing from new voicemail notifications.")
        if (id == null) {
            subscriptionManager?.removeAllSubscribers()
        } else {
            subscriptionManager?.removeSubscriber(id)
        }
    }
}

