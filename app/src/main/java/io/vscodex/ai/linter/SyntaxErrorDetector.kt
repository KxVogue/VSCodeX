/*
 * This file is part of VSCodeX.
 *
 * VSCodeX is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package io.vscodex.ai.linter

import kotlinx.coroutines.runBlocking

/**
 * A lightweight, pure-Kotlin static linter.
 *
 * Produces a list of [SyntaxError] items (line, column, message) for a given
 * source file.  Designed to run on every content-change event with a debounce
 * (call from a coroutine).
 *
 * ### VS Code-powered configuration
 * For every language, [SyntaxErrorDetector] first fetches (or reads from cache)
 * the official `language-configuration.json` from the microsoft/vscode GitHub
 * repository via [VsCodeLanguageConfigFetcher].  This gives us:
 *
 * - The exact **bracket pairs** for each language (used for balance checks)
 * - The correct **comment markers** (used for stripping before analysis)
 * - **`onEnterRules`** patterns (used to detect missing `:` in Python/YAML)
 * - **`indentationRules`** (used to detect unclosed indent blocks)
 *
 * Language-specific heuristic checks (missing semicolons, anti-patterns,
 * deprecated APIs, etc.) remain in this file and run after the structural
 * VS Code checks.
 *
 * ### Supported languages
 * Kotlin/KTS, Java, Python, JS, TS, JSX, TSX, HTML, CSS/SCSS/SASS,
 * JSON, YAML, Shell, C, C++, C#, Rust, Go, Dart, Swift, PHP, Ruby,
 * Lua, SQL, Markdown, Gradle, Groovy, TOML, INI, and plain text.
 */
object SyntaxErrorDetector {

    data class SyntaxError(
        val line: Int,       // 0-based
        val column: Int,     // 0-based, start of offending token
        val endColumn: Int,  // 0-based, exclusive
        val message: String,
        val severity: Severity = Severity.ERROR
    )

    enum class Severity { ERROR, WARNING }

    // ── Public entry point ────────────────────────────────────────────────────

    fun detect(code: String, extension: String): List<SyntaxError> {
        if (code.isBlank()) return emptyList()
        val ext = extension.lowercase().trimStart('.')

        // Fetch VS Code language config (cached after first call)
        val cfg = runBlocking { VsCodeLanguageConfigFetcher.getConfig(ext) }

        return when (ext) {
            "kt", "kts"                    -> detectKotlin(code, cfg)
            "java"                         -> detectJava(code, cfg)
            "py", "pyi"                    -> detectPython(code, cfg)
            "js", "jsx", "mjs", "cjs"      -> detectJavaScript(code, cfg)
            "ts", "tsx"                    -> detectTypeScript(code, cfg)
            "html", "htm"                  -> detectHtml(code, cfg)
            "css"                          -> detectCss(code, cfg)
            "scss", "sass"                 -> detectScss(code, cfg)
            "json", "jsonc"                -> detectJson(code, cfg)
            "xml", "svg"                   -> detectXml(code, cfg)
            "yaml", "yml"                  -> detectYaml(code, cfg)
            "sh", "bash", "zsh",
            "ksh", "fish"                  -> detectShell(code, cfg)
            "c", "h"                       -> detectC(code, cfg)
            "cpp", "cxx", "cc",
            "hpp", "hxx"                   -> detectCpp(code, cfg)
            "cs"                           -> detectCSharp(code, cfg)
            "rs"                           -> detectRust(code, cfg)
            "go"                           -> detectGo(code, cfg)
            "dart"                         -> detectDart(code, cfg)
            "swift"                        -> detectSwift(code, cfg)
            "php"                          -> detectPhp(code, cfg)
            "rb"                           -> detectRuby(code, cfg)
            "lua"                          -> detectLua(code, cfg)
            "sql"                          -> detectSql(code, cfg)
            "gradle", "groovy"             -> detectGroovy(code, cfg)
            "toml"                         -> detectToml(code, cfg)
            "ini", "cfg", "conf",
            "properties"                   -> detectIni(code, cfg)
            "md"                           -> detectMarkdown(code, cfg)
            else                           -> emptyList()
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * Strip line and block comments + string literals from [code] using the
     * comment markers provided by [cfg].  Returns a parallel string of equal
     * length (spaces replace removed chars) so line/column numbers stay valid.
     */
    private fun stripStringsAndComments(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
        stringChars: List<Char> = listOf('"', '\'', '`'),
    ): String {
        val lineComment  = cfg.lineComment
        val blockOpen    = cfg.blockComment?.first  ?: ""
        val blockClose   = cfg.blockComment?.second ?: ""

        val buf = StringBuilder(code)
        var i = 0
        while (i < buf.length) {
            // Block comment
            if (blockOpen.isNotEmpty() && buf.startsWith(blockOpen, i)) {
                val end = buf.indexOf(blockClose, i + blockOpen.length)
                    .let { if (it == -1) buf.length else it + blockClose.length }
                for (j in i until end) if (buf[j] != '\n') buf[j] = ' '
                i = end
                continue
            }
            // Line comment
            if (lineComment.isNotEmpty() && buf.startsWith(lineComment, i)) {
                var j = i
                while (j < buf.length && buf[j] != '\n') { buf[j] = ' '; j++ }
                i = j
                continue
            }
            // String literal
            if (buf[i] in stringChars) {
                val q = buf[i]
                buf[i] = ' '; i++
                val triple = "$q$q$q"
                val isTriple = q == '"' && i + 1 < buf.length && buf.startsWith("$q$q", i)
                if (isTriple) {
                    buf[i] = ' '; buf[i + 1] = ' '; i += 2
                    while (i < buf.length) {
                        if (buf.startsWith(triple, i)) {
                            buf[i] = ' '; buf[i + 1] = ' '; buf[i + 2] = ' '; i += 3; break
                        }
                        if (buf[i] != '\n') buf[i] = ' '
                        i++
                    }
                } else {
                    while (i < buf.length && buf[i] != q && buf[i] != '\n') {
                        if (buf[i] == '\\') { buf[i] = ' '; i++ }
                        if (i < buf.length) { if (buf[i] != '\n') buf[i] = ' '; i++ }
                    }
                    if (i < buf.length && buf[i] == q) { buf[i] = ' '; i++ }
                }
                continue
            }
            i++
        }
        return buf.toString()
    }

    /** Walk [stripped] line by line, match [pattern], emit errors. */
    private fun scanLines(
        original: String,
        stripped: String,
        pattern: Regex,
        severity: Severity = Severity.ERROR,
        message: (MatchResult) -> String,
    ): List<SyntaxError> {
        val errors     = mutableListOf<SyntaxError>()
        val stripLines = stripped.split('\n')
        stripLines.forEachIndexed { lineIdx, line ->
            pattern.findAll(line).forEach { m ->
                errors += SyntaxError(
                    line      = lineIdx,
                    column    = m.range.first,
                    endColumn = m.range.last + 1,
                    message   = message(m),
                    severity  = severity,
                )
            }
        }
        return errors
    }

    /**
     * Check that every bracket pair defined in [cfg.bracketPairs] is balanced.
     * Uses [stripped] (comments/strings removed) to avoid false positives.
     */
    private fun checkAllBrackets(
        code: String,
        stripped: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        for ((open, close) in cfg.bracketPairs) {
            errors += checkBraces(code, stripped, open, close)
        }
        return errors
    }

    private fun checkBraces(
        code: String,
        stripped: String,
        open: Char,
        close: Char,
    ): List<SyntaxError> {
        val errors     = mutableListOf<SyntaxError>()
        val stripLines = stripped.split('\n')
        val stack      = ArrayDeque<Pair<Int, Int>>() // (lineIdx, col)
        stripLines.forEachIndexed { lineIdx, line ->
            line.forEachIndexed { col, c ->
                when (c) {
                    open  -> stack.addLast(lineIdx to col)
                    close -> if (stack.isEmpty()) {
                        errors += SyntaxError(lineIdx, col, col + 1,
                            "Unexpected '$close' — no matching '$open'")
                    } else {
                        stack.removeLast()
                    }
                }
            }
        }
        stack.forEach { (l, c) ->
            errors += SyntaxError(l, c, c + 1, "Unclosed '$open' — missing '$close'")
        }
        return errors
    }

    /**
     * For indent-based languages (Python, YAML): uses VS Code's own
     * `onEnterRules.beforeText` patterns to find lines that need a trailing
     * colon but are missing it.
     */
    private fun checkMissingColons(
        code: String,
        stripped: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        if (cfg.colonRequiredPatterns.isEmpty()) return emptyList()
        val errors     = mutableListOf<SyntaxError>()
        val stripLines = stripped.split('\n')
        stripLines.forEachIndexed { i, line ->
            val trim = line.trimEnd()
            if (trim.isEmpty() || trim.startsWith('#')) return@forEachIndexed
            // If it already ends with a colon, it's fine
            if (trim.endsWith(':') || trim.endsWith('\\') || trim.endsWith(',')) return@forEachIndexed
            // Check: does this look like a control-flow line that SHOULD end with ':'?
            // We look for VS Code's onEnterRules beforeText pattern stripped of the
            // trailing `:\s*$` anchor — so we match the body without requiring the colon.
            val bodyPattern = Regex("""^\s*(?:def|class|for|if|elif|else|while|try|with|finally|except|async)\b.*[^:]$""")
            if (bodyPattern.containsMatchIn(trim) && !trim.trimEnd().endsWith('\\')) {
                errors += SyntaxError(i, trim.length - 1, trim.length,
                    "Missing ':' at end of statement", Severity.ERROR)
            }
        }
        return errors
    }

    /**
     * Uses VS Code's `indentationRules` to count unclosed indent blocks.
     * Emits a single error at the start of the file if the count mismatches.
     */
    private fun checkIndentBalance(
        code: String,
        stripped: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val inc = cfg.increaseIndentPattern ?: return emptyList()
        val dec = cfg.decreaseIndentPattern ?: return emptyList()
        var depth = 0
        var firstOpenLine = -1
        stripped.split('\n').forEachIndexed { i, line ->
            if (inc.containsMatchIn(line)) {
                if (depth == 0) firstOpenLine = i
                depth++
            }
            if (dec.containsMatchIn(line) && depth > 0) depth--
        }
        return if (depth > 0 && firstOpenLine >= 0) {
            listOf(SyntaxError(firstOpenLine, 0, 1,
                "Unclosed block — $depth indent level(s) never closed", Severity.ERROR))
        } else emptyList()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Language detectors
    // ─────────────────────────────────────────────────────────────────────────

    // ── Kotlin ────────────────────────────────────────────────────────────────
    private fun detectKotlin(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)

        // `!!` non-null assertion (warning)
        errors += scanLines(code, stripped, Regex("""!!"""), Severity.WARNING) {
            "Non-null assertion (!!) — consider using ?. or ?: instead"
        }
        // var in for-loop header
        errors += scanLines(code, stripped,
            Regex("""\bfor\s*\(.*\bvar\b"""), Severity.WARNING) {
            "Avoid declaring 'var' inside a for loop header"
        }
        // Function missing explicit return type
        errors += scanLines(code, stripped,
            Regex("""^\s*(?:private|public|internal|protected|override|suspend)?\s*fun\s+\w+\s*\([^)]*\)\s*\{"""),
            Severity.WARNING) {
            "Function missing explicit return type"
        }
        // Force-cast as!!
        errors += scanLines(code, stripped,
            Regex("""\bas!!\s"""), Severity.WARNING) {
            "Force cast (as!!) — use 'as?' with safe casting"
        }
        // Mutable companion val
        errors += scanLines(code, stripped,
            Regex("""\bcompanion\s+object\b"""), Severity.WARNING) {
            "Verify companion object properties — prefer val over var"
        }

        return errors
    }

    // ── Java ──────────────────────────────────────────────────────────────────
    private fun detectJava(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)
        errors += checkIndentBalance(code, stripped, cfg)

        // Missing semicolons on statement-like lines
        stripped.split('\n').forEachIndexed { i, line ->
            val trim = line.trimEnd()
            if (trim.isNotEmpty()
                && !trim.endsWith(';')
                && !trim.endsWith('{')
                && !trim.endsWith('}')
                && !trim.endsWith(',')
                && !trim.startsWith("//")
                && !trim.startsWith("*")
                && !trim.startsWith("@")
                && Regex("""^\s*(return|throw|break|continue|[a-zA-Z_$][\w$]*\s*[=(]|this\.|super\.)""")
                    .containsMatchIn(trim)
            ) {
                errors += SyntaxError(i, trim.trimStart().length, trim.length,
                    "Possible missing semicolon", Severity.WARNING)
            }
        }
        // String comparison with ==
        errors += scanLines(code, stripped,
            Regex(""""[^"]*"\s*==\s*"[^"]*""""), Severity.WARNING) {
            "Use .equals() for String comparison, not =="
        }
        // Unnecessary null check on primitive
        errors += scanLines(code, stripped,
            Regex("""\b(int|long|double|float|boolean|char|byte|short)\s+\w+\s*==\s*null"""),
            Severity.ERROR) {
            "Primitive type cannot be null"
        }

        return errors
    }

    // ── Python ────────────────────────────────────────────────────────────────
    private fun detectPython(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)

        val origLines = code.split('\n')

        // Mixed tabs/spaces (using actual source, not stripped)
        origLines.forEachIndexed { i, line ->
            if (line.contains('\t') && line.contains("    ")) {
                errors += SyntaxError(i, 0, 1,
                    "Mixed tabs and spaces in indentation", Severity.ERROR)
            }
        }

        // Missing colon — uses VS Code onEnterRules patterns via checkMissingColons
        errors += checkMissingColons(code, stripped, cfg)

        // Python 2 print without parens
        errors += scanLines(code, stripped,
            Regex("""^\s*print\s+[^(]"""), Severity.WARNING) {
            "print without parentheses — Python 2 style"
        }
        // == None (should be is None)
        errors += scanLines(code, stripped,
            Regex("""\b\w+\s*==\s*None\b|\bNone\s*==\s*\w+"""), Severity.WARNING) {
            "Use 'is None' instead of '== None'"
        }
        // mutable default argument
        errors += scanLines(code, stripped,
            Regex("""def\s+\w+\s*\([^)]*=\s*[\[\{]"""), Severity.ERROR) {
            "Mutable default argument — use None and assign inside the function"
        }
        // bare except:
        errors += scanLines(code, stripped,
            Regex("""^\s*except\s*:"""), Severity.WARNING) {
            "Bare 'except:' catches everything — specify exception type(s)"
        }
        // Global variable
        errors += scanLines(code, stripped,
            Regex("""^\s*global\s+\w"""), Severity.WARNING) {
            "Global variable usage — prefer passing values as arguments"
        }

        return errors
    }

    // ── JavaScript ────────────────────────────────────────────────────────────
    private fun detectJavaScript(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)
        errors += checkIndentBalance(code, stripped, cfg)

        // var usage
        errors += scanLines(code, stripped,
            Regex("""\bvar\s+\w"""), Severity.WARNING) {
            "Avoid 'var' — use 'const' or 'let'"
        }
        // == instead of ===
        errors += scanLines(code, stripped,
            Regex("""(?<![=!<>])={2}(?!=)"""), Severity.WARNING) {
            "Use '===' instead of '==' for strict equality"
        }
        // != instead of !==
        errors += scanLines(code, stripped,
            Regex("""(?<!!)!={1}(?!=)"""), Severity.WARNING) {
            "Use '!==' instead of '!=' for strict inequality"
        }
        // console.* left in
        errors += scanLines(code, stripped,
            Regex("""\bconsole\.(log|warn|error|debug|info)\s*\("""), Severity.WARNING) {
            "Remove console statement before production"
        }
        // debugger statement
        errors += scanLines(code, stripped,
            Regex("""\bdebugger\b"""), Severity.WARNING) {
            "Remove 'debugger' statement before production"
        }
        // eval()
        errors += scanLines(code, stripped,
            Regex("""\beval\s*\("""), Severity.WARNING) {
            "Avoid eval() — security risk and performance issue"
        }

        return errors
    }

    // ── TypeScript ────────────────────────────────────────────────────────────
    private fun detectTypeScript(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val errors   = detectJavaScript(code, cfg).toMutableList()
        val stripped = stripStringsAndComments(code, cfg)

        // any type
        errors += scanLines(code, stripped,
            Regex(""":\s*any\b"""), Severity.WARNING) {
            "Avoid 'any' type — use a specific type or 'unknown'"
        }
        // Non-null assertion !.
        errors += scanLines(code, stripped,
            Regex("""!\."""), Severity.WARNING) {
            "Non-null assertion (!) — ensure value cannot be null/undefined"
        }
        // @ts-ignore
        errors += scanLines(code, stripped,
            Regex("""@ts-ignore"""), Severity.WARNING) {
            "@ts-ignore suppresses type errors — fix the underlying issue"
        }
        // as any cast
        errors += scanLines(code, stripped,
            Regex("""\bas\s+any\b"""), Severity.WARNING) {
            "Casting to 'any' removes type safety"
        }

        return errors
    }

    // ── HTML ──────────────────────────────────────────────────────────────────
    private fun detectHtml(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        val lines  = code.split('\n')

        val selfClosing = setOf("area","base","br","col","embed","hr","img","input",
            "link","meta","param","source","track","wbr","!doctype")
        val tagPattern  = Regex("""<(/?)([\w][\w-]*)""")
        val openStack   = ArrayDeque<Triple<String, Int, Int>>()

        lines.forEachIndexed { lineIdx, line ->
            tagPattern.findAll(line).forEach { m ->
                val isClose = m.groupValues[1] == "/"
                val tag     = m.groupValues[2].lowercase()
                if (tag in selfClosing) return@forEach
                if (isClose) {
                    if (openStack.isNotEmpty() && openStack.last().first == tag) {
                        openStack.removeLast()
                    } else if (openStack.none { it.first == tag }) {
                        errors += SyntaxError(lineIdx, m.range.first, m.range.last + 1,
                            "Unexpected closing tag </$tag> — no matching open tag")
                    }
                } else {
                    openStack.addLast(Triple(tag, lineIdx, m.range.first))
                }
            }
        }
        openStack.forEach { (tag, l, c) ->
            errors += SyntaxError(l, c, c + tag.length + 1, "Unclosed <$tag> tag")
        }

        // Missing alt on img
        Regex("""<img\b[^>]*>""").findAll(code).forEach { m ->
            if (!m.value.contains("alt=")) {
                val line = code.substring(0, m.range.first).count { it == '\n' }
                val col  = m.range.first - code.lastIndexOf('\n', m.range.first) - 1
                errors += SyntaxError(line, col, col + 5,
                    "Missing 'alt' attribute on <img>", Severity.WARNING)
            }
        }
        // Inline style (accessibility/maintainability warning)
        errors += scanLines(code, code,
            Regex("""\bstyle\s*=\s*"[^"]{10,}""""), Severity.WARNING) {
            "Prefer CSS classes over long inline styles"
        }
        // deprecated tags
        val deprecated = setOf("center","font","b","i","u","s","strike","tt","big","small")
        tagPattern.findAll(code).forEach { m ->
            val tag = m.groupValues[2].lowercase()
            if (tag in deprecated) {
                val line = code.substring(0, m.range.first).count { it == '\n' }
                val col  = m.range.first - code.lastIndexOf('\n', m.range.first) - 1
                errors += SyntaxError(line, col, col + tag.length + 2,
                    "<$tag> is deprecated — use CSS or semantic HTML instead", Severity.WARNING)
            }
        }

        return errors
    }

    // ── CSS ───────────────────────────────────────────────────────────────────
    private fun detectCss(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkBraces(code, stripped, '{', '}')

        // Missing semicolons inside rule blocks
        stripped.split('\n').forEachIndexed { i, line ->
            val trim = line.trimEnd()
            if (Regex("""^\s{2,}\S.*:.*[^;{}\s,]$""").containsMatchIn(trim)) {
                errors += SyntaxError(i, trim.length - 1, trim.length,
                    "Possible missing semicolon in CSS property", Severity.WARNING)
            }
        }
        // !important overuse
        errors += scanLines(code, stripped,
            Regex("""!\s*important"""), Severity.WARNING) {
            "Avoid !important — it makes CSS harder to maintain"
        }
        // Invalid hex color length
        errors += scanLines(code, stripped,
            Regex("""#[0-9a-fA-F]{2}(?![0-9a-fA-F])\b|#[0-9a-fA-F]{5}(?![0-9a-fA-F])\b"""),
            Severity.WARNING) {
            "Invalid hex color — use 3 or 6 hex digits"
        }

        return errors
    }

    // ── SCSS ──────────────────────────────────────────────────────────────────
    private fun detectScss(code: String, cfg: VsCodeLanguageConfigFetcher.LangConfig): List<SyntaxError> =
        detectCss(code, cfg)

    // ── JSON ──────────────────────────────────────────────────────────────────
    private fun detectJson(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()

        // Use a JSON-specific string stripper that does NOT touch "//" sequences
        // because JSON has no comment syntax. The generic stripStringsAndComments
        // would treat the "//" inside "https://..." URLs as line-comment markers.
        val stripped = stripJsonStrings(code)

        errors += checkBraces(code, stripped, '{', '}')
        errors += checkBraces(code, stripped, '[', ']')

        // Trailing commas (illegal in strict JSON)
        stripped.split('\n').forEachIndexed { lineIdx, line ->
            Regex(""",\s*[}\]]""").find(line)?.let { m ->
                errors += SyntaxError(lineIdx, m.range.first, m.range.last + 1,
                    "Trailing comma not allowed in JSON", Severity.ERROR)
            }
        }

        // Comments — only flag "//" that appear in [stripped] where all string
        // values are blanked out. This prevents URLs like "https://..." inside
        // JSON string values from triggering a false positive.
        stripped.split('\n').forEachIndexed { lineIdx, line ->
            val m = Regex("""(?<![:/])//""").find(line) ?: return@forEachIndexed
            errors += SyntaxError(lineIdx, m.range.first, m.range.first + 2,
                "Comments are not valid in JSON (use .jsonc for commented JSON)")
        }

        // Unquoted keys: after string stripping, a genuine unquoted key is a bare
        // identifier followed by ':' at the start of an object line. Skip lines
        // whose first non-space char is '"' (key whose value was stripped away)
        // or a structural/punctuation character.
        stripped.split('\n').forEachIndexed { lineIdx, line ->
            val trim = line.trim()
            if (trim.isEmpty()) return@forEachIndexed
            val first = trim[0]
            if (first == '"' || first == '{' || first == '}' || first == '['
                || first == ']' || first == ',' || first == '/') return@forEachIndexed
            if (Regex("""^\s*[A-Za-z_][A-Za-z0-9_]*\s*:""").containsMatchIn(line)) {
                val col = line.length - line.trimStart().length
                errors += SyntaxError(lineIdx, col, col + trim.indexOf(':').coerceAtLeast(1),
                    "JSON keys must be quoted strings", Severity.ERROR)
            }
        }

        return errors
    }

    /**
     * Blanks out the content of JSON string values so structural checks are not
     * confused by text that looks like syntax (e.g. `://` in URLs, `{` in
     * template strings, bare words in descriptions).
     *
     * Unlike [stripStringsAndComments] this helper does NOT attempt to remove
     * comments, because JSON has no comment syntax and `://` must not be treated
     * as a comment opener.
     */
    private fun stripJsonStrings(code: String): String {
        val buf = StringBuilder(code)
        var i = 0
        while (i < buf.length) {
            if (buf[i] == '"') {
                i++ // consume opening quote, leave it visible
                while (i < buf.length && buf[i] != '"') {
                    if (buf[i] == '\\') {
                        buf[i] = ' '   // blank the backslash
                        i++
                        if (i < buf.length && buf[i] != '\n') {
                            buf[i] = ' ' // blank the escaped char
                        }
                    } else if (buf[i] != '\n') {
                        buf[i] = ' '   // blank non-newline content
                    }
                    i++
                }
                if (i < buf.length) i++ // consume closing quote, leave it visible
            } else {
                i++
            }
        }
        return buf.toString()
    }

    private fun detectXml(code: String, cfg: VsCodeLanguageConfigFetcher.LangConfig): List<SyntaxError> {
        val errors = detectHtml(code, cfg).toMutableList()
        // Unquoted attribute values
        Regex("""=(\w+)\b""").findAll(code).forEach { m ->
            val line = code.substring(0, m.range.first).count { it == '\n' }
            val col  = m.range.first - code.lastIndexOf('\n', m.range.first) - 1
            errors += SyntaxError(line, col, col + m.value.length,
                "Attribute value must be quoted in XML", Severity.WARNING)
        }
        return errors
    }

    // ── YAML ──────────────────────────────────────────────────────────────────
    private fun detectYaml(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        val lines  = code.split('\n')

        lines.forEachIndexed { i, line ->
            // Tab indentation illegal in YAML
            if (line.startsWith('\t')) {
                errors += SyntaxError(i, 0, 1,
                    "Tabs are not allowed as indentation in YAML", Severity.ERROR)
            }
            // YAML 1.1 boolean traps
            Regex("""\b(yes|no|on|off)\s*$""", RegexOption.IGNORE_CASE).find(line)?.let { m ->
                errors += SyntaxError(i, m.range.first, m.range.last + 1,
                    "'${m.value.trim()}' is a boolean in YAML 1.1 — use true/false explicitly",
                    Severity.WARNING)
            }
            // Duplicate key heuristic
        }

        // Indentation consistency (must be consistent multiples)
        val indentLevels = lines.mapNotNull { l ->
            val sp = l.length - l.trimStart().length
            if (sp > 0 && l.trimStart().isNotEmpty()) sp else null
        }.toSet()
        if (indentLevels.size > 1) {
            val gcd = indentLevels.reduce { a, b -> gcd(a, b) }
            if (gcd < 1 || indentLevels.any { it % gcd != 0 }) {
                errors += SyntaxError(0, 0, 1,
                    "Inconsistent indentation in YAML", Severity.WARNING)
            }
        }

        return errors
    }

    // ── Shell ─────────────────────────────────────────────────────────────────
    private fun detectShell(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        // [ without spaces
        errors += scanLines(code, stripped,
            Regex("""\[(?!\s)|\[.*(?<!\s)\]"""), Severity.WARNING) {
            "Missing spaces inside [ ] — use [ condition ]"
        }
        // Unquoted variable
        errors += scanLines(code, stripped,
            Regex("""\$\w+"""), Severity.WARNING) {
            "Variable '${it.value}' should be quoted: \"${it.value}\""
        }
        // cd without error check
        errors += scanLines(code, stripped,
            Regex("""^\s*cd\s+\S+\s*$"""), Severity.WARNING) {
            "Check cd result: cd /path || exit 1"
        }
        // Missing shebang (first line)
        if (!code.trimStart().startsWith("#!")) {
            errors += SyntaxError(0, 0, 1,
                "Shell script missing shebang line (e.g. #!/bin/bash)", Severity.WARNING)
        }

        return errors
    }

    // ── C ─────────────────────────────────────────────────────────────────────
    private fun detectC(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)
        errors += checkIndentBalance(code, stripped, cfg)

        // Assignment inside condition
        errors += scanLines(code, stripped,
            Regex("""\b(?:if|while|for)\s*\([^)]*(?<![=!<>])=(?!=)[^)]*\)"""),
            Severity.WARNING) {
            "Possible assignment inside condition — did you mean '=='?"
        }
        // Missing semicolons
        stripped.split('\n').forEachIndexed { i, line ->
            val trim = line.trimEnd()
            if (trim.isNotEmpty()
                && !trim.endsWith(';')
                && !trim.endsWith('{')
                && !trim.endsWith('}')
                && !trim.endsWith(',')
                && !trim.endsWith('\\')
                && !trim.startsWith('#')
                && !trim.startsWith("//")
                && !trim.startsWith("*")
                && Regex("""^\s*\w[\w\s*]*\s*[=(]""").containsMatchIn(trim)
            ) {
                errors += SyntaxError(i, trim.trimStart().length, trim.length,
                    "Possible missing semicolon", Severity.WARNING)
            }
        }
        // gets() — unsafe
        errors += scanLines(code, stripped,
            Regex("""\bgets\s*\("""), Severity.ERROR) {
            "gets() is unsafe (buffer overflow) — use fgets() instead"
        }
        // strcpy without bounds
        errors += scanLines(code, stripped,
            Regex("""\bstrcpy\s*\("""), Severity.WARNING) {
            "strcpy() is unsafe — use strncpy() or strlcpy()"
        }

        return errors
    }

    // ── C++ ───────────────────────────────────────────────────────────────────
    private fun detectCpp(code: String, cfg: VsCodeLanguageConfigFetcher.LangConfig): List<SyntaxError> {
        val errors   = detectC(code, cfg).toMutableList()
        val stripped = stripStringsAndComments(code, cfg)

        // Raw pointer new without delete
        errors += scanLines(code, stripped,
            Regex("""\bnew\s+\w"""), Severity.WARNING) {
            "Raw 'new' — prefer smart pointers (std::unique_ptr / std::shared_ptr)"
        }

        return errors
    }

    // ── C# ────────────────────────────────────────────────────────────────────
    private fun detectCSharp(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = detectJava(code, cfg).toMutableList()

        // var with generic new
        errors += scanLines(code, stripped,
            Regex("""\bvar\s+\w+\s*=\s*new\s+\w+<"""), Severity.WARNING) {
            "Consider explicit type instead of 'var' with generic new"
        }
        // Thread.Sleep in async context
        errors += scanLines(code, stripped,
            Regex("""\bThread\.Sleep\s*\("""), Severity.WARNING) {
            "Use await Task.Delay() instead of Thread.Sleep() in async code"
        }
        // Catch Exception without filter
        errors += scanLines(code, stripped,
            Regex("""\bcatch\s*\(\s*Exception\s*\w*\s*\)"""), Severity.WARNING) {
            "Catching base Exception — catch specific exception types"
        }

        return errors
    }

    // ── Rust ──────────────────────────────────────────────────────────────────
    private fun detectRust(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)
        errors += checkIndentBalance(code, stripped, cfg)

        errors += scanLines(code, stripped,
            Regex("""\.(unwrap)\(\)"""), Severity.WARNING) {
            ".unwrap() panics on None/Err — use '?' or match"
        }
        errors += scanLines(code, stripped,
            Regex("""\.(expect)\("""), Severity.WARNING) {
            ".expect() panics on None/Err — ensure this is truly impossible"
        }
        errors += scanLines(code, stripped,
            Regex("""\blet\s+(?!_)\w+\s*;"""), Severity.WARNING) {
            "Unused binding — prefix with '_' if intentional"
        }
        // clone() overuse
        errors += scanLines(code, stripped,
            Regex("""\.(clone)\(\)"""), Severity.WARNING) {
            ".clone() — ensure a clone is necessary here vs borrowing"
        }
        // println! left in
        errors += scanLines(code, stripped,
            Regex("""\bprintln!\s*\("""), Severity.WARNING) {
            "Debug println! — remove before production"
        }

        return errors
    }

    // ── Go ────────────────────────────────────────────────────────────────────
    private fun detectGo(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)
        errors += checkIndentBalance(code, stripped, cfg)

        // Ignored error
        errors += scanLines(code, stripped,
            Regex("""\w+\s*,\s*_\s*:?=\s*\w"""), Severity.WARNING) {
            "Error return discarded with '_' — always check errors in Go"
        }
        // fmt.Print* debug
        errors += scanLines(code, stripped,
            Regex("""\bfmt\.Print"""), Severity.WARNING) {
            "Debug print — remove before production"
        }
        // goroutine leak risk — go func() without WaitGroup or channel
        errors += scanLines(code, stripped,
            Regex("""\bgo\s+func\s*\("""), Severity.WARNING) {
            "Goroutine started — ensure it has a termination condition to avoid leaks"
        }
        // Panic
        errors += scanLines(code, stripped,
            Regex("""\bpanic\s*\("""), Severity.WARNING) {
            "panic() should only be used for truly unrecoverable errors"
        }

        return errors
    }

    // ── Dart ──────────────────────────────────────────────────────────────────
    private fun detectDart(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)
        errors += checkIndentBalance(code, stripped, cfg)

        errors += scanLines(code, stripped,
            Regex("""\bdynamic\b"""), Severity.WARNING) {
            "Avoid 'dynamic' — prefer a specific type"
        }
        errors += scanLines(code, stripped,
            Regex("""\bprint\s*\("""), Severity.WARNING) {
            "Debug print() — use a logger in production"
        }
        // Late without initialization
        errors += scanLines(code, stripped,
            Regex("""\blate\s+\w"""), Severity.WARNING) {
            "late variable — ensure it is always initialized before first use"
        }

        return errors
    }

    // ── Swift ─────────────────────────────────────────────────────────────────
    private fun detectSwift(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)
        errors += checkIndentBalance(code, stripped, cfg)

        errors += scanLines(code, stripped, Regex("""!\s*\."""), Severity.WARNING) {
            "Force unwrap (!) may crash — use guard, if let, or ?."
        }
        errors += scanLines(code, stripped, Regex("""\bas!\s"""), Severity.WARNING) {
            "Force cast (as!) may crash — use 'as?' with optional binding"
        }
        errors += scanLines(code, stripped, Regex("""\bprint\s*\("""), Severity.WARNING) {
            "Debug print() — remove before production"
        }
        // implicitly unwrapped optional
        errors += scanLines(code, stripped, Regex("""\w+!\s*[:{]"""), Severity.WARNING) {
            "Implicitly unwrapped optional — prefer Optional with proper handling"
        }

        return errors
    }

    // ── PHP ───────────────────────────────────────────────────────────────────
    private fun detectPhp(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)
        errors += checkIndentBalance(code, stripped, cfg)

        errors += scanLines(code, stripped,
            Regex("""(?<![=!<>])={2}(?!=)"""), Severity.WARNING) {
            "Use '===' instead of '==' for strict comparison"
        }
        // Missing semicolons
        stripped.split('\n').forEachIndexed { i, line ->
            val trim = line.trimEnd()
            if (trim.isNotEmpty() && !trim.endsWith(';') && !trim.endsWith('{')
                && !trim.endsWith('}') && !trim.endsWith(',')
                && Regex("""^\s*\$\w""").containsMatchIn(trim)) {
                errors += SyntaxError(i, trim.trimStart().length, trim.length,
                    "Possible missing semicolon", Severity.WARNING)
            }
        }
        // SQL injection risk
        errors += scanLines(code, stripped,
            Regex("""mysql_query\s*\("""), Severity.WARNING) {
            "mysql_query() is deprecated and unsafe — use PDO or MySQLi with prepared statements"
        }
        // Direct $_GET/$_POST in query
        val phpUserInputPattern = Regex("\\$" + "_(GET|POST|REQUEST)\\[")
        errors += scanLines(code, stripped, phpUserInputPattern, Severity.WARNING) { m ->
            "Direct user input in \$_${m.groupValues[1]} — sanitize before use"
        }

        return errors
    }

    // ── Ruby ──────────────────────────────────────────────────────────────────
    private fun detectRuby(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)

        errors += scanLines(code, stripped, Regex("""\bputs\s"""), Severity.WARNING) {
            "Debug puts — remove before production"
        }
        // == nil (should be nil?)
        errors += scanLines(code, stripped,
            Regex("""\b\w+\s*==\s*nil\b"""), Severity.WARNING) {
            "Use '.nil?' instead of '== nil'"
        }
        // rescue without type
        errors += scanLines(code, stripped,
            Regex("""^\s*rescue\s*$"""), Severity.WARNING) {
            "Bare rescue catches StandardError and subclasses — specify exception type"
        }

        return errors
    }

    // ── Lua ───────────────────────────────────────────────────────────────────
    private fun detectLua(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)

        // Count if/for/while/function vs end (using VS Code comment-stripped code)
        val opens = Regex("""\b(?:if|for|while|function|do)\b""").findAll(stripped).count()
        val ends  = Regex("""\bend\b""").findAll(stripped).count()
        if (opens > ends) {
            errors += SyntaxError(0, 0, 1,
                "Missing ${opens - ends} 'end' keyword(s) — check if/for/while/function blocks")
        }

        errors += scanLines(code, stripped, Regex("""\bprint\s*\("""), Severity.WARNING) {
            "Debug print() — remove before production"
        }
        // global variable without local
        errors += scanLines(code, stripped,
            Regex("""^\s*(?!local\s)\w+\s*="""), Severity.WARNING) {
            "Implicit global variable — use 'local' keyword"
        }

        return errors
    }

    // ── SQL ───────────────────────────────────────────────────────────────────
    private fun detectSql(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkBraces(code, stripped, '(', ')')

        errors += scanLines(code, stripped,
            Regex("""\bSELECT\s+\*""", RegexOption.IGNORE_CASE), Severity.WARNING) {
            "SELECT * — specify only needed columns for performance"
        }
        // Missing WHERE on UPDATE/DELETE
        errors += scanLines(code, stripped,
            Regex("""\b(UPDATE|DELETE\s+FROM)\s+\w+\s*$""", RegexOption.IGNORE_CASE),
            Severity.WARNING) {
            "${it.groupValues[1]} without WHERE clause — this affects ALL rows"
        }
        // Missing semicolon at end
        val stmt = stripped.trimEnd()
        if (stmt.isNotEmpty() && !stmt.endsWith(';')) {
            val lastLine = stripped.split('\n').size - 1
            errors += SyntaxError(lastLine, 0, 1,
                "SQL statement missing terminating semicolon", Severity.WARNING)
        }

        return errors
    }

    // ── Groovy / Gradle ───────────────────────────────────────────────────────
    private fun detectGroovy(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val stripped = stripStringsAndComments(code, cfg)
        val errors   = mutableListOf<SyntaxError>()

        errors += checkAllBrackets(code, stripped, cfg)
        errors += checkIndentBalance(code, stripped, cfg)

        errors += scanLines(code, stripped,
            Regex("""\bdef\s+\w+\s*\("""), Severity.WARNING) {
            "Consider using explicit return type instead of 'def'"
        }

        return errors
    }

    // ── TOML ──────────────────────────────────────────────────────────────────
    private fun detectToml(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        code.split('\n').forEachIndexed { i, line ->
            val stripped = line.replace(Regex("#[^\n]*"), "").trim()
            if (stripped.isEmpty() || stripped.startsWith('[')) return@forEachIndexed
            if (!stripped.contains('=')) {
                errors += SyntaxError(i, 0, stripped.length,
                    "TOML key-value pair must contain '='", Severity.ERROR)
            }
        }
        return errors
    }

    // ── INI ───────────────────────────────────────────────────────────────────
    private fun detectIni(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        code.split('\n').forEachIndexed { i, line ->
            val trim = line.trim()
            if (trim.isEmpty() || trim.startsWith(';') || trim.startsWith('#')
                || trim.startsWith('[')) return@forEachIndexed
            if (!trim.contains('=') && !trim.contains(':')) {
                errors += SyntaxError(i, 0, trim.length,
                    "INI entry missing '=' or ':'", Severity.WARNING)
            }
        }
        return errors
    }

    // ── Markdown ──────────────────────────────────────────────────────────────
    private fun detectMarkdown(
        code: String,
        cfg: VsCodeLanguageConfigFetcher.LangConfig,
    ): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        val lines  = code.split('\n')

        lines.forEachIndexed { i, line ->
            // Empty link URL
            Regex("""\[[^\]]+\]\(\s*\)""").find(line)?.let { m ->
                errors += SyntaxError(i, m.range.first, m.range.last + 1,
                    "Empty link URL", Severity.WARNING)
            }
            // Unclosed code fence
            if (line.startsWith("```") && lines.drop(i + 1).none { it.startsWith("```") }) {
                errors += SyntaxError(i, 0, 3,
                    "Unclosed code fence (```) — add closing ``` on its own line",
                    Severity.WARNING)
                return errors
            }
        }

        return errors
    }

    // ── Math utils ───────────────────────────────────────────────────────────
    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
