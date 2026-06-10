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

package io.vscodex.ai.ui.screens.crash.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.AppUtils
import io.vscodex.ai.app.strings
import io.vscodex.ai.core.components.common.VSXLargeTopBar
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior
) {
    VSXLargeTopBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = stringResource(strings.app_name),
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App crashed,".uppercase(),
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        style = MaterialTheme.typography.labelLarge
                    )

                    val timeNow =
                        DateFormat.format("MMMM d, yyyy 'at' hh:mm:ss a", Calendar.getInstance())

                    Text(
                        text = timeNow.toString(),
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = AppUtils::exitApp) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(strings.close)
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}