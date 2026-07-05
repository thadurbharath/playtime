package com.example.sample1

import android.Manifest
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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.sample1.ui.theme.Sample1Theme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Sample1Theme {
                MainNavigation()
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val viewModel: PlaylistViewModel = viewModel()
    val context = LocalContext.current

    // MediaController state (Shared across screens)
    var controller by remember { mutableStateOf<Player?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentSongTitle by remember { mutableStateOf("") }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

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
        }

        controllerFuture.addListener({
            try {
                val mediaController = controllerFuture.get()
                controller = mediaController
                isPlaying = mediaController.isPlaying
                currentSongTitle = mediaController.mediaMetadata.title?.toString() ?: ""
                duration = mediaController.duration.coerceAtLeast(0L)
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

    Scaffold(
        bottomBar = {
            if (controller != null && (isPlaying || currentSongTitle.isNotEmpty())) {
                PlayerControlBar(
                    title = currentSongTitle,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    onPlayPause = {
                        if (isPlaying) controller?.pause() else controller?.play()
                    },
                    onStop = {
                        val intent = Intent(context, MusicService::class.java).apply { action = "STOP" }
                        context.startService(intent)
                        isPlaying = false
                        currentSongTitle = ""
                    },
                    onNext = { controller?.seekToNext() },
                    onPrevious = { controller?.seekToPrevious() },
                    onSeek = { controller?.seekTo(it) }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "list",
            modifier = Modifier.padding(padding)
        ) {
            composable("list") {
                PlaylistListScreen(
                    viewModel = viewModel,
                    onAddPlaylist = { navController.navigate("edit/-1") },
                    onEditPlaylist = { id -> navController.navigate("edit/$id") }
                )
            }
            composable(
                route = "edit/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.IntType })
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: -1
                PlaylistEditScreen(
                    viewModel = viewModel,
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistListScreen(
    viewModel: PlaylistViewModel,
    onAddPlaylist: () -> Unit,
    onEditPlaylist: (Int) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val context = LocalContext.current

    // Permission request
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        launcher.launch(permissions.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Melodies", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
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
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
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
    var selectedSongs by remember { mutableStateOf(existing?.songs ?: emptyList()) }
    var selectedCalendar by remember { 
        mutableStateOf(Calendar.getInstance().apply { 
            if (existing != null) timeInMillis = existing.playlist.alarmTime 
        }) 
    }
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
                                    viewModel.addPlaylist(name, selectedSongs, selectedCalendar.timeInMillis, repeatMode, daysStr)
                                } else {
                                    viewModel.updatePlaylist(existing!!.copy(
                                        playlist = existing.playlist.copy(
                                            name = name, 
                                            alarmTime = selectedCalendar.timeInMillis, 
                                            isEnabled = true,
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
                    Text("Alarm: $timeString ($repeatText)", style = MaterialTheme.typography.bodySmall)
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
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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

fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}
