package com.sudoplatform.sudotelephony

class TelephonyInvalidConfigException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyDecryptSealedDataException(throwable: Throwable? = null) : Exception(throwable)

class TelephonySignInFailedException(throwable: Throwable? = null) : Exception(throwable)

class TelephonySearchException(throwable: Throwable? = null) : Exception(throwable)

class UnsupportedCountryCodeException(throwable: Throwable? = null) : Exception(throwable)

class InvalidCountryCodeException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyNumberProvisionException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyNumberDeletionException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyGetPhoneNumberException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyGetAllPhoneNumbersException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyCreatePublicKeyException(throwable: Throwable? = null) : Exception(throwable)

class TelephonySendMessageException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyGetMessageException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyDeleteMessageException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyGetMessagesException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyFileUploadException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyFileDownloadException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyGetConversationException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyGetConversationsException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyInsufficientEntitlementException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyCallingFailedToAuthorizeOutgoingCallException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyCallingFailedToStartOutgoingCallException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyCallingFailedToAcceptIncomingCallException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyCallingFailedToConnectException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyCallingDisconnectedException(throwable: Throwable? = null) : Exception(throwable)

class TelephonyCallingCallCancelledException(throwable: Throwable? = null) : Exception(throwable)

/**
 * A type representing an exception in Telephony
 */
enum class TelephonyException(val exception: Exception) {
    InsufficientEntitlement(TelephonyInsufficientEntitlementException()),
    Unknown(Exception());

    companion object {
        fun fromInternalError(error: Error): TelephonyException {
            if (error.message == "sudoplatform.telephony.NoPhoneNumberEntitlementError") {
                return InsufficientEntitlement
            }
            else { return Unknown }
        }
    }
}
