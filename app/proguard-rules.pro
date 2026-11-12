# ---------------------------------------------------------------------------
# Morrow 自己的 keep 规则
#
# Glasmio 的 consumer rules 随 AAR 自动带入，这里只写本项目的部分。
# 原则：**只保留真正靠反射读写的形状**，其余尽量让 R8 去裁。
# ---------------------------------------------------------------------------

# Gson 按「字段名」序列化。EventMapper.ReminderJson 会被写进 Room 的 reminders 列，
# 混淆把字段名改掉之后，下一个版本（映射变了）就再也反序列化不出来——
# 表现是升级后所有事件的提醒静默消失（decodeReminders 里 runCatching 吃掉了异常）。
-keep class com.morrowviento.morrow.data.db.EventMapper$ReminderJson { *; }

# 迁移报告只写不读（仅用于诊断展示），但保留字段名便于用户/开发者直接看懂
-keepclassmembers class com.morrowviento.morrow.data.legacy.** {
    <fields>;
}

# 小部件由系统按清单实例化，必须保留无参构造
-keep class * extends android.appwidget.AppWidgetProvider {
    public <init>();
}

# 保留行号表（崩溃栈可读）；不保留源码文件名，体积影响可以忽略
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
