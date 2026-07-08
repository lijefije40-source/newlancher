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

package com.movtery.zalithlauncher.ui.screens.content

import android.content.Context
import com.movtery.zalithlauncher.game.driver.TurnipDriverManager
import com.movtery.zalithlauncher.utils.device.SnapdragonDetector
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File

private const val TAG = "TurnipDriverManagerScreen"

/**
 * شاشة إدارة تعريفات Mesa Turnip
 * تسمح للمستخدم بتثبيت وحذف واختيار تعريفات Vulkan المخصصة
 */
object TurnipDriverManagerScreen {
    
    /**
     * الحصول على معلومات الجهاز والتعريف الموصى به
     */
    fun getDeviceInfo(context: Context): String {
        val gpuInfo = SnapdragonDetector.getGPUInfo(context)
        
        return buildString {
            appendLine("Device GPU Information:")
            appendLine("----------------------------")
            appendLine("Model: ${gpuInfo.model}")
            appendLine("Renderer: ${gpuInfo.renderer}")
            appendLine("Generation: ${gpuInfo.generation}")
            appendLine("Snapdragon 8: ${if (gpuInfo.isSnapdragon8) "Yes" else "No"}")
            appendLine("Requires Turnip: ${if (gpuInfo.requiresTurnip) "Yes" else "No"}")
            appendLine("Vulkan 1.3: ${if (gpuInfo.hasVulkan1_3) "Yes" else "No"}")
            appendLine()
            
            if (gpuInfo.requiresTurnip) {
                val recommended = SnapdragonDetector.getRecommendedTurnipDriver(context)
                if (recommended != null) {
                    appendLine("Recommended Driver: $recommended")
                } else {
                    appendLine("No specific driver recommendation")
                }
            } else {
                appendLine("Your device does not require Turnip drivers")
            }
        }
    }
    
    /**
     * الحصول على قائمة التعريفات المثبتة
     */
    fun getInstalledDriversList(context: Context): List<DriverInfo> {
        val drivers = TurnipDriverManager.getInstalledDrivers(context)
        val recommended = TurnipDriverManager.getRecommendedDriver(context)
        
        return drivers.map { driver ->
            DriverInfo(
                name = driver.name,
                version = driver.version,
                supportedGPUs = driver.supportedGPUs.joinToString(", "),
                isRecommended = driver == recommended,
                size = formatFileSize(driver.filePath.length())
            )
        }
    }
    
    data class DriverInfo(
        val name: String,
        val version: String,
        val supportedGPUs: String,
        val isRecommended: Boolean,
        val size: String
    )
    
    /**
     * تنسيق حجم الملف
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
    
    /**
     * تثبيت تعريف جديد
     */
    fun installDriver(context: Context, zipFile: File): Result<String> {
        return try {
            val result = TurnipDriverManager.installDriver(context, zipFile)
            
            if (result.isSuccess) {
                val driver = result.getOrNull()!!
                Result.success("Installed ${driver.name} v${driver.version} successfully")
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Installation failed"))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to install driver", e)
            Result.failure(e)
        }
    }
    
    /**
     * حذف تعريف
     */
    fun uninstallDriver(context: Context, driverName: String): Result<String> {
        return try {
            val drivers = TurnipDriverManager.getInstalledDrivers(context)
            val driver = drivers.find { it.name == driverName }
            
            if (driver != null) {
                if (TurnipDriverManager.uninstallDriver(driver)) {
                    Result.success("Uninstalled $driverName successfully")
                } else {
                    Result.failure(Exception("Failed to delete driver file"))
                }
            } else {
                Result.failure(Exception("Driver not found"))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to uninstall driver", e)
            Result.failure(e)
        }
    }
    
    /**
     * الحصول على توصيات التنزيل
     */
    fun getDownloadRecommendations(context: Context): List<DownloadRecommendation> {
        val gpuInfo = SnapdragonDetector.getGPUInfo(context)
        
        if (!gpuInfo.requiresTurnip) {
            return emptyList()
        }
        
        val recommendations = mutableListOf<DownloadRecommendation>()
        
        when (gpuInfo.generation) {
            SnapdragonDetector.AdrenoGeneration.ADRENO_8XX -> {
                recommendations.add(DownloadRecommendation(
                    name = "Turnip A8XX (Latest)",
                    description = "Latest Mesa Turnip for Adreno 8xx (Snapdragon 8 Elite)",
                    githubRepo = "whitebelyash/turnip_builder",
                    fileName = "turnip_a8xx_latest.zip"
                ))
            }
            
            SnapdragonDetector.AdrenoGeneration.ADRENO_7XX -> {
                if (gpuInfo.renderer.contains("750")) {
                    recommendations.add(DownloadRecommendation(
                        name = "Turnip A750 v26.2.0-R7",
                        description = "Optimized for Adreno 750 (Snapdragon 8 Gen 3)",
                        githubRepo = "StevenMXZ/turnip-builder",
                        fileName = "turnip_a750_v26.2.0-R7.zip"
                    ))
                } else {
                    recommendations.add(DownloadRecommendation(
                        name = "Turnip A7XX v26.0.0-R7",
                        description = "For Adreno 730/740 (Snapdragon 8 Gen 1/2)",
                        githubRepo = "K11MCH1/turnip-builder",
                        fileName = "turnip_a7xx_v26.0.0-R7.zip"
                    ))
                }
            }
            
            else -> {}
        }
        
        return recommendations
    }
    
    data class DownloadRecommendation(
        val name: String,
        val description: String,
        val githubRepo: String,
        val fileName: String
    ) {
        fun getGitHubUrl(): String = "https://github.com/$githubRepo/releases"
    }
}
