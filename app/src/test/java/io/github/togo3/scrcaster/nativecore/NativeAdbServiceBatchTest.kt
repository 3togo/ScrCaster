package io.github.togo3.scrcaster.nativecore

import org.junit.Assert.*
import org.junit.Test

class NativeAdbServiceBatchTest {

    @Test
    fun parseBatchOutputSplitsResponseByMarkers() {
        val markers = listOf("MARKER_0", "MARKER_1")
        val response = "output0\nMARKER_0\noutput1\nMARKER_1\n"
        val result = NativeAdbService.parseBatchOutput(response, markers)
        assertEquals(listOf("output0", "output1"), result)
    }

    @Test
    fun parseBatchOutputHandlesSingleCommand() {
        val markers = listOf("MARKER_0")
        val response = "hello world\nMARKER_0\n"
        val result = NativeAdbService.parseBatchOutput(response, markers)
        assertEquals(listOf("hello world"), result)
    }

    @Test
    fun parseBatchOutputTrimsTrailingNewlinesAndCarriageReturns() {
        val markers = listOf("MARKER_0")
        val response = "line1\r\nline2\r\n\r\nMARKER_0\n"
        val result = NativeAdbService.parseBatchOutput(response, markers)
        assertEquals(listOf("line1\r\nline2"), result)
    }

    @Test
    fun parseBatchOutputPreservesInternalNewlines() {
        val markers = listOf("MARKER_0")
        val response = "line1\nline2\nline3\nMARKER_0\n"
        val result = NativeAdbService.parseBatchOutput(response, markers)
        assertEquals(listOf("line1\nline2\nline3"), result)
    }

    @Test
    fun parseBatchOutputHandlesEmptyOutputBetweenMarkers() {
        val markers = listOf("MARKER_0", "MARKER_1")
        val response = "\nMARKER_0\n\nMARKER_1\n"
        val result = NativeAdbService.parseBatchOutput(response, markers)
        assertEquals(listOf("", ""), result)
    }

    @Test
    fun parseBatchOutputHandlesOutputAfterLastMarker() {
        val markers = listOf("MARKER_0")
        val response = "output0\nMARKER_0\ntail data"
        val result = NativeAdbService.parseBatchOutput(response, markers)
        assertEquals(listOf("output0"), result)
    }

    @Test(expected = IllegalStateException::class)
    fun parseBatchOutputThrowsWhenMarkerMissing() {
        val markers = listOf("MISSING_MARKER")
        val response = "some output without any marker"
        NativeAdbService.parseBatchOutput(response, markers)
    }

    @Test(expected = IllegalStateException::class)
    fun parseBatchOutputThrowsWhenSecondMarkerMissing() {
        val markers = listOf("MARKER_0", "MISSING_MARKER")
        val response = "output0\nMARKER_0\noutput1 without second marker"
        NativeAdbService.parseBatchOutput(response, markers)
    }

    @Test
    fun parseBatchOutputHandlesEmptyMarkersList() {
        val result = NativeAdbService.parseBatchOutput("any response", emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseBatchOutputHandlesMultipleCommands() {
        val markers = listOf("M0", "M1", "M2")
        val response = "r0\nM0\nr1\nM1\nr2\nM2\n"
        val result = NativeAdbService.parseBatchOutput(response, markers)
        assertEquals(listOf("r0", "r1", "r2"), result)
    }

    @Test
    fun shellBatchBuilderAccumulatesCommands() {
        val builder = NativeAdbService.ShellBatchBuilder()
        builder.command("echo hello")
        builder.command("echo world")
        assertEquals(2, builder.commands.size)
        assertEquals("echo hello", builder.commands[0])
        assertEquals("echo world", builder.commands[1])
    }

    @Test
    fun shellBatchBuilderStartsEmpty() {
        val builder = NativeAdbService.ShellBatchBuilder()
        assertTrue(builder.commands.isEmpty())
    }
}
