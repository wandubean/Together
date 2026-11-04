package com.together.lmsalert.dev

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.together.lmsalert.AppContainer
import com.together.lmsalert.lms.auth.LmsLoginScreen
import com.together.lmsalert.sync.InMemoryAssignmentStore
import com.together.lmsalert.sync.LmsSyncWorker
import com.together.lmsalert.sync.SyncInterval
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * 작업 A 기능 확인용 임시 화면 (로그인 → 수동 동기화 → 결과 확인).
 * TODO(작업 B): 실제 홈/캘린더/설정 화면이 생기면 MainActivity 에서 교체하고 이 파일은 삭제.
 */
@Composable
fun LmsDevScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var loginStatus by remember { mutableStateOf("확인 중…") }
    var interval by rememberSaveable { mutableStateOf(SyncInterval.DEFAULT) }

    val manualWork by container.syncScheduler.observeManualSync().collectAsState(initial = null)
    val periodicWork by container.syncScheduler.observePeriodicSync().collectAsState(initial = null)
    val assignments by ((container.assignmentStore as? InMemoryAssignmentStore)
        ?.assignmentsFlow?.collectAsState() ?: remember { mutableStateOf(emptyMap()) })

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(showLogin) {
        if (showLogin) return@LaunchedEffect
        loginStatus = try {
            if (container.sessionManager.isLoggedIn()) "로그인됨" else "로그인 안 됨"
        } catch (_: IOException) {
            "확인 불가 (네트워크 오류)"
        }
    }

    if (showLogin) {
        LmsLoginScreen(
            verifySession = { container.lmsApi.isSessionValid() },
            onLoginSuccess = {
                showLogin = false
                container.syncScheduler.schedulePeriodic(interval)
                container.syncScheduler.syncNow()
            },
            onCancel = { showLogin = false },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("작업 A 테스트 화면", style = MaterialTheme.typography.titleLarge)
        Text("LMS: $loginStatus")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showLogin = true }) { Text("LMS 로그인") }
            OutlinedButton(onClick = {
                scope.launch {
                    container.sessionManager.logout()
                    container.syncScheduler.cancelPeriodic()
                    loginStatus = "로그인 안 됨"
                }
            }) { Text("로그아웃") }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !container.notifier.canPostNotifications()
        ) {
            OutlinedButton(onClick = {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("알림 권한 허용") }
        }

        HorizontalDivider()
        Button(onClick = { container.syncScheduler.syncNow() }) { Text("지금 동기화") }
        Text("수동 동기화: ${describe(manualWork)}")

        Text("자동 조회 주기: ${interval.label} / 상태: ${periodicWork?.state ?: "예약 안 됨"}")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SyncInterval.entries.forEach { option ->
                val select = {
                    interval = option
                    container.syncScheduler.schedulePeriodic(option)
                }
                if (option == interval) {
                    Button(onClick = select) { Text(option.label) }
                } else {
                    OutlinedButton(onClick = select) { Text(option.label) }
                }
            }
        }

        HorizontalDivider()
        Text("저장된 과제 ${assignments.size}개", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(assignments.values.sortedBy { it.dueAt }, key = { it.id }) { a ->
                Column {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${a.courseName} · 마감 ${a.dueAt ?: "없음"} · ${a.submissionState}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun describe(info: WorkInfo?): String {
    if (info == null) return "실행 기록 없음"
    val outcome = LmsSyncWorker.outcomeOf(info.outputData)
    return when (info.state) {
        WorkInfo.State.SUCCEEDED -> {
            val d = info.outputData
            "완료 — 과제 ${d.getInt(LmsSyncWorker.KEY_TOTAL, 0)}개, " +
                "신규 ${d.getInt(LmsSyncWorker.KEY_NEW, 0)}, " +
                "마감변경 ${d.getInt(LmsSyncWorker.KEY_DUE_CHANGED, 0)}, " +
                "삭제 ${d.getInt(LmsSyncWorker.KEY_REMOVED, 0)}, " +
                "실패 과목 ${d.getInt(LmsSyncWorker.KEY_FAILED_COURSES, 0)}"
        }
        WorkInfo.State.FAILED -> "실패 ($outcome)"
        else -> info.state.name
    }
}
