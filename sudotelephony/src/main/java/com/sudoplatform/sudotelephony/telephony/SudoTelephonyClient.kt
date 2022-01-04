package com.sudoplatform.sudotelephony.telephony

import android.content.Context
import androidx.annotation.Keep
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudotelephony.calling.SudoTelephonyCalling
import com.sudoplatform.sudotelephony.media.MediaObject
import com.sudoplatform.sudotelephony.exceptions.SudoTelephonyException
import com.sudoplatform.sudotelephony.messages.PhoneMessage
import com.sudoplatform.sudotelephony.messages.PhoneMessageConversation
import com.sudoplatform.sudotelephony.messages.PhoneMessageSubscriber
import com.sudoplatform.sudotelephony.phonenumbers.PhoneNumber
import com.sudoplatform.sudotelephony.phonenumbers.PhoneNumberSearchResult
import com.sudoplatform.sudotelephony.phonenumbers.SupportedCountriesResult
import com.sudoplatform.sudouser.SudoUserClient
import java.net.URL
import java.util.Objects

/**
 * Main interface for Telephony operations. Implemented by [DefaultSudoTelephonyClient]
 */
interface SudoTelephonyClient {

    companion object {
        /** Create a [Builder] for [SudoTelephonyClient]. */
        @Keep
        @JvmStatic
        fun builder() = Builder()

        private const val DEFAULT_KEY_NAMESPACE = "tel"
    }

    /**
     * Builder used to construct the [SudoTelephonyClient].
     */
    @Keep
    class Builder internal constructor() {
        private var context: Context? = null
        private var sudoUserClient: SudoUserClient? = null
        private var sudoProfilesClient: SudoProfilesClient? = null
        private var logger: Logger = Logger("telephony", AndroidUtilsLogDriver(LogLevel.DEBUG))

        /**
         * Provide the application context (required input).
         */
        fun setContext(context: Context) = also {
            this.context = context
        }

        /**
         * Provide the implementation of the [SudoUserClient] used to perform
         * sign in and ownership operations (required input).
         */
        fun setSudoUserClient(sudoUserClient: SudoUserClient) = also {
            this.sudoUserClient = sudoUserClient
        }

        /**
         * Provide the implementation of the [SudoProfilesClient] used for sudo management.
         */
        fun setSudoProfilesClient(sudoProfilesClient: SudoProfilesClient) = also {
            this.sudoProfilesClient = sudoProfilesClient
        }

        /**
         * Provide the implementation of the [Logger] used for logging errors (optional input).
         * If a value is not supplied, a default implementation will be used.
         */
        fun setLogger(logger: Logger) = also {
            this.logger = logger
        }

        /**
         * Construct the [SudoTelephonyClient]. Will throw a [NullPointerException] if
         * the [context] and [sudoUserClient] has not been provided.
         */
        @Throws(NullPointerException::class)
        fun build(): SudoTelephonyClient {
            Objects.requireNonNull(context, "Context must be supplied.")
            Objects.requireNonNull(sudoUserClient, "SudoUserClient must be provided.")
            Objects.requireNonNull(sudoProfilesClient, "SudoProfilesClient must be provided.")

            return DefaultSudoTelephonyClient(
                context = context!!,
                sudoUserClient = sudoUserClient!!,
                sudoProfilesClient = sudoProfilesClient!!,
                logger = logger
            )
        }
    }

    /**
     * Checks to see if the client is registered
     */
    fun isRegistered(): Boolean

    /**
     * Resets the current telephony client
     */
    @Throws(SudoTelephonyException::class)
    suspend fun reset()

    /**
     * Get supported countries for searching available phone numbers. The supported countries are returned in ISO 3166-1 alpha-2 format. For example: US, ND, etc.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getSupportedCountries(): SupportedCountriesResult

    /**
     * Get available phone numbers for the specified country code.
     * @param countryCode Supported country code to search on. Code must be formatted in ISO 3166-1 alpha-2 format. For example: US, BR, ND, etc.
     * @param limit Optional limit on the number of available phone numbers returned in the search result. If the limit is not specified, the default value is 10 and the search result will provide no more than 10 phone numbers.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun searchAvailablePhoneNumbers(
        countryCode: String,
        limit: Int? = 10
    ): PhoneNumberSearchResult

    /**
     * Get available phone numbers for the specified country code and prefix.
     * @param countryCode Supported country code to search on. Code must be formatted in the ISO 3166-1 alpha-2 format. For example: US, BR, ND, etc.
     * @param prefix Optional prefix (area code) used to limit the search results. If nil, only the country code will used for the search. If the country code and area code do not match, the resulting list of available phone numbers might be empty.
     * @param limit Optional limit on the number of available phone numbers returned in the search result. If the limit is not specified, the default value is 10 and the search result will provide no more than 10 phone numbers.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun searchAvailablePhoneNumbers(
        countryCode: String,
        prefix: String,
        limit: Int? = 10
    ): PhoneNumberSearchResult

    /**
     * Get available phone numbers for the specified coordinates.
     * @param countryCode Supported country code to search on. Code must be formatted in the ISO 3166-1 alpha-2 format. For example: US, BR, ND, etc.
     * @param longitude Double value of the longitude of the coordinate to search on.
     * @param latitude Double value of the latitude of the coordinate to search on.
     * @param limit Optional limit on the number of available phone numbers returned in the search result. If the limit is not specified, the default value is 10 and the search result will provide no more than 10 phone numbers.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun searchAvailablePhoneNumbers(
        countryCode: String,
        latitude: String,
        longitude: String,
        limit: Int? = 10
    ): PhoneNumberSearchResult

    /**
     * Provision a phone number to the signed in account. Searching for available phone numbers should be done before this call, ensuring that the passed in phone number is available.
     * @param countryCode Supported country of the number to provision. Code must be formatted in the ISO 3166-1 alpha-2 format. For example: US, BR, ND, etc.
     * @param phoneNumber The E164 formatted phone number to provision.. For example: "+14155552671".
     * @param sudoId The ID of the Sudo to provision the phone number for.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun provisionPhoneNumber(
        countryCode: String,
        phoneNumber: String,
        sudoId: String
    ): PhoneNumber

    /**
     * Delete an already provisioned phone number from the signed in account.
     * @param phoneNumber The e164 number of the phone number to delete. For example: "+14155552671".
     */
    @Throws(SudoTelephonyException::class)
    suspend fun deletePhoneNumber(phoneNumber: String): PhoneNumber

    /**
     * List all the phone numbers provisioned by the user account. If the user does not have phone numbers associated with the account, an empty list will be returned.
     * The list can be queried by batches, where the limit and the next batch token can be specified.
     * @param sudoId The sudo id to fetch for.
     * @param limit The limit of the batch to fetch. If none specified, all of them will be returned.
     * @param nextToken The token to use for pagination. Must pass same parameters as the previous call or unexpected results may be returned.
     * @return a TelephonyListToken<PhoneNumber> which contains a list of phone numbers and a new nextToken
     */
    @Throws(SudoTelephonyException::class)
    suspend fun listPhoneNumbers(
        sudoId: String?,
        limit: Int?,
        nextToken: String?
    ): TelephonyListToken<PhoneNumber>

    /**
     * Get the number for the provided ID. If the number is not associated/provisioned by the account, null will be returned.
     * @param id The id of the phone number to search for.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getPhoneNumber(id: String): PhoneNumber

    /**
     * Sends an SMS message from a provisioned phone number to another number
     * @param localNumber The E164 formatted phone number to send the message from. For example: "+14155552671".
     * @param remoteNumber The E164 formatted phone number of the recipient. For example: "+14155552671".
     * @param body The body of the SMS message
     */
    @Throws(SudoTelephonyException::class)
    suspend fun sendSMSMessage(
        localNumber: PhoneNumber,
        remoteNumber: String,
        body: String,
    ): PhoneMessage

    /**
     * Sends an MMS message from a provisioned phone number to another number
     * @param localNumber The provisioned phone number to send the message from
     * @param remoteNumber The E164 formatted phone number of the recipient. For example: "+14155552671".
     * @param body The body of the MMS message
     * @param localUrl The local path of the media to be uploaded.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun sendMMSMessage(
        localNumber: PhoneNumber,
        remoteNumber: String,
        body: String,
        localUrl: URL,
    ): PhoneMessage

    /**
     * Retrieves a message matching the associated id.
     * @param id The id of the message to retrieve.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getMessage(id: String): PhoneMessage

    /**
     * Retrieves a list of messages matching the specified criteria
     * @param localNumber The E164 formatted phone number of the participating sudo. For example: "+14155552671".
     * @param remoteNumber The E164 formatted phone number of the other participant in the phone conversation. For example: "+14155552671".
     * @param limit The maximum number of messages to retrieve.
     * @param nextToken The token to use for pagination.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getMessages(
        localNumber: PhoneNumber,
        remoteNumber: String,
        limit: Int?,
        nextToken: String?,
    ): TelephonyListToken<PhoneMessage>

    /**
     * Retrieves a list of messages matching the specified criteria
     * @param conversationId The ID of the conversation associated with the messages to be retrieved.
     * @param limit The maximum number of messages to retrieve.
     * @param nextToken The token to use for pagination.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getMessages(
        conversationId: String,
        limit: Int?,
        nextToken: String?,
    ): TelephonyListToken<PhoneMessage>

    /**
     * Downloads MMS attachments.
     * @param media The MediaObject that provides the necessary info for retrieving the media
     */
    @Throws(SudoTelephonyException::class)
    suspend fun downloadData(media: MediaObject): ByteArray

    /**
     * Deletes a message matching the associated id.
     * @param id The id of the message to delete.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun deleteMessage(id: String): String

    /**
     * Subscribes to be notified of new `PhoneMessage` objects. If no id is provided,
     * a default id will be used.
     *
     * @param subscriber The subscriber to notify.
     * @param id The unique ID for the subscriber.
     */
    @Throws(SudoTelephonyException::class)
    suspend fun subscribeToMessages(subscriber: PhoneMessageSubscriber, id: String?)

    /**
     * Unsubscribes the specified subscriber so that it no longer receives notifications about
     * new 'PhoneMessage' objects. If no id is provided, all subscribers will be unsubscribed.
     *
     * @param id unique ID for the subscriber.
     */
    @Throws(SudoTelephonyException::class)
    fun unsubscribeFromPhoneMessages(id: String?)

    /**
     * Retrieves the conversation matching the specified criteria
     * @param conversationId The ID of the conversation to be retrieved
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getConversation(
        conversationId: String,
    ): PhoneMessageConversation

    /**
     * Retrieves the conversation matching the specified criteria
     * @param localNumber The phone number of the participating sudo
     * @param remoteNumber The phone number of the other participant in the phone conversation in e164 format
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getConversation(
        localNumber: PhoneNumber,
        remoteNumber: String,
    ): PhoneMessageConversation

    /**
     * Retrieves a list of conversations matching the specified criteria
     * @param localNumber The phone number of the participating sudo
     * @param limit The maximum number of conversations to retrieve
     * @param nextToken The token to use for pagination
     */
    @Throws(SudoTelephonyException::class)
    suspend fun getConversations(
        localNumber: PhoneNumber,
        limit: Int?,
        nextToken: String?,
    ): TelephonyListToken<PhoneMessageConversation>

    /**
     * Interface for calling related actions
     */
    val calling: SudoTelephonyCalling
}
