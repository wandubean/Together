package com.together.lmsalert.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.together.lmsalert.AppContainer
import com.together.lmsalert.lms.auth.LmsLoginScreen
import com.together.lmsalert.lms.model.LmsAssignment
import com.together.lmsalert.lms.model.LmsAnnouncement
import com.together.lmsalert.lms.model.SubmissionState
import com.together.lmsalert.sync.InMemoryAssignmentStore
import com.together.lmsalert.sync.LmsSyncWorker
import com.together.lmsalert.sync.SyncInterval
import com.together.lmsalert.ui.theme.Amber
import com.together.lmsalert.ui.theme.AmberTint
import com.together.lmsalert.ui.theme.Blue
import com.together.lmsalert.ui.theme.BlueTint
import com.together.lmsalert.ui.theme.Canvas
import com.together.lmsalert.ui.theme.Divider
import com.together.lmsalert.ui.theme.Green
import com.together.lmsalert.ui.theme.GreenTint
import com.together.lmsalert.ui.theme.Ink
import com.together.lmsalert.ui.theme.MutedInk
import com.together.lmsalert.ui.theme.Red
import com.together.lmsalert.ui.theme.RedTint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private enum class AppTab { HOME, CALENDAR, SETTINGS }
private data class PersonalEvent(val title: String, val date: LocalDate, val time: String, val reminder: String)

@Composable
fun AppScreen(container: AppContainer) {
    val context = LocalContext.current
    val assignments by ((container.assignmentStore as? InMemoryAssignmentStore)
        ?.assignmentsFlow?.collectAsState() ?: remember { mutableStateOf(emptyMap()) })
    val manualWork by container.syncScheduler.observeManualSync().collectAsState(initial = null)
    val store = container.assignmentStore as? InMemoryAssignmentStore
    val announcements by (store?.announcementsFlow?.collectAsState()
        ?: remember { mutableStateOf(emptyMap<Long, LmsAnnouncement>()) })
    val lastSyncedAt by (store?.lastSyncedAtFlow?.collectAsState()
        ?: remember { mutableStateOf<Instant?>(null) })
    var selectedAnnouncement by remember { mutableStateOf<LmsAnnouncement?>(null) }
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var selectedAssignmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    val events = remember { mutableStateListOf<PersonalEvent>() }

    if (showLogin) {
        LmsLoginScreen(
            verifySession = { container.lmsApi.isSessionValid() },
            onLoginSuccess = {
                showLogin = false
                container.syncScheduler.schedulePeriodic(SyncInterval.DEFAULT)
                container.syncScheduler.syncNow()
            },
            onCancel = { showLogin = false },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val selected = selectedAssignmentId?.let(assignments::get)
    if (selected != null) {
        AssignmentDetail(
            assignment = selected,
            onBack = { selectedAssignmentId = null },
            onOpenLink = { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
        )
        return
    }

    selectedAnnouncement?.let { notice ->
        AlertDialog(
            onDismissRequest = { selectedAnnouncement = null },
            title = { Text(notice.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(notice.courseName, color = MutedInk)
                    Text(stripHtml(notice.message).ifBlank { "등록된 내용이 없습니다." })
                }
            },
            confirmButton = {
                notice.htmlUrl?.let { url ->
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Text("LMS에서 보기")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { selectedAnnouncement = null }) { Text("닫기") } },
        )
    }

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Color.White,
                tonalElevation = 0.dp,
            ) {
                NavItem(AppTab.HOME, tab, Icons.Default.Home, "홈") { tab = it }
                NavItem(AppTab.CALENDAR, tab, Icons.Default.CalendarMonth, "캘린더") { tab = it }
                NavItem(AppTab.SETTINGS, tab, Icons.Default.Settings, "설정") { tab = it }
            }
        },
    ) { padding ->
        when (tab) {
            AppTab.HOME -> HomeScreen(
                assignments = assignments.values.toList(),
                announcements = announcements.values.toList(),
                lastSyncedAt = lastSyncedAt,
                onAnnouncementClick = { selectedAnnouncement = it },
                manualWork = manualWork,
                onRefresh = { container.syncScheduler.syncNow() },
                onAssignmentClick = { selectedAssignmentId = it.id },
                onLogin = { showLogin = true },
                modifier = Modifier.padding(padding),
            )
            AppTab.CALENDAR -> CalendarScreen(
                assignments = assignments.values.toList(),
                events = events,
                onAddEvent = { events += it },
                modifier = Modifier.padding(padding),
            )
            AppTab.SETTINGS -> SettingsScreen(
                container = container,
                onLogin = { showLogin = true },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun RowScope.NavItem(tab: AppTab, selected: AppTab, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onSelect: (AppTab) -> Unit) {
    NavigationBarItem(
        selected = tab == selected,
        onClick = { onSelect(tab) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

@Composable
private fun HomeScreen(
    assignments: List<LmsAssignment>,
    announcements: List<LmsAnnouncement>,
    lastSyncedAt: Instant?,
    onAnnouncementClick: (LmsAnnouncement) -> Unit,
    manualWork: WorkInfo?,
    onRefresh: () -> Unit,
    onAssignmentClick: (LmsAssignment) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = assignments.sortedBy { it.dueAt ?: Instant.MAX }
    val now = Instant.now()
    val urgentCount = sorted.count { it.isDueSoon(now) }
    val lastSynced = lastSyncedAt?.let(::formatTime)
    val syncing = manualWork?.state == WorkInfo.State.RUNNING || manualWork?.state == WorkInfo.State.ENQUEUED

    RefreshableHome(isRefreshing = syncing, onRefresh = onRefresh, modifier = modifier) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("안녕하세요", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            Text("오늘의 과제", style = MaterialTheme.typography.headlineSmall, color = Ink)
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Blue),
            ) {
                Box(
                    Modifier.background(
                        Brush.linearGradient(listOf(Blue, Color(0xFF5C83E8), Color(0xFF8DA8F1)))
                    ).padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("마감이 다가오는 과제", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
                                Text("${urgentCount}개", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                            }
                            IconButton(
                                onClick = onRefresh,
                                enabled = !syncing,
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)),
                            ) {
                                if (syncing) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                                else Icon(Icons.Default.Refresh, "동기화", tint = Color.White)
                            }
                        }
                        Divider(color = Color.White.copy(alpha = .2f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sync, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (syncing) "LMS에서 최신 정보를 가져오는 중…" else "마지막 동기화 ${lastSynced ?: "아직 없음"}",
                                color = Color.White.copy(alpha = .85f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
        if (manualWork?.state == WorkInfo.State.FAILED) {
            item {
                Column {
                    SyncMessage(manualWork)
                    if (LmsSyncWorker.outcomeOf(manualWork.outputData) == com.together.lmsalert.sync.SyncOutcome.AUTH_REQUIRED) {
                        TextButton(onClick = onLogin) { Text("LMS 로그인") }
                    }
                }
            }
        }
        if (manualWork?.state == WorkInfo.State.SUCCEEDED &&
            manualWork.outputData.getInt(LmsSyncWorker.KEY_FAILED_COURSES, 0) > 0) {
            item { Text("일부 과목을 갱신하지 못했어요. 기존 정보를 유지했어요. 아래로 당겨 다시 시도해 주세요.", color = Amber) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("다가오는 과제", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${sorted.size}개", color = MutedInk, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (sorted.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.CheckCircle,
                    title = "등록된 과제가 없어요",
                    body = "LMS에 로그인하면 과제가 여기에 표시됩니다.",
                    action = "LMS 로그인",
                    onAction = onLogin,
                )
            }
        } else {
            items(sorted, key = { it.id }) { assignment ->
                AssignmentCard(assignment, onClick = { onAssignmentClick(assignment) })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("과목 공지", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${announcements.size}개", color = MutedInk)
            }
        }
        if (announcements.isEmpty()) {
            item { Text("등록된 공지가 없어요. 아래로 당겨 최신 정보를 가져오세요.", color = MutedInk) }
        }
        items(announcements.sortedByDescending { it.postedAt ?: Instant.MIN }, key = { "notice_${it.id}" }) { notice ->
            Card(Modifier.fillMaxWidth().clickable { onAnnouncementClick(notice) },
                shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(notice.courseName, color = MutedInk, style = MaterialTheme.typography.labelSmall)
                    Text(notice.title, style = MaterialTheme.typography.titleMedium)
                    Text(stripHtml(notice.message), color = MutedInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    notice.postedAt?.let { Text(formatTime(it), color = MutedInk, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
    }
}

@Composable
private fun AssignmentCard(assignment: LmsAssignment, onClick: () -> Unit) {
    val now = Instant.now()
    val urgent = assignment.isDueSoon(now)
    val overdue = assignment.dueAt?.isBefore(now) == true
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(assignment.courseName, color = MutedInk, style = MaterialTheme.typography.labelSmall)
                    Text(assignment.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                StatusPill(
                    text = when {
                        assignment.submissionState == SubmissionState.SUBMITTED -> "제출됨"
                        overdue -> "마감 지남"
                        urgent -> "임박"
                        else -> "예정"
                    },
                    tint = if (urgent) AmberTint else BlueTint,
                    content = if (urgent) Amber else Blue,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = if (urgent) Amber else MutedInk, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(assignment.dueAt?.let(::formatDeadline) ?: "마감일 없음", color = if (urgent) Amber else MutedInk, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                SubmissionLabel(assignment.submissionState)
                Icon(Icons.Default.ArrowForward, null, tint = MutedInk, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentDetail(assignment: LmsAssignment, onBack: () -> Unit, onOpenLink: (String) -> Unit) {
    BackHandler(onBack = onBack)
    var markedDone by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = { Text("과제 상세") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(assignment.courseName, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
                Text(assignment.name, style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        DetailRow(Icons.Default.Schedule, "마감", assignment.dueAt?.let(::formatDeadline) ?: "마감일 없음")
                        Divider(color = Divider)
                        DetailRow(Icons.Default.CheckCircle, "제출 상태", submissionDescription(assignment.submissionState))
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("설명", style = MaterialTheme.typography.titleMedium)
                        Text(stripHtml(assignment.description).ifBlank { "등록된 설명이 없습니다." }, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (assignment.htmlUrl != null) {
                item {
                    OutlinedButton(onClick = { onOpenLink(assignment.htmlUrl) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text("LMS에서 원본 보기")
                    }
                }
            }
            item {
                Button(onClick = { markedDone = !markedDone }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (markedDone) "완료 표시 해제" else "완료로 표시")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Blue, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = MutedInk, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarScreen(
    assignments: List<LmsAssignment>,
    events: MutableList<PersonalEvent>,
    onAddEvent: (PersonalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showEventSheet by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<PersonalEvent?>(null) }
    val dayAssignments = assignments.filter { it.dueAt?.atZone(ZoneId.systemDefault())?.toLocalDate() == selectedDate }
    val dayEvents = events.filter { it.date == selectedDate }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("캘린더", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                CalendarCard(month, selectedDate, onMonthChange = { month = it }, onDateSelected = { selectedDate = it })
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("${dayAssignments.size + dayEvents.size}개", color = MutedInk)
                }
            }
            items(dayAssignments, key = { "a${it.id}" }) { assignment ->
                CalendarItem(assignment.name, assignment.courseName, Icons.Default.Event, Blue, "LMS 과제")
            }
            items(dayEvents) { event ->
                CalendarItem(event.title, event.time, Icons.Default.Event, Green, "개인 일정") { selectedEvent = event }
            }
            if (dayAssignments.isEmpty() && dayEvents.isEmpty()) {
                item { Text("이 날짜에는 일정이 없습니다.", color = MutedInk, modifier = Modifier.padding(vertical = 24.dp)) }
            }
        }
        FloatingActionButton(
            onClick = { showEventSheet = true },
            containerColor = Blue,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Default.Add, "일정 추가") }
    }
    if (showEventSheet) {
        EventSheet(selectedDate, onDismiss = { showEventSheet = false }, onSave = {
            onAddEvent(it)
            showEventSheet = false
        })
    }
    selectedEvent?.let { event ->
        var editing by remember(event) { mutableStateOf(false) }
        var editTitle by remember(event) { mutableStateOf(event.title) }
        var editTime by remember(event) { mutableStateOf(event.time) }
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            title = { Text(event.title) },
            text = {
                if (editing) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(editTitle, { editTitle = it }, label = { Text("일정 제목") }, singleLine = true)
                        OutlinedTextField(editTime, { editTime = it }, label = { Text("시간") }, singleLine = true)
                    }
                } else {
                    Text("${event.date} · ${event.time}\n알림: ${event.reminder}")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        events.remove(event)
                        selectedEvent = null
                    }) { Text("삭제", color = Red) }
                    TextButton(onClick = {
                        if (editing) {
                            val index = events.indexOf(event)
                            if (index >= 0) events[index] = event.copy(title = editTitle, time = editTime)
                            selectedEvent = null
                        } else {
                            editing = true
                        }
                    }) { Text(if (editing) "저장" else "수정") }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedEvent = null }) { Text("닫기") }
            },
        )
    }
}

@Composable
private fun CalendarCard(month: YearMonth, selectedDate: LocalDate, onMonthChange: (YearMonth) -> Unit, onDateSelected: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value % 7
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Icon(Icons.Default.ChevronLeft, "이전 달") }
                Text(month.format(DateTimeFormatter.ofPattern("yyyy년 M월")), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Icon(Icons.Default.ChevronRight, "다음 달") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach { Text(it, color = MutedInk, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            val days = (0 until offset).map { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                val selected = date == selectedDate
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(if (selected) Blue else Color.Transparent)
                                        .clickable { onDateSelected(date) },
                                    contentAlignment = Alignment.Center,
                                ) { Text("${date.dayOfMonth}", color = if (selected) Color.White else Ink) }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventSheet(date: LocalDate, onDismiss: () -> Unit, onSave: (PersonalEvent) -> Unit) {
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("09:00") }
    var reminder by remember { mutableStateOf("10분 전") }
    var recurring by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("개인 일정 추가", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "닫기") }
            }
            OutlinedTextField(title, { title = it }, label = { Text("일정 제목") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(date.toString(), {}, label = { Text("날짜") }, modifier = Modifier.weight(1f), readOnly = true)
                OutlinedTextField(time, { time = it }, label = { Text("시간") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(reminder, { reminder = it }, label = { Text("알림") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("반복 일정", modifier = Modifier.weight(1f))
                Switch(checked = recurring, onCheckedChange = { recurring = it })
            }
            if (recurring) {
                Text("반복 설정", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(date.toString(), {}, label = { Text("시작일") }, modifier = Modifier.weight(1f), readOnly = true)
                    OutlinedTextField(date.plusDays(7).toString(), {}, label = { Text("종료일") }, modifier = Modifier.weight(1f), readOnly = true)
                }
                OutlinedTextField("7", {}, label = { Text("반복 간격 (일)") }, modifier = Modifier.fillMaxWidth(), readOnly = true)
            }
            Button(onClick = { if (title.isNotBlank()) onSave(PersonalEvent(title, date, time, reminder)) }, modifier = Modifier.fillMaxWidth()) { Text("일정 저장") }
        }
    }
}

@Composable
private fun CalendarItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, type: String, onClick: () -> Unit = {}) {
    Card(Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
            }
            Text(type, color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingsScreen(container: AppContainer, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    var interval by rememberSaveable { mutableStateOf(SyncInterval.DEFAULT) }
    var reminders by rememberSaveable { mutableStateOf("1일 전") }
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("설정", style = MaterialTheme.typography.headlineSmall) }
        item {
            SettingsGroup("LMS 계정") {
                SettingsRow(Icons.Default.Link, "LMS 계정", "과제를 가져오려면 로그인이 필요해요", onClick = onLogin)
            }
        }
        item {
            SettingsGroup("동기화") {
                SettingsRow(Icons.Default.Sync, "자동 동기화", interval.label) {
                    scope.launch { interval = nextInterval(interval); container.syncScheduler.schedulePeriodic(interval) }
                }
            }
        }
        item {
            SettingsGroup("알림") {
                SettingsRow(Icons.Default.Schedule, "마감 알림", reminders) {
                    reminders = if (reminders == "1일 전") "1일 전 · 1시간 전" else "1일 전"
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BlueTint)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = Blue, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("LMSalert", style = MaterialTheme.typography.titleMedium)
                        Text("학업 일정을 한 곳에서 관리하세요.", color = MutedInk, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = MutedInk, style = MaterialTheme.typography.labelLarge)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { content() }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Blue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MutedInk)
    }
}

@Composable
private fun SyncMessage(work: WorkInfo) {
    val outcome = LmsSyncWorker.outcomeOf(work.outputData)
    val message = when (outcome) {
        com.together.lmsalert.sync.SyncOutcome.AUTH_REQUIRED -> SyncMessageData(Icons.Default.WarningAmber, "로그인이 필요해요", "LMS 세션이 만료되었어요.", Amber, AmberTint)
        else -> SyncMessageData(Icons.Default.ErrorOutline, "동기화에 실패했어요", "연결 상태를 확인하고 다시 시도해 주세요.", Red, RedTint)
    }
    Card(colors = CardDefaults.cardColors(containerColor = message.background), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(message.icon, null, tint = message.color)
            Spacer(Modifier.width(10.dp))
            Column { Text(message.title, color = message.color, style = MaterialTheme.typography.titleMedium); Text(message.body, color = MutedInk, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private data class SyncMessageData(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val body: String,
    val color: Color,
    val background: Color,
)

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(28.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Green, modifier = Modifier.size(34.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun StatusPill(text: String, tint: Color, content: Color) {
    Surface(color = tint, shape = RoundedCornerShape(8.dp)) { Text(text, color = content, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) }
}

@Composable
private fun SubmissionLabel(state: SubmissionState) {
    val (text, color) = when (state) {
        SubmissionState.SUBMITTED -> "제출됨" to Green
        SubmissionState.NOT_SUBMITTED -> "미제출" to Amber
        SubmissionState.UNKNOWN -> "미확인" to MutedInk
    }
    Text(text, color = color, style = MaterialTheme.typography.labelSmall)
}

private fun submissionDescription(state: SubmissionState) = when (state) {
    SubmissionState.SUBMITTED -> "제출됨"
    SubmissionState.NOT_SUBMITTED -> "미제출"
    SubmissionState.UNKNOWN -> "미확인 · LMS에서 상태를 가져오지 못했어요"
}

private fun nextInterval(current: SyncInterval) =
    SyncInterval.entries[(SyncInterval.entries.indexOf(current) + 1) % SyncInterval.entries.size]

private fun formatDeadline(value: Instant): String =
    value.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.KOREAN))

private fun formatTime(value: Instant): String =
    value.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN))

private fun stripHtml(value: String?): String =
    value?.replace(Regex("<[^>]*>"), " ")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
