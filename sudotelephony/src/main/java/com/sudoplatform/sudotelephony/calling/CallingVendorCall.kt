package com.sudoplatform.sudotelephony.calling

/**
 * Manages the vendor context for a call.
 */
interface CallingVendorCall {
    // Static variables for a vendor call
    companion object {
        /**
         * A Boolean value that indicates whether this vendor's calls can send DTMF (dual tone multifrequency) tones via hard pause digits or in-call keypad entries.
         * If false, `playDTMF` will never be called.
         */
        var supportsDTMF: Boolean = false

        /**
         * A Boolean value that indicates whether this vendor's calls can be placed on hold or removed from hold.
         * If false, `isOnHold` will never be accessed.
         */
        var supportsHolding: Boolean = false

        /**
         * A Boolean value that indicates whether this vendor's calls can be grouped with other calls.
         */
        var supportsGrouping: Boolean = false

        /**
         * A Boolean value that indicates whether this vendor's calls can be ungrouped from other calls.
         */
        var supportsUngrouping: Boolean = false
    }

    /**
     * Disconnects this call.
     */
    fun disconnect()

    /**
     * Indicates if this call's outgoing audio should be muted.
     */
    var isMuted: Boolean

    /**
     * Indicates if this call should be placed on hold.
     */
    var isOnHold: Boolean

    /**
     * Plays a string of DTMF tones over this call.
     */
    fun playDTMF(digits: String)
}
