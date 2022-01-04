package com.sudoplatform.sudotelephony.callrecords

import androidx.annotation.Keep
import com.sudoplatform.sudotelephony.graphql.type.CallState

/**
 * Wrapper for [CallState]
 *
 *   Possible states for a call.
 *
 *  AUTHORIZED  - Outbound call authorized but not yet initiated at telephony vendor.
 *
 *  QUEUED      - Call is queued before ringing.
 *
 *  RINGING     - Call is ringing.
 *
 *  ANSWERED    - Call has been answered and is ongoing.
 *
 *  COMPLETED   - Call ended after being answered.
 *
 *  UNANSWERED  - Call failed or was not answered.
 *
 */
@Keep
enum class CallRecordState {
    AUTHORIZED,
    QUEUED,
    RINGING,
    ANSWERED,
    COMPLETED,
    UNANSWERED,
    UNKNOWN;

    companion object {
        internal fun fromInternalState(internalState: CallState): CallRecordState {
            return when (internalState) {
                CallState.AUTHORIZED -> AUTHORIZED
                CallState.QUEUED -> QUEUED
                CallState.RINGING -> RINGING
                CallState.ANSWERED -> ANSWERED
                CallState.COMPLETED -> COMPLETED
                CallState.UNANSWERED -> UNANSWERED
            }
        }
    }
}
