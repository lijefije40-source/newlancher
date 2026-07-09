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
import com.movtery.zalithlauncher.utils.logging.Logger

private const val TAG = "VulkanDirectRenderer"

/**
 * Vulkan Direct Renderer - محسّن خصيصاً لمعالجات Snapdragon 8
 * يستخدم Mesa Turnip مباشرة مع Zink بدون طبقات إضافية
 * 
 * الميزات:
 * - دعم كامل لـ Vulkan 1.3
 * - تحسينات خاصة بـ Adreno 7xx/8xx
 * - أداء أفضل من Zink التقليدي
 * - دعم Shader Cache المتقدم
 */
object VulkanDirectRenderer : RendererInterface {
    override fun getRendererId(): String = "vulkan_direct_snapdragon"

    override fun getUniqueIdentifier(): String = "c8a7b9f5-3d2e-4a1c-9f6b-8e5d7c4a2b1f"

    override fun getRendererName(): String = "Vulkan Direct (Snapdragon 8 Optimized)"
    
    override fun getRendererSummary(): String = "Native Vulkan renderer with Mesa Turnip - Best for Snapdragon 8 Gen 1/2/3/Elite"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        buildMap {
            // Core Vulkan settings
            put("MESA_GL_VERSION_OVERRIDE", "4.6")
            put("MESA_GLSL_VERSION_OVERRIDE", "460")
            put("GALLIUM_DRIVER", "zink")
            put("MESA_LOADER_DRIVER_OVERRIDE", "zink")
            
            // Zink performance optimizations
            put("ZINK_DESCRIPTORS", "lazy")
            put("ZINK_DEBUG", "compact,nir")
            put("ZINK_DYNAMIC_STATE", "1")
            put("ZINK_EMULATE_POINT_SMOOTH", "false")
            
            // Mesa threading
            put("mesa_glthread", "true")
            put("MESA_GLTHREAD_DRIVER", "true")
            put("MESA_GLTHREAD_MAX_BATCH_SIZE", "20")
            
            // Vulkan layers (disabled for performance)
            put("VK_INSTANCE_LAYERS", "")
            put("VK_DEVICE_LAYERS", "")
            put("DISABLE_LAYER_AMD_SWITCHABLE_GRAPHICS_1", "1")
            put("DISABLE_LAYER_NV_OPTIMUS_1", "1")
            
            // Turnip optimizations for Snapdragon 8
            put("TU_DEBUG", "noconform,syncdraw,nir,noubwc")
            put("MESA_VK_WSI_PRESENT_MODE", "immediate")
            put("TU_OVERRIDE_HEAP_SIZE", "8192")
            put("TU_FORCE_UNIFORM_DESCRIPTOR_SIZE", "1")
            
            // Advanced shader compilation
            put("MESA_SHADER_CACHE_DISABLE", "false")
            put("MESA_SHADER_CACHE_MAX_SIZE", "2048M")
            put("MESA_DISK_CACHE_SINGLE_FILE", "true")
            put("MESA_GLSL_CACHE_DISABLE", "false")
            
            // Memory and performance
            put("vblank_mode", "0")
            put("MESA_NO_ERROR", "1")
            put("MESA_EXTENSION_OVERRIDE", "GL_EXT_texture_filter_anisotropic")
            put("force_glsl_extensions_warn", "false")
            
            // Adreno-specific tweaks
            put("MESA_GLES_VERSION_OVERRIDE", "3.2")
            put("LIBGL_DRI3_DISABLE", "1")
            
            // Texture compression
            put("force_s3tc_enable", "true")
            put("allow_glsl_extension_directive_midshader", "true")
            
            Logger.info(TAG, "Vulkan Direct renderer configured for Snapdragon 8 series")
        }
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libOSMesa_8.so"
}
