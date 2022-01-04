package com.sudoplatform.sudotelephony.calling

import com.sudoplatform.sudotelephony.calllisteners.ActiveCallListener
import com.sudoplatform.sudotelephony.calllisteners.IncomingCallNotificationListener
import com.sudoplatform.sudotelephony.callrecords.CallRecord
import com.sudoplatform.sudotelephony.callrecords.CallRecordSubscriber
import com.sudoplatform.sudotelephony.exceptions.SudoTelephonyException
import com.sudoplatform.sudotelephony.phonenumbers.PhoneNumber
import com.sudoplatform.sudotelephony.telephony.TelephonyListToken
import com.sudoplatform.sudotelephony.voicemail.Voicemail
import com.sudoplatform.sudotelephony.voicemail.VoicemailSubscriber

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
    @Throws(SudoTelephonyException::class)
    suspend fun createVoiceCall(
        localNumber: PhoneNumber,
        remoteNumber: String,
        listener: ActiveCallListener
    )

    /**
     * Retrieves a call record.
     * @param callRecordId The id of the call record to be retrieved
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getCallRecord(callRecordId: String): CallRecord

    /**
     * Retrieves call records for a given phone number
     * @param localNumber The phone number to fetch phone records for
     * @param limit The limit of the batch to fetch. If none specified, all call records will be returned.
     * @param nextToken The token to use for pagination.
     * @return TelephonyListToken<CallRecord> with the list of fetched call records and a new nextToken
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getCallRecords(
        localNumber: PhoneNumber,
        limit: Int?,
        nextToken: String?,
    ): TelephonyListToken<CallRecord>

    /**
     * Subscribes to be notified of new `CallRecord` objects. If no id is provided,
     * a default id will be used.
     *
     * @param subscriber The subscriber to notify.
     * @param id The unique ID for the subscriber.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun subscribeToCallRecords(subscriber: CallRecordSubscriber, id: String)

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
     * @return String containing the id of the deleted Call Record if successful
     */
    @Throws(SudoTelephonyException::class)
    suspend fun deleteCallRecord(id: String): String

    /**
     * Registers for incoming call push notifications with the given token
     * @param token FCM token obtained from FirebaseInstanceId
     */
    @Throws(SudoTelephonyException::class)
    suspend fun registerForIncomingCalls(token: String)

    /**
     * De-registers from incoming call push notifications with the given token
     * @param token FCM token obtained from FirebaseInstanceId
     */
    @Throws(SudoTelephonyException::class)
    suspend fun deregisterForIncomingCalls(token: String)

    /**
     * Handles an incoming push notification with the given payload
     * @param payload The data of the push notification message
     * @param notificationListener IncomingCallNotificationListener for receiving incoming call events
     * @return Boolean indicating whether or not the notification was handled successfully
     */
    @Throws(SudoTelephonyException::class)
    suspend fun handleIncomingPushNotification(
        payload: Map<String, String>,
        notificationListener: IncomingCallNotificationListener
    ): Boolean

    /**
     * Retrieves a list of `Voicemail`s for the given `PhoneNumber`.
     * @param localNumber The `PhoneNumber` to fetch `Voicemail`s for.
     * @param limit The maximum number of `Voicemail`s to retrieve per page.
     * @param nextToken The token to use for pagination.
     * @return TelephonyListToken<Voicemail> with the list of fetched voicemails and a new nextToken
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getVoicemails(
        localNumber: PhoneNumber,
        limit: Int?,
        nextToken: String?
    ): TelephonyListToken<Voicemail>

    /** Retrieves the voicemail with the specified `id`.
     * @param id The `id` of the `Voicemail` to retrieve.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getVoicemail(id: String): Voicemail

    /**
     * Deletes a voicemail matching the given `id`.
     * @param id The `id` of the `Voicemail` to delete.
     * @return String containing the id of the deleted voicemail if successful
     */
    @Throws(SudoTelephonyException::class)
    suspend fun deleteVoicemail(id: String): String

    /**
     * Subscribe to new, updated, and deleted `Voicemail` records.
     * @param subscriber The subscriber to notify.
     * @param id The unique ID for the subscriber.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun subscribeToVoicemails(subscriber: VoicemailSubscriber, id: String)

    /**
     * Unsubscribes the specified subscriber so that it no longer receives notifications about
     * new 'Voicemail' objects. If no id is provided, all subscribers will be unsubscribed.
     *
     * @param id unique ID for the subscriber.
     */
    @Throws(SudoTelephonyException::class)
    fun unsubscribeFromVoicemails(id: String?)
}
