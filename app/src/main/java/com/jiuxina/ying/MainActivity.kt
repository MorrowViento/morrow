package com.jiuxina.ying

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiuxina.ying.ui.glass.GlassBackdropHost
import com.jiuxina.ying.ui.glass.GlassPanel
import com.jiuxina.ying.ui.glass.GlassTheme

/**
 * M0 验证页：确认 Glasmio 0.1.2 玻璃材质在本工程依赖树下可正常渲染。
 *
 * 背景故意使用高对比渐变——纯色背景下玻璃的折射与高光不可见，无法判定是否真的生效。
 * 玻璃面板内若可见模糊、边缘折射与高光，即代表渲染管线工作正常。
 * 后续替换为首页（M2）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlassTheme {
                GlassBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF3B6FE0),
                                            Color(0xFF9B4BE8),
                                            Color(0xFFE84B8A),
                                        ),
                                    ),
                                ),
                        )
                    },
                    overlayContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "萤",
                                style = TextStyle(
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                ),
                            )
                            Text(
                                text = "Kotlin 重构 · M0 渲染验证",
                                style = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f)),
                            )
                            Spacer(Modifier.height(28.dp))
                            GlassPanel(
                                modifier = Modifier.fillMaxWidth().height(132.dp),
                            ) {
                                Text(
                                    text = "玻璃材质\nGlasmio 0.1.2",
                                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            GlassPanel(
                                modifier = Modifier.fillMaxWidth().height(72.dp),
                            ) {
                                Text(
                                    text = "若此处可见模糊与边缘折射，M0 验收通过",
                                    style = TextStyle(fontSize = 13.sp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
