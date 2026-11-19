package com.example.mpod.testutil

import com.example.mpod.data.local.MpodDatabase
import com.example.mpod.data.local.dao.AudiobookDao
import kotlinx.coroutines.flow.emptyFlow
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatcher
import org.mockito.Mockito
import org.mockito.stubbing.OngoingStubbing

inline fun <reified T> mock(): T = Mockito.mock(T::class.java)

fun mockAudiobookDao(): AudiobookDao {
    val dao: AudiobookDao = mock()
    whenever(dao.getPlaylistAudiobookTracksFlow()).thenReturn(emptyFlow())
    return dao
}

fun mockMpodDatabase(): MpodDatabase {
    val db: MpodDatabase = mock()
    whenever(db.transactionExecutor).thenReturn(java.util.concurrent.Executor { it.run() })
    whenever(db.suspendingTransactionId).thenReturn(ThreadLocal())
    return db
}

fun <T> whenever(methodCall: T): OngoingStubbing<T> = Mockito.`when`(methodCall)

fun <T> any(): T {
    Mockito.any<T>()
    return uninitialized()
}

fun <T> any(type: Class<T>): T {
    Mockito.any(type)
    return uninitialized()
}

fun <T> argThat(matcher: T.() -> Boolean): T {
    Mockito.argThat(ArgumentMatcher<T> { it.matcher() })
    return uninitialized()
}

inline fun <reified T : Any> argumentCaptor(): ArgumentCaptor<T> =
    ArgumentCaptor.forClass(T::class.java)

val <T> ArgumentCaptor<T>.firstValue: T
    get() = if (this.allValues.isNotEmpty()) this.allValues.first() else this.value

val <T> ArgumentCaptor<T>.lastValue: T
    get() = if (this.allValues.isNotEmpty()) this.allValues.last() else this.value

fun <T> verify(mock: T): T = Mockito.verify(mock)

fun <T> verify(mock: T, mode: org.mockito.verification.VerificationMode): T = Mockito.verify(mock, mode)

fun times(wantedNumberOfInvocations: Int): org.mockito.verification.VerificationMode =
    Mockito.times(wantedNumberOfInvocations)

fun never(): org.mockito.verification.VerificationMode =
    Mockito.never()

@Suppress("UNCHECKED_CAST")
fun <T> uninitialized(): T = null as T
