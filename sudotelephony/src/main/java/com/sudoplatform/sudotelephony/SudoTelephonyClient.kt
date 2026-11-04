/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import android.content.Context
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudokeymanager.AndroidSQLiteStore
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.keys.DefaultServiceKeyManager
import com.sudoplatform.sudotelephony.logging.LogConstants
import com.sudoplatform.sudotelephony.subscription.PhoneMessageSubscriber
import com.sudoplatform.sudotelephony.subscription.PhoneNumberSubscriber
import com.sudoplatform.sudotelephony.subscription.Subscriber
import com.sudoplatform.sudotelephony.types.ListOutput
import com.sudoplatform.sudotelephony.types.PhoneMessage
import com.sudoplatform.sudotelephony.types.PhoneMessageBatchResult
import com.sudoplatform.sudotelephony.types.PhoneNumber
import com.sudoplatform.sudotelephony.types.PhoneNumberSearch
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudotelephony.types.inputs.DeletePhoneMessagesInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneMessagesInput
import com.sudoplatform.sudotelephony.types.inputs.ListPhoneNumbersInput
import com.sudoplatform.sudotelephony.types.inputs.ProvisionPhoneNumberInput
import com.sudoplatform.sudotelephony.types.inputs.SearchAvailablePhoneNumbersInput
import com.sudoplatform.sudotelephony.types.inputs.UpdatePhoneMessagesInput
import com.sudoplatform.sudouser.SudoUserClient
import java.util.Objects

/**
 * Interface encapsulating a library for interacting with the Sudo Platform Telephony service.
 *
 * Operations are grouped by domain; all service calls are `suspend` and return public types.
 * Construct instances through the [Builder]; the concrete implementation is internal.
 *
 * @sample com.sudoplatform.sudotelephony.samples.Samples.sudoTelephonyClient
 */
interface SudoTelephonyClient : AutoCloseable {
    companion object {
        /** Create a [Builder] for [SudoTelephonyClient]. */
        @JvmStatic
        fun builder() = Builder()

        /** Name of the service stanza in `sudoplatformconfig.json`. */
        internal const val CONFIG_TELEPHONY_SERVICE = "telephonyService"

        /** Namespace the telephony key ring identifier is derived from. */
        const val DEFAULT_KEYRING_SERVICE_NAME = "sudo-telephony"

        /** Namespace the telephony keys are stored under in the device key store. */
        const val DEFAULT_KEY_NAMESPACE = "tels"
    }

    /**
     * Retrieve the ISO 3166-1 alpha-2 country codes in which the telephony service can
     * search for and provision phone numbers.
     *
     * A code returned here is the value to pass as the country code of a phone number
     * search.
     *
     * @return [List] of the supported ISO 3166-1 alpha-2 country codes, for example
     *  `"US"`, `"CA"`, `"GB"`.
     * @throws SupportedCountriesException if the supported countries could not be retrieved.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.getSupportedCountries
     */
    @Throws(SupportedCountriesException::class)
    suspend fun getSupportedCountries(): List<String>

    /**
     * Search for phone numbers available to provision in an area code.
     *
     * The service keeps the returned search for a short time so that provisioning can check
     * the requested number came from it. The carrier does not reserve the numbers, so one can
     * be taken by someone else before it is provisioned.
     *
     * @param input [SearchAvailablePhoneNumbersInput] Parameters narrowing the search.
     * @return The [PhoneNumberSearch] holding the available numbers.
     * @throws PhoneNumberSearchException if the search could not be completed.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.searchAvailablePhoneNumbers
     */
    @Throws(PhoneNumberSearchException::class)
    suspend fun searchAvailablePhoneNumbers(input: SearchAvailablePhoneNumbersInput): PhoneNumberSearch

    /**
     * Provision one of the numbers from a previous search.
     *
     * The SDK generates the key pair that seals the number's messages, registers the public
     * key with the service, and keeps the private key in the device key store. Losing that
     * private key makes the number's messages unreadable.
     *
     * The returned number is in [com.sudoplatform.sudotelephony.types.PhoneNumberState.PROVISIONING];
     * the order completes asynchronously with the telephony provider.
     *
     * @param input [ProvisionPhoneNumberInput] The search and number to provision.
     * @return The [PhoneNumber] being provisioned.
     * @throws PhoneNumberException if the number could not be provisioned.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.provisionPhoneNumber
     */
    @Throws(PhoneNumberException::class)
    suspend fun provisionPhoneNumber(input: ProvisionPhoneNumberInput): PhoneNumber

    /**
     * Release one of the caller's phone numbers.
     *
     * The unit of the caller's phone number allowance is returned at once and the number is
     * handed back to the telephony provider in the background. Releasing a number that is
     * already released returns it unchanged, so the call is safe to repeat.
     *
     * Only an [com.sudoplatform.sudotelephony.types.PhoneNumberState.ACTIVE] or
     * [com.sudoplatform.sudotelephony.types.PhoneNumberState.SUSPENDED] number can be
     * released; releasing one that is still being provisioned fails with
     * [PhoneNumberException.InvalidPhoneNumberStateException].
     *
     * @param id [String] ID of the phone number to release.
     * @return The [PhoneNumber] in [com.sudoplatform.sudotelephony.types.PhoneNumberState.RELEASED].
     * @throws PhoneNumberException if the number could not be released.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.deprovisionPhoneNumber
     */
    @Throws(PhoneNumberException::class)
    suspend fun deprovisionPhoneNumber(id: String): PhoneNumber

    /**
     * Retrieve one of the caller's phone numbers, in any state including
     * [com.sudoplatform.sudotelephony.types.PhoneNumberState.RELEASED] and
     * [com.sudoplatform.sudotelephony.types.PhoneNumberState.FAILED].
     *
     * Use this to follow a number through provisioning: it is reported as
     * [com.sudoplatform.sudotelephony.types.PhoneNumberState.PROVISIONING] until the order
     * completes with the telephony provider.
     *
     * @param id [String] ID of the phone number to retrieve.
     * @return The [PhoneNumber], or `null` when no number with [id] belongs to the caller.
     * @throws PhoneNumberException if the number could not be retrieved.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.getPhoneNumber
     */
    @Throws(PhoneNumberException::class)
    suspend fun getPhoneNumber(id: String): PhoneNumber?

    /**
     * Retrieve a page of the caller's phone numbers, oldest first.
     *
     * Lists numbers that are being provisioned, active or suspended. Released and failed
     * numbers are not included; fetch those by id with [getPhoneNumber].
     *
     * @param input [ListPhoneNumbersInput] Paging parameters.
     * @return A [ListOutput] holding this page of numbers and the token for the next one.
     * @throws PhoneNumberException if the numbers could not be retrieved.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.listPhoneNumbers
     */
    @Throws(PhoneNumberException::class)
    suspend fun listPhoneNumbers(input: ListPhoneNumbersInput = ListPhoneNumbersInput()): ListOutput<PhoneNumber>

    /**
     * Retrieve one of the caller's phone messages, with its sealed fields made readable.
     *
     * The other party's number and the message text are sealed with the key registered for
     * the message's phone number, so reading a message requires the device that provisioned
     * that number.
     *
     * @param id [String] ID of the message to retrieve.
     * @return The [PhoneMessage], or `null` when no message with [id] belongs to the caller.
     * @throws PhoneMessageException if the message could not be retrieved or unsealed.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.getPhoneMessage
     */
    @Throws(PhoneMessageException::class)
    suspend fun getPhoneMessage(id: String): PhoneMessage?

    /**
     * Retrieve a page of a phone number's messages, with their sealed fields made readable.
     *
     * Defaults to newest first across every conversation of the number; set
     * [ListPhoneMessagesInput.conversationId] to limit the page to one conversation.
     *
     * The service filters the inbox by owner rather than rejecting an unknown phone number,
     * so a number that does not exist, or belongs to someone else, reads as an empty page.
     *
     * @param input [ListPhoneMessagesInput] Which messages to list and how to page them.
     * @return A [ListOutput] holding this page of messages and the token for the next one.
     * @throws PhoneMessageException if the messages could not be retrieved or unsealed.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.listPhoneMessages
     */
    @Throws(PhoneMessageException::class)
    suspend fun listPhoneMessages(input: ListPhoneMessagesInput): ListOutput<PhoneMessage>

    /**
     * Delete a batch of the caller's phone messages.
     *
     * The batch can partially succeed: a message that was not found, or does not belong to
     * the caller, is reported in [PhoneMessageBatchResult.failedIds] rather than failing the
     * whole call, so check the result rather than relying on the absence of an exception.
     *
     * @param input [DeletePhoneMessagesInput] The messages to delete, at most 100.
     * @return A [PhoneMessageBatchResult] splitting the batch into deleted and not deleted.
     * @throws PhoneMessageException if the batch could not be submitted.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.deletePhoneMessages
     */
    @Throws(PhoneMessageException::class)
    suspend fun deletePhoneMessages(input: DeletePhoneMessagesInput): PhoneMessageBatchResult

    /**
     * Change the read state of a batch of the caller's phone messages.
     *
     * The batch can partially succeed, in the same way as [deletePhoneMessages].
     *
     * @param input [UpdatePhoneMessagesInput] The messages to update and the state to set.
     * @return A [PhoneMessageBatchResult] splitting the batch into updated and not updated.
     * @throws PhoneMessageException if the batch could not be submitted.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.updatePhoneMessages
     */
    @Throws(PhoneMessageException::class)
    suspend fun updatePhoneMessages(input: UpdatePhoneMessagesInput): PhoneMessageBatchResult

    /**
     * Subscribe to changes to the caller's phone numbers.
     *
     * This is how a caller learns that a number being provisioned became
     * [com.sudoplatform.sudotelephony.types.PhoneNumberState.ACTIVE] or
     * [com.sudoplatform.sudotelephony.types.PhoneNumberState.FAILED], rather than polling
     * [getPhoneNumber].
     *
     * Subscribing again with the same [subscriptionId] replaces the previous subscriber. The
     * subscriber is told when the connection opens and closes; on
     * [Subscriber.ConnectionState.DISCONNECTED] every subscriber is dropped and the caller
     * must subscribe again.
     *
     * @param subscriptionId [String] A unique identifier for this subscription.
     * @param subscriber [PhoneNumberSubscriber] Notified when one of the caller's numbers
     *  changes.
     * @throws PhoneNumberException if the subscription could not be established.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.subscribeToPhoneNumbers
     */
    @Throws(PhoneNumberException::class)
    suspend fun subscribeToPhoneNumbers(
        subscriptionId: String,
        subscriber: PhoneNumberSubscriber,
    )

    /**
     * Unsubscribe the subscriber registered under [subscriptionId] from phone number changes.
     *
     * @param subscriptionId [String] The subscription identifier to unsubscribe.
     */
    fun unsubscribeFromPhoneNumbers(subscriptionId: String)

    /** Unsubscribe every subscriber from phone number changes. */
    fun unsubscribeAllFromPhoneNumbers()

    /**
     * Subscribe to the caller's phone messages being received, updated or deleted.
     *
     * An event carries no message content; fetch the message with [getPhoneMessage] to read
     * its text.
     *
     * Subscribing again with the same [subscriptionId] replaces the previous subscriber.
     *
     * @param subscriptionId [String] A unique identifier for this subscription.
     * @param subscriber [PhoneMessageSubscriber] Notified when one of the caller's messages
     *  changes.
     * @throws PhoneMessageException if the subscription could not be established.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.subscribeToPhoneMessages
     */
    @Throws(PhoneMessageException::class)
    suspend fun subscribeToPhoneMessages(
        subscriptionId: String,
        subscriber: PhoneMessageSubscriber,
    )

    /**
     * Unsubscribe the subscriber registered under [subscriptionId] from phone message events.
     *
     * @param subscriptionId [String] The subscription identifier to unsubscribe.
     */
    fun unsubscribeFromPhoneMessages(subscriptionId: String)

    /** Unsubscribe every subscriber from phone message events. */
    fun unsubscribeAllFromPhoneMessages()

    /**
     * Replace the cryptographic keys held on this device with those in [archiveData].
     *
     * A phone number's messages can only be unsealed with the private key generated when the
     * number was provisioned, so importing an archive exported from another device is how a
     * number's messages become readable there.
     *
     * Every key already on this device is removed first, so the store ends up matching the
     * archive rather than merging with it.
     *
     * @param archiveData [ByteArray] The key archive to import, as returned by [exportKeys].
     * @throws CryptographicKeysException if the archive is empty or cannot be read.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.importKeys
     */
    @Throws(CryptographicKeysException::class)
    suspend fun importKeys(archiveData: ByteArray)

    /**
     * Export the cryptographic keys held on this device as an archive.
     *
     * The archive carries the private keys that unseal phone message content, so it is secret
     * and must be stored and transferred accordingly.
     *
     * @return The key archive, to be imported with [importKeys].
     * @throws CryptographicKeysException if the archive cannot be written.
     * @sample com.sudoplatform.sudotelephony.samples.Samples.exportKeys
     */
    @Throws(CryptographicKeysException::class)
    suspend fun exportKeys(): ByteArray

    /**
     * Reset any internal state, dropping every subscription.
     */
    fun reset()

    /**
     * Builder used to construct the [SudoTelephonyClient].
     */
    class Builder internal constructor() {
        private var context: Context? = null
        private var sudoUserClient: SudoUserClient? = null
        private var apiClient: ApiClient? = null
        private var keyManager: KeyManagerInterface? = null
        private var namespace: String = DEFAULT_KEY_NAMESPACE
        private var databaseName: String = AndroidSQLiteStore.DEFAULT_DATABASE_NAME
        private var logger: Logger =
            Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))

        /**
         * Provide the application context (required input).
         */
        fun setContext(context: Context) =
            also {
                this.context = context
            }

        /**
         * Provide the implementation of the [SudoUserClient] used to perform sign in and
         * ownership operations (required input).
         */
        fun setSudoUserClient(sudoUserClient: SudoUserClient) =
            also {
                this.sudoUserClient = sudoUserClient
            }

        /**
         * Provide an [ApiClient] for the [SudoTelephonyClient] to use (optional input). If
         * this is not supplied, an [ApiClient] will be constructed and used.
         */
        fun setApiClient(apiClient: ApiClient) =
            also {
                this.apiClient = apiClient
            }

        /**
         * Provide the implementation of the [Logger] used for logging errors (optional
         * input). If a value is not supplied a default implementation will be used.
         */
        fun setLogger(logger: Logger) =
            also {
                this.logger = logger
            }

        /**
         * Provide a [KeyManagerInterface] for storing the key pairs that seal phone number
         * messages (optional input). If this is not supplied, a key manager backed by the
         * Android key store is constructed from [setNamespace] and [setDatabaseName].
         */
        fun setKeyManager(keyManager: KeyManagerInterface) =
            also {
                this.keyManager = keyManager
            }

        /**
         * Provide the namespace the telephony keys are stored under in the device key store
         * (optional input). Defaults to [DEFAULT_KEY_NAMESPACE]. Supply a distinct value when
         * several instances must not share keys.
         */
        fun setNamespace(namespace: String) =
            also {
                this.namespace = namespace
            }

        /**
         * Provide the name of the database backing the device key store (optional input).
         */
        fun setDatabaseName(databaseName: String) =
            also {
                this.databaseName = databaseName
            }

        /** A configuration item that is needed is missing. */
        class ConfigurationException(
            message: String,
            cause: Throwable? = null,
        ) : RuntimeException(message, cause)

        /**
         * Construct the [SudoTelephonyClient]. Will throw a [NullPointerException] if the
         * context and [SudoUserClient] have not been provided.
         *
         * The AppSync endpoint is resolved from the telephony service stanza in
         * `sudoplatformconfig.json`; it is never hard-coded.
         *
         * @return A configured [SudoTelephonyClient].
         */
        @Throws(NullPointerException::class, ConfigurationException::class)
        fun build(): SudoTelephonyClient {
            Objects.requireNonNull(context, "Context must be provided.")
            Objects.requireNonNull(sudoUserClient, "SudoUserClient must be provided.")

            val client =
                apiClient ?: ApiClient(
                    ApiClientManager.getClient(
                        this@Builder.context!!,
                        this@Builder.sudoUserClient!!,
                        CONFIG_TELEPHONY_SERVICE,
                    ),
                    logger,
                )

            val serviceKeyManager =
                DefaultServiceKeyManager(
                    keyRingServiceName = DEFAULT_KEYRING_SERVICE_NAME,
                    userClient = sudoUserClient!!,
                    keyManager =
                        keyManager ?: KeyManagerFactory(context!!).createAndroidKeyManager(
                            this.namespace,
                            this.databaseName,
                        ),
                    logger = logger,
                )

            return DefaultSudoTelephonyClient(
                context = context!!,
                apiClient = client,
                sudoUserClient = sudoUserClient!!,
                serviceKeyManager = serviceKeyManager,
                logger = logger,
            )
        }
    }

    /**
     * Defines the exceptions for the supported country based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class SupportedCountriesException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        /** A generic, non-recoverable failure. */
        class FailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : SupportedCountriesException(message = message, cause = cause)

        /** The caller is not authenticated or the session has expired. */
        class AuthenticationException(
            message: String? = null,
            cause: Throwable? = null,
        ) : SupportedCountriesException(message = message, cause = cause)

        /** An argument supplied to the service was invalid. */
        class InvalidArgumentException(
            message: String? = null,
            cause: Throwable? = null,
        ) : SupportedCountriesException(message = message, cause = cause)

        /** The identity context required for the operation was missing. */
        class IdentityContextMissingException(
            message: String? = null,
            cause: Throwable? = null,
        ) : SupportedCountriesException(message = message, cause = cause)

        /**
         * The source of the supported countries was reachable but returned an unusable
         * result, so the set of supported countries could not be determined.
         */
        class SupportedCountriesUnavailableException(
            message: String? = null,
            cause: Throwable? = null,
        ) : SupportedCountriesException(message = message, cause = cause)

        /** The service reported an error. */
        class ServiceException(
            message: String? = null,
            cause: Throwable? = null,
        ) : SupportedCountriesException(message = message, cause = cause)

        /** An unexpected error wrapping the underlying cause. */
        class UnknownException(
            cause: Throwable,
        ) : SupportedCountriesException(cause = cause)
    }

    /**
     * Defines the exceptions for the phone number search based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class PhoneNumberSearchException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        /** A generic, non-recoverable failure. */
        class FailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberSearchException(message = message, cause = cause)

        /** The caller is not authenticated or the session has expired. */
        class AuthenticationException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberSearchException(message = message, cause = cause)

        /** An argument supplied to the service was invalid. */
        class InvalidArgumentException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberSearchException(message = message, cause = cause)

        /** The identity context required for the operation was missing. */
        class IdentityContextMissingException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberSearchException(message = message, cause = cause)

        /** The telephony service cannot search for phone numbers in the requested country. */
        class UnsupportedCountryException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberSearchException(message = message, cause = cause)

        /** The requested area code is not in use in the requested country. */
        class InvalidAreaCodeException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberSearchException(message = message, cause = cause)

        /** The requested number of results is above the maximum the service accepts. */
        class LimitExceededException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberSearchException(message = message, cause = cause)

        /** The user is not entitled to use the telephony service. */
        class InsufficientEntitlementsException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberSearchException(message = message, cause = cause)

        /** The service reported an error. */
        class ServiceException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberSearchException(message = message, cause = cause)

        /** An unexpected error wrapping the underlying cause. */
        class UnknownException(
            cause: Throwable,
        ) : PhoneNumberSearchException(cause = cause)
    }

    /**
     * Defines the exceptions for the provisioned phone number based methods. No Apollo or
     * transport error types are ever leaked to consumers.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class PhoneNumberException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        /** A generic, non-recoverable failure. */
        class FailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /** The service accepted the request but returned no phone number. */
        class ProvisionFailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /** The caller is not authenticated or the session has expired. */
        class AuthenticationException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /** An argument supplied to the service was invalid. */
        class InvalidArgumentException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /** The identity context required for the operation was missing. */
        class IdentityContextMissingException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /**
         * The search named for provisioning does not exist, belongs to another user, or has
         * expired. Run a new search and provision from its results.
         */
        class PhoneNumberSearchExpiredException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /** The number to provision is not one of the named search's results. */
        class PhoneNumberNotInSearchException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /**
         * The number is no longer available, for example because someone else took it after
         * the search.
         */
        class PhoneNumberUnavailableException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /** No phone number with the requested id belongs to the caller. */
        class PhoneNumberNotFoundException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /**
         * The phone number is in a state that does not allow the operation. Only an
         * [com.sudoplatform.sudotelephony.types.PhoneNumberState.ACTIVE] or
         * [com.sudoplatform.sudotelephony.types.PhoneNumberState.SUSPENDED] number can be
         * released.
         *
         * @property state [PhoneNumberState] The number's current state, when the service
         *  reported it.
         */
        class InvalidPhoneNumberStateException(
            val state: PhoneNumberState? = null,
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /** The supplied Sudo ownership proof could not be verified. */
        class InvalidTokenException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /** The user's entitlements do not cover another phone number. */
        class InsufficientEntitlementsException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /** The service reported an error. */
        class ServiceException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneNumberException(message = message, cause = cause)

        /** An unexpected error wrapping the underlying cause. */
        class UnknownException(
            cause: Throwable,
        ) : PhoneNumberException(cause = cause)
    }

    /**
     * Defines the exceptions for the phone message based methods. No Apollo or transport
     * error types are ever leaked to consumers.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class PhoneMessageException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        /** A generic, non-recoverable failure. */
        class FailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneMessageException(message = message, cause = cause)

        /** The caller is not authenticated or the session has expired. */
        class AuthenticationException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneMessageException(message = message, cause = cause)

        /** An argument supplied to the service was invalid. */
        class InvalidArgumentException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneMessageException(message = message, cause = cause)

        /** The identity context required for the operation was missing. */
        class IdentityContextMissingException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneMessageException(message = message, cause = cause)

        /** No phone number with the requested id belongs to the caller. */
        class PhoneNumberNotFoundException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneMessageException(message = message, cause = cause)

        /**
         * The batch names more messages than the service accepts in one call, which is 100.
         */
        class LimitExceededException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneMessageException(message = message, cause = cause)

        /**
         * The private key that would unseal the message is not in this device's key store.
         * A message can only be read on the device that provisioned its phone number.
         */
        class KeyNotFoundException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneMessageException(message = message, cause = cause)

        /** The message was returned but its sealed fields could not be unsealed. */
        class UnsealingFailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneMessageException(message = message, cause = cause)

        /** The service reported an error. */
        class ServiceException(
            message: String? = null,
            cause: Throwable? = null,
        ) : PhoneMessageException(message = message, cause = cause)

        /** An unexpected error wrapping the underlying cause. */
        class UnknownException(
            cause: Throwable,
        ) : PhoneMessageException(cause = cause)
    }

    /**
     * Defines the exceptions for the cryptographic key based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class CryptographicKeysException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        /** An argument supplied to the operation was invalid. */
        class InvalidArgumentException(
            message: String? = null,
            cause: Throwable? = null,
        ) : CryptographicKeysException(message = message, cause = cause)

        /** The key archive could not be read or written. */
        class SecureKeyArchiveException(
            message: String? = null,
            cause: Throwable? = null,
        ) : CryptographicKeysException(message = message, cause = cause)

        /** An unexpected error wrapping the underlying cause. */
        class UnknownException(
            cause: Throwable,
        ) : CryptographicKeysException(cause = cause)
    }
}
