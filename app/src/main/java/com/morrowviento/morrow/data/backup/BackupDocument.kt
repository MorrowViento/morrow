package com.morrowviento.morrow.data.backup

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.morrowviento.morrow.data.settings.AppSettings
import com.morrowviento.morrow.data.settings.GLASS_FROST_DEFAULT
import com.morrowviento.morrow.data.settings.legacyGlassFrost
import com.morrowviento.morrow.data.settings.ThemeMode
import com.morrowviento.morrow.domain.model.CountDirection
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventReminder
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
    val createdAt: String,
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
     * 玻璃厚度 0~1。**负数表示这份备份没有这个字段**（alpha7 及更早的备份），
     * 导入时依次按旧四档字符串、再按布尔反推，见 [BackupMapper.toSettings]。
     */
    val glassFrost: Float = -1f,
    /** 旧版四档字符串，只为兼容老备份的读取；导出时仍写一份，方便降级。 */
    val glassMaterial: String = "",
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

    /** 当前写入的版本。读到更高的版本说明备份来自更新的 App，拒绝而不是猜测。 */
    const val CURRENT_VERSION = 1

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
        addProperty("repeatType", event.repeatType)
        event.repeatMonth?.let { addProperty("repeatMonth", it) }
        event.repeatDay?.let { addProperty("repeatDay", it) }
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
        // 新旧字段都写：新版本读 glassFrost，旧版本读 glassMaterial
        addProperty("glassFrost", settings.glassFrost)
        addProperty("glassMaterial", settings.glassMaterial)
        addProperty("reduceTransparency", settings.reduceTransparency)
        addProperty("reduceMotion", settings.reduceMotion)
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
            createdAt = createdAtRaw,
        )
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
        // 旧备份没有 glassFrost：留 -1，交给 toSettings 按旧四档 / 布尔反推
        glassFrost = obj.floatOrNull("glassFrost") ?: -1f,
        glassMaterial = obj.stringOrNull("glassMaterial").orEmpty(),
        reduceTransparency = obj.boolOrNull("reduceTransparency") ?: false,
        reduceMotion = obj.boolOrNull("reduceMotion") ?: false,
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
        createdAt = event.createdAt.toString(),
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
            createdAt = created,
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
            glassFrost = frost,
            reduceTransparency = frost >= 0.85f,
            reduceMotion = backup.reduceMotion,
        )
    }

    fun ofSettings(settings: AppSettings): BackupSettings = BackupSettings(
        themeMode = settings.themeMode.prefValue,
        sortMode = settings.sortMode.prefValue,
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
