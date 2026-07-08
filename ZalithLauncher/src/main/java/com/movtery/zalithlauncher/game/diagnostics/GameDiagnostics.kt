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

package com.movtery.zalithlauncher.game.diagnostics

import android.app.ActivityManager
import android.content.Context
import com.movtery.zalithlauncher.game.driver.TurnipDriverManager
import com.movtery.zalithlauncher.game.mod.ModCompatibilityChecker
import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.game.renderer.Renderers
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionFolders
import com.movtery.zalithlauncher.utils.device.SnapdragonDetector
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File

private const val TAG = "GameDiagnostics"

/**
 * نظام التشخيص التلقائي وإصلاح المشاكل الشائعة
 */
object GameDiagnostics {
    
    data class DiagnosticResult(
        val issues: List<Issue>,
        val warnings: List<Warning>,
        val recommendations: List<String>,
        val autoFixAvailable: Boolean
    )
    
    data class Issue(
        val title: String,
        val description: String,
        val severity: Severity,
        val autoFixable: Boolean,
        val fix: (() -> Boolean)? = null
    )
    
    data class Warning(
        val title: String,
        val description: String
    )
    
    enum class Severity {
        CRITICAL,  // يمنع تشغيل اللعبة
        HIGH,      // يسبب انهيارات متكررة
        MEDIUM,    // يسبب مشاكل رسومية
        LOW        // يسبب انخفاض الأداء
    }
    
    /**
     * تشخيص شامل قبل تشغيل اللعبة
     */
    fun diagnose(
        context: Context,
        version: Version,
        renderer: RendererInterface
    ): DiagnosticResult {
        Logger.info(TAG, "Starting game diagnostics for ${version.getVersionName()} with ${renderer.getRendererName()}")
        
        val issues = mutableListOf<Issue>()
        val warnings = mutableListOf<Warning>()
        val recommendations = mutableListOf<String>()
        
        val gpuInfo = SnapdragonDetector.getGPUInfo(context)
        val modsDir = VersionFolders.MOD.getDir(version.getGameDir())
        
        // 1. فحص توافق المحرك مع المعالج
        checkRendererCompatibility(context, renderer, gpuInfo, issues, warnings, recommendations)
        
        // 2. فحص الإضافات المتعارضة
        checkModCompatibility(context, modsDir, renderer, issues, warnings, recommendations)
        
        // 3. فحص تعريفات Turnip
        checkTurnipDrivers(context, renderer, gpuInfo, issues, warnings, recommendations)
        
        // 4. فحص الذاكرة العشوائية
        checkMemoryAllocation(context, version, warnings, recommendations)
        
        // 5. فحص إصدار Minecraft ومشاكل معروفة
        checkMinecraftVersion(context, version, renderer, gpuInfo, warnings, recommendations)
        
        val autoFixAvailable = issues.any { it.autoFixable }
        
        Logger.info(TAG, "Diagnostics complete: ${issues.size} issues, ${warnings.size} warnings")
        
        return DiagnosticResult(issues, warnings, recommendations, autoFixAvailable)
    }
    
    /**
     * فحص توافق المحرك مع المعالج
     */
    private fun checkRendererCompatibility(
        context: Context,
        renderer: RendererInterface,
        gpuInfo: SnapdragonDetector.DeviceGPUInfo,
        issues: MutableList<Issue>,
        warnings: MutableList<Warning>,
        recommendations: MutableList<String>
    ) {
        val rendererName = renderer.getRendererId().lowercase()
        
        // مشاكل Snapdragon 8 مع Zink بدون Turnip
        if (rendererName.contains("zink") && gpuInfo.isSnapdragon8) {
            if (gpuInfo.requiresTurnip && !gpuInfo.hasVulkan1_3) {
                val recommendedDriver = SnapdragonDetector.getRecommendedTurnipDriver(context)
                
                if (recommendedDriver != null) {
                    val drivers = TurnipDriverManager.getInstalledDrivers(context)
                    if (drivers.isEmpty()) {
                        issues.add(Issue(
                            title = "Missing Turnip Driver",
                            description = "Snapdragon ${gpuInfo.generation} requires Mesa Turnip driver for Vulkan Zink. Install $recommendedDriver",
                            severity = Severity.CRITICAL,
                            autoFixable = false
                        ))
                        recommendations.add("Download and install $recommendedDriver from GitHub")
                    }
                } else {
                    warnings.add(Warning(
                        title = "Stock Vulkan Driver",
                        description = "Using stock Vulkan drivers. May cause crashes or red screen on ${gpuInfo.generation}"
                    ))
                    recommendations.add("Consider installing Mesa Turnip driver for better compatibility")
                }
            }
        }
        
        // مشاكل GL4ES مع Adreno 7xx/8xx
        if (rendererName.contains("gl4es") && gpuInfo.generation in listOf(
            SnapdragonDetector.AdrenoGeneration.ADRENO_7XX,
            SnapdragonDetector.AdrenoGeneration.ADRENO_8XX
        )) {
            warnings.add(Warning(
                title = "GL4ES on Modern Adreno",
                description = "GL4ES may have lower performance on ${gpuInfo.generation}. Consider using Vulkan Zink."
            ))
            recommendations.add("Switch to Vulkan Zink renderer for better performance on Snapdragon 8")
        }
    }
    
    /**
     * فحص تعارضات الإضافات
     */
    private fun checkModCompatibility(
        context: Context,
        modsDir: File,
        renderer: RendererInterface,
        issues: MutableList<Issue>,
        warnings: MutableList<Warning>,
        recommendations: MutableList<String>
    ) {
        val incompatibleMods = ModCompatibilityChecker.checkMods(context, modsDir, renderer)
        
        if (incompatibleMods.isNotEmpty()) {
            val criticalMods = incompatibleMods.filter { it.severity == ModCompatibilityChecker.Severity.CRITICAL }
            val highMods = incompatibleMods.filter { it.severity == ModCompatibilityChecker.Severity.HIGH }
            
            if (criticalMods.isNotEmpty()) {
                issues.add(Issue(
                    title = "Critical Mod Conflicts",
                    description = "Found ${criticalMods.size} mods that will crash the game: ${criticalMods.joinToString(", ") { it.modName }}",
                    severity = Severity.CRITICAL,
                    autoFixable = true,
                    fix = {
                        ModCompatibilityChecker.disableMods(criticalMods).isSuccess
                    }
                ))
            }
            
            if (highMods.isNotEmpty()) {
                issues.add(Issue(
                    title = "Performance-Breaking Mods",
                    description = "Found ${highMods.size} mods causing rainbow glitch/low FPS: ${highMods.joinToString(", ") { it.modName }}",
                    severity = Severity.HIGH,
                    autoFixable = true,
                    fix = {
                        ModCompatibilityChecker.disableMods(highMods).isSuccess
                    }
                ))
            }
            
            recommendations.addAll(ModCompatibilityChecker.getRecommendations(context, incompatibleMods, renderer))
        }
    }
    
    /**
     * فحص تعريفات Turnip
     */
    private fun checkTurnipDrivers(
        context: Context,
        renderer: RendererInterface,
        gpuInfo: SnapdragonDetector.DeviceGPUInfo,
        issues: MutableList<Issue>,
        warnings: MutableList<Warning>,
        recommendations: MutableList<String>
    ) {
        val rendererName = renderer.getRendererId().lowercase()
        
        if (rendererName.contains("vulkan") || rendererName.contains("zink")) {
            if (gpuInfo.requiresTurnip) {
                val recommendedDriver = TurnipDriverManager.getRecommendedDriver(context)
                
                if (recommendedDriver == null) {
                    warnings.add(Warning(
                        title = "No Turnip Driver",
                        description = "Mesa Turnip driver not installed. Game may crash or show red screen."
                    ))
                    
                    val suggestedDriver = SnapdragonDetector.getRecommendedTurnipDriver(context)
                    if (suggestedDriver != null) {
                        recommendations.add("Install $suggestedDriver for ${gpuInfo.generation}")
                    }
                }
            }
        }
    }
    
    /**
     * فحص تخصيص الذاكرة العشوائية
     */
    private fun checkMemoryAllocation(
        context: Context,
        version: Version,
        warnings: MutableList<Warning>,
        recommendations: MutableList<String>
    ) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val totalMemoryGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        
        // التحقق من التخصيص الزائد
        // TODO: قراءة التخصيص الحالي من إعدادات الإصدار
        val recommendedMaxGB = (totalMemoryGB * 0.5).coerceAtMost(6.0)
        
        recommendations.add("Recommended RAM allocation: ${recommendedMaxGB.toInt()}GB (max 6GB)")
        recommendations.add("Avoid allocating more than 50% of device memory")
    }
    
    /**
     * فحص إصدار Minecraft ومشاكل معروفة
     */
    private fun checkMinecraftVersion(
        context: Context,
        version: Version,
        renderer: RendererInterface,
        gpuInfo: SnapdragonDetector.DeviceGPUInfo,
        warnings: MutableList<Warning>,
        recommendations: MutableList<String>
    ) {
        val mcVersion = version.getVersionInfo()?.minecraftVersion ?: version.getVersionName()
        val rendererName = renderer.getRendererId().lowercase()
        
        // مشكلة LTW Z-buffer في 1.21.5+
        if (rendererName.contains("ltw") && SnapdragonDetector.hasLTWZBufferIssue(mcVersion)) {
            if (gpuInfo.generation in listOf(
                SnapdragonDetector.AdrenoGeneration.ADRENO_6XX,
                SnapdragonDetector.AdrenoGeneration.ADRENO_7XX
            )) {
                warnings.add(Warning(
                    title = "LTW Z-Buffer Issue",
                    description = "Minecraft $mcVersion has Z-buffer issues with LTW on ${gpuInfo.generation}"
                ))
                recommendations.add("Update to latest LTW version with Z-buffer fix")
                recommendations.add("Or switch to Vulkan Zink renderer")
            }
        }
    }
    
    /**
     * تطبيق الإصلاحات التلقائية
     */
    fun applyAutoFixes(result: DiagnosticResult): Result<String> {
        val fixableIssues = result.issues.filter { it.autoFixable && it.fix != null }
        
        if (fixableIssues.isEmpty()) {
            return Result.failure(Exception("No auto-fixable issues found"))
        }
        
        var fixedCount = 0
        val errors = mutableListOf<String>()
        
        fixableIssues.forEach { issue ->
            try {
                if (issue.fix?.invoke() == true) {
                    fixedCount++
                    Logger.info(TAG, "Fixed issue: ${issue.title}")
                } else {
                    val error = "Failed to fix: ${issue.title}"
                    errors.add(error)
                    Logger.warning(TAG, error)
                }
            } catch (e: Exception) {
                val error = "Error fixing ${issue.title}: ${e.message}"
                errors.add(error)
                Logger.error(TAG, error, e)
            }
        }
        
        val message = "Fixed $fixedCount out of ${fixableIssues.size} issues"
        
        return if (errors.isEmpty()) {
            Result.success(message)
        } else {
            Result.failure(Exception("$message. Errors: ${errors.joinToString(", ")}"))
        }
    }
}
