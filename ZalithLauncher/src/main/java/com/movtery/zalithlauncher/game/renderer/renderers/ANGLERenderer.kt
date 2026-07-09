package com.movtery.zalithlauncher.game.renderer.renderers

import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.logging.Logger

private const val TAG = "ANGLERenderer"

/**
 * محرك ANGLE - يترجم OpenGL إلى Vulkan
 * يدعم OpenGL 3.0 مع دعم جزئي لـ 3.2
 * مناسب للأجهزة التي تعاني من مشاكل مع Zink أو GL4ES
 * محسّن لـ Snapdragon 8 مع دعم Turnip
 */
object ANGLERenderer : RendererInterface {
    override fun getRendererId(): String = "angle"

    override fun getUniqueIdentifier(): String = "7f3a9c8b-1e4d-4f2a-9c7b-8d5e6f3a2b1c"

    override fun getRendererName(): String = "ANGLE"
    
    override fun getRendererSummary(): String = "ANGLE (OpenGL 3.0/3.2) - Vulkan backend optimized for Snapdragon"

    override fun getMaxMCVersion(): String? = null // يدعم جميع الإصدارات

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        buildMap {
            // ANGLE Vulkan backend
            put("ANGLE_DEFAULT_PLATFORM", "vulkan")
            put("ANGLE_ENABLE_VULKAN", "1")
            
            // OpenGL version override
            put("MESA_GL_VERSION_OVERRIDE", "3.2")
            put("MESA_GLSL_VERSION_OVERRIDE", "150")
            
            // Disable validation for performance
            put("ANGLE_ENABLE_DEBUG_LAYERS", "0")
            put("ANGLE_ENABLE_VALIDATION_LAYERS", "0")
            
            // Vulkan optimizations
            put("VK_INSTANCE_LAYERS", "")
            put("DISABLE_LAYER_AMD_SWITCHABLE_GRAPHICS_1", "1")
            
            // ANGLE performance features
            put("ANGLE_FEATURE_OVERRIDES_ENABLED", "preferSubmitAtFBOBoundary:preferCPUForBufferSubData")
            put("ANGLE_ENABLE_SHARE_CONTEXT_MUTEX", "0")
            
            // Turnip optimizations for ANGLE
            put("TU_DEBUG", "noconform")
            put("MESA_VK_WSI_PRESENT_MODE", "mailbox")
            
            Logger.info(TAG, "ANGLE renderer configured with Vulkan backend for Snapdragon")
        }
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { 
        listOf("libEGL_angle.so", "libGLESv2_angle.so")
    }

    override fun getRendererLibrary(): String = "libgl_angle.so"
    
    override fun getRendererEGL(): String = "libEGL_angle.so"
}
