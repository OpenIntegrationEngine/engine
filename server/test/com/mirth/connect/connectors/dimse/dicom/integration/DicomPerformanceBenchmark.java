// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Saga IT, LLC

package com.mirth.connect.connectors.dimse.dicom.integration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory;
import com.mirth.connect.connectors.dimse.dicom.DicomLibraryFactory.DicomLibrary;
import com.mirth.connect.connectors.dimse.dicom.OieDicomConverter;
import com.mirth.connect.connectors.dimse.dicom.OieDicomObject;
import com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomConverter;
import com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.dcm2.Dcm2DicomSender;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomConverter;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomObject;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomSender;
import com.mirth.connect.donkey.model.message.RawMessage;
import com.mirth.connect.donkey.server.channel.SourceConnector;

/**
 * Performance benchmark comparing dcm4che2 and dcm4che5 backends.
 *
 * <p>Measures converter throughput (serialize/deserialize/XML), network C-STORE
 * throughput, and memory usage. Results are printed as formatted comparison tables.
 *
 * <p>This is not a regression gate — it produces data for human review. Run with:
 * <pre>
 * ./gradlew :server:test --tests "*.DicomPerformanceBenchmark" --info
 * </pre>
 */
public class DicomPerformanceBenchmark extends DicomIntegrationTestBase {

    // Benchmark parameters
    private static final int WARMUP_ITERATIONS = 50;
    private static final int MEASURE_ITERATIONS = 200;
    private static final int NETWORK_WARMUP = 5;
    private static final int NETWORK_MEASURE = 50;

    private Dcm2DicomReceiver dcm2Receiver;
    private Dcm2DicomSender dcm2Sender;

    @Override
    public void tearDown() {
        if (dcm2Sender != null) {
            try { dcm2Sender.close(); } catch (Exception e) { /* ignore */ }
            try { dcm2Sender.stop(); } catch (Exception e) { /* ignore */ }
            dcm2Sender = null;
        }
        if (dcm2Receiver != null) {
            try { dcm2Receiver.stop(); } catch (Exception e) { /* ignore */ }
            dcm2Receiver = null;
        }
        super.tearDown();
    }

    @Test
    public void benchmarkConverterSerialization() throws Exception {
        System.out.println("\n========== CONVERTER: dicomObjectToByteArray ==========");
        System.out.printf("%-12s %12s %12s %12s%n", "Backend", "Ops/sec", "Avg (ms)", "Mem (KB)");
        System.out.println("----------------------------------------------------");

        // dcm4che2
        BenchmarkResult dcm2Result = benchmarkSerialization(new Dcm2DicomConverter(), "dcm2");
        printRow("dcm4che2", dcm2Result);

        // dcm4che5
        BenchmarkResult dcm5Result = benchmarkSerialization(new Dcm5DicomConverter(), "dcm5");
        printRow("dcm4che5", dcm5Result);

        printSpeedup(dcm2Result, dcm5Result);
    }

    @Test
    public void benchmarkConverterDeserialization() throws Exception {
        System.out.println("\n========== CONVERTER: byteArrayToDicomObject ==========");
        System.out.printf("%-12s %12s %12s %12s%n", "Backend", "Ops/sec", "Avg (ms)", "Mem (KB)");
        System.out.println("----------------------------------------------------");

        // Prepare test data
        Dcm2DicomConverter dcm2Conv = new Dcm2DicomConverter();
        byte[] dcm2Bytes = createTestBytes(dcm2Conv);

        Dcm5DicomConverter dcm5Conv = new Dcm5DicomConverter();
        byte[] dcm5Bytes = createTestBytes(dcm5Conv);

        // dcm4che2
        BenchmarkResult dcm2Result = benchmarkDeserialization(dcm2Conv, dcm2Bytes);
        printRow("dcm4che2", dcm2Result);

        // dcm4che5
        BenchmarkResult dcm5Result = benchmarkDeserialization(dcm5Conv, dcm5Bytes);
        printRow("dcm4che5", dcm5Result);

        printSpeedup(dcm2Result, dcm5Result);
    }

    @Test
    public void benchmarkConverterXml() throws Exception {
        System.out.println("\n========== CONVERTER: dicomBytesToXml ==========");
        System.out.printf("%-12s %12s %12s %12s%n", "Backend", "Ops/sec", "Avg (ms)", "Mem (KB)");
        System.out.println("----------------------------------------------------");

        // Prepare base64-encoded test data
        Dcm2DicomConverter dcm2Conv = new Dcm2DicomConverter();
        byte[] dcm2Raw = createTestBytes(dcm2Conv);
        byte[] dcm2B64 = java.util.Base64.getEncoder().encode(dcm2Raw);

        Dcm5DicomConverter dcm5Conv = new Dcm5DicomConverter();
        byte[] dcm5Raw = createTestBytes(dcm5Conv);
        byte[] dcm5B64 = java.util.Base64.getEncoder().encode(dcm5Raw);

        // dcm4che2
        BenchmarkResult dcm2Result = benchmarkXmlConversion(dcm2Conv, dcm2B64);
        printRow("dcm4che2", dcm2Result);

        // dcm4che5
        BenchmarkResult dcm5Result = benchmarkXmlConversion(dcm5Conv, dcm5B64);
        printRow("dcm4che5", dcm5Result);

        printSpeedup(dcm2Result, dcm5Result);
    }

    @Test
    public void benchmarkNetworkCStoreDcm5() throws Exception {
        System.out.println("\n========== NETWORK: dcm5 sender → dcm5 receiver (C-STORE) ==========");
        System.out.printf("%-12s %12s %12s %12s%n", "Phase", "Ops/sec", "Avg (ms)", "Total (ms)");
        System.out.println("----------------------------------------------------");

        int port = allocatePort();
        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);
        receiver = startDcm5Receiver(port, mockConnector);

        // Create test files
        List<File> files = new ArrayList<>();
        for (int i = 0; i < NETWORK_WARMUP + NETWORK_MEASURE; i++) {
            files.add(createDicomTempFile("Bench^Patient" + i, "BENCH" + String.format("%04d", i)));
        }

        // Warmup
        for (int i = 0; i < NETWORK_WARMUP; i++) {
            sendOneDcm5File(port, files.get(i));
        }

        // Measure
        long[] latencies = new long[NETWORK_MEASURE];
        long start = System.nanoTime();
        for (int i = 0; i < NETWORK_MEASURE; i++) {
            long opStart = System.nanoTime();
            sendOneDcm5File(port, files.get(NETWORK_WARMUP + i));
            latencies[i] = System.nanoTime() - opStart;
        }
        long totalNs = System.nanoTime() - start;

        printNetworkRow("Measured", latencies, totalNs);
        printLatencyPercentiles("dcm5→dcm5", latencies);
    }

    @Test
    public void benchmarkNetworkCStoreDcm2() throws Exception {
        System.out.println("\n========== NETWORK: dcm2 sender → dcm2 receiver (C-STORE) ==========");
        System.out.printf("%-12s %12s %12s %12s%n", "Phase", "Ops/sec", "Avg (ms)", "Total (ms)");
        System.out.println("----------------------------------------------------");

        int port = allocatePort();
        ArgumentCaptor<RawMessage> captor = ArgumentCaptor.forClass(RawMessage.class);
        SourceConnector mockConnector = createMockSourceConnector(captor);

        // Start dcm2 receiver
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        dcm2Receiver = new Dcm2DicomReceiver(mockConnector, new TestConfig());
        dcm2Receiver.setHostname("127.0.0.1");
        dcm2Receiver.setPort(port);
        dcm2Receiver.setAEtitle("BENCH_SCP");
        dcm2Receiver.setDestination(tempFolder.getRoot().getAbsolutePath());
        dcm2Receiver.setTransferSyntax(new String[] { "1.2.840.10008.1.2", "1.2.840.10008.1.2.1" });
        dcm2Receiver.initTransferCapability();
        dcm2Receiver.start();
        waitForPort(port, 2000);

        // Create test files using dcm2 converter
        List<File> files = new ArrayList<>();
        for (int i = 0; i < NETWORK_WARMUP + NETWORK_MEASURE; i++) {
            files.add(createDcm2TempFile("Bench^Patient" + i, "BENCH" + String.format("%04d", i)));
        }

        // Warmup
        for (int i = 0; i < NETWORK_WARMUP; i++) {
            sendOneDcm2File(port, files.get(i));
        }

        // Measure
        long[] latencies = new long[NETWORK_MEASURE];
        long start = System.nanoTime();
        for (int i = 0; i < NETWORK_MEASURE; i++) {
            long opStart = System.nanoTime();
            sendOneDcm2File(port, files.get(NETWORK_WARMUP + i));
            latencies[i] = System.nanoTime() - opStart;
        }
        long totalNs = System.nanoTime() - start;

        printNetworkRow("Measured", latencies, totalNs);
        printLatencyPercentiles("dcm2→dcm2", latencies);
    }

    @Test
    public void benchmarkMemoryFootprint() throws Exception {
        System.out.println("\n========== MEMORY: converter object creation ==========");
        System.out.printf("%-12s %12s %12s%n", "Backend", "Per-obj (B)", "100-obj (KB)");
        System.out.println("--------------------------------------------");

        // dcm4che2
        long dcm2Single = measureObjectMemory(() -> {
            Dcm2DicomConverter c = new Dcm2DicomConverter();
            OieDicomObject obj = c.createDicomObject();
            obj.putString(Tag.PatientName, "PN", "Test^Patient");
            obj.putString(Tag.PatientID, "LO", "ID001");
            return obj;
        });
        long dcm2Bulk = measureBulkMemory(() -> {
            Dcm2DicomConverter c = new Dcm2DicomConverter();
            OieDicomObject obj = c.createDicomObject();
            obj.putString(Tag.PatientName, "PN", "Test^Patient");
            obj.putString(Tag.PatientID, "LO", "ID001");
            return obj;
        }, 100);
        System.out.printf("%-12s %12d %12.1f%n", "dcm4che2", dcm2Single, dcm2Bulk / 1024.0);

        // dcm4che5
        long dcm5Single = measureObjectMemory(() -> {
            Dcm5DicomConverter c = new Dcm5DicomConverter();
            OieDicomObject obj = c.createDicomObject();
            obj.putString(Tag.PatientName, "PN", "Test^Patient");
            obj.putString(Tag.PatientID, "LO", "ID001");
            return obj;
        });
        long dcm5Bulk = measureBulkMemory(() -> {
            Dcm5DicomConverter c = new Dcm5DicomConverter();
            OieDicomObject obj = c.createDicomObject();
            obj.putString(Tag.PatientName, "PN", "Test^Patient");
            obj.putString(Tag.PatientID, "LO", "ID001");
            return obj;
        }, 100);
        System.out.printf("%-12s %12d %12.1f%n", "dcm4che5", dcm5Single, dcm5Bulk / 1024.0);
    }

    private BenchmarkResult benchmarkSerialization(OieDicomConverter converter, String label) throws IOException {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            OieDicomObject obj = createTestObject(converter);
            converter.dicomObjectToByteArray(obj);
        }

        // Measure
        forceGc();
        long memBefore = usedMemory();
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            OieDicomObject obj = createTestObject(converter);
            converter.dicomObjectToByteArray(obj);
        }
        long elapsed = System.nanoTime() - start;
        long memAfter = usedMemory();

        return new BenchmarkResult(MEASURE_ITERATIONS, elapsed, memAfter - memBefore);
    }

    private BenchmarkResult benchmarkDeserialization(OieDicomConverter converter, byte[] bytes) throws IOException {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            converter.byteArrayToDicomObject(bytes, false);
        }

        // Measure
        forceGc();
        long memBefore = usedMemory();
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            converter.byteArrayToDicomObject(bytes, false);
        }
        long elapsed = System.nanoTime() - start;
        long memAfter = usedMemory();

        return new BenchmarkResult(MEASURE_ITERATIONS, elapsed, memAfter - memBefore);
    }

    private BenchmarkResult benchmarkXmlConversion(OieDicomConverter converter, byte[] b64Bytes) throws Exception {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            converter.dicomBytesToXml(b64Bytes);
        }

        // Measure
        forceGc();
        long memBefore = usedMemory();
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            converter.dicomBytesToXml(b64Bytes);
        }
        long elapsed = System.nanoTime() - start;
        long memAfter = usedMemory();

        return new BenchmarkResult(MEASURE_ITERATIONS, elapsed, memAfter - memBefore);
    }

    private void sendOneDcm5File(int port, File file) throws Exception {
        Dcm5DicomSender snd = new Dcm5DicomSender(new TestConfig());
        snd.setRemoteHost("127.0.0.1");
        snd.setRemotePort(port);
        snd.setCalledAET("TEST_SCP");
        snd.setCalling("BENCH_SCU");
        snd.addFile(file);
        snd.configureTransferCapability();
        snd.start();
        try {
            CapturingDimseRspHandler handler = new CapturingDimseRspHandler(1);
            snd.open();
            snd.send(handler);
            handler.awaitResponses(10000);
            snd.close();
        } finally {
            try { snd.stop(); } catch (Exception e) { /* ignore */ }
        }
    }

    private void sendOneDcm2File(int port, File file) throws Exception {
        DicomLibraryFactory.resetForTesting(DicomLibrary.DCM4CHE2);
        Dcm2DicomSender snd = new Dcm2DicomSender(new TestConfig());
        snd.setRemoteHost("127.0.0.1");
        snd.setRemotePort(port);
        snd.setCalledAET("BENCH_SCP");
        snd.setCalling("BENCH_SCU");
        snd.addFile(file);
        snd.configureTransferCapability();
        snd.start();
        try {
            snd.open();
            snd.send((cmd, data) -> {});
            snd.close();
        } finally {
            try { snd.stop(); } catch (Exception e) { /* ignore */ }
        }
    }

    private File createDcm2TempFile(String patientName, String patientId) throws Exception {
        Dcm2DicomConverter converter = new Dcm2DicomConverter();
        OieDicomObject obj = converter.createDicomObject();
        obj.putString(Tag.PatientName, "PN", patientName);
        obj.putString(Tag.PatientID, "LO", patientId);
        obj.putString(Tag.StudyInstanceUID, "UI", org.dcm4che2.util.UIDUtils.createUID());
        obj.putString(Tag.SeriesInstanceUID, "UI", org.dcm4che2.util.UIDUtils.createUID());
        obj.putString(Tag.SOPInstanceUID, "UI", org.dcm4che2.util.UIDUtils.createUID());
        obj.putString(Tag.Modality, "CS", "CT");
        obj.initFileMetaInformation(
                "1.2.840.10008.5.1.4.1.1.2",
                org.dcm4che2.util.UIDUtils.createUID(),
                "1.2.840.10008.1.2");
        byte[] bytes = converter.dicomObjectToByteArray(obj);
        File tempFile = tempFolder.newFile(patientId + ".dcm");
        Files.write(tempFile.toPath(), bytes);
        return tempFile;
    }

    private OieDicomObject createTestObject(OieDicomConverter converter) {
        OieDicomObject obj = converter.createDicomObject();
        obj.putString(Tag.PatientName, "PN", "Benchmark^Patient");
        obj.putString(Tag.PatientID, "LO", "BENCH001");
        obj.putString(Tag.StudyInstanceUID, "UI", "1.2.3.4.5.6.7.8.9");
        obj.putString(Tag.SeriesInstanceUID, "UI", "1.2.3.4.5.6.7.8.10");
        obj.putString(Tag.SOPInstanceUID, "UI", "1.2.3.4.5.6.7.8.11");
        obj.putString(Tag.Modality, "CS", "CT");
        obj.putString(Tag.StudyDate, "DA", "20260326");
        obj.putString(Tag.StudyDescription, "LO", "Performance benchmark test study");
        obj.putString(Tag.InstitutionName, "LO", "Test Hospital");
        obj.putString(Tag.ReferringPhysicianName, "PN", "Doctor^Test");
        obj.initFileMetaInformation("1.2.840.10008.5.1.4.1.1.2",
                "1.2.3.4.5.6.7.8.11", "1.2.840.10008.1.2");
        return obj;
    }

    private byte[] createTestBytes(OieDicomConverter converter) throws IOException {
        OieDicomObject obj = createTestObject(converter);
        return converter.dicomObjectToByteArray(obj);
    }

    @FunctionalInterface
    private interface ObjectFactory {
        Object create() throws Exception;
    }

    private long measureObjectMemory(ObjectFactory factory) throws Exception {
        forceGc();
        long before = usedMemory();
        Object obj = factory.create();
        long after = usedMemory();
        // Keep reference alive past measurement
        if (obj.hashCode() == Integer.MIN_VALUE) System.out.print("");
        return Math.max(0, after - before);
    }

    private long measureBulkMemory(ObjectFactory factory, int count) throws Exception {
        forceGc();
        long before = usedMemory();
        Object[] objects = new Object[count];
        for (int i = 0; i < count; i++) {
            objects[i] = factory.create();
        }
        long after = usedMemory();
        // Keep references alive past measurement
        if (objects[0].hashCode() == Integer.MIN_VALUE) System.out.print("");
        return Math.max(0, after - before);
    }

    private static void forceGc() {
        System.gc();
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        System.gc();
    }

    private static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static class BenchmarkResult {
        final int iterations;
        final long elapsedNs;
        final long memoryDeltaBytes;

        BenchmarkResult(int iterations, long elapsedNs, long memoryDeltaBytes) {
            this.iterations = iterations;
            this.elapsedNs = elapsedNs;
            this.memoryDeltaBytes = memoryDeltaBytes;
        }

        double opsPerSec() {
            return iterations / (elapsedNs / 1_000_000_000.0);
        }

        double avgMs() {
            return (elapsedNs / 1_000_000.0) / iterations;
        }

        double memoryKB() {
            return Math.max(0, memoryDeltaBytes) / 1024.0;
        }
    }

    private static void printRow(String label, BenchmarkResult result) {
        System.out.printf("%-12s %12.1f %12.3f %12.1f%n",
                label, result.opsPerSec(), result.avgMs(), result.memoryKB());
    }

    private static void printSpeedup(BenchmarkResult dcm2, BenchmarkResult dcm5) {
        double speedup = dcm5.opsPerSec() / dcm2.opsPerSec();
        System.out.printf("%n  dcm5 vs dcm2: %.2fx %s%n",
                Math.abs(speedup),
                speedup >= 1.0 ? "faster" : "slower");
    }

    private static void printNetworkRow(String label, long[] latenciesNs, long totalNs) {
        double avgMs = 0;
        for (long l : latenciesNs) avgMs += l;
        avgMs = (avgMs / latenciesNs.length) / 1_000_000.0;
        double opsPerSec = latenciesNs.length / (totalNs / 1_000_000_000.0);
        double totalMs = totalNs / 1_000_000.0;
        System.out.printf("%-12s %12.1f %12.1f %12.0f%n", label, opsPerSec, avgMs, totalMs);
    }

    private static void printLatencyPercentiles(String label, long[] latenciesNs) {
        long[] sorted = latenciesNs.clone();
        java.util.Arrays.sort(sorted);
        System.out.printf("%n  %s latency percentiles (ms):%n", label);
        System.out.printf("    p50: %.1f  p90: %.1f  p99: %.1f  min: %.1f  max: %.1f%n",
                sorted[(int)(sorted.length * 0.50)] / 1_000_000.0,
                sorted[(int)(sorted.length * 0.90)] / 1_000_000.0,
                sorted[(int)(sorted.length * 0.99)] / 1_000_000.0,
                sorted[0] / 1_000_000.0,
                sorted[sorted.length - 1] / 1_000_000.0);
    }
}
