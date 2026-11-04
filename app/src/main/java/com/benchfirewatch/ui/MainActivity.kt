package com.benchfirewatch.ui

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benchfirewatch.BuildConfig
import com.benchfirewatch.alert.AlarmCover
import com.benchfirewatch.data.EulaStore
import com.benchfirewatch.data.SessionStore
import com.benchfirewatch.data.SettingsStore
import com.benchfirewatch.data.VersionSeen
import com.benchfirewatch.eula.Eula
import com.benchfirewatch.release.ReleaseNotes
import com.benchfirewatch.service.MonitorRuntime

class MainActivity : ComponentActivity() {
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED &&
            EulaStore(this).hasAccepted() &&
            SettingsStore(this).load().startOnBoot
        ) {
            MonitorRuntime.start(this)
        }
    }

    override fun onStop() {
        MonitorRuntime.flushLog(this)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val alreadyAccepted = EulaStore(this).hasAccepted()
        if (alreadyAccepted) {
            askNotifications()
            askCamera()
            startWatchIfNeeded()
        }
        setContent {
            val model: WatchViewModel = viewModel()
            val runtime by model.runtime.collectAsState()
            val prefs by model.prefs.collectAsState()
            val night = (if (runtime.running) runtime.prefs else prefs).nightView
            BenchTheme(night = night) {
                BenchApp(
                    eulaAccepted = alreadyAccepted,
                    onAccepted = {
                        askNotifications()
                        askCamera()
                        startWatchIfNeeded()
                    },
                    onDecline = { finishAffinity() },
                    onScreenFlag = { keepOn ->
                        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    },
                )
            }
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun askCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startWatchIfNeeded() {
        if (!EulaStore(this).hasAccepted()) return
        val session = SessionStore(this)
        if (session.monitoring && !session.cleanStop) {
            MonitorRuntime.recover(this)
        } else if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED && SettingsStore(this).load().startOnBoot) {
            MonitorRuntime.start(this)
        }
    }
}

@Composable
private fun BenchApp(
    eulaAccepted: Boolean,
    onAccepted: () -> Unit,
    onDecline: () -> Unit,
    onScreenFlag: (Boolean) -> Unit,
) {
    val model: WatchViewModel = viewModel()
    val runtime by model.runtime.collectAsState()
    val prefs by model.prefs.collectAsState()
    val diskEvents by model.diskEvents.collectAsState()
    val context = LocalContext.current
    var accepted by rememberSaveable { mutableStateOf(eulaAccepted) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var savedPage by rememberSaveable { mutableIntStateOf(AppPages.WATCH) }
    val pagerState = rememberPagerState(initialPage = savedPage.coerceIn(0, AppPages.COUNT - 1)) { AppPages.COUNT }
    val scope = rememberCoroutineScope()
    val pagerLocks = remember { PagerModalLocks() }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var showTutorial by rememberSaveable { mutableStateOf(false) }
    var editingZones by remember { mutableStateOf(false) }
    var pictureSheet by remember { mutableStateOf(false) }
    var settingsSub by remember { mutableStateOf(false) }
    LaunchedEffect(accepted) {
        if (!accepted) return@LaunchedEffect
        val seen = VersionSeen(context)
        val code = seen.seenCode()
        if (ReleaseNotes.shouldShow(code, BuildConfig.VERSION_CODE, seen.hadPriorData())) {
            showNotes = true
        } else if (code < BuildConfig.VERSION_CODE) {
            seen.markSeen(BuildConfig.VERSION_CODE)
        }
        if (Tutorial.shouldOffer(SettingsStore(context).load().tutorialSeen)) {
            showTutorial = true
        }
    }
    val shownPrefs = if (runtime.running) runtime.prefs else prefs
    val alarmCover = AlarmCover.visible(runtime.status, runtime.silenced)
    DisposableEffect(accepted, runtime.siren, runtime.running, shownPrefs.keepScreenOn, fullScreen, alarmCover) {
        if (accepted) {
            onScreenFlag(fullScreen || alarmCover || runtime.siren || (runtime.running && shownPrefs.keepScreenOn))
        }
        onDispose { }
    }
    if (!accepted) {
        EulaScreen(
            review = false,
            onAccept = {
                EulaStore(context).accept(Eula.VERSION)
                accepted = true
                onAccepted()
            },
            onDecline = onDecline,
            onClose = {},
        )
        return
    }
    if (reviewing) {
        EulaScreen(
            review = true,
            onAccept = {},
            onDecline = {},
            onClose = { reviewing = false },
        )
        return
    }
    val events = if (runtime.running || runtime.events.isNotEmpty()) runtime.events else diskEvents

    if (showTutorial && !alarmCover) {
        TutorialScreen {
            model.save(prefs.copy(tutorialSeen = true))
            showTutorial = false
        }
        return
    }

    if (fullScreen) {
        FullScreenCamera(
            state = runtime,
            prefs = shownPrefs,
            onPrefs = model::save,
            onExit = { fullScreen = false },
        )
        return
    }

    CompositionLocalProvider(LocalPagerModalLocks provides pagerLocks) {
        val selected = PageSwipe.selectedTab(
            pagerState.currentPage,
            pagerState.targetPage,
            pagerState.isScrollInProgress,
        )
        val userScroll = !editingZones && !pictureSheet && !settingsSub && PageSwipe.allowsPaging(
            touch = PagerTouch.OPEN,
            inSystemGesture = false,
            fullScreen = false,
            modalVisible = pagerLocks.count > 0 || alarmCover,
        )
        LaunchedEffect(pagerState.settledPage) {
            savedPage = pagerState.settledPage
        }
        val backTo = PageSwipe.backPage(pagerState.currentPage)
        BackHandler(enabled = backTo != null) {
            val dest = backTo ?: return@BackHandler
            scope.launch { pagerState.animateScrollToPage(dest) }
        }
        Scaffold(
            containerColor = chromeInk(),
            bottomBar = {
                Column {
                    PageDots(page = selected, count = AppPages.COUNT)
                    NavigationBar(containerColor = chromePanel()) {
                        val icons = listOf(
                            Icons.Filled.Home,
                            Icons.AutoMirrored.Filled.List,
                            Icons.Filled.Settings,
                            Icons.Filled.Info,
                        )
                        AppPages.titles.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selected == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                icon = { Icon(icons[index], contentDescription = label) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().yieldSystemBackEdges(),
                    userScrollEnabled = userScroll,
                    beyondViewportPageCount = 1,
                ) { page ->
                    val pageModifier = Modifier.fillMaxSize()
                    when (page) {
                        AppPages.WATCH -> WatchScreen(
                            pageModifier,
                            runtime,
                            shownPrefs,
                            model::save,
                            model::update,
                            onFullScreen = { fullScreen = true },
                            onEditing = { editingZones = it },
                            onSheet = { pictureSheet = it },
                        )
                        AppPages.LOG -> RecordsScreen(pageModifier, events, runtime.logging)
                        AppPages.SETTINGS -> SettingsScreen(
                            pageModifier,
                            shownPrefs,
                            runtime.logging,
                            model::save,
                            onOpenEula = { reviewing = true },
                            onReplayTutorial = { showTutorial = true },
                            onSubScreen = { settingsSub = it },
                        )
                        else -> HelpScreen(pageModifier) { reviewing = true }
                    }
                }
                if (alarmCover) {
                    Box(Modifier.fillMaxSize().absorbTouches()) {
                        AlarmOverlay(
                            runtime.status,
                            runtime.headline,
                            silent = !shownPrefs.sirenEnabled || !runtime.siren,
                            onAck = { MonitorRuntime.acknowledge(model.getApplication()) },
                            onStop = { MonitorRuntime.stop(model.getApplication()) },
                        )
                    }
                }
            }
        }
        if (showNotes && !alarmCover) {
            WhatsNewDialog {
                VersionSeen(context).markSeen(BuildConfig.VERSION_CODE)
                showNotes = false
            }
        }
    }
}
