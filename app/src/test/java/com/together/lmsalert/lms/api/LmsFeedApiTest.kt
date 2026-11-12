package com.together.lmsalert.lms.api

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class LmsFeedApiTest {
    @Test
    fun `공지 페이지를 순회하고 과제와 공지를 함께 가져온다`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val url = chain.request().url
            val body = when {
                url.encodedPath.endsWith("/courses") -> """[{"id":1,"name":"과목"}]"""
                url.encodedPath.endsWith("/assignments") -> """[{"id":10,"name":"과제","submission":{"workflow_state":"unsubmitted"}}]"""
                url.queryParameter("page") == "2" -> """[{"id":21,"title":"다음 공지"},{"id":22,"published":false}]"""
                else -> {
                    assertEquals("true", url.queryParameter("only_announcements"))
                    """[{"id":20,"title":"공지","message":"<p>본문</p>","posted_at":"2026-11-12T00:00:00Z"}]"""
                }
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "application/json")
                .apply {
                    if (url.encodedPath.endsWith("/discussion_topics") && url.queryParameter("page") == null) {
                        header("Link", "<https://lms.test/api/v1/courses/1/discussion_topics?page=2>; rel=\"next\"")
                    }
                }
                .body("while(1);$body".toResponseBody("application/json".toMediaType())).build()
        }).build()
        val result = LmsApiClient(http, baseUrl = "https://lms.test/".toHttpUrl()).fetchAll()
        assertEquals(listOf(10L), result.assignments.map { it.id })
        assertEquals(listOf(20L, 21L), result.announcements.map { it.id })
        assertEquals("<p>본문</p>", result.announcements.first().message)
        assertEquals(setOf(1L), result.fetchedCourseIds)
        assertEquals(setOf(1L), result.fetchedAnnouncementCourseIds)
    }

    @Test
    fun `공지 조회 실패도 과제 조회 성공 결과를 유지한다`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            val code = if (path.endsWith("discussion_topics")) 403 else 200
            val body = if (path.endsWith("/courses")) """[{"id":1,"name":"과목"}]""" else "[]"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("response")
                .header("Content-Type", "application/json")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val result = LmsApiClient(http, baseUrl = "https://lms.test/".toHttpUrl()).fetchAll()
        assertEquals(setOf(1L), result.fetchedCourseIds)
        assertEquals(emptySet<Long>(), result.failedCourseIds)
        assertEquals(setOf(1L), result.failedAnnouncementCourseIds)
        assertEquals(emptySet<Long>(), result.fetchedAnnouncementCourseIds)
    }
}
