package com.sudoplatform.sudotelephony.telephony

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.annotation.Keep
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
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudoprofiles.Sudo
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudotelephony.appsync.enqueue
import com.sudoplatform.sudotelephony.calling.DefaultSudoTelephonyCalling
import com.sudoplatform.sudotelephony.calling.SudoTelephonyCalling
import com.sudoplatform.sudotelephony.exceptions.SudoTelephonyException
import com.sudoplatform.sudotelephony.graphql.AvailablePhoneNumberResultQuery
import com.sudoplatform.sudotelephony.graphql.AvailablePhoneNumbersForCountryCodeMutation
import com.sudoplatform.sudotelephony.graphql.AvailablePhoneNumbersForGPSMutation
import com.sudoplatform.sudotelephony.graphql.AvailablePhoneNumbersForPrefixMutation
import com.sudoplatform.sudotelephony.graphql.CreatePublicKeyMutation
import com.sudoplatform.sudotelephony.graphql.DeleteMessageMutation
import com.sudoplatform.sudotelephony.graphql.DeprovisionPhoneNumberMutation
import com.sudoplatform.sudotelephony.graphql.GetConversationQuery
import com.sudoplatform.sudotelephony.graphql.GetMessageQuery
import com.sudoplatform.sudotelephony.graphql.ListConversationsQuery
import com.sudoplatform.sudotelephony.graphql.ListMessagesQuery
import com.sudoplatform.sudotelephony.graphql.OnMessageReceivedSubscription
import com.sudoplatform.sudotelephony.graphql.PhoneNumberQuery
import com.sudoplatform.sudotelephony.graphql.PhoneNumbersQuery
import com.sudoplatform.sudotelephony.graphql.ProvisionPhoneNumberMutation
import com.sudoplatform.sudotelephony.graphql.SendMessageMutation
import com.sudoplatform.sudotelephony.graphql.SupportedCountriesQuery
import com.sudoplatform.sudotelephony.graphql.fragment.PublicKey
import com.sudoplatform.sudotelephony.graphql.type.ConversationFilterInput
import com.sudoplatform.sudotelephony.graphql.type.CreatePublicKeyInput
import com.sudoplatform.sudotelephony.graphql.type.DeprovisionPhoneNumberInput
import com.sudoplatform.sudotelephony.graphql.type.IDFilterInput
import com.sudoplatform.sudotelephony.graphql.type.MessageFilterInput
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberFilterInput
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberSearchState
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberState
import com.sudoplatform.sudotelephony.graphql.type.ProvisionPhoneNumberInput
import com.sudoplatform.sudotelephony.graphql.type.S3MediaObjectInput
import com.sudoplatform.sudotelephony.graphql.type.SendMessageInput
import com.sudoplatform.sudotelephony.media.MediaObject
import com.sudoplatform.sudotelephony.messages.MessageConversationType
import com.sudoplatform.sudotelephony.messages.PhoneMessage
import com.sudoplatform.sudotelephony.messages.PhoneMessageConversation
import com.sudoplatform.sudotelephony.messages.PhoneMessageSubscriber
import com.sudoplatform.sudotelephony.phonenumbers.PhoneNumber
import com.sudoplatform.sudotelephony.phonenumbers.PhoneNumberSearchResult
import com.sudoplatform.sudotelephony.phonenumbers.SupportedCountriesResult
import com.sudoplatform.sudotelephony.subscription.SubscriptionManager
import com.sudoplatform.sudotelephony.utils.UUIDV5
import com.sudoplatform.sudouser.SudoUserClient
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.charset.Charset
import java.security.KeyPair
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Main implementation of [SudoTelephonyClient] interface
 */
@Keep
internal class DefaultSudoTelephonyClient : SudoTelephonyClient {

    companion object {
        private const val CONFIG_NAMESPACE_IDENTITY_SERVICE = "identityService"
        private const val CONFIG_NAMESPACE_API_SERVICE = "apiService"

        private const val TELEPHONY_SERVICE_REGION = "region"
        private const val TELEPHONY_SERVICE_BUCKET = "bucket"
        private const val TELEPHONY_SERVICE_TRANSIENT_BUCKET = "transientBucket"

        private const val KEY_MANAGER_KEY_ID_NAME = "com.sudoplatform.keyId"
        private const val KEY_MANAGER_KEYRING_ID_NAME = "com.sudoplatform.keyRingId"

        private const val SUDO_PROFILES_TELEPHONY_AUDIENCE =
            "sudoplatform.sudotelephony.phone-number"

        private const val RSA_ENCRYPTION_OAEP_AES_CBC = "RSAEncryptionOAEPAESCBC"

        private const val DEFAULT_MESSAGE_SUBSCRIBER_ID = "DEFAULT_MESSAGE_SUBSCRIBER"
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
    private val logger: Logger
    override val calling: SudoTelephonyCalling

    constructor(
        context: Context,
        sudoUserClient: SudoUserClient,
        sudoProfilesClient: SudoProfilesClient,
        logger: Logger
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
        this.logger = logger
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

        val telephonyKeyManager = DefaultTelephonyKeyManager(
            this.sudoUserClient,
            this.graphQLClient,
            context,
            logger
        )

        this.onMessageSubscriptionManager = SubscriptionManager()
        this.calling = DefaultSudoTelephonyCalling(
            applicationContext,
            graphQLClient,
            telephonyKeyManager,
            logger
        )
    }

    override fun isRegistered() = sudoUserClient.isRegistered()

    override suspend fun reset() {
        try {
            this.transferUtility.cancelAllWithType(TransferType.ANY)
            this.graphQLClient.clearCaches()
            sudoProfilesClient.reset()
            sudoUserClient.reset()
            this.keyManager.removeAllKeys()
        } catch (e: Throwable) {
            logger.debug("error $e")
            throw SudoTelephonyException.ResetTelephonyClientException("reset failed", e)
        }
    }

    private fun getIdentityId(): String? {
        return this@DefaultSudoTelephonyClient.sudoUserClient.getUserClaim("custom:identityId") as? String
    }

    //region Keys
    private suspend fun generateKeyPair(): PublicKey {
        val identityId = this.getIdentityId()

        if (identityId == null) {
            val error = SudoTelephonyException.CreatePublicKeyException()
            logger.debug("Unable to retrieve identity id: $error")
            throw SudoTelephonyException.CreatePublicKeyException("Unable to retrieve identity id")
        }

        val keyRingId = UUID.randomUUID().toString()
        val keyId = UUID.randomUUID().toString()

        var publicKey: String? = null
        try {
            this@DefaultSudoTelephonyClient.keyManager.generateKeyPair(identityId)
            val pubKey = this@DefaultSudoTelephonyClient
                .keyManager
                .getPublicKeyData(identityId) ?: throw (KeyManagerException("Failed to generate key pair"))
            publicKey = Base64.getEncoder().encodeToString(pubKey)
        } catch (error: KeyManagerException) {
            logger.debug("Failed to generate key pair: $error")
        }

        if (publicKey == null) {
            val error = SudoTelephonyException.CreatePublicKeyException()
            logger.debug("Failed to create public key: $error")
            throw SudoTelephonyException.CreatePublicKeyException("Failed to create public key")
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
            .publicKey(publicKey)
            .keyId(keyId)
            .keyRingId(keyRingId)
            .algorithm(RSA_ENCRYPTION_OAEP_AES_CBC)
            .build()

        val mutation = CreatePublicKeyMutation.builder()
            .input(publicKeyInput)
            .build()
        val createResponse = graphQLClient.mutate(mutation)
            .enqueue()

        if (createResponse.hasErrors()) {
            logger.debug("errors = ${createResponse.errors()}")
        }

        val createResult = createResponse.data()
            ?.createPublicKeyForTelephony()
            ?.fragments()
            ?.publicKey()
            ?: throw SudoTelephonyException.CreatePublicKeyException("create key failed")

        logger.debug("Create Public Key succeeded")

        return createResult
    }

    private fun getKeyPair(): KeyPair? {
        val identityId = this.getIdentityId() ?: return null
        val privateKey = this.keyManager.getPrivateKey(identityId) ?: return null
        val publicKey = this.keyManager.getPublicKey(identityId) ?: return null

        return KeyPair(publicKey, privateKey)
    }

    private fun getKeyId(): String? {
        val identityId = this.getIdentityId() ?: return null
        return this.keyManager.getPassword(KEY_MANAGER_KEY_ID_NAME + identityId)
            .toString(Charset.forName("UTF-8"))
    }

    private fun getKeyRingId(): String? {
        val identityId = this.getIdentityId() ?: return null
        return this.keyManager.getPassword(KEY_MANAGER_KEYRING_ID_NAME + identityId)
            .toString(Charset.forName("UTF-8"))
    }

    private fun decryptSealedData(data: ByteArray): ByteArray {
        if (data.isEmpty()) {
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
            logger.debug("Failed to decrypt sealed data: $error")
            throw(SudoTelephonyException.DecryptSealedDataException("failed to decrypt sealed data", error))
        }
    }
    //endregion

    // region Telephony search
    override suspend fun getSupportedCountries(): SupportedCountriesResult {
        val query = SupportedCountriesQuery.builder()
            .build()

        val getCountriesResponse = graphQLClient.query(query)
            .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
            .enqueue()

        if (getCountriesResponse.hasErrors()) {
            val error = getCountriesResponse.errors().first()
            logger.debug("Failed to get supported countries: $error")
            throw SudoTelephonyException.GetPhoneNumberException(error.message())
        }

        val result = getCountriesResponse.data()?.phoneNumberCountries
            ?: throw SudoTelephonyException.GetPhoneNumberException("failed to get supported countries")

        return SupportedCountriesResult(result.countries())
    }

    private suspend fun pollForPhoneSearchResult(
        searchId: String,
    ): PhoneNumberSearchResult {
        while (true) {
            val query = AvailablePhoneNumberResultQuery(searchId)
            val response = graphQLClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue()
            if (response.hasErrors()) {
                throw SudoTelephonyException.SearchException(response.errors().first().message())
            }
            val state = response.data()
                ?.phoneNumberSearch
                ?.fragments()
                ?.availablePhoneNumberResult()
                ?.state()
                ?: throw SudoTelephonyException.SearchException()
            if (state == PhoneNumberSearchState.FAILED) {
                throw SudoTelephonyException.SearchException("Phone number search failed")
            }
            if (state == PhoneNumberSearchState.SEARCHING) {
                continue
            }
            if (state == PhoneNumberSearchState.COMPLETE) {
                val result = response.data()
                    ?.phoneNumberSearch
                    ?.fragments()
                    ?.availablePhoneNumberResult()
                return PhoneNumberSearchResult(result!!)
            }
        }
    }

    private suspend fun <Data : Operation.Data?, Variables : Operation.Variables?> runPhoneSearchMutation(
        mutation: Mutation<Data, Data, Variables>
    ): PhoneNumberSearchResult {
        val phoneSearchResponse = graphQLClient
            .mutate(mutation)
            .enqueue()
        if (phoneSearchResponse.hasErrors()) {
            val errors = phoneSearchResponse.errors()
            val exception = when (errors.first().customAttributes()["errorType"]) {
                "Telephony:CountryNotSupported" -> SudoTelephonyException.UnsupportedCountryCodeException()
                "Telephony:InvalidCountryCode" -> SudoTelephonyException.InvalidCountryCodeException()
                else -> SudoTelephonyException.SearchException()
            }
            throw exception
        }

        val responseData = phoneSearchResponse.data()
        val searchId: String?
        if (responseData is AvailablePhoneNumbersForCountryCodeMutation.Data) {
            searchId = responseData.searchPhoneNumbers()
                ?.fragments()
                ?.availablePhoneNumberResult()
                ?.id()
        } else if (responseData is AvailablePhoneNumbersForGPSMutation.Data) {
            searchId = responseData.searchPhoneNumbers()
                ?.fragments()
                ?.availablePhoneNumberResult()
                ?.id()
        } else if (responseData is AvailablePhoneNumbersForPrefixMutation.Data) {
            searchId = responseData.searchPhoneNumbers()
                ?.fragments()
                ?.availablePhoneNumberResult()
                ?.id()
        } else {
            throw SudoTelephonyException.SearchException("Failed to search for available phone numbers")
        }
        return pollForPhoneSearchResult(searchId!!)
    }

    override suspend fun searchAvailablePhoneNumbers(
        countryCode: String,
        limit: Int?
    ): PhoneNumberSearchResult {
        val searchMutation = AvailablePhoneNumbersForCountryCodeMutation.builder()
            .limit(limit)
            .country(countryCode)
            .build()

        return runPhoneSearchMutation(searchMutation)
    }

    override suspend fun searchAvailablePhoneNumbers(
        countryCode: String,
        prefix: String,
        limit: Int?
    ): PhoneNumberSearchResult {
        return when (prefix) {
            "" -> this.searchAvailablePhoneNumbers(countryCode)
            else -> {
                val searchMutation = AvailablePhoneNumbersForPrefixMutation.builder()
                    .country(countryCode)
                    .prefix(prefix)
                    .limit(limit)
                    .build()

                return runPhoneSearchMutation(searchMutation)
            }
        }
    }

    override suspend fun searchAvailablePhoneNumbers(
        countryCode: String,
        latitude: String,
        longitude: String,
        limit: Int?
    ): PhoneNumberSearchResult {
        val searchMutation = AvailablePhoneNumbersForGPSMutation.builder()
            .country(countryCode)
            .latitude(latitude)
            .longitude(longitude)
            .limit(limit)
            .build()

        return runPhoneSearchMutation(searchMutation)
    }

    //endregion

    //region Phone phoneNumber CRUD-ops
    override suspend fun provisionPhoneNumber(
        countryCode: String,
        phoneNumber: String,
        sudoId: String
    ): PhoneNumber {
        logger.info("Provisioning phone phoneNumber $phoneNumber")

        if (getKeyPair() == null) {
            try {
                generateKeyPair()
            } catch (e: Throwable) {
                throw SudoTelephonyException.NumberProvisionException(
                    "Failed to generate key pair",
                    e
                )
            }
        }
        val keyRingId = getKeyRingId()
        if (keyRingId == null) {
            val error = SudoTelephonyException.NumberProvisionException("missing Key Ring Id")
            logger.debug("Failed provisioning phoneNumber $phoneNumber: $error")
            throw error
        } else {
            val sudo = Sudo(sudoId)
            try {
                val ownershipProof = sudoProfilesClient.getOwnershipProof(
                    sudo,
                    SUDO_PROFILES_TELEPHONY_AUDIENCE
                )
                val provisionInput = ProvisionPhoneNumberInput.builder()
                    .country(countryCode)
                    .phoneNumber(phoneNumber)
                    .ownerProofs(listOf(ownershipProof))
                    .keyRingId(keyRingId)
                    .build()
                return provisionPhoneNumber(provisionInput)
            } catch (e: Throwable) {
                logger.debug("Failed provisioning phoneNumber $phoneNumber: $e")
                throw SudoTelephonyException.NumberProvisionException(cause = e)
            }
        }
    }

    private suspend fun provisionPhoneNumber(
        provisionInput: ProvisionPhoneNumberInput
    ): PhoneNumber {
        val provisionMutation = ProvisionPhoneNumberMutation.builder()
            .input(provisionInput)
            .build()

        val provisionResponse = graphQLClient.mutate(provisionMutation)
            .enqueue()
        if (provisionResponse.hasErrors()) {
            val responseErrors = provisionResponse.errors()
            logger.debug("errors = $responseErrors")
            if (responseErrors.first().message() == "sudoplatform.telephony.NoPhoneNumberEntitlementError") {
                throw SudoTelephonyException.InsufficientEntitlementException()
            } else {
                throw SudoTelephonyException.NumberProvisionException(
                    responseErrors.first().message()
                )
            }
        }
        val data = provisionResponse.data()?.provisionPhoneNumber()
            ?: throw SudoTelephonyException.NumberProvisionException("No provision phone number data available")

        val searchId = data.fragments().phoneNumber().id()
        val query = PhoneNumberQuery(searchId)

        while (true) {
            val phoneNumberResponse = graphQLClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue()
            if (phoneNumberResponse.hasErrors()) {
                val phoneNumberResponseErrors = phoneNumberResponse.errors()
                logger.debug("errors = $phoneNumberResponseErrors")
                throw SudoTelephonyException.NumberProvisionException(phoneNumberResponse.errors().first().message())
            }
            val number = phoneNumberResponse.data()?.phoneNumber
                ?: throw SudoTelephonyException.NumberProvisionException("Unable to retrieve phone number")
            val state = number.fragments().phoneNumber().state()
            if (state == PhoneNumberState.COMPLETE) {
                return PhoneNumber(
                    id = number.fragments().phoneNumber().id(),
                    phoneNumber = number.fragments().phoneNumber()
                        .phoneNumber(),
                    state = number.fragments().phoneNumber().state(),
                    version = number.fragments().phoneNumber().version(),
                    created = Date(
                        number.fragments().phoneNumber().createdAtEpochMs()
                            .toLong()
                    ),
                    updated = Date(
                        number.fragments().phoneNumber().updatedAtEpochMs()
                            .toLong()
                    )
                )
            } else if (state == PhoneNumberState.PROVISIONING) {
                continue
            } else if (state == PhoneNumberState.FAILED) {
                throw SudoTelephonyException.NumberProvisionException("Failed to provision phone number")
            }
        }
    }

    override suspend fun deletePhoneNumber(phoneNumber: String): PhoneNumber {
        val deprovisionPhoneNumberMutation = DeprovisionPhoneNumberMutation.builder()
            .input(
                DeprovisionPhoneNumberInput
                    .builder()
                    .phoneNumber(phoneNumber)
                    .build()
            )
            .build()

        val deprovisionResponse = graphQLClient.mutate(deprovisionPhoneNumberMutation)
            .enqueue()

        if (deprovisionResponse.hasErrors()) {
            val error = SudoTelephonyException.NumberDeletionException(
                deprovisionResponse.errors().first().message()
            )
            logger.debug("Failed to delete phoneNumber $phoneNumber: $error")

            throw error
        }

        val number = deprovisionResponse
            .data()
            ?.deprovisionPhoneNumber()
            ?.fragments()
            ?.phoneNumber()
        if (number == null) {
            throw SudoTelephonyException.NumberDeletionException()
        }

        return PhoneNumber(
            id = number.id(),
            phoneNumber = number.phoneNumber(),
            state = number.state(),
            version = number.version(),
            created = Date(number.createdAtEpochMs().toLong()),
            updated = Date(number.updatedAtEpochMs().toLong())
        )
    }

    override suspend fun listPhoneNumbers(
        sudoId: String?,
        limit: Int?,
        nextToken: String?,
    ): TelephonyListToken<PhoneNumber> {
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

        val phoneNumbersResponse = graphQLClient.query(query)
            .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
            .enqueue()

        if (phoneNumbersResponse.hasErrors()) {
            val error = phoneNumbersResponse.errors().first()
            logger.debug("Failed to get all phone numbers for user: $error")
            throw SudoTelephonyException.GetAllPhoneNumbersException(error.message())
        }

        val numbers = phoneNumbersResponse.data()?.listPhoneNumbers()?.items()
        val newNextToken = phoneNumbersResponse.data()?.listPhoneNumbers()?.nextToken()
        if (numbers == null) {
            val error = SudoTelephonyException.GetAllPhoneNumbersException()
            logger.debug("Failed to list phone numbers: $error")
            throw error
        }

        val allNumbers = numbers.map { number ->
            PhoneNumber(
                id = number.fragments().phoneNumber().id(),
                phoneNumber = number.fragments().phoneNumber().phoneNumber(),
                state = number.fragments().phoneNumber().state(),
                version = number.fragments().phoneNumber().version(),
                created = Date(
                    number.fragments().phoneNumber().createdAtEpochMs().toLong()
                ),
                updated = Date(
                    number.fragments().phoneNumber().updatedAtEpochMs().toLong()
                )
            )
        }

        return TelephonyListToken(allNumbers, newNextToken)
    }

    override suspend fun getPhoneNumber(id: String): PhoneNumber {
        val query = PhoneNumberQuery.builder()
            .id(id)
            .build()

        val getNumberResponse = graphQLClient.query(query)
            .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
            .enqueue()

        if (getNumberResponse.hasErrors()) {
            val error = SudoTelephonyException.GetPhoneNumberException(
                getNumberResponse.errors().first().message()
            )
            logger.debug("Failed to get phone number $id: $error")
            throw error
        }

        val number = getNumberResponse.data()?.getPhoneNumber()?.fragments()?.phoneNumber()
            ?: throw SudoTelephonyException.GetPhoneNumberException()

        return PhoneNumber(
            id = number.id(),
            phoneNumber = number.phoneNumber(),
            state = number.state(),
            version = number.version(),
            created = Date(number.createdAtEpochMs().toLong()),
            updated = Date(number.updatedAtEpochMs().toLong())
        )
    }
    //endregion

    //region Messaging
    override suspend fun sendSMSMessage(
        localNumber: PhoneNumber,
        remoteNumber: String,
        body: String,
    ): PhoneMessage {
        val input = SendMessageInput.builder()
            .to(remoteNumber)
            .from(localNumber.phoneNumber)
            .body(body)
            .build()
        val sendMessageMutation = SendMessageMutation.builder()
            .input(input)
            .build()

        val sendResponse = graphQLClient.mutate(sendMessageMutation).enqueue()

        if (sendResponse.hasErrors()) {
            val error = SudoTelephonyException.SendMessageException(
                sendResponse.errors().first().message()
            )
            logger.debug("Failed sending message to number $remoteNumber: $error")
            throw error
        }

        val messageId = sendResponse.data()?.sendMessage()
        if (messageId == null) {
            val error = SudoTelephonyException.SendMessageException()
            logger.debug("Failed sending message to number $remoteNumber: $error")
            throw error
        }

        try {
            return getMessage(messageId)
        } catch (e: Throwable) {
            throw SudoTelephonyException.SendMessageException(
                "Failed to retrieve sent message, it may have succeeded"
            )
        }
    }

    override suspend fun sendMMSMessage(localNumber: PhoneNumber, remoteNumber: String, body: String, localUrl: URL): PhoneMessage {
        logger.info("Sending MMS Message")
        val s3Key = uploadFile(localUrl)
        val s3KeyPath = s3KeyPath(s3Key)

        val s3Input = S3MediaObjectInput.builder()
            .bucket(this@DefaultSudoTelephonyClient.transientS3Bucket)
            .key(s3KeyPath)
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

        val sendMessageResponse = graphQLClient.mutate(sendMessageMutation).enqueue()
        if (sendMessageResponse.hasErrors()) {
            logger.debug(
                "Failed sending message to number $remoteNumber"
            )
            throw SudoTelephonyException.SendMessageException(sendMessageResponse.errors().first().message())
        }

        val messageId = sendMessageResponse.data()?.sendMessage()
        if (messageId == null) {
            val error = SudoTelephonyException.SendMessageException()
            logger.debug(
                "Failed sending message to number $remoteNumber: $error"
            )
            throw error
        }

        return getMessage(messageId)
    }

    private fun s3KeyPath(s3Key: String): String {
        return this.getIdentityId().let { it + "/telephony/media/" + s3Key }
    }

    // returns the s3 key for the uploaded file
    private suspend fun uploadFile(localUrl: URL): String {
        logger.info("Uploading ${localUrl.file} to S3")

        val originalFile = File(localUrl.toURI())

        if (!originalFile.exists()) {
            val error = SudoTelephonyException.FileUploadException()
            val message = "File ${localUrl.file} not found at provided path: $error"
            logger.debug(message)
            throw error
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

        val observer = this.transferUtility
            .upload(this.transientS3Bucket, s3KeyPath, tmpFile, fileMetadata)

        return suspendCoroutine { cont ->
            observer.setTransferListener(object : TransferListener {
                override fun onStateChanged(id: Int, state: TransferState?) {
                    if (TransferState.COMPLETED == state) {
                        logger.info("S3 upload completed successfully")
                        cont.resume(s3Key)
                    }
                }

                override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                    logger.info("S3 upload progress changed: id=$id, bytesCurrent=$bytesCurrent, bytesTotal=$bytesTotal")
                }

                override fun onError(id: Int, e: Exception?) {
                    val error = SudoTelephonyException.FileUploadException()
                    val message = e?.message ?: "Failed to upload file: $error"
                    logger.debug(message)
                    cont.resumeWithException(error)
                }
            })
        }
    }

    override suspend fun downloadData(media: MediaObject): ByteArray {
        logger.info("Downloading data ${media.key}")
        val tempPath = this.applicationContext.cacheDir.path + "/" + media.key
        val tempFile = File(tempPath)
        val observer = this.transferUtility.download(media.bucket, media.key, tempFile)
        return suspendCoroutine { cont ->
            observer.setTransferListener(object : TransferListener {
                override fun onStateChanged(id: Int, state: TransferState?) {
                    if (TransferState.COMPLETED == state) {
                        logger.info("Download completed successfully.")

                        val file = File(tempPath)
                        if (!file.exists()) {
                            val error = SudoTelephonyException.FileDownloadException()
                            val message = "File not downloaded: $error"
                            logger.debug(message)
                            cont.resumeWithException(error)
                        }

                        val data = file.readBytes()
                        val decryptedData = this@DefaultSudoTelephonyClient.decryptSealedData(data)
                        cont.resume(decryptedData)
                    }
                }

                override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                    logger.debug("Download progress changed: id=$id, bytesCurrent=$bytesCurrent, bytesTotal=$bytesTotal")
                }

                override fun onError(id: Int, e: Exception?) {
                    val error = SudoTelephonyException.FileDownloadException()
                    val message = e?.message ?: "Failed to download file: $error"
                    logger.debug(message)
                    cont.resumeWithException(error)
                }
            })
        }
    }

    override suspend fun getMessage(id: String): PhoneMessage {
        val keyId = getKeyId()
        if (keyId == null) {
            val error = SudoTelephonyException.GetMessageException()
            logger.debug("Failed to retrieve message for id $id: $error")
            throw error
        }

        val getMessageQuery = GetMessageQuery.builder()
            .id(id)
            .keyId(keyId)
            .build()

        val getMessageResponse = graphQLClient.query(getMessageQuery)
            .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
            .enqueue()

        if (getMessageResponse.hasErrors()) {
            logger.debug("Failed to retrieve message for id $id")
            throw SudoTelephonyException.GetMessageException()
        }

        val message = getMessageResponse.data()?.message?.fragments()?.sealedMessage()
        if (message == null) {
            val error = SudoTelephonyException.GetMessageException()
            logger.debug("Failed to retrieve message for id $id: $error")
            throw error
        }

        var media: List<MediaObject> = emptyList()
        if (message.media() != null) {
            val mediaList = message.media()!!
            media = mediaList.map {
                MediaObject.fromS3Media(
                    it.fragments().s3MediaObject()
                )
            }
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
        return PhoneMessage(
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
    }

    override suspend fun getMessages(
        localNumber: PhoneNumber,
        remoteNumber: String,
        limit: Int?,
        nextToken: String?,
    ): TelephonyListToken<PhoneMessage> {
        val ownerId = sudoUserClient.getSubject()
        if (ownerId == null) {
            val error = SudoTelephonyException.GetMessageException()
            logger.debug("Failed to retrieve messages: $error")
            throw error
        }
        val conversationId = "tl-cnv-" + UUIDV5.UUIDFromNamespaceAndName(
            ownerId,
            localNumber.phoneNumber + remoteNumber
        ).toString().lowercase(Locale.getDefault())

        return getMessages(conversationId, limit, nextToken)
    }

    override suspend fun getMessages(
        conversationId: String,
        limit: Int?,
        nextToken: String?,
    ): TelephonyListToken<PhoneMessage> {
        val keyId = getKeyId()
        if (keyId == null) {
            val error = SudoTelephonyException.GetMessageException()
            logger.debug("Failed to retrieve messages for conversation $conversationId: $error")
            throw error
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

        val listMessageResponse = graphQLClient.query(messagesQuery)
            .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
            .enqueue()

        if (listMessageResponse.hasErrors()) {
            logger.debug("Failed to retrieve messages for conversation $conversationId")
            throw SudoTelephonyException.GetMessageException()
        }

        val messages = listMessageResponse.data()?.listMessages()?.items()
        val newNextToken = listMessageResponse.data()?.listMessages()?.nextToken()

        if (messages == null) {
            val error = SudoTelephonyException.GetMessagesException()
            logger.debug(
                "Failed to retrieve messages for conversation $conversationId: $error"
            )
            throw error
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

        return TelephonyListToken(allMessages, newNextToken)
    }

    override suspend fun deleteMessage(id: String): String {
        val mutation = DeleteMessageMutation.builder()
            .id(id)
            .build()

        val deleteResponse = graphQLClient.mutate(mutation).enqueue()

        if (deleteResponse.hasErrors()) {
            logger.debug("Failed to delete message $id")
            throw SudoTelephonyException.DeleteMessageException(deleteResponse.errors().first().message())
        }

        return deleteResponse.data()?.deleteMessage() ?: ""
    }

    override suspend fun subscribeToMessages(subscriber: PhoneMessageSubscriber, id: String?) {
        logger.debug("Subscribing to new PhoneMessage notifications.")

        val subscriberId = id ?: DEFAULT_MESSAGE_SUBSCRIBER_ID
        val owner = this.sudoUserClient.getSubject()
        require(owner != null) { "Owner was null. The client may not be signed in." }

        this.onMessageSubscriptionManager.replaceSubscriber(subscriberId, subscriber)
        if (this.onMessageSubscriptionManager.watcher == null) {
            val subscription = OnMessageReceivedSubscription.builder().owner(owner).build()
            val watcher = graphQLClient.subscribe(subscription)
            onMessageSubscriptionManager.watcher = watcher
            watcher.execute(
                object : AppSyncSubscriptionCall.Callback<OnMessageReceivedSubscription.Data> {
                    override fun onResponse(response: Response<OnMessageReceivedSubscription.Data>) {
                        try {
                            val item = response.data()?.OnMessage()
                            if (item != null) {
                                val message = item.fragments().sealedMessage()
                                var media: List<MediaObject> = emptyList()
                                if (message.media() != null) {
                                    val mediaList = message.media()!!
                                    media = mediaList.map {
                                        MediaObject.fromS3Media(
                                            it.fragments().s3MediaObject()
                                        )
                                    }
                                }
                                val decoder = Base64.getDecoder()
                                val sealedBody = message.body() ?: ""
                                val sealedBodyData = decoder.decode(sealedBody)
                                val sealedRemoteData = decoder
                                    .decode(message.remotePhoneNumber())

                                val sealedLocalData = decoder
                                    .decode(message.localPhoneNumber())

                                // decrypt the message
                                val decryptedBody = decryptSealedData(sealedBodyData)
                                val decryptedRemote =
                                    decryptSealedData(sealedRemoteData)
                                val decryptedLocal = decryptSealedData(sealedLocalData)
                                val bodyString = String(
                                    decryptedBody, Charset.defaultCharset()
                                )
                                val remoteString = String(
                                    decryptedRemote, Charset.defaultCharset()
                                )
                                val localString = String(
                                    decryptedLocal, Charset.defaultCharset()
                                )
                                val newMessage = PhoneMessage(
                                    message.id(),
                                    message.owner(),
                                    message.conversation(),
                                    Instant.ofEpochMilli(
                                        message.updatedAtEpochMs().toLong()
                                    ),
                                    Instant.ofEpochMilli(
                                        message.createdAtEpochMs().toLong()
                                    ),
                                    localString,
                                    remoteString,
                                    bodyString,
                                    message.direction(),
                                    message.state(),
                                    media
                                )
                                // Notify subscribers
                                onMessageSubscriptionManager.phoneMessageReceived(newMessage)
                            }
                        } catch (e: Exception) {
                            logger.debug("Failed to process the subscription response: $e")
                            throw SudoTelephonyException.SubscribeToMessagesException(cause = e)
                        }
                    }

                    override fun onCompleted() {
                        // Subscription was terminated. Notify the subscribers.
                        onMessageSubscriptionManager.connectionStatusChanged(
                            TelephonySubscriber.ConnectionState.DISCONNECTED
                        )
                    }

                    override fun onFailure(e: ApolloException) {
                        // Failed create a subscription. Notify the subscribers.
                        onMessageSubscriptionManager.connectionStatusChanged(
                            TelephonySubscriber.ConnectionState.DISCONNECTED
                        )
                    }
                })
            onMessageSubscriptionManager.connectionStatusChanged(
                TelephonySubscriber.ConnectionState.CONNECTED
            )
        }
    }

    override fun unsubscribeFromPhoneMessages(id: String?) {
        logger.debug("Unsubscribing from new message notifications.")
        if (id == null) {
            onMessageSubscriptionManager.removeAllSubscribers()
        } else {
            onMessageSubscriptionManager.removeSubscriber(id)
        }
    }

    override suspend fun getConversation(
        conversationId: String,
    ): PhoneMessageConversation {
        val query = GetConversationQuery.builder()
            .id(conversationId)
            .build()

        val getConversationResponse = graphQLClient.query(query)
            .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
            .enqueue()

        if (getConversationResponse.hasErrors()) {
            val error = SudoTelephonyException.GetConversationException()
            logger.debug("Failed to retrieve conversation for id $conversationId: $error")
            throw error
        }

        val conversation = getConversationResponse
            .data()
            ?.getConversation()
            ?.fragments()
            ?.conversation()

        if (conversation == null) {
            val error = SudoTelephonyException.GetConversationException()
            logger.debug(
                "Failed to retrieve conversation for id $conversationId: $error"
            )
            throw error
        }

        // get the last message on the conversation
        val phoneMessage = getMessage(conversation.lastMessage())
        val messageConversation = PhoneMessageConversation(
            conversation.id(),
            conversation.owner(),
            MessageConversationType.fromInternalType(
                conversation.type()
            ),
            conversation.lastMessage(),
            phoneMessage,
            Date(conversation.createdAtEpochMs().toLong()),
            Date(conversation.updatedAtEpochMs().toLong())
        )
        return messageConversation
    }

    override suspend fun getConversation(
        localNumber: PhoneNumber,
        remoteNumber: String,
    ): PhoneMessageConversation {
        val ownerId = sudoUserClient.getSubject()
        if (ownerId == null) {
            val error = SudoTelephonyException.GetMessageException()
            logger.debug("Failed to retrieve message for number ${localNumber.phoneNumber}: $error")
            throw error
        }
        val conversationId = "tl-cnv-" + UUIDV5.UUIDFromNamespaceAndName(
            ownerId,
            localNumber.phoneNumber + remoteNumber
        ).toString().lowercase(Locale.getDefault())
        return getConversation(conversationId)
    }

    override suspend fun getConversations(
        localNumber: PhoneNumber,
        limit: Int?,
        nextToken: String?,
    ): TelephonyListToken<PhoneMessageConversation> {
        logger.info("Fetching conversations")

        val conversationsInput = ConversationFilterInput.builder()
            .phoneNumberId(IDFilterInput.builder().eq(localNumber.id).build())
            .build()
        val query = ListConversationsQuery.builder()
            .filter(conversationsInput)
            .limit(limit)
            .nextToken(nextToken)
            .build()

        val listResponse = graphQLClient.query(query).responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
            .enqueue()

        if (listResponse.hasErrors()) {
            logger.debug(
                "Failed to retrieve conversations for phoneNumber ${localNumber.phoneNumber}"
            )
            throw SudoTelephonyException.GetConversationsException(listResponse.errors().first().message())
        }

        val nextPage = listResponse.data()?.listConversations()?.nextToken()
        val items = listResponse.data()?.listConversations()?.items()
        if (items == null) {
            val error = SudoTelephonyException.GetConversationsException()
            logger.debug(
                "Failed to retrieve conversations for phoneNumber ${localNumber.phoneNumber}: $error"
            )
            throw error
        }
        // go through each conversation and convert it to a PhoneMessageConversation
        val conversations = mutableListOf<PhoneMessageConversation>()

        for (item in items) {
            val conversation = getConversation(item.fragments().conversation().id())
            conversations.add(conversation)
        }
        return TelephonyListToken(conversations, nextPage)
    }
}
