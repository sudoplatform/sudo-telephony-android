/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudotelephony

import android.content.Context
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.api.ApiClient
import com.sudoplatform.sudotelephony.rules.ActualPropertyResetter
import com.sudoplatform.sudotelephony.rules.PropertyResetRule
import com.sudoplatform.sudotelephony.rules.PropertyResetter
import com.sudoplatform.sudotelephony.rules.TimberLogRule
import com.sudoplatform.sudouser.SudoUserClient
import org.junit.Rule
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Base class that sets up:
 * - [TimberLogRule]
 * - [PropertyResetRule]
 *
 * And provides convenient access to the [PropertyResetRule.before] via [PropertyResetter.before],
 * along with the collaborators every layer's tests need.
 */
abstract class BaseTests : PropertyResetter by ActualPropertyResetter() {
    @Rule
    @JvmField
    val timberLogRule = TimberLogRule()

    private val mockLogDriver by before {
        mock<LogDriverInterface>().stub {
            on { logLevel } doReturn LogLevel.VERBOSE
        }
    }

    /** Shared logger backed by a mock log driver. */
    protected val mockLogger by before {
        Logger("mock", mockLogDriver)
    }

    /** Shared mock Android context. */
    protected val mockContext by before {
        mock<Context>()
    }

    /** Shared mock [SudoUserClient]. */
    protected open val mockUserClient by before {
        mock<SudoUserClient>()
    }

    /** Shared mock GraphQL transport. */
    protected open val mockApiClient by before {
        mock<ApiClient>()
    }

    // Common fixtures used across tests.
    protected val mockCountryCodes = listOf("US", "CA", "GB", "AU")
}
