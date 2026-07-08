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
 * محرك ANGLE - يترجم OpenGL إلى Vulkan
 * يدعم OpenGL 3.0 مع دعم جزئي لـ 3.2
 * مناسب للأجهزة التي تعاني من مشاكل مع Zink أو GL4ES
 */
object ANGLERenderer : RendererInterface {
    override fun getRendererId(): String = "angle"

    override fun getUniqueIdentifier(): String = "7f3a9c8b-1e4d-4f2a-9c7b-8d5e6f3a2b1c"

    override fun getRendererName(): String = "ANGLE"
    
    override fun getRendererSummary(): String = "ANGLE (OpenGL 3.0/3.2) - Vulkan backend"

    override fun getMaxMCVersion(): String? = null // يدعم جميع الإصدارات

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "ANGLE_DEFAULT_PLATFORM" to "vulkan",
            "MESA_GL_VERSION_OVERRIDE" to "3.2",
            "MESA_GLSL_VERSION_OVERRIDE" to "150"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { 
        listOf("libEGL_angle.so", "libGLESv2_angle.so")
    }

    override fun getRendererLibrary(): String = "libgl_angle.so"
    
    override fun getRendererEGL(): String = "libEGL_angle.so"
}
