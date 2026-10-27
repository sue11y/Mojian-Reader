package com.mojian.reader.data

data class TxtTocRule(
    val id: Int,
    val name: String,
    val enabled: Boolean,
    val rule: String
) {
    val compiled: Regex?
        get() = runCatching { Regex(rule) }.getOrNull()
}

object TxtTocRuleLibrary {
    val rules: List<TxtTocRule> = listOf(
        TxtTocRule(0, "第X章", true, """(?m)^[ \t　]{0,4}第[0-9零一二两三四五六七八九十百千万〇]{1,10}[章回节卷部篇][ 　\t]{0,4}.{0,30}$"""),
        TxtTocRule(1, "序章/楔子/尾声", true, """(?m)^[ \t　]{0,4}(?:序章|序言|卷首语|扉页|楔子|引子|终章|后记|尾声|番外)(?:[ 　\t].{0,30})?$"""),
        TxtTocRule(2, "数字分隔符", true, """(?m)^[ \t　]{0,4}[0-9]{1,5}[：:,.，、_—\-].{1,30}$"""),
        TxtTocRule(3, "中文数字分隔符", true, """(?m)^[ \t　]{0,4}[〇零一二两三四五六七八九十百千万]{1,8}[ 、_—\-].{1,30}$"""),
        TxtTocRule(4, "Chapter/Section", true, """(?m)^[ \t　]{0,4}(?:[Cc]hapter|[Ss]ection|[Pp]art|[Ee]pisode|[Nn][oO]\.)[ ]{0,4}[0-9]{1,4}.{0,30}$"""),
        TxtTocRule(5, "特殊符号标题", true, """(?m)^[ \t　]{0,4}[【〔〖「『〈［\[(（](?:第|[Cc]hapter).{0,24}$"""),
        TxtTocRule(6, "卷/章序号", true, """(?m)^[ \t　]{0,4}[卷章][0-9零一二两三四五六七八九十百千万〇]{1,8}[ 　\t]{0,4}.{0,30}$"""),
        TxtTocRule(7, "正文序号", true, """(?m)^[ \t　]{0,4}正文[ 　\t]{1,4}.{0,20}$"""),
        TxtTocRule(8, "纯数字标题", false, """(?m)^[ \t　]{0,4}[0-9]{1,5}\.?[ 　\t]{0,4}$"""),
        TxtTocRule(9, "章节前缀", false, """(?m)^[ \t　]{0,4}(?:简介|文案|前言|序章|楔子|正文|终章|后记|尾声|番外)[ 　\t].{0,30}$""")
    )

    fun firstMatching(text: String): TxtTocRule? = rules
        .filter { it.enabled }
        .firstOrNull { rule ->
            val matches = rule.compiled?.findAll(text)?.take(3)?.toList().orEmpty()
            matches.size >= 2
        }
}
