package com.mojian.reader.data

data class ReplaceRule(
    val pattern: String,
    val replacement: String = ""
) {
    fun apply(text: String): String = runCatching {
        Regex(pattern, RegexOption.MULTILINE).replace(text, replacement)
    }.getOrDefault(text)
}

object ContentCleaner {
    val defaultRules: List<ReplaceRule> = listOf(
        ReplaceRule("""(?im)^\s*(?:最新章节|请收藏|手机阅读|本站首发|免费阅读|推荐阅读|无弹窗|广告).{0,40}$"""),
        ReplaceRule("""(?im)^\s*(?:未完待续|本章未完|点击下一页继续阅读|如有问题请及时反馈).{0,40}$"""),
        ReplaceRule("""(?im)^\s*(?:返回首页|上一页|下一页|目录|书签|加入书架|收藏本站|桌面版|手机版).{0,40}$"""),
        ReplaceRule("""(?i)[\w\-\u4e00-\u9fa5/\\_]{0,80}(?:微信|QQ|Screenshot|IMG|image|截图|图片)[^\s]{0,60}\.(?:jpg|jpeg|png|gif|webp)"""),
        ReplaceRule("""[ \t]{2,}""", " "),
        ReplaceRule("""\n{4,}""", "\n\n\n")
    )

    fun clean(text: String, rules: List<ReplaceRule> = defaultRules): String {
        var result = text
        rules.forEach { rule -> result = rule.apply(result) }
        return result
    }

    fun parseLegacyRules(raw: String): List<ReplaceRule> = raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("//") }
        .mapNotNull { line ->
            val parts = line.split("##", limit = 2)
            val pattern = parts.getOrNull(0)?.trim().orEmpty()
            if (pattern.isEmpty()) null
            else ReplaceRule(pattern, parts.getOrNull(1).orEmpty())
        }
        .toList()
}
