/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.data.common

/**
 * Constants used across the data layer, primarily for interpreting service errors.
 */
internal object StringConstants {
    /** Key under which the service error type is provided in a GraphQL error's extensions. */
    const val ERROR_TYPE = "errorType"

    /** Errors returned from the service. */
    const val ERROR_INVALID_ARGUMENT = "InvalidArgument"
    const val ERROR_IDENTITY_CONTEXT_MISSING = "IdentityContextMissing"
    const val ERROR_SUPPORTED_COUNTRIES_UNAVAILABLE = "SupportedCountriesUnavailable"
    const val ERROR_UNSUPPORTED_COUNTRY = "UnsupportedCountry"
    const val ERROR_INVALID_AREA_CODE = "InvalidAreaCode"
    const val ERROR_LIMIT_EXCEEDED = "LimitExceededError"
    const val ERROR_INSUFFICIENT_ENTITLEMENTS = "InsufficientEntitlementsError"
    const val ERROR_SERVICE = "ServiceError"
    const val ERROR_PHONE_NUMBER_SEARCH_EXPIRED = "PhoneNumberSearchExpired"
    const val ERROR_PHONE_NUMBER_NOT_IN_SEARCH = "PhoneNumberNotInSearch"
    const val ERROR_PHONE_NUMBER_UNAVAILABLE = "PhoneNumberUnavailable"
    const val ERROR_PHONE_NUMBER_NOT_FOUND = "PhoneNumberNotFound"
    const val ERROR_INVALID_TOKEN = "InvalidTokenError"
    const val ERROR_INVALID_PHONE_NUMBER_STATE = "InvalidPhoneNumberState"

    /** Key under which structured error detail is provided in a GraphQL error's extensions. */
    const val ERROR_INFO = "errorInfo"

    /** Key within [ERROR_INFO] holding a phone number's current state. */
    const val ERROR_INFO_STATE = "state"

    /** Algorithm the service records for the public key that seals a number's messages. */
    const val PUBLIC_KEY_ALGORITHM = "RSAEncryptionOAEPAESGCM"

    /** Exception messages. */
    const val INVALID_ARGUMENT_ERROR_MSG = "Invalid input"
    const val IDENTITY_CONTEXT_MISSING_MSG = "Identity context is missing"
    const val SUPPORTED_COUNTRIES_UNAVAILABLE_MSG = "The set of supported countries is unavailable"
    const val UNSUPPORTED_COUNTRY_MSG = "Phone number search is not supported in the requested country"
    const val INVALID_AREA_CODE_MSG = "The requested area code is not in use in the requested country"
    const val LIMIT_EXCEEDED_MSG = "The requested limit is above the maximum the service accepts"
    const val INSUFFICIENT_ENTITLEMENTS_MSG = "The user is not entitled to use the telephony service"
    const val SERVICE_ERROR_MSG = "The telephony service returned an error"
    const val PHONE_NUMBER_SEARCH_EXPIRED_MSG = "The phone number search has expired or does not belong to the caller"
    const val PHONE_NUMBER_NOT_IN_SEARCH_MSG = "The phone number is not one of the search's results"
    const val PHONE_NUMBER_UNAVAILABLE_MSG = "The phone number is no longer available"
    const val PHONE_NUMBER_NOT_FOUND_MSG = "Phone number not found"
    const val INVALID_TOKEN_MSG = "The ownership proof could not be verified"
    const val INVALID_PHONE_NUMBER_STATE_MSG = "The phone number is in a state that does not allow the operation"
    const val NO_SUPPORTED_COUNTRIES_ERROR_MSG = "No supported countries returned."
    const val NO_CONFIGURATION_DATA_ERROR_MSG = "No configuration data returned."
    const val NO_PHONE_NUMBER_SEARCH_ERROR_MSG = "No phone number search returned."
    const val NO_PHONE_NUMBER_ERROR_MSG = "No phone number returned."
    const val NO_PHONE_NUMBERS_ERROR_MSG = "No phone numbers returned."
    const val NO_PHONE_MESSAGES_ERROR_MSG = "No phone messages returned."
    const val NO_BATCH_RESULT_ERROR_MSG = "No batch result returned."
    const val MESSAGE_BATCH_LIMIT_EXCEEDED_MSG = "A batch may name at most 100 messages"
    const val EMPTY_KEY_ARCHIVE_MSG = "The key archive is empty"
    const val SUBSCRIPTION_UNAUTHENTICATED_MSG =
        "User client does not have subject. Is the user authenticated?"
    const val PHONE_MESSAGE_KEY_NOT_FOUND_MSG =
        "The key that sealed this message is not held on this device"
}
