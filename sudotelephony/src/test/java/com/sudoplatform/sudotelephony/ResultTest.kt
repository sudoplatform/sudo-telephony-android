package com.sudoplatform.sudotelephony

import com.sudoplatform.sudotelephony.results.Result
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [Result]
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ResultTest {

    @Test
    fun `test success result contains value`() {
        val success: Result<Int> = Result.Success(1)

        success.get() shouldBe 1
    }
}
