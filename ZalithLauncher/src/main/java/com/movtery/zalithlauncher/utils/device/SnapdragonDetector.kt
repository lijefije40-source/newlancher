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

package com.movtery.zalithlauncher.utils.device

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.os.Build
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File

private const val TAG = "SnapdragonDetector"

/**
 * كاشف معالجات Snapdragon وكروت Adreno لتحديد المشاكل التوافقية
 */
object SnapdragonDetector {
    
    enum class AdrenoGeneration {
        ADRENO_6XX,    // Snapdragon 8 Gen 1/Gen 2
        ADRENO_7XX,    // Snapdragon 8 Gen 1/Gen 2/Gen 3
        ADRENO_8XX,    // Snapdragon 8 Elite
        MALI,          // Mali GPUs
        POWERVR,       // PowerVR GPUs
        NVIDIA,        // Nvidia Tegra
        UNKNOWN
    }
    
    data class DeviceGPUInfo(
        val generation: AdrenoGeneration,
        val model: String,
        val renderer: String,
        val isSnapdragon8: Boolean,
        val requiresTurnip: Boolean,
        val hasVulkan1_3: Boolean
    )
    
    private var cachedGPUInfo: DeviceGPUInfo? = null
    
    /**
     * الحصول على معلومات GPU الخاصة بالجهاز
     */
    fun getGPUInfo(context: Context): DeviceGPUInfo {
        cachedGPUInfo?.let { return it }
        
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val configInfo = activityManager.deviceConfigurationInfo
        
        // قراءة معلومات GPU من OpenGL
        val renderer = try {
            GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to get GL_RENDERER", e)
            "Unknown"
        }
        
        val vendor = try {
            GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to get GL_VENDOR", e)
            "Unknown"
        }
        
        Logger.info(TAG, "Device GPU: Renderer=$renderer, Vendor=$vendor, Model=${Build.MODEL}, SoC=${Build.HARDWARE}")
        
        val generation = detectAdrenoGeneration(renderer, Build.HARDWARE)
        val isSD8 = isSnapdragon8Series(Build.HARDWARE, generation)
        val requiresTurnip = generation in listOf(AdrenoGeneration.ADRENO_7XX, AdrenoGeneration.ADRENO_8XX) && isSD8
        val hasVulkan13 = checkVulkan13Support(context)
        
        val info = DeviceGPUInfo(
            generation = generation,
            model = Build.MODEL,
            renderer = renderer,
            isSnapdragon8 = isSD8,
            requiresTurnip = requiresTurnip,
            hasVulkan1_3 = hasVulkan13
        )
        
        cachedGPUInfo = info
        Logger.info(TAG, "GPU Info: generation=$generation, isSD8=$isSD8, requiresTurnip=$requiresTurnip, vulkan1.3=$hasVulkan13")
        
        return info
    }
    
    /**
     * كشف جيل Adreno من معلومات Renderer
     */
    private fun detectAdrenoGeneration(renderer: String, hardware: String): AdrenoGeneration {
        val lowerRenderer = renderer.lowercase()
        val lowerHardware = hardware.lowercase()
        
        return when {
            // كشف Adreno 8xx (Snapdragon 8 Elite)
            lowerRenderer.contains("adreno") && (
                lowerRenderer.contains("830") || 
                lowerRenderer.contains("8") && lowerHardware.contains("elite")
            ) -> AdrenoGeneration.ADRENO_8XX
            
            // كشف Adreno 7xx (Snapdragon 8 Gen 1/2/3)
            lowerRenderer.contains("adreno") && (
                lowerRenderer.contains("730") || 
                lowerRenderer.contains("740") || 
                lowerRenderer.contains("750") ||
                lowerRenderer.contains("7")
            ) -> AdrenoGeneration.ADRENO_7XX
            
            // كشف Adreno 6xx
            lowerRenderer.contains("adreno") && (
                lowerRenderer.contains("6") ||
                lowerRenderer.contains("660") ||
                lowerRenderer.contains("650") ||
                lowerRenderer.contains("640")
            ) -> AdrenoGeneration.ADRENO_6XX
            
            // كشف Mali
            lowerRenderer.contains("mali") -> AdrenoGeneration.MALI
            
            // كشف PowerVR
            lowerRenderer.contains("powervr") -> AdrenoGeneration.POWERVR
            
            // كشف Nvidia
            lowerRenderer.contains("nvidia") || lowerRenderer.contains("tegra") -> AdrenoGeneration.NVIDIA
            
            else -> AdrenoGeneration.UNKNOWN
        }
    }
    
    /**
     * التحقق من أن الجهاز يحمل معالج من سلسلة Snapdragon 8
     */
    private fun isSnapdragon8Series(hardware: String, generation: AdrenoGeneration): Boolean {
        val lowerHardware = hardware.lowercase()
        
        // كشف مباشر من اسم المعالج
        val isSD8Direct = lowerHardware.contains("sm8") || // SM8xxx هو رمز Snapdragon 8
                         lowerHardware.contains("pineapple") || // Snapdragon 8 Gen 3
                         lowerHardware.contains("kalama") || // Snapdragon 8 Gen 2
                         lowerHardware.contains("taro") || // Snapdragon 8 Gen 1
                         lowerHardware.contains("sun") // Snapdragon 8 Elite
        
        // كشف غير مباشر من GPU
        val isSD8FromGPU = generation in listOf(
            AdrenoGeneration.ADRENO_7XX, 
            AdrenoGeneration.ADRENO_8XX
        )
        
        return isSD8Direct || isSD8FromGPU
    }
    
    /**
     * التحقق من دعم Vulkan 1.3
     */
    private fun checkVulkan13Support(context: Context): Boolean {
        // قراءة إصدار Vulkan من PackageManager
        val packageManager = context.packageManager
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val features = packageManager.systemAvailableFeatures
                features.any { feature ->
                    feature.name?.contains("vulkan") == true && feature.version >= 0x401000 // Vulkan 1.3 = 0x401000
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to check Vulkan 1.3 support", e)
            false
        }
    }
    
    /**
     * التحقق من الحاجة لتفعيل libadrenotools للحقن
     */
    fun needsAdrenoToolsInjection(context: Context): Boolean {
        val info = getGPUInfo(context)
        return info.requiresTurnip && !info.hasVulkan1_3
    }
    
    /**
     * الحصول على مسار تعريف Turnip المناسب للمعالج
     */
    fun getRecommendedTurnipDriver(context: Context): String? {
        val info = getGPUInfo(context)
        
        return when {
            !info.requiresTurnip -> null
            
            info.generation == AdrenoGeneration.ADRENO_8XX -> {
                // Snapdragon 8 Elite يحتاج A8XX Turnip
                "turnip_a8xx_latest.zip"
            }
            
            info.generation == AdrenoGeneration.ADRENO_7XX -> {
                // Snapdragon 8 Gen 1/2/3 يحتاج A7XX Turnip
                when {
                    info.renderer.contains("750") -> "turnip_a750_v26.2.0-R7.zip"
                    info.renderer.contains("740") -> "turnip_a7xx_v26.0.0-R7.zip"
                    info.renderer.contains("730") -> "turnip_a7xx_v26.0.0-R7.zip"
                    else -> "turnip_a7xx_v26.0.0-R7.zip"
                }
            }
            
            else -> null
        }
    }
    
    /**
     * التحقق من تعارض إضافات تحسين الأداء مع GL4ES
     */
    fun hasIncompatibleModsForGL4ES(modsDir: File): List<String> {
        if (!modsDir.exists() || !modsDir.isDirectory) return emptyList()
        
        val incompatibleMods = listOf(
            "sodium",
            "embeddium", 
            "immediatelyfast",
            "iris",
            "oculus"
        )
        
        val foundIncompatible = mutableListOf<String>()
        
        modsDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".jar")) {
                val lowerName = file.name.lowercase()
                incompatibleMods.forEach { modName ->
                    if (lowerName.contains(modName)) {
                        foundIncompatible.add(file.name)
                    }
                }
            }
        }
        
        return foundIncompatible
    }
    
    /**
     * التحقق من مشاكل LTW Z-buffer المعروفة
     */
    fun hasLTWZBufferIssue(minecraftVersion: String): Boolean {
        // LTW لديه مشكلة Z-buffer في 1.21.5 وما بعدها على Adreno 6xx/7xx
        val versionParts = minecraftVersion.split(".")
        if (versionParts.size < 2) return false
        
        try {
            val major = versionParts[0].toIntOrNull() ?: return false
            val minor = versionParts[1].toIntOrNull() ?: return false
            val patch = if (versionParts.size > 2) versionParts[2].toIntOrNull() ?: 0 else 0
            
            // 1.21.5 وما بعدها
            return major > 1 || (major == 1 && minor > 21) || (major == 1 && minor == 21 && patch >= 5)
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to parse Minecraft version: $minecraftVersion", e)
            return false
        }
    }
}
