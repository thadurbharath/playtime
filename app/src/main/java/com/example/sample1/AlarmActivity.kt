package com.example.sample1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sample1.ui.theme.Sample1Theme

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val playlistName = intent.getStringExtra("playlistName") ?: "Melody Timer"
        val currentUri = intent.getStringExtra("currentUri") ?: ""
        val isExternal = currentUri.startsWith("http") && 
                (currentUri.contains("youtube") || currentUri.contains("youtu.be") || currentUri.contains("spotify"))

        setContent {
            Sample1Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(32.dp))
                        Text(
                            text = "PlayTime Schedule",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = playlistName,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        
                        if (isExternal) {
                            val youtubeId = remember(currentUri) {
                                when {
                                    currentUri.contains("v=") -> currentUri.substringAfter("v=").substringBefore("&").substringBefore("?")
                                    currentUri.contains("youtu.be/") -> currentUri.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                                    currentUri.contains("/shorts/") -> currentUri.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
                                    currentUri.contains("/embed/") -> currentUri.substringAfter("/embed/").substringBefore("?").substringBefore("&")
                                    else -> null
                                }
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            if (youtubeId != null && youtubeId.length >= 11) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter("https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"),
                                    contentDescription = null,
                                    modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Spacer(Modifier.height(16.dp))
                            }

                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUri)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.OpenInNew, null)
                                Spacer(Modifier.width(8.dp))
                                Text("OPEN IN APP", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(48.dp))
                        Button(
                            onClick = {
                                val stopIntent = Intent(this@AlarmActivity, MusicService::class.java).apply {
                                    action = "STOP"
                                }
                                startService(stopIntent)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, null)
                            Spacer(Modifier.width(8.dp))
                            Text("STOP MUSIC", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}