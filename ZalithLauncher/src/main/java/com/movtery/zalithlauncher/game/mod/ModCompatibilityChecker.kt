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

package com.movtery.zalithlauncher.game.mod

import android.content.Context
import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.device.SnapdragonDetector
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File

private const val TAG = "ModCompatibilityChecker"

/**
 * نظام كشف وإدارة تعارضات الإضافات الرسومية
 * يكشف الإضافات غير المتوافقة مع محركات الرسوميات المحمولة
 */
object ModCompatibilityChecker {
    
    data class IncompatibleMod(
        val file: File,
        val modName: String,
        val reason: String,
        val severity: Severity
    )
    
    enum class Severity {
        CRITICAL,  // يسبب انهيار أو شاشة سوداء
        HIGH,      // يسبب مشاكل رسومية كبيرة (rainbow glitch)
        MEDIUM,    // يسبب انخفاض الأداء
        LOW        // يسبب مشاكل بسيطة
    }
    
    // قائمة الإضافات غير المتوافقة مع GL4ES
    private val gl4esIncompatibleMods = mapOf(
        "sodium" to Pair("PC optimization mod - causes rainbow glitch on mobile", Severity.HIGH),
        "embeddium" to Pair("PC optimization mod - causes rainbow glitch on mobile", Severity.HIGH),
        "rubidium" to Pair("PC optimization mod - causes rainbow glitch on mobile", Severity.HIGH),
        "immediatelyfast" to Pair("PC optimization mod - incompatible with GL4ES", Severity.HIGH),
        "iris" to Pair("Shader mod - causes rendering issues on mobile", Severity.CRITICAL),
        "oculus" to Pair("Shader mod - causes rendering issues on mobile", Severity.CRITICAL),
        "optifine" to Pair("May cause compatibility issues with GL4ES", Severity.MEDIUM)
    )
    
    // قائمة الإضافات غير المتوافقة مع ANGLE
    private val angleIncompatibleMods = mapOf(
        "sodium" to Pair("Requires OpenGL 4.3+ features", Severity.CRITICAL),
        "embeddium" to Pair("Requires OpenGL 4.3+ features", Severity.CRITICAL),
        "rubidium" to Pair("Requires OpenGL 4.3+ features", Severity.CRITICAL)
    )
    
    // قائمة الإضافات غير المتوافقة مع LTW
    private val ltwIncompatibleMods = mapOf(
        "sodium" to Pair("May cause Z-buffer issues on Adreno GPUs", Severity.MEDIUM),
        "embeddium" to Pair("May cause Z-buffer issues on Adreno GPUs", Severity.MEDIUM)
    )
    
    /**
     * فحص الإضافات في مجلد معين
     */
    fun checkMods(
        context: Context,
        modsDir: File,
        renderer: RendererInterface
    ): List<IncompatibleMod> {
        if (!modsDir.exists() || !modsDir.isDirectory) {
            return emptyList()
        }
        
        val incompatibleMods = mutableListOf<IncompatibleMod>()
        val rendererName = renderer.getRendererId().lowercase()
        
        // تحديد قائمة التعارضات حسب المحرك
        val incompatibilityMap = when {
            rendererName.contains("gl4es") || rendererName.contains("opengles") -> gl4esIncompatibleMods
            rendererName.contains("angle") -> angleIncompatibleMods
            rendererName.contains("ltw") -> ltwIncompatibleMods
            else -> emptyMap()
        }
        
        // فحص كل ملف في المجلد
        modsDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".jar")) {
                val lowerName = file.name.lowercase()
                
                // البحث عن تطابق
                incompatibilityMap.forEach { (modId, reasonPair) ->
                    if (lowerName.contains(modId)) {
                        incompatibleMods.add(
                            IncompatibleMod(
                                file = file,
                                modName = modId,
                                reason = reasonPair.first,
                                severity = reasonPair.second
                            )
                        )
                    }
                }
            }
        }
        
        if (incompatibleMods.isNotEmpty()) {
            Logger.warning(TAG, "Found ${incompatibleMods.size} incompatible mods with $rendererName:")
            incompatibleMods.forEach { mod ->
                Logger.warning(TAG, "  - ${mod.file.name}: ${mod.reason} (${mod.severity})")
            }
        }
        
        return incompatibleMods
    }
    
    /**
     * تعطيل الإضافات غير المتوافقة بإعادة تسميتها
     */
    fun disableMods(mods: List<IncompatibleMod>): Result<Int> {
        var disabledCount = 0
        val errors = mutableListOf<String>()
        
        mods.forEach { mod ->
            try {
                val newName = "${mod.file.name}.disabled"
                val newFile = File(mod.file.parentFile, newName)
                
                if (mod.file.renameTo(newFile)) {
                    disabledCount++
                    Logger.info(TAG, "Disabled incompatible mod: ${mod.file.name}")
                } else {
                    val error = "Failed to rename ${mod.file.name}"
                    errors.add(error)
                    Logger.error(TAG, error)
                }
            } catch (e: Exception) {
                val error = "Error disabling ${mod.file.name}: ${e.message}"
                errors.add(error)
                Logger.error(TAG, error, e)
            }
        }
        
        return if (errors.isEmpty()) {
            Result.success(disabledCount)
        } else {
            Result.failure(Exception("Failed to disable some mods: ${errors.joinToString(", ")}"))
        }
    }
    
    /**
     * حذف الإضافات غير المتوافقة نهائياً
     */
    fun deleteMods(mods: List<IncompatibleMod>): Result<Int> {
        var deletedCount = 0
        val errors = mutableListOf<String>()
        
        mods.forEach { mod ->
            try {
                if (mod.file.delete()) {
                    deletedCount++
                    Logger.info(TAG, "Deleted incompatible mod: ${mod.file.name}")
                } else {
                    val error = "Failed to delete ${mod.file.name}"
                    errors.add(error)
                    Logger.error(TAG, error)
                }
            } catch (e: Exception) {
                val error = "Error deleting ${mod.file.name}: ${e.message}"
                errors.add(error)
                Logger.error(TAG, error, e)
            }
        }
        
        return if (errors.isEmpty()) {
            Result.success(deletedCount)
        } else {
            Result.failure(Exception("Failed to delete some mods: ${errors.joinToString(", ")}"))
        }
    }
    
    /**
     * إعادة تفعيل الإضافات المعطلة
     */
    fun enableMods(modsDir: File): Result<Int> {
        if (!modsDir.exists() || !modsDir.isDirectory) {
            return Result.failure(Exception("Mods directory does not exist"))
        }
        
        var enabledCount = 0
        val errors = mutableListOf<String>()
        
        modsDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".jar.disabled")) {
                try {
                    val newName = file.name.removeSuffix(".disabled")
                    val newFile = File(file.parentFile, newName)
                    
                    if (file.renameTo(newFile)) {
                        enabledCount++
                        Logger.info(TAG, "Re-enabled mod: $newName")
                    } else {
                        val error = "Failed to rename ${file.name}"
                        errors.add(error)
                        Logger.error(TAG, error)
                    }
                } catch (e: Exception) {
                    val error = "Error enabling ${file.name}: ${e.message}"
                    errors.add(error)
                    Logger.error(TAG, error, e)
                }
            }
        }
        
        return if (errors.isEmpty()) {
            Result.success(enabledCount)
        } else {
            Result.failure(Exception("Failed to enable some mods: ${errors.joinToString(", ")}"))
        }
    }
    
    /**
     * الحصول على توصيات لإصلاح المشاكل
     */
    fun getRecommendations(
        context: Context,
        incompatibleMods: List<IncompatibleMod>,
        renderer: RendererInterface
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (incompatibleMods.isEmpty()) {
            return recommendations
        }
        
        val gpuInfo = SnapdragonDetector.getGPUInfo(context)
        val rendererName = renderer.getRendererId().lowercase()
        
        // توصيات حسب المحرك
        when {
            rendererName.contains("gl4es") -> {
                recommendations.add("Disable or remove PC optimization mods (Sodium, Embeddium, ImmediatelyFast)")
                recommendations.add("Lower render distance to 8-12 chunks")
                recommendations.add("Disable entity shadows in video settings")
                recommendations.add("Set resolution scale to 60-80%")
                
                if (gpuInfo.isSnapdragon8) {
                    recommendations.add("Consider switching to Vulkan Zink renderer for better performance")
                }
            }
            
            rendererName.contains("angle") -> {
                recommendations.add("Remove mods that require OpenGL 4.3+ (Sodium, Embeddium)")
                recommendations.add("Use vanilla Minecraft or Fabric with compatible mods")
                recommendations.add("ANGLE only supports OpenGL 3.0/3.2")
            }
            
            rendererName.contains("ltw") -> {
                recommendations.add("Update to latest LTW version with Z-buffer fix")
                recommendations.add("Disable or remove Sodium/Embeddium if experiencing visual glitches")
                
                if (gpuInfo.generation == SnapdragonDetector.AdrenoGeneration.ADRENO_7XX) {
                    recommendations.add("Consider using Vulkan Zink with Turnip drivers for Adreno 7xx")
                }
            }
            
            rendererName.contains("vulkan") || rendererName.contains("zink") -> {
                if (gpuInfo.requiresTurnip) {
                    recommendations.add("Install Mesa Turnip driver for ${gpuInfo.generation}")
                    recommendations.add("Use libadrenotools for driver injection on non-rooted devices")
                }
            }
        }
        
        return recommendations
    }
}
