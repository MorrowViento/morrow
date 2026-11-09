package com.jiuxina.ying

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jiuxina.ying.ui.glass.GlassBackdropHost
import com.jiuxina.ying.ui.glass.GlassTheme
import com.jiuxina.ying.ui.glass.glassColors
import com.jiuxina.ying.ui.glass.glassTypography

/**
 * M0 验证页：确认 Glasmio 0.1.2 玻璃材质在本工程依赖树下可正常渲染。
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
                        Box(Modifier.fillMaxSize().background(glassColors.groupedBackground))
                    },
                    overlayContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(64.dp))
                            Text(
                                text = "萤 · Kotlin 重构",
                                style = glassTypography.headlineLarge,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "M0 验证页 · Glasmio 0.1.2\n玻璃材质渲染正常即通过",
                                style = glassTypography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    },
                )
            }
        }
    }
}
