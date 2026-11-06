package com.rassvet.essential.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

data class CodeHighlightColors(
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val typeName: Color,
    val punctuation: Color,
)

fun defaultCodeHighlightColors(base: Color): CodeHighlightColors =
    CodeHighlightColors(
        plain = base,
        keyword = Color(0xFFC792EA),
        string = Color(0xFFA5D6FF),
        comment = Color(0xFF6A9955),
        number = Color(0xFFF78C6C),
        typeName = Color(0xFF82AAFF),
        punctuation = Color(0xFF89DDFF),
    )

private val LANG_ALIASES =
    mapOf(
        "js" to "javascript",
        "ts" to "typescript",
        "py" to "python",
        "rb" to "ruby",
        "sh" to "bash",
        "shell" to "bash",
        "zsh" to "bash",
        "yml" to "yaml",
        "md" to "markdown",
        "kt" to "kotlin",
        "kts" to "kotlin",
        "cs" to "csharp",
        "cxx" to "cpp",
        "h" to "c",
        "hpp" to "cpp",
    )

private val KEYWORDS: Map<String, Set<String>> =
    mapOf(
        "kotlin" to setOf(
            "fun", "val", "var", "class", "object", "interface", "enum", "data", "sealed",
            "when", "if", "else", "for", "while", "do", "return", "break", "continue",
            "try", "catch", "finally", "throw", "import", "package", "as", "is", "in",
            "true", "false", "null", "this", "super", "override", "private", "public",
            "protected", "internal", "open", "abstract", "suspend", "inline", "const",
            "lateinit", "companion", "init", "typealias", "where", "get", "set", "field",
            "by", "constructor", "operator", "infix", "tailrec", "crossinline", "noinline",
            "reified", "out", "in", "typealias", "expect", "actual",
        ),
        "java" to setOf(
            "class", "interface", "enum", "extends", "implements", "import", "package",
            "public", "private", "protected", "static", "final", "void", "int", "long",
            "float", "double", "boolean", "char", "byte", "short", "if", "else", "for",
            "while", "do", "switch", "case", "default", "break", "continue", "return",
            "try", "catch", "finally", "throw", "new", "this", "super", "true", "false",
            "null", "synchronized", "volatile", "transient", "abstract", "native", "strictfp",
        ),
        "python" to setOf(
            "def", "class", "if", "elif", "else", "for", "while", "break", "continue",
            "return", "import", "from", "as", "with", "try", "except", "finally", "raise",
            "pass", "lambda", "yield", "global", "nonlocal", "True", "False", "None",
            "and", "or", "not", "in", "is", "async", "await", "match", "case",
        ),
        "javascript" to setOf(
            "function", "const", "let", "var", "class", "extends", "import", "export",
            "from", "default", "if", "else", "for", "while", "do", "switch", "case",
            "break", "continue", "return", "try", "catch", "finally", "throw", "new",
            "this", "super", "typeof", "instanceof", "in", "of", "async", "await",
            "true", "false", "null", "undefined", "void", "delete", "yield", "static",
        ),
        "typescript" to setOf(
            "function", "const", "let", "var", "class", "extends", "implements", "import",
            "export", "from", "default", "if", "else", "for", "while", "do", "switch",
            "case", "break", "continue", "return", "try", "catch", "finally", "throw",
            "new", "this", "super", "typeof", "instanceof", "in", "of", "async", "await",
            "true", "false", "null", "undefined", "void", "delete", "type", "interface",
            "enum", "namespace", "module", "declare", "as", "is", "keyof", "readonly",
            "private", "public", "protected", "abstract", "static", "satisfies",
        ),
        "rust" to setOf(
            "fn", "let", "mut", "const", "static", "struct", "enum", "impl", "trait",
            "pub", "use", "mod", "crate", "if", "else", "match", "for", "while", "loop",
            "break", "continue", "return", "true", "false", "self", "Self", "super",
            "where", "async", "await", "move", "ref", "type", "dyn", "unsafe", "extern",
        ),
        "go" to setOf(
            "func", "var", "const", "type", "struct", "interface", "map", "chan",
            "package", "import", "if", "else", "for", "range", "switch", "case", "default",
            "break", "continue", "return", "go", "defer", "select", "fallthrough", "true",
            "false", "nil", "make", "new", "len", "cap", "append",
        ),
        "sql" to setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
            "DELETE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "AND", "OR", "NOT",
            "NULL", "IS", "AS", "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET",
            "CREATE", "TABLE", "ALTER", "DROP", "INDEX", "PRIMARY", "KEY", "FOREIGN",
            "REFERENCES", "UNIQUE", "DEFAULT", "TRUE", "FALSE", "CASE", "WHEN", "THEN",
            "ELSE", "END", "DISTINCT", "UNION", "ALL", "EXISTS", "BETWEEN", "LIKE", "IN",
        ),
        "bash" to setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case",
            "esac", "in", "function", "return", "export", "local", "readonly", "shift",
            "exit", "echo", "cd", "pwd", "source", "alias", "true", "false",
        ),
        "json" to emptySet(),
        "xml" to setOf("xml", "version", "encoding"),
        "html" to setOf(
            "html", "head", "body", "div", "span", "p", "a", "img", "script", "style",
            "link", "meta", "title", "ul", "ol", "li", "table", "tr", "td", "th",
            "form", "input", "button", "href", "src", "class", "id", "type",
        ),
        "cpp" to setOf(
            "if", "else", "for", "while", "do", "switch", "case", "default", "break",
            "continue", "return", "class", "struct", "enum", "namespace", "using",
            "typedef", "template", "typename", "public", "private", "protected", "virtual",
            "override", "const", "constexpr", "static", "extern", "inline", "void", "int",
            "long", "float", "double", "bool", "char", "true", "false", "nullptr", "new",
            "delete", "this", "sizeof", "include",
        ),
        "c" to setOf(
            "if", "else", "for", "while", "do", "switch", "case", "default", "break",
            "continue", "return", "struct", "enum", "typedef", "const", "static", "extern",
            "void", "int", "long", "float", "double", "char", "true", "false", "NULL",
            "sizeof", "include",
        ),
        "csharp" to setOf(
            "class", "interface", "struct", "enum", "namespace", "using", "public",
            "private", "protected", "internal", "static", "readonly", "const", "void",
            "int", "string", "bool", "if", "else", "for", "foreach", "while", "do",
            "switch", "case", "break", "continue", "return", "try", "catch", "finally",
            "throw", "new", "this", "base", "true", "false", "null", "async", "await",
            "var", "get", "set", "value", "where", "yield", "delegate", "event",
        ),
        "yaml" to setOf("true", "false", "null"),
        "markdown" to emptySet(),
    )

private val TYPE_HINTS =
    setOf(
        "String", "Int", "Long", "Float", "Double", "Boolean", "Char", "Byte", "Short",
        "List", "Map", "Set", "Array", "Unit", "Any", "Nothing", "Result", "Flow",
        "State", "MutableState", "Composable", "Modifier", "Context", "View", "Activity",
        "Fragment", "Bundle", "Intent", "Uri", "Color", "Text", "Column", "Row", "Box",
    )

fun normalizeCodeLanguage(raw: String): String {
    val key = raw.trim().lowercase()
    if (key.isEmpty()) return "text"
    return LANG_ALIASES[key] ?: key
}

fun displayLanguageLabel(raw: String): String {
    val norm = normalizeCodeLanguage(raw)
    return when (norm) {
        "text" -> "code"
        "csharp" -> "C#"
        "cpp" -> "C++"
        else -> norm.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

fun highlightCode(code: String, language: String, colors: CodeHighlightColors): AnnotatedString {
    val lang = normalizeCodeLanguage(language)
    if (lang == "text" || lang == "markdown") {
        return AnnotatedString(code, SpanStyle(color = colors.plain, fontFamily = FontFamily.Monospace))
    }
    val keywords = KEYWORDS[lang].orEmpty()
    val sqlMode = lang == "sql"
    return buildAnnotatedString {
        var i = 0
        val n = code.length
        while (i < n) {
            when {
                code.startsWith("//", i) || (lang != "python" && code.startsWith("/*", i)) -> {
                    val end =
                        if (code.startsWith("/*", i)) {
                            code.indexOf("*/", i + 2).let { if (it == -1) n else it + 2 }
                        } else {
                            code.indexOf('\n', i).let { if (it == -1) n else it }
                        }
                    withStyle(SpanStyle(color = colors.comment, fontFamily = FontFamily.Monospace)) {
                        append(code, i, end)
                    }
                    i = end
                }
                lang == "python" && code.startsWith("#", i) -> {
                    val end = code.indexOf('\n', i).let { if (it == -1) n else it }
                    withStyle(SpanStyle(color = colors.comment, fontFamily = FontFamily.Monospace)) {
                        append(code, i, end)
                    }
                    i = end
                }
                code[i] == '"' || code[i] == '\'' -> {
                    val quote = code[i]
                    var j = i + 1
                    while (j < n) {
                        if (code[j] == '\\') {
                            j += 2
                            continue
                        }
                        if (code[j] == quote) {
                            j++
                            break
                        }
                        j++
                    }
                    withStyle(SpanStyle(color = colors.string, fontFamily = FontFamily.Monospace)) {
                        append(code, i, j)
                    }
                    i = j
                }
                code[i].isDigit() ||
                    (code[i] == '.' && i + 1 < n && code[i + 1].isDigit()) -> {
                    var j = i
                    while (j < n && (code[j].isDigit() || code[j] == '.' || code[j] == 'x' || code[j] == 'X' ||
                            code[j] in 'a'..'f' || code[j] in 'A'..'F')) {
                        j++
                    }
                    withStyle(SpanStyle(color = colors.number, fontFamily = FontFamily.Monospace)) {
                        append(code, i, j)
                    }
                    i = j
                }
                code[i].isLetter() || code[i] == '_' -> {
                    var j = i
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    val word = code.substring(i, j)
                    val isKeyword =
                        if (sqlMode) keywords.contains(word.uppercase()) else keywords.contains(word)
                    val style =
                        when {
                            isKeyword ->
                                SpanStyle(
                                    color = colors.keyword,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            TYPE_HINTS.contains(word) ->
                                SpanStyle(color = colors.typeName, fontFamily = FontFamily.Monospace)
                            else -> SpanStyle(color = colors.plain, fontFamily = FontFamily.Monospace)
                        }
                    withStyle(style) { append(word) }
                    i = j
                }
                else -> {
                    val style =
                        if (code[i] in "{}[]();,.:+-*/%=<>!&|^~?") {
                            SpanStyle(color = colors.punctuation, fontFamily = FontFamily.Monospace)
                        } else {
                            SpanStyle(color = colors.plain, fontFamily = FontFamily.Monospace)
                        }
                    withStyle(style) { append(code[i]) }
                    i++
                }
            }
        }
    }
}


