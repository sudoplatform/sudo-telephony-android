/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.common.transformers

import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudotelephony.SudoTelephonyClient
import com.sudoplatform.sudotelephony.SudoTelephonyClient.ConfigurationDataException
import com.sudoplatform.sudotelephony.SudoTelephonyClient.CryptographicKeysException
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneMessageException
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException
import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberSearchException
import com.sudoplatform.sudotelephony.SudoTelephonyClient.SupportedCountriesException
import com.sudoplatform.sudotelephony.internal.data.common.StringConstants
import com.sudoplatform.sudotelephony.internal.data.phoneNumber.transformers.PhoneNumberStateTransformer
import com.sudoplatform.sudotelephony.keys.DeviceKeyManager
import com.sudoplatform.sudotelephony.types.PhoneNumberState
import com.sudoplatform.sudouser.exceptions.HTTP_STATUS_CODE_KEY
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException
import com.sudoplatform.sudotelephony.graphql.type.PhoneNumberState as PhoneNumberStateGraphQL

/**
 * Transformer for interpreting and converting errors from various sources into appropriate
 * [SudoTelephonyClient] exception types.
 */
internal object ErrorTransformer {
    /**
     * Interprets a GraphQL error and converts it to an appropriate
     * [SupportedCountriesException].
     *
     * @param e [GraphQLResponse.Error] The GraphQL error to interpret.
     * @return The corresponding [SupportedCountriesException].
     */
    fun interpretSupportedCountriesError(e: GraphQLResponse.Error): SupportedCountriesException {
        val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
        val error = e.extensions?.get(StringConstants.ERROR_TYPE)?.toString() ?: ""

        if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return SupportedCountriesException.AuthenticationException("$e")
        }
        if (httpStatusCode != null && httpStatusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
            return SupportedCountriesException.FailedException("$e")
        }
        if (error.contains(StringConstants.ERROR_SUPPORTED_COUNTRIES_UNAVAILABLE)) {
            return SupportedCountriesException.SupportedCountriesUnavailableException(
                StringConstants.SUPPORTED_COUNTRIES_UNAVAILABLE_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_INVALID_ARGUMENT)) {
            return SupportedCountriesException.InvalidArgumentException(
                StringConstants.INVALID_ARGUMENT_ERROR_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_IDENTITY_CONTEXT_MISSING)) {
            return SupportedCountriesException.IdentityContextMissingException(
                StringConstants.IDENTITY_CONTEXT_MISSING_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_SERVICE)) {
            return SupportedCountriesException.ServiceException(
                StringConstants.SERVICE_ERROR_MSG,
            )
        }
        return SupportedCountriesException.FailedException(e.toString())
    }

    /**
     * Interprets a throwable exception and converts it to an appropriate
     * [SupportedCountriesException].
     *
     * @param e [Throwable] The throwable to interpret.
     * @return [Throwable] The corresponding exception, which may be the original exception if
     *  already handled.
     */
    fun interpretSupportedCountriesException(e: Throwable): Throwable =
        when (e) {
            is CancellationException,
            is SupportedCountriesException,
            -> e
            else -> SupportedCountriesException.UnknownException(e)
        }

    /**
     * Interprets a GraphQL error and converts it to an appropriate
     * [ConfigurationDataException].
     *
     * @param e [GraphQLResponse.Error] The GraphQL error to interpret.
     * @return The corresponding [ConfigurationDataException].
     */
    fun interpretConfigurationDataError(e: GraphQLResponse.Error): ConfigurationDataException {
        val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
        val error = e.extensions?.get(StringConstants.ERROR_TYPE)?.toString() ?: ""

        if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return ConfigurationDataException.AuthenticationException("$e")
        }
        if (httpStatusCode != null && httpStatusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
            return ConfigurationDataException.FailedException("$e")
        }
        if (error.contains(StringConstants.ERROR_IDENTITY_CONTEXT_MISSING)) {
            return ConfigurationDataException.IdentityContextMissingException(
                StringConstants.IDENTITY_CONTEXT_MISSING_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_SERVICE)) {
            return ConfigurationDataException.ServiceException(
                StringConstants.SERVICE_ERROR_MSG,
            )
        }
        return ConfigurationDataException.FailedException(e.toString())
    }

    /**
     * Interprets a throwable exception and converts it to an appropriate
     * [ConfigurationDataException].
     *
     * @param e [Throwable] The throwable to interpret.
     * @return [Throwable] The corresponding exception, which may be the original exception if
     *  already handled.
     */
    fun interpretConfigurationDataException(e: Throwable): Throwable =
        when (e) {
            is CancellationException,
            is ConfigurationDataException,
            -> e
            else -> ConfigurationDataException.UnknownException(e)
        }

    /**
     * Interprets a GraphQL error and converts it to an appropriate
     * [PhoneNumberSearchException].
     *
     * @param e [GraphQLResponse.Error] The GraphQL error to interpret.
     * @return The corresponding [PhoneNumberSearchException].
     */
    fun interpretPhoneNumberSearchError(e: GraphQLResponse.Error): PhoneNumberSearchException {
        val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
        val error = e.extensions?.get(StringConstants.ERROR_TYPE)?.toString() ?: ""

        if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return PhoneNumberSearchException.AuthenticationException("$e")
        }
        if (httpStatusCode != null && httpStatusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
            return PhoneNumberSearchException.FailedException("$e")
        }
        if (error.contains(StringConstants.ERROR_UNSUPPORTED_COUNTRY)) {
            return PhoneNumberSearchException.UnsupportedCountryException(
                StringConstants.UNSUPPORTED_COUNTRY_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_INVALID_AREA_CODE)) {
            return PhoneNumberSearchException.InvalidAreaCodeException(
                StringConstants.INVALID_AREA_CODE_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_LIMIT_EXCEEDED)) {
            return PhoneNumberSearchException.LimitExceededException(
                StringConstants.LIMIT_EXCEEDED_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_INSUFFICIENT_ENTITLEMENTS)) {
            return PhoneNumberSearchException.InsufficientEntitlementsException(
                StringConstants.INSUFFICIENT_ENTITLEMENTS_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_INVALID_ARGUMENT)) {
            return PhoneNumberSearchException.InvalidArgumentException(
                StringConstants.INVALID_ARGUMENT_ERROR_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_IDENTITY_CONTEXT_MISSING)) {
            return PhoneNumberSearchException.IdentityContextMissingException(
                StringConstants.IDENTITY_CONTEXT_MISSING_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_SERVICE)) {
            return PhoneNumberSearchException.ServiceException(
                StringConstants.SERVICE_ERROR_MSG,
            )
        }
        return PhoneNumberSearchException.FailedException(e.toString())
    }

    /**
     * Interprets a throwable exception and converts it to an appropriate
     * [PhoneNumberSearchException].
     *
     * @param e [Throwable] The throwable to interpret.
     * @return [Throwable] The corresponding exception, which may be the original exception if
     *  already handled.
     */
    fun interpretPhoneNumberSearchException(e: Throwable): Throwable =
        when (e) {
            is CancellationException,
            is PhoneNumberSearchException,
            -> e
            else -> PhoneNumberSearchException.UnknownException(e)
        }

    /**
     * Interprets a GraphQL error and converts it to an appropriate [PhoneNumberException].
     *
     * @param e [GraphQLResponse.Error] The GraphQL error to interpret.
     * @return The corresponding [PhoneNumberException].
     */
    fun interpretPhoneNumberError(e: GraphQLResponse.Error): PhoneNumberException {
        val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
        val error = e.extensions?.get(StringConstants.ERROR_TYPE)?.toString() ?: ""

        if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return PhoneNumberException.AuthenticationException("$e")
        }
        if (httpStatusCode != null && httpStatusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
            return PhoneNumberException.FailedException("$e")
        }
        if (error.contains(StringConstants.ERROR_INVALID_PHONE_NUMBER_STATE)) {
            return PhoneNumberException.InvalidPhoneNumberStateException(
                state = currentStateFrom(e),
                message = StringConstants.INVALID_PHONE_NUMBER_STATE_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_PHONE_NUMBER_SEARCH_EXPIRED)) {
            return PhoneNumberException.PhoneNumberSearchExpiredException(
                StringConstants.PHONE_NUMBER_SEARCH_EXPIRED_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_PHONE_NUMBER_NOT_IN_SEARCH)) {
            return PhoneNumberException.PhoneNumberNotInSearchException(
                StringConstants.PHONE_NUMBER_NOT_IN_SEARCH_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_PHONE_NUMBER_UNAVAILABLE)) {
            return PhoneNumberException.PhoneNumberUnavailableException(
                StringConstants.PHONE_NUMBER_UNAVAILABLE_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_PHONE_NUMBER_NOT_FOUND)) {
            return PhoneNumberException.PhoneNumberNotFoundException(
                StringConstants.PHONE_NUMBER_NOT_FOUND_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_INSUFFICIENT_ENTITLEMENTS)) {
            return PhoneNumberException.InsufficientEntitlementsException(
                StringConstants.INSUFFICIENT_ENTITLEMENTS_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_INVALID_TOKEN)) {
            return PhoneNumberException.InvalidTokenException(
                StringConstants.INVALID_TOKEN_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_INVALID_ARGUMENT)) {
            return PhoneNumberException.InvalidArgumentException(
                StringConstants.INVALID_ARGUMENT_ERROR_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_IDENTITY_CONTEXT_MISSING)) {
            return PhoneNumberException.IdentityContextMissingException(
                StringConstants.IDENTITY_CONTEXT_MISSING_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_SERVICE)) {
            return PhoneNumberException.ServiceException(
                StringConstants.SERVICE_ERROR_MSG,
            )
        }
        return PhoneNumberException.FailedException(e.toString())
    }

    /**
     * Interprets a throwable exception and converts it to an appropriate
     * [PhoneNumberException].
     *
     * @param e [Throwable] The throwable to interpret.
     * @return [Throwable] The corresponding exception, which may be the original exception if
     *  already handled.
     */
    fun interpretPhoneNumberException(e: Throwable): Throwable =
        when (e) {
            is CancellationException,
            is PhoneNumberException,
            -> e
            else -> PhoneNumberException.UnknownException(e)
        }

    /**
     * Interprets a GraphQL error and converts it to an appropriate [PhoneMessageException].
     *
     * @param e [GraphQLResponse.Error] The GraphQL error to interpret.
     * @return The corresponding [PhoneMessageException].
     */
    fun interpretPhoneMessageError(e: GraphQLResponse.Error): PhoneMessageException {
        val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
        val error = e.extensions?.get(StringConstants.ERROR_TYPE)?.toString() ?: ""

        if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return PhoneMessageException.AuthenticationException("$e")
        }
        if (httpStatusCode != null && httpStatusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
            return PhoneMessageException.FailedException("$e")
        }
        if (error.contains(StringConstants.ERROR_PHONE_NUMBER_NOT_FOUND)) {
            return PhoneMessageException.PhoneNumberNotFoundException(
                StringConstants.PHONE_NUMBER_NOT_FOUND_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_LIMIT_EXCEEDED)) {
            return PhoneMessageException.LimitExceededException(
                StringConstants.MESSAGE_BATCH_LIMIT_EXCEEDED_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_INVALID_ARGUMENT)) {
            return PhoneMessageException.InvalidArgumentException(
                StringConstants.INVALID_ARGUMENT_ERROR_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_IDENTITY_CONTEXT_MISSING)) {
            return PhoneMessageException.IdentityContextMissingException(
                StringConstants.IDENTITY_CONTEXT_MISSING_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_SERVICE)) {
            return PhoneMessageException.ServiceException(StringConstants.SERVICE_ERROR_MSG)
        }
        return PhoneMessageException.FailedException(e.toString())
    }

    /**
     * Interprets a throwable exception and converts it to an appropriate
     * [PhoneMessageException].
     *
     * @param e [Throwable] The throwable to interpret.
     * @return [Throwable] The corresponding exception, which may be the original exception if
     *  already handled.
     */
    fun interpretPhoneMessageException(e: Throwable): Throwable =
        when (e) {
            is CancellationException,
            is PhoneMessageException,
            -> e
            else -> PhoneMessageException.UnknownException(e)
        }

    /**
     * Interprets the failure of a phone number subscription as the cause reported with its
     * disconnection.
     *
     * @param e [ApiException] The failure reported by the subscription.
     * @return [PhoneNumberException.AuthenticationException] when the caller is not
     *  authorized, otherwise [PhoneNumberException.FailedException].
     */
    fun interpretPhoneNumberSubscriptionException(e: ApiException): PhoneNumberException =
        when (e) {
            is ApiException.ApiAuthException -> PhoneNumberException.AuthenticationException(e.message, e)
            else -> PhoneNumberException.FailedException(e.message, e)
        }

    /**
     * Interprets the failure of a phone message subscription as the cause reported with its
     * disconnection.
     *
     * @param e [ApiException] The failure reported by the subscription.
     * @return [PhoneMessageException.AuthenticationException] when the caller is not
     *  authorized, otherwise [PhoneMessageException.FailedException].
     */
    fun interpretPhoneMessageSubscriptionException(e: ApiException): PhoneMessageException =
        when (e) {
            is ApiException.ApiAuthException -> PhoneMessageException.AuthenticationException(e.message, e)
            else -> PhoneMessageException.FailedException(e.message, e)
        }

    /**
     * Interprets a throwable raised by a key store operation and converts it to an
     * appropriate [CryptographicKeysException].
     *
     * @param e [Throwable] The throwable to interpret.
     * @return [Throwable] The corresponding exception, which may be the original exception if
     *  already handled.
     */
    fun interpretCryptographicKeysException(e: Throwable): Throwable =
        when (e) {
            is CancellationException,
            is CryptographicKeysException,
            -> e
            is DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException ->
                CryptographicKeysException.SecureKeyArchiveException(e.message, e)
            else -> CryptographicKeysException.UnknownException(e)
        }

    /**
     * The phone number state the service reported alongside an
     * `InvalidPhoneNumberState` error, or `null` when it reported none.
     *
     * The state arrives as a raw string in the error's structured `errorInfo`, so an
     * unrecognised value maps to [PhoneNumberState.Unknown] rather than failing.
     */
    private fun currentStateFrom(e: GraphQLResponse.Error): PhoneNumberState? {
        val errorInfo = e.extensions?.get(StringConstants.ERROR_INFO) as? Map<*, *> ?: return null
        val rawState = errorInfo[StringConstants.ERROR_INFO_STATE]?.toString() ?: return null
        return PhoneNumberStateTransformer.fromEntityToApi(
            PhoneNumberStateTransformer.fromGraphQLToEntity(
                PhoneNumberStateGraphQL.safeValueOf(rawState),
            ),
        )
    }
}
