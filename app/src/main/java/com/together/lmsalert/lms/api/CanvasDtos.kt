package com.together.lmsalert.lms.api

import com.together.lmsalert.lms.model.LmsAssignment
import com.together.lmsalert.lms.model.LmsCourse
import com.together.lmsalert.lms.model.SubmissionState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeParseException

/* Canvas REST API 응답 형태. 필요한 필드만 선언하고 나머지는 무시(ignoreUnknownKeys). */

@Serializable
data class CanvasUserDto(
    val id: Long,
    val name: String? = null,
)

@Serializable
data class CanvasCourseDto(
    val id: Long,
    val name: String? = null,
    @SerialName("course_code") val courseCode: String? = null,
    /** 기간이 지나 접근이 막힌 과목은 이름 없이 이 값만 true 로 내려온다. */
    @SerialName("access_restricted_by_date") val accessRestrictedByDate: Boolean = false,
)

@Serializable
data class CanvasAssignmentDto(
    val id: Long,
    val name: String? = null,
    val description: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("lock_at") val lockAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val published: Boolean = true,
    /** include[]=submission 으로 요청해야 내려온다. */
    val submission: CanvasSubmissionDto? = null,
)

@Serializable
data class CanvasSubmissionDto(
    /** unsubmitted / submitted / pending_review / graded */
    @SerialName("workflow_state") val workflowState: String? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
)

internal fun CanvasCourseDto.toModelOrNull(): LmsCourse? {
    if (accessRestrictedByDate) return null
    val courseName = name ?: return null
    return LmsCourse(id = id, name = courseName, code = courseCode)
}

internal fun CanvasAssignmentDto.toModel(course: LmsCourse): LmsAssignment = LmsAssignment(
    id = id,
    courseId = course.id,
    courseName = course.name,
    name = name ?: "(제목 없음)",
    description = description,
    dueAt = parseInstant(dueAt),
    lockAt = parseInstant(lockAt),
    htmlUrl = htmlUrl,
    submissionState = submission.toSubmissionState(),
    submittedAt = parseInstant(submission?.submittedAt),
)

internal fun CanvasSubmissionDto?.toSubmissionState(): SubmissionState {
    if (this == null) return SubmissionState.UNKNOWN
    if (submittedAt != null) return SubmissionState.SUBMITTED
    return when (workflowState) {
        "submitted", "pending_review", "graded" -> SubmissionState.SUBMITTED
        "unsubmitted" -> SubmissionState.NOT_SUBMITTED
        else -> SubmissionState.UNKNOWN
    }
}

internal fun parseInstant(value: String?): Instant? = try {
    value?.let(Instant::parse)
} catch (_: DateTimeParseException) {
    null
}
