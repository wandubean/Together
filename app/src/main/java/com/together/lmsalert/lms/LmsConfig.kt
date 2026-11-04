package com.together.lmsalert.lms

/**
 * 한양대 LMS(Canvas 기반) 접속 정보.
 *
 * learning.hanyang.ac.kr 은 Instructure Canvas 로 운영되며 `/api/v1/...` REST API 를 제공한다.
 * 로그인은 WebView 에서 학교 SSO 로 진행하고, 이때 생긴 세션 쿠키로 API 를 호출한다.
 */
object LmsConfig {
    const val HOST = "learning.hanyang.ac.kr"
    const val BASE_URL = "https://$HOST/"

    /** WebView 로그인 시작 주소. 미로그인 상태면 학교 SSO 로 리다이렉트된다. */
    const val LOGIN_URL = "${BASE_URL}login"

    /** 페이지네이션 무한루프 방지용 상한. */
    const val MAX_PAGES = 20
    const val PER_PAGE = 100
}
