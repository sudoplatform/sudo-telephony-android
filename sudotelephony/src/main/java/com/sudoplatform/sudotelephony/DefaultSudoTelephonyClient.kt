package com.sudoplatform.sudotelephony

import android.content.Context
import android.provider.Settings
import android.webkit.MimeTypeMap
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferType
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.*
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudoprofiles.GetOwnershipProofResult
import com.sudoplatform.sudoprofiles.Sudo
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudotelephony.type.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.charset.Charset
import java.security.KeyPair
import java.time.Instant
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule


/**
 * Main interface for Telephony operations. Implemented by [DefaultSudoTelephonyClient]
 */
interface SudoTelephonyClient {
    /**
     * Checks to see if the client is registered
     */
    fun isRegistered(): Boolean

    /**
     * Resets the current telephony client
     */
    fun reset()

    /**
     * Get supported countries for searching available phone numbers. The supported countries are returned in ISO 3166-1 alpha-2 format. For example: US, ND, etc.
     * @param callback Completion callback that returns supported countries.
     */
    fun getSupportedCountries(callback: (Result<SupportedCountriesResult>) -> Unit)

    /**
     * Get available phone numbers for the specified country code.
     * @param countryCode Supported country code to search on. Code must be formatted in ISO 3166-1 alpha-2 format. For example: US, BR, ND, etc.
     * @param limit Optional limit on the number of available phone numbers returned in the search result. If the limit is not specified, the default value is 10 and the search result will provide no more than 10 phone numbers.
     * @param callback Completion callback providing a search result containing available phone numbers with the given country code.
     */
    fun searchAvailablePhoneNumbers(countryCode: String, limit: Int? = 10, callback: (Result<PhoneNumberSearchResult>) -> Unit )

    /**
     * Get available phone numbers for the specified country code and prefix.
     * @param countryCode Supported country code to search on. Code must be formatted in the ISO 3166-1 alpha-2 format. For example: US, BR, ND, etc.
     * @param prefix Optional prefix (area code) used to limit the search results. If nil, only the country code will used for the search. If the country code and area code do not match, the resulting list of available phone numbers might be empty.
     * @param limit Optional limit on the number of available phone numbers returned in the search result. If the limit is not specified, the default value is 10 and the search result will provide no more than 10 phone numbers.
     * @param callback Completion callback providing a search result containing available phone numbers with the given country code and prefix.
     */
    fun searchAvailablePhoneNumbers(countryCode: String, prefix: String, limit: Int? = 10, callback: (Result<PhoneNumberSearchResult>) -> Unit )

    /**
     * Get available phone numbers for the specified coordinates.
     * @param countryCode Supported country code to search on. Code must be formatted in the ISO 3166-1 alpha-2 format. For example: US, BR, ND, etc.
     * @param longitude Double value of the longitude of the coordinate to search on.
     * @param latitude Double value of the latitude of the coordinate to search on.
     * @param limit Optional limit on the number of available phone numbers returned in the search result. If the limit is not specified, the default value is 10 and the search result will provide no more than 10 phone numbers.
     * @param callback Completion callback providing a search result containing available phone numbers with the given country code and coordinates.
     */
    fun searchAvailablePhoneNumbers(countryCode: String, latitude: String, longitude: String, limit: Int? = 10, callback: (Result<PhoneNumberSearchResult>) -> Unit )

    /**
     * Provision a phone number to the signed in account. Searching for available phone numbers should be done before this call, ensuring that the passed in phone number is available.
     * @param countryCode Supported country of the number to provision. Code must be formatted in the ISO 3166-1 alpha-2 format. For example: US, BR, ND, etc.
     * @param phoneNumber The E164 formatted phone number to provision.. For example: "+14155552671".
     * @param sudoId The ID of the Sudo to provision the phone number for.
     * @param callback Completion callback that provides the provisioned phone number.
     */
    fun provisionPhoneNumber(countryCode: String, phoneNumber: String, sudoId: String, callback: (Result<PhoneNumber>) -> Unit )

    /**
     * Delete an already provisioned phone number from the signed in account.
     * @param phoneNumber The e164 number of the phone number to delete. For example: "+14155552671".
     * @param callback Completion callback that provides the phone number string on success.
     */
    fun deletePhoneNumber(phoneNumber: String, callback: (Result<PhoneNumber>) -> Unit)

    /**
     * List all the phone numbers provisioned by the user account. If the user does not have phone numbers associated with the account, an empty list will be returned.
     * The list can be queried by batches, where the limit and the next batch token can be specified.
     * @param sudoId The sudo id to fetch for.
     * @param limit The limit of the batch to fetch. If none specified, all of them will be returned.
     * @param nextToken The token to use for pagination. Must pass same parameters as the previous call or unexpected results may be returned.
     * @param callback Completion callback that provides a list of phone numbers or an error if there was a failure.
     */
    fun listPhoneNumbers(sudoId: String?, limit: Int?, nextToken: String?, callback: (Result<TelephonyListToken<PhoneNumber>>) -> Unit)

    /**
     * Get the number for the provided ID. If the number is not associated/provisioned by the account, null will be returned.
     * @param id The id of the phone number to search for.
     * @param callback Completion callback to handle the result.
     */
    fun getPhoneNumber(id: String, callback: (Result<PhoneNumber>) -> Unit)

    /**
     * Sends an SMS message from a provisioned phone number to another number
     * @param localNumber The E164 formatted phone number to send the message from. For example: "+14155552671".
     * @param remoteNumber The E164 formatted phone number of the recipient. For example: "+14155552671".
     * @param body The body of the SMS message
     * @param callback Completion callback providing the sent message or an error if there was a failure.
     */
    fun sendSMSMessage(localNumber: PhoneNumber, remoteNumber: String, body: String, callback: (Result<PhoneMessage>) -> Unit)

    /**
     * Sends an MMS message from a provisioned phone number to another number
     * @param localNumber The provisioned phone number to send the message from
     * @param remoteNumber The E164 formatted phone number of the recipient. For example: "+14155552671".
     * @param body The body of the MMS message
     * @param localUrl The local path of the media to be uploaded.
     * @param callback Completion callback providing the sent message or an error if there was a failure.
     */
    fun sendMMSMessage(localNumber: PhoneNumber, remoteNumber: String, body: String, localUrl: URL, callback: (Result<PhoneMessage>) -> Unit)

    /**
     * Retrieves a message matching the associated id.
     * @param id The id of the message to retrieve.
     * @param callback Completion callback providing the message or an error if it could not be retrieved.
     */
    fun getMessage(id: String, callback: (Result<PhoneMessage>) -> Unit)

    /**
     * Retrieves a list of messages matching the specified criteria
     * @param localNumber The E164 formatted phone number of the participating sudo. For example: "+14155552671".
     * @param remoteNumber The E164 formatted phone number of the other participant in the phone conversation. For example: "+14155552671".
     * @param limit The maximum number of messages to retrieve.
     * @param nextToken The token to use for pagination.
     * @param callback Completion callback providing a list of messages or an error if there was a failure.
     */
    fun getMessages(localNumber: PhoneNumber, remoteNumber: String, limit: Int?, nextToken: String?, callback: (Result<TelephonyListToken<PhoneMessage>>) -> Unit)

    /**
     * Retrieves a list of messages matching the specified criteria
     * @param conversationId The ID of the conversation associated with the messages to be retrieved.
     * @param limit The maximum number of messages to retrieve.
     * @param nextToken The token to use for pagination.
     * @param callback Completion callback providing a list of messages or an error if there was a failure.
     */
    fun getMessages(conversationId: String, limit: Int?, nextToken: String?, callback: (Result<TelephonyListToken<PhoneMessage>>) -> Unit)

    /**
     * Downloads MMS attachments.
     * @param media The MediaObject that provides the necessary info for retrieving the media
     * @param callback Completion handler to be called when the data is retrieved or fails to be retrieved. The result object contains the raw data or an error
     */
    fun downloadData(media: MediaObject, callback: (Result<ByteArray>) -> Unit)

    /**
     * Deletes a message matching the associated id.
     * @param id The id of the message to delete.
     * @param callback Completion callback providing the id of the deleted message or an error if the message could not be deleted.
     */
    fun deleteMessage(id: String, callback: (Result<String>) -> Unit)

    /**
     * Subscribes to be notified of new `PhoneMessage` objects. If no id is provided,
     * a default id will be used.
     *
     * @param subscriber The subscriber to notify.
     * @param id The unique ID for the subscriber.
     */
    fun subscribeToPhoneMessages(subscriber: PhoneMessageSubscriber, id: String?)

    /**
     * Unsubscribes the specified subscriber so that it no longer receives notifications about
     * new 'PhoneMessage' objects. If no id is provided, all subscribers will be unsubscribed.
     *
     * @param id unique ID for the subscriber.
     */
    fun unsubscribeFromPhoneMessages(id: String?)

    /**
     * Retrieves the conversation matching the specified criteria
     * @param conversationId The ID of the conversation to be retrieved
     * @param callback Completion callback providing the conversation or an error if it could not be retrieved.
     */
    fun getConversation(conversationId: String, callback: (Result<PhoneMessageConversation>) -> Unit)

    /**
     * Retrieves the conversation matching the specified criteria
     * @param localNumber The phone number of the participating sudo
     * @param remoteNumber The phone number of the other participant in the phone conversation in e164 format
     * @param callback Completion callback providing the conversation or an error if it could not be retrieved.
     */
    fun getConversation(localNumber: PhoneNumber, remoteNumber: String, callback: (Result<PhoneMessageConversation>) -> Unit)

    /**
     * Retrieves a list of conversations matching the specified criteria
     * @param localNumber The phone number of the participating sudo
     * @param limit The maximum number of conversations to retrieve
     * @param nextToken The token to use for pagination
     * @param callback Completion callback providing a list of conversations or an error if there was a failure.
     */
    fun getConversations(localNumber: PhoneNumber, limit: Int?, nextToken: String?, callback: (Result<TelephonyListToken<PhoneMessageConversation>>) -> Unit)
}

interface SudoAuthenticationProvider {
    fun getLatestAuthToken(): String?
}

/**
 * Main implementation of [SudoTelephonyClient] interface
 */
class DefaultSudoTelephonyClient : SudoTelephonyClient {

    companion object {
        private const val CONFIG_NAMESPACE_IDENTITY_SERVICE = "identityService"
        private const val CONFIG_NAMESPACE_API_SERVICE = "apiService"

        private const val TELEPHONY_SERVICE_REGION = "region"
        private const val TELEPHONY_SERVICE_BUCKET = "bucket"
        private const val TELEPHONY_SERVICE_TRANSIENT_BUCKET = "transientBucket"

        private const val KEY_MANAGER_DEFAULT_KEY_TAG = "com.sudoplatform"
        private const val KEY_MANAGER_KEY_ID_NAME = "com.sudoplatform.keyId"
        private const val KEY_MANAGER_KEYRING_ID_NAME = "com.sudoplatform.keyRingId"

        private const val SUDO_PROFILES_TELEPHONY_AUDIENCE =
            "sudoplatform.sudotelephony.phone-number"

        private const val RSA_ENCRYPTION_OAEP_AES_CBC = "RSAEncryptionOAEPAESCBC"

        private const val DEFAULT_MESSAGE_SUBSCRIBER_ID = "DEFAULT_MESSAGE_SUBSCRIBER"

        internal val timber
            get() = Timber.tag("TelephonySDK")
    }

    private val graphQLClient: AWSAppSyncClient
    private val applicationContext: Context
    private val region: String
    private val s3Region: String
    private val s3Bucket: String
    private val transientS3Bucket: String
    private val sudoUserClient: SudoUserClient
    private val sudoProfilesClient: SudoProfilesClient
    private val keyManager: KeyManagerInterface
    private val s3Client: AmazonS3Client
    private val transferUtility: TransferUtility
    private val onMessageSubscriptionManager: SubscriptionManager<OnMessageReceivedSubscription.Data>

    constructor(
        context: Context,
        sudoUserClient: SudoUserClient,
        sudoProfilesClient: SudoProfilesClient
    ) {

        val configManager = DefaultSudoConfigManager(context)
        val identityServiceConfig = configManager.getConfigSet(CONFIG_NAMESPACE_IDENTITY_SERVICE)
        require(identityServiceConfig != null) { "User Client configuration not found." }
        val profilesServiceConfig = configManager.getConfigSet(CONFIG_NAMESPACE_IDENTITY_SERVICE)
        require(profilesServiceConfig != null) { "Profiles Client configuration not found." }
        val telephonyServiceConfig = configManager.getConfigSet(CONFIG_NAMESPACE_API_SERVICE)
        require(telephonyServiceConfig != null) { "Telephony config not found" }

        this.region = telephonyServiceConfig[TELEPHONY_SERVICE_REGION] as String
        this.s3Region = identityServiceConfig[TELEPHONY_SERVICE_REGION] as String
        this.s3Bucket = identityServiceConfig[TELEPHONY_SERVICE_BUCKET] as String
        this.transientS3Bucket = identityServiceConfig[TELEPHONY_SERVICE_TRANSIENT_BUCKET] as String

        this.sudoUserClient = sudoUserClient
        this.sudoProfilesClient = sudoProfilesClient
        this.s3Client = AmazonS3Client(
            this.sudoUserClient.getCredentialsProvider(),
            Region.getRegion(this.s3Region)
        )
        this.transferUtility = TransferUtility.builder()
            .context(context)
            .s3Client(this.s3Client)
            .defaultBucket(this.transientS3Bucket)
            .build()

        this.graphQLClient = ApiClientManager.getClient(context, this.sudoUserClient)

        this.keyManager = KeyManagerFactory(context).createAndroidKeyManager()
        this.applicationContext = context

        this.onMessageSubscriptionManager = SubscriptionManager()
    }

    override fun isRegistered() = sudoUserClient.isRegistered()

    override fun reset() {
        this.transferUtility.cancelAllWithType(TransferType.ANY)
        this.graphQLClient.clearCaches()
        sudoProfilesClient.reset()
        sudoUserClient.reset()
        this.keyManager.removeAllKeys()
    }

    private fun getIdentityId(): String {
        return this.sudoUserClient.getCredentialsProvider().identityId
    }

    //region Keys
    private fun generateKeyPair(callback: (Result<com.sudoplatform.sudotelephony.fragment.PublicKey>) -> Unit) {
        val identityId = this@DefaultSudoTelephonyClient.getIdentityId()

        val keyRingId = UUID.randomUUID().toString()
        val keyId = UUID.randomUUID().toString()

        var publicKey: String? = null
        try {
            this@DefaultSudoTelephonyClient.keyManager.generateKeyPair(identityId)
            val pubKey = this@DefaultSudoTelephonyClient.keyManager.getPublicKeyData(identityId) ?: throw (KeyManagerException(
                "Failed to generate key pair"
            ))
            publicKey = Base64.getEncoder().encodeToString(pubKey)
        } catch (error: KeyManagerException) {
            timber.e(error, "Failed to generate key pair")
        }

        if (publicKey == null) {
            val error = TelephonyCreatePublicKeyException()
            timber.e(error, "Failed to create public key")
            callback.runOnUiThread()(Result.Error(error))
            return
        }

        this@DefaultSudoTelephonyClient.keyManager.addPassword(
            keyId.toByteArray(Charset.forName("UTF-8")),
            KEY_MANAGER_KEY_ID_NAME + identityId
        )
        this@DefaultSudoTelephonyClient.keyManager.addPassword(
            keyRingId.toByteArray(Charset.forName("UTF-8")),
            KEY_MANAGER_KEYRING_ID_NAME + identityId
        )
        val publicKeyInput = CreatePublicKeyInput.builder()
            .publicKey(publicKey!!)
            .keyId(keyId)
            .keyRingId(keyRingId)
            .algorithm(RSA_ENCRYPTION_OAEP_AES_CBC)
            .build()

        val mutation = CreatePublicKeyMutation.builder().input(publicKeyInput).build()
        graphQLClient.mutate(mutation)
            .enqueue(object : GraphQLCall.Callback<CreatePublicKeyMutation.Data>() {
                override fun onResponse(response: Response<CreatePublicKeyMutation.Data>) {
                    val pubKey =
                        response.data()?.createPublicKeyForTelephony?.fragments()?.publicKey()
                    if (pubKey != null) {
                        callback.runOnUiThread()(Result.Success(pubKey))
                    } else {
                        val error = TelephonyCreatePublicKeyException()
                        timber.e(error, "Failed to create public key")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                }

                override fun onFailure(e: ApolloException) {
                    val error = TelephonyCreatePublicKeyException(e)
                    timber.e(error, "Failed to create public key")
                    callback.runOnUiThread()(Result.Error(error))
                }
            })
    }

    private fun getKeyPair(): KeyPair? {
        val identityId = this.getIdentityId()
        val privateKey = this.keyManager.getPrivateKey(identityId) ?: return null
        val publicKey = this.keyManager.getPublicKey(identityId) ?: return null

        return KeyPair(publicKey, privateKey)
    }

    private fun getKeyId(): String? {
        val identityId = this.getIdentityId()
        return this.keyManager.getPassword(KEY_MANAGER_KEY_ID_NAME + identityId)
            .toString(Charset.forName("UTF-8"))
    }

    private fun getKeyRingId(): String? {
        val identityId = this.getIdentityId()
        return this.keyManager.getPassword(KEY_MANAGER_KEYRING_ID_NAME + identityId)
            .toString(Charset.forName("UTF-8"))
    }

    private fun deleteKeyPair() {
        try {
            val identityId = this.getIdentityId()
            this.keyManager.deleteKeyPair(identityId)
        } catch (error: Exception) {
            timber.e(error, "Failed to delete key pair")
        }
    }

    private fun decryptSealedData(data: ByteArray): ByteArray {
        if (data.size < 1) {
            return data
        }

        try {
            val encryptedCipherKey = data.copyOfRange(0, 256)
            val cipherKey = this.keyManager.decryptWithPrivateKey(
                this.getIdentityId(),
                encryptedCipherKey,
                KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1
            )
            val symmetricKey = cipherKey.copyOfRange(0, 32)
            val iv = ByteArray(16, { 0 })
            val encryptedData = data.copyOfRange(256, data.size)

            return keyManager.decryptWithSymmetricKey(symmetricKey, encryptedData, iv)
        } catch (error: KeyManagerException) {
            timber.e(error, "Failed to decrypt sealed data")
            throw(TelephonyDecryptSealedDataException(error))
        }
    }
    //endregion

    // region Telephony search
    override fun getSupportedCountries(callback: (Result<SupportedCountriesResult>) -> Unit) {
        val query = SupportedCountriesQuery.builder()
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<SupportedCountriesQuery.Data>() {
                    override fun onResponse(response: Response<SupportedCountriesQuery.Data>) {
                        val result = response.data()?.phoneNumberCountries
                        if (result == null) {
                            return callback.runOnUiThread()(Result.Absent)
                        }

                        callback.runOnUiThread()(Result.Success(SupportedCountriesResult(result.countries())))
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyGetPhoneNumberException(e)
                        timber.e(error, "Failed to get supported countries")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    private fun <Data : Operation.Data?, Variables : Operation.Variables?> runPhoneSearchMutation(
        mutation: Mutation<Data, Data, Variables>,
        callback: (Result<PhoneNumberSearchResult>) -> Unit,
        extractSearchId: (Data) -> String?
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient
                .mutate(mutation)
                .enqueue(PhoneSearchCallback<Data>(graphQLClient, extractSearchId, callback))
        }
    }

    override fun searchAvailablePhoneNumbers(
        countryCode: String,
        limit: Int?,
        callback: (Result<PhoneNumberSearchResult>) -> Unit
    ) {
        val searchMutation = AvailablePhoneNumbersForCountryCodeMutation.builder()
            .limit(limit)
            .country(countryCode)
            .build()

        runPhoneSearchMutation(searchMutation, callback) {
            it.searchPhoneNumbers()?.fragments()?.availablePhoneNumberResult()?.id()
        }
    }

    override fun searchAvailablePhoneNumbers(
        countryCode: String,
        prefix: String,
        limit: Int?,
        callback: (Result<PhoneNumberSearchResult>) -> Unit
    ) {
        when (prefix) {
            "" -> this.searchAvailablePhoneNumbers(countryCode, limit, callback)
            else -> {
                val searchMutation = AvailablePhoneNumbersForPrefixMutation.builder()
                    .country(countryCode)
                    .prefix(prefix)
                    .limit(limit)
                    .build()

                runPhoneSearchMutation(searchMutation, callback) {
                    it.searchPhoneNumbers()?.fragments()?.availablePhoneNumberResult()?.id()
                }
            }
        }
    }

    override fun searchAvailablePhoneNumbers(
        countryCode: String,
        latitude: String,
        longitude: String,
        limit: Int?,
        callback: (Result<PhoneNumberSearchResult>) -> Unit
    ) {
        val searchMutation = AvailablePhoneNumbersForGPSMutation.builder()
            .country(countryCode)
            .latitude(latitude)
            .longitude(longitude)
            .limit(limit)
            .build()

        runPhoneSearchMutation(searchMutation, callback) {
            it.searchPhoneNumbers()?.fragments()?.availablePhoneNumberResult()?.id()
        }
    }

    //endregion

    //region Phone phoneNumber CRUD-ops
    override fun provisionPhoneNumber(
        countryCode: String,
        phoneNumber: String,
        sudoId: String,
        callback: (Result<PhoneNumber>) -> Unit
    ) {
        timber.i("Provisioning phone phoneNumber $phoneNumber")

        GlobalScope.launch(Dispatchers.IO) {
            if (this@DefaultSudoTelephonyClient.getKeyPair() == null) {
                generateKeyPair() { result ->
                    when (result) {
                        is Result.Success -> {
                            val keyRingId = this@DefaultSudoTelephonyClient.getKeyRingId()
                            if (keyRingId == null) {
                                val error = TelephonyNumberProvisionException()
                                timber.e(error, "Failed provisioning phoneNumber $phoneNumber")
                                callback.runOnUiThread()(Result.Error(error))
                            } else {
                                val sudo = Sudo(sudoId)
                                this@DefaultSudoTelephonyClient.sudoProfilesClient.getOwnershipProof(
                                    sudo,
                                    SUDO_PROFILES_TELEPHONY_AUDIENCE
                                ) { result ->
                                    when (result) {
                                        is GetOwnershipProofResult.Success -> {
                                            val proofs = result.jwt
                                            val provisionInput = ProvisionPhoneNumberInput.builder()
                                                .country(countryCode)
                                                .phoneNumber(phoneNumber)
                                                .ownerProofs(listOf(proofs))
                                                .keyRingId(keyRingId)
                                                .build()

                                            provisionPhoneNumber(provisionInput, callback)
                                        }
                                        else -> {
                                            val error = TelephonyNumberProvisionException()
                                            timber.e(
                                                error,
                                                "Failed provisioning phoneNumber $phoneNumber"
                                            )
                                            callback.runOnUiThread()(Result.Error(error))
                                        }
                                    }
                                }
                            }
                        }
                        is Result.Error -> {
                            val error = TelephonyCreatePublicKeyException()
                            timber.e(
                                error,
                                "Failed provisioning phoneNumber $phoneNumber, key generation failed"
                            )
                            callback.runOnUiThread()(Result.Error(error))
                        }
                    }
                }
            } else {
                val keyRingId = this@DefaultSudoTelephonyClient.getKeyRingId()
                if (keyRingId == null) {
                    val error = TelephonyNumberProvisionException()
                    timber.e(error, "Failed provisioning phoneNumber $phoneNumber")
                    callback.runOnUiThread()(Result.Error(error))
                } else {
                    val sudo = Sudo(sudoId)
                    this@DefaultSudoTelephonyClient.sudoProfilesClient.getOwnershipProof(
                        sudo,
                        SUDO_PROFILES_TELEPHONY_AUDIENCE
                    ) { result ->
                        when (result) {
                            is GetOwnershipProofResult.Success -> {
                                val proofs = result.jwt
                                val provisionInput = ProvisionPhoneNumberInput.builder()
                                    .country(countryCode)
                                    .phoneNumber(phoneNumber)
                                    .ownerProofs(listOf(proofs))
                                    .keyRingId(keyRingId)
                                    .build()

                                provisionPhoneNumber(provisionInput, callback)
                            }
                            else -> {
                                val error = TelephonyNumberProvisionException()
                                timber.e(
                                    error, "Failed provisioning phoneNumber $phoneNumber"
                                )
                                callback.runOnUiThread()(Result.Error(error))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun provisionPhoneNumber(
        provisionInput: ProvisionPhoneNumberInput,
        callback: (Result<PhoneNumber>) -> Unit
    ) {
        val provisionMutation = ProvisionPhoneNumberMutation.builder()
            .input(provisionInput)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.mutate(provisionMutation)
                .enqueue(object : GraphQLCall.Callback<ProvisionPhoneNumberMutation.Data>() {
                    override fun onResponse(response: Response<ProvisionPhoneNumberMutation.Data>) {
                        val data = response.data()?.provisionPhoneNumber()
                        // handle provision error
                        if (data == null) {
                            if (response.hasErrors() &&
                                response.errors().first().message() == "sudoplatform.telephony.NoPhoneNumberEntitlementError"
                            ) {
                                return callback.runOnUiThread()(
                                    Result.Error(
                                        TelephonyInsufficientEntitlementException()
                                    )
                                )
                            }
                            return callback.runOnUiThread()(
                                Result.Error(
                                    TelephonyNumberProvisionException()
                                )
                            )
                        }

                        val searchId = data.fragments().phoneNumber().id()
                        val timer = Timer()
                        timer.schedule(Date(), 1000) {
                            val query = PhoneNumberQuery(searchId)
                            graphQLClient.query(query)
                                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                                .enqueue(object :
                                    GraphQLCall.Callback<PhoneNumberQuery.Data>() {
                                    override fun onResponse(response: Response<PhoneNumberQuery.Data>) {
                                        val number = response.data()?.phoneNumber ?: return

                                        if (number.fragments().phoneNumber().state() != PhoneNumberState.COMPLETE) {
                                            return
                                        }

                                        val phoneNumber = PhoneNumber(
                                            id = number.fragments().phoneNumber().id(),
                                            phoneNumber = number.fragments().phoneNumber().phoneNumber(),
                                            state = number.fragments().phoneNumber().state(),
                                            version = number.fragments().phoneNumber().version(),
                                            created = Date(number.fragments().phoneNumber().createdAtEpochMs().toLong()),
                                            updated = Date(number.fragments().phoneNumber().updatedAtEpochMs().toLong())
                                        )

                                        this@schedule.cancel()
                                        callback.runOnUiThread()(Result.Success(phoneNumber))
                                    }

                                    override fun onFailure(e: ApolloException) {
                                        val error = TelephonyNumberProvisionException(e)
                                        timber.e(
                                            error,
                                            "Failed provisioning number ${provisionInput.phoneNumber()}"
                                        )
                                        this@schedule.cancel()
                                        callback.runOnUiThread()(Result.Error(error))
                                    }
                                })
                        }
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyNumberProvisionException(e)
                        timber.e(
                            error,
                            "Failed provisioning number ${provisionInput.phoneNumber()}"
                        )
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun deletePhoneNumber(phoneNumber: String, callback: (Result<PhoneNumber>) -> Unit) {
        val deprovisionPhoneNumberMutation = DeprovisionPhoneNumberMutation.builder()
            .input(
                DeprovisionPhoneNumberInput
                    .builder()
                    .phoneNumber(phoneNumber)
                    .build()
            )
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.mutate(deprovisionPhoneNumberMutation)
                .enqueue(object : GraphQLCall.Callback<DeprovisionPhoneNumberMutation.Data>() {
                    override fun onResponse(response: Response<DeprovisionPhoneNumberMutation.Data>) {
                        val number =
                            response.data()?.deprovisionPhoneNumber()?.fragments()?.phoneNumber()
                        if (number == null) {
                            return callback.runOnUiThread()(
                                Result.Error(
                                    TelephonyNumberDeletionException()
                                )
                            )
                        }

                        val deletedNumber = PhoneNumber(
                            id = number.id(),
                            phoneNumber = number.phoneNumber(),
                            state = number.state(),
                            version = number.version(),
                            created = Date(number.createdAtEpochMs().toLong()),
                            updated = Date(number.updatedAtEpochMs().toLong())
                        )

                        callback.runOnUiThread()(Result.Success(deletedNumber))
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyNumberDeletionException(e)
                        timber.e(error, "Failed to delete phoneNumber $phoneNumber")

                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun listPhoneNumbers(
        sudoId: String?,
        limit: Int?,
        nextToken: String?,
        callback: (Result<TelephonyListToken<PhoneNumber>>) -> Unit
    ) {
        var phoneNumberFilter: PhoneNumberFilterInput? = null
        if (sudoId != null) {
            phoneNumberFilter = PhoneNumberFilterInput.builder()
                .sudoOwner(IDFilterInput.builder().eq(sudoId).build())
                .build()
        }

        val query = PhoneNumbersQuery.builder()
            .filter(phoneNumberFilter)
            .limit(limit)
            .nextToken(nextToken)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<PhoneNumbersQuery.Data>() {

                    override fun onResponse(response: Response<PhoneNumbersQuery.Data>) {
                        val numbers = response.data()?.listPhoneNumbers()?.items()
                        val newNextToken = response.data()?.listPhoneNumbers()?.nextToken()
                        if (numbers == null) {
                            val error = TelephonyGetAllPhoneNumbersException()
                            timber.e(error, "Failed to list phone numbers")
                            callback.runOnUiThread()(Result.Error(error))
                            return
                        }

                        val allNumbers = numbers.map { number ->
                            PhoneNumber(
                                id = number.fragments().phoneNumber().id(),
                                phoneNumber = number.fragments().phoneNumber().phoneNumber(),
                                state = number.fragments().phoneNumber().state(),
                                version = number.fragments().phoneNumber().version(),
                                created = Date(number.fragments().phoneNumber().createdAtEpochMs().toLong()),
                                updated = Date(number.fragments().phoneNumber().updatedAtEpochMs().toLong())
                            )
                        }

                        callback.runOnUiThread()(
                            Result.Success(
                                TelephonyListToken(
                                    allNumbers,
                                    newNextToken
                                )
                            )
                        )
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyGetAllPhoneNumbersException(e)
                        timber.e(error, "Failed to get all phone numbers for user")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun getPhoneNumber(id: String, callback: (Result<PhoneNumber>) -> Unit) {
        val query = PhoneNumberQuery.builder()
            .id(id)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<PhoneNumberQuery.Data>() {
                    override fun onResponse(response: Response<PhoneNumberQuery.Data>) {
                        val number = response.data()?.getPhoneNumber()?.fragments()?.phoneNumber()
                        if (number == null) {
                            return callback.runOnUiThread()(Result.Absent)
                        }

                        val phoneNumber = PhoneNumber(
                            id = number.id(),
                            phoneNumber = number.phoneNumber(),
                            state = number.state(),
                            version = number.version(),
                            created = Date(number.createdAtEpochMs().toLong()),
                            updated = Date(number.updatedAtEpochMs().toLong())
                        )

                        callback.runOnUiThread()(Result.Success(phoneNumber))
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyGetPhoneNumberException(e)
                        timber.e(error, "Failed to get phone number $id")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }
    //endregion

    //region Messaging
    override fun sendSMSMessage(
        localNumber: PhoneNumber,
        remoteNumber: String,
        body: String,
        callback: (Result<PhoneMessage>) -> Unit
    ) {
        val input = SendMessageInput.builder()
            .to(remoteNumber)
            .from(localNumber.phoneNumber)
            .body(body)
            .build()
        val sendMessageMutation = SendMessageMutation.builder()
            .input(input)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.mutate(sendMessageMutation)
                .enqueue(object : GraphQLCall.Callback<SendMessageMutation.Data>() {
                    override fun onResponse(response: Response<SendMessageMutation.Data>) {
                        val messageId = response.data()?.sendMessage()
                        if (messageId == null) {
                            val error = TelephonySendMessageException()
                            timber.e(error, "Failed sending message to number $remoteNumber")
                            callback.runOnUiThread()(Result.Error(error))
                            return
                        }

                        this@DefaultSudoTelephonyClient.getMessage(messageId) { result ->
                            when (result) {
                                is Result.Success -> callback.runOnUiThread()(Result.Success(result.value))
                                is Result.Error -> callback.runOnUiThread()(
                                    Result.Error(
                                        TelephonySendMessageException(),
                                        "Failed to retrieve sent message, it may have succeeded"
                                    )
                                )
                            }
                        }
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonySendMessageException(e)
                        timber.e(error, "Failed sending message to number $remoteNumber")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun sendMMSMessage(
        localNumber: PhoneNumber,
        remoteNumber: String,
        body: String,
        localUrl: URL,
        callback: (Result<PhoneMessage>) -> Unit
    ) {
        timber.i("Sending MMS Message")

        GlobalScope.launch(Dispatchers.IO) {
            uploadFile(localUrl) { result ->
                when (result) {
                    is Result.Error -> {
                        val error = result.throwable
                        timber.e(error)
                        callback.runOnUiThread()(Result.Error(error))
                    }
                    is Result.Success -> {
                        val s3Key = result.value
                        val s3KeyPath = this@DefaultSudoTelephonyClient.s3KeyPath(s3Key)
                        if (s3KeyPath == null) {
                            val error = TelephonyFileUploadException()
                            timber.e(error, "Failed to upload file ${localUrl.file}")
                            callback.runOnUiThread()(Result.Error(error))
                        }

                        val s3Input = S3MediaObjectInput.builder()
                            .bucket(this@DefaultSudoTelephonyClient.transientS3Bucket)
                            .key(s3KeyPath!!)
                            .region(this@DefaultSudoTelephonyClient.s3Region)
                            .build()

                        val sendInput = SendMessageInput.builder()
                            .to(remoteNumber)
                            .from(localNumber.phoneNumber)
                            .body(body)
                            .media(listOf(s3Input))
                            .build()

                        val sendMessageMutation = SendMessageMutation.builder()
                            .input(sendInput)
                            .build()

                        graphQLClient.mutate(sendMessageMutation)
                            .enqueue(object : GraphQLCall.Callback<SendMessageMutation.Data>() {
                                override fun onResponse(response: Response<SendMessageMutation.Data>) {
                                    val messageId = response.data()?.sendMessage()
                                    if (messageId == null) {
                                        val error = TelephonySendMessageException()
                                        timber.e(
                                            error,
                                            "Failed sending message to number $remoteNumber"
                                        )
                                        callback.runOnUiThread()(Result.Error(error))
                                        return
                                    }

                                    this@DefaultSudoTelephonyClient.getMessage(messageId) { result ->
                                        when (result) {
                                            is Result.Success -> callback.runOnUiThread()(
                                                Result.Success(
                                                    result.value
                                                )
                                            )
                                            is Result.Error -> callback.runOnUiThread()(
                                                Result.Error(
                                                    TelephonySendMessageException(),
                                                    "Failed to retrieve sent message, it may have succeeded"
                                                )
                                            )
                                        }
                                    }
                                }

                                override fun onFailure(e: ApolloException) {
                                    val error = TelephonySendMessageException(e)
                                    timber.e(
                                        error,
                                        "Failed sending message to number $remoteNumber"
                                    )
                                    callback.runOnUiThread()(Result.Error(error))
                                }
                            })
                    }
                }
            }
        }
    }

    private fun s3KeyPath(s3Key: String): String? {
        val identityId = this.sudoUserClient.getCredentialsProvider().identityId
        return identityId + "/telephony/media/" + s3Key
    }

    // returns the s3 key for the uploaded file
    private fun uploadFile(localUrl: URL, callback: (Result<String>) -> Unit) {
        timber.i("Uploading ${localUrl.file} to S3")

        val originalFile = File(localUrl.toURI())

        if (!originalFile.exists()) {
            val message = "File ${localUrl.file} not found at provided path"
            val error = TelephonyFileUploadException()
            timber.e(error, message)
            callback.runOnUiThread()(Result.Error(error))
        }
        val data = originalFile.readBytes()

        val s3Key = UUID.randomUUID().toString()
        val s3KeyPath = this.s3KeyPath(s3Key)

        val file = File(s3Key)
        val tmpFile = File.createTempFile(file.name, ".tmp")
        val fos = FileOutputStream(tmpFile)
        fos.write(data)

        val fileMetadata = ObjectMetadata()
        val extension = MimeTypeMap.getFileExtensionFromUrl(localUrl.toString())
        if (extension != null) {
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (type != null) {
                fileMetadata.contentType = type
            }
        }

        val observer = this.transferUtility.upload(this.transientS3Bucket, s3KeyPath, tmpFile, fileMetadata)
        observer.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                if (TransferState.COMPLETED == state) {
                    Timber.i("S3 upload completed successfully.")
                    callback.runOnUiThread()(Result.Success(s3Key))
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                Timber.d("S3 upload progress changed: id=$id, bytesCurrent=$bytesCurrent, bytesTotal=$bytesTotal")
            }

            override fun onError(id: Int, e: Exception?) {
                val error = TelephonyFileUploadException()
                val message = e?.message ?: "Failed to upload file"
                timber.e(e ?: error, message)
                callback.runOnUiThread()(Result.Error(error))
            }
        })
    }

    override fun downloadData(media: MediaObject, callback: (Result<ByteArray>) -> Unit) {
        timber.i("Downloading data ${media.key}")
        val tempPath = this.applicationContext.cacheDir.path + "/" + media.key
        val tempFile = File(tempPath)
        val observer = this.transferUtility.download(media.bucket, media.key, tempFile)
        observer.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                if (TransferState.COMPLETED == state) {
                    Timber.i("Download completed successfully.")

                    val file = File(tempPath)
                    if (!file.exists()) {
                        val message = "File not downloaded"
                        val error = TelephonyFileDownloadException()
                        timber.e(error, message)
                        callback.runOnUiThread()(Result.Error(error))
                    }

                    val data = file.readBytes()
                    val decryptedData = this@DefaultSudoTelephonyClient.decryptSealedData(data)
                    callback.runOnUiThread()(Result.Success(decryptedData))
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                Timber.d("Download progress changed: id=$id, bytesCurrent=$bytesCurrent, bytesTotal=$bytesTotal")
            }

            override fun onError(id: Int, e: Exception?) {
                val error = TelephonyFileDownloadException()
                val message = e?.message ?: "Failed to download file"
                timber.e(e ?: error, message)
                callback.runOnUiThread()(Result.Error(error))
            }
        })
    }

    override fun getMessage(id: String, callback: (Result<PhoneMessage>) -> Unit) {
        val keyId = getKeyId()
        if (keyId == null) {
            val error = TelephonyGetMessageException()
            timber.e( error, "Failed to retrieve message for id $id" )
            callback.runOnUiThread()(Result.Error(error))
            return
        }

        val getMessageQuery = GetMessageQuery.builder()
            .id(id)
            .keyId(keyId)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.query(getMessageQuery)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<GetMessageQuery.Data>() {
                    override fun onResponse(response: Response<GetMessageQuery.Data>) {
                        val message = response.data()?.getMessage?.fragments()?.sealedMessage
                        if (message == null) {
                            val error = TelephonyGetMessageException()
                            timber.e(error, "Failed to retrieve message for id $id")
                            callback.runOnUiThread()(Result.Error(error))
                            return
                        }

                        var media: List<MediaObject> = emptyList()
                        if (message.media() != null) {
                            val mediaList = message.media()!!
                            media =
                                mediaList.map { MediaObject.fromS3Media(it.fragments().s3MediaObject()) }
                        }
                        val decoder = Base64.getDecoder()
                        val sealedBody = message.body() ?: ""
                        val sealedBodyData = decoder.decode(sealedBody)
                        val sealedRemoteData = decoder.decode(message.remotePhoneNumber())
                        val sealedLocalData = decoder.decode(message.localPhoneNumber())

                        // decrypt the message
                        val decryptedBody = decryptSealedData(sealedBodyData)
                        val decryptedRemote = decryptSealedData(sealedRemoteData)
                        val decryptedLocal = decryptSealedData(sealedLocalData)
                        val bodyString = String(decryptedBody, Charset.defaultCharset())
                        val remoteString = String(decryptedRemote, Charset.defaultCharset())
                        val localString = String(decryptedLocal, Charset.defaultCharset())
                        val newMessage = PhoneMessage(
                            message.id(),
                            message.owner(),
                            message.conversation(),
                            Instant.ofEpochMilli(message.updatedAtEpochMs().toLong()),
                            Instant.ofEpochMilli(message.createdAtEpochMs().toLong()),
                            localString,
                            remoteString,
                            bodyString,
                            message.direction(),
                            message.state(),
                            media
                        )

                        callback.runOnUiThread()(Result.Success(newMessage))
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyGetMessageException(e)
                        timber.e(error, "Failed to retrieve message for id $id")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun getMessages(localNumber: PhoneNumber, remoteNumber: String, limit: Int?, nextToken: String?, callback: (Result<TelephonyListToken<PhoneMessage>>) -> Unit ) {
        val ownerId = sudoUserClient.getSubject()
        if (ownerId == null) {
            val error = TelephonyGetMessageException()
            timber.e( error, "Failed to retrieve messages." )
            callback.runOnUiThread()(Result.Error(error))
            return
        }
        val conversationId = "tl-cnv-" + UUIDV5.UUIDFromNamespaceAndName(ownerId, localNumber.phoneNumber + remoteNumber).toString().toLowerCase()
        this.getMessages(conversationId, limit, nextToken, callback)
    }

    override fun getMessages(conversationId: String, limit: Int?, nextToken: String?, callback: (Result<TelephonyListToken<PhoneMessage>>) -> Unit) {
        val keyId = getKeyId()
        if (keyId == null) {
            val error = TelephonyGetMessageException()
            timber.e( error, "Failed to retrieve messages for conversation $conversationId" )
            callback.runOnUiThread()(Result.Error(error))
            return
        }

        val messagesInput = MessageFilterInput.builder()
            .conversation(IDFilterInput.builder().eq(conversationId).build())
            .keyId(IDFilterInput.builder().eq(keyId).build())
            .build()

        val messagesQuery = ListMessagesQuery.builder()
            .filter(messagesInput)
            .limit(limit)
            .nextToken(nextToken)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.query(messagesQuery)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<ListMessagesQuery.Data>() {
                    override fun onResponse(response: Response<ListMessagesQuery.Data>) {
                        val messages = response.data()?.listMessages()?.items()
                        val newNextToken = response.data()?.listMessages()?.nextToken()

                        if (messages == null) {
                            val error = TelephonyGetMessagesException()
                            timber.e(
                                error,
                                "Failed to retrieve messages for conversation $conversationId"
                            )
                            callback.runOnUiThread()(Result.Error(error))
                            return
                        }

                        if (messages.size < 1) {
                            callback.runOnUiThread()(Result.Absent)
                            return
                        }

                        val decoder = Base64.getDecoder()
                        val allMessages = messages.map {
                            val msg = it.fragments().sealedMessage()
                            val media = msg.media()
                                ?.map { MediaObject.fromS3Media(it.fragments().s3MediaObject()) }

                            val sealedBody = msg.body() ?: ""
                            val sealedBodyData = decoder.decode(sealedBody)
                            val sealedRemoteData = decoder.decode(msg.remotePhoneNumber())
                            val sealedLocalData = decoder.decode(msg.localPhoneNumber())

                            // decrypt the message
                            val decryptedBody = decryptSealedData(sealedBodyData)
                            val decryptedRemote = decryptSealedData(sealedRemoteData)
                            val decryptedLocal = decryptSealedData(sealedLocalData)
                            val bodyString = String(decryptedBody, Charset.defaultCharset())
                            val remoteString = String(decryptedRemote, Charset.defaultCharset())
                            val localString = String(decryptedLocal, Charset.defaultCharset())

                            PhoneMessage(
                                id = msg.id(),
                                owner = msg.owner(),
                                conversation = msg.conversation(),
                                created = Instant.ofEpochMilli(msg.createdAtEpochMs().toLong()),
                                updated = Instant.ofEpochMilli(msg.updatedAtEpochMs().toLong()),
                                local = localString,
                                remote = remoteString,
                                body = bodyString,
                                direction = msg.direction(),
                                state = msg.state(),
                                media = media ?: emptyList()
                            )
                        }.sortedByDescending { it.created }

                        callback.runOnUiThread()(
                            Result.Success(
                                TelephonyListToken(
                                    allMessages,
                                    newNextToken
                                )
                            )
                        )
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyGetMessagesException(e)
                        timber.e(
                            error,
                            "Failed to retrieve messages for conversation $conversationId"
                        )
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun deleteMessage(id: String, callback: (Result<String>) -> Unit) {
        val mutation = DeleteMessageMutation.builder()
            .id(id)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.mutate(mutation)
                .enqueue(object : GraphQLCall.Callback<DeleteMessageMutation.Data>() {
                    override fun onResponse(response: Response<DeleteMessageMutation.Data>) {
                        val messageId = response.data()?.deleteMessage
                        if (messageId == null) {
                            val error = TelephonyDeleteMessageException()
                            timber.e(error, "Failed to delete message $id")
                            callback.runOnUiThread()(Result.Error(error))
                            return
                        }

                        callback.runOnUiThread()(Result.Success(messageId))
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyDeleteMessageException(e)
                        timber.e(error, "Failed to delete message $id")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun subscribeToPhoneMessages(subscriber: PhoneMessageSubscriber, id: String?) {
        Timber.d("Subscribing to new PhoneMessage notifications.")

        val subscriberId = id ?: DEFAULT_MESSAGE_SUBSCRIBER_ID
        val owner = this.sudoUserClient.getSubject()
        require(owner != null) { "Owner was null. The client may not be signed in." }

        this.onMessageSubscriptionManager.replaceSubscriber(subscriberId, subscriber)
        if (this.onMessageSubscriptionManager.watcher == null) {
            GlobalScope.launch(Dispatchers.IO) {
                val subscription = OnMessageReceivedSubscription.builder().owner(owner).build()
                val watcher = this@DefaultSudoTelephonyClient.graphQLClient.subscribe(subscription)
                this@DefaultSudoTelephonyClient.onMessageSubscriptionManager.watcher = watcher
                watcher.execute(object :
                    AppSyncSubscriptionCall.Callback<OnMessageReceivedSubscription.Data> {
                    override fun onCompleted() {
                        // Subscription was terminated. Notify the subscribers.
                        this@DefaultSudoTelephonyClient.onMessageSubscriptionManager.connectionStatusChanged(
                            TelephonySubscriber.ConnectionState.DISCONNECTED
                        )
                    }

                    override fun onFailure(e: ApolloException) {
                        // Failed create a subscription. Notify the subscribers.
                        this@DefaultSudoTelephonyClient.onMessageSubscriptionManager.connectionStatusChanged(
                            TelephonySubscriber.ConnectionState.DISCONNECTED
                        )
                    }

                    override fun onResponse(response: Response<OnMessageReceivedSubscription.Data>) {
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                val error = response.errors().firstOrNull()
                                if (error != null) {
                                    Timber.e("Subscription response contained error: $error")
                                } else {
                                    val item = response.data()?.OnMessage()
                                    if (item != null) {
                                        val message = item.fragments().sealedMessage()
                                        var media: List<MediaObject> = emptyList()
                                        if (message.media() != null) {
                                            val mediaList = message.media()!!
                                            media = mediaList.map { MediaObject.fromS3Media(it.fragments().s3MediaObject()) }
                                        }
                                        val decoder = Base64.getDecoder()
                                        val sealedBody = message.body() ?: ""
                                        val sealedBodyData = decoder.decode(sealedBody)
                                        val sealedRemoteData = decoder.decode(message.remotePhoneNumber())
                                        val sealedLocalData = decoder.decode(message.localPhoneNumber())

                                        // decrypt the message
                                        val decryptedBody = decryptSealedData(sealedBodyData)
                                        val decryptedRemote = decryptSealedData(sealedRemoteData)
                                        val decryptedLocal = decryptSealedData(sealedLocalData)
                                        val bodyString = String(decryptedBody, Charset.defaultCharset())
                                        val remoteString = String(decryptedRemote, Charset.defaultCharset())
                                        val localString = String(decryptedLocal, Charset.defaultCharset())
                                        val newMessage = PhoneMessage(
                                            message.id(),
                                            message.owner(),
                                            message.conversation(),
                                            Instant.ofEpochMilli(message.updatedAtEpochMs().toLong()),
                                            Instant.ofEpochMilli(message.createdAtEpochMs().toLong()),
                                            localString,
                                            remoteString,
                                            bodyString,
                                            message.direction(),
                                            message.state(),
                                            media
                                        )

                                        // Notify subscribers
                                        this@DefaultSudoTelephonyClient.onMessageSubscriptionManager.phoneMessageReceived(newMessage)
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e("Failed to process the subscription response: $e")
                            }
                        }
                    }
                })

                this@DefaultSudoTelephonyClient.onMessageSubscriptionManager.connectionStatusChanged(
                    TelephonySubscriber.ConnectionState.CONNECTED
                )
            }
        }
    }

    override fun unsubscribeFromPhoneMessages(id: String?) {
        Timber.d("Unsubscribing from new message notifications.")
        if (id == null) {
            onMessageSubscriptionManager.removeAllSubscribers()
        } else {
            onMessageSubscriptionManager.removeSubscriber(id)
        }
    }

    override fun getConversation(conversationId: String, callback: (Result<PhoneMessageConversation>) -> Unit) {
        val query = GetConversationQuery.builder()
            .id(conversationId)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<GetConversationQuery.Data>() {
                    override fun onResponse(response: Response<GetConversationQuery.Data>) {
                        val conversation =
                            response.data()?.getConversation()?.fragments()?.conversation()
                        if (conversation == null) {
                            val error = TelephonyGetConversationException()
                            timber.e(
                                error,
                                "Failed to retrieve conversation for id $conversationId"
                            )
                            callback.runOnUiThread()(Result.Error(error))
                            return
                        }

                        // get the last message on the conversation
                        try {
                            getMessage(conversation.lastMessage()) { result ->
                                when (result) {
                                    is Result.Success -> {
                                        val messageConversation = PhoneMessageConversation(
                                            conversation.id(),
                                            conversation.owner(),
                                            MessageConversationType.fromInternalType(conversation.type()),
                                            conversation.lastMessage(),
                                            result.value,
                                            Date(conversation.createdAtEpochMs().toLong()),
                                            Date(conversation.updatedAtEpochMs().toLong())
                                        )
                                        callback.runOnUiThread()(Result.Success(messageConversation))
                                    }
                                    is Result.Error -> {
                                        val error = TelephonyGetConversationException()
                                        timber.e(
                                            error,
                                            "Failed to retrieve conversation for id $conversationId: ${result.throwable}"
                                        )
                                        callback.runOnUiThread()(Result.Error(error))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            val error = TelephonyGetConversationException()
                            timber.e(
                                error,
                                "Failed to retrieve conversation for id $conversationId: $e"
                            )
                            callback.runOnUiThread()(Result.Error(error))
                        }

                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyGetConversationException(e)
                        timber.e(error, "Failed to retrieve conversation for id $conversationId")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }

    override fun getConversation(localNumber: PhoneNumber, remoteNumber: String, callback: (Result<PhoneMessageConversation>) -> Unit) {
        val ownerId = sudoUserClient.getSubject()
        if (ownerId == null) {
            val error = TelephonyGetMessageException()
            timber.e( error, "Failed to retrieve message for number ${localNumber.phoneNumber}" )
            return callback.runOnUiThread()(Result.Error(error))
        }
        val conversationId = "tl-cnv-" + UUIDV5.UUIDFromNamespaceAndName(ownerId, localNumber.phoneNumber + remoteNumber).toString().toLowerCase()
        this.getConversation(conversationId, callback)
    }

    override fun getConversations(localNumber: PhoneNumber, limit: Int?, nextToken: String?, callback: (Result<TelephonyListToken<PhoneMessageConversation>>) -> Unit ) {
        timber.i("Fetching conversations")

        val conversationsInput = ConversationFilterInput.builder()
            .phoneNumberId(IDFilterInput.builder().eq(localNumber.id).build())
            .build()
        val query = ListConversationsQuery.builder()
            .filter(conversationsInput)
            .limit(limit)
            .nextToken(nextToken)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            graphQLClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<ListConversationsQuery.Data>() {
                    override fun onResponse(response: Response<ListConversationsQuery.Data>) {
                        val nextPage = response.data()?.listConversations()?.nextToken()
                        val items = response.data()?.listConversations()?.items()
                        if (items == null) {
                            val error = TelephonyGetConversationsException()
                            timber.e(
                                error,
                                "Failed to retrieve conversations for phoneNumber ${localNumber.phoneNumber}"
                            )
                            return callback.runOnUiThread()(Result.Error(error))
                        }
                        // go through each conversation and convert it to a PhoneMessageConversation
                        val conversations = mutableListOf<PhoneMessageConversation>()
                        val countDownLatch = CountDownLatch(items.size)
                        for (item in items) {
                            getConversation(item.fragments().conversation().id()) { conversationResult ->
                                when (conversationResult) {
                                    is Result.Success -> {
                                        conversations.add(conversationResult.value)
                                    }
                                    is Result.Error -> {
                                        timber.e(
                                            conversationResult.throwable,
                                            "Error getting conversation id: ${item.fragments().conversation().id()}"
                                        )
                                    }
                                }
                                countDownLatch.countDown()
                            }
                        }
                        countDownLatch.await(60, TimeUnit.SECONDS)
                        callback.runOnUiThread()(
                            Result.Success(
                                TelephonyListToken(
                                    conversations,
                                    nextPage
                                )
                            )
                        )
                    }

                    override fun onFailure(e: ApolloException) {
                        val error = TelephonyGetConversationsException()
                        timber.e(
                            error,
                            "Failed to retrieve conversations for phoneNumber ${localNumber.phoneNumber}: $e"
                        )
                        callback.runOnUiThread()(Result.Error(error))
                    }
                })
        }
    }
}
