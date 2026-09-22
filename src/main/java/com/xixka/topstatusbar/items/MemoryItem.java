package com.xixka.topstatusbar.items;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.DirectByteBufferAllocator;
import com.intellij.util.io.IOUtil;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 内存指示器: IDE heap usage, refreshed on a background schedule.
 * Click triggers a garbage collection.
 * <p>
 * 悬停提示复刻原生 MemoryUsagePanel 的 HTML 详情面板（master 源码核实，
 * 2026-09-22）：堆（已用/已提交/最大）、字节缓冲区(直接)（文件缓存/其他）、
 * JVM（内部/线程堆栈/总计(预估)）三段结构、取值公式与原生一致，总计
 * 口径为原生 roundUpTo(…, 100M)。两处刻意差异（241 编译基线无对应 API）：
 * 不含「内存映射文件」行（MMappedFileStorage 为 2026.x 新模块类）；
 * 不含 OS 段「操作系统提供」（依赖 JBR 专有 API，原生也仅在 extended
 * 注册表键开启时展示）。
 */
public final class MemoryItem extends AbstractStatusItem {

    private static final long UPDATE_INTERVAL_SECONDS = 2;

    @Nullable
    private Future<?> refreshTask;

    private static final MemoryMXBean MEMORY_MX_BEAN = ManagementFactory.getMemoryMXBean();
    private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();

    public MemoryItem() {
        super("memoryIndicator", 85);
    }

    @Override
    public @Nullable String getPlatformWidgetId() {
        return "Memory";
    }

    @Override
    protected void install() {
        // Plain text like the native memory indicator widget: no icon.
        refreshTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                this::scheduledUpdate, UPDATE_INTERVAL_SECONDS, UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        ApplicationManager.getApplication().invokeLater(this::update);
    }

    @Override
    public void uninstall() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        super.uninstall();
    }

    @Override
    public void onClick(@Nullable Project project, @NotNull JComponent source) {
        // Same as the native memory indicator widget: click runs the GC.
        System.gc();
        ApplicationManager.getApplication().invokeLater(this::update);
    }

    private void scheduledUpdate() {
        if (project() == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(this::update);
    }

    private void update() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        setText(format(used) + " / " + format(max));
        boolean critical = max > 0 && used * 100 / max > 90;
        setSeverity(critical ? StatusSeverity.WARNING : StatusSeverity.NORMAL);
        setTooltip(buildTooltip());
        setVisible(true);
    }

    /**
     * 与原生 memory.usage.panel.message.tooltip 模板同构的 HTML 表格
     * （中文文案对齐原生语言包）。
     */
    private static String buildTooltip() {
        MemoryUsage heap = MEMORY_MX_BEAN.getHeapMemoryUsage();
        long heapUsed = heap.getUsed();
        long heapCommitted = heap.getCommitted();
        long heapMax = heap.getMax();

        // 原生同款：file cache = DirectByteBufferAllocator 统计；direct 总量 =
        // IOUtil 计数（不可用时以 file cache 为下界）；Others = 总量 − file cache
        long fileCacheBytes = DirectByteBufferAllocator.ALLOCATOR.getStatistics().totalSizeOfBuffersAllocatedInBytes;
        long directTotalBytes = IOUtil.directBuffersTotalAllocatedSize();
        if (directTotalBytes <= 0) {
            directTotalBytes = fileCacheBytes;
        }
        long directOthersBytes = Math.max(0L, directTotalBytes - fileCacheBytes);

        long threadStackBytes = (long) THREAD_MX_BEAN.getThreadCount() * IOUtil.MiB;
        long jvmInternalsBytes = jvmInternalsMemory(ManagementFactory.getMemoryPoolMXBeans());

        // 原生口径：总计(预估) = 堆已用 + JVM 内部 + 线程堆栈 + 直接缓冲区，
        // 向上取整到 100M；原生还加内存映射文件（241 无对应 API，此处不含）
        long estimatedTotalBytes = roundUpTo(
                heapUsed + jvmInternalsBytes + threadStackBytes + directTotalBytes,
                100L * IOUtil.MiB);

        StringBuilder html = new StringBuilder("<html><table>");
        section(html, "堆");
        valueRow(html, "已用:", toMb(heapUsed) + "M");
        valueRow(html, "已提交:", toMb(heapCommitted) + "M");
        valueRow(html, "最大:", toMb(heapMax) + "M");
        section(html, "字节缓冲区(直接)");
        valueRow(html, "文件缓存:", toMb(fileCacheBytes) + "M");
        valueRow(html, "其他:", toMb(directOthersBytes) + "M");
        section(html, "JVM");
        valueRow(html, "内部:", toMb(jvmInternalsBytes) + "M");
        valueRow(html, "线程堆栈:", "~" + toMb(threadStackBytes) + "M");
        wideRow(html, "总计(预估):", "~" + toMb(estimatedTotalBytes) + "M");
        html.append("</table></html>");
        return html.toString();
    }

    private static void section(StringBuilder html, String title) {
        html.append("<tr><td colspan='3'><b>").append(title).append("</b></td></tr>");
    }

    private static void valueRow(StringBuilder html, String label, String value) {
        html.append("<tr><td> </td><td>").append(label).append("</td><td align='right'>")
                .append(value).append("</td></tr>");
    }

    private static void wideRow(StringBuilder html, String label, String value) {
        html.append("<tr><td colspan='2'>").append(label).append("</td><td align='right'>")
                .append(value).append("</td></tr>");
    }

    /** 原生 jvmInternalsMemory：所有 NON_HEAP 池已用字节求和。 */
    private static long jvmInternalsMemory(List<MemoryPoolMXBean> pools) {
        long sum = 0L;
        for (MemoryPoolMXBean pool : pools) {
            if (pool.getType() == MemoryType.NON_HEAP) {
                MemoryUsage usage = pool.getUsage();
                if (usage != null) {
                    sum += usage.getUsed();
                }
            }
        }
        return sum;
    }

    /** 原生 roundUpTo：向上取整到最近的 bucket。 */
    private static long roundUpTo(long value, long bucket) {
        long fraction = value / bucket;
        long remainder = value % bucket;
        return remainder > 0 ? (fraction + 1) * bucket : value;
    }

    private static long toMb(long bytes) {
        return bytes / IOUtil.MiB;
    }

    private static String format(long bytes) {
        if (bytes < 1024 * 1024) {
            return bytes / 1024 + " KB";
        }
        long megaBytes = bytes / (1024 * 1024);
        if (megaBytes < 1024) {
            return megaBytes + " MB";
        }
        return String.format(Locale.ROOT, "%.1f GB", megaBytes / 1024.0);
    }
}
