/*
 * This file is part of VSCodeX.
 *
 * VSCodeX is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * VSCodeX is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with VSCodeX.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package io.vscodex.ai.editor.language.textmate

import android.os.Bundle
import androidx.annotation.WorkerThread
import io.vscodex.ai.editor.completion.CompletionItemKind
import io.vscodex.ai.editor.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.IdentifierAutoComplete
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition
import io.github.rosemoe.sora.langs.textmate.utils.StringUtils
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.util.MyCharacter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration
import java.io.Reader

class VSXTMLanguage protected constructor(
    val grammar: IGrammar?,
    @JvmField
    var languageConfiguration: LanguageConfiguration?,
    var grammarRegistry: GrammarRegistry,
    var themeRegistry: ThemeRegistry,
    @JvmField val createIdentifiers: Boolean
) : EmptyLanguage() {

    @JvmField
    var tabSize: Int = 4

    private var useTab = false

    val autoCompleter: IdentifierAutoComplete = IdentifierAutoComplete()
    var isAutoCompleteEnabled: Boolean = true

    var textMateAnalyzer: VSXTMAnalyzer? = null

    private lateinit var newlineHandlers: Array<VSXTMNewlineHandler>

    var symbolPairMatch: VSXTMSymbolPairMatch = VSXTMSymbolPairMatch(this)

    var newlineHandler: VSXTMNewlineHandler? = null
        private set

    init {
        createAnalyzerAndNewlineHandler(grammar, languageConfiguration)
        // Pre-warm the VS Code snippet cache for this language so the first
        // completion request returns instantly rather than waiting on the network.
        grammar?.name?.let { name ->
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                VsCodeCompletionFetcher.prefetch(name)
            }
        }
    }

    @WorkerThread
    @Deprecated(
        "Use {@link ThemeRegistry#setTheme(String)}",
        ReplaceWith("themeRegistry.loadTheme(theme)")
    )
    @Throws(Exception::class)
    fun updateTheme(theme: IThemeSource?) {
        themeRegistry.loadTheme(theme)
    }

    private fun createAnalyzerAndNewlineHandler(
        grammar: IGrammar?,
        languageConfiguration: LanguageConfiguration?
    ) {
        val old = textMateAnalyzer
        if (old != null) {
            old.receiver = null
            old.destroy()
        }
        try {
            textMateAnalyzer = VSXTMAnalyzer(this, grammar, languageConfiguration, themeRegistry)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        this.languageConfiguration = languageConfiguration
        newlineHandler = VSXTMNewlineHandler(this)
        newlineHandlers = arrayOf(newlineHandler!!)
        if (languageConfiguration != null) {
            symbolPairMatch.updatePair()
        }
    }

    fun updateLanguage(scopeName: String?) {
        val grammar = grammarRegistry.findGrammar(scopeName)
        val languageConfiguration = grammarRegistry.findLanguageConfiguration(grammar!!.scopeName)
        createAnalyzerAndNewlineHandler(grammar, languageConfiguration)
    }

    fun updateLanguage(grammarDefinition: GrammarDefinition?) {
        val grammar = grammarRegistry.loadGrammar(grammarDefinition)
        val languageConfiguration = grammarRegistry.findLanguageConfiguration(grammar.scopeName)
        createAnalyzerAndNewlineHandler(grammar, languageConfiguration)
    }

    override fun getAnalyzeManager(): AnalyzeManager {
        if (textMateAnalyzer == null) return EmptyAnalyzeManager.INSTANCE
        return textMateAnalyzer as VSXTMAnalyzer
    }

    override fun useTab(): Boolean = useTab

    fun useTab(useTab: Boolean) {
        this.useTab = useTab
    }

    override fun getSymbolPairs(): VSXTMSymbolPairMatch = symbolPairMatch

    override fun getNewlineHandlers(): Array<VSXTMNewlineHandler> = newlineHandlers

    // ─────────────────────────────────────────────────────────────────────────
    // requireAutoComplete — the core fix
    //
    // Strategy:
    //   1. Re-tokenize the entire document with the TM grammar (already done
    //      by the analyzer; we re-use syncIdentifiers for the word list).
    //   2. Build a "symbol table" that maps each identifier to the best
    //      CompletionItemKind by inspecting the TextMate scope names that
    //      the grammar assigned to every token in the file.
    //   3. Also classify the language's built-in keywords.
    //   4. Emit a VSX SimpleCompletionItem for every match so the adapter
    //      can render the correct badge letter, color, and kind label.
    // ─────────────────────────────────────────────────────────────────────────
    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        if (!isAutoCompleteEnabled) return

        val prefix = CompletionHelper.computePrefix(
            content, position, MyCharacter::isJavaIdentifierPart
        )
        if (prefix.isEmpty()) return

        runBlocking {
            // ── Step 1: Build scope-aware symbol table from the document ──────
            val symbolTable = buildSymbolTable(content, position)

            // ── Step 2: Emit matched items ────────────────────────────────────
            val seen = mutableSetOf<String>()
            for ((word, kind) in symbolTable) {
                if (word.startsWith(prefix, ignoreCase = false) && word != prefix) {
                    if (seen.add(word)) {
                        publisher.addItem(
                            SimpleCompletionItem(
                                completionKind = kind,
                                label         = word,
                                desc          = kindSignature(kind, word),
                                prefixLength  = prefix.length,
                                commitText    = word
                            )
                        )
                    }
                }
            }

            // ── Step 3: VS Code GitHub snippets ───────────────────────────────
            // Fetch live snippet definitions from microsoft/vscode on GitHub.
            // Results are cached after the first network call per language so
            // subsequent completions are instant.
            val grammarName = grammar?.name ?: ""
            val vsCodeItems = VsCodeCompletionFetcher.fetchCompletions(grammarName, prefix)
            for (item in vsCodeItems) {
                if (seen.add(item.label.toString())) {
                    publisher.addItem(item)
                }
            }

            // ── Step 4: Fall back — identifiers from Sora's own scanner ───────
            // These cover words the TM grammar didn't classify specially
            // (they'll appear as IDENTIFIER kind).
            val idt = textMateAnalyzer!!.syncIdentifiers
            autoCompleter.requireAutoComplete(content, position, prefix, publisher, idt)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // buildSymbolTable
    //
    // Scans every line with the TM grammar and maps each token's text to a
    // CompletionItemKind using the scope names.  This is the same data the
    // syntax highlighter already has; we're just reading it for completion.
    //
    // TextMate scope conventions (standard):
    //   entity.name.function.*          → FUNCTION/IDENTIFIER
    //   entity.name.type.*              → CLASS
    //   storage.type.* / keyword.*      → KEYWORD
    //   variable.other.* / meta.var.*   → VALUE
    //   meta.definition.method.*        → IDENTIFIER (method def)
    //   support.function.*              → IDENTIFIER
    //   constant.* / support.constant.* → VALUE
    //   entity.name.tag.*               → TAG
    //   entity.other.attribute-name.*   → ATTRIBUTE
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildSymbolTable(
        content: ContentReference,
        cursorPosition: CharPosition
    ): LinkedHashMap<String, CompletionItemKind> {
        val result = LinkedHashMap<String, CompletionItemKind>()
        val ref = content.reference
        val lineCount = ref.lineCount

        // Language-level keywords (always KEYWORD kind regardless of context)
        languageKeywords().forEach { kw -> result[kw] = CompletionItemKind.KEYWORD }

        val g = grammar ?: return result

        var ruleStack: org.eclipse.tm4e.core.grammar.IStateStack? = null

        for (lineIdx in 0 until lineCount) {
            val lineText = ref.getLine(lineIdx).toString()
            if (lineText.isBlank()) continue

            val lineTokens = try {
                g.tokenizeLine(lineText, ruleStack, java.time.Duration.ofMillis(50))
            } catch (_: Exception) {
                continue
            }
            ruleStack = lineTokens.ruleStack

            val tokens = lineTokens.tokens
            for (i in tokens.indices) {
                val tok      = tokens[i]
                val startIdx = tok.startIndex
                val endIdx   = if (i + 1 < tokens.size) tokens[i + 1].startIndex else lineText.length
                if (startIdx >= endIdx || startIdx >= lineText.length) continue

                val word = lineText.substring(startIdx, endIdx.coerceAtMost(lineText.length)).trim()
                if (word.length < 2 || !word[0].isLetter()) continue

                // Skip if we already have a higher-priority classification
                val existing = result[word]

                val scopes = tok.scopes  // List<String>
                val kind   = scopesToKind(scopes)

                // Priority: KEYWORD > CLASS > IDENTIFIER > VALUE > others
                // Don't downgrade an already-known symbol
                if (existing == null || kindPriority(kind) > kindPriority(existing)) {
                    result[word] = kind
                }
            }
        }
        return result
    }

    // ── Scope → Kind mapping ──────────────────────────────────────────────────
    private fun scopesToKind(scopes: List<String>): CompletionItemKind {
        // Evaluate from innermost scope outward (last = most specific)
        for (scope in scopes.asReversed()) {
            val s = scope.lowercase()
            return when {
                // Keywords / storage
                s.startsWith("keyword")           -> CompletionItemKind.KEYWORD
                s.startsWith("storage.type")      -> CompletionItemKind.KEYWORD
                s.startsWith("storage.modifier")  -> CompletionItemKind.KEYWORD

                // Types / classes
                s.startsWith("entity.name.type")  -> CompletionItemKind.VALUE // class names as VALUE
                s.startsWith("support.class")     -> CompletionItemKind.VALUE

                // Functions / methods
                s.startsWith("entity.name.function") -> CompletionItemKind.IDENTIFIER
                s.startsWith("support.function")     -> CompletionItemKind.IDENTIFIER
                s.startsWith("meta.definition.method") -> CompletionItemKind.IDENTIFIER

                // Variables / values
                s.startsWith("variable.other")    -> CompletionItemKind.VALUE
                s.startsWith("constant")          -> CompletionItemKind.VALUE
                s.startsWith("support.constant")  -> CompletionItemKind.VALUE

                // Snippets
                s.startsWith("meta.embedded")     -> CompletionItemKind.SNIPPET

                // HTML/XML tags & attributes
                s.startsWith("entity.name.tag")           -> CompletionItemKind.TAG
                s.startsWith("entity.other.attribute")    -> CompletionItemKind.ATTRIBUTE
                s.startsWith("meta.attribute")            -> CompletionItemKind.ATTRIBUTE

                else -> continue  // try next scope
            }
        }
        return CompletionItemKind.IDENTIFIER
    }

    // Priority for upgrading a kind (higher = keep this one)
    private fun kindPriority(kind: CompletionItemKind): Int = when (kind) {
        CompletionItemKind.KEYWORD      -> 5
        CompletionItemKind.IDENTIFIER   -> 4
        CompletionItemKind.VALUE        -> 3
        CompletionItemKind.TAG          -> 2
        CompletionItemKind.ATTRIBUTE    -> 2
        CompletionItemKind.SNIPPET      -> 1
        else                            -> 0
    }

    // Human-readable description shown as the signature line under the label
    private fun kindSignature(kind: CompletionItemKind, word: String): String = when (kind) {
        CompletionItemKind.KEYWORD    -> "keyword"
        CompletionItemKind.IDENTIFIER -> "fun $word(...)"
        CompletionItemKind.VALUE      -> "val $word"
        CompletionItemKind.TAG        -> "<$word>"
        CompletionItemKind.ATTRIBUTE  -> "attr: $word"
        CompletionItemKind.SNIPPET    -> "snippet"
        else                          -> word
    }

    // ── Language keyword lists ────────────────────────────────────────────────
    // Pulled from grammar name so we get the right set per language.
    private fun languageKeywords(): List<String> {
        val name = grammar?.name?.lowercase() ?: return emptyList()
        return when {
            "kotlin" in name -> KOTLIN_KEYWORDS
            "java"   in name -> JAVA_KEYWORDS
            "python" in name -> PYTHON_KEYWORDS
            "javascript" in name || "typescript" in name -> JS_KEYWORDS
            "html"   in name -> HTML_KEYWORDS
            "css"    in name -> CSS_KEYWORDS
            "rust"   in name -> RUST_KEYWORDS
            "go"     in name -> GO_KEYWORDS
            "swift"  in name -> SWIFT_KEYWORDS
            "dart"   in name -> DART_KEYWORDS
            else             -> emptyList()
        }
    }

    fun setCompleterKeywords(keywords: Array<String?>?) {
        autoCompleter.setKeywords(keywords, false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Keyword tables
    // ─────────────────────────────────────────────────────────────────────────
    companion object {

        private val KOTLIN_KEYWORDS = listOf(
            "as", "as?", "break", "class", "continue", "do", "else", "false",
            "for", "fun", "if", "in", "!in", "interface", "is", "!is", "null",
            "object", "package", "return", "super", "this", "throw", "true",
            "try", "typealias", "typeof", "val", "var", "when", "while",
            "by", "catch", "constructor", "delegate", "dynamic", "field",
            "file", "finally", "get", "import", "init", "param", "property",
            "receiver", "set", "setparam", "value", "where", "actual", "abstract",
            "annotation", "companion", "const", "crossinline", "data", "enum",
            "expect", "external", "final", "infix", "inline", "inner", "internal",
            "lateinit", "noinline", "open", "operator", "out", "override",
            "private", "protected", "public", "reified", "sealed", "suspend",
            "tailrec", "vararg", "it", "field", "Unit", "Nothing", "Any",
            "Boolean", "Byte", "Char", "Double", "Float", "Int", "Long",
            "Number", "Short", "String", "Array", "List", "Map", "Set",
            "MutableList", "MutableMap", "MutableSet", "Pair", "Triple",
            "println", "print", "readLine", "TODO", "also", "apply", "let",
            "run", "with", "takeIf", "takeUnless", "repeat", "forEach",
            "map", "filter", "reduce", "fold", "flatMap", "first", "last",
            "find", "any", "all", "none", "count", "sum", "sortedBy",
            "groupBy", "associate", "zip", "contains", "isEmpty", "isNotEmpty"
        )

        private val JAVA_KEYWORDS = listOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false",
            "null", "var", "record", "sealed", "permits", "yield",
            "String", "Integer", "Long", "Double", "Float", "Boolean",
            "Object", "Class", "System", "Math", "Arrays", "Collections",
            "List", "Map", "Set", "ArrayList", "HashMap", "HashSet",
            "StringBuilder", "Exception", "RuntimeException", "Override",
            "Deprecated", "SuppressWarnings", "FunctionalInterface"
        )

        private val PYTHON_KEYWORDS = listOf(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else",
            "except", "finally", "for", "from", "global", "if", "import",
            "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
            "return", "try", "while", "with", "yield",
            "int", "str", "float", "bool", "list", "dict", "set", "tuple",
            "bytes", "bytearray", "type", "object", "super", "property",
            "staticmethod", "classmethod", "print", "len", "range", "enumerate",
            "zip", "map", "filter", "sorted", "reversed", "any", "all",
            "min", "max", "sum", "abs", "round", "isinstance", "issubclass",
            "hasattr", "getattr", "setattr", "delattr", "open", "input",
            "self", "cls", "__init__", "__str__", "__repr__", "__len__",
            "__eq__", "__lt__", "__gt__", "__add__", "__mul__"
        )

        private val JS_KEYWORDS = listOf(
            "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "export", "extends", "false",
            "finally", "for", "function", "if", "import", "in", "instanceof",
            "let", "new", "null", "return", "static", "super", "switch",
            "this", "throw", "true", "try", "typeof", "undefined", "var",
            "void", "while", "with", "yield", "async", "await", "of",
            "from", "as", "type", "interface", "enum", "implements",
            "abstract", "declare", "namespace", "module", "readonly",
            "string", "number", "boolean", "any", "never", "unknown",
            "object", "symbol", "bigint", "void",
            "console", "document", "window", "process", "require",
            "module", "exports", "Promise", "Array", "Object", "String",
            "Number", "Boolean", "Math", "Date", "JSON", "Error",
            "Map", "Set", "WeakMap", "WeakSet", "Symbol", "Proxy",
            "Reflect", "Intl", "fetch", "setTimeout", "setInterval",
            "clearTimeout", "clearInterval", "addEventListener",
            "querySelector", "querySelectorAll", "getElementById",
            "createElement", "appendChild", "removeChild"
        )

        private val HTML_KEYWORDS = listOf(
            "html", "head", "body", "title", "meta", "link", "script", "style",
            "div", "span", "p", "h1", "h2", "h3", "h4", "h5", "h6",
            "a", "img", "input", "button", "form", "label", "select",
            "option", "textarea", "table", "tr", "td", "th", "thead",
            "tbody", "tfoot", "ul", "ol", "li", "nav", "header", "footer",
            "main", "section", "article", "aside", "figure", "figcaption",
            "blockquote", "pre", "code", "em", "strong", "br", "hr",
            "canvas", "video", "audio", "iframe", "source",
            "class", "id", "src", "href", "alt", "type", "name",
            "value", "placeholder", "disabled", "required", "readonly",
            "action", "method", "enctype", "target", "rel", "charset",
            "content", "property", "width", "height", "style", "data"
        )

        private val CSS_KEYWORDS = listOf(
            "display", "flex", "grid", "block", "inline", "none", "absolute",
            "relative", "fixed", "sticky", "position", "top", "bottom",
            "left", "right", "width", "height", "margin", "padding",
            "border", "background", "color", "font", "font-size",
            "font-weight", "font-family", "line-height", "text-align",
            "overflow", "opacity", "transform", "transition", "animation",
            "z-index", "box-shadow", "border-radius", "cursor", "pointer",
            "center", "auto", "inherit", "initial", "unset", "important",
            "px", "em", "rem", "vh", "vw", "%"
        )

        private val RUST_KEYWORDS = listOf(
            "as", "break", "const", "continue", "crate", "else", "enum",
            "extern", "false", "fn", "for", "if", "impl", "in", "let",
            "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
            "self", "Self", "static", "struct", "super", "trait", "true",
            "type", "unsafe", "use", "where", "while", "async", "await",
            "dyn", "abstract", "become", "box", "do", "final", "macro",
            "override", "priv", "typeof", "unsized", "virtual", "yield",
            "String", "str", "i8", "i16", "i32", "i64", "i128", "isize",
            "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64",
            "bool", "char", "Vec", "HashMap", "HashSet", "Option", "Result",
            "Ok", "Err", "Some", "None", "Box", "Rc", "Arc", "Cell",
            "RefCell", "Mutex", "println!", "print!", "eprintln!", "format!",
            "vec!", "assert!", "assert_eq!", "panic!", "todo!", "unimplemented!"
        )

        private val GO_KEYWORDS = listOf(
            "break", "case", "chan", "const", "continue", "default", "defer",
            "else", "fallthrough", "for", "func", "go", "goto", "if",
            "import", "interface", "map", "package", "range", "return",
            "select", "struct", "switch", "type", "var",
            "bool", "byte", "complex64", "complex128", "error", "float32",
            "float64", "int", "int8", "int16", "int32", "int64", "rune",
            "string", "uint", "uint8", "uint16", "uint32", "uint64",
            "uintptr", "true", "false", "nil", "iota",
            "make", "new", "len", "cap", "append", "copy", "delete",
            "close", "panic", "recover", "print", "println", "fmt"
        )

        private val SWIFT_KEYWORDS = listOf(
            "class", "deinit", "enum", "extension", "func", "import", "init",
            "inout", "let", "operator", "precedencegroup", "protocol",
            "struct", "subscript", "typealias", "var", "break", "case",
            "continue", "default", "defer", "do", "else", "fallthrough",
            "for", "guard", "if", "in", "repeat", "return", "throw",
            "switch", "where", "while", "Any", "as", "catch", "false",
            "is", "nil", "rethrows", "self", "Self", "super", "throw",
            "throws", "true", "try", "associatedtype", "convenience",
            "dynamic", "didSet", "final", "get", "indirect", "lazy",
            "left", "mutating", "none", "nonmutating", "open", "optional",
            "override", "postfix", "precedence", "prefix", "private",
            "public", "required", "right", "set", "some", "static",
            "unowned", "weak", "willSet", "Int", "String", "Double",
            "Float", "Bool", "Character", "Array", "Dictionary", "Set",
            "Optional", "print", "Swift", "Foundation", "UIKit", "SwiftUI"
        )

        private val DART_KEYWORDS = listOf(
            "abstract", "as", "assert", "async", "await", "break", "case",
            "catch", "class", "const", "continue", "covariant", "default",
            "deferred", "do", "dynamic", "else", "enum", "export",
            "extends", "extension", "external", "factory", "false",
            "final", "finally", "for", "Function", "get", "hide", "if",
            "implements", "import", "in", "interface", "is", "late",
            "library", "mixin", "new", "null", "on", "operator", "part",
            "required", "rethrow", "return", "set", "show", "static",
            "super", "switch", "sync", "this", "throw", "true", "try",
            "typedef", "var", "void", "while", "with", "yield",
            "int", "double", "String", "bool", "List", "Map", "Set",
            "Iterable", "Future", "Stream", "Object", "dynamic",
            "print", "Widget", "StatelessWidget", "StatefulWidget",
            "BuildContext", "State", "Column", "Row", "Container",
            "Text", "Scaffold", "MaterialApp", "Navigator"
        )

        // ── Factory methods (unchanged from original) ─────────────────────────

        @Deprecated("")
        fun prepareLoad(
            grammarSource: IGrammarSource,
            languageConfiguration: Reader?,
            themeSource: IThemeSource?
        ): IGrammar {
            val definition = DefaultGrammarDefinition.withGrammarSource(
                grammarSource,
                StringUtils.getFileNameWithoutExtension(grammarSource.filePath),
                null
            )
            val languageRegistry = GrammarRegistry.getInstance()
            val grammar = languageRegistry.loadGrammar(definition)
            if (languageConfiguration != null) {
                languageRegistry.languageConfigurationToGrammar(
                    LanguageConfiguration.load(languageConfiguration), grammar
                )
            }
            val themeRegistry = ThemeRegistry.getInstance()
            try {
                themeRegistry.loadTheme(themeSource)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return grammar
        }

        @Deprecated("")
        fun create(
            grammarSource: IGrammarSource,
            languageConfiguration: Reader?,
            themeSource: IThemeSource?
        ): VSXTMLanguage {
            val grammar = prepareLoad(grammarSource, languageConfiguration, themeSource)
            return create(grammar.scopeName, true)
        }

        @Deprecated("")
        fun create(grammarSource: IGrammarSource, themeSource: IThemeSource?): VSXTMLanguage {
            val grammar = prepareLoad(grammarSource, null, themeSource)
            return create(grammar.scopeName, true)
        }

        @Deprecated("")
        fun createNoCompletion(
            grammarSource: IGrammarSource,
            languageConfiguration: Reader?,
            themeSource: IThemeSource?
        ): VSXTMLanguage {
            val grammar = prepareLoad(grammarSource, languageConfiguration, themeSource)
            return create(grammar.scopeName, false)
        }

        @Deprecated("")
        fun createNoCompletion(
            grammarSource: IGrammarSource,
            themeSource: IThemeSource?
        ): VSXTMLanguage {
            val grammar = prepareLoad(grammarSource, null, themeSource)
            return create(grammar.scopeName, false)
        }

        fun create(languageScopeName: String?, autoCompleteEnabled: Boolean): VSXTMLanguage {
            return create(languageScopeName, GrammarRegistry.getInstance(), autoCompleteEnabled)
        }

        fun create(
            languageScopeName: String?,
            grammarRegistry: GrammarRegistry,
            autoCompleteEnabled: Boolean
        ): VSXTMLanguage {
            return create(languageScopeName, grammarRegistry, ThemeRegistry.getInstance(), autoCompleteEnabled)
        }

        fun create(
            languageScopeName: String?,
            grammarRegistry: GrammarRegistry,
            themeRegistry: ThemeRegistry,
            autoCompleteEnabled: Boolean
        ): VSXTMLanguage {
            val grammar = grammarRegistry.findGrammar(languageScopeName)
            requireNotNull(grammar) {
                String.format("Language with %s scope name not found", grammarRegistry)
            }
            val languageConfiguration = grammarRegistry.findLanguageConfiguration(grammar.scopeName)
            return VSXTMLanguage(grammar, languageConfiguration, grammarRegistry, themeRegistry, autoCompleteEnabled)
        }

        fun create(grammarDefinition: GrammarDefinition?, autoCompleteEnabled: Boolean): VSXTMLanguage {
            return create(grammarDefinition, GrammarRegistry.getInstance(), autoCompleteEnabled)
        }

        fun create(
            grammarDefinition: GrammarDefinition?,
            grammarRegistry: GrammarRegistry,
            autoCompleteEnabled: Boolean
        ): VSXTMLanguage {
            return create(grammarDefinition, grammarRegistry, ThemeRegistry.getInstance(), autoCompleteEnabled)
        }

        fun create(
            grammarDefinition: GrammarDefinition?,
            grammarRegistry: GrammarRegistry,
            themeRegistry: ThemeRegistry,
            autoCompleteEnabled: Boolean
        ): VSXTMLanguage {
            val grammar = grammarRegistry.loadGrammar(grammarDefinition)
            requireNotNull(grammar) {
                String.format("Language with %s scope name not found", grammarRegistry)
            }
            val languageConfiguration = grammarRegistry.findLanguageConfiguration(grammar.scopeName)
            return VSXTMLanguage(grammar, languageConfiguration, grammarRegistry, themeRegistry, autoCompleteEnabled)
        }
    }
}
