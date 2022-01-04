package com.sudoplatform.sudotelephony.exceptions

import java.lang.RuntimeException

/**
 * Defines the exceptions thrown by the methods of the [SudoTelephonyClient].
 *
 * @property message Accompanying message for the exception.
 * @property cause The cause for the exception
 */
sealed class SudoTelephonyException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
    class InvalidConfigException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class DecryptSealedDataException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class SignInFailedException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class SearchException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class UnsupportedCountryCodeException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class InvalidCountryCodeException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class NumberProvisionException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class NumberDeletionException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class GetPhoneNumberException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class GetAllPhoneNumbersException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class CreatePublicKeyException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class SendMessageException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class GetMessageException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class DeleteMessageException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class GetMessagesException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class FileUploadException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class FileDownloadException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class GetConversationException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class GetConversationsException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class InsufficientEntitlementException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class CallingFailedToAuthorizeOutgoingCallException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class CallingFailedToStartOutgoingCallException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class CallingFailedToAcceptIncomingCallException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class CallingFailedToConnectException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class CallingDisconnectedException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class CallingCallCancelledException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class CallingFailedToRegisterForIncomingCallsException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class CallingFailedToDeRegisterForIncomingCallsException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class GetCallRecordException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class DeleteCallRecordException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class GetVoicemailException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class DeleteVoicemailException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class ResetTelephonyClientException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
    class SubscribeToMessagesException(message: String? = null, cause: Throwable? = null) :
        SudoTelephonyException(message = message, cause = cause)
}
