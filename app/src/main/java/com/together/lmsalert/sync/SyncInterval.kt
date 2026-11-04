package com.together.lmsalert.sync

/** 자동 조회 주기 (기획서 2.1). WorkManager 주기 작업의 최소 간격은 15분이다. */
enum class SyncInterval(val minutes: Long, val label: String) {
    MIN_15(15, "15분"),
    MIN_30(30, "30분"),
    HOUR_1(60, "1시간"),
    HOUR_2(120, "2시간"),
    HOUR_4(240, "4시간"),
    HOUR_8(480, "8시간"),
    HOUR_12(720, "12시간"),
    HOUR_24(1440, "24시간");

    companion object {
        val DEFAULT = HOUR_1

        /** DataStore 에 name 으로 저장했다가 다시 읽을 때 사용. */
        fun fromName(name: String?): SyncInterval = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
