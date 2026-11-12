package com.together.lmsalert.sync

import com.together.lmsalert.lms.model.LmsAnnouncement
import com.together.lmsalert.lms.model.LmsFetchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AnnouncementSyncTest {
    @Test
    fun `실패한 과목의 공지는 보존하고 성공한 과목만 갱신 및 삭제한다`() = runBlocking {
        val store = InMemoryAssignmentStore()
        fun notice(id: Long, course: Long) = LmsAnnouncement(id, course, "과목", "공지", "본문", null, null)
        suspend fun save(notices: List<LmsAnnouncement>, fetched: Set<Long>, failed: Set<Long> = emptySet()) {
            val result = LmsFetchResult(emptyList(), emptyList(), emptySet(), emptySet(), Instant.now(),
                notices, fetched, failed)
            store.saveSyncResult(result, AssignmentChangeDetector.detect(emptyList(), result, false))
        }
        save(listOf(notice(1, 1), notice(2, 2)), setOf(1, 2))
        save(listOf(notice(3, 1)), setOf(1), setOf(2))
        assertEquals(setOf(2L, 3L), store.announcementsFlow.value.keys)
        save(emptyList(), setOf(1, 2))
        assertEquals(emptySet<Long>(), store.announcementsFlow.value.keys)
    }
}
