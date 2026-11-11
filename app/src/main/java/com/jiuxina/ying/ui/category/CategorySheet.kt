package com.jiuxina.ying.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiuxina.ying.data.category.CategoryDelete
import com.jiuxina.ying.data.category.CategoryEdit
import com.jiuxina.ying.data.category.CategoryItem
import com.jiuxina.ying.domain.model.EventCategory
import com.jiuxina.ying.ui.component.ChoiceChip
import com.jiuxina.ying.ui.component.GlassTextField
import com.jiuxina.ying.ui.component.SectionTitle
import com.jiuxina.ying.ui.glass.GlassPanel
import com.jiuxina.ying.ui.glass.glassTextPrimary
import com.jiuxina.ying.ui.glass.glassTextSecondary
import kotlinx.coroutines.launch

/**
 * 分类管理浮层（M2b2）。
 *
 * 同样使用同页浮层而非独立窗口，保证玻璃材质仍能采样到首页背景。
 * 操作结果（重名 / 超长 / 至少保留一个）直接回显在对应输入框下，而不是弹 Toast：
 * 用户在输入现场就能看到原因并修改。
 */
@Composable
fun CategorySheet(
    items: List<CategoryItem>,
    actions: CategoryActions,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingName by remember { mutableStateOf<String?>(null) }
    var pendingPrompt by remember { mutableStateOf<CategoryDelete.NeedsTarget?>(null) }

    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onDismiss),
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.86f),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(Modifier.fillMaxSize()) {
                SheetHeader(onDismiss)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 18.dp, end = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassTextField(
                            value = newName,
                            onValueChange = {
                                newName = it
                                addError = null
                            },
                            placeholder = "新分类名称",
                            modifier = Modifier.weight(1f),
                            error = addError,
                        )
                        ChoiceChip("添加", false, onClick = {
                            scope.launch {
                                when (val result = actions.add(newName)) {
                                    is CategoryEdit.Ok -> {
                                        newName = ""
                                        addError = null
                                        notice = "已添加分类"
                                    }
                                    else -> addError = result.message()
                                }
                            }
                        })
                    }

                    notice?.let {
                        Text(
                            text = it,
                            style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                        )
                    }

                    SectionTitle("共 ${items.size} 个分类")

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(items, key = { it.name }) { item ->
                            CategoryRow(
                                item = item,
                                editing = editingName == item.name,
                                editingText = editingText,
                                editError = editError,
                                onEditingTextChange = {
                                    editingText = it
                                    editError = null
                                },
                                onStartRename = {
                                    editingName = item.name
                                    editingText = item.name
                                    editError = null
                                },
                                onCancelRename = {
                                    editingName = null
                                    editError = null
                                },
                                onConfirmRename = {
                                    scope.launch {
                                        val from = item.name
                                        when (val result = actions.rename(from, editingText)) {
                                            is CategoryEdit.Ok -> {
                                                editingName = null
                                                editError = null
                                                notice = if (editingText.trim() == from) {
                                                    "名称未变化"
                                                } else {
                                                    "已重命名为「${editingText.trim()}」"
                                                }
                                            }
                                            else -> editError = result.message()
                                        }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        when (val result = actions.delete(item.name, null)) {
                                            is CategoryDelete.NeedsTarget -> {
                                                pendingName = item.name
                                                pendingPrompt = result
                                            }
                                            else -> notice = result.message()
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        pendingPrompt?.let { prompt ->
            DeleteConfirmLayer(
                name = pendingName.orEmpty(),
                prompt = prompt,
                onCancel = {
                    pendingPrompt = null
                    pendingName = null
                },
                onPick = { target ->
                    val from = pendingName
                    scope.launch {
                        if (from != null) notice = actions.delete(from, target).message()
                        pendingPrompt = null
                        pendingName = null
                    }
                },
            )
        }
    }
}

/** 删除确认层：分类被占用时必须先选迁移目标，不允许静默丢事件。 */
@Composable
private fun DeleteConfirmLayer(
    name: String,
    prompt: CategoryDelete.NeedsTarget,
    onCancel: () -> Unit,
    onPick: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onCancel),
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "删除「$name」",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
                )
                Text(
                    text = if (prompt.candidates.isEmpty()) {
                        "该分类下还有 ${prompt.eventCount} 个事件，且没有可迁移的目标分类，请先新增一个分类。"
                    } else {
                        "该分类下还有 ${prompt.eventCount} 个事件，请选择迁移到的分类："
                    },
                    style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                )
                prompt.candidates.take(8).forEach { candidate ->
                    ChoiceChip(candidate, false, modifier = Modifier.fillMaxWidth()) {
                        onPick(candidate)
                    }
                }
                Text(
                    text = "取消",
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .padding(vertical = 6.dp),
                    style = TextStyle(fontSize = 13.sp, color = glassTextSecondary()),
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
    ) {
        Text(
            text = "分类管理",
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
        )
        Text(
            text = "完成",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
        )
    }
}

@Composable
private fun CategoryRow(
    item: CategoryItem,
    editing: Boolean,
    editingText: String,
    editError: String?,
    onEditingTextChange: (String) -> Unit,
    onStartRename: () -> Unit,
    onCancelRename: () -> Unit,
    onConfirmRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(primary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (editing) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTextField(
                    value = editingText,
                    onValueChange = onEditingTextChange,
                    placeholder = "分类名称",
                    modifier = Modifier.weight(1f),
                    minHeight = 40.dp,
                    error = editError,
                )
                Text(
                    text = "确定",
                    modifier = Modifier
                        .clickable(onClick = onConfirmRename)
                        .padding(4.dp),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primary),
                )
                Text(
                    text = "取消",
                    modifier = Modifier
                        .clickable(onClick = onCancelRename)
                        .padding(4.dp),
                    style = TextStyle(fontSize = 13.sp, color = secondary),
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = TextStyle(fontSize = 15.sp, color = primary),
                    )
                    Text(
                        text = buildString {
                            append(if (item.usage > 0) "${item.usage} 个事件" else "暂无事件")
                            if (item.isOrphan) append(" · 旧数据中的分类")
                        },
                        style = TextStyle(fontSize = 11.sp, color = secondary),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = "重命名",
                    modifier = Modifier
                        .clickable(onClick = onStartRename)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    style = TextStyle(fontSize = 13.sp, color = secondary),
                )
                Text(
                    text = "删除",
                    modifier = Modifier
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    style = TextStyle(fontSize = 13.sp, color = Color(0xFFFF7A7A)),
                )
            }
        }
    }
}

private fun CategoryEdit.message(): String? = when (this) {
    is CategoryEdit.Ok -> null
    CategoryEdit.Blank -> "分类名不能为空"
    CategoryEdit.TooLong -> "最多 ${EventCategory.MAX_NAME_LENGTH} 个字"
    CategoryEdit.Duplicate -> "已存在同名分类"
    CategoryEdit.NotFound -> "该分类已不存在"
    CategoryEdit.LastOne -> "至少保留一个分类"
}

private fun CategoryDelete.message(): String = when (this) {
    is CategoryDelete.Ok -> if (migratedEvents > 0) "已删除，$migratedEvents 个事件已迁移" else "已删除"
    is CategoryDelete.NeedsTarget -> "该分类下还有 $eventCount 个事件"
    CategoryDelete.LastOne -> "至少保留一个分类"
    CategoryDelete.NotFound -> "该分类已不存在"
    CategoryDelete.InvalidTarget -> "迁移目标不可用"
}
