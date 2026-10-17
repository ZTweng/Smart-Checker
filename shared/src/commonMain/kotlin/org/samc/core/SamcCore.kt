package org.samc.core

import kotlin.math.abs

enum class ActivityType { CONVENTIONAL, UNCONVENTIONAL }

data class CellGrid(private val values: Map<String, String?>) {
    fun value(ref: String): String? = values[ref.uppercase()]?.trim()?.takeIf { it.isNotEmpty() }

    fun contains(ref: String): Boolean = value(ref) != null

    companion object {
        fun of(vararg entries: Pair<String, String?>): CellGrid =
            CellGrid(entries.associate { it.first.uppercase() to it.second })
    }
}

data class CheckFailure(
    val title: String,
    val message: String,
    val cells: List<String> = emptyList()
) {
    val displayText: String get() = "$title：$message"
}

data class FileCheckResult(
    val fileName: String,
    val activityType: ActivityType,
    val passed: Boolean,
    val failures: List<CheckFailure> = emptyList(),
    val checkedAt: String = "",
    val annotatedFilePath: String? = null
)

data class CheckReport(
    val checkedFiles: Int,
    val passedFiles: Int,
    val failedFiles: Int,
    val results: List<FileCheckResult>,
    val elapsedMillis: Long = 0
) {
    val progress: Float get() = if (checkedFiles == 0) 0f else passedFiles.toFloat() / checkedFiles
}

object SamcRules {
    private val conventionalFields = linkedMapOf(
        "学生社团编号" to listOf("F4"),
        "学生社团名称" to listOf("J4"),
        "活动名称" to listOf("F5"),
        "活动时间" to listOf("K5"),
        "参与人员" to listOf("F6"),
        "活动地点" to listOf("K6"),
        "常规活动频率" to listOf("F7"),
        "本学期第几次常规活动" to listOf("K7"),
        "面向全校师生" to listOf("G8", "I8"),
        "参与人数" to listOf("K8"),
        "会员注册人数" to listOf("F9"),
        "会员参与人数" to listOf("J9"),
        "会员参与比例" to listOf("M9"),
        "违规举办活动" to listOf("G10", "G11"),
        "收到会员投诉" to listOf("L10", "L11"),
        "学生社团活动负责人姓名" to listOf("F13"),
        "学生社团活动负责人联系方式" to listOf("F14"),
        "学生社团活动负责人学号" to listOf("F15"),
        "姓名（主/副跟进员）" to listOf("K13"),
        "跟进员联系方式" to listOf("K14"),
        "跟进员学号" to listOf("K15"),
        "自办/联办" to listOf("F17"),
        "联办单位" to listOf("F18"),
        "外联商家及其合作情况" to listOf("J17"),
        "活动现场的秩序" to listOf("M19"),
        "工作人员的工作组织情况" to listOf("M24"),
        "（选做）" to listOf("M29", "M33"),
        "本次活动是否具有创意" to listOf("M39"),
        "会员对本次活动评价" to listOf("M44"),
        "采访现场会员，是否享受会员权益" to listOf("M49"),
        "会员参加热情" to listOf("M53"),
        "活动缺点" to listOf("J57"),
        "活动优点" to listOf("H58"),
        "活动总体评分" to listOf("M59"),
        "照片1" to listOf("F60"),
        "照片2" to listOf("I60"),
        "照片3" to listOf("L60"),
        "跟进员总结" to listOf("F65"),
        "跟进员评分" to listOf("M69"),
        "学生社团活动负责人总结" to listOf("F70"),
        "学生社团活动负责人评分" to listOf("M74"),
        "活动总分" to listOf("F75"),
        "评分日期" to listOf("L75")
    )

    private val unconventionalFields = conventionalFields.toMutableMap().apply {
        remove("常规活动频率")
        put("活动形式", listOf("F7"))
        remove("本学期第几次常规活动")
        put("学年第几次非常规活动", listOf("K7"))
        replace("工作人员的工作组织情况", listOf("M23"))
        replace("（选做）", listOf("M28", "M32"))
        replace("本次活动是否具有创意", listOf("M37"))
        remove("会员对本次活动评价")
        put("会员觉得这次活动与自己预期效果相比是否用心", listOf("M42"))
        remove("采访现场会员，是否享受会员权益")
        put("会员参加活动是否有收获", listOf("M47"))
        remove("会员参加热情")
        put("会员认为本次活动是否具有实际意义", listOf("M52"))
        replace("活动缺点", listOf("J56"))
        replace("活动优点", listOf("H57"))
        replace("活动总体评分", listOf("M58"))
        replace("照片1", listOf("F61"))
        replace("照片2", listOf("I61"))
        replace("照片3", listOf("L61"))
        replace("跟进员总结", listOf("F66"))
        replace("跟进员评分", listOf("M70"))
        replace("学生社团活动负责人总结", listOf("F71"))
        replace("学生社团活动负责人评分", listOf("M75"))
        replace("活动总分", listOf("F76"))
        replace("评分日期", listOf("L76"))
    }

    private val conventionalScores = mapOf(
        "M19" to mapOf("A" to 10.0, "B" to 8.0, "C" to 5.0, "D" to 0.0),
        "M24" to mapOf("A" to 10.0, "B" to 7.0, "C" to 4.0, "D" to 0.0),
        "M29" to mapOf("A" to 10.0, "B" to 7.0, "C" to 0.0),
        "M33" to mapOf("A" to 10.0, "B" to 7.0, "C" to 4.0, "D" to 0.0),
        "M39" to mapOf("A" to 10.0, "B" to 7.0, "C" to 4.0, "D" to 1.0),
        "M44" to mapOf("A" to 10.0, "B" to 8.0, "C" to 4.0, "D" to 0.0),
        "M49" to mapOf("A" to 10.0, "B" to 6.0, "C" to 2.0),
        "M53" to mapOf("A" to 10.0, "B" to 5.0, "C" to 2.0)
    )

    private val unconventionalScores = mapOf(
        "M19" to mapOf("A" to 10.0, "B" to 5.0, "C" to 0.0),
        "M23" to mapOf("A" to 10.0, "B" to 7.0, "C" to 4.0, "D" to 0.0),
        "M28" to mapOf("A" to 10.0, "B" to 7.0, "C" to 0.0),
        "M32" to mapOf("A" to 10.0, "B" to 7.0, "C" to 4.0, "D" to 0.0),
        "M37" to mapOf("A" to 10.0, "B" to 7.0, "C" to 4.0, "D" to 1.0),
        "M42" to mapOf("A" to 10.0, "B" to 8.0, "C" to 4.0, "D" to 0.0),
        "M47" to mapOf("A" to 10.0, "B" to 8.0, "C" to 4.0, "D" to 0.0),
        "M52" to mapOf("A" to 10.0, "B" to 5.0, "C" to 0.0),
        "M58" to mapOf("A" to 10.0, "B" to 8.0, "C" to 4.0, "D" to 0.0)
    )

    private val requiredNonEmpty = setOf(
        "学生社团活动负责人学号", "跟进员学号", "活动缺点", "活动优点",
        "照片1", "照片2", "照片3", "跟进员总结", "学生社团活动负责人总结"
    )

    fun checkFile(fileName: String, grid: CellGrid, activityType: ActivityType): FileCheckResult {
        val fields = if (activityType == ActivityType.CONVENTIONAL) conventionalFields else unconventionalFields
        val failures = mutableListOf<CheckFailure>()
        checkFilename(fileName, grid)?.let(failures::add)

        fields.forEach { (title, cells) ->
            val values = cells.map(grid::value)
            if (values.all { it == null }) {
                failures += CheckFailure(title, "单元格 ${cells.joinToString("、")} 均为空", cells)
            } else {
                validateField(title, values, cells, grid, activityType)?.let(failures::add)
            }
        }
        return FileCheckResult(fileName, activityType, failures.isEmpty(), failures)
    }

    private fun checkFilename(fileName: String, grid: CellGrid): CheckFailure? {
        val club = grid.value("J4") ?: return CheckFailure(
            "文件名", "J4（学生社团名称）为空或无效，无法检查文件名", listOf("J4")
        )
        if (club !in fileName) {
            return CheckFailure("文件名", "应包含学生社团名称“$club”", listOf("J4"))
        }
        if (club != "广州大学模拟政协学生社团" && "学生社团" in fileName) {
            return CheckFailure("文件名", "文件名中不应重复包含“学生社团”", listOf("J4"))
        }
        return null
    }

    private fun validateField(
        title: String,
        values: List<String?>,
        cells: List<String>,
        grid: CellGrid,
        activityType: ActivityType
    ): CheckFailure? {
        val filled = values.filterNotNull()
        if (title in requiredNonEmpty && filled.isEmpty()) {
            return CheckFailure(title, "不能为空", cells)
        }
        if (title == "学生社团名称" && (filled.isEmpty() || "学生" !in filled.first())) {
            return CheckFailure(title, "名称必须包含“学生”二字", cells)
        }
        if (title in setOf("面向全校师生", "违规举办活动", "收到会员投诉") && filled.size != 1) {
            return CheckFailure(title, "选项只能填写一项，当前填写 ${filled.size} 项", cells)
        }
        if (title == "（选做）" && filled.size != 1) {
            return CheckFailure(title, "二选一题必须且只能填写一项", cells)
        }
        if (title == "会员参与比例") {
            return ratioFailure(grid, cells)
        }

        val letterFields = if (activityType == ActivityType.CONVENTIONAL) {
            setOf("会员参加热情", "工作人员的工作组织情况", "（选做）", "本次活动是否具有创意",
                "会员对本次活动评价", "采访现场会员，是否享受会员权益", "活动现场的秩序")
        } else {
            setOf("活动现场的秩序", "工作人员的工作组织情况", "（选做）", "本次活动是否具有创意",
                "会员觉得这次活动与自己预期效果相比是否用心",
                "会员参加活动是否有收获", "会员认为本次活动是否具有实际意义")
        }
        if (title in letterFields && filled.firstOrNull()?.uppercase() !in setOf("A", "B", "C", "D")) {
            return CheckFailure(title, "必须填写英文 A/B/C/D", cells)
        }
        if (title == "活动总分") return totalScoreFailure(grid, activityType, cells)
        return null
    }

    private fun ratioFailure(grid: CellGrid, cells: List<String>): CheckFailure? {
        val registered = grid.value("F9")?.toDoubleOrNull()
        val member = grid.value("J9")?.toDoubleOrNull()
        val actual = grid.value("M9")?.removeSuffix("%")?.toDoubleOrNull()?.let { if (it > 1) it / 100 else it }
        if (registered == null || member == null || actual == null) {
            return CheckFailure("会员参与比例", "F9、J9、M9 必须为有效数字", listOf("F9", "J9", "M9"))
        }
        if (registered == 0.0) return CheckFailure("会员参与比例", "会员注册人数不能为 0", listOf("F9"))
        val expected = member / registered
        return if (abs(actual - expected) > 0.01) {
            CheckFailure("会员参与比例", "应为 %.2f，实际为 %.2f".format(expected, actual), cells)
        } else null
    }

    private fun totalScoreFailure(grid: CellGrid, activityType: ActivityType, cells: List<String>): CheckFailure? {
        val actual = grid.value(cells.first())?.toDoubleOrNull()
            ?: return CheckFailure("活动总分", "必须为有效数字", cells)
        val groups = if (activityType == ActivityType.CONVENTIONAL) {
            listOf(listOf("M19"), listOf("M24"), listOf("M29", "M33"), listOf("M39"),
                listOf("M44"), listOf("M49"), listOf("M53"), listOf("M59"), listOf("M69"), listOf("M74"))
        } else {
            listOf(listOf("M19"), listOf("M23"), listOf("M28", "M32"), listOf("M37"),
                listOf("M42"), listOf("M47"), listOf("M52"), listOf("M58"), listOf("M70"), listOf("M75"))
        }
        val direct = if (activityType == ActivityType.CONVENTIONAL) setOf("M59", "M69", "M74") else setOf("M70", "M75")
        val scores = if (activityType == ActivityType.CONVENTIONAL) conventionalScores else unconventionalScores
        var expected = 0.0
        groups.forEach { group ->
            val selected = group.mapNotNull { cell -> grid.value(cell)?.let { cell to it } }
            if (selected.size != 1) {
                return CheckFailure("活动总分", "评分组 ${group.joinToString("、")} 必须且只能填写一项", group)
            }
            val (cell, raw) = selected.single()
            expected += if (cell in direct) raw.toDoubleOrNull()
                ?: return CheckFailure("活动总分", "$cell 必须为数字", listOf(cell))
            else scores[cell]?.get(raw.uppercase())
                ?: return CheckFailure("活动总分", "$cell 必须填写有效的 A/B/C/D", listOf(cell))
        }
        return if (abs(actual - expected) > 0.01) {
            CheckFailure("活动总分", "应为 $expected，实际为 $actual", cells)
        } else null
    }
}
