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

package io.vscodex.ai.git

import org.eclipse.jgit.lib.BatchingProgressMonitor
import java.time.Duration

abstract class SimpleProgressMonitor : BatchingProgressMonitor() {
    override fun onUpdate(taskName: String?, workCurr: Int, duration: Duration?) {}

    override fun onUpdate(
        taskName: String,
        workCurr: Int,
        workTotal: Int,
        percentDone: Int,
        duration: Duration?
    ) {
        onUpdate(percentDone, taskName)
    }

    override fun onEndTask(taskName: String?, workCurr: Int, duration: Duration?) {}

    override fun onEndTask(
        taskName: String?,
        workCurr: Int,
        workTotal: Int,
        percentDone: Int,
        duration: Duration?
    ) {
    }

    abstract fun onUpdate(progress: Int, taskName: String)
}