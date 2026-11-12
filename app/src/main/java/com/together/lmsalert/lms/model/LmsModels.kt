package com.together.lmsalert.lms.model

import java.time.Instant

/**
 * LMS 에서 가져온 데이터를 앱 내부에서 쓰는 형태.
 * (Canvas JSON 형태는 lms.api.CanvasDtos 에 따로 두고, 여기로 변환해서 사용)
 *
 * 작업 B(데이터 구조/Room)는 이 모델을 Entity 로 매핑해서 저장하면 된다.
 */
data class LmsCourse(
    val id: Long,
    val name: String,
    val code: String?,
)

/** LMS 제출 상태. 조회에 실패했거나 정보가 없으면 UNKNOWN('미확인'). */
enum class SubmissionState { SUBMITTED, NOT_SUBMITTED, UNKNOWN }

data class LmsAssignment(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val name: String,
    /** HTML 원문. 변경 감지는 이 값을 그대로 비교한다. */
    val description: String?,
    val dueAt: Instant?,
    val lockAt: Instant?,
    /** LMS 원본 링크 (과제 상세 화면의 '원본 보기'용). */
    val htmlUrl: String?,
    val submissionState: SubmissionState,
    val submittedAt: Instant?,
)

/**
 * 한 번의 LMS 전체 조회 결과.
 *
 * 일부 과목만 조회에 실패할 수 있으므로 성공/실패 과목을 따로 기록한다.
 * → 실패한 과목의 과제는 '삭제'로 판단하지 않기 위함 (기획서 2.2).
 */
data class LmsAnnouncement(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val title: String,
    val message: String?,
    val postedAt: Instant?,
    val htmlUrl: String?,
)

data class LmsFetchResult(
    val courses: List<LmsCourse>,
    val assignments: List<LmsAssignment>,
    val fetchedCourseIds: Set<Long>,
    val failedCourseIds: Set<Long>,
    val fetchedAt: Instant,
    val announcements: List<LmsAnnouncement> = emptyList(),
    val fetchedAnnouncementCourseIds: Set<Long> = emptySet(),
    val failedAnnouncementCourseIds: Set<Long> = emptySet(),
)
