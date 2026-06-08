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

package io.vscodex.ai.compose.ui

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vscodex.ai.compose.ui.graphics.rememberSvgAssetImageBitmap
import io.vscodex.ai.core.FileIcons
import io.vscodex.ai.ui.screens.editor.EditorViewModel
import kiwi.orbit.compose.icons.Icons
import kiwi.orbit.compose.ui.controls.Icon
import kiwi.orbit.compose.ui.controls.Text

// ─────────────────────────────────────────────────────────────────────────────
// Language display name helper
// Maps common file extensions to human-readable language labels,
// mirroring VS Code's bottom-right language indicator.
// ─────────────────────────────────────────────────────────────────────────────
private fun languageLabel(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt"         -> "Kotlin"
        "kts"        -> "Kotlin Script"
        "java"       -> "Java"
        "py"         -> "Python"
        "js"         -> "JavaScript"
        "ts"         -> "TypeScript"
        "jsx"        -> "JSX"
        "tsx"        -> "TSX"
        "html","htm" -> "HTML"
        "css"        -> "CSS"
        "scss"       -> "SCSS"
        "sass"       -> "Sass"
        "less"       -> "Less"
        "json"       -> "JSON"
        "xml"        -> "XML"
        "yaml","yml" -> "YAML"
        "toml"       -> "TOML"
        "md"         -> "Markdown"
        "sh","bash"  -> "Shell Script"
        "zsh"        -> "Zsh"
        "fish"       -> "Fish"
        "cpp","cxx"  -> "C++"
        "c"          -> "C"
        "h","hpp"    -> "C/C++ Header"
        "cs"         -> "C#"
        "rs"         -> "Rust"
        "go"         -> "Go"
        "rb"         -> "Ruby"
        "php"        -> "PHP"
        "swift"      -> "Swift"
        "dart"       -> "Dart"
        "lua"        -> "Lua"
        "r"          -> "R"
        "sql"        -> "SQL"
        "gradle"     -> "Gradle"
        "groovy"     -> "Groovy"
        "tf"         -> "Terraform"
        "dockerfile" -> "Dockerfile"
        "ini","cfg"  -> "INI"
        "txt"        -> "Plain Text"
        ""           -> "Plain Text"
        else         -> ext.replaceFirstChar { it.uppercase() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Breadcrumb path helper
// Returns path segments for the breadcrumb strip (parent folder → file name).
// Keeps at most the last 3 path segments to avoid overflow on mobile.
// ─────────────────────────────────────────────────────────────────────────────
private fun breadcrumbSegments(filePath: String): List<String> {
    val parts = filePath
        .replace("\\", "/")
        .split("/")
        .filter { it.isNotEmpty() }
    return if (parts.size <= 3) parts else parts.takeLast(3)
}

// ─────────────────────────────────────────────────────────────────────────────
// Pill badge – small rounded rectangle chip (used for language label)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LangBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Breadcrumb strip – shown below the tab row, inspired by VS Code's breadcrumbs.
// Displays: folder › … › file name   with the active file name highlighted.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EditorBreadcrumb(
    filePath: String,
    modifier: Modifier = Modifier
) {
    val segments = breadcrumbSegments(filePath)
    val breadcrumbBg   = MaterialTheme.colorScheme.surface
    val dimText        = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val activeText     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val separatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(breadcrumbBg)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        segments.forEachIndexed { i, segment ->
            val isLast = i == segments.lastIndex

            Text(
                text       = segment,
                color      = if (isLast) activeText else dimText,
                fontSize   = 11.sp,
                fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )

            if (!isLast) {
                Text(
                    text     = " › ",
                    color    = separatorColor,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }

    // thin bottom separator
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// EditorStatusBar – VS Code-style bottom status strip shown inside the tab area.
// Displays: language badge  •  line/col indicator  •  encoding  •  modified count
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EditorStatusBar(
    selectedFile: EditorViewModel.OpenedFile?,
    totalModified: Int,
    cursorLine: Int = 1,
    cursorCol: Int  = 1,
    errorCount: Int = 0,
    diagnostics: List<io.vscodex.ai.linter.SyntaxErrorDetector.SyntaxError> = emptyList(),
    modifier: Modifier = Modifier
) {
    val bg         = MaterialTheme.colorScheme.surfaceContainerLowest
    val dimText    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val primary    = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(bg)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Language badge
        selectedFile?.let {
            val lang = languageLabel(it.file.name)
            LangBadge(label = lang, color = primary)
        }

        // Cursor position  Ln X, Col Y
        Text(
            text     = "Ln $cursorLine, Col $cursorCol",
            color    = dimText,
            fontSize = 10.sp,
            maxLines = 1
        )

        // Encoding (always UTF-8 for now – can be wired up later)
        Text(
            text     = "UTF-8",
            color    = dimText,
            fontSize = 10.sp,
            maxLines = 1
        )

        Spacer(modifier = Modifier.weight(1f))

        // Error / warning indicator with first-error line number
        val errors   = diagnostics.filter { it.severity == io.vscodex.ai.linter.SyntaxErrorDetector.Severity.ERROR }
        val warnings = diagnostics.filter { it.severity == io.vscodex.ai.linter.SyntaxErrorDetector.Severity.WARNING }
        if (errors.isNotEmpty()) {
            val firstLine = errors.minOf { it.line + 1 }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(errorColor)
                )
                Text(
                    text     = "${errors.size} error${if (errors.size > 1) "s" else ""}",
                    color    = errorColor,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text(
                    text     = "· L$firstLine",
                    color    = errorColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        } else if (warnings.isNotEmpty()) {
            val firstLine = warnings.minOf { it.line + 1 }
            val warnColor = androidx.compose.ui.graphics.Color(0xFFE6AC00)
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(warnColor)
                )
                Text(
                    text     = "${warnings.size} warning${if (warnings.size > 1) "s" else ""}",
                    color    = warnColor,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text(
                    text     = "· L$firstLine",
                    color    = warnColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        // Unsaved files indicator
        if (totalModified > 0) {
            Row(
                verticalAlignment  = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(errorColor.copy(alpha = 0.8f))
                )
                Text(
                    text     = "$totalModified unsaved",
                    color    = errorColor.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main EditorTab  – VS Code-inspired tab row with:
//   • File icon + name + modified dot / close button
//   • Active top-border accent
//   • Pin tab support (pinned tabs show a 📌 icon instead of close)
//   • Context menu: Close / Pin / Close Others / Close All / Copy Path / Copy Name
//   • Breadcrumb strip below the tab row
//   • Status bar strip (language, cursor, encoding, unsaved count)
// ─────────────────────────────────────────────────────────────────────────────
@SuppressLint("MaterialDesignInsteadOrbitDesign")
@Composable
fun EditorTab(
    files: List<EditorViewModel.OpenedFile>,
    selectedFileIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onTabReselected: (Int) -> Unit = {},
    onCloseOthers: (Int) -> Unit  = {},
    onCloseAll: () -> Unit        = {},
    errorCounts: Map<String, Int> = emptyMap(),
    fileDiagnostics: Map<String, List<io.vscodex.ai.linter.SyntaxErrorDetector.SyntaxError>> = emptyMap(),
    // Optional live values wired from EditorTopBar / ViewModel
    cursorLine: Int = 1,
    cursorCol:  Int = 1
) {
    val clipboard = LocalClipboardManager.current

    // Track which tabs are pinned (local UI state – no ViewModel change needed)
    val pinnedIndices = remember { mutableStateOf(setOf<Int>()) }

    // VSCode color tokens
    val tabActiveBg       = MaterialTheme.colorScheme.surface
    val tabInactiveBg     = MaterialTheme.colorScheme.surfaceContainerLow
    val tabHoverBg        = MaterialTheme.colorScheme.surfaceContainerHigh
    val tabBorderColor    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val activeTextColor   = MaterialTheme.colorScheme.onSurface
    val inactiveTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val activeIndicator   = MaterialTheme.colorScheme.primary
    val modifiedColor     = MaterialTheme.colorScheme.tertiary
    val pinnedColor       = MaterialTheme.colorScheme.secondary

    val totalModified = files.count { it.isModified }
    val selectedFile  = files.getOrNull(selectedFileIndex)

    // ── Tab row ──────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(tabInactiveBg)
    ) {
        // Column stacks: tabs | breadcrumb | status bar
        androidx.compose.foundation.layout.Column {

            // ── Scrollable tab strip ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(35.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.Bottom
                ) {
                    files.forEachIndexed { index, file ->
                        val isSelected       = index == selectedFileIndex
                        val isPinned         = index in pinnedIndices.value
                        var expanded         by remember { mutableStateOf(false) }
                        val interactionSource = remember { MutableInteractionSource() }
                        val isHovered        by interactionSource.collectIsHoveredAsState()

                        val tabBg by animateColorAsState(
                            targetValue = when {
                                isSelected -> tabActiveBg
                                isHovered  -> tabHoverBg
                                else       -> tabInactiveBg
                            },
                            animationSpec = tween(120),
                            label = "tab_bg_$index"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) activeTextColor else inactiveTextColor,
                            animationSpec = tween(120),
                            label = "tab_text_$index"
                        )

                        val svgIconPath   = FileIcons.getSvgIconForFile(file.file.path)
                        val isDefaultIcon = svgIconPath == "files/icons/file.svg"

                        Box(modifier = Modifier.fillMaxHeight()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(tabBg)
                                    .then(
                                        if (!isSelected) Modifier
                                            .background(tabBorderColor)
                                            .padding(end = 1.dp)
                                            .background(tabBg)
                                        else Modifier
                                    )
                                    .pointerInput(index) {
                                        detectTapGestures(
                                            onTap = {
                                                if (isSelected) {
                                                    expanded = true
                                                    onTabReselected(index)
                                                } else {
                                                    onTabSelected(index)
                                                }
                                            },
                                            onLongPress = { expanded = true }
                                        )
                                    }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                // ── Pin indicator (small pin icon for pinned tabs) ────────
                                if (isPinned) {
                                    androidx.compose.material3.Icon(
                                        imageVector        = androidx.compose.material.icons.Icons.Rounded.PushPin,
                                        contentDescription = "Pinned",
                                        tint               = pinnedColor,
                                        modifier           = Modifier.size(10.dp)
                                    )
                                }

                                // ── File type icon ───────────────────────────────────────
                                if (isDefaultIcon) {
                                    androidx.compose.material3.Icon(
                                        imageVector        = androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        tint               = textColor,
                                        modifier           = Modifier.size(14.dp)
                                    )
                                } else {
                                    Image(
                                        bitmap             = rememberSvgAssetImageBitmap(svgIconPath),
                                        contentDescription = null,
                                        modifier           = Modifier.size(14.dp)
                                    )
                                }

                                // ── File name ────────────────────────────────────────────
                                Text(
                                    text       = file.file.name,
                                    color      = textColor,
                                    fontSize   = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis,
                                    modifier   = Modifier.width(80.dp)
                                )

                                // ── Error badge — shows first error line number ───────────
                                val filePath   = file.file.path ?: ""
                                val fileDiags  = fileDiagnostics[filePath] ?: emptyList()
                                val errCount   = fileDiags.count {
                                    it.severity == io.vscodex.ai.linter.SyntaxErrorDetector.Severity.ERROR
                                }
                                val warnCount  = fileDiags.count {
                                    it.severity == io.vscodex.ai.linter.SyntaxErrorDetector.Severity.WARNING
                                }
                                // First error line (1-based for display)
                                val firstErrLine = fileDiags
                                    .filter { it.severity == io.vscodex.ai.linter.SyntaxErrorDetector.Severity.ERROR }
                                    .minOfOrNull { it.line + 1 }

                                if (errCount > 0 && firstErrLine != null) {
                                    // Error badge: red circle count + "L{line}"
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        // Error count pill
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f))
                                                .padding(horizontal = 3.dp, vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text       = errCount.toString(),
                                                color      = MaterialTheme.colorScheme.error,
                                                fontSize   = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines   = 1
                                            )
                                        }
                                        // Line number badge
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                                                .padding(horizontal = 3.dp, vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text       = "L$firstErrLine",
                                                color      = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                                fontSize   = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines   = 1
                                            )
                                        }
                                    }
                                } else if (warnCount > 0) {
                                    // Warning-only badge: amber
                                    val firstWarnLine = fileDiags
                                        .filter { it.severity == io.vscodex.ai.linter.SyntaxErrorDetector.Severity.WARNING }
                                        .minOfOrNull { it.line + 1 }
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(androidx.compose.ui.graphics.Color(0xFFFFC107).copy(alpha = 0.18f))
                                                .padding(horizontal = 3.dp, vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text       = warnCount.toString(),
                                                color      = androidx.compose.ui.graphics.Color(0xFFE6AC00),
                                                fontSize   = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines   = 1
                                            )
                                        }
                                        firstWarnLine?.let { wl ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(androidx.compose.ui.graphics.Color(0xFFFFC107).copy(alpha = 0.10f))
                                                    .padding(horizontal = 3.dp, vertical = 1.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text       = "L$wl",
                                                    color      = androidx.compose.ui.graphics.Color(0xFFE6AC00).copy(alpha = 0.85f),
                                                    fontSize   = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines   = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                // ── Modified dot / Close button ──────────────────────────
                                // Pinned tabs: show dot only (no close button)
                                // Modified + not hovered: filled dot
                                // Otherwise: × close icon
                                Box(
                                    modifier         = Modifier.size(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isPinned -> {
                                            // Pinned: only show dot if modified, no close
                                            if (file.isModified) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(modifiedColor)
                                                )
                                            }
                                        }
                                        file.isModified && !isHovered -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(modifiedColor)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication        = null,
                                                        onClick           = { onTabClose(index) }
                                                    )
                                            )
                                        }
                                        else -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication        = null,
                                                        onClick           = { onTabClose(index) }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Close,
                                                    contentDescription = "Close",
                                                    tint               = textColor,
                                                    modifier           = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } // end Row (tab cell content)

                            // ── Active tab top-border accent ─────────────────────────
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(activeIndicator)
                                )
                            }

                            // ── Right separator between inactive tabs ─────────────────
                            if (!isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(1.dp)
                                        .fillMaxHeight(0.6f)
                                        .background(tabBorderColor)
                                )
                            }

                            // ── Context menu ─────────────────────────────────────────
                            DropdownMenu(
                                expanded         = expanded,
                                onDismissRequest = { expanded = false },
                                shape            = MaterialTheme.shapes.medium
                            ) {
                                // Close
                                DropdownMenuItem(
                                    text    = { Text("Close", fontSize = 13.sp) },
                                    onClick = { expanded = false; onTabClose(index) },
                                    leadingIcon = {
                                        Icon(Icons.Close, null, modifier = Modifier.size(15.dp))
                                    }
                                )
                                // Close Others
                                DropdownMenuItem(
                                    text    = { Text("Close Others", fontSize = 13.sp) },
                                    onClick = { expanded = false; onCloseOthers(index) }
                                )
                                // Close All
                                DropdownMenuItem(
                                    text    = { Text("Close All", fontSize = 13.sp) },
                                    onClick = { expanded = false; onCloseAll() }
                                )

                                HorizontalDivider()

                                // Pin / Unpin tab
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isPinned) "Unpin Tab" else "Pin Tab",
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        expanded = false
                                        pinnedIndices.value = if (isPinned) {
                                            pinnedIndices.value - index
                                        } else {
                                            pinnedIndices.value + index
                                        }
                                    },
                                    leadingIcon = {
                                        androidx.compose.material3.Icon(
                                            imageVector        = androidx.compose.material.icons.Icons.Rounded.PushPin,
                                            contentDescription = null,
                                            modifier           = Modifier.size(15.dp)
                                        )
                                    }
                                )

                                HorizontalDivider()

                                // Copy Path
                                DropdownMenuItem(
                                    text    = { Text("Copy Path", fontSize = 13.sp) },
                                    onClick = {
                                        expanded = false
                                        clipboard.setText(AnnotatedString(file.file.absolutePath ?: ""))
                                    }
                                )
                                // Copy File Name
                                DropdownMenuItem(
                                    text    = { Text("Copy File Name", fontSize = 13.sp) },
                                    onClick = {
                                        expanded = false
                                        clipboard.setText(AnnotatedString(file.file.name))
                                    }
                                )
                                // Copy Language
                                DropdownMenuItem(
                                    text    = { Text("Copy Language", fontSize = 13.sp) },
                                    onClick = {
                                        expanded = false
                                        clipboard.setText(AnnotatedString(languageLabel(file.file.name)))
                                    }
                                )
                            }
                        } // end outer Box (tab cell)
                    } // end forEachIndexed

                    // Fill remaining space
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(tabInactiveBg)
                    )
                } // end scrollable Row

                // Bottom border line (full width)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(tabBorderColor)
                )
            } // end tab strip Box

            // ── Breadcrumb strip ─────────────────────────────────────────────
            selectedFile?.let { sf ->
                EditorBreadcrumb(filePath = sf.file.path ?: sf.file.name)
            }

            // ── Status bar ───────────────────────────────────────────────────
            EditorStatusBar(
                selectedFile  = selectedFile,
                totalModified = totalModified,
                cursorLine    = cursorLine,
                cursorCol     = cursorCol,
                errorCount    = errorCounts[selectedFile?.file?.path] ?: 0,
                diagnostics   = fileDiagnostics[selectedFile?.file?.path] ?: emptyList()
            )
        } // end Column
    } // end outer Box
}
