package com.jiuxina.ying.domain.model

/**
 * 分类的领域常量与约束。
 *
 * 身份策略：分类以「名字」作为唯一标识，事件表继续存字符串。
 * 重命名时级联更新事件行，用户观察到的效果与 id 引用一致；
 * 这样旧数据无需二次回填，也不必为一个展示性字段引入 Room schema 变更。
 */
object EventCategory {

    /** 首次启动时的预置分类。可被改名或删除，删除后不会自动复原。 */
    val PRESETS = listOf("生活", "生日", "纪念", "节日", "工作", "学习", "旅行")

    /** 名称长度上限：超出会撑破表单 chips 与列表行布局。 */
    const val MAX_NAME_LENGTH = 12
}
