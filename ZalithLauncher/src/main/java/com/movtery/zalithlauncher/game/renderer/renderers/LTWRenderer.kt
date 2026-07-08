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

package com.movtery.zalithlauncher.game.renderer.renderers

import com.movtery.zalithlauncher.game.renderer.RendererInterface

/**
 * محرك LTW (Lightweight Tangent Wrapper)
 * نسخة محسنة مع إصلاح مشكلة Z-buffer على Adreno 6xx/7xx
 * يعمل مع Minecraft 1.21.5 وما بعدها
 */
object LTWRenderer : RendererInterface {
    override fun getRendererId(): String = "ltw"

    override fun getUniqueIdentifier(): String = "9c4b5d6e-7f8a-4b1c-9d2e-3f4a5b6c7d8e"

    override fun getRendererName(): String = "LTW (Fixed)"
    
    override fun getRendererSummary(): String = "Lightweight wrapper with Z-buffer fix for Adreno GPUs"

    override fun getMinMCVersion(): String? = "1.17"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "MESA_GL_VERSION_OVERRIDE" to "4.6",
            "MESA_GLSL_VERSION_OVERRIDE" to "460",
            // إصلاح Z-buffer لـ Adreno
            "MESA_EXTENSION_OVERRIDE" to "-GL_ARB_depth_clamp",
            "force_glsl_extensions_warn" to "true",
            "LIBGL_ALWAYS_SOFTWARE" to "0"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libgl4es_ltw_fixed.so"
}
