package com.example.sample1

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sample1.ui.theme.Sample1Theme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val darkModeState by settingsViewModel.darkMode.collectAsState()
            val isDark = when(darkModeState) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            Sample1Theme(darkTheme = isDark) {
                MainNavigation(settingsViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val playlistViewModel: PlaylistViewModel = viewModel()
    val mediaViewModel: MediaViewModel = viewModel()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // MediaController state (Shared across screens)
    var controller by remember { mutableStateOf<Player?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentSongTitle by remember { mutableStateOf("") }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var shuffleModeEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }

    DisposableEffect(context) {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                currentSongTitle = mediaMetadata.title?.toString() ?: ""
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
            }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                shuffleModeEnabled = enabled
            }
            override fun onRepeatModeChanged(mode: Int) {
                repeatMode = mode
            }
        }

        controllerFuture.addListener({
            try {
                val mediaController = controllerFuture.get()
                controller = mediaController
                isPlaying = mediaController.isPlaying
                currentSongTitle = mediaController.mediaMetadata.title?.toString() ?: ""
                duration = mediaController.duration.coerceAtLeast(0L)
                shuffleModeEnabled = mediaController.shuffleModeEnabled
                repeatMode = mediaController.repeatMode
                mediaController.addListener(listener)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to connect to MediaController", e)
            }
        }, MoreExecutors.directExecutor())

        onDispose {
            controller?.removeListener(listener)
            MediaController.releaseFuture(controllerFuture)
        }
    }

    // Position polling
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
            delay(1000)
        }
    }

    // Auto-exit player screen if music stops
    LaunchedEffect(currentSongTitle, controller) {
        if (currentRoute == "player" && (controller == null || currentSongTitle.isEmpty())) {
            navController.popBackStack("main", inclusive = false)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("PlayTime Menu", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                NavigationDrawerItem(label = { Text("Home") }, selected = currentRoute == "main", onClick = { navController.navigate("main"); scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Home, null) })
                NavigationDrawerItem(label = { Text("Calendar") }, selected = currentRoute == "calendar", onClick = { navController.navigate("calendar"); scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.CalendarMonth, null) })
                NavigationDrawerItem(label = { Text("Settings") }, selected = currentRoute == "settings", onClick = { navController.navigate("settings"); scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Settings, null) })
            }
        }
    ) {
        Scaffold(
            topBar = {
                val route = currentRoute
                if (route != null && route != "player" && route != "calendar" && !route.startsWith("edit")) {
                    Column {
                        CenterAlignedTopAppBar(
                            title = { Text("PlayTime", fontWeight = FontWeight.ExtraBold) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, "Menu")
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            },
            bottomBar = {
                if (currentRoute != "player" && controller != null && (isPlaying || currentSongTitle.isNotEmpty())) {
                    PlayerControlBar(
                        title = currentSongTitle,
                        isPlaying = isPlaying,
                        position = position,
                        duration = duration,
                        shuffleModeEnabled = shuffleModeEnabled,
                        repeatMode = repeatMode,
                        onPlayPause = {
                            if (isPlaying) controller?.pause() else controller?.play()
                        },
                        onStop = {
                            val intent = Intent(context, MusicService::class.java).apply { action = "STOP" }
                            context.startService(intent)
                        },
                        onNext = { controller?.seekToNext() },
                        onPrevious = { controller?.seekToPrevious() },
                        onSeek = { controller?.seekTo(it) },
                        onShuffleToggle = { controller?.shuffleModeEnabled = !shuffleModeEnabled },
                        onRepeatToggle = {
                            val nextMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            controller?.repeatMode = nextMode
                        },
                        onMaximize = { navController.navigate("player") }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "main",
                modifier = Modifier.padding(padding)
            ) {
                composable("main") {
                    MainScreen(
                        playlistViewModel = playlistViewModel,
                        mediaViewModel = mediaViewModel,
                        settingsViewModel = settingsViewModel,
                        onAddPlaylist = { navController.navigate("edit/-1") },
                        onEditPlaylist = { id -> navController.navigate("edit/$id") },
                        onPlayTracks = { trackList, index ->
                            val intent = Intent(context, MusicService::class.java).apply {
                                putExtra("songUris", trackList.map { it.uri }.toTypedArray())
                                putExtra("songTitles", trackList.map { it.title }.toTypedArray())
                                putExtra("playlistName", "Library")
                                putExtra("startIndex", index)
                                
                                if (trackList.isNotEmpty()) {
                                    val clip = ClipData.newRawUri("Songs", Uri.parse(trackList[0].uri))
                                    for (i in 1 until trackList.size) {
                                        clip.addItem(ClipData.Item(Uri.parse(trackList[i].uri)))
                                    }
                                    clipData = clip
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            }
                            context.startService(intent)
                        },
                        onScheduleTrack = { track ->
                            playlistViewModel.pendingTrack = Song(
                                uri = track.uri,
                                title = track.title,
                                playlistId = 0
                            )
                            navController.navigate("edit/-1")
                        },
                        onQueueTrack = { track ->
                            val item = MediaItem.Builder()
                                .setUri(Uri.parse(track.uri))
                                .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
                                .build()
                            controller?.addMediaItem(item)
                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                composable(
                    route = "edit/{playlistId}",
                    arguments = listOf(navArgument("playlistId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: -1
                    PlaylistEditScreen(
                        viewModel = playlistViewModel,
                        playlistId = playlistId,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("calendar") {
                    CalendarScreen(
                        viewModel = playlistViewModel,
                        onBack = { navController.popBackStack() },
                        onEditPlaylist = { id -> navController.navigate("edit/$id") }
                    )
                }
                composable("player") {
                    PlayerScreen(
                        title = currentSongTitle,
                        isPlaying = isPlaying,
                        position = position,
                        duration = duration,
                        shuffleModeEnabled = shuffleModeEnabled,
                        repeatMode = repeatMode,
                        onPlayPause = { if (isPlaying) controller?.pause() else controller?.play() },
                        onNext = { controller?.seekToNext() },
                        onPrevious = { controller?.seekToPrevious() },
                        onSeek = { controller?.seekTo(it) },
                        onShuffleToggle = { controller?.shuffleModeEnabled = !shuffleModeEnabled },
                        onRepeatToggle = {
                            val nextMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            controller?.repeatMode = nextMode
                        },
                        onStop = {
                            val intent = Intent(context, MusicService::class.java).apply { action = "STOP" }
                            context.startService(intent)
                            navController.popBackStack()
                        },
                        onSchedule = {
                            controller?.currentMediaItem?.let { item ->
                                playlistViewModel.pendingTrack = Song(
                                    uri = item.mediaId,
                                    title = item.mediaMetadata.title?.toString() ?: "Unknown",
                                    playlistId = 0
                                )
                                navController.navigate("edit/-1")
                            }
                        },
                        onAddToPlaylist = {
                            controller?.currentMediaItem?.let { item ->
                                val track = AudioTrack(
                                    id = 0,
                                    title = item.mediaMetadata.title?.toString() ?: "Unknown",
                                    artist = item.mediaMetadata.artist?.toString() ?: "Unknown",
                                    album = item.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                                    uri = item.mediaId,
                                    albumId = 0
                                )
                                mediaViewModel.selectedTrackForPlaylist.value = track
                            }
                        },
                        onMinimize = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    playlistViewModel: PlaylistViewModel,
    mediaViewModel: MediaViewModel,
    settingsViewModel: SettingsViewModel,
    onAddPlaylist: () -> Unit,
    onEditPlaylist: (Int) -> Unit,
    onPlayTracks: (List<AudioTrack>, Int) -> Unit,
    onScheduleTrack: (AudioTrack) -> Unit,
    onQueueTrack: (AudioTrack) -> Unit
) {
    val enabledTabs by settingsViewModel.enabledTabs.collectAsState()
    val tabs = listOf("Tracks", "Playlists", "Albums").filter { enabledTabs.contains(it) }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val searchQuery by mediaViewModel.searchQuery.collectAsState()

    val requiredPermissions = remember {
        mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    var hasPermissions by remember {
        mutableStateOf(
            requiredPermissions.all { 
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
            }
        )
    }

    // Permission request launcher
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = requiredPermissions.all { results[it] == true }
        if (hasPermissions) {
            mediaViewModel.loadMedia()
        }
    }

    // We no longer auto-launch permissions on start to allow the user to trigger it
    // but we still load media if permissions are already there.
    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            mediaViewModel.loadMedia()
        }
    }

    Scaffold(
        topBar = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { mediaViewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search songs or artists...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { mediaViewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (pagerState.currentPage == 1) {
                ExtendedFloatingActionButton(
                    onClick = onAddPlaylist,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Playlist")
                }
            }
        }
    ) { padding ->
        if (!hasPermissions) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Permissions Required", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "PlayTime needs access to your music files to display your library.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        launcher.launch(requiredPermissions.toTypedArray())
                    }) {
                        Text("Grant Permissions")
                    }
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(padding)
            ) { page ->
                val tabTitle = tabs[page]
                when (tabTitle) {
                    "Tracks" -> TracksScreen(
                        mediaViewModel = mediaViewModel,
                        playlistViewModel = playlistViewModel,
                        onPlayTracks = onPlayTracks,
                        onScheduleTrack = onScheduleTrack,
                        onQueueTrack = onQueueTrack
                    )
                    "Playlists" -> PlaylistListScreenContent(playlistViewModel, onEditPlaylist)
                    "Albums" -> AlbumsScreen(mediaViewModel, onPlayTracks)
                }
            }
        }
    }
}

@Composable
fun TracksScreen(
    mediaViewModel: MediaViewModel,
    playlistViewModel: PlaylistViewModel,
    onPlayTracks: (List<AudioTrack>, Int) -> Unit,
    onScheduleTrack: (AudioTrack) -> Unit,
    onQueueTrack: (AudioTrack) -> Unit
) {
    val tracks by mediaViewModel.filteredTracks.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()
    
    var selectedTrackForDetails by remember { mutableStateOf<AudioTrack?>(null) }
    val selectedTrackForPlaylist by mediaViewModel.selectedTrackForPlaylist.collectAsState()

    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tracks found", color = MaterialTheme.colorScheme.outline)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(tracks) { index, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPlayTracks(tracks, index) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.secondary)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play Now") },
                                onClick = { expanded = false; onPlayTracks(tracks, index) },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Queue") },
                                onClick = { expanded = false; onQueueTrack(track) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Schedule") },
                                onClick = { expanded = false; onScheduleTrack(track) },
                                leadingIcon = { Icon(Icons.Default.Schedule, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                onClick = { expanded = false; mediaViewModel.selectedTrackForPlaylist.value = track },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Details") },
                                onClick = { expanded = false; selectedTrackForDetails = track },
                                leadingIcon = { Icon(Icons.Default.Info, null) }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedTrackForDetails?.let { track ->
        AlertDialog(
            onDismissRequest = { selectedTrackForDetails = null },
            title = { Text("Track Details") },
            text = {
                Column {
                    Text("Title: ${track.title}", fontWeight = FontWeight.Bold)
                    Text("Artist: ${track.artist}")
                    Text("Album: ${track.album}")
                    Spacer(Modifier.height(8.dp))
                    Text("URI: ${track.uri}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTrackForDetails = null }) { Text("Close") }
            }
        )
    }

    selectedTrackForPlaylist?.let { track ->
        AlertDialog(
            onDismissRequest = { mediaViewModel.selectedTrackForPlaylist.value = null },
            title = { Text("Add to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists available. Create one first.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(playlists) { item ->
                            ListItem(
                                headlineContent = { Text(item.playlist.name) },
                                supportingContent = { Text("${item.songs.size} songs") },
                                modifier = Modifier.clickable {
                                    playlistViewModel.addTrackToPlaylist(item, track)
                                    mediaViewModel.selectedTrackForPlaylist.value = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { mediaViewModel.selectedTrackForPlaylist.value = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AlbumsScreen(viewModel: MediaViewModel, onPlayTracks: (List<AudioTrack>, Int) -> Unit) {
    val albums by viewModel.albums.collectAsState()
    var selectedAlbum by remember { mutableStateOf<AudioAlbum?>(null) }

    if (selectedAlbum != null) {
        val albumTracks = viewModel.getTracksByAlbum(selectedAlbum!!.id)
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedAlbum = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
                Text(selectedAlbum!!.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(albumTracks) { index, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPlayTracks(albumTracks, index) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(track.title, style = MaterialTheme.typography.bodyLarge)
                            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    } else {
        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No albums found", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(albums) { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedAlbum = album }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Album, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(album.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${album.artist} • ${album.trackCount} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistListScreenContent(
    viewModel: PlaylistViewModel,
    onEditPlaylist: (Int) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val context = LocalContext.current

    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MusicOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(playlists) { item ->
                AlarmCard(
                    playlistWithSongs = item,
                    onToggle = { viewModel.togglePlaylist(item) },
                    onDelete = { 
                        viewModel.deletePlaylist(item)
                        val intent = Intent(context, MusicService::class.java).apply { action = "STOP" }
                        context.startService(intent)
                    },
                    onEdit = { onEditPlaylist(item.playlist.id) },
                    onPlaySong = { index ->
                        try {
                            val intent = Intent(context, MusicService::class.java).apply {
                                val uris = item.songs.map { it.uri }.toTypedArray()
                                putExtra("songUris", uris)
                                putExtra("songTitles", item.songs.map { it.title }.toTypedArray())
                                putExtra("playlistName", item.playlist.name)
                                putExtra("startIndex", index)
                                
                                if (uris.isNotEmpty()) {
                                    val clip = ClipData.newRawUri("Songs", Uri.parse(uris[0]))
                                    for (i in 1 until uris.size) {
                                        clip.addItem(ClipData.Item(Uri.parse(uris[i])))
                                    }
                                    clipData = clip
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            }
                            context.startService(intent)
                        } catch (e: SecurityException) {
                            Log.e("MainActivity", "Permission lost for some songs", e)
                            Toast.makeText(context, "Permission lost for some songs. Please edit the playlist and re-add them.", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to start playback", e)
                            Toast.makeText(context, "Failed to play music.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditScreen(
    viewModel: PlaylistViewModel,
    playlistId: Int,
    onBack: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val existing = playlists.find { it.playlist.id == playlistId }
    
    var name by remember { mutableStateOf(existing?.playlist?.name ?: "") }
    var selectedSongs by remember { 
        mutableStateOf(
            existing?.songs ?: (viewModel.pendingTrack?.let { listOf(it) } ?: emptyList())
        ) 
    }
    
    // Clear pending track once consumed
    LaunchedEffect(Unit) {
        if (playlistId == -1) {
            viewModel.pendingTrack = null
        }
    }
    var selectedCalendar by remember { 
        mutableStateOf(Calendar.getInstance().apply { 
            if (existing != null) timeInMillis = existing.playlist.alarmTime 
        }) 
    }
    var scheduledDate by remember { mutableStateOf(existing?.playlist?.scheduledDate ?: 0L) }
    var autoDelete by remember { mutableStateOf(existing?.playlist?.isAutoDelete ?: false) }
    val context = LocalContext.current

    var repeatMode by remember { mutableStateOf(existing?.playlist?.repeatMode ?: 0) }
    var selectedDays by remember { 
        mutableStateOf(existing?.playlist?.daysOfWeek?.split(",")?.filter { it.isNotEmpty() }?.map { it.toInt() }?.toSet() ?: emptySet()) 
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val newSongs = uris.map { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { 
                Log.e("PlaylistEdit", "Failed to take persistable permission", e)
            }
            val title = uri.lastPathSegment ?: "Unknown Song"
            Song(uri = uri.toString(), title = title, playlistId = if (playlistId == -1) 0 else playlistId)
        }
        selectedSongs = selectedSongs + newSongs
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (playlistId == -1) "Create Playlist" else "Edit Playlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) { 
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") 
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank() && selectedSongs.isNotEmpty()) {
                                val daysStr = selectedDays.joinToString(",")
                                if (playlistId == -1) {
                                    viewModel.addPlaylist(name, selectedSongs, selectedCalendar.timeInMillis, repeatMode, daysStr, scheduledDate, autoDelete)
                                } else {
                                    viewModel.updatePlaylist(existing!!.copy(
                                        playlist = existing.playlist.copy(
                                            name = name, 
                                            alarmTime = selectedCalendar.timeInMillis, 
                                            scheduledDate = scheduledDate,
                                            isEnabled = true,
                                            isAutoDelete = autoDelete,
                                            repeatMode = repeatMode,
                                            daysOfWeek = daysStr
                                        ),
                                        songs = selectedSongs
                                    ))
                                }
                                onBack()
                            }
                        },
                        enabled = name.isNotBlank() && selectedSongs.isNotEmpty()
                    ) {
                        Text("SAVE", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Alarm Schedule", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0)
                                    // Initial scheduling will be handled by AlarmScheduler
                                }
                                selectedCalendar = cal
                            }, selectedCalendar.get(Calendar.HOUR_OF_DAY), selectedCalendar.get(Calendar.MINUTE), false).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        Text("Set Time: ${timeFormat.format(selectedCalendar.time)}")
                    }
                    
                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val now = Calendar.getInstance()
                            DatePickerDialog(context, { _, y, m, d ->
                                val cal = Calendar.getInstance().apply { set(y, m, d) }
                                scheduledDate = cal.timeInMillis
                            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        Text(if (scheduledDate > 0) "Date: ${sdf.format(Date(scheduledDate))}" else "Specific Date (Optional)")
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text("Repeat", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Once", "Daily", "Days").forEachIndexed { index, label ->
                            FilterChip(
                                selected = repeatMode == index,
                                onClick = { repeatMode = index },
                                label = { Text(label) }
                            )
                        }
                    }
                    
                    if (repeatMode == 2) {
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val days = listOf("S", "M", "T", "W", "T", "F", "S")
                            days.forEachIndexed { index, day ->
                                val dayNum = index + 1
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedDays.contains(dayNum)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            selectedDays = if (selectedDays.contains(dayNum)) selectedDays - dayNum else selectedDays + dayNum
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(day, color = if (selectedDays.contains(dayNum)) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { autoDelete = !autoDelete },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Auto-Delete", style = MaterialTheme.typography.bodyLarge)
                            Text("Delete playlist after it triggers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = autoDelete, 
                            onCheckedChange = { autoDelete = it },
                            enabled = repeatMode == 0 // Only allow auto-delete for "Once" mode
                        )
                    }
                    if (repeatMode != 0 && autoDelete) {
                        // Reset auto-delete if user switches to a repeat mode
                        LaunchedEffect(repeatMode) { autoDelete = false }
                    }
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Songs (${selectedSongs.size})", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { launcher.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (selectedSongs.isEmpty()) {
                    Text("No songs added", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                } else {
                    selectedSongs.forEachIndexed { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    try {
                                        val intent = Intent(context, MusicService::class.java).apply {
                                            val uris = selectedSongs.map { it.uri }.toTypedArray()
                                            putExtra("songUris", uris)
                                            putExtra("songTitles", selectedSongs.map { it.title }.toTypedArray())
                                            putExtra("playlistName", name)
                                            putExtra("startIndex", index)
                                            
                                            if (uris.isNotEmpty()) {
                                                val clip = ClipData.newRawUri("Songs", Uri.parse(uris[0]))
                                                for (i in 1 until uris.size) {
                                                    clip.addItem(ClipData.Item(Uri.parse(uris[i])))
                                                }
                                                clipData = clip
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                        }
                                        context.startService(intent)
                                    } catch (e: SecurityException) {
                                        Toast.makeText(context, "No permission to play this file.", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error playing file.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text(song.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { selectedSongs = selectedSongs - song }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.RemoveCircleOutline, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmCard(
    playlistWithSongs: PlaylistWithSongs,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onPlaySong: (Int) -> Unit
) {
    val playlist = playlistWithSongs.playlist
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val timeString = timeFormat.format(Date(playlist.alarmTime))

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (playlist.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).clickable { onEdit() }) {
                    Text(playlist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val repeatText = when(playlist.repeatMode) {
                        1 -> "Daily"
                        2 -> "Custom Days"
                        else -> "Once"
                    }
                    val autoDeleteText = if (playlist.isAutoDelete) " • Auto-Delete" else ""
                    Text("Alarm: $timeString ($repeatText$autoDeleteText)", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = playlist.isEnabled, onCheckedChange = { onToggle() })
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text("Songs (${playlistWithSongs.songs.size})", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            
            playlistWithSongs.songs.take(3).forEachIndexed { index, song ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPlaySong(index) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(song.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (playlistWithSongs.songs.size > 3) {
                Text("... and ${playlistWithSongs.songs.size - 3} more", style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { onEdit() })
            }

            Spacer(Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onPlaySong(0) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play All")
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun PlayerControlBar(
    title: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onMaximize: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onMaximize() },
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title.ifEmpty { "Unknown Song" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "${formatTime(position)} / ${formatTime(duration)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShuffleToggle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (shuffleModeEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = if (shuffleModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRepeatToggle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                Player.REPEAT_MODE_ALL -> Icons.Default.RepeatOn
                                else -> Icons.Default.Repeat
                            },
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onMaximize, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Fullscreen, null, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                        Icon(if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            Slider(
                value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                onValueChange = { onSeek((it * duration).toLong()) },
                modifier = Modifier.fillMaxWidth().height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun PlayerScreen(
    title: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onStop: () -> Unit,
    onSchedule: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onMinimize: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", modifier = Modifier.size(32.dp))
                }
                Row {
                    IconButton(onClick = onAddToPlaylist) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Playlist", modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = onSchedule) {
                        Icon(Icons.Default.Schedule, contentDescription = "Schedule", modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = onStop) {
                        Icon(Icons.Default.Close, contentDescription = "Stop", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Box(
                modifier = Modifier.size(280.dp).clip(RoundedCornerShape(24.dp)).background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(120.dp), tint = MaterialTheme.colorScheme.primary)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = title.ifEmpty { "Unknown Song" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "Now Playing", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
            }

            Column {
                Slider(
                    value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatTime(position), style = MaterialTheme.typography.labelMedium)
                    Text(text = formatTime(duration), style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffleToggle) {
                    Icon(
                        if (shuffleModeEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(80.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                        null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = onRepeatToggle) {
                    Icon(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            Player.REPEAT_MODE_ALL -> Icons.Default.RepeatOn
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onEditPlaylist: (Int) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf<Calendar?>(null) }
    
    val displayPlaylists = if (selectedDate != null) {
        playlists.filter { item ->
            val date = selectedDate!!
            val cal = Calendar.getInstance().apply { timeInMillis = item.playlist.alarmTime }
            if (item.playlist.scheduledDate > 0) {
                val scheduledCal = Calendar.getInstance().apply { timeInMillis = item.playlist.scheduledDate }
                scheduledCal.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
                scheduledCal.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
            } else if (item.playlist.repeatMode == 1) { // Daily
                true
            } else if (item.playlist.repeatMode == 2) { // Specific Days
                val dayNum = date.get(Calendar.DAY_OF_WEEK)
                item.playlist.daysOfWeek.split(",").contains(dayNum.toString())
            } else { // Once
                cal.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
            }
        }
    } else {
        // Show all enabled/upcoming playlists if no date selected
        playlists.filter { it.playlist.isEnabled }.sortedBy { it.playlist.alarmTime }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedDate == null) "Upcoming Schedules" else "Schedule Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (selectedDate != null) {
                        IconButton(onClick = { selectedDate = null }) {
                            Icon(Icons.Default.ClearAll, "Show All")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Button(
                onClick = {
                    val initial = selectedDate ?: Calendar.getInstance()
                    DatePickerDialog(context, { _, y, m, d ->
                        selectedDate = Calendar.getInstance().apply { set(y, m, d) }
                    }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                val sdf = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault())
                Icon(Icons.Default.CalendarMonth, null)
                Spacer(Modifier.width(8.dp))
                Text(if (selectedDate != null) "Selected: ${sdf.format(selectedDate!!.time)}" else "Filter by Date")
            }
            
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (selectedDate == null) "All Scheduled Playlists:" else "Events for this day:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            
            if (displayPlaylists.isEmpty()) {
                Box(modifier = Modifier.fillWeight(1f), contentAlignment = Alignment.Center) {
                    Text("No playlists scheduled", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(displayPlaylists) { item ->
                        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onEditPlaylist(item.playlist.id) }
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (item.playlist.repeatMode > 0) Icons.Default.Repeat else Icons.Default.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.playlist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    val scheduleInfo = when(item.playlist.repeatMode) {
                                        1 -> "Daily at ${timeFormat.format(Date(item.playlist.alarmTime))}"
                                        2 -> "Custom Days at ${timeFormat.format(Date(item.playlist.alarmTime))}"
                                        else -> {
                                            val datePart = if (item.playlist.scheduledDate > 0) dateFormat.format(Date(item.playlist.scheduledDate)) else "Once"
                                            "$datePart at ${timeFormat.format(Date(item.playlist.alarmTime))}"
                                        }
                                    }
                                    Text(scheduleInfo, style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.fillWeight(weight: Float): Modifier = this.then(Modifier.fillMaxHeight().fillMaxWidth())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val enabledTabs by viewModel.enabledTabs.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val volumeBoost by viewModel.volumeBoost.collectAsState()
    val isMovieMode by viewModel.isMovieMode.collectAsState()
    val eqEnabled by viewModel.equalizerEnabled.collectAsState()
    val fadeInEnabled by viewModel.isFadeInEnabled.collectAsState()
    val sleepTimer by viewModel.sleepTimerMinutes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Manage Tabs
            SettingsSection(title = "Manage Home Tabs") {
                val allTabs = listOf("Tracks", "Playlists", "Albums")
                allTabs.forEach { tab ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleTab(tab) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tab)
                        Checkbox(
                            checked = enabledTabs.contains(tab),
                            onCheckedChange = { viewModel.toggleTab(tab) }
                        )
                    }
                }
            }

            // Dark Mode
            SettingsSection(title = "Display") {
                Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf("System", "Light", "Dark")
                    modes.forEachIndexed { index, label ->
                        FilterChip(
                            selected = darkMode == index,
                            onClick = { viewModel.setDarkMode(index) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Volume & Audio
            SettingsSection(title = "Audio & Sound") {
                Text("Volume Boost: ${(volumeBoost * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = volumeBoost,
                    onValueChange = { viewModel.setVolumeBoost(it) },
                    valueRange = 0.5f..2.0f
                )
                
                ListItem(
                    headlineContent = { Text("Movie Mode") },
                    supportingContent = { Text("Optimized audio for immersive sound") },
                    trailingContent = { Switch(checked = isMovieMode, onCheckedChange = { viewModel.toggleMovieMode(it) }) }
                )
                
                ListItem(
                    headlineContent = { Text("Equalizer") },
                    supportingContent = { Text("Custom frequency adjustments") },
                    trailingContent = { Switch(checked = eqEnabled, onCheckedChange = { viewModel.toggleEqualizer(it) }) }
                )

                ListItem(
                    headlineContent = { Text("Sunrise Alarm (Fade-in)") },
                    supportingContent = { Text("Slowly increase volume over 30 seconds") },
                    trailingContent = { Switch(checked = fadeInEnabled, onCheckedChange = { viewModel.toggleFadeIn(it) }) }
                )

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Sleep Timer: ${if (sleepTimer > 0) "$sleepTimer min" else "Off"}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = sleepTimer.toFloat(),
                        onValueChange = { viewModel.setSleepTimer(it.toInt()) },
                        valueRange = 0f..120f,
                        steps = 8
                    )
                }
            }

            // App Updates
            SettingsSection(title = "App Updates") {
                val updateStatus by viewModel.updateStatus.collectAsState()
                
                when (val status = updateStatus) {
                    is UpdateStatus.Idle -> {
                        Button(onClick = { viewModel.checkForUpdates() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Check for Updates")
                        }
                    }
                    is UpdateStatus.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(16.dp))
                            Text("Checking GitHub for updates...")
                        }
                    }
                    is UpdateStatus.UpdateAvailable -> {
                        Column {
                            Text("New version available: ${status.version}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("An update is available on GitHub. Tap below to download the APK.", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { viewModel.downloadAndInstall(status.url) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Download & Install Update")
                            }
                        }
                    }
                    is UpdateStatus.NoUpdate -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color.Green)
                            Text("You are up to date!", fontWeight = FontWeight.Bold)
                            TextButton(onClick = { viewModel.checkForUpdates() }) { Text("Check Again") }
                        }
                    }
                    is UpdateStatus.Downloading -> {
                        Column {
                            Text("Downloading update...")
                            LinearProgressIndicator(progress = status.progress, modifier = Modifier.fillMaxWidth())
                            Text("${(status.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    is UpdateStatus.ReadyToInstall -> {
                        Text("Download complete. Opening installer...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    is UpdateStatus.Error -> {
                        Column {
                            Text("Update check failed", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Text(status.message, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { viewModel.checkForUpdates() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Try Again")
                            }
                        }
                    }
                }
            }

            // Permissions
            SettingsSection(title = "App Permissions") {
                val permissions = remember {
                    mutableListOf<String>().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.READ_MEDIA_AUDIO)
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            add(Manifest.permission.SCHEDULE_EXACT_ALARM)
                        }
                    }
                }
                
                permissions.forEach { perm ->
                    val isGranted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(perm.substringAfterLast("."), style = MaterialTheme.typography.bodySmall)
                        Icon(
                            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isGranted) Color.Green else Color.Red
                        )
                    }
                }
                
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage in System Settings")
                }
            }

            // About & Version
            SettingsSection(title = "About PlayTime") {
                Text("PlayTime is your ultimate scheduled music companion, designed to wake you up or set the mood at exactly the right time.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val version = packageInfo.versionName ?: "1.0.0"
                Text("Version: $version", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Build: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode}", style = MaterialTheme.typography.labelSmall)
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}
