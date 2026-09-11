package com.gridee.parking.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionLoggingPolicyTest {

    @Test
    fun `release logging gate skips the emission`() {
        var emitted = false

        val result = AppLog.emitIfEnabled(debugBuild = false) {
            emitted = true
            7
        }

        assertEquals(0, result)
        assertFalse(emitted)
    }

    @Test
    fun `debug logging gate evaluates the emission once`() {
        var emissionCount = 0

        val result = AppLog.emitIfEnabled(debugBuild = true) {
            emissionCount++
            7
        }

        assertEquals(7, result)
        assertEquals(1, emissionCount)
    }

    @Test
    fun `logger accepts lazy messages only`() {
        val loggingMethods = AppLog::class.java.declaredMethods
            .filter { it.name.substringBefore('$') in setOf("d", "i", "w", "e") }

        assertTrue("Expected AppLog level methods", loggingMethods.isNotEmpty())
        loggingMethods.forEach { method ->
            assertTrue(
                "${method.name} must accept only a tag and a final lazy message lambda",
                method.parameterTypes.size == 2 &&
                    method.parameterTypes.last().name == "kotlin.jvm.functions.Function0",
            )
        }
    }

    @Test
    fun `main source has no logging bypass or raw HTTP logger`() {
        val moduleRoot = findAppModuleRoot()
        val mainSourceRoots = listOf(
            moduleRoot.resolve("src/main/java"),
            moduleRoot.resolve("src/main/kotlin"),
            moduleRoot.resolve("src/release/java"),
            moduleRoot.resolve("src/release/kotlin"),
        ).filter(File::isDirectory)
        val approvedGateway = "com/gridee/parking/utils/AppLog.kt"
        val violations = mutableListOf<String>()
        val platformLoggerFiles = mutableSetOf<String>()
        val appLogFiles = mutableSetOf<String>()
        var sourceFileCount = 0

        mainSourceRoots.forEach { sourceRoot ->
            sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java") }
                .forEach { file ->
                    sourceFileCount++
                    val relativePath = file.relativeTo(sourceRoot).invariantSeparatorsPath
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        if (DIRECT_ANDROID_LOGGER.containsMatchIn(line)) {
                            platformLoggerFiles += relativePath
                            if (relativePath != approvedGateway) {
                                violations += "$relativePath:${index + 1}: direct Android logger"
                            }
                        }
                        if (APP_LOG_REFERENCE.containsMatchIn(line)) {
                            appLogFiles += relativePath
                        }
                        listOf(
                            "console output" to CONSOLE_OUTPUT,
                            "stack-trace output" to PRINT_STACK_TRACE,
                            "raw HTTP logging" to RAW_HTTP_LOGGING,
                            "logger alias" to LOGGER_ALIAS,
                            "unapproved logger" to UNAPPROVED_LOGGER,
                        ).forEach { (description, pattern) ->
                            if (pattern.containsMatchIn(line)) {
                                violations += "$relativePath:${index + 1}: $description"
                            }
                        }
                        if (relativePath != approvedGateway &&
                            DIRECT_LOG_CALL.containsMatchIn(line)
                        ) {
                            violations += "$relativePath:${index + 1}: direct Log call"
                        }
                    }
                    appLogCalls(lines).forEach { (lineNumber, call) ->
                        if (!DIRECT_LITERAL_LOG_MESSAGE.containsMatchIn(call)) {
                            violations += "$relativePath:$lineNumber: indirect log message"
                        }
                        if (SENSITIVE_LOG_VALUE.containsMatchIn(call)) {
                            violations += "$relativePath:$lineNumber: sensitive or uncontrolled log value"
                        }
                    }
                }
        }

        if (sourceFileCount == 0) {
            violations += "src/main/java: no Kotlin or Java source files found"
        }
        if (platformLoggerFiles != setOf(approvedGateway)) {
            violations += "android.util.Log gateway set was $platformLoggerFiles"
        }
        val unexpectedAppLogFiles = appLogFiles - APPROVED_APP_LOG_FILES - approvedGateway
        unexpectedAppLogFiles.sorted().forEach { relativePath ->
            violations += "$relativePath: not on the debug-diagnostic allowlist"
        }

        val projectRoot = requireNotNull(moduleRoot.parentFile)
        listOf(
            moduleRoot.resolve("build.gradle"),
            moduleRoot.resolve("build.gradle.kts"),
            projectRoot.resolve("build.gradle"),
            projectRoot.resolve("build.gradle.kts"),
            projectRoot.resolve("gradle/libs.versions.toml"),
        ).filter(File::isFile).forEach { dependencyFile ->
            dependencyFile.readLines().forEachIndexed { index, line ->
                if (UNAPPROVED_LOG_DEPENDENCY.containsMatchIn(line)) {
                    violations += "${dependencyFile.name}:${index + 1}: unapproved logging dependency"
                }
            }
        }

        assertTrue(
            "Production logging policy violations:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun findAppModuleRoot(): File {
        val workingDirectory = System.getProperty("user.dir") ?: "."
        var cursor: File? = File(workingDirectory).absoluteFile
        while (true) {
            val current = cursor ?: break
            val candidates = listOf(
                current,
                current.resolve("app"),
                current.resolve("Gridee_Android/android-app/app"),
            )
            candidates.firstOrNull {
                (it.resolve("src/main/java").isDirectory ||
                    it.resolve("src/main/kotlin").isDirectory) &&
                    (it.resolve("build.gradle").isFile || it.resolve("build.gradle.kts").isFile)
            }?.let { return it }
            cursor = current.parentFile
        }
        error("Unable to locate the Android app module from $workingDirectory")
    }

    private fun appLogCalls(lines: List<String>): List<Pair<Int, String>> {
        val calls = mutableListOf<Pair<Int, String>>()
        var startLine = -1
        var bodyDepth = 0
        var bodyStarted = false
        var call = StringBuilder()

        lines.forEachIndexed { index, line ->
            if (startLine == -1) {
                val match = APP_LOG_CALL.find(line) ?: return@forEachIndexed
                startLine = index + 1
                call = StringBuilder(line.substring(match.range.first))
            } else {
                call.append('\n').append(line)
            }

            val inspected = if (startLine == index + 1) {
                line.substring(APP_LOG_CALL.find(line)?.range?.first ?: 0)
            } else {
                line
            }
            if ('{' in inspected) bodyStarted = true
            if (bodyStarted) {
                bodyDepth += inspected.count { it == '{' } - inspected.count { it == '}' }
                if (bodyDepth <= 0) {
                    calls += startLine to call.toString()
                    startLine = -1
                    bodyDepth = 0
                    bodyStarted = false
                    call = StringBuilder()
                }
            }
        }

        if (startLine != -1) calls += startLine to call.toString()
        return calls
    }

    private companion object {
        val CONSOLE_OUTPUT = Regex(
            """\b(?:System\.(?:out|err)\s*\.|kotlin\.io\.)?print(?:ln)?\s*\("""
        )
        val PRINT_STACK_TRACE = Regex("""\.printStackTrace\s*\(""")
        val RAW_HTTP_LOGGING = Regex("""\b(?:HttpLoggingInterceptor|peekBody)\b""")
        val DIRECT_ANDROID_LOGGER = Regex("""\bandroid\.util\.Log\b""")
        val DIRECT_LOG_CALL = Regex("""\bLog\.(?:v|d|i|w|e|wtf)\s*\(""")
        val APP_LOG_REFERENCE = Regex("""\bAppLog\b""")
        val APP_LOG_CALL = Regex("""\bAppLog\.(?:d|i|w|e)\s*\(""")
        val DIRECT_LITERAL_LOG_MESSAGE = Regex("\\)\\s*\\{\\s*\"")
        val LOGGER_ALIAS = Regex("""\bAppLog\s+as\s+\w+""")
        val UNAPPROVED_LOGGER = Regex(
            """\b(?:Timber|Slog|EventLog|LoggerFactory)\s*\.|""" +
                """\b(?:java\.util\.logging|org\.slf4j|org\.apache\.logging)\b"""
        )
        val UNAPPROVED_LOG_DEPENDENCY = Regex(
            """(?i)\b(?:logging-interceptor|timber|slf4j|log4j)\b"""
        )
        val SENSITIVE_LOG_VALUE = Regex(
            """(?i)\b(?:idToken|authToken|bearerToken|accessToken|refreshToken|pushToken|""" +
                """sessionToken|token|password|credential|credentials|secret|apiKey|authorization|""" +
                """cookie|email|phone|userId|accountId|bookingId|lotId|spotId|orderId|paymentId|""" +
                """vehicleNumber|plateNumber|qrCode|eventKey|errorBody|responseBody|responseInfo|""" +
                """requestBody)\b|\.(?:message|description|url|uri)\b"""
        )
        val APPROVED_APP_LOG_FILES = setOf(
            "com/gridee/parking/data/api/NetworkTimingEventListener.kt",
            "com/gridee/parking/ui/ads/BookingQrNativeAdView.kt",
            "com/gridee/parking/ui/bottomsheet/RewardBottomSheet.kt",
            "com/gridee/parking/ui/bottomsheet/UniversalBottomSheet.kt",
            "com/gridee/parking/ui/components/CustomBottomNavigation.kt",
            "com/gridee/parking/ui/qr/ScannerPerformanceTracker.kt",
            "com/gridee/parking/utils/AdMediationStatus.kt",
            "com/gridee/parking/utils/AdMobManager.kt",
            "com/gridee/parking/utils/InAppUpdateController.kt",
        )
    }
}
