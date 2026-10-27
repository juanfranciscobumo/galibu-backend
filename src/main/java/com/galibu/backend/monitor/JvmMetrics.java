package com.galibu.backend.monitor;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Estado de la JVM: memoria, CPU, hilos, GC y tiempo encendido. */
@Component
public class JvmMetrics {

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        long heapMax = heap.getMax() > 0 ? heap.getMax() : Runtime.getRuntime().maxMemory();
        out.put("heapUsed", heap.getUsed());
        out.put("heapCommitted", heap.getCommitted());
        out.put("heapMax", heapMax);
        out.put("nonHeapUsed", nonHeap.getUsed());

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        out.put("threads", threads.getThreadCount());
        out.put("peakThreads", threads.getPeakThreadCount());

        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        out.put("uptimeMs", runtime.getUptime());
        out.put("startedAt", runtime.getStartTime());
        out.put("processors", Runtime.getRuntime().availableProcessors());

        long gcCount = 0;
        long gcTimeMs = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() > 0) {
                gcCount += gc.getCollectionCount();
            }
            if (gc.getCollectionTime() > 0) {
                gcTimeMs += gc.getCollectionTime();
            }
        }
        out.put("gcCount", gcCount);
        out.put("gcTimeMs", gcTimeMs);
        out.put("classesLoaded", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());

        double processCpu = -1;
        double systemCpu = -1;
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            processCpu = sun.getProcessCpuLoad();
            systemCpu = sun.getCpuLoad();
            out.put("memoryFree", sun.getFreeMemorySize());
            out.put("memoryTotal", sun.getTotalMemorySize());
        }
        out.put("processCpu", toPercent(processCpu));
        out.put("systemCpu", toPercent(systemCpu));
        return out;
    }

    private static double toPercent(double value) {
        return value < 0 ? -1 : Math.round(value * 1000.0) / 10.0;
    }
}
