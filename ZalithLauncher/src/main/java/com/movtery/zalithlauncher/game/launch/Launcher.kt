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

import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.system.Os
import android.util.ArrayMap
import androidx.annotation.CallSuper
import androidx.compose.ui.unit.IntSize
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.bridge.LoggerBridge
import com.movtery.zalithlauncher.bridge.ZLBridge
import com.movtery.zalithlauncher.bridge.ZLNativeInvoker
import com.movtery.zalithlauncher.game.multirt.Runtime
import com.movtery.zalithlauncher.game.multirt.RuntimesManager
import com.movtery.zalithlauncher.game.path.getGameHome
import com.movtery.zalithlauncher.game.plugin.ffmpeg.FFmpegPluginManager
import com.movtery.zalithlauncher.game.plugin.natives.NativePluginManager
import com.movtery.zalithlauncher.game.plugin.renderer.RendererPluginManager
import com.movtery.zalithlauncher.path.LibPath
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.unit.getOrMin
import com.movtery.zalithlauncher.utils.device.Architecture
import com.movtery.zalithlauncher.utils.device.Architecture.ARCH_X86
import com.movtery.zalithlauncher.utils.device.Architecture.is64BitsDevice
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.utils.string.splitPreservingQuotes
import com.oracle.dalvik.VMLauncher
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.TimeZone

private const val TAG = "Launcher"

abstract class Launcher(
    val onExit: (code: Int, isSignal: Boolean) -> Unit,
    val openPath: (folder: File) -> Unit
) {
    lateinit var runtime: Runtime
        protected set

    private val runtimeHome: String by lazy {
        RuntimesManager.getRuntimeHome(runtime.name).absolutePath
    }

    private fun getJavaHome() = if (runtime.isJDK8) "$runtimeHome/jre" else runtimeHome

    abstract suspend fun launch(screenSize: IntSize): Int
    abstract fun chdir(): String
    abstract fun getLogFile(): File
    abstract fun exit()

    protected suspend fun launchJvm(
        context: Context,
        jvmArgs: List<String>,
        userHome: String,
        userArgs: String,
        screenSize: IntSize,
        useLocalLanguage: Boolean = true
    ): Int {
        return try {
            LoggerBridge.append("▷ Initializing launcher...")
            ZLNativeInvoker.staticLauncher = this

            LoggerBridge.append("▷ Setting library path...")
            val libPath = getRuntimeLibraryPath()
            LoggerBridge.append("▷ Library path: $libPath")
            ZLBridge.setLdLibraryPath(libPath)

            LoggerBridge.appendTitle("Env Map")
            LoggerBridge.append("▷ Setting environment variables...")
            setEnv(screenSize)

            LoggerBridge.appendTitle("DLOPEN Java Runtime")
            dlopenJavaRuntime()

            LoggerBridge.appendTitle("DLOPEN Engine")
            dlopenEngine()

            LoggerBridge.append("▷ Proceeding to JVM launch...")
            launchJavaVM(
                context = context,
                jvmArgs = jvmArgs,
                userHome = userHome,
                userArgs = userArgs,
                screenSize = screenSize,
                useLocalLanguage = useLocalLanguage
            )
        } catch (e: Throwable) {
            LoggerBridge.append("FATAL ERROR in launchJvm: ${e.message}")
            LoggerBridge.append("Error type: ${e::class.qualifiedName}")
            Logger.error(TAG, "Fatal error during JVM launch preparation", e)
            -994
        }
    }

    //伪 suspend 函数،等待 JVM 的退出代码
    private suspend fun launchJavaVM(
        context: Context,
        jvmArgs: List<String>,
        userHome: String,
        userArgs: String,
        screenSize: IntSize,
        useLocalLanguage: Boolean
    ): Int {
        LoggerBridge.append("Preparing JVM arguments...")
        val args = getJavaArgs(
            userHome = userHome,
            userArgumentsString = userArgs,
            screenSize = screenSize,
            useLocalLanguage = useLocalLanguage
        ).toMutableList()
        
        LoggerBridge.append("Processing final user args...")
        progressFinalUserArgs(args)

        args.addAll(jvmArgs)
        args.add(0, "$runtimeHome/bin/java")

        LoggerBridge.appendTitle("JVM Args")
        val iterator = args.iterator()
        while (iterator.hasNext()) {
            val arg = iterator.next()
            if (arg.startsWith("--accessToken") && iterator.hasNext()) {
                iterator.next()
                LoggerBridge.append("▷ $arg")
                LoggerBridge.append("▷ ********************")
                continue
            }
            LoggerBridge.append("▷ $arg")
        }

        LoggerBridge.append("Setting up exit handlers...")
        ZLBridge.setupExitMethod(context.applicationContext)
        ZLBridge.initializeGameExitHook()
        
        val chdirPath = chdir()
        LoggerBridge.append("Changing directory to: $chdirPath")
        ZLBridge.chdir(chdirPath)

        LoggerBridge.append("=".repeat(50))
        LoggerBridge.append("Starting JVM launch...")
        LoggerBridge.append("=".repeat(50))
        
        val exitCode = try {
            LoggerBridge.append("Calling VMLauncher.launchJVM with ${args.size} arguments")
            VMLauncher.launchJVM(args.toTypedArray())
        } catch (e: UnsatisfiedLinkError) {
            LoggerBridge.append("FATAL: Native library error: ${e.message}")
            Logger.error(TAG, "Failed to load native library", e)
            -996
        } catch (e: Exception) {
            LoggerBridge.append("FATAL: JVM Launch Exception: ${e.message}")
            Logger.error(TAG, "Failed to launch JVM", e)
            -995
        }
        
        LoggerBridge.append("=".repeat(50))
        LoggerBridge.append("Java Exit code: $exitCode")
        LoggerBridge.append("=".repeat(50))
        
        // Log specific error codes
        when (exitCode) {
            -999 -> LoggerBridge.append("ERROR: Args array was null")
            -998 -> LoggerBridge.append("ERROR: Args array was empty")
            -997 -> LoggerBridge.append("ERROR: Failed to convert args array")
            -996 -> LoggerBridge.append("ERROR: Native library loading failed")
            -995 -> LoggerBridge.append("ERROR: JVM launch exception")
            -1 -> LoggerBridge.append("ERROR: JLI library loading failed")
            in 1..255 -> LoggerBridge.append("WARNING: Java process exited with non-zero code")
            0 -> LoggerBridge.append("SUCCESS: Java process exited normally")
        }
        
        return exitCode
    }

    /**
     * 添加 JVM 参数
     */
    protected open fun MutableMap<String, String>.putJavaArgs() {}

    private fun getJavaArgs(
        userHome: String,
        userArgumentsString: String,
        screenSize: IntSize,
        useLocalLanguage: Boolean
    ): List<String> {
        val userArguments = userArgumentsString.splitPreservingQuotes().toMutableList()
        val resolvFile = ensureDNSConfig()

        val overridableArguments = mutableMapOf<String, String>().apply {
            put("java.home", getJavaHome())
            put("java.io.tmpdir", PathManager.DIR_CACHE.absolutePath)
            put("jna.boot.library.path", PathManager.DIR_NATIVE_LIB)
            put("user.home", userHome)
            if (useLocalLanguage) {
                put("user.language", System.getProperty("user.language") ?: "en")
                put("user.country", Locale.getDefault().country)
            }
            put("user.timezone", TimeZone.getDefault().id)
            put("os.name", "Linux")
            put("os.version", "Android-${Build.VERSION.RELEASE}")
            put("pojav.path.minecraft", getGameHome())
            put("pojav.path.private.account", PathManager.DIR_DATA_BASES.absolutePath)
            put("org.lwjgl.vulkan.libname", "libvulkan.so")
            put("glfwstub.windowWidth", screenSize.width.toString())
            put("glfwstub.windowHeight", screenSize.height.toString())
            put("glfwstub.initEgl", "false")
            put("ext.net.resolvPath", resolvFile.absolutePath)

            put("log4j2.formatMsgNoLookups", "true")
            // Fix RCE vulnerability of log4j2
            put("java.rmi.server.useCodebaseOnly", "true")
            put("com.sun.jndi.rmi.object.trustURLCodebase", "false")
            put("com.sun.jndi.cosnaming.object.trustURLCodebase", "false")

            put("net.minecraft.clientmodname", BuildKeys.LAUNCHER_NAME)

            // fml - Enhanced mod loading support
            put("fml.earlyprogresswindow", "false")
            put("fml.ignoreInvalidMinecraftCertificates", "true")
            put("fml.ignorePatchDiscrepancies", "true")
            put("fml.skipFirstTextureLoad", "true") // Speed up mod loading
            put("fml.confirmedCodecs", "true") // Skip codec confirmation

            put("loader.disable_forked_guis", "true")
            put("jdk.lang.Process.launchMechanism", "FORK")

            put("sodium.checks.issue2561", "false")
            
            // Better mod compatibility including old versions (1.0.0+)
            put("legacy.debugClassLoading", "false") // Faster class loading
            put("legacy.debugClassLoadingFiner", "false")
            
            // Forge-specific optimizations
            put("forge.logging.console.level", "info") // Reduce log spam
            put("forge.forceDisplayStopScreen", "false") // Skip crash screen on exit
            
            // Fabric/Quilt mod loader optimizations
            put("fabric.gameJarPath.fallback", "true") // Better mod compatibility
            put("fabric.development", "false") // Production mode
            
            // OptiFine compatibility
            put("optifine.init", "true")
            put("optifine.skipPrompt", "true")
            
            // OpenGL/LWJGL optimizations for better FPS
            put("org.lwjgl.opengl.Display.enableOSXFullscreenModeAPI", "false")
            put("org.lwjgl.opengl.Display.enableHighDPI", "false") // Better performance on weak devices
            put("org.lwjgl.system.stackSize", "256") // Optimize stack size
            put("org.lwjgl.system.SharedLibraryExtractPath", PathManager.DIR_CACHE.absolutePath)
            
            // Memory optimization
            put("sun.rmi.dgc.client.gcInterval", "2147483646") // Reduce GC frequency
            put("sun.rmi.dgc.server.gcInterval", "2147483646")
            put("sun.io.useCanonCaches", "false") // Reduce IO caching overhead
            
            // Better threading for multi-core devices
            val cores = java.lang.Runtime.getRuntime().availableProcessors()
            put("java.util.concurrent.ForkJoinPool.common.parallelism", cores.toString())
            
            // Network optimizations
            put("http.keepAlive", "true")
            put("http.maxConnections", "8")
            
            // ClassLoader optimizations
            put("sun.reflect.inflationThreshold", "0") // Faster reflection
            
            // Better resource loading
            put("minecraft.applet.TargetDirectory", PathManager.DIR_GAME.absolutePath)

            putJavaArgs()
        }.map { entry ->
            "-D${entry.key}=${entry.value}"
        }

        val additionalArguments = overridableArguments.filter { arg ->
            val stripped = arg.substringBefore('=')
            val overridden = userArguments.any { it.startsWith(stripped) }
            if (overridden) {
                Logger.info(TAG, "Arg skipped: $arg")
            }
            !overridden
        }

        userArguments += additionalArguments
        return userArguments
    }

    /**
     * 确保 DNS 配置文件存在
     */
    private fun ensureDNSConfig(): File {
        val resolvFile = File(PathManager.DIR_GAME, "resolv.conf")
        if (!resolvFile.exists()) {
            val configText = if (LocaleList.getDefault().get(0).country != "CN") {
                """
                    nameserver 1.1.1.1
                    nameserver 1.0.0.1
                """.trimIndent()
            } else {
                """
                    nameserver 8.8.8.8
                    nameserver 8.8.4.4
                """.trimIndent()
            }
            runCatching {
                resolvFile.writeText(configText)
            }.onFailure {
                Logger.warning(TAG, "Failed to create resolv.conf", it)
                FileUtils.deleteQuietly(resolvFile)
            }
        }
        return resolvFile
    }

    /**
     * @param args 需要进行处理的参数
     * @param ramAllocation 指定内存空间大小
     */
    protected open fun progressFinalUserArgs(
        args: MutableList<String>,
        ramAllocation: Int = AllSettings.ramAllocation.getOrMin()
    ) {
        args.purgeArg("-Xms")
        args.purgeArg("-Xmx")
        args.purgeArg("-d32")
        args.purgeArg("-d64")
        args.purgeArg("-Xint")
        args.purgeArg("-XX:+UseTransparentHugePages")
        args.purgeArg("-XX:+UseLargePagesInMetaspace")
        args.purgeArg("-XX:+UseLargePages")
        args.purgeArg("-Dorg.lwjgl.opengl.libname")
        // Don't let the user specify a custom Freetype library (as the user is unlikely to specify a version compiled for Android)
        args.purgeArg("-Dorg.lwjgl.freetype.libname")
        // Overridden by us to specify the exact number of cores that the android system has
        args.purgeArg("-XX:ActiveProcessorCount")

        args.add("-javaagent:${LibPath.MIO_LIB_PATCHER.absolutePath}")

        //Add automatically generated args
        val ramAllocationString = ramAllocation.toString()
        args.add("-Xms${ramAllocationString}M")
        args.add("-Xmx${ramAllocationString}M")

        // Force LWJGL to use the Freetype library intended for it, instead of using the one
        // that we ship with Java (since it may be older than what's needed)
        args.add("-Dorg.lwjgl.freetype.libname=${PathManager.DIR_NATIVE_LIB}/libfreetype.so")

        // Our spirv-cross is compiled shared, so it gets named shared.
        args.add("-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared")

        // We don't have jemalloc for our LWJGL so set the allocator to system to avoid error logs
        args.add("-Dorg.lwjgl.system.allocator=system")

        // Some phones are not using the right number of cores, fix that
        val processorCount = java.lang.Runtime.getRuntime().availableProcessors()
        args.add("-XX:ActiveProcessorCount=$processorCount")
        
        // Adaptive performance based on RAM allocation
        val isLowEndDevice = ramAllocation <= 1024
        val isMidRangeDevice = ramAllocation in 1025..2048
        val isHighEndDevice = ramAllocation > 2048
        
        if (isLowEndDevice) {
            // Optimizations for weak devices (<=1GB RAM)
            args.add("-XX:+UseSerialGC") // Serial GC uses less memory
            args.add("-XX:MaxGCPauseMillis=100")
            args.add("-XX:MinHeapFreeRatio=20")
            args.add("-XX:MaxHeapFreeRatio=40")
            args.add("-XX:+DisableExplicitGC")
            args.add("-XX:+UseStringDeduplication")
            args.add("-XX:CompileThreshold=3000") // Higher threshold to save memory
        } else if (isMidRangeDevice) {
            // Balanced settings for mid-range devices (1-2GB RAM)
            args.add("-XX:+UseG1GC")
            args.add("-XX:+ParallelRefProcEnabled")
            args.add("-XX:MaxGCPauseMillis=50")
            args.add("-XX:G1NewSizePercent=20")
            args.add("-XX:G1ReservePercent=20")
            args.add("-XX:G1HeapRegionSize=16M")
            args.add("-XX:+DisableExplicitGC")
            args.add("-XX:+UseStringDeduplication")
            args.add("-XX:CompileThreshold=1500")
        } else {
            // Performance settings for high-end devices (>2GB RAM)
            args.add("-XX:+UseG1GC")
            args.add("-XX:+ParallelRefProcEnabled")
            args.add("-XX:MaxGCPauseMillis=30")
            args.add("-XX:+UnlockExperimentalVMOptions")
            args.add("-XX:G1NewSizePercent=30")
            args.add("-XX:G1ReservePercent=10")
            args.add("-XX:G1HeapRegionSize=32M")
            args.add("-XX:+DisableExplicitGC")
            args.add("-XX:+AlwaysPreTouch")
            args.add("-XX:+UseStringDeduplication")
            args.add("-XX:-UseAdaptiveSizePolicy")
            args.add("-XX:CompileThreshold=1000") // Faster JIT for better performance
            args.add("-XX:+UseFastAccessorMethods")
            args.add("-XX:+OptimizeStringConcat")
        }
        
        // Common optimizations for all devices
        args.add("-XX:+UseCompressedOops") // Compressed pointers save memory
        args.add("-XX:+UseCompressedClassPointers")
        
        // LWJGL optimizations for better rendering and FPS
        args.add("-Dorg.lwjgl.util.NoChecks=true") // Disable runtime checks for performance
        args.add("-Dorg.lwjgl.util.DebugLoader=false") // Disable debug loader
        args.add("-Dfml.readTimeout=180") // Increase timeout for loading mods on slow devices
        args.add("-Dfml.queryResult=confirm") // Auto-confirm queries
        
        // Minecraft-specific optimizations for old versions (1.0.0+)
        args.add("-Djava.net.preferIPv4Stack=true") // Better compatibility with old versions
        args.add("-Dminecraft.applet.TargetDirectory=${PathManager.DIR_GAME.absolutePath}")
        args.add("-Dfml.ignoreInvalidMinecraftCertificates=true") // Support modded old versions
        args.add("-Dfml.ignorePatchDiscrepancies=true")
        
        // Graphics optimizations for better FPS on all devices
        args.add("-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true") // Fallback for weak GPUs
        args.add("-Dorg.lwjgl.opengl.Window.undecorated=false")
        
        // Thread optimizations based on processor count
        if (processorCount >= 4) {
            args.add("-XX:ParallelGCThreads=${(processorCount * 0.75).toInt()}")
            args.add("-XX:ConcGCThreads=${(processorCount * 0.25).toInt()}")
        }
    }

    protected fun MutableList<String>.purgeArg(argStart: String) {
        removeIf { arg: String -> arg.startsWith(argStart) }
    }

    protected fun getJavaLibDir(): String {
        val architecture = runtime.arch?.let { arch ->
            if (Architecture.archAsInt(arch) == ARCH_X86) "i386/i486/i586"
            else arch
        } ?: throw IOException("Unsupported runtime environment: ${runtime.name}, arch is null!")

        var libDir = "/lib"
        architecture.split("/").forEach { arch ->
            val file = File(runtimeHome, "lib/$arch")
            if (file.exists() && file.isDirectory()) {
                libDir = "/lib/$arch"
            }
        }
        return libDir
    }

    private fun getJvmLibDir(): String {
        val jvmLibDir: String
        val path = (if (RuntimesManager.isJDK8(runtimeHome)) "/jre" else "") + getJavaLibDir()
        val jvmFile = File("$runtimeHome$path/server/libjvm.so")
        jvmLibDir = if (jvmFile.exists()) "/server" else "/client"
        return jvmLibDir
    }

    protected fun getRuntimeLibraryPath(): String {
        val javaLibDir = getJavaLibDir()
        val jvmLibDir = getJvmLibDir()

        val libName = if (is64BitsDevice) "lib64" else "lib"
        val paths = buildList {
            FFmpegPluginManager.takeIf { it.isAvailable }?.libraryPath?.let { add(it) }
            RendererPluginManager.selectedRendererPlugin?.path?.let { add(it) }
            addAll(NativePluginManager.getPaths())
            add("$runtimeHome$javaLibDir/jli")
            if (runtime.isJDK8) {
                add("$runtimeHome/jre$javaLibDir$jvmLibDir:$runtimeHome/jre$javaLibDir")
            } else {
                add("$runtimeHome$javaLibDir$jvmLibDir")
            }
            add("/system/$libName")
            add("/vendor/$libName")
            add("/vendor/$libName/hw")
            add("/system_ext/$libName")
            add(LibPath.JNA.absolutePath)
            PathManager.DIR_RUNTIME_MOD?.absolutePath?.let { add(it) }
            add(PathManager.DIR_NATIVE_LIB)
        }
        return paths.joinToString(":")
    }

    protected fun getLibraryPath(): String {
        val libDirName = if (is64BitsDevice) "lib64" else "lib"
        val path = listOfNotNull(
            "/system/$libDirName",
            "/vendor/$libDirName",
            "/vendor/$libDirName/hw",
            "/system_ext/$libDirName",
            RendererPluginManager.selectedRendererPlugin?.path,
            PathManager.DIR_RUNTIME_MOD?.absolutePath,
            PathManager.DIR_NATIVE_LIB
        )
        return path.joinToString(":")
    }

    protected fun findInLdLibPath(libName: String): String {
        val path = getLibraryPath()
        return path.split(":").find { libPath ->
            val file = File(libPath, libName)
            file.exists() && file.isFile
        }?.let {
            File(it, libName).absolutePath
        } ?: libName
    }

    private fun locateLibs(path: File): List<File> {
        val children = path.listFiles() ?: return emptyList()
        return children.flatMap { file ->
            when {
                file.isFile && file.name.endsWith(".so") -> listOf(file)
                file.isDirectory -> locateLibs(file)
                else -> emptyList()
            }
        }
    }

    private fun setEnv(screenSize: IntSize) {
        val envMap = initEnv(screenSize)
        envMap.forEach { (key, value) ->
            LoggerBridge.append("▷ $key = $value")
            runCatching {
                Os.setenv(key, value, true)
            }.onFailure {
                Logger.error(TAG, "Unable to set environment variable.", it)
            }
        }
    }

    @CallSuper
    protected open fun initEnv(screenSize: IntSize): MutableMap<String, String> {
        val envMap: MutableMap<String, String> = ArrayMap()
        setJavaEnv(
            screenSize = screenSize,
            envMap = { envMap }
        )
        return envMap
    }

    private fun setJavaEnv(
        screenSize: IntSize,
        envMap: () -> MutableMap<String, String>
    ) {
        val path = listOfNotNull("$runtimeHome/bin", Os.getenv("PATH"))

        envMap().let { map ->
            map["POJAV_NATIVEDIR"] = PathManager.DIR_NATIVE_LIB
            map["JAVA_HOME"] = getJavaHome()
            map["HOME"] = PathManager.DIR_FILES_EXTERNAL.absolutePath
            map["TMPDIR"] = PathManager.DIR_CACHE.absolutePath
            map["LD_LIBRARY_PATH"] = getLibraryPath()
            map["PATH"] = path.joinToString(":")
            map["AWTSTUB_WIDTH"] = screenSize.width.toString()
            map["AWTSTUB_HEIGHT"] = screenSize.height.toString()
            map["MOD_ANDROID_RUNTIME"] = PathManager.DIR_RUNTIME_MOD?.absolutePath ?: ""

            if (AllSettings.dumpShaders.getValue()) map["LIBGL_VGPU_DUMP"] = "1"
            if (AllSettings.zinkPreferSystemDriver.getValue()) map["POJAV_ZINK_PREFER_SYSTEM_DRIVER"] = "1"
            if (AllSettings.vsyncInZink.getValue()) map["POJAV_VSYNC_IN_ZINK"] = "1"
            if (AllSettings.bigCoreAffinity.getValue()) map["POJAV_BIG_CORE_AFFINITY"] = "1"

            if (FFmpegPluginManager.isAvailable) map["POJAV_FFMPEG_PATH"] = FFmpegPluginManager.executablePath!!
        }
    }

    private fun dlopenJavaRuntime() {
        LoggerBridge.append("▷ Starting Java Runtime library loading...")
        LoggerBridge.append("▷ Runtime home: $runtimeHome")
        
        var javaLibDir = "$runtimeHome${getJavaLibDir()}"
        val jliLibDir = if (File("$javaLibDir/jli/libjli.so").exists()) "$javaLibDir/jli" else javaLibDir

        if (runtime.isJDK8) {
            javaLibDir = "$runtimeHome/jre${getJavaLibDir()}"
            LoggerBridge.append("▷ Detected JDK8, using jre subdirectory")
        }
        val jvmLibDir = "$javaLibDir${getJvmLibDir()}"
        
        LoggerBridge.append("▷ Java lib dir: $javaLibDir")
        LoggerBridge.append("▷ JLI lib dir: $jliLibDir")
        LoggerBridge.append("▷ JVM lib dir: $jvmLibDir")
        
        fun tryDlopen(path: String, name: String, critical: Boolean = true): Boolean {
            val file = File(path)
            if (!file.exists()) {
                if (critical) {
                    LoggerBridge.append("▷ WARNING: $name not found at: $path")
                    Logger.warning(TAG, "$name not found at: $path")
                }
                return false
            }
            val result = ZLBridge.dlopen(path)
            if (!result && critical) {
                LoggerBridge.append("▷ ERROR: Failed to load $name")
                Logger.error(TAG, "Failed to dlopen $name at: $path")
            }
            return result
        }
        
        // Load critical libraries first
        tryDlopen("$jliLibDir/libjli.so", "libjli.so", true)
        tryDlopen("$jvmLibDir/libjvm.so", "libjvm.so", true)
        
        // Load essential libraries
        val essentialLibs = listOf(
            "$javaLibDir/libverify.so" to "libverify.so",
            "$javaLibDir/libjava.so" to "libjava.so",
            "$javaLibDir/libnet.so" to "libnet.so",
            "$javaLibDir/libnio.so" to "libnio.so"
        )
        
        essentialLibs.forEach { (path, name) ->
            tryDlopen(path, name, true)
        }
        
        // Load optional libraries (for rendering and fonts)
        val optionalLibs = listOf(
            "$javaLibDir/libfreetype.so" to "libfreetype.so",
            "$javaLibDir/libawt.so" to "libawt.so",
            "$javaLibDir/libawt_headless.so" to "libawt_headless.so",
            "$javaLibDir/libfontmanager.so" to "libfontmanager.so"
        )
        
        optionalLibs.forEach { (path, name) ->
            tryDlopen(path, name, false)
        }
        
        // Load additional libraries from runtime home (non-critical)
        LoggerBridge.append("▷ Loading additional libs from runtime home...")
        val additionalLibs = locateLibs(File(runtimeHome))
        val loadedLibs = mutableSetOf<String>()
        
        additionalLibs.forEach { file ->
            val libName = file.name
            // Skip already loaded libraries
            if (!loadedLibs.contains(libName)) {
                tryDlopen(file.absolutePath, libName, false)
                loadedLibs.add(libName)
            }
        }
        
        LoggerBridge.append("▷ Java Runtime library loading completed")
    }

    @CallSuper
    protected open fun dlopenEngine() {
        LoggerBridge.append("▷ Loading sound engine (OpenAL)...")
        val openalPath = "${PathManager.DIR_NATIVE_LIB}/libopenal.so"
        val openalFile = File(openalPath)
        if (!openalFile.exists()) {
            LoggerBridge.append("▷ ERROR: libopenal.so not found at: $openalPath")
            Logger.error(TAG, "libopenal.so not found at: $openalPath")
        } else {
            val result = ZLBridge.dlopen(openalPath)
            if (result) {
                LoggerBridge.append("▷ Successfully loaded libopenal.so")
            } else {
                LoggerBridge.append("▷ ERROR: Failed to load libopenal.so")
                Logger.error(TAG, "Failed to dlopen libopenal.so")
            }
        }
    }
}

fun getCacioJavaArgs(
    screenSize: IntSize,
    isJava8: Boolean
): List<String> {
    val argsList: MutableList<String> = ArrayList()

    // Caciocavallo config AWT-enabled version
    argsList.add("-Djava.awt.headless=false")
    argsList.add("-Dcacio.managed.screensize=${screenSize.width}x${screenSize.height}")
    argsList.add("-Dcacio.font.fontmanager=sun.awt.X11FontManager")
    argsList.add("-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler")
    argsList.add("-Dswing.defaultlaf=javax.swing.plaf.nimbus.NimbusLookAndFeel")
    if (isJava8) {
        argsList.add("-Dawt.toolkit=net.java.openjdk.cacio.ctc.CTCToolkit")
        argsList.add("-Djava.awt.graphicsenv=net.java.openjdk.cacio.ctc.CTCGraphicsEnvironment")
    } else {
        argsList.add("-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit")
        argsList.add("-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment")
        argsList.add("-javaagent:${LibPath.CACIO_17_AGENT.absolutePath}")

        argsList.add("--add-exports=java.desktop/java.awt=ALL-UNNAMED")
        argsList.add("--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED")
        argsList.add("--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED")
        argsList.add("--add-exports=java.desktop/sun.java2d=ALL-UNNAMED")
        argsList.add("--add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED")
        argsList.add("--add-exports=java.desktop/sun.awt=ALL-UNNAMED")
        argsList.add("--add-exports=java.desktop/sun.awt.event=ALL-UNNAMED")
        argsList.add("--add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED")
        argsList.add("--add-exports=java.desktop/sun.font=ALL-UNNAMED")
        argsList.add("--add-exports=java.base/sun.security.action=ALL-UNNAMED")
        argsList.add("--add-opens=java.base/java.util=ALL-UNNAMED")
        argsList.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED")
        argsList.add("--add-opens=java.desktop/sun.font=ALL-UNNAMED")
        argsList.add("--add-opens=java.desktop/sun.java2d=ALL-UNNAMED")
        argsList.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")

        // Opens the java.net package to Arc DNS injector on Java 9+
        argsList.add("--add-opens=java.base/java.net=ALL-UNNAMED")
    }

    val cacioClassPath = StringBuilder()
    cacioClassPath.append("-Xbootclasspath/").append(if (isJava8) "p" else "a")
    val cacioFiles = if (isJava8) LibPath.CACIO_8 else LibPath.CACIO_17
    cacioFiles.listFiles()?.onEach {
        if (it.name.endsWith(".jar")) cacioClassPath.append(":").append(it.absolutePath)
    }

    argsList.add(cacioClassPath.toString())

    return argsList
}