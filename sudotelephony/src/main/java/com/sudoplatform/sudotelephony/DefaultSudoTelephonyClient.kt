/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import android.content.Context
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudotelephony.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudotelephony.internal.data.configuration.GraphQLConfigurationDataService
import com.sudoplatform.sudotelephony.internal.data.configuration.transformers.ConfigurationDataTransformer
import com.sudoplatform.sudotelephony.internal.data.country.GraphQLCountryService
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.DefaultPhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.GraphQLPhoneMessageService
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.PhoneMessageBatchResultTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.PhoneMessageTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneMessage.transformers.SortOrderTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneNumber.GraphQLPhoneNumberService
import com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers.PhoneNumberTransformer
import com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch.GraphQLPhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.data.phoneNumberSearch.transformers.PhoneNumberSearchTransformer
import com.sudoplatform.sudotelephony.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudotelephony.internal.domain.entities.country.CountryService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.PhoneMessageUnsealer
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumber.PhoneNumberService
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneNumberSearch.PhoneNumberSearchService
import com.sudoplatform.sudotelephony.internal.domain.useCases.DefaultUseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneMessage.UpdatePhoneMessagesUseCaseInput
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.DeprovisionedKeyPairRemovalException
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.ListPhoneNumbersForSudoIdUseCaseInput
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.ListPhoneNumbersUseCaseInput
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber.ProvisionPhoneNumberUseCaseInput
import com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumberSearch.SearchAvailablePhoneNumbersUseCaseInput
import com.sudoplatform.sudotelephony.keys.ServiceKeyManager
import com.sudoplatform.sudotelephony.logging.LogConstants
import com.sudoplatform.sudotelephony.subscription.PhoneMessageSubscriber
import com.sudoplatform.sudotelephony.subscription.PhoneNumberSubscriber
import com.sudoplatform.sudotelephony.subscription.SubscriptionService
import com.sudoplatform.sudotelephony.types.ConfigurationData
import com.sudoplatform.sudotelephony.types.CreateSingletonKeysIfAbsentResult
import com.sudoplatform.sudotelephony.types.KeyResult
import com.sudoplatform.sudotelephony.types.ListAPIResult
import com.sudoplatform.sudotelephony.types.ListOutput
import com.sudoplatform.sudotelephony.types.PartialPhoneMessage
import com.sudoplatform.sudotelephony.types.PhoneMessage
import com.sudoplatform.sudotelephony.types.PhoneMessageBatchResult
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.types.PhoneNumberSearch
import com.sudoplatform.sudotelephony.types.inputs.DeletePhoneMessagesInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesForConversationIdInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesForPhoneNumberIdInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersForSudoIdInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersInput
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
import com.sudoplatform.sudotelephony.types.inputs.SearchAvailablePhoneNumbersInput
import com.sudoplatform.sudotelephony.types.inputs.UpdatePhoneMessagesInput
import com.sudoplatform.sudouser.SignInGuard
import com.sudoplatform.sudouser.SudoPlatformSignInCallback
import com.sudoplatform.sudouser.SudoUserClient
import kotlinx.coroutines.runBlocking
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesForConversationIdInput as ListPhoneMessagesForConversationIdInputEntity
import com.sudoplatform.sudotelephony.internal.domain.entities.phoneMessage.ListPhoneMessagesForPhoneNumberIdInput as ListPhoneMessagesForPhoneNumberIdInputEntity

/**
 * Default implementation of the [SudoTelephonyClient] interface and the SDK's composition
 * root. Each public method is a thin adapter: create the use case from the factory, execute
 * it, and map the resulting entity to the public type.
 *
 * Consumers construct instances through [SudoTelephonyClient.Builder].
 *
 * @property context [Context] Application context.
 * @property apiClient [ApiClient] GraphQL client used to make service requests.
 * @property sudoUserClient [SudoUserClient] Used to determine ownership and sign in state.
 * @property serviceKeyManager [ServiceKeyManager] Generates and stores the key pairs that
 *  seal phone number messages.
 * @property logger [Logger] Errors and diagnostic information are logged here.
 * @property enforceSingletonKeyPair [Boolean] Whether every phone number is provisioned with
 *  one shared key pair rather than a key pair of its own.
 * @property countryService [CountryService] The country domain service.
 * @property configurationDataService [ConfigurationDataService] The service configuration
 *  domain service.
 * @property phoneNumberSearchService [PhoneNumberSearchService] The phone number search
 *  domain service.
 * @property phoneNumberService [PhoneNumberService] The provisioned phone number domain
 *  service.
 * @property phoneMessageService [PhoneMessageService] The phone message domain service.
 * @property phoneMessageUnsealer [PhoneMessageUnsealer] Makes sealed messages readable.
 * @property subscriptionService [SubscriptionService] Owns the subscription lifecycle.
 * @property useCaseFactory [UseCaseFactory] Builds the use cases the client delegates to.
 */
internal class DefaultSudoTelephonyClient(
    private val context: Context,
    private val apiClient: ApiClient,
    private val sudoUserClient: SudoUserClient,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger =
        Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO)),
    private val enforceSingletonKeyPair: Boolean = false,
    private val countryService: CountryService =
        GraphQLCountryService(apiClient, logger),
    private val configurationDataService: ConfigurationDataService =
        GraphQLConfigurationDataService(apiClient, logger),
    private val phoneNumberSearchService: PhoneNumberSearchService =
        GraphQLPhoneNumberSearchService(apiClient, logger),
    private val phoneNumberService: PhoneNumberService =
        GraphQLPhoneNumberService(apiClient, logger),
    private val phoneMessageService: PhoneMessageService =
        GraphQLPhoneMessageService(apiClient, logger),
    private val phoneMessageUnsealer: PhoneMessageUnsealer =
        DefaultPhoneMessageUnsealer(
            phoneNumberService = phoneNumberService,
            serviceKeyManager = serviceKeyManager,
            unsealer = Unsealer(serviceKeyManager),
            logger = logger,
        ),
    private val subscriptionService: SubscriptionService =
        SubscriptionService(apiClient, sudoUserClient, logger),
    private val useCaseFactory: UseCaseFactory =
        DefaultUseCaseFactory(
            countryService = countryService,
            configurationDataService = configurationDataService,
            phoneNumberSearchService = phoneNumberSearchService,
            phoneNumberService = phoneNumberService,
            phoneMessageService = phoneMessageService,
            phoneMessageUnsealer = phoneMessageUnsealer,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
            enforceSingletonKeyPair = enforceSingletonKeyPair,
        ),
) : SudoTelephonyClient {
    /**
     * Checksum's for each file are generated and are used to create a checksum that is used when
     * publishing to maven central. In order to retry a failed publish without needing to change any
     * functionality, we need a way to generate a different checksum for the source code. We can
     * change the value of this property which will generate a different checksum for publishing
     * and allow us to retry. The value of `version` doesn't need to be kept up-to-date with the
     * version of the code.
     */
    private val version: String = "1.0.0"

    /** Signs the user in through the caller's callback before an operation, when one is set. */
    private val signInGuard = SignInGuard(sudoUserClient)

    override fun setSignInCallback(callback: SudoPlatformSignInCallback?) {
        logger.debug("setSignInCallback")
        runBlocking { signInGuard.setCallback(callback) }
    }

    override suspend fun getSupportedCountries(): List<String> {
        logger.debug("getSupportedCountries")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createGetSupportedCountriesUseCase()
        return useCase.execute()
    }

    override suspend fun getConfigurationData(): ConfigurationData {
        logger.debug("getConfigurationData")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createGetConfigurationDataUseCase()
        return ConfigurationDataTransformer.fromEntityToApi(useCase.execute())
    }

    override suspend fun searchAvailablePhoneNumbers(input: SearchAvailablePhoneNumbersInput): PhoneNumberSearch {
        logger.debug("searchAvailablePhoneNumbers")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createSearchAvailablePhoneNumbersUseCase()
        val result =
            useCase.execute(
                SearchAvailablePhoneNumbersUseCaseInput(
                    countryCode = input.countryCode,
                    areaCode = input.areaCode,
                    limit = input.limit,
                ),
            )
        return PhoneNumberSearchTransformer.fromEntityToApi(result)
    }

    override suspend fun provisionPhoneNumber(input: ProvisionPhoneNumberInput): PhoneNumber {
        logger.debug("provisionPhoneNumber")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createProvisionPhoneNumberUseCase()
        val result =
            useCase.execute(
                ProvisionPhoneNumberUseCaseInput(
                    searchId = input.searchId,
                    phoneNumber = input.phoneNumber,
                    ownershipProofToken = input.ownershipProofToken,
                    clientRefId = input.clientRefId,
                ),
            )
        return PhoneNumberTransformer.fromEntityToApi(result)
    }

    override suspend fun deprovisionPhoneNumber(id: String): PhoneNumber {
        logger.debug("deprovisionPhoneNumber")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createDeprovisionPhoneNumberUseCase()
        try {
            return PhoneNumberTransformer.fromEntityToApi(useCase.execute(id))
        } catch (e: DeprovisionedKeyPairRemovalException) {
            throw SudoTelephonyClient.PhoneNumberException.KeyPairRemovalFailedException(
                deprovisioned = PhoneNumberTransformer.fromEntityToApi(e.deprovisioned),
                message = StringConstants.KEY_PAIR_REMOVAL_FAILED_MSG,
                cause = e.cause,
            )
        }
    }

    override suspend fun getPhoneNumber(id: String): PhoneNumber? {
        logger.debug("getPhoneNumber")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createGetPhoneNumberUseCase()
        return useCase.execute(id)?.let { PhoneNumberTransformer.fromEntityToApi(it) }
    }

    override suspend fun listPhoneNumbers(input: ListPhoneNumbersInput): ListOutput<PhoneNumber> {
        logger.debug("listPhoneNumbers")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createListPhoneNumbersUseCase()
        val result =
            useCase.execute(
                ListPhoneNumbersUseCaseInput(
                    limit = input.limit,
                    nextToken = input.nextToken,
                ),
            )
        return ListOutput(
            items = result.items.map { PhoneNumberTransformer.fromEntityToApi(it) },
            nextToken = result.nextToken,
        )
    }

    override suspend fun listPhoneNumbersForSudoId(input: ListPhoneNumbersForSudoIdInput): ListOutput<PhoneNumber> {
        logger.debug("listPhoneNumbersForSudoId")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createListPhoneNumbersUseCase()
        val result =
            useCase.execute(
                ListPhoneNumbersForSudoIdUseCaseInput(
                    sudoId = input.sudoId,
                    limit = input.limit,
                    nextToken = input.nextToken,
                ),
            )
        return ListOutput(
            items = result.items.map { PhoneNumberTransformer.fromEntityToApi(it) },
            nextToken = result.nextToken,
        )
    }

    override suspend fun getPhoneMessage(id: String): PhoneMessage? {
        logger.debug("getPhoneMessage")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createGetPhoneMessageUseCase()
        return useCase.execute(id)?.let { PhoneMessageTransformer.fromEntityToApi(it) }
    }

    override suspend fun listPhoneMessagesForPhoneNumberId(
        input: ListPhoneMessagesForPhoneNumberIdInput,
    ): ListAPIResult<PhoneMessage, PartialPhoneMessage> {
        logger.debug("listPhoneMessagesForPhoneNumberId")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createListPhoneMessagesUseCase()
        val result =
            useCase.execute(
                ListPhoneMessagesForPhoneNumberIdInputEntity(
                    phoneNumberId = input.phoneNumberId,
                    sortOrder = input.sortOrder?.let { SortOrderTransformer.fromApiToEntity(it) },
                    limit = input.limit,
                    nextToken = input.nextToken,
                ),
            )
        return PhoneMessageTransformer.fromListEntityToApi(result)
    }

    override suspend fun listPhoneMessagesForConversationId(
        input: ListPhoneMessagesForConversationIdInput,
    ): ListAPIResult<PhoneMessage, PartialPhoneMessage> {
        logger.debug("listPhoneMessagesForConversationId")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createListPhoneMessagesUseCase()
        val result =
            useCase.execute(
                ListPhoneMessagesForConversationIdInputEntity(
                    conversationId = input.conversationId,
                    sortOrder = input.sortOrder?.let { SortOrderTransformer.fromApiToEntity(it) },
                    limit = input.limit,
                    nextToken = input.nextToken,
                ),
            )
        return PhoneMessageTransformer.fromListEntityToApi(result)
    }

    override suspend fun deletePhoneMessages(input: DeletePhoneMessagesInput): PhoneMessageBatchResult {
        logger.debug("deletePhoneMessages")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createDeletePhoneMessagesUseCase()
        return PhoneMessageBatchResultTransformer.fromEntityToApi(useCase.execute(input.ids))
    }

    override suspend fun updatePhoneMessages(input: UpdatePhoneMessagesInput): PhoneMessageBatchResult {
        logger.debug("updatePhoneMessages")
        signInGuard.ensureSignedIn()
        val useCase = useCaseFactory.createUpdatePhoneMessagesUseCase()
        val result =
            useCase.execute(
                UpdatePhoneMessagesUseCaseInput(ids = input.ids, seen = input.seen),
            )
        return PhoneMessageBatchResultTransformer.fromEntityToApi(result)
    }

    override suspend fun subscribeToPhoneNumbers(
        subscriptionId: String,
        subscriber: PhoneNumberSubscriber,
    ) {
        logger.debug("subscribeToPhoneNumbers")
        subscriptionService.subscribeToPhoneNumbers(subscriptionId, subscriber)
    }

    override fun unsubscribeFromPhoneNumbers(subscriptionId: String) {
        logger.debug("unsubscribeFromPhoneNumbers")
        subscriptionService.unsubscribeFromPhoneNumbers(subscriptionId)
    }

    override fun unsubscribeAllFromPhoneNumbers() {
        logger.debug("unsubscribeAllFromPhoneNumbers")
        subscriptionService.unsubscribeAllFromPhoneNumbers()
    }

    override suspend fun subscribeToPhoneMessages(
        subscriptionId: String,
        subscriber: PhoneMessageSubscriber,
    ) {
        logger.debug("subscribeToPhoneMessages")
        subscriptionService.subscribeToPhoneMessages(subscriptionId, subscriber)
    }

    override fun unsubscribeFromPhoneMessages(subscriptionId: String) {
        logger.debug("unsubscribeFromPhoneMessages")
        subscriptionService.unsubscribeFromPhoneMessages(subscriptionId)
    }

    override fun unsubscribeAllFromPhoneMessages() {
        logger.debug("unsubscribeAllFromPhoneMessages")
        subscriptionService.unsubscribeAllFromPhoneMessages()
    }

    override suspend fun importKeys(archiveData: ByteArray) {
        logger.debug("importKeys")
        if (archiveData.isEmpty()) {
            throw SudoTelephonyClient.CryptographicKeysException.InvalidArgumentException(
                StringConstants.EMPTY_KEY_ARCHIVE_MSG,
            )
        }
        try {
            serviceKeyManager.importKeys(archiveData)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretCryptographicKeysException(e)
        }
    }

    override suspend fun exportKeys(): ByteArray {
        logger.debug("exportKeys")
        try {
            return serviceKeyManager.exportKeys()
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretCryptographicKeysException(e)
        }
    }

    override suspend fun createSingletonKeysIfAbsent(): CreateSingletonKeysIfAbsentResult {
        logger.debug("createSingletonKeysIfAbsent")
        signInGuard.ensureSignedIn()
        try {
            val result = useCaseFactory.createCreateSingletonKeysIfAbsentUseCase().execute()
            return CreateSingletonKeysIfAbsentResult(
                keyPair = KeyResult(created = result.created, keyId = result.keyPair.keyId),
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretCryptographicKeysException(e)
        }
    }

    override fun reset() {
        logger.debug("reset")
        close()
        this.serviceKeyManager.removeAllKeys()
    }

    override fun close() {
        logger.debug("close")
        subscriptionService.close()
    }
}
