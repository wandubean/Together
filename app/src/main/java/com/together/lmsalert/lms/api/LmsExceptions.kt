package com.together.lmsalert.lms.api

import java.io.IOException

/** 로그인 세션이 없거나 만료됨 → 사용자에게 재로그인을 요청해야 한다. 재시도해도 해결되지 않는다. */
class LmsAuthException(message: String) : Exception(message)

/** 서버가 오류 코드로 응답함 (5xx 등). 네트워크 오류와 같이 '잠시 후 재시도' 대상이다. */
class LmsHttpException(val code: Int, message: String) : IOException(message)
