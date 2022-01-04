package com.sudoplatform.sudotelephony.calling

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.appsync.enqueue
import com.sudoplatform.sudotelephony.calllisteners.ActiveCallListener
import com.sudoplatform.sudotelephony.calllisteners.IncomingCallNotificationListener
import com.sudoplatform.sudotelephony.callrecords.CallRecord
import com.sudoplatform.sudotelephony.callrecords.CallRecordSubscriber
import com.sudoplatform.sudotelephony.exceptions.SudoTelephonyException
import com.sudoplatform.sudotelephony.graphql.CreateVoiceCallMutation
import com.sudoplatform.sudotelephony.graphql.DeleteCallRecordMutation
import com.sudoplatform.sudotelephony.graphql.DeleteVoicemailMutation
import com.sudoplatform.sudotelephony.graphql.GetCallRecordQuery
import com.sudoplatform.sudotelephony.graphql.GetVoicemailQuery
import com.sudoplatform.sudotelephony.graphql.ListCallRecordsQuery
import com.sudoplatform.sudotelephony.graphql.ListVoicemailsQuery
import com.sudoplatform.sudotelephony.graphql.OnCallRecordSubscription
import com.sudoplatform.sudotelephony.graphql.OnVoicemailSubscription
import com.sudoplatform.sudotelephony.graphql.RegisterDeviceForIncomingCallsMutation
import com.sudoplatform.sudotelephony.graphql.UnregisterDeviceForIncomingCallsMutation
import com.sudoplatform.sudotelephony.graphql.type.CallRecordFilterInput
import com.sudoplatform.sudotelephony.graphql.type.CallRecordKeyInput
import com.sudoplatform.sudotelephony.graphql.type.CallState
import com.sudoplatform.sudotelephony.graphql.type.CallStateFilterInput
import com.sudoplatform.sudotelephony.graphql.type.CreateVoiceCallInput
import com.sudoplatform.sudotelephony.graphql.type.DeviceRegistrationInput
import com.sudoplatform.sudotelephony.graphql.type.PushNotificationService
import com.sudoplatform.sudotelephony.graphql.type.VoicemailKeyInput
import com.sudoplatform.sudotelephony.keys.TelephonyKeyManager
import com.sudoplatform.sudotelephony.phonenumbers.PhoneNumber
import com.sudoplatform.sudotelephony.subscription.SubscriptionManager
import com.sudoplatform.sudotelephony.telephony.TelephonyListToken
import com.sudoplatform.sudotelephony.telephony.TelephonySubscriber
import com.sudoplatform.sudotelephony.voicemail.Voicemail
import com.sudoplatform.sudotelephony.voicemail.VoicemailSubscriber
import com.twilio.voice.CallInvite
import com.twilio.voice.CancelledCallInvite
import com.twilio.voice.MessageListener
import com.twilio.voice.RegistrationException
import com.twilio.voice.RegistrationListener
import com.twilio.voice.UnregistrationListener
import com.twilio.voice.Voice
import java.util.UUID

/**
 * Main implementation of [SudoTelephonyCalling] interface
 */
class DefaultSudoTelephonyCalling(
    val context: Context,
    val graphQLClient: AWSAppSyncClient,
    val keyManager: TelephonyKeyManager,
    val logger: Logger
) : SudoTelephonyCalling {

    private lateinit var activeVoiceCall: ActiveVoiceCall

    // TODO: find out if there can be multiple incoming calls and if we need to keep track of this access token differently
    private var incomingCallAccessToken = ""

    private var callRecordSubscriptionManager:
        SubscriptionManager<OnCallRecordSubscription.Data>? = null

    private var voicemailSubscriptionManager:
        SubscriptionManager<OnVoicemailSubscription.Data>? = null

    override suspend fun createVoiceCall(
        localNumber: PhoneNumber,
        remoteNumber: String,
        listener: ActiveCallListener
    ) {
        val input = CreateVoiceCallInput.builder()
            .from(localNumber.phoneNumber)
            .to(remoteNumber)
            .build()
        val mutation = CreateVoiceCallMutation.builder()
            .input(input)
            .build()
        val createVoiceCallResponse = graphQLClient.mutate(mutation)
            .enqueue()
        if (createVoiceCallResponse.hasErrors()) {
            listener.activeVoiceCallDidFailToConnect(
                SudoTelephonyException.CallingFailedToAuthorizeOutgoingCallException(
                    createVoiceCallResponse.errors().first().message()
                )
            )
        }

        val token =
            createVoiceCallResponse.data()?.createVoiceCall()?.vendorAuthorization()?.accessToken()
        token?.let { accessToken ->
            val callId = UUID.randomUUID()
            val connected = { vendorCall: TwilioVendorCall ->
                activeVoiceCall = ActiveVoiceCall(
                    context,
                    accessToken,
                    localNumber.phoneNumber,
                    remoteNumber,
                    callId,
                    vendorCall,
                    listener
                )
                listener.activeVoiceCallDidConnect(activeVoiceCall)
            }

            val connectionFailed: ((Exception) -> Unit) = { e: Exception ->
                listener.activeVoiceCallDidFailToConnect(
                    SudoTelephonyException.CallingFailedToConnectException(cause = e)
                )
            }

            val disconnected: ((Exception?) -> Unit) = { e: Exception? ->
                activeVoiceCall.stopAudioDeviceListener()
                listener.activeVoiceCallDidDisconnect(
                    activeVoiceCall,
                    e?.let { SudoTelephonyException.CallingDisconnectedException(cause = it) }
                )
                listener.connectionStatusChanged(TelephonySubscriber.ConnectionState.DISCONNECTED)
            }
            // instantiate the vendor call
            // Twilio might throw an exception if the RECORD_AUDIO permission is not approved
            try {
                TwilioVendorCall(
                    context,
                    accessToken,
                    localNumber.phoneNumber,
                    remoteNumber,
                    connected,
                    connectionFailed,
                    disconnected
                )
            } catch (e: Exception) {
                listener.activeVoiceCallDidFailToConnect(
                    SudoTelephonyException.CallingFailedToStartOutgoingCallException(cause = e)
                )
            }
        } ?: run {
            listener.activeVoiceCallDidFailToConnect(
                SudoTelephonyException.CallingFailedToAuthorizeOutgoingCallException()
            )
        }
    }

    override suspend fun getCallRecord(callRecordId: String): CallRecord {
        val getCallRecordQuery = GetCallRecordQuery.builder()
            .id(callRecordId)
            .build()

        val callRecordResponse = graphQLClient.query(getCallRecordQuery)
            .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
            .enqueue()

        if (callRecordResponse.hasErrors()) {
            logger.error("Failed to retrieve call record for id $callRecordId")
            throw SudoTelephonyException.GetCallRecordException(callRecordResponse.errors().first().message())
        }

        val sealedCallRecord = callRecordResponse
            .data()
            ?.callRecord
            ?.fragments()
            ?.sealedCallRecord()
        if (sealedCallRecord == null) {
            logger.error("Failed to retrieve call record for id $callRecordId")
            throw SudoTelephonyException.GetCallRecordException()
        }

        return CallRecord.createFrom(sealedCallRecord, keyManager)
    }

    override suspend fun getCallRecords(
        localNumber: PhoneNumber,
        limit: Int?,
        nextToken: String?
    ): TelephonyListToken<CallRecord> {
        val callStateFilter = CallStateFilterInput
            .builder()
            .`in`(listOf(CallState.COMPLETED, CallState.UNANSWERED))
            .build()

        val callRecordInput = CallRecordFilterInput
            .builder()
            .state(callStateFilter)
            .build()

        val keyInput = CallRecordKeyInput
            .builder()
            .phoneNumberId(localNumber.id)
            .build()

        val query = ListCallRecordsQuery.builder()
            .key(keyInput)
            .filter(callRecordInput)
            .limit(limit)
            .nextToken(nextToken)
            .build()

        val callRecordsResponse = graphQLClient.query(query)
            .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
            .enqueue()

        if (callRecordsResponse.hasErrors()) {
            logger.error("Failed to retrieve call records for phone number ${localNumber.phoneNumber}")
            throw SudoTelephonyException.GetCallRecordException(
                callRecordsResponse.errors().first().message()
            )
        }
        val sealedCallRecords = callRecordsResponse.data()?.listCallRecords()?.items()
        val newNextToken = callRecordsResponse.data()?.listCallRecords()?.nextToken()

        if (sealedCallRecords == null) {
            logger.error("Failed to retrieve call records for phone number ${localNumber.phoneNumber}")
            throw SudoTelephonyException.GetCallRecordException()
        }

        if (sealedCallRecords.size < 1) {
            return TelephonyListToken(emptyList(), null)
        }

        val allRecords = sealedCallRecords.map {
            val record = it.fragments().sealedCallRecord()
            CallRecord.createFrom(record, keyManager)
        }.sortedByDescending { it.created }

        return TelephonyListToken(allRecords, newNextToken)
    }

    override suspend fun subscribeToCallRecords(subscriber: CallRecordSubscriber, id: String) {
        logger.debug("Subscribing to CallRecord notifications.")

        val owner = keyManager.getOwner()
        require(owner != null) { "Owner was null. The client may not be signed in." }

        if (this.callRecordSubscriptionManager == null) {
            this.callRecordSubscriptionManager = SubscriptionManager()
        }
        val subscriptionManager = this.callRecordSubscriptionManager
        subscriptionManager?.replaceSubscriber(id, subscriber)
        if (subscriptionManager?.watcher == null) {
            val subscription = OnCallRecordSubscription.builder().owner(owner).build()
            val watcher = graphQLClient.subscribe(subscription)
            subscriptionManager?.watcher = watcher

            watcher.execute(
                object : AppSyncSubscriptionCall.Callback<OnCallRecordSubscription.Data> {
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
                        try {
                            val error = response.errors().firstOrNull()
                            if (error != null) {
                                logger.error("Subscription response contained error: $error")
                            } else {
                                val item = response.data()?.OnCallRecord()
                                if (item != null) {
                                    val sealedCallRecord =
                                        item.fragments().sealedCallRecord()
                                    val callRecord = CallRecord
                                        .createFrom(
                                            sealedCallRecord,
                                            keyManager
                                        )

                                    // Notify subscribers
                                    subscriptionManager?.callRecordReceived(callRecord)
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to process the subscription response: $e")
                        }
                    }
                })

            subscriptionManager?.connectionStatusChanged(
                TelephonySubscriber.ConnectionState.CONNECTED
            )
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

    override suspend fun deleteCallRecord(id: String): String {
        val mutation = DeleteCallRecordMutation.builder()
            .id(id)
            .build()

        val deleteCallRecordResponse = graphQLClient.mutate(mutation).enqueue()

        if (deleteCallRecordResponse.hasErrors()) {
            val error = deleteCallRecordResponse.errors().first()
            logger.debug("Delete call record response contained error: $error")
            throw SudoTelephonyException.DeleteCallRecordException(error.message())
        }

        return deleteCallRecordResponse.data()?.deleteCallRecord() ?: ""
    }

    private fun registrationListener(): RegistrationListener {
        return object : RegistrationListener {
            override fun onRegistered(
                accessToken: String,
                fcmToken: String
            ) {}

            override fun onError(
                registrationException: RegistrationException,
                accessToken: String,
                fcmToken: String
            ) {}
        }
    }

    private fun unregistrationListener(): UnregistrationListener {
        return object : UnregistrationListener {
            override fun onUnregistered(accessToken: String?, fcmToken: String?) {
            }

            override fun onError(
                registrationException: RegistrationException,
                accessToken: String,
                fcmToken: String
            ) {
            }
        }
    }

    private fun twilioMessageListener(
        callInviteCallback: (CallInvite) -> Unit,
        cancelledCallInviteCallback: (CancelledCallInvite) -> Unit
    ): MessageListener {
        return object : MessageListener {
            override fun onCallInvite(callInvite: CallInvite) {
                callInviteCallback(callInvite)
            }

            override fun onCancelledCallInvite(cancelledCallInvite: CancelledCallInvite) {
                cancelledCallInviteCallback(cancelledCallInvite)
            }
        }
    }

    override suspend fun registerForIncomingCalls(token: String) {
        val input = DeviceRegistrationInput
            .builder()
            .pushNotificationService(
                PushNotificationService.FCM
            ).build()
        val mutation = RegisterDeviceForIncomingCallsMutation.builder().input(input).build()

        val registerResponse = graphQLClient.mutate(mutation).enqueue()

        if (registerResponse.hasErrors()) {
            throw SudoTelephonyException.CallingFailedToRegisterForIncomingCallsException(
                registerResponse.errors().first().message()
            )
        }

        registerResponse.data()?.registerDeviceForIncomingCalls()?.vendorAuthorizations()
            ?.forEach { vendorAuth ->
                incomingCallAccessToken = vendorAuth.accessToken()
                if (vendorAuth.vendor() == "Twilio") {
                    Voice.register(
                        vendorAuth.accessToken(),
                        Voice.RegistrationChannel.FCM,
                        token,
                        registrationListener()
                    )
                }
            }
    }

    override suspend fun deregisterForIncomingCalls(token: String) {
        val input = DeviceRegistrationInput
            .builder()
            .pushNotificationService(
                PushNotificationService.FCM
            ).build()
        val mutation = UnregisterDeviceForIncomingCallsMutation.builder().input(input).build()

        val unregisterResponse = graphQLClient.mutate(mutation).enqueue()

        if (unregisterResponse.hasErrors()) {
            throw SudoTelephonyException.CallingFailedToDeRegisterForIncomingCallsException()
        }

        unregisterResponse.data()
            ?.unregisterDeviceForIncomingCalls()
            ?.vendorAuthorizations()
            ?.forEach { vendorAuth ->
                incomingCallAccessToken = vendorAuth.accessToken()
                if (vendorAuth.vendor() == "Twilio") {
                    Voice.unregister(
                        vendorAuth.accessToken(),
                        Voice.RegistrationChannel.FCM,
                        token,
                        unregistrationListener()
                    )
                }
            }
    }

    override suspend fun handleIncomingPushNotification(
        payload: Map<String, String>,
        notificationListener: IncomingCallNotificationListener
    ): Boolean {
        // set up Twilio MessageListener and pass on its callbacks to the notification listener
        val messageListener = twilioMessageListener(
            callInviteCallback = { callInvite ->
                val callAccepted: (IncomingCall, ActiveCallListener) ->
                Unit = { incomingCall, listener ->
                    val callId = UUID.randomUUID()
                    val connected = { vendorCall: TwilioVendorCall ->
                        activeVoiceCall = ActiveVoiceCall(
                            context,
                            incomingCallAccessToken,
                            incomingCall.localNumber,
                            incomingCall.remoteNumber,
                            callId,
                            vendorCall, listener
                        )
                        listener.activeVoiceCallDidConnect(activeVoiceCall)
                    }

                    val connectionFailed: ((Exception) -> Unit) = { e: Exception ->
                        listener.activeVoiceCallDidFailToConnect(
                            SudoTelephonyException.CallingFailedToConnectException(cause = e)
                        )
                    }

                    val disconnected: ((Exception?) -> Unit) = { e: Exception? ->
                        activeVoiceCall.stopAudioDeviceListener()
                        listener.activeVoiceCallDidDisconnect(
                            activeVoiceCall,
                            e?.let {
                                SudoTelephonyException.CallingDisconnectedException(cause = it)
                            }
                        )
                        listener.connectionStatusChanged(TelephonySubscriber.ConnectionState.DISCONNECTED)
                    }
                    try {
                        TwilioVendorCall(
                            context,
                            callInvite,
                            connected,
                            connectionFailed,
                            disconnected
                        )
                    } catch (e: Exception) {
                        listener.activeVoiceCallDidFailToConnect(
                            SudoTelephonyException.CallingFailedToAcceptIncomingCallException(cause = e)
                        )
                    }
                }
                val callDeclined: (IncomingCall) -> Unit = {
                    callInvite.reject(context)
                }
                val incomingCall = IncomingCall(
                    callInvite.to,
                    callInvite.from ?: "",
                    callInvite.callSid,
                    callAccepted,
                    callDeclined
                )
                notificationListener.incomingCallReceived(incomingCall)
            },
            cancelledCallInviteCallback = { cancelledCallInvite ->
                val incomingCall = IncomingCall(
                    cancelledCallInvite.to,
                    cancelledCallInvite.from ?: "",
                    cancelledCallInvite.callSid,
                    null,
                    null
                )
                notificationListener.incomingCallCanceled(incomingCall, null)
            }
        )
        return Voice.handleMessage(payload, messageListener)
    }

    // Voicemail

    override suspend fun getVoicemails(
        localNumber: PhoneNumber,
        limit: Int?,
        nextToken: String?
    ): TelephonyListToken<Voicemail> {
        val keyInput = VoicemailKeyInput.builder()
            .phoneNumberId(localNumber.id)
            .build()
        val query = ListVoicemailsQuery.builder()
            .key(keyInput)
            .limit(limit)
            .nextToken(nextToken)
            .build()

        val listVoicemailsResponse = graphQLClient.query(query)
            .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
            .enqueue()

        if (listVoicemailsResponse.hasErrors()) {
            throw SudoTelephonyException.GetVoicemailException(
                listVoicemailsResponse.errors().first().message()
            )
        }

        val sealedVoicemails = listVoicemailsResponse.data()?.listVoicemails()?.items()
        val newNextToken = listVoicemailsResponse.data()?.listVoicemails()?.nextToken()

        if (sealedVoicemails == null) {
            logger.error("Failed to retrieve voicemails for phone number ${localNumber.phoneNumber}")
            throw SudoTelephonyException.GetVoicemailException()
        }

        val allVoicemails = sealedVoicemails.map {
            val record = it.fragments().sealedVoicemail()
            Voicemail.createFrom(record, keyManager)
        }

        return TelephonyListToken(allVoicemails, newNextToken)
    }

    override suspend fun getVoicemail(id: String): Voicemail {
        val query = GetVoicemailQuery.builder()
            .id(id)
            .build()

        val getVoicemailResponse = graphQLClient.query(query)
            .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
            .enqueue()

        if (getVoicemailResponse.hasErrors()) {
            throw SudoTelephonyException.GetVoicemailException(
                getVoicemailResponse.errors().first().message()
            )
        }

        val sealedVoicemail = getVoicemailResponse
            .data()
            ?.voicemail
            ?.fragments()
            ?.sealedVoicemail()

        if (sealedVoicemail == null) {
            logger.error("Failed to retrieve voicemail for id $id")
            throw SudoTelephonyException.GetVoicemailException()
        }

        return Voicemail.createFrom(sealedVoicemail, keyManager)
    }

    override suspend fun deleteVoicemail(id: String): String {
        val mutation = DeleteVoicemailMutation.builder()
            .id(id)
            .build()

        val deleteResponse = graphQLClient.mutate(mutation).enqueue()

        if (deleteResponse.hasErrors()) {
            logger.error("Failed to delete voicemail for id $id")
            throw SudoTelephonyException.DeleteVoicemailException(
                deleteResponse.errors().first().message()
            )
        }

        return deleteResponse.data()?.deleteVoicemail() ?: ""
    }

    override suspend fun subscribeToVoicemails(subscriber: VoicemailSubscriber, id: String) {
        logger.debug("Subscribing to Voicemail notifications.")

        val owner = keyManager.getOwner()
        require(owner != null) { "Owner was null. The client may not be signed in." }

        if (this.voicemailSubscriptionManager == null) {
            this.voicemailSubscriptionManager = SubscriptionManager()
        }
        val subscriptionManager = this.voicemailSubscriptionManager
        subscriptionManager?.replaceSubscriber(id, subscriber)
        if (subscriptionManager?.watcher == null) {
            val subscription = OnVoicemailSubscription.builder().owner(owner).build()
            val watcher = graphQLClient.subscribe(subscription)
            subscriptionManager?.watcher = watcher

            watcher.execute(
                object : AppSyncSubscriptionCall.Callback<OnVoicemailSubscription.Data> {
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
                        try {
                            val error = response.errors().firstOrNull()
                            if (error != null) {
                                logger.error("Subscription response contained error: $error")
                            } else {
                                val item = response.data()?.OnVoicemail()
                                if (item != null) {
                                    val sealedVoicemail = item.fragments().sealedVoicemail()
                                    val voicemail = Voicemail
                                        .createFrom(
                                            sealedVoicemail,
                                            keyManager
                                        )

                                    // Notify subscribers
                                    subscriptionManager?.voicemailUpdated(voicemail)
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to process the subscription response: $e")
                        }
                    }
                })

            subscriptionManager?.connectionStatusChanged(
                TelephonySubscriber.ConnectionState.CONNECTED
            )
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
