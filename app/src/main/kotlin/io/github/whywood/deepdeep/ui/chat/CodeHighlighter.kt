package io.github.whywood.deepdeep.ui.chat

internal enum class CodeTokenType {
    KEYWORD,
    STRING,
    COMMENT,
    NUMBER,
    ANNOTATION,
    FUNCTION,
    TYPE,
}

internal data class CodeToken(val start: Int, val end: Int, val type: CodeTokenType)

private class CodeLanguage(
    val lineComments: List<String> = emptyList(),
    val blockComments: List<Pair<String, String>> = emptyList(),
    val strings: List<String> = listOf("\""),
    val multilineStrings: Set<String> = emptySet(),
    val keywords: Set<String> = emptySet(),
    val annotations: Boolean = false,
    val typeByCapital: Boolean = false,
    val caseSensitive: Boolean = true,
)

private fun words(value: String): Set<String> = value.split(' ').filter { it.isNotEmpty() }.toSet()

private val Kotlin = CodeLanguage(
    lineComments = listOf("//"),
    blockComments = listOf("/*" to "*/"),
    strings = listOf("\"\"\"", "\"", "'"),
    multilineStrings = setOf("\"\"\""),
    keywords = words(
        "fun val var if else when for while do return break continue class object interface data sealed enum import " +
            "package try catch finally throw in is as out by init constructor override open private public internal " +
            "protected companion lateinit suspend inline noinline crossinline reified typealias this super null true " +
            "false abstract operator infix tailrec external const vararg where",
    ),
    annotations = true,
    typeByCapital = true,
)

private val Java = CodeLanguage(
    lineComments = listOf("//"),
    blockComments = listOf("/*" to "*/"),
    strings = listOf("\"", "'"),
    keywords = words(
        "public private protected class interface enum extends implements import package static final void int long " +
            "double float boolean char byte short if else for while do return break continue switch case default try " +
            "catch finally throw throws new this super null true false abstract synchronized volatile transient native " +
            "instanceof record sealed permits strictfp assert",
    ),
    annotations = true,
    typeByCapital = true,
)

private val ScriptKeywords = words(
    "const let var function return if else for while do switch case break continue new this typeof instanceof in of " +
        "class extends super import export default from as async await yield try catch finally throw delete void null " +
        "undefined true false get set require module",
)

private val JavaScript = CodeLanguage(
    lineComments = listOf("//"),
    blockComments = listOf("/*" to "*/"),
    strings = listOf("`", "\"", "'"),
    multilineStrings = setOf("`"),
    keywords = ScriptKeywords,
    typeByCapital = false,
)

private val TypeScript = CodeLanguage(
    lineComments = listOf("//"),
    blockComments = listOf("/*" to "*/"),
    strings = listOf("`", "\"", "'"),
    multilineStrings = setOf("`"),
    keywords = ScriptKeywords + words(
        "interface type enum implements private public protected readonly namespace declare abstract static keyof infer " +
            "satisfies is unknown never any string number boolean object symbol",
    ),
    typeByCapital = true,
)

private val Python = CodeLanguage(
    lineComments = listOf("#"),
    strings = listOf("\"\"\"", "'''", "\"", "'"),
    multilineStrings = setOf("\"\"\"", "'''"),
    keywords = words(
        "def class return if elif else for while break continue import from as pass raise try except finally with " +
            "lambda yield global nonlocal assert del in is not and or None True False async await match case",
    ),
    typeByCapital = true,
)

private val Bash = CodeLanguage(
    lineComments = listOf("#"),
    strings = listOf("\"", "'"),
    keywords = words(
        "if then else elif fi for while until do done case esac function in return local export readonly declare echo " +
            "cd pwd exit source alias unset set shift trap true false",
    ),
)

private val Json = CodeLanguage(
    strings = listOf("\""),
    keywords = words("true false null"),
)

private val Markup = CodeLanguage(
    blockComments = listOf("<!--" to "-->"),
    strings = listOf("\"", "'"),
)

private val Css = CodeLanguage(
    blockComments = listOf("/*" to "*/"),
    strings = listOf("\"", "'"),
)

private val Sql = CodeLanguage(
    lineComments = listOf("--"),
    blockComments = listOf("/*" to "*/"),
    strings = listOf("'", "\""),
    keywords = words(
        "select from where insert into values update set delete create table drop alter add index join inner left " +
            "right outer full on group by order having limit offset union all distinct as and or not null is like in " +
            "exists between case when then else end primary key foreign references default asc desc",
    ),
    caseSensitive = false,
)

private val Yaml = CodeLanguage(
    lineComments = listOf("#"),
    strings = listOf("\"", "'"),
    keywords = words("true false null yes no on off"),
    caseSensitive = false,
)

private val Toml = CodeLanguage(
    lineComments = listOf("#"),
    strings = listOf("\"\"\"", "\"", "'"),
    multilineStrings = setOf("\"\"\""),
    keywords = words("true false"),
    caseSensitive = false,
)

private val Go = CodeLanguage(
    lineComments = listOf("//"),
    blockComments = listOf("/*" to "*/"),
    strings = listOf("`", "\"", "'"),
    multilineStrings = setOf("`"),
    keywords = words(
        "package import func var const type struct interface map chan go defer return if else for range switch case " +
            "default break continue fallthrough select nil true false make new len cap append string int int64 " +
            "float64 bool byte rune error",
    ),
    typeByCapital = true,
)

private val Rust = CodeLanguage(
    lineComments = listOf("//"),
    blockComments = listOf("/*" to "*/"),
    strings = listOf("\"", "'"),
    keywords = words(
        "fn let mut const static struct enum trait impl pub use mod match if else for while loop return break " +
            "continue where self Self super crate as in ref move unsafe async await dyn box true false Some None Ok Err",
    ),
    typeByCapital = true,
)

private val CKeywords = words(
    "int long short char float double void bool unsigned signed struct union enum typedef static extern const volatile " +
        "register return if else for while do switch case default break continue goto sizeof auto inline",
)

private val C = CodeLanguage(
    lineComments = listOf("//"),
    blockComments = listOf("/*" to "*/"),
    strings = listOf("\"", "'"),
    keywords = CKeywords,
    typeByCapital = true,
)

private val Cpp = CodeLanguage(
    lineComments = listOf("//"),
    blockComments = listOf("/*" to "*/"),
    strings = listOf("\"", "'"),
    keywords = CKeywords + words(
        "class public private protected virtual override final new delete nullptr null true false namespace using " +
            "template typename constexpr explicit friend operator this",
    ),
    typeByCapital = true,
)

private val Swift = CodeLanguage(
    lineComments = listOf("//"),
    blockComments = listOf("/*" to "*/"),
    strings = listOf("\"\"\"", "\"", "'"),
    multilineStrings = setOf("\"\"\""),
    keywords = words(
        "func let var if else guard for while repeat switch case default return break continue class struct enum " +
            "protocol extension import init deinit self Self nil true false public private internal fileprivate open " +
            "static final override mutating async await throws try catch defer typealias where",
    ),
    annotations = true,
    typeByCapital = true,
)

private val Ruby = CodeLanguage(
    lineComments = listOf("#"),
    strings = listOf("\"", "'"),
    keywords = words(
        "def end class module if elsif else unless while until for do return break next yield begin rescue ensure " +
            "raise nil true false self super attr_accessor attr_reader attr_writer require include extend",
    ),
    typeByCapital = true,
)

private val languages: Map<String, CodeLanguage> = buildMap {
    put("kotlin", Kotlin)
    put("kt", Kotlin)
    put("kts", Kotlin)
    put("java", Java)
    put("js", JavaScript)
    put("javascript", JavaScript)
    put("jsx", JavaScript)
    put("ts", TypeScript)
    put("typescript", TypeScript)
    put("tsx", TypeScript)
    put("python", Python)
    put("py", Python)
    put("bash", Bash)
    put("sh", Bash)
    put("shell", Bash)
    put("zsh", Bash)
    put("json", Json)
    put("xml", Markup)
    put("html", Markup)
    put("htm", Markup)
    put("css", Css)
    put("scss", Css)
    put("sql", Sql)
    put("yaml", Yaml)
    put("yml", Yaml)
    put("toml", Toml)
    put("go", Go)
    put("golang", Go)
    put("rust", Rust)
    put("rs", Rust)
    put("c", C)
    put("h", C)
    put("cpp", Cpp)
    put("c++", Cpp)
    put("cc", Cpp)
    put("cxx", Cpp)
    put("hpp", Cpp)
    put("swift", Swift)
    put("ruby", Ruby)
    put("rb", Ruby)
}

private val numberPattern = Regex(
    "(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?[fFdDlLuU]*|\\.\\d[\\d_]*)",
)
private val identifierPattern = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
private val annotationPattern = Regex("@[A-Za-z_][A-Za-z0-9_.]*")

internal fun highlightCode(code: String, language: String?): List<CodeToken> {
    if (code.isEmpty()) return emptyList()
    val key = language?.trim()?.lowercase().orEmpty()
    if (key == "diff" || key == "patch") return diffTokens(code)
    val spec = languages[key] ?: return emptyList()
    return lex(code, spec)
}

private fun lex(code: String, language: CodeLanguage): List<CodeToken> {
    val tokens = mutableListOf<CodeToken>()
    val length = code.length
    var index = 0
    while (index < length) {
        var matched = false
        for (marker in language.lineComments) {
            if (!code.startsWith(marker, index)) continue
            var end = code.indexOf('\n', index)
            if (end < 0) end = length
            tokens += CodeToken(index, end, CodeTokenType.COMMENT)
            index = end
            matched = true
            break
        }
        if (matched) continue
        for ((open, close) in language.blockComments) {
            if (!code.startsWith(open, index)) continue
            val closeIndex = code.indexOf(close, index + open.length)
            val end = if (closeIndex < 0) length else closeIndex + close.length
            tokens += CodeToken(index, end, CodeTokenType.COMMENT)
            index = end
            matched = true
            break
        }
        if (matched) continue
        val delimiter = language.strings.firstOrNull { code.startsWith(it, index) }
        if (delimiter != null) {
            val end = stringEnd(code, index, delimiter, delimiter in language.multilineStrings)
            tokens += CodeToken(index, end, CodeTokenType.STRING)
            index = end
            continue
        }
        val char = code[index]
        if (char.isDigit() || (char == '.' && index + 1 < length && code[index + 1].isDigit())) {
            val number = numberPattern.matchAt(code, index)
            if (number != null && number.value.isNotEmpty()) {
                tokens += CodeToken(index, index + number.value.length, CodeTokenType.NUMBER)
                index += number.value.length
                continue
            }
        }
        if (char == '@' && language.annotations) {
            val annotation = annotationPattern.matchAt(code, index)
            if (annotation != null) {
                tokens += CodeToken(index, index + annotation.value.length, CodeTokenType.ANNOTATION)
                index += annotation.value.length
                continue
            }
        }
        if (char.isLetter() || char == '_' || char == '$') {
            val identifier = identifierPattern.matchAt(code, index)
            if (identifier != null) {
                val word = identifier.value
                val end = index + word.length
                val lookup = if (language.caseSensitive) word else word.lowercase()
                val type = when {
                    lookup in language.keywords -> CodeTokenType.KEYWORD
                    end < length && code[end] == '(' -> CodeTokenType.FUNCTION
                    language.typeByCapital && word[0].isUpperCase() -> CodeTokenType.TYPE
                    else -> null
                }
                if (type != null) tokens += CodeToken(index, end, type)
                index = end
                continue
            }
        }
        index++
    }
    return tokens
}

private fun stringEnd(code: String, start: Int, delimiter: String, multiline: Boolean): Int {
    var index = start + delimiter.length
    val length = code.length
    while (index < length) {
        val char = code[index]
        if (char == '\\' && index + 1 < length) {
            index += 2
            continue
        }
        if (code.startsWith(delimiter, index)) return index + delimiter.length
        if (!multiline && char == '\n') return index
        index++
    }
    return length
}

private fun diffTokens(code: String): List<CodeToken> {
    val tokens = mutableListOf<CodeToken>()
    val length = code.length
    var lineStart = 0
    while (lineStart < length) {
        var lineEnd = code.indexOf('\n', lineStart)
        if (lineEnd < 0) lineEnd = length
        val type = when {
            code.startsWith("@@", lineStart) -> CodeTokenType.KEYWORD
            code.startsWith("+++", lineStart) || code.startsWith("---", lineStart) -> CodeTokenType.COMMENT
            code.startsWith("+", lineStart) -> CodeTokenType.STRING
            code.startsWith("-", lineStart) -> CodeTokenType.COMMENT
            else -> null
        }
        if (type != null && lineEnd > lineStart) tokens += CodeToken(lineStart, lineEnd, type)
        lineStart = lineEnd + 1
    }
    return tokens
}
