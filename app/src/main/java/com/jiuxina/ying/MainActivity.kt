package com.jiuxina.ying

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiuxina.ying.ui.glass.GlassBackdropHost
import com.jiuxina.ying.ui.glass.GlassTheme
import com.jiuxina.ying.ui.glass.glassColors

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
                    sampledColor = glassColors().groupedBackground,
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
                                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "M0 验证页 · Glasmio 0.1.2\n玻璃材质渲染正常即通过",
                                style = TextStyle(fontSize = 15.sp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    },
                )
            }
        }
    }
}
