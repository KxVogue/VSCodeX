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

package io.vscodex.ai.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import io.vscodex.ai.activities.AboutActivity
import io.vscodex.ai.app.BaseApplication
import io.vscodex.ai.app.strings
import io.vscodex.ai.extensions.isNotNull
import io.vscodex.ai.extensions.isNull
import io.vscodex.ai.extensions.open
import io.vscodex.ai.github.User
import io.vscodex.ai.github.auth.Api
import io.vscodex.ai.resources.R
import io.vscodex.ai.ui.navigateSingleTop
import io.vscodex.ai.ui.screens.SettingScreens
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.preferenceCategory

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val navController = rememberNavController()

    var user: User? by remember { mutableStateOf(null) }
    LaunchedEffect(key1 = true) {
        user = Api.getUserInfo()?.user
    }

    NavHost(navController, startDestination = SettingScreens.Default) {
        composable<SettingScreens.Default> {
            ProvidePreferenceLocals {
                LazyColumn(modifier = modifier.fillMaxSize()) {
                    preferenceCategory(
                        key = "pref_category_configure",
                        title = { Text(stringResource(strings.pref_category_configure)) }
                    )

                    preference(
                        key = "pref_configure_general_key",
                        title = { Text(stringResource(strings.pref_configure_general)) },
                        summary = { Text(stringResource(strings.pref_configure_general_summary)) },
                        onClick = {
                            navController.navigateSingleTop(SettingScreens.General)
                        }
                    )

                    preference(
                        key = "pref_configure_editor_key",
                        title = { Text(stringResource(strings.pref_configure_editor)) },
                        summary = { Text(stringResource(strings.pref_configure_editor_summary)) },
                        onClick = {
                            navController.navigateSingleTop(SettingScreens.Editor)
                        }
                    )

                    preference(
                        key = "pref_configure_file_key",
                        title = { Text(stringResource(strings.pref_configure_file_explorer)) },
                        summary = { Text(stringResource(strings.pref_configure_file_explorer_summary)) },
                        onClick = {
                            navController.navigateSingleTop(SettingScreens.File)
                        }
                    )

                    preference(
                        key = "pref_configure_ai_key",
                        title = { Text("AI Agent Configuration") },
                        summary = { Text("Configure API key, model, and AI rewrite behavior") },
                        onClick = {
                            navController.navigateSingleTop(SettingScreens.AI)
                        }
                    )

                    preference(
                        key = "pref_configure_git_key",
                        title = {
                            Text(
                                text = if (user.isNull()) {
                                    stringResource(R.string.login_with_github)
                                } else {
                                    stringResource(
                                        R.string.logged_in_as,
                                        user!!.username,
                                        user!!.name ?: ""
                                    )
                                }
                            )
                        },
                        icon = if (user.isNotNull()) {
                            {
                                AsyncImage(
                                    model = user!!.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 16.dp)
                                        .clip(CircleShape)
                                        .size(40.dp)
                                )
                            }
                        } else null,
                        summary = if (user.isNotNull()) {
                            {
                                Text(
                                    text = user!!.email ?: ""
                                )
                            }
                        } else null,
                        onClick = if (user.isNull()) {
                            {
                                Api.startLogin(uriHandler)
                            }
                        } else {
                            {}
                        }
                    )

                    item { HorizontalDivider(thickness = 2.dp) }

                    preferenceCategory(
                        key = "pref_category_about",
                        title = { Text(stringResource(strings.pref_category_about)) }
                    )

                    preference(
                        key = "about_vsx",
                        title = { Text("Protected Source License") },
                        summary = { Text("This software is proprietary and confidential. Unauthorized copying, distribution, or modification is strictly prohibited.") },
                        onClick = {
                            context.open(AboutActivity::class.java)
                        }
                    )
                }
            }
        }

        composable<SettingScreens.General> {
            ProvidePreferenceLocals {
                GeneralSettingsScreen(
                    modifier = modifier,
                    onNavigateUp = navController::navigateUp
                )
            }
        }

        composable<SettingScreens.File> {
            ProvidePreferenceLocals {
                FileSettingsScreen(
                    modifier = modifier,
                    onNavigateUp = navController::navigateUp
                )
            }
        }

        composable<SettingScreens.Editor> {
            ProvidePreferenceLocals {
                EditorSettingsScreen(
                    modifier = modifier,
                    onNavigateUp = navController::navigateUp,
                    onNavigateToMonacoEditorSettings = {
                        navController.navigateSingleTop(
                            SettingScreens.MonacoEditor
                        )
                    }
                )
            }
        }

        composable<SettingScreens.MonacoEditor> {
            ProvidePreferenceLocals {
                MonacoEditorSettingsScreen(
                    modifier = modifier,
                    onNavigateUp = { navController.navigateSingleTop(SettingScreens.Editor) }
                )
            }
        }

        composable<SettingScreens.AI> {
            ProvidePreferenceLocals {
                AiSettingsScreen(
                    modifier = modifier,
                    onNavigateUp = navController::navigateUp
                )
            }
        }
    }
}

object PreferenceShape {
    // Large outer corners, subtle inner corners — iOS-style grouped list
    val Top = RoundedCornerShape(
        topStart    = 20.dp,
        topEnd      = 20.dp,
        bottomStart = 6.dp,
        bottomEnd   = 6.dp
    )

    val Middle = RoundedCornerShape(
        topStart    = 6.dp,
        topEnd      = 6.dp,
        bottomStart = 6.dp,
        bottomEnd   = 6.dp
    )

    val Bottom = RoundedCornerShape(
        topStart    = 6.dp,
        topEnd      = 6.dp,
        bottomStart = 20.dp,
        bottomEnd   = 20.dp
    )

    val Alone = RoundedCornerShape(20.dp)
}