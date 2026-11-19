package com.morrowviento.morrow.data.backup

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.morrowviento.morrow.data.settings.AccentColor
import com.morrowviento.morrow.data.settings.AppSettings
import com.morrowviento.morrow.data.settings.BackdropMode
import com.morrowviento.morrow.data.settings.BackdropSettings
import com.morrowviento.morrow.data.settings.GLASS_FROST_DEFAULT
import com.morrowviento.morrow.data.settings.legacyGlassFrost
import com.morrowviento.morrow.data.settings.ThemeMode
import com.morrowviento.morrow.domain.model.CountDirection
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventReminder
import com.morrowviento.morrow.domain.model.EventKind
import java.time.LocalDate
import com.morrowviento.morrow.domain.model.RepeatType
import com.morrowviento.morrow.domain.model.SortMode
import java.time.LocalDateTime

/**
 * 备份文档（M6）。
 *
 * 刻意不用 Gson 直接反序列化 [CountdownEvent]：领域模型里是非空 `LocalDateTime` 与枚举，
 * Gson 遇到缺失字段会把 null 塞进 non-null 属性、遇到未知枚举值直接抛异常，
 * 一份「用户从旧版本导出、少了一个字段」的备份就会整包导入失败。
 * 这里逐字段读、逐条事件解析，坏的那一条跳过并计数，其余照常导入。
 *
 * 时间统一用 **ISO-8601 本地时刻字符串**（与 Flutter 版 `toIso8601String()` 同语义，
 * 也便于用户直接打开文件查看），解码时兼容毫秒时间戳，兼容将来换成时间戳的备份。
 */
data class BackupDocument(
    val exportedAt: String,
    val appVersion: String,
    val events: List<BackupEvent>,
    val categories: List<String>,
    val settings: BackupSettings,
    /** 小部件样式：`widgetId -> 样式 JSON 原文`，原样搬运，不解码成结构体。 */
    val widgets: List<BackupWidget>,
)

data class BackupEvent(
    val id: String,
    val title: String,
    /** ISO-8601 本地时刻。 */
    val targetDate: String,
    val category: String,
    val note: String,
    val icon: String,
    val direction: String,
    val reminders: List<BackupReminder>,
    val isAllDay: Boolean,
    val isCompleted: Boolean,
    val isPinned: Boolean,
    val repeatType: String,
    val repeatMonth: Int?,
    val repeatDay: Int?,
    /** 重复间隔（4.2.0）。缺省 1，旧备份读成「每周期一次」。 */
    val repeatInterval: Int = 1,
    /** 每周重复的星期（ISO 1..7）。旧备份没有这一项。 */
    val repeatWeekdays: List<Int> = emptyList(),
    /** 年循环按农历。旧备份没有这一项。 */
    val repeatOnLunar: Boolean = false,
    /** 重复结束日期（ISO 本地日期）。 */
    val repeatUntil: String? = null,
    /** 持续事件的结束日期（ISO 本地日期）。 */
    val endDate: String? = null,
    /** 持续事件结束日的具体时刻（ISO 本地时间 HH:mm）；缺省沿用开始时刻。 */
    val endTime: String? = null,
    val createdAt: String,
    val kind: String = EventKind.TODO.name,
    val lastHandledOccurrence: String? = null,
    /** 多维标记（4.2.0 第二批）。旧备份没有这一项 → 空列表。 */
    val tags: List<String> = emptyList(),
    /** 归档时刻（ISO 本地时刻）；缺省未归档。归档 ≠ 完成，见 CountdownEvent。 */
    val archivedAt: String? = null,
    /** 预计时长（分钟），0 = 未指定。只影响时间块高度。 */
    val durationMinutes: Int = 0,
    /** 子任务清单（4.5.0）。旧备份没有这一项 → 空列表。 */
    val checklist: List<BackupChecklistItem> = emptyList(),
    /** 纪念日的「第 N 天」里程碑（4.6.0）。旧备份没有这一项 → 空列表。 */
    val milestones: List<Int> = emptyList(),
)

data class BackupChecklistItem(
    val id: String,
    val title: String,
    val done: Boolean,
)

data class BackupReminder(
    val id: String,
    val minutesBefore: Int,
    val enabled: Boolean,
)

data class BackupSettings(
    val themeMode: String,
    val sortMode: String,
    val reduceTransparency: Boolean,
    val reduceMotion: Boolean,
    /**
     * 主题色（[AccentColor.prefValue]）。空串表示这份备份没有这个字段（alpha25 之前），
     * 导入时回落默认蓝——旧备份不会把用户的主题色改成别的颜色。
     */
    val accent: String = "",
    /**
     * 玻璃厚度 0~1。**负数表示这份备份没有这个字段**（alpha7 及更早的备份），
     * 导入时依次按旧四档字符串、再按布尔反推，见 [BackupMapper.toSettings]。
     */
    val glassFrost: Float = -1f,
    /** 旧版四档字符串，只为兼容老备份的读取；导出时仍写一份，方便降级。 */
    val glassMaterial: String = "",
    /**
     * 页面底色与取色（4.1.0 新增）。
     *
     * **图片本体不进备份**：一张 3 MB 的照片塞进 JSON 会让文件膨胀到没法用邮箱传，
     * 而且换机后路径也失效。这里只带走「取色种子」——导入后配色仍是同一套，
     * 底图回落到丝缎，用户在新设备上重新选一张即可。空串表示这份备份没有这个字段。
     */
    val backdropMode: String = "",
    val backdropAutoTint: Boolean = false,
    val backdropTintSeed: Long = 0L,
)

data class BackupWidget(
    val widgetId: Int,
    val style: String,
)

/** 解析结果。[Ok.skipped] 是被跳过的坏事件条数——UI 要如实告诉用户少导了几条。 */
sealed interface BackupParse {
    data class Ok(val document: BackupDocument, val skippedEvents: Int) : BackupParse

    /** [reason] 直接展示给用户，所以用中文短句而不是异常栈。 */
    data class Failure(val reason: String) : BackupParse
}

object BackupCodec {

    const val FORMAT = "morrow-backup"

    /**
     * 当前写入的版本。读到更高的版本说明备份来自更新的 App，拒绝而不是猜测。
     *
     * 4 → 5（4.5.0）：事件多了 `checklist` 子任务数组。字段本身是可选的，旧 App 读到会
     * 静默忽略——但用户会以为「子任务也备份了」，所以宁可在旧版本上直接报「请先升级」。
     *
     * 5 → 6（4.6.0）：多了纪念日的 `milestones`。同理：旧版本导入新版备份直接要求升级。
     */
    const val CURRENT_VERSION = 6

    // ---------------------------------------------------------------- 编码

    fun encode(document: BackupDocument): String {
        val root = JsonObject()
        root.addProperty("format", FORMAT)
        root.addProperty("version", CURRENT_VERSION)
        root.addProperty("exportedAt", document.exportedAt)
        root.addProperty("appVersion", document.appVersion)

        root.add(
            "events",
            JsonArray().apply {
                document.events.forEach { add(encodeEvent(it)) }
            },
        )
        root.add(
            "categories",
            JsonArray().apply { document.categories.forEach { add(it) } },
        )
        root.add("settings", encodeSettings(document.settings))
        root.add(
            "widgets",
            JsonArray().apply {
                document.widgets.forEach { widget ->
                    add(
                        JsonObject().apply {
                            addProperty("id", widget.widgetId)
                            addProperty("style", widget.style)
                        },
                    )
                }
            },
        )
        return root.toString()
    }

    private fun encodeEvent(event: BackupEvent): JsonObject = JsonObject().apply {
        addProperty("id", event.id)
        addProperty("title", event.title)
        addProperty("targetDate", event.targetDate)
        addProperty("category", event.category)
        // 备注可以很长且含换行，JsonObject 会自行转义，不需要额外处理
        addProperty("note", event.note)
        addProperty("icon", event.icon)
        addProperty("direction", event.direction)
        addProperty("isAllDay", event.isAllDay)
        addProperty("isCompleted", event.isCompleted)
        addProperty("isPinned", event.isPinned)
        addProperty("kind", event.kind)
        event.lastHandledOccurrence?.let { addProperty("lastHandledOccurrence", it) }
        addProperty("repeatType", event.repeatType)
        event.repeatMonth?.let { addProperty("repeatMonth", it) }
        event.repeatDay?.let { addProperty("repeatDay", it) }
        // 新增可选项：只在非默认时写，旧版本读到也不会被空字段困扰
        if (event.repeatInterval != 1) addProperty("repeatInterval", event.repeatInterval)
        if (event.repeatWeekdays.isNotEmpty()) {
            add("repeatWeekdays", JsonArray().apply { event.repeatWeekdays.forEach { add(it) } })
        }
        if (event.repeatOnLunar) addProperty("repeatOnLunar", true)
        event.repeatUntil?.let { addProperty("repeatUntil", it) }
        event.endDate?.let { addProperty("endDate", it) }
        event.endTime?.let { addProperty("endTime", it) }
        // 标签用数组而不是逗号串：备份是给人看的，数组比 "a,b,c" 更不容易被手改坏
        if (event.tags.isNotEmpty()) {
            add("tags", JsonArray().apply { event.tags.forEach { add(it) } })
        }
        event.archivedAt?.let { addProperty("archivedAt", it) }
        if (event.durationMinutes > 0) addProperty("durationMinutes", event.durationMinutes)
        // 子任务写成对象数组：备份要能被人读懂，也要能只改其中一条
        if (event.checklist.isNotEmpty()) {
            add(
                "checklist",
                JsonArray().apply {
                    event.checklist.forEach { item ->
                        add(
                            JsonObject().apply {
                                addProperty("id", item.id)
                                addProperty("title", item.title)
                                addProperty("done", item.done)
                            },
                        )
                    }
                },
            )
        }
        // 里程碑是纯整数，数组最直观，也方便手改
        if (event.milestones.isNotEmpty()) {
            add("milestones", JsonArray().apply { event.milestones.forEach { add(it) } })
        }
        addProperty("createdAt", event.createdAt)
        add(
            "reminders",
            JsonArray().apply {
                event.reminders.forEach { reminder ->
                    add(
                        JsonObject().apply {
                            addProperty("id", reminder.id)
                            addProperty("minutesBefore", reminder.minutesBefore)
                            addProperty("enabled", reminder.enabled)
                        },
                    )
                }
            },
        )
    }

    private fun encodeSettings(settings: BackupSettings): JsonObject = JsonObject().apply {
        addProperty("themeMode", settings.themeMode)
        addProperty("sortMode", settings.sortMode)
        addProperty("accent", settings.accent)
        // 新旧字段都写：新版本读 glassFrost，旧版本读 glassMaterial
        addProperty("glassFrost", settings.glassFrost)
        addProperty("glassMaterial", settings.glassMaterial)
        addProperty("reduceTransparency", settings.reduceTransparency)
        addProperty("reduceMotion", settings.reduceMotion)
        addProperty("backdropMode", settings.backdropMode)
        addProperty("backdropAutoTint", settings.backdropAutoTint)
        addProperty("backdropTintSeed", settings.backdropTintSeed)
    }

    // ---------------------------------------------------------------- 解码

    fun decode(raw: String): BackupParse {
        val root = runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?: return BackupParse.Failure("这不是有效的 JSON 文件")
        if (!root.isJsonObject) return BackupParse.Failure("备份文件格式不对")

        val obj = root.asJsonObject
        val format = obj.stringOrNull("format")
        if (format != FORMAT) return BackupParse.Failure("这不是「朝夕」的备份文件")

        val version = obj.intOrNull("version") ?: 0
        if (version <= 0) return BackupParse.Failure("备份文件缺少版本号")
        if (version > CURRENT_VERSION) {
            return BackupParse.Failure("备份来自更新版本的朝夕（v$version），请先升级 App")
        }

        var skipped = 0
        val events = buildList {
            obj.arrayOrNull("events")?.forEach { element ->
                val parsed = element.takeIf { it.isJsonObject }?.asJsonObject?.let(::decodeEvent)
                if (parsed == null) skipped++ else add(parsed)
            }
        }

        val categories = obj.arrayOrNull("categories")
            ?.mapNotNull { element ->
                element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()
            }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val widgets = buildList {
            obj.arrayOrNull("widgets")?.forEach { element ->
                val widget = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val id = widget.intOrNull("id") ?: return@forEach
                val style = widget.stringOrNull("style")?.takeIf { it.isNotBlank() } ?: return@forEach
                add(BackupWidget(widgetId = id, style = style))
            }
        }

        val settings = obj.objectOrNull("settings")?.let(::decodeSettings)
            ?: BackupSettings(
                themeMode = ThemeMode.SYSTEM.prefValue,
                sortMode = SortMode.DISTANCE.prefValue,
                reduceTransparency = false,
                reduceMotion = false,
                glassFrost = GLASS_FROST_DEFAULT,
            )

        return BackupParse.Ok(
            document = BackupDocument(
                exportedAt = obj.stringOrNull("exportedAt").orEmpty(),
                appVersion = obj.stringOrNull("appVersion").orEmpty(),
                events = events,
                categories = categories,
                settings = settings,
                widgets = widgets,
            ),
            skippedEvents = skipped,
        )
    }

    /**
     * 缺 id / 目标时间的条目视为无效——这两项缺任何一项都无法还原成一条事件。
     *
     * 目标时间在**这里**就试解析一次：留到 [BackupMapper.toDomain] 才发现解析不了，
     * 那条数据会被静默丢弃，用户只看到「导入了 N 个」却不知道自己少了哪几条。
     * 在这里判掉，跳过数才是用户能对上账的真实数字。
     */
    private fun decodeEvent(obj: JsonObject): BackupEvent? {
        val id = obj.stringOrNull("id")?.takeIf { it.isNotBlank() } ?: return null
        val targetRaw = obj.stringOrNull("targetDate") ?: return null
        parseTime(targetRaw) ?: return null
        val createdRaw = obj.stringOrNull("createdAt")
        // 创建时间只是排序参考，坏掉就用目标时间顶上，不至于为它丢整条事件
        val createdAtRaw = createdRaw?.takeIf { parseTime(it) != null } ?: targetRaw
        return BackupEvent(
            id = id,
            title = obj.stringOrNull("title").orEmpty(),
            targetDate = targetRaw,
            category = obj.stringOrNull("category") ?: CountdownEvent.DEFAULT_CATEGORY,
            note = obj.stringOrNull("note").orEmpty(),
            icon = obj.stringOrNull("icon").orEmpty(),
            direction = obj.stringOrNull("direction") ?: CountDirection.AUTO.name,
            reminders = decodeReminders(obj.arrayOrNull("reminders")),
            isAllDay = obj.boolOrNull("isAllDay") ?: false,
            isCompleted = obj.boolOrNull("isCompleted") ?: false,
            isPinned = obj.boolOrNull("isPinned") ?: false,
            repeatType = obj.stringOrNull("repeatType") ?: RepeatType.NONE.name,
            repeatMonth = obj.intOrNull("repeatMonth"),
            repeatDay = obj.intOrNull("repeatDay"),
            repeatInterval = (obj.intOrNull("repeatInterval") ?: 1)
                .coerceIn(1, CountdownEvent.MAX_REPEAT_INTERVAL),
            repeatWeekdays = obj.arrayOrNull("repeatWeekdays")
                ?.mapNotNull { element ->
                    element.takeIf { it.isJsonPrimitive }
                        ?.let { runCatching { it.asJsonPrimitive.asInt }.getOrNull() }
                }
                ?.filter { it in 1..7 }
                ?.distinct()
                ?.sorted()
                .orEmpty(),
            repeatOnLunar = obj.boolOrNull("repeatOnLunar") ?: false,
            repeatUntil = obj.stringOrNull("repeatUntil")?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess },
            endDate = obj.stringOrNull("endDate")?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess },
            endTime = obj.stringOrNull("endTime")?.takeIf { runCatching { java.time.LocalTime.parse(it) }.isSuccess },
            createdAt = createdAtRaw,
            kind = obj.stringOrNull("kind") ?: EventKind.TODO.name,
            lastHandledOccurrence = obj.stringOrNull("lastHandledOccurrence")?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess },
            // 标签在这里也过一遍 normalize：手改过的备份可能塞进 20 个标签或带逗号的名字
            tags = com.morrowviento.morrow.domain.model.EventTags.normalize(
                obj.arrayOrNull("tags")?.mapNotNull { element ->
                    element.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asJsonPrimitive.asString }.getOrNull() }
                }.orEmpty(),
            ),
            // 归档时刻只接受能解析的值：坏掉就当未归档，而不是整条事件作废
            archivedAt = obj.stringOrNull("archivedAt")?.takeIf { parseTime(it) != null },
            durationMinutes = (obj.intOrNull("durationMinutes") ?: 0)
                .coerceIn(0, CountdownEvent.MAX_DURATION_MINUTES),
            checklist = decodeChecklist(obj.arrayOrNull("checklist")),
            milestones = com.morrowviento.morrow.domain.model.EventMilestones.normalize(
                obj.arrayOrNull("milestones")?.mapNotNull { element ->
                    element.takeIf { it.isJsonPrimitive }?.let {
                        runCatching { it.asJsonPrimitive.asInt }.getOrNull()
                    }
                }.orEmpty(),
            ),
        )
    }

    /**
     * 子任务：坏元素只丢自己，不让整条事件作废。
     *
     * 标题为空的项直接丢弃——它没有可展示的内容，留着只会在详情页出现一条点不动的空行。
     */
    private fun decodeChecklist(array: JsonArray?): List<BackupChecklistItem> {
        if (array == null) return emptyList()
        return array.mapNotNull { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val title = item.stringOrNull("title")?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            BackupChecklistItem(
                id = item.stringOrNull("id")?.takeIf { it.isNotBlank() } ?: "c-${title.hashCode()}",
                title = title,
                done = item.boolOrNull("done") ?: false,
            )
        }.take(com.morrowviento.morrow.domain.model.EventChecklist.MAX_COUNT)
    }

    private fun decodeReminders(array: JsonArray?): List<BackupReminder> {
        if (array == null) return emptyList()
        return array.mapNotNull { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val minutes = item.intOrNull("minutesBefore") ?: return@mapNotNull null
            if (minutes < 0) return@mapNotNull null
            BackupReminder(
                id = item.stringOrNull("id") ?: "r-$minutes",
                minutesBefore = minutes,
                enabled = item.boolOrNull("enabled") ?: true,
            )
        }.sortedByDescending { it.minutesBefore }.take(EventReminder.MAX_COUNT)
    }

    private fun decodeSettings(obj: JsonObject): BackupSettings = BackupSettings(
        themeMode = obj.stringOrNull("themeMode") ?: ThemeMode.SYSTEM.prefValue,
        sortMode = obj.stringOrNull("sortMode") ?: SortMode.DISTANCE.prefValue,
        // 旧备份没有 accent：留空串，由 toSettings 回落默认蓝
        accent = obj.stringOrNull("accent").orEmpty(),
        // 旧备份没有 glassFrost：留 -1，交给 toSettings 按旧四档 / 布尔反推
        glassFrost = obj.floatOrNull("glassFrost") ?: -1f,
        glassMaterial = obj.stringOrNull("glassMaterial").orEmpty(),
        reduceTransparency = obj.boolOrNull("reduceTransparency") ?: false,
        reduceMotion = obj.boolOrNull("reduceMotion") ?: false,
        backdropMode = obj.stringOrNull("backdropMode").orEmpty(),
        backdropAutoTint = obj.boolOrNull("backdropAutoTint") ?: false,
        backdropTintSeed = obj.longOrNull("backdropTintSeed") ?: 0L,
    )

    // ---------------------------------------------------------------- 工具

    /**
     * 解析时间字符串。
     *
     * 两种都收：ISO-8601 本地时刻（本版导出格式）与毫秒时间戳（早期字段可能的形态）。
     * 都失败返回 null，由调用方把该条事件算作坏数据跳过。
     */
    fun parseTime(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        runCatching { LocalDateTime.parse(raw) }.getOrNull()?.let { return it }
        raw.toLongOrNull()?.let { millis ->
            return runCatching {
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
            }.getOrNull()
        }
        return null
    }

    /** Gson 的 `getAsJsonArray` 在类型不符时直接抛异常，所以统一走这两个空安全入口。 */
    private fun JsonObject.arrayOrNull(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        return element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asInt
            primitive.isString -> primitive.asString.toIntOrNull()
            else -> null
        }
    }

    /** 浮点读取：`glassFrost` 是 0~1 的小数，用 [intOrNull] 会被截断成 0。 */
    private fun JsonObject.floatOrNull(key: String): Float? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asFloat
            primitive.isString -> primitive.asString.trim().toFloatOrNull()
            else -> null
        }
    }

    /** 长整型读取：`backdropTintSeed` 是 ARGB（可能超过 Int 上限），先按 Long 收。 */
    private fun JsonObject.longOrNull(key: String): Long? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asLong
            primitive.isString -> primitive.asString.trim().toLongOrNull()
            else -> null
        }
    }

    private fun JsonObject.boolOrNull(key: String): Boolean? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isString -> when (primitive.asString.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
            else -> null
        }
    }
}

/**
 * 备份与领域模型的互转。
 *
 * 放在 backup 包而不是复用 `EventMapper`：那一层的职责是「领域 ↔ Room 实体」，
 * 这里需要的是「领域 ↔ 可读文本」，两者对时间的处理不同（实体存毫秒、备份存 ISO 字符串）。
 */
object BackupMapper {

    fun of(event: CountdownEvent): BackupEvent = BackupEvent(
        id = event.id,
        title = event.title,
        targetDate = event.targetDate.toString(),
        category = event.category,
        note = event.note,
        icon = event.icon,
        direction = event.direction.name,
        reminders = event.reminders.map {
            BackupReminder(id = it.id, minutesBefore = it.minutesBefore, enabled = it.enabled)
        },
        isAllDay = event.isAllDay,
        isCompleted = event.isCompleted,
        isPinned = event.isPinned,
        repeatType = event.repeatType.name,
        repeatMonth = event.repeatMonth,
        repeatDay = event.repeatDay,
        repeatInterval = event.repeatInterval,
        repeatWeekdays = event.repeatWeekdays.sorted(),
        repeatOnLunar = event.repeatOnLunar,
        repeatUntil = event.repeatUntil?.toString(),
        endDate = event.endDate?.toString(),
        endTime = event.endTime?.toString(),
        createdAt = event.createdAt.toString(),
        kind = event.kind.name,
        lastHandledOccurrence = event.lastHandledOccurrence?.toString(),
        tags = event.tags,
        archivedAt = event.archivedAt?.toString(),
        durationMinutes = event.durationMinutes,
        checklist = event.checklist.map {
            BackupChecklistItem(id = it.id, title = it.title, done = it.done)
        },
        milestones = event.milestones,
    )

    fun toDomain(backup: BackupEvent): CountdownEvent? {
        val target = BackupCodec.parseTime(backup.targetDate) ?: return null
        val created = BackupCodec.parseTime(backup.createdAt) ?: target
        return CountdownEvent(
            id = backup.id,
            title = backup.title,
            targetDate = target,
            category = backup.category.ifBlank { CountdownEvent.DEFAULT_CATEGORY },
            note = backup.note,
            icon = backup.icon,
            direction = enumOrDefault(backup.direction, CountDirection.AUTO),
            // 刻意用 normalize：允许手工编辑过的备份把提醒写得乱序或超量
            reminders = EventReminder.normalize(
                backup.reminders.map {
                    EventReminder(id = it.id, minutesBefore = it.minutesBefore, enabled = it.enabled)
                },
            ),
            isAllDay = backup.isAllDay,
            isCompleted = backup.isCompleted,
            isPinned = backup.isPinned,
            repeatType = enumOrDefault(backup.repeatType, RepeatType.NONE),
            repeatMonth = backup.repeatMonth,
            repeatDay = backup.repeatDay,
            repeatInterval = backup.repeatInterval.coerceIn(1, CountdownEvent.MAX_REPEAT_INTERVAL),
            repeatWeekdays = backup.repeatWeekdays.filter { it in 1..7 }.toSortedSet(),
            repeatOnLunar = backup.repeatOnLunar,
            repeatUntil = backup.repeatUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            endDate = backup.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            endTime = backup.endTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() },
            createdAt = created,
            kind = enumOrDefault(backup.kind, EventKind.TODO),
            lastHandledOccurrence = backup.lastHandledOccurrence?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            tags = com.morrowviento.morrow.domain.model.EventTags.normalize(backup.tags),
            archivedAt = backup.archivedAt?.let { BackupCodec.parseTime(it) },
            durationMinutes = backup.durationMinutes.coerceIn(0, CountdownEvent.MAX_DURATION_MINUTES),
            checklist = com.morrowviento.morrow.domain.model.EventChecklist.normalize(
                backup.checklist.map {
                    com.morrowviento.morrow.domain.model.ChecklistItem(id = it.id, title = it.title, done = it.done)
                },
            ),
            milestones = com.morrowviento.morrow.domain.model.EventMilestones.normalize(backup.milestones),
        )
    }

    fun toSettings(backup: BackupSettings): AppSettings {
        val frost = if (backup.glassFrost >= 0f) {
            backup.glassFrost.coerceIn(0f, 1f)
        } else {
            legacyGlassFrost(backup.glassMaterial)
                ?: if (backup.reduceTransparency) 0.92f else GLASS_FROST_DEFAULT
        }
        return AppSettings(
            themeMode = ThemeMode.fromPref(backup.themeMode),
            sortMode = SortMode.fromPref(backup.sortMode),
            // 旧备份没有这一项 → fromPref(null) 回落默认蓝，不会把用户的颜色改掉
            accent = AccentColor.fromPref(backup.accent.ifBlank { null }),
            glassFrost = frost,
            reduceTransparency = frost >= 0.85f,
            reduceMotion = backup.reduceMotion,
            // 图片本体不随备份走，所以这里强制回落到丝缎；取色种子留着，
            // 导入后配色仍是那套「从原图取出来的」颜色，只是底图要重新选一张。
            backdrop = BackdropSettings(
                mode = BackdropMode.SATIN,
                image = null,
                autoTint = backup.backdropAutoTint,
                tintSeed = backup.backdropTintSeed,
            ),
        )
    }

    fun ofSettings(settings: AppSettings): BackupSettings = BackupSettings(
        themeMode = settings.themeMode.prefValue,
        sortMode = settings.sortMode.prefValue,
        accent = settings.accent.prefValue,
        glassFrost = settings.glassFrost,
        // 旧版本读的是这个字符串，写一个最接近的档位
        glassMaterial = when {
            settings.glassFrost < 0.14f -> "clear"
            settings.glassFrost < 0.45f -> "light"
            settings.glassFrost < 0.85f -> "standard"
            else -> "heavy"
        },
        reduceTransparency = settings.glassFrost >= 0.85f,
        reduceMotion = settings.reduceMotion,
        backdropMode = settings.backdrop.effectiveMode.prefValue,
        backdropAutoTint = settings.backdrop.autoTint,
        backdropTintSeed = settings.backdrop.tintSeed,
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
}

/**
 * 合并策略（导入时选择「合并」而非「覆盖」）。
 *
 * 冲突以**备份里的版本为准**：用户导入通常是想把另一台设备的改动带过来，
 * 保留本机较新反而是更意外的行为。同 id 的条目直接替换。
 */
object BackupMerge {

    fun events(current: List<CountdownEvent>, incoming: List<CountdownEvent>): List<CountdownEvent> {
        if (incoming.isEmpty()) return current
        val byId = LinkedHashMap<String, CountdownEvent>(current.size + incoming.size)
        current.forEach { byId[it.id] = it }
        incoming.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    /** 分类取并集，保持本机顺序，新分类按备份中的顺序追加。 */
    fun categories(current: List<String>, incoming: List<String>): List<String> {
        if (incoming.isEmpty()) return current
        val merged = LinkedHashSet<String>(current.size + incoming.size)
        merged.addAll(current)
        merged.addAll(incoming)
        return merged.toList()
    }
}
