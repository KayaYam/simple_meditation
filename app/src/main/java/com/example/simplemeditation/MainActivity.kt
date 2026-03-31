package com.kayayam.simplemeditation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MindfulnessApp() }
    }
}

data class ThemeData(
    val backgroundColor: Color,
    val textColor: Color,
    val buttonColor: Color,
    val secondaryTextColor: Color,
    val accentColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindfulnessApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("meditation_prefs", Context.MODE_PRIVATE) }

    var selectedTime by remember { mutableStateOf(prefs.getInt("time", 10)) }
    var selectedTheme by remember { mutableStateOf(prefs.getString("theme", "Светлая") ?: "Светлая") }
    var selectedSound by remember { mutableStateOf(prefs.getString("sound", "Птицы") ?: "Птицы") }
    var isTimerRunning by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(0) }

    var showTimeDialog by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showMeditationMenu by remember { mutableStateOf(false) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val themeData = remember(selectedTheme) {
        when (selectedTheme) {
            "Тёмная" -> ThemeData(Color(0xFF1A1A1A), Color(0xFFE0E0E0), Color(0xFF4A6B5F), Color(0xFFB0B0B0), Color(0xFF6B9B8D))
            "Океан" -> ThemeData(Color(0xFFE8F4F8), Color(0xFF2C3E50), Color(0xFF5B8C9E), Color(0xFF7F8C8D), Color(0xFF3498DB))
            "Закат" -> ThemeData(Color(0xFFFFF5EB), Color(0xFF5D4E37), Color(0xFFD4A574), Color(0xFF8B7355), Color(0xFFE67E22))
            else -> ThemeData(Color(0xFFF5F5F0), Color(0xFF4A4A4A), Color(0xFF6B9B8D), Color(0xFF666666), Color(0xFF27AE60))
        }
    }

    val timerAlpha by animateFloatAsState(if (isTimerRunning || timeLeft > 0) 1f else 0f, label = "timer")
    val uiElementsAlpha by animateFloatAsState(if (isTimerRunning) 0f else 1f, label = "ui")

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (isTimerRunning) 1.15f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = EaseInOutSine), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )

    LaunchedEffect(isTimerRunning, timeLeft) {
        if (isTimerRunning && timeLeft > 0) { delay(1000); timeLeft-- }
        else if (timeLeft == 0 && isTimerRunning) isTimerRunning = false
    }

    LaunchedEffect(isTimerRunning, selectedSound) {
        if (isTimerRunning) {
            // При старте нового звука мы НЕ трогаем старый (пусть дозатухает сам)
            // Но нам нужно убедиться, что mediaPlayer свободен для нового объекта
            mediaPlayer = null

            val resId = when (selectedSound) {
                "Птицы" -> R.raw.birds; "Ферма" -> R.raw.farm
                "Водопад" -> R.raw.waterfall; "Волны" -> R.raw.sea
                "Ручей" -> R.raw.stream; "Снег" -> R.raw.snow
                "Костер" -> R.raw.fire; "Лягушки" -> R.raw.frogs
                "Волки" -> R.raw.wolf
                else -> R.raw.birds
            }

            var isFirstStart = true

            suspend fun playTrack() {
                if (!isTimerRunning) return

                try {
                    val mp = MediaPlayer.create(context, resId)
                    mediaPlayer = mp

                    // FADE IN
                    val fadeInSteps = if (isFirstStart) 20 else 5
                    mp.setVolume(0f, 0f)
                    mp.start()

                    for (i in 1..fadeInSteps) {
                        delay(100L)
                        if (!isTimerRunning) return
                        val vol = i.toFloat() / fadeInSteps
                        mp.setVolume(vol, vol)
                    }

                    isFirstStart = false

                    val duration = mp.duration
                    if (duration > 1000) {
                        delay((duration - 600).toLong())

                        // FADE OUT МЕЖДУ КЛИПАМИ (0.5 сек)
                        if (isTimerRunning) {
                            for (i in 5 downTo 1) {
                                val vol = i.toFloat() / 5f
                                mp.setVolume(vol, vol)
                                delay(100L)
                            }
                        }
                    }

                    mp.stop()
                    mp.release()

                    if (isTimerRunning) playTrack()

                } catch (e: Exception) { e.printStackTrace() }
            }

            playTrack()

        } else {
            // МЯГКИЙ ФИНАЛ (3 секунды)
            val mpToFade = mediaPlayer // Копируем ссылку на текущий плеер
            mediaPlayer = null // Сразу освобождаем основную переменную для новых запусков

            mpToFade?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        val finalFadeSteps = 15
                        for (i in finalFadeSteps downTo 0) {
                            val vol = i.toFloat() / finalFadeSteps
                            mp.setVolume(vol, vol)
                            delay(100L)
                        }
                        mp.stop()
                    }
                    mp.release()
                } catch (e: Exception) {
                    try { mp.release() } catch (ex: Exception) {}
                }
            }
        }
    }


    Surface(modifier = Modifier.fillMaxSize(), color = themeData.backgroundColor) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(100.dp))

            Text(
                text = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraLight,
                color = themeData.textColor,
                modifier = Modifier.graphicsLayer { alpha = timerAlpha }
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(themeData.accentColor, CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (!isTimerRunning) { timeLeft = selectedTime * 60; isTimerRunning = true }
                        else { isTimerRunning = false; timeLeft = 0 }
                    }
            ) {
                Icon(painterResource(R.drawable.ic_meditation_figure), null, tint = themeData.backgroundColor, modifier = Modifier.fillMaxSize(0.55f))
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
                    .padding(horizontal = 32.dp)
                    .graphicsLayer { alpha = uiElementsAlpha },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ПЕРЕДАЕМ ЗНАЧЕНИЯ ДЛЯ НАДПИСЕЙ
                ControlIcon(R.drawable.ic_timer, "$selectedTime мин", themeData) {
                    if (uiElementsAlpha > 0.5f) showTimeDialog = true
                }
                ControlIcon(R.drawable.ic_wave, selectedSound, themeData) {
                    if (uiElementsAlpha > 0.5f) showBackgroundDialog = true
                }
                ControlIcon(R.drawable.ic_menu, "Меню", themeData) {
                    if (uiElementsAlpha > 0.5f) showMainMenu = true
                }
            }
        }

        if (showTimeDialog) {
            TimeSelectionDialog(current = selectedTime, onSelect = { selectedTime = it; prefs.edit().putInt("time", it).apply(); showTimeDialog = false }, onDismiss = { showTimeDialog = false }, themeData = themeData)
        }

        if (showBackgroundDialog) {
            SoundSelectionDialog(current = selectedSound, onSelect = { selectedSound = it; prefs.edit().putString("sound", it).apply(); showBackgroundDialog = false }, onDismiss = { showBackgroundDialog = false }, themeData = themeData)
        }

        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                containerColor = themeData.backgroundColor,
                title = { Text("Выберите тему", color = themeData.textColor) },
                text = {
                    Column {
                        listOf("Светлая", "Тёмная", "Океан", "Закат").forEach { themeName ->
                            TextButton(
                                onClick = {
                                    selectedTheme = themeName
                                    prefs.edit().putString("theme", themeName).apply()
                                    showThemeDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(themeName, color = if (themeName == selectedTheme) themeData.accentColor else themeData.textColor)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Закрыть", color = themeData.accentColor) } }
            )
        }

        if (showMainMenu) {
            ModalBottomSheet(onDismissRequest = { showMainMenu = false }, containerColor = themeData.backgroundColor) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp).padding(horizontal = 24.dp)) {
                    Text("Настройки", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = themeData.textColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    MenuItem("Тема оформления", themeData) { showMainMenu = false; showThemeDialog = true }
                    MenuItem("Библиотека смыслов", themeData) { showMainMenu = false; showMeditationMenu = true }
                    MenuItem("Мы в ВКонтакте", themeData) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://vk.com/simple_meditation")))
                    }
                }
            }
        }

        if (showMeditationMenu) {
            MeditationMenu(showMenu = true, onDismiss = { showMeditationMenu = false }, onBack = { showMeditationMenu = false }, themeData = themeData)
        }
    }
}

@Composable
fun ControlIcon(id: Int, label: String, themeData: ThemeData, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() }
    ) {
        Icon(
            painter = painterResource(id),
            contentDescription = null,
            tint = themeData.textColor.copy(alpha = 0.7f),
            modifier = Modifier.size(30.dp)
        )
        // ВОТ ЭТА СТРОЧКА ВЕРНУЛАСЬ:
        Text(label, fontSize = 11.sp, color = themeData.secondaryTextColor)
    }
}

@Composable
fun TimeSelectionDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit, themeData: ThemeData) {
    var tempTime by remember { mutableStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = themeData.backgroundColor,
        title = { Text("Время медитации", color = themeData.textColor) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${tempTime.toInt()} минут", fontSize = 24.sp, color = themeData.accentColor)
                Slider(
                    value = tempTime,
                    onValueChange = { tempTime = it },
                    valueRange = 1f..60f,
                    steps = 59,
                    colors = SliderDefaults.colors(thumbColor = themeData.accentColor, activeTrackColor = themeData.accentColor)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(tempTime.toInt()) }) { Text("ОК", color = themeData.accentColor) } }
    )
}

@Composable
fun SoundSelectionDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit, themeData: ThemeData) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = themeData.backgroundColor,
        title = { Text("Фоновый звук", color = themeData.textColor) },
        text = {
            Column {
                listOf("Птицы", "Ферма", "Водопад", "Волны", "Ручей", "Снег", "Костер", "Лягушки", "Волки").forEach { sound ->
                    TextButton(onClick = { onSelect(sound) }, modifier = Modifier.fillMaxWidth()) {
                        Text(sound, color = if (sound == current) themeData.accentColor else themeData.textColor)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть", color = themeData.accentColor) } }
    )
}