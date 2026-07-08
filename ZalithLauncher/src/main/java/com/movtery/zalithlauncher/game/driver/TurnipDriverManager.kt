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

package com.movtery.zalithlauncher.game.driver

import android.content.Context
import com.movtery.zalithlauncher.utils.device.SnapdragonDetector
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

private const val TAG = "TurnipDriverManager"

/**
 * مدير تعريفات Mesa Turnip المخصصة لمعالجات Snapdragon 8
 * يتعامل مع تحميل وحقن التعريفات المناسبة لكل جيل من Adreno
 */
object TurnipDriverManager {
    
    data class TurnipDriver(
        val name: String,
        val version: String,
        val supportedGPUs: List<SnapdragonDetector.AdrenoGeneration>,
        val filePath: File,
        val isInstalled: Boolean
    )
    
    private const val TURNIP_DIR_NAME = "turnip_drivers"
    
    /**
     * الحصول على مجلد تعريفات Turnip
     */
    fun getTurnipDirectory(context: Context): File {
        val dir = File(context.filesDir, TURNIP_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * الحصول على قائمة التعريفات المثبتة
     */
    fun getInstalledDrivers(context: Context): List<TurnipDriver> {
        val driversDir = getTurnipDirectory(context)
        val drivers = mutableListOf<TurnipDriver>()
        
        driversDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".zip")) {
                val driverInfo = parseTurnipDriver(file)
                if (driverInfo != null) {
                    drivers.add(driverInfo)
                }
            }
        }
        
        return drivers
    }
    
    /**
     * تحليل معلومات تعريف Turnip من الملف
     */
    private fun parseTurnipDriver(file: File): TurnipDriver? {
        val fileName = file.nameWithoutExtension.lowercase()
        
        return try {
            // تحديد الجيل المدعوم من اسم الملف
            val supportedGPUs = when {
                fileName.contains("a8") || fileName.contains("8xx") -> 
                    listOf(SnapdragonDetector.AdrenoGeneration.ADRENO_8XX)
                    
                fileName.contains("a750") -> 
                    listOf(SnapdragonDetector.AdrenoGeneration.ADRENO_7XX)
                    
                fileName.contains("a7") || fileName.contains("7xx") -> 
                    listOf(SnapdragonDetector.AdrenoGeneration.ADRENO_7XX)
                    
                fileName.contains("a6") || fileName.contains("6xx") -> 
                    listOf(SnapdragonDetector.AdrenoGeneration.ADRENO_6XX)
                    
                else -> emptyList()
            }
            
            // استخراج الإصدار
            val versionRegex = "v?(\\d+\\.\\d+\\.\\d+)".toRegex()
            val version = versionRegex.find(fileName)?.groupValues?.get(1) ?: "Unknown"
            
            TurnipDriver(
                name = file.nameWithoutExtension,
                version = version,
                supportedGPUs = supportedGPUs,
                filePath = file,
                isInstalled = true
            )
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse Turnip driver: ${file.name}", e)
            null
        }
    }
    
    /**
     * الحصول على التعريف الموصى به للجهاز الحالي
     */
    fun getRecommendedDriver(context: Context): TurnipDriver? {
        val gpuInfo = SnapdragonDetector.getGPUInfo(context)
        
        if (!gpuInfo.requiresTurnip) {
            Logger.info(TAG, "Device does not require Turnip driver")
            return null
        }
        
        val installedDrivers = getInstalledDrivers(context)
        
        // البحث عن تعريف مناسب للجيل الحالي
        val compatibleDrivers = installedDrivers.filter { driver ->
            gpuInfo.generation in driver.supportedGPUs
        }
        
        if (compatibleDrivers.isEmpty()) {
            Logger.warning(TAG, "No compatible Turnip driver found for ${gpuInfo.generation}")
            return null
        }
        
        // اختيار أحدث إصدار
        return compatibleDrivers.maxByOrNull { driver ->
            parseVersion(driver.version)
        }
    }
    
    /**
     * تحويل رقم الإصدار إلى رقم صحيح للمقارنة
     */
    private fun parseVersion(version: String): Int {
        return try {
            val parts = version.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            major * 10000 + minor * 100 + patch
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * تثبيت تعريف Turnip من ملف ZIP
     */
    fun installDriver(context: Context, zipFile: File): Result<TurnipDriver> {
        return try {
            val driversDir = getTurnipDirectory(context)
            val targetFile = File(driversDir, zipFile.name)
            
            // نسخ الملف إلى مجلد التعريفات
            zipFile.copyTo(targetFile, overwrite = true)
            
            val driver = parseTurnipDriver(targetFile)
            if (driver != null) {
                Logger.info(TAG, "Installed Turnip driver: ${driver.name} v${driver.version}")
                Result.success(driver)
            } else {
                Result.failure(Exception("Failed to parse driver information"))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to install Turnip driver", e)
            Result.failure(e)
        }
    }
    
    /**
     * حذف تعريف
     */
    fun uninstallDriver(driver: TurnipDriver): Boolean {
        return try {
            driver.filePath.delete()
            Logger.info(TAG, "Uninstalled Turnip driver: ${driver.name}")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to uninstall Turnip driver", e)
            false
        }
    }
    
    /**
     * تطبيق تعريف Turnip على البيئة
     */
    fun applyDriver(context: Context, driver: TurnipDriver?): Map<String, String> {
        val env = mutableMapOf<String, String>()
        
        if (driver == null) {
            Logger.info(TAG, "No Turnip driver to apply")
            return env
        }
        
        val gpuInfo = SnapdragonDetector.getGPUInfo(context)
        
        if (!gpuInfo.requiresTurnip) {
            Logger.info(TAG, "Device does not require Turnip driver")
            return env
        }
        
        // التحقق من التوافق
        if (gpuInfo.generation !in driver.supportedGPUs) {
            Logger.warning(TAG, "Driver ${driver.name} is not compatible with ${gpuInfo.generation}")
            return env
        }
        
        // إعداد متغيرات البيئة للحقن
        val driversDir = getTurnipDirectory(context)
        env["TURNIP_DRIVER_PATH"] = driver.filePath.absolutePath
        env["VULKAN_ICD_FILENAMES"] = "${driversDir.absolutePath}/turnip_icd.json"
        env["VK_ICD_FILENAMES"] = "${driversDir.absolutePath}/turnip_icd.json"
        env["DISABLE_LAYER_AMD_SWITCHABLE_GRAPHICS_1"] = "1"
        
        // تفعيل libadrenotools للحقن
        env["ADRENOTOOLS_DRIVER_FILE_REDIRECT"] = "1"
        
        Logger.info(TAG, "Applied Turnip driver: ${driver.name} v${driver.version}")
        
        return env
    }
    
    /**
     * إنشاء ملف ICD لـ Vulkan
     */
    fun createVulkanICD(context: Context, driver: TurnipDriver): Boolean {
        val driversDir = getTurnipDirectory(context)
        val icdFile = File(driversDir, "turnip_icd.json")
        
        return try {
            val icdContent = """
                {
                    "file_format_version": "1.0.0",
                    "ICD": {
                        "library_path": "${driver.filePath.absolutePath}",
                        "api_version": "1.3.0"
                    }
                }
            """.trimIndent()
            
            icdFile.writeText(icdContent)
            Logger.info(TAG, "Created Vulkan ICD file: ${icdFile.absolutePath}")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create Vulkan ICD file", e)
            false
        }
    }
}
