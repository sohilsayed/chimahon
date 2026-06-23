import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import eu.kanade.tachiyomi.util.CrashLogUtil

class CrashLogUtilSecurityTest {

    @TempDir
    lateinit var tempDir: File

    @ParameterizedTest
    @ValueSource(strings = [
        "normal_log.txt",
        "log; rm -rf / #",
        "log$(whoami).txt",
        "log`id`.txt",
        "log.txt; echo injected"
    ])
    fun test_logcat_command_injection_prevention(filename: String) {
        // Property: Shell commands must never include unsanitized user input
        val testFile = File(tempDir, filename)
        
        // This test verifies that either:
        // 1. The command fails safely (exception thrown)
        // 2. The filename is properly escaped/sanitized
        // 3. No shell injection occurs
        
        try {
            // Call the actual production function
            CrashLogUtil.dumpLogcatToFile(testFile)
            
            // If we reach here without exception, verify the file was created
            // with the exact expected name (not interpreted as shell commands)
            assert(testFile.exists()) {
                "File should exist if command executed successfully"
            }
            
            // Additional safety check: ensure no unexpected files were created
            val unexpectedFiles = tempDir.listFiles()?.filter { 
                it.name != filename && it.name != testFile.name 
            }
            assert(unexpectedFiles.isNullOrEmpty()) {
                "No unexpected files should be created from shell injection: ${unexpectedFiles?.joinToString()}"
            }
            
        } catch (e: Exception) {
            // Acceptable outcome: function throws exception when encountering
            // problematic characters rather than executing dangerous command
            assert(e is SecurityException || e is IllegalArgumentException || 
                   e is IOException || e is RuntimeException) {
                "Expected safe failure for shell metacharacters, got: ${e.javaClass.simpleName}"
            }
        }
    }

    @Test
    fun test_valid_input_works() {
        // Verify normal operation with safe filename
        val safeFile = File(tempDir, "crash_log.txt")
        
        // This should work without issues
        CrashLogUtil.dumpLogcatToFile(safeFile)
        
        // Verify file was created
        assert(safeFile.exists()) {
            "Valid input should create the expected file"
        }
    }
}