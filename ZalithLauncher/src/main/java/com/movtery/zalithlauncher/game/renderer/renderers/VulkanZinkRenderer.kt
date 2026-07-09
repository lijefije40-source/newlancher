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

import android.content.Context
import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.device.SnapdragonDetector
import com.movtery.zalithlauncher.utils.logging.Logger

private const val TAG = "VulkanZinkRenderer"

object VulkanZinkRenderer : RendererInterface {
    override fun getRendererId(): String = "vulkan_zink"

    override fun getUniqueIdentifier(): String = "0fa435e2-46df-45c9-906c-b29606aaef00"

    override fun getRendererName(): String = "Vulkan Zink"
    
    override fun getRendererSummary(): String = "OpenGL to Vulkan translation with Mesa Turnip support for Snapdragon 8"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        buildMap {
            // Mesa Zink core settings
            put("MESA_GL_VERSION_OVERRIDE", "4.6")
            put("MESA_GLSL_VERSION_OVERRIDE", "460")
            put("GALLIUM_DRIVER", "zink")
            put("MESA_LOADER_DRIVER_OVERRIDE", "zink")
            
            // Zink optimizations for Snapdragon 8
            put("ZINK_DESCRIPTORS", "lazy")
            put("ZINK_DEBUG", "compact")
            put("mesa_glthread", "true")
            put("MESA_GLTHREAD_DRIVER", "true")
            
            // Vulkan layer optimizations
            put("VK_INSTANCE_LAYERS", "")
            put("DISABLE_LAYER_AMD_SWITCHABLE_GRAPHICS_1", "1")
            
            // Turnip-specific optimizations for Adreno 7xx/8xx
            put("TU_DEBUG", "noconform,syncdraw")
            put("MESA_VK_WSI_PRESENT_MODE", "mailbox")
            
            // Performance tweaks
            put("MESA_SHADER_CACHE_DISABLE", "false")
            put("MESA_SHADER_CACHE_MAX_SIZE", "1024M")
            put("vblank_mode", "0")
            
            // Adreno-specific settings
            put("MESA_EXTENSION_OVERRIDE", "GL_EXT_texture_filter_anisotropic")
            put("force_glsl_extensions_warn", "false")
            
            Logger.info(TAG, "Vulkan Zink environment configured for Snapdragon 8")
        }
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libOSMesa_8.so"
}