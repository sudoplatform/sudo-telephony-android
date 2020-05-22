package com.sudoplatform.sudotelephony

import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudotelephony.type.PhoneNumberSearchState
import java.util.*
import kotlin.concurrent.schedule


internal class PhoneSearchCallback<T>(
    val graphQLClient: AWSAppSyncClient,
    val extractSearchId: (T) -> String?,
    val callback: (Result<PhoneNumberSearchResult>) -> Unit
) : GraphQLCall.Callback<T>() {

    override fun onResponse(response: Response<T>) {
        if (response.errors().size > 0) {
            val error = response.errors()[0]
            val errorType = error.customAttributes()["errorType"]
            val exception = when (errorType) {
                "Telephony:CountryNotSupported" -> UnsupportedCountryCodeException()
                "Telephony:InvalidCountryCode" -> InvalidCountryCodeException()
                else -> TelephonySearchException()
            }

            return callback.runOnUiThread()(Result.Error(exception))
        }

        val searchId = response.data()?.let { extractSearchId(it) }
        if (searchId == null) {
            callback.runOnUiThread()(Result.Error(TelephonySearchException()))
            return
        }

        pollForPhoneSearchResult(searchId, callback)
    }

    override fun onFailure(e: ApolloException) {
        val error = TelephonySearchException(e)
        DefaultSudoTelephonyClient.timber.e(error, "Failed searching for phone numbers")
        callback.runOnUiThread()(Result.Error(error))
    }

    private fun pollForPhoneSearchResult(
        searchId: String,
        callback: (Result<PhoneNumberSearchResult>) -> Unit
    ) {
        val timer = Timer()
        timer.schedule(Date(), 1000) {
            val query = AvailablePhoneNumberResultQuery(searchId)
            graphQLClient.query(query)
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(PollCallback(graphQLClient, this, callback))
        }
    }
}

private class PollCallback(
    val graphQLClient: AWSAppSyncClient,
    val timerTask: TimerTask,
    val callback: (Result<PhoneNumberSearchResult>) -> Unit
) : GraphQLCall.Callback<AvailablePhoneNumberResultQuery.Data>() {

    override fun onResponse(response: Response<AvailablePhoneNumberResultQuery.Data>) {
        val result = response.data()?.getPhoneNumberSearch ?: return
        val state =
            result.fragments().availablePhoneNumberResult().state()
        when (state) {
            PhoneNumberSearchState.SEARCHING -> return
            PhoneNumberSearchState.FAILED -> {
                timerTask.cancel()
                val error = TelephonySearchException()
                DefaultSudoTelephonyClient.timber.e(
                    error,
                    "Failed searching for phone numbers"
                )
                callback.runOnUiThread()(Result.Error(error))
                return
            }
        }

        val phoneNumberSearchResult =
            PhoneNumberSearchResult(result.fragments().availablePhoneNumberResult())
        timerTask.cancel()
        callback.runOnUiThread()(
            Result.Success(
                phoneNumberSearchResult
            )
        )
    }

    override fun onFailure(e: ApolloException) {
        val error = TelephonySearchException(e)
        DefaultSudoTelephonyClient.timber.e(
            error,
            "Failed searching for phone numbers"
        )
        timerTask.cancel()
        callback.runOnUiThread()(Result.Error(error))
    }
}
