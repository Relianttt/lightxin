package com.lightxin.core.database

import android.app.Application
import androidx.room.Room
import com.lightxin.core.auth.AccountContext
import com.lightxin.core.auth.AccountSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SnapshotStoreTest {
    private lateinit var db: LightXinDatabase
    private lateinit var store: SnapshotStore
    private val session = AccountSession()
    private val account get() = AccountContext("A", session.capture())

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LightXinDatabase::class.java).build()
        store = SnapshotStore(db, session)
    }

    @After fun cleanup() { db.close(); session.invalidate() }

    @Test fun roundTripAndAccountIsolation() = runBlocking {
        store.write(account, "test", "value")
        val cached = store.read<String>(account, "test", String::class.java)!!
        assertEquals("value", cached.value)
        assertTrue(cached.fetchedAt > 0)
        assertNull(store.read<String>(account.copy(userCode = "B"), "test", String::class.java))
    }

    @Test fun staleGenerationRejectsReadsAndWrites() = runBlocking {
        val old = account
        session.invalidate()
        assertTrue(runCatching { store.read<String>(old, "test", String::class.java) }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { store.write(old, "test", "stale") }.exceptionOrNull() is CancellationException)
    }

    @Test fun corruptJsonIsACacheMiss() = runBlocking {
        db.remoteSnapshotDao().put(RemoteSnapshotEntity("A", "test", "{broken", 1))
        assertNull(store.read<String>(account, "test", String::class.java))
    }
}
