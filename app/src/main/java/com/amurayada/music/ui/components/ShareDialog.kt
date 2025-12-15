package com.amurayada.music.ui.components

import android.graphics.Bitmap
import android.graphics.Picture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amurayada.music.data.model.Song
import com.amurayada.music.utils.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShareDialog(
    song: Song,
    dominantColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Picture to capture the drawing commands
    val picture = remember { Picture() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Compartir Canción",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Container that captures the content into a Picture
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val width = this.size.width.toInt()
                        val height = this.size.height.toInt()
                        
                        onDrawWithContent {
                            val pictureCanvas = picture.beginRecording(width, height)
                            val composeCanvas = androidx.compose.ui.graphics.Canvas(pictureCanvas)
                            
                            draw(this, this.layoutDirection, composeCanvas, this.size) {
                                this@onDrawWithContent.drawContent()
                            }
                            
                            picture.endRecording()
                            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPicture(picture) }
                        }
                    }
            ) {
                ShareSongCard(
                    song = song,
                    dominantColor = dominantColor
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            val bitmap = createBitmapFromPicture(picture)
                            if (bitmap != null) {
                                ShareUtils.shareBitmap(
                                    context, 
                                    bitmap, 
                                    "Escuchando ${song.title} de ${song.artist} en Harmoni"
                                )
                                onDismiss()
                            }
                        }
                    }
                ) {
                    Text("Compartir")
                }
            }
        }
    }
}

suspend fun createBitmapFromPicture(picture: Picture): Bitmap? = withContext(Dispatchers.IO) {
    if (picture.width <= 0 || picture.height <= 0) return@withContext null
    
    try {
        val bitmap = Bitmap.createBitmap(
            picture.width,
            picture.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
        canvas.drawPicture(picture)
        return@withContext bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
