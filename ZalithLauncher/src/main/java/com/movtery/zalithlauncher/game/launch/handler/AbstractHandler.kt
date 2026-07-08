/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.launch.handler

import android.view.KeyEvent
import android.view.Surface
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.movtery.zalithlauncher.game.launch.Launcher
import com.movtery.zalithlauncher.game.renderer.Renderers
import com.movtery.zalithlauncher.ui.control.input.TextInputMode
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import com.movtery.zalithlauncher.viewmodel.EventViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class AbstractHandler(
    val type: HandlerType,
    protected val errorViewModel: ErrorViewModel,
    protected val eventViewModel: EventViewModel,
    val launcher: Launcher,
    val onExit: (code: Int) -> Unit
) {
    var mIsSurfaceDestroyed: Boolean = false
    open val inputArea: StateFlow<IntRect?> = MutableStateFlow(null)

    @CallSuper
    open suspend fun execute(
        surface: Surface,
        screenSize: IntSize,
        scope: CoroutineScope
    ) {
        scope.launch(Dispatchers.Default) {
            val code = try {
                launcher.launch(screenSize)
            } catch (e: Exception) {
                Logger.error("AbstractHandler", "Unhandled exception during game launch", e)
                errorViewModel.showError(ErrorViewModel.ThrowableMessage(
                    title = "Launch Error",
                    message = "An unexpected error occurred while launching the game:\n${e.message}\n\nPlease check the game log for more details."
                ))
                -994
            }
            
            // Handle error codes and show appropriate error messages
            if (code != 0) {
                val errorMessage = getErrorMessageForCode(code)
                errorViewModel.showError(ErrorViewModel.ThrowableMessage(
                    title = "Game Launch Error",
                    message = errorMessage
                ))
            }
            
            onExit(code)
        }
    }
    
    private fun getErrorMessageForCode(code: Int): String {
        val rendererInfo = getRendererDebugInfo()
        val baseMsg = when (code) {
            -999 -> "Fatal Error: JVM arguments array was null.\nPlease check your game configuration."
            -998 -> "Fatal Error: JVM arguments array was empty.\nPlease check your game configuration."
            -997 -> "Fatal Error: Failed to convert JVM arguments.\nTry restarting the launcher."
            -996 -> "Fatal Error: Native library loading failed.\nThe game's native libraries could not be loaded. This may be due to:\n• Corrupted installation\n• Incompatible architecture\n• Missing system libraries"
            -995 -> "Fatal Error: JVM launch exception occurred.\nAn unexpected error prevented the game from starting."
            -1 -> "Fatal Error: Java Runtime library (libjli.so) failed to load.\nPossible causes:\n• Java Runtime is corrupted\n• Incompatible JRE version\n• Missing JRE files\n\nTry:\n• Reinstalling the Java Runtime\n• Using a different JRE version"
            in 1..255 -> "Game exited with error code: $code\n\nCommon causes:\n• Out of memory (try increasing RAM allocation)\n• Incompatible mods/modloader\n• Corrupted game files\n• Graphics driver issues"
            else -> if (code < 0) {
                "Unknown fatal error occurred (code: $code)."
            } else {
                "Game exited with unknown code: $code."
            }
        }
        return "$baseMsg\n\n$rendererInfo"
    }

    private fun getRendererDebugInfo(): String {
        return try {
            if (Renderers.isCurrentRendererValid()) {
                val renderer = Renderers.getCurrentRenderer()
                buildString {
                    append("━━━ Renderer Info ━━━")
                    append("\nRenderer: ${renderer.getRendererName()} (${renderer.getRendererId()})")
                    renderer.getRendererSummary()?.let { append("\nSummary: $it") }
                    append("\n\nTry changing the renderer in Settings → Renderer")
                    append("\nIf the issue persists, try a different renderer type")
                }
            } else {
                "Renderer: Not initialized"
            }
        } catch (e: Exception) {
            "Renderer: Unable to get renderer info"
        }
    }

    abstract fun onPause()
    abstract fun onResume()
    abstract fun onDestroy()
    abstract fun onGraphicOutput()
    abstract fun shouldIgnoreKeyEvent(event: KeyEvent): Boolean
    abstract fun sendMouseRight(isPressed: Boolean)

    @Composable
    abstract fun ComposableLayout(
        textInputMode: TextInputMode
    )
}