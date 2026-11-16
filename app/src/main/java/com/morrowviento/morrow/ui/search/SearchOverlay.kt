package com.morrowviento.morrow.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.data.category.CategoryItem
import com.morrowviento.morrow.ui.component.ChoiceChip
import com.morrowviento.morrow.ui.component.ChipRow
import com.morrowviento.morrow.ui.event.EventCardModel
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.glassAccent
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary

/** 只展开输入与筛选栏；结果由首页原有卡片列表显示，不创建遮罩或独立结果页。 */
@Composable
fun SearchBar(
    active: List<EventCardModel>,
    completed: List<EventCardModel>,
    categories: List<CategoryItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: CategoryFilter,
    onFilterChange: (CategoryFilter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allCards = remember(active, completed) { active + completed }
    val counts = remember(allCards, categories) { EventSearch.categoryCounts(allCards, categories.map { it.name }) }
    val uncategorized = remember(allCards) { EventSearch.uncategorizedCount(allCards) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val accent = glassAccent()
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GlassPanel(modifier = Modifier.weight(1f).height(48.dp), capsule = true) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(15.dp)) {
                        drawCircle(secondary, size.width * 0.31f, Offset(size.width * 0.42f, size.height * 0.41f),
                            style = Stroke(1.7.dp.toPx()))
                        drawLine(secondary, Offset(size.width * 0.64f, size.height * 0.63f),
                            Offset(size.width * 0.92f, size.height * 0.90f), 1.7.dp.toPx(), cap = StrokeCap.Round)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("搜索标题、备注或分类", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontSize = 14.sp, color = secondary))
                        BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = primary), cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                                .semantics { contentDescription = "搜索事件" })
                    }
                    if (query.isNotEmpty()) Box(
                        Modifier.size(36.dp).clip(CircleShape).background(primary.copy(alpha = 0.10f))
                            .semantics { contentDescription = "清空搜索" }.clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", style = TextStyle(fontSize = 12.sp, color = secondary)) }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("取消", modifier = Modifier.clip(CircleShape).clickable {
                keyboard?.hide(); focusManager.clearFocus(); onDismiss()
            }.padding(horizontal = 8.dp, vertical = 14.dp),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = primary))
        }
        ChipRow(Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)) {
            ChoiceChip("全部 ${allCards.size}", filter == CategoryFilter.All, onClick = { onFilterChange(CategoryFilter.All) })
            counts.forEach { (name, count) ->
                val named = CategoryFilter.Named(name)
                ChoiceChip("$name $count", filter == named, onClick = {
                    onFilterChange(if (filter == named) CategoryFilter.All else named)
                })
            }
            if (uncategorized > 0) ChoiceChip("未分类 $uncategorized", filter == CategoryFilter.Uncategorized, onClick = {
                onFilterChange(if (filter == CategoryFilter.Uncategorized) CategoryFilter.All else CategoryFilter.Uncategorized)
            })
        }
    }
}
