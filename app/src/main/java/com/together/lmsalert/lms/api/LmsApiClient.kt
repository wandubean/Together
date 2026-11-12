package com.together.lmsalert.lms.api

import com.together.lmsalert.lms.LmsConfig
import com.together.lmsalert.lms.model.LmsAssignment
import com.together.lmsalert.lms.model.LmsAnnouncement
import com.together.lmsalert.lms.model.LmsCourse
import com.together.lmsalert.lms.model.LmsFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant

/**
 * Canvas REST API 호출 담당.
 *
 * 인증은 OkHttpClient 에 붙은 CookieJar(WebView 쿠키 공유)가 처리하므로 여기서는 신경 쓰지 않는다.
 * 모든 함수는 suspend 이고 내부에서 IO 디스패처로 전환한다.
 *
 * 예외:
 *  - [LmsAuthException] : 세션 없음/만료 → 재로그인 필요
 *  - [IOException]      : 네트워크 오류, 서버 오류([LmsHttpException]) → 재시도 대상
 */
class LmsApiClient(
    private val http: OkHttpClient,
    private val json: Json = LmsJson,
    private val baseUrl: HttpUrl = LmsConfig.BASE_URL.toHttpUrl(),
) {

    suspend fun fetchCurrentUser(): CanvasUserDto = try {
        json.decodeFromString(get(apiUrl("users/self")).body)
    } catch (e: LmsHttpException) {
        // Canvas 는 비로그인 상태에서 users/self 에 404 를 준다.
        if (e.code == 404) throw LmsAuthException("로그인 세션 없음") else throw e
    }

    /** 세션이 살아 있는지 확인. 네트워크 오류는 그대로 던진다(로그인 여부를 판단할 수 없으므로). */
    suspend fun isSessionValid(): Boolean = try {
        fetchCurrentUser()
        true
    } catch (_: LmsAuthException) {
        false
    }

    suspend fun fetchActiveCourses(): List<LmsCourse> =
        getPaged<CanvasCourseDto>(
            apiUrl("courses") { addQueryParameter("enrollment_state", "active") }
        ).mapNotNull { it.toModelOrNull() }

    suspend fun fetchAssignments(course: LmsCourse): List<LmsAssignment> =
        getPaged<CanvasAssignmentDto>(
            apiUrl("courses/${course.id}/assignments") {
                addQueryParameter("include[]", "submission")
                addQueryParameter("order_by", "due_at")
            }
        ).filter { it.published }.map { it.toModel(course) }

    suspend fun fetchAnnouncements(course: LmsCourse): List<LmsAnnouncement> =
        getPaged<CanvasAnnouncementDto>(
            apiUrl("courses/${course.id}/discussion_topics") {
                addQueryParameter("only_announcements", "true")
                addQueryParameter("order_by", "recent_activity")
            }
        ).filter { it.published }.map { it.toModel(course) }

    /**
     * 수강 중인 모든 과목의 과제를 가져온다.
     * 과목 목록 조회 실패는 전체 실패로 보고 예외를 던지고,
     * 개별 과목 실패는 [LmsFetchResult.failedCourseIds] 에 기록하고 계속 진행한다.
     */
    suspend fun fetchAll(): LmsFetchResult {
        val courses = fetchActiveCourses()
        val assignments = mutableListOf<LmsAssignment>()
        val fetched = mutableSetOf<Long>()
        val failed = mutableSetOf<Long>()
        val announcements = mutableListOf<LmsAnnouncement>()
        val fetchedAnnouncements = mutableSetOf<Long>()
        val failedAnnouncements = mutableSetOf<Long>()

        for (course in courses) {
            try {
                assignments += fetchAssignments(course)
                fetched += course.id
            } catch (e: LmsAuthException) {
                throw e
            } catch (_: IOException) {
                failed += course.id
            } catch (_: SerializationException) {
                failed += course.id
            }
            try {
                announcements += fetchAnnouncements(course)
                fetchedAnnouncements += course.id
            } catch (e: LmsAuthException) {
                throw e
            } catch (_: IOException) {
                failedAnnouncements += course.id
            } catch (_: SerializationException) {
                failedAnnouncements += course.id
            }
        }

        return LmsFetchResult(
            courses = courses,
            assignments = assignments,
            fetchedCourseIds = fetched,
            failedCourseIds = failed,
            fetchedAt = Instant.now(),
            announcements = announcements,
            fetchedAnnouncementCourseIds = fetchedAnnouncements,
            failedAnnouncementCourseIds = failedAnnouncements,
        )
    }

    // ---------------------------------------------------------------- 내부 구현

    private inline fun apiUrl(path: String, block: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        baseUrl.newBuilder()
            .addPathSegments("api/v1/$path")
            .addQueryParameter("per_page", LmsConfig.PER_PAGE.toString())
            .apply(block)
            .build()

    private suspend inline fun <reified T> getPaged(firstPage: HttpUrl): List<T> {
        val items = mutableListOf<T>()
        var next: HttpUrl? = firstPage
        var pages = 0
        while (next != null && pages < LmsConfig.MAX_PAGES) {
            val page = get(next)
            items += json.decodeFromString<List<T>>(page.body)
            next = parseNextLink(page.linkHeader)?.also {
                if (it.host != baseUrl.host || it.scheme != baseUrl.scheme || it.port != baseUrl.port) {
                    throw IOException("잘못된 다음 페이지 주소")
                }
            }
            pages++
        }
        if (next != null) throw IOException("페이지 상한 초과: 조회 결과가 불완전합니다")
        return items
    }

    private class Page(val body: String, val linkHeader: String?)

    private suspend fun get(url: HttpUrl): Page = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            when {
                response.code == 401 || (response.code == 403 && url.encodedPath.endsWith("/users/self")) ->
                    throw LmsAuthException("인증 필요 (HTTP ${response.code})")
                !response.isSuccessful ->
                    throw LmsHttpException(response.code, "LMS 오류 (HTTP ${response.code})")
            }
            // 세션이 만료되면 JSON 대신 로그인 HTML 페이지로 리다이렉트되는 경우가 있다.
            val contentType = response.header("Content-Type").orEmpty()
            if (!contentType.contains("json")) {
                if (response.request.url.encodedPath.contains("login")) {
                    throw LmsAuthException("로그인 페이지로 리다이렉트됨")
                }
                throw LmsHttpException(response.code, "JSON 이 아닌 응답: $contentType")
            }
            Page(stripJsonPrefix(response.body.string()), response.header("Link"))
        }
    }

    companion object {
        val LmsJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        /** Canvas 는 쿠키 인증 요청에 JSON 하이재킹 방지용 `while(1);` 접두어를 붙인다. */
        internal fun stripJsonPrefix(body: String): String = body.removePrefix("while(1);")

        /** `Link: <url>; rel="current", <url>; rel="next", ...` 에서 next 주소를 꺼낸다. */
        internal fun parseNextLink(header: String?): HttpUrl? =
            header?.split(',')
                ?.map { it.trim() }
                ?.firstOrNull { it.contains("rel=\"next\"") }
                ?.substringAfter('<')
                ?.substringBefore('>')
                ?.toHttpUrlOrNull()
    }
}
