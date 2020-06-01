package com.sudoplatform.sudotelephony

import android.os.Parcelable
import com.sudoplatform.sudotelephony.type.PhoneNumberState
import kotlinx.android.parcel.Parcelize
import java.util.*


/**
 * Object that represents a phone number either provisioned, in the process of getting provisioned or that has been deleted.
 */
@Parcelize
data class PhoneNumber(
    // ID to uniquely identify the phone number
    val id: String,
    // E.164 formatted number. For more detail visit https://en.wikipedia.org/wiki/E.164
    val phoneNumber: String,
    // State of the phone number that represents the state of being provisioned by the service.
    val state: PhoneNumberState,
    // Version of the phone number that represents how much has been updated for conflict resolution.
    val version: Int,
    // Created timestamp
    val created: Date,
    // Updated timestamp
    val updated: Date
) : Parcelable