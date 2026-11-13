/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony.internal.domain.useCases.phoneNumber

import com.sudoplatform.sudotelephony.SudoTelephonyClient.PhoneNumberException

/**
 * Whether this failure proves the service, or the client before sending, refused the
 * request, so local state created for it, such as a provisioning key pair, can be discarded.
 *
 * Decided on the [PhoneNumberException] the GraphQL service maps a failure to. That mapping
 * works from the service's typed `errorType` and the HTTP status rather than from message
 * wording, so a typed rejection is proof the service did not act. Anything that could follow a
 * request the service accepted does not qualify: a 5xx or unclassified error
 * ([PhoneNumberException.FailedException]), an internal service error
 * ([PhoneNumberException.ServiceException]), a response with no phone number
 * ([PhoneNumberException.ProvisionFailedException]), a transport or unexpected failure
 * ([PhoneNumberException.UnknownException]), and any other throwable, including
 * cancellation. After those the number may exist, and its key is the only way to read its
 * messages. [PhoneNumberException.KeyPairRemovalFailedException] follows a release the service
 * carried out, so it does not qualify either.
 *
 * The `when` over [PhoneNumberException] is exhaustive so a new subtype must be classified.
 */
internal val Throwable.isDefinitiveRejection: Boolean
    get() =
        when (this) {
            is PhoneNumberException ->
                when (this) {
                    is PhoneNumberException.AuthenticationException,
                    is PhoneNumberException.InvalidArgumentException,
                    is PhoneNumberException.IdentityContextMissingException,
                    is PhoneNumberException.PhoneNumberSearchExpiredException,
                    is PhoneNumberException.PhoneNumberNotInSearchException,
                    is PhoneNumberException.PhoneNumberUnavailableException,
                    is PhoneNumberException.PhoneNumberNotFoundException,
                    is PhoneNumberException.InvalidPhoneNumberStateException,
                    is PhoneNumberException.InvalidTokenException,
                    is PhoneNumberException.InsufficientEntitlementsException,
                    is PhoneNumberException.SingletonKeyPairNotFoundException,
                    -> true
                    is PhoneNumberException.FailedException,
                    is PhoneNumberException.ProvisionFailedException,
                    is PhoneNumberException.ServiceException,
                    is PhoneNumberException.UnknownException,
                    is PhoneNumberException.KeyPairRemovalFailedException,
                    -> false
                }
            else -> false
        }
