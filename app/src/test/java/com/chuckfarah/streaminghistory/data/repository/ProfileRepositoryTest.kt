package com.chuckfarah.streaminghistory.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ProfileRepositoryTest {

    private lateinit var repository: ProfileRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Use a unique preferences name to avoid test leakage
        repository = ProfileRepository(context)
        repository.clearActiveProfile()
    }

    @Test
    fun `active profile is null by default`() = runTest {
        assertThat(repository.activeProfileFlow.first()).isNull()
    }

    @Test
    fun `setActiveProfile persists and emits the value`() = runTest {
        repository.setActiveProfile("Alex")
        assertThat(repository.activeProfileFlow.first()).isEqualTo("Alex")
        assertThat(repository.activeProfile).isEqualTo("Alex")
    }

    @Test
    fun `clearActiveProfile removes and emits null`() = runTest {
        repository.setActiveProfile("Jordan")
        repository.clearActiveProfile()
        assertThat(repository.activeProfileFlow.first()).isNull()
        assertThat(repository.activeProfile).isNull()
    }
}
