package com.jiuxina.ying.data.category

import com.google.gson.JsonArray
import com.google.gson.JsonParser

/**
 * 分类列表的 JSON 编解码（存 DataStore 单个字符串键，便于 M6 备份直接透传）。
 *
 * 格式：`["生活","工作"]`。
 */
object CategoryCodec {

    fun encode(names: List<String>): String =
        JsonArray().apply { names.forEach { add(it) } }.toString()

    /**
     * 解码。
     *
     * @return `null` 表示内容无法解析——调用方应回落到默认值，而不是把列表清空；
     *   合法的空数组会返回空列表（由上层规则决定回落到预置分类）。
     */
    fun decode(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return null
        if (!root.isJsonArray) return null
        return root.asJsonArray.mapNotNull { element ->
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        }
    }
}
