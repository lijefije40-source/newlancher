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

package com.movtery.zalithlauncher.game.launch

import android.app.Activity
import android.os.Build
import android.os.Parcelable
import android.widget.Toast
import androidx.compose.ui.unit.IntSize
import com.movtery.zalithlauncher.BuildConfig
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ZLApplication
import com.movtery.zalithlauncher.bridge.LoggerBridge
import com.movtery.zalithlauncher.bridge.LoggerBridge.append
import com.movtery.zalithlauncher.bridge.LoggerBridge.appendTitle
import com.movtery.zalithlauncher.bridge.ZLBridge
import com.movtery.zalithlauncher.context.readAssetFile
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountType
import com.movtery.zalithlauncher.game.account.offline.OfflineYggdrasilServer
import com.movtery.zalithlauncher.game.addons.modloader.ModLoader
import com.movtery.zalithlauncher.game.download.game.parseLibraryComponents
import com.movtery.zalithlauncher.game.driver.TurnipDriverManager
import com.movtery.zalithlauncher.game.multirt.Runtime
import com.movtery.zalithlauncher.game.multirt.RuntimesManager
import com.movtery.zalithlauncher.game.path.GamePathManager
import com.movtery.zalithlauncher.game.plugin.driver.DriverPluginManager
import com.movtery.zalithlauncher.game.plugin.renderer.RendererPluginManager
import com.movtery.zalithlauncher.game.renderer.Renderers
import com.movtery.zalithlauncher.game.support.touch_controller.ControllerProxy
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionInfoParser
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.game.versioninfo.models.GameManifest
import com.movtery.zalithlauncher.path.LibPath
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.GSON
import com.movtery.zalithlauncher.utils.device.Architecture
import com.movtery.zalithlauncher.utils.file.child
import com.movtery.zalithlauncher.utils.file.ensureDirectorySilently
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.utils.string.isBiggerTo
import com.movtery.zalithlauncher.utils.string.isEqualTo
import kotlinx.parcelize.Parcelize
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext

private const val TAG = "GameLauncher"

@Parcelize
class LaunchConfig(
    val version: Version,
    val account: Account,
): Parcelable

class GameLauncher(
    private val activity: Activity,
    config: LaunchConfig,
    onExit: (code: Int, isSignal: Boolean) -> Unit,
    openPath: (folder: File) -> Unit
) : Launcher(onExit, openPath) {
    
    private lateinit var gameManifest: GameManifest
    
    private val version: Version
    private val usingAccount: Account
    private val offlineServer by lazy {
        Logger.info(TAG, "Lazy initialization of OfflineYggdrasilServer started")
        LoggerBridge.append("▷ Initializing offline server...")
        createOfflineServer().also {
            Logger.info(TAG, "Lazy initialization of OfflineYggdrasilServer completed")
            LoggerBridge.append("▷ Offline server ready")
        }
    }
    
    init {
        try {
            Logger.info(TAG, "GameLauncher init started")
            LoggerBridge.append("=".repeat(50))
            LoggerBridge.append("▷ GameLauncher initialization")
            Logger.info(TAG, "Version: ${config.version.getVersionName()}, Account: ${config.account.username}")
            LoggerBridge.append("▷ Version: ${config.version.getVersionName()}")
            LoggerBridge.append("▷ Account: ${config.account.username}")
            
            Logger.info(TAG, "Setting up version...")
            LoggerBridge.append("▷ Setting up version...")
            version = config.version
            Logger.info(TAG, "Version setup completed")
            LoggerBridge.append("▷ Version ready")
            
            Logger.info(TAG, "Setting up account...")
            LoggerBridge.append("▷ Setting up account...")
            usingAccount = if (config.version.offlineAccountLogin) {
                Logger.info(TAG, "Using offline account login")
                LoggerBridge.append("▷ Using offline account mode")
                config.account.copy(
                    accountType = AccountType.LOCAL.tag
                )
            } else {
                Logger.info(TAG, "Using online account: ${config.account.accountType}")
                LoggerBridge.append("▷ Using online account: ${config.account.accountType}")
                config.account
            }
            Logger.info(TAG, "Account setup completed")
            LoggerBridge.append("▷ Account ready")
            
            Logger.info(TAG, "GameLauncher initialization completed successfully")
            LoggerBridge.append("▷ GameLauncher ready")
            LoggerBridge.append("=".repeat(50))
        } catch (e: Exception) {
            Logger.error(TAG, "FATAL ERROR in GameLauncher constructor: ${e.message}", e)
            LoggerBridge.append("▷ FATAL ERROR in initialization: ${e.message}")
            throw e
        }
    }
    
    private fun createOfflineServer(): OfflineYggdrasilServer {
        Logger.info(TAG, "createOfflineServer() called")
        LoggerBridge.append("▷ Creating OfflineYggdrasilServer...")
        
        val startTime = System.currentTimeMillis()
        return try {
            Logger.info(TAG, "Attempting to create OfflineYggdrasilServer instance")
            val server = OfflineYggdrasilServer(0)
            val elapsedTime = System.currentTimeMillis() - startTime
            Logger.info(TAG, "OfflineYggdrasilServer created successfully in ${elapsedTime}ms")
            LoggerBridge.append("▷ OfflineYggdrasilServer created in ${elapsedTime}ms")
            
            if (elapsedTime > 3000) {
                Logger.warning(TAG, "Server creation took ${elapsedTime}ms (slow)")
                LoggerBridge.append("▷ WARNING: Server creation took ${elapsedTime}ms")
            }
            
            server
        } catch (e: Exception) {
            val elapsedTime = System.currentTimeMillis() - startTime
            Logger.error(TAG, "ERROR creating OfflineYggdrasilServer after ${elapsedTime}ms: ${e.message}", e)
            LoggerBridge.append("▷ ERROR creating OfflineYggdrasilServer: ${e.message}")
            throw e
        }
    }

    override fun exit() {
        offlineServer.stop()
    }

    override suspend fun launch(screenSize: IntSize): Int {
        return try {
            LoggerBridge.append("=".repeat(50))
            LoggerBridge.append("GameLauncher.launch() called")
            LoggerBridge.append("▷ Screen size: ${screenSize.width}x${screenSize.height}")
            LoggerBridge.append("▷ Version: ${version.getVersionName()}")
            LoggerBridge.append("▷ Account: ${usingAccount.username}")
            LoggerBridge.append("=".repeat(50))

            if (!Renderers.isCurrentRendererValid()) {
                LoggerBridge.append("▷ Setting current renderer...")
                Renderers.setCurrentRenderer(activity, version.getRenderer())
            }

            val manifest = GSON.fromJson(File(version.getVersionPath(), "${version.getVersionName()}.json").readText(), GameManifest::class.java)
            val clientJar = manifest.inheritsFrom?.let { inheritsFrom ->
                //FIXME: 依赖的是一个原版ID的版本，但这个版本可能是用户自行安装的，只是版本名称与ID一致，不保证客户端真的是对应版本
                VersionsManager.getVersion(inheritsFrom)?.getClientJar()
            } ?: version.getClientJar()

            gameManifest = VersionInfoParser(version)
                .setManifest(manifest)
                .setInheriting()
                .build()

            CallbackBridge.nativeSetUseInputStackQueue(gameManifest.arguments != null)

            val customArgs = version.getJvmArgs().takeIf { it.isNotBlank() } ?: AllSettings.jvmArgs.getValue()
            val javaRuntime = getRuntime()

            printLauncherInfo(
                javaArguments = customArgs.takeIf { it.isNotEmpty() } ?: "NONE",
                javaRuntime = javaRuntime,
            )

            launchGame(
                screenSize = screenSize,
                clientJar = clientJar,
                javaRuntime = javaRuntime,
                customArgs = customArgs,
            )
        } catch (e: Exception) {
            Logger.error(TAG, "Fatal error during game launch", e)
            LoggerBridge.append("FATAL ERROR during launch: ${e.message}")
            -994
        }
    }

    override fun MutableMap<String, String>.putJavaArgs() {
        val versionInfo = version.getVersionInfo()
        //Fix Forge 1.7.2
        val is172 = (versionInfo?.minecraftVersion ?: "0.0").isEqualTo("1.7.2")
        if (is172 && (versionInfo?.loaderInfo?.loader == ModLoader.FORGE)) {
            Logger.debug(TAG, "Is Forge 1.7.2, use the patched sorting method.")
            put("sort.patch", "true")
        }

        // Enhanced compatibility for old Minecraft versions (1.0.0+)
        val mcVersion = versionInfo?.minecraftVersion ?: "0.0"
        val versionParts = mcVersion.split(".")
        if (versionParts.isNotEmpty()) {
            val majorVersion = versionParts[0].toIntOrNull() ?: 0
            val minorVersion = if (versionParts.size > 1) versionParts[1].toIntOrNull() ?: 0 else 0
            
            // For very old versions (1.0-1.5)
            if (majorVersion == 1 && minorVersion <= 5) {
                put("minecraft.legacy.mode", "true")
                put("java.net.preferIPv4Stack", "true")
                put("legacy.lwjgl.mode", "true")
            }
            
            // For old modded versions (1.2.5-1.7.10)
            if (majorVersion == 1 && minorVersion >= 2 && minorVersion <= 7) {
                put("fml.readTimeout", "240") // Longer timeout for old mod loading
                put("legacy.mod.support", "true")
            }
        }

        //jna
        gameManifest.libraries?.find { library ->
            library.name.startsWith("net.java.dev.jna:jna:")
        }?.let { library ->
            parseLibraryComponents(library.name).version
        }?.let { jnaVersion ->
            val jnaDir = File(LibPath.JNA, jnaVersion)
            if (jnaDir.exists()) {
                val dirPath = jnaDir.absolutePath
                put("java.library.path", "$dirPath:${PathManager.DIR_NATIVE_LIB}")
                put("jna.boot.library.path", dirPath) //覆盖父类添加的jna路径
            }
        }
    }

    override fun chdir(): String {
        return version.getGameDir().absolutePath
    }

    override fun getLogFile(): File = VersionsManager.getLatestLog(version)

    override fun initEnv(screenSize: IntSize): MutableMap<String, String> {
        val envMap = super.initEnv(screenSize)

        DriverPluginManager.setDriverById(version.getDriver())
        envMap["DRIVER_PATH"] = DriverPluginManager.getDriver().path

        // Adrenotools: حقن مشغّل Turnip Vulkan بدون صلاحيات الجذر
        val adrenotoolsLib = tryLoadAdrenotools()
        if (adrenotoolsLib != null) {
            envMap["ADRENOTOOLS_DRIVER_PATH"] = adrenotoolsLib
            envMap["ADRENOTOOLS_ENABLE"] = "1"
            append("▷ Adrenotools enabled: Turnip driver at $adrenotoolsLib")
        }

        // TurnipDriverManager: دمج متغيرات تعريفات Mesa Turnip Vulkan
        val renderer = Renderers.getCurrentRenderer()
        val rendererId = renderer.getRendererId().lowercase()
        if (rendererId.contains("vulkan") || rendererId.contains("zink")) {
            val recommendedDriver = TurnipDriverManager.getRecommendedDriver(activity)
            if (recommendedDriver != null) {
                val driverEnv = TurnipDriverManager.applyDriver(activity, recommendedDriver)
                envMap.putAll(driverEnv)
                driverEnv.forEach { (key, value) ->
                    append("▷ TurnipDriverManager: $key=$value")
                }
            }
        }

        checkAndUsedJSPH(envMap, runtime)
        version.getVersionInfo()?.loaderInfo?.getLoaderEnvKey()?.let { loaderKey ->
            envMap[loaderKey] = "1"
        }
        if (Renderers.isCurrentRendererValid()) {
            setRendererEnv(envMap)
        }
        envMap["ZALITH_VERSION_CODE"] = BuildConfig.VERSION_CODE.toString()
        return envMap
    }

    override fun dlopenEngine() {
        super.dlopenEngine()
        appendTitle("DLOPEN Renderer")

        // Try to load libadrenotools for Mesa Turnip driver injection
        val adrenotoolsPaths = listOf(
            "${DriverPluginManager.getDriver().path}/libadrenotools.so",
            "${TurnipDriverManager.getTurnipDirectory(activity)}/libadrenotools.so"
        )
        for (adrenotoolsPath in adrenotoolsPaths) {
            if (File(adrenotoolsPath).exists()) {
                append("▷ Loading Adrenotools: $adrenotoolsPath")
                if (ZLBridge.dlopen(adrenotoolsPath)) {
                    append("▷ Adrenotools loaded successfully")
                } else {
                    append("▷ WARNING: Failed to load Adrenotools from $adrenotoolsPath")
                }
            }
        }

        val rendererPlugin = RendererPluginManager.selectedRendererPlugin
        if (rendererPlugin != null) {
            append("▷ Loading renderer plugin: ${rendererPlugin.displayName}")
            rendererPlugin.dlopen.forEach { lib ->
                val libPath = "${rendererPlugin.path}/$lib"
                append("▷ Loading renderer plugin lib: $libPath")
                if (!ZLBridge.dlopen(libPath)) {
                    Logger.error(TAG, "Failed to load renderer plugin library: $libPath")
                    append("▷ ERROR: Failed to load renderer plugin library: $libPath")
                }
            }
            return
        }

        val rendererLib = loadGraphicsLibrary()
        if (rendererLib == null) {
            Logger.error(TAG, "No renderer library to load")
            append("▷ ERROR: No renderer library configured")
            return
        }

        append("▷ Loading renderer library: $rendererLib")
        if (!ZLBridge.dlopen(rendererLib)) {
            val altPath = findInLdLibPath(rendererLib)
            if (ZLBridge.dlopen(altPath)) {
                append("▷ Successfully loaded renderer from LD path: $altPath")
                return
            }
            Logger.error(TAG, "Failed to load renderer library: $rendererLib")
            append("▷ ERROR: Failed to load renderer library!")
            append("▷ Try changing the renderer in settings > Renderer")
        } else {
            append("▷ Renderer library loaded successfully")
        }
    }

    private fun tryLoadAdrenotools(): String? {
        return try {
            val driverPath = DriverPluginManager.getDriver().path
            // Adrenotools يبحث في نفس مجلد libadrenotools.so عن ملفات Turnip
            val turnipFiles = File(driverPath).listFiles { f ->
                f.name.startsWith("libvulkan") && f.name.endsWith(".so")
            }
            if (turnipFiles != null && turnipFiles.isNotEmpty()) {
                driverPath
            } else {
                // لو ملفات Turnip مو في driver path, جرب native lib directory
                val appDir = File(DriverPluginManager.getDriver().path)
                if (appDir.exists() && File(appDir, "libadrenotools.so").exists()) {
                    driverPath
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to check Adrenotools path", e)
            null
        }
    }

    override fun progressFinalUserArgs(args: MutableList<String>, ramAllocation: Int) {
        super.progressFinalUserArgs(args, version.getRamAllocation(activity))
        if (Renderers.isCurrentRendererValid()) {
            args.add("-Dorg.lwjgl.opengl.libname=${loadGraphicsLibrary()}")
        }
    }

    private suspend fun launchGame(
        screenSize: IntSize,
        clientJar: File,
        javaRuntime: String,
        customArgs: String
    ): Int {
        return try {
            val runtime = RuntimesManager.forceReload(javaRuntime)

            val gameDirPath = version.getGameDir()

            // Async operations for faster loading
            disableSplash(gameDirPath)
            
            // Pre-create necessary directories
            ensureGameDirectories(gameDirPath)

            //初始化运行环境
            this.runtime = runtime
            val runtimeLibraryPath = getRuntimeLibraryPath()

            val launchArgs = LaunchArgs(
                runtimeLibraryPath = runtimeLibraryPath,
                account = usingAccount,
                offlineServer = offlineServer,
                gameDirPath = gameDirPath,
                version = version,
                clientJar = clientJar,
                gameManifest = gameManifest,
                runtime = runtime,
                readAssetsFile = { path -> activity.readAssetFile(path) },
                getCacioJavaArgs = { isJava8 ->
                    getCacioJavaArgs(screenSize, isJava8)
                }
            ).getAllArgs()

            tryStartTouchProxy()

            launchJvm(
                context = activity,
                jvmArgs = launchArgs,
                userHome = GamePathManager.getCurrentPath(),
                userArgs = customArgs,
                screenSize = screenSize
            )
        } catch (e: Exception) {
            Logger.error(TAG, "Fatal error during launch game preparation", e)
            LoggerBridge.append("FATAL ERROR during game preparation: ${e.message}")
            -994
        }
    }
    
    /**
     * Pre-create necessary game directories to avoid delays during launch
     */
    private fun ensureGameDirectories(gameDir: File) {
        runCatching {
            val dirs = listOf(
                File(gameDir, "saves"),
                File(gameDir, "screenshots"),
                File(gameDir, "resourcepacks"),
                File(gameDir, "shaderpacks"),
                File(gameDir, "mods"),
                File(gameDir, "config"),
                File(gameDir, "logs")
            )
            dirs.forEach { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to pre-create game directories", e)
        }
    }

    private fun tryStartTouchProxy() {
        if (version.enableTouchProxy) {
            ControllerProxy.startProxy(
                context = activity,
                vibrateDuration = version.getTouchVibrateDuration(),
                vibrateKind = version.getTouchVibrateKind(),
            )
        }
    }

    private fun printLauncherInfo(
        javaArguments: String,
        javaRuntime: String,
    ) {
        var mcInfo = version.getVersionName()
        version.getVersionInfo()?.let { info -> mcInfo = info.getInfoString() }
        val renderer = Renderers.getCurrentRenderer()

        appendTitle("Launch Minecraft")
        append("▷ Launcher version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        append("▷ Architecture: ${Architecture.archAsString(ZLApplication.DEVICE_ARCHITECTURE)}")
        append("▷ Device model: ${Build.MANUFACTURER}, ${Build.MODEL}")
        append("▷ API version: ${Build.VERSION.SDK_INT}")
        append("▷ Renderer: ${renderer.getRendererName()}")
        renderer.getRendererSummary()?.let { summary ->
            append("▷ Renderer Summary: $summary")
        }
        append("▷ Selected Minecraft version: ${version.getVersionName()}")
        append("▷ Minecraft Info: $mcInfo")
        append("▷ Game Path: ${version.getGameDir().absolutePath} (Isolation: ${version.isIsolation()})")
        append("▷ Custom Java arguments: $javaArguments")
        append("▷ Java Runtime: $javaRuntime")
        append("▷ Account: ${usingAccount.username} (${usingAccount.accountType})")
    }

    /**
     * 获取Java运行环境名称，
     * 如果版本独立设置了运行环境，则直接选定它；
     * 如果版本未设置，则根据全局设置或自动选择
     */
    private fun getRuntime(): String {
        val versionRuntime = version.getJavaRuntime().takeIf { it.isNotEmpty() } ?: ""
        if (versionRuntime.isNotEmpty()) return versionRuntime

        val runtime = AllSettings.javaRuntime.getValue()
        val pickedRuntime = RuntimesManager.loadRuntime(runtime)

        if (AllSettings.autoPickJavaRuntime.getValue()) {
            val loaderInfo = version.getVersionInfo()?.loaderInfo
            //开启了自动选择，根据游戏需求的版本做选择
            val targetJavaVersion = when (loaderInfo?.loader) {
                ModLoader.BABRIC -> 17
                ModLoader.FABRIC, ModLoader.LEGACY_FABRIC -> 17
                ModLoader.CLEANROOM -> {
                    if (loaderInfo.version.isBiggerTo("0.4.4-alpha")) {
                        25
                    } else {
                        21
                    }
                }
                else -> gameManifest.javaVersion?.majorVersion ?: 8
            }
            if (pickedRuntime.javaVersion == 0 || pickedRuntime.javaVersion < targetJavaVersion) {
                val runtime0 = RuntimesManager.getNearestJreName(targetJavaVersion)
                if (runtime0 != null) {
                    return runtime0
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(activity, activity.getString(R.string.game_auto_pick_runtime_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        return runtime
    }

    /**
     * 禁用Forge的启动屏幕
     * [Modified from PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher/blob/a6f3fc0/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Tools.java#L372-L391)
     */
    private fun disableSplash(dir: File) {
        File(dir, "config").let { configDir ->
            if (configDir.ensureDirectorySilently()) {
                val forgeSplashFile = configDir.child("splash.properties")
                runCatching {
                    val props = java.util.Properties()
                    if (forgeSplashFile.exists()) {
                        forgeSplashFile.inputStream().use { props.load(it) }
                    }
                    if (props.getProperty("enabled", "true") == "true") {
                        props.setProperty("enabled", "false")
                        forgeSplashFile.outputStream().use { props.store(it, null) }
                    }
                }.onFailure {
                    Logger.warning(TAG, "Could not disable Forge 1.12.2 and below splash screen!", it)
                }
            } else {
                Logger.warning(TAG, "Failed to create the configuration directory")
            }
        }
    }
}

private fun checkAndUsedJSPH(envMap: MutableMap<String, String>, runtime: Runtime) {
    if (runtime.javaVersion < 11) return //onUseJSPH
    val dir = File(PathManager.DIR_NATIVE_LIB).takeIf { it.isDirectory } ?: return
    val jsphHome = if (runtime.javaVersion == 17) "libjsph17" else "libjsph21"
    dir.listFiles { _, name -> name.startsWith(jsphHome) }?.takeIf { it.isNotEmpty() }?.let {
        val libName = "${PathManager.DIR_NATIVE_LIB}/$jsphHome.so"
        envMap["JSP"] = libName
    }
}

private fun setRendererEnv(envMap: MutableMap<String, String>) {
    val renderer = Renderers.getCurrentRenderer()
    val rendererId = renderer.getRendererId()

    if (rendererId.startsWith("opengles2")) {
        envMap["LIBGL_ES"] = "2"
        envMap["LIBGL_MIPMAP"] = "3"
        envMap["LIBGL_NOERROR"] = "1"
        envMap["LIBGL_NOINTOVLHACK"] = "1"
        envMap["LIBGL_NORMALIZE"] = "1"
        // Additional optimizations for weak devices
        envMap["LIBGL_SILENTSTUB"] = "1"
        envMap["LIBGL_VSYNC"] = "0" // Disable vsync for better FPS on weak devices
    }

    envMap += renderer.getRendererEnv().value

    renderer.getRendererEGL()?.let { eglName ->
        envMap["POJAVEXEC_EGL"] = eglName
    }

    envMap["POJAV_RENDERER"] = rendererId

    if (RendererPluginManager.selectedRendererPlugin != null) return

    if (!rendererId.startsWith("opengles")) {
        envMap["MESA_LOADER_DRIVER_OVERRIDE"] = "zink"
        envMap["MESA_GLSL_CACHE_DIR"] = PathManager.DIR_CACHE.absolutePath
        envMap["force_glsl_extensions_warn"] = "true"
        envMap["allow_higher_compat_version"] = "true"
        envMap["allow_glsl_extension_directive_midshader"] = "true"
        envMap["LIB_MESA_NAME"] = loadGraphicsLibrary() ?: "null"
        
        // Performance optimizations for Zink/Vulkan
        envMap["MESA_GLTHREAD"] = "true" // Enable GL threading for better performance
        envMap["mesa_glthread"] = "true"
        envMap["MESA_NO_ERROR"] = "1" // Reduce error checking overhead
        envMap["MESA_DEBUG"] = "silent" // Silent for release, use "verbose" for debugging
    }

    if (!envMap.containsKey("LIBGL_ES")) {
        val glesMajor = getDetectedVersion()
        Logger.info(TAG, "GLES version detected: $glesMajor")

        envMap["LIBGL_ES"] = if (glesMajor < 3) {
            //fallback to 2 since it's the minimum for the entire app
            "2"
        } else if (rendererId.startsWith("opengles")) {
            rendererId.replace("opengles", "").replace("_5", "")
        } else {
            // TODO if can: other backends such as Vulkan.
            // Sure, they should provide GLES 3 support.
            "3"
        }
    }
    
    // Additional optimizations for all renderers
    envMap["LIBGL_FPS"] = "0" // Disable FPS counter overhead
    envMap["GALLIUM_HUD"] = "" // Disable HUD overlay

    // Enable renderer debugging for troubleshooting
    envMap["LIBGL_DEBUG"] = "verbose"
}

/**
 * Open the render library in accordance to the settings.
 * It will fallback if it fails to load the library.
 * @return The name of the loaded library
 */
private fun loadGraphicsLibrary(): String? {
    if (!Renderers.isCurrentRendererValid()) return null
    else {
        val rendererPlugin = RendererPluginManager.selectedRendererPlugin
        return if (rendererPlugin != null) {
            "${rendererPlugin.path}/${rendererPlugin.glName}"
        } else {
            Renderers.getCurrentRenderer().getRendererLibrary()
        }
    }
}

/**
 * [Modified from PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher/blob/98947f2/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java#L505-L516)
 */
private fun hasExtension(extensions: String, name: String): Boolean {
    var start = extensions.indexOf(name)
    while (start >= 0) {
        // check that we didn't find a prefix of a longer extension name
        val end = start + name.length
        if (end == extensions.length || extensions[end] == ' ') {
            return true
        }
        start = extensions.indexOf(name, end)
    }
    return false
}

private const val EGL_OPENGL_ES_BIT: Int = 0x0001
private const val EGL_OPENGL_ES2_BIT: Int = 0x0004
private const val EGL_OPENGL_ES3_BIT_KHR: Int = 0x0040

private fun getDetectedVersion(): Int {
    val egl = EGLContext.getEGL() as EGL10
    val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
    val numConfigs = IntArray(1)
    if (egl.eglInitialize(display, null)) {
        try {
            val checkES3: Boolean = hasExtension(egl.eglQueryString(display, EGL10.EGL_EXTENSIONS), "EGL_KHR_create_context")
            if (egl.eglGetConfigs(display, null, 0, numConfigs)) {
                val configs = arrayOfNulls<EGLConfig>(
                    numConfigs[0]
                )
                if (egl.eglGetConfigs(display, configs, numConfigs[0], numConfigs)) {
                    var highestEsVersion = 0
                    val value = IntArray(1)
                    for (i in 0..<numConfigs[0]) {
                        if (egl.eglGetConfigAttrib(
                                display, configs[i],
                                EGL10.EGL_RENDERABLE_TYPE, value
                            )
                        ) {
                            if (checkES3 && ((value[0] and EGL_OPENGL_ES3_BIT_KHR) == EGL_OPENGL_ES3_BIT_KHR)) {
                                if (highestEsVersion < 3) highestEsVersion = 3
                            } else if ((value[0] and EGL_OPENGL_ES2_BIT) == EGL_OPENGL_ES2_BIT) {
                                if (highestEsVersion < 2) highestEsVersion = 2
                            } else if ((value[0] and EGL_OPENGL_ES_BIT) == EGL_OPENGL_ES_BIT) {
                                if (highestEsVersion < 1) highestEsVersion = 1
                            }
                        } else {
                            Logger.warning(TAG,
                                ("Getting config attribute with "
                                        + "EGL10#eglGetConfigAttrib failed "
                                        + "(" + i + "/" + numConfigs[0] + "): "
                                        + egl.eglGetError())
                            )
                        }
                    }
                    return highestEsVersion
                } else {
                    Logger.error(TAG,
                        "Getting configs with EGL10#eglGetConfigs failed: "
                                + egl.eglGetError()
                    )
                    return -1
                }
            } else {
                Logger.error(TAG,
                    "Getting number of configs with EGL10#eglGetConfigs failed: "
                            + egl.eglGetError()
                )
                return -2
            }
        } finally {
            egl.eglTerminate(display)
        }
    } else {
        Logger.error(TAG, "Couldn't initialize EGL.")
        return -3
    }
}