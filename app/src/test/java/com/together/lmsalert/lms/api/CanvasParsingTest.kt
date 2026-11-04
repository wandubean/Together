package com.together.lmsalert.lms.api

import com.together.lmsalert.lms.model.LmsCourse
import com.together.lmsalert.lms.model.SubmissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class CanvasParsingTest {

    private val json = LmsApiClient.LmsJson

    @Test
    fun `while(1) 접두어를 제거한다`() {
        assertEquals("[]", LmsApiClient.stripJsonPrefix("while(1);[]"))
        assertEquals("[]", LmsApiClient.stripJsonPrefix("[]"))
    }

    @Test
    fun `Link 헤더에서 next 페이지 주소를 꺼낸다`() {
        val header = "<https://learning.hanyang.ac.kr/api/v1/courses?page=1>; rel=\"current\", " +
            "<https://learning.hanyang.ac.kr/api/v1/courses?page=2>; rel=\"next\", " +
            "<https://learning.hanyang.ac.kr/api/v1/courses?page=5>; rel=\"last\""
        assertEquals("2", LmsApiClient.parseNextLink(header)?.queryParameter("page"))
        assertNull(LmsApiClient.parseNextLink("<https://x/?page=1>; rel=\"current\""))
        assertNull(LmsApiClient.parseNextLink(null))
    }

    @Test
    fun `과제 JSON 을 모델로 변환한다`() {
        val body = """
            [{"id": 10, "name": "보고서", "description": "<p>내용</p>",
              "due_at": "2026-11-10T14:59:00Z", "html_url": "https://learning.hanyang.ac.kr/courses/1/assignments/10",
              "published": true, "unknown_field": 1,
              "submission": {"workflow_state": "submitted", "submitted_at": "2026-11-09T01:00:00Z"}},
             {"id": 11, "name": "퀴즈", "due_at": null, "submission": {"workflow_state": "unsubmitted"}},
             {"id": 12, "name": "제출정보없음"}]
        """.trimIndent()
        val course = LmsCourse(1, "프로그래밍방법론", "PM")
        val models = json.decodeFromString<List<CanvasAssignmentDto>>(body).map { it.toModel(course) }

        assertEquals(Instant.parse("2026-11-10T14:59:00Z"), models[0].dueAt)
        assertEquals(SubmissionState.SUBMITTED, models[0].submissionState)
        assertEquals("프로그래밍방법론", models[0].courseName)
        assertNull(models[1].dueAt)
        assertEquals(SubmissionState.NOT_SUBMITTED, models[1].submissionState)
        assertEquals(SubmissionState.UNKNOWN, models[2].submissionState)
    }

    @Test
    fun `접근 제한된 과목은 제외한다`() {
        val body = """[{"id": 1, "name": "수강중"}, {"id": 2, "access_restricted_by_date": true}]"""
        val courses = json.decodeFromString<List<CanvasCourseDto>>(body).mapNotNull { it.toModelOrNull() }
        assertEquals(listOf(1L), courses.map { it.id })
    }
}
