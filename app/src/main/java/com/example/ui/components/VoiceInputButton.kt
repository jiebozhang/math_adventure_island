package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.MutedGray

@Composable
fun VoiceInputButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf("") }
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    fun stopListening() {
        recognizer?.stopListening()
        listening = false
        amplitude = 0f
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { message = "可以开始说啦" }
            override fun onBeginningOfSpeech() { message = "正在听你说" }
            override fun onRmsChanged(rmsdB: Float) { amplitude = rmsdB.coerceIn(0f, 12f) }
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onResult(text) else message = "没有听清，可以再试一次哦"
                listening = false
                amplitude = 0f
            }
            override fun onError(error: Int) {
                // 顺手优化项，非本次根因修复（Manifest <queries>）的一部分：
                // 把"权限不足"从笼统的"没有检测到麦克风"中单独分出来，
                // 避免后续排查语音问题时，把权限问题和识别错误混为一谈。
                message = if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    "没有麦克风权限，可以用打字代替哦"
                } else {
                    "没有检测到麦克风，可以用打字代替哦"
                }
                listening = false
                amplitude = 0f
            }
            override fun onEndOfSpeech() { if (listening) message = "正在整理你的话" }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
        })
        onDispose { recognizer?.destroy() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.startListening(intent)
            listening = true
        } else {
            message = "没有麦克风权限，可以用打字代替哦"
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                if (listening) {
                    stopListening()
                } else if (recognizer == null) {
                    message = "这台设备暂不支持语音输入，可以用打字代替哦"
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    }
                    recognizer.startListening(intent)
                    listening = true
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (listening) MutedGray else AccentAmber),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (listening) "停止录音" else "语音输入", fontSize = 12.sp)
        }

        if (listening) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(5) { index ->
                    val height = (8f + amplitude * (index % 3 + 1) * 1.8f).coerceAtMost(30f)
                    Spacer(
                        modifier = Modifier
                            .width(4.dp)
                            .height(height.dp)
                            .background(Color(0xFF4F6BED))
                    )
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = MutedGray, fontSize = 11.sp)
    }
}
