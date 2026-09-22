package com.xixka.topstatusbar.items;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.DirectByteBufferAllocator;
import com.intellij.util.io.IOUtil;
import com.xixka.topstatusbar.model.AbstractStatusItem;
import com.xixka.topstatusbar.model.StatusSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
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
 * Click triggers a garbage collection（与原生一致：单击 System.gc()）。
 * <p>
 * 单元格显示样式（2026-09-22 第三轮用户反馈定案：之前版本样式正确，
 * 回退 d236a4f/3bce32b 的原生仪表条画法）：
 * <ul>
 * <li>纯文本 {@code "700 MB / 1700 MB"} 自有 KB/MB/GB 格式，不再绘制
 *     已分配/已用仪表条（顶栏上满单元格色块观感差，用户两次截图实证）、
 *     不再用 UIBundle memory.usage.panel.message.text 与 Registry 总预估口径</li>
 * <li>用量超过最大值 90% 时文本转警告色（回退版本的阈值样式）</li>
 * </ul>
 * 悬停详情保留原生同构 HTML 模板：堆 / 字节缓冲区(直接) / JVM /
 * 内存映射文件 / 操作系统提供（总计1/总计2）。后两组数据源
 * （MMappedFileStorage、PlatformMemoryUtil）为 2026.x 平台内部类，
 * 241 编译基线不存在——按只读反射获取，任何失败（含 241 运行时）
 * 静默省略对应行，与 241 原生展示一致。
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
        refreshTask = com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
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
        MemoryUsage heap = MEMORY_MX_BEAN.getHeapMemoryUsage();
        long heapUsedBytes = heap.getUsed();
        long heapCommittedBytes = heap.getCommitted();
        long heapMaxBytes = heap.getMax();

        long fileCacheBytes = DirectByteBufferAllocator.ALLOCATOR.getStatistics().totalSizeOfBuffersAllocatedInBytes;
        long directTotalBytes = IOUtil.directBuffersTotalAllocatedSize();
        if (directTotalBytes <= 0) {
            directTotalBytes = fileCacheBytes;
        }
        long directOthersBytes = Math.max(0L, directTotalBytes - fileCacheBytes);

        long threadStackBytes = (long) THREAD_MX_BEAN.getThreadCount() * IOUtil.MiB;
        long jvmInternalsBytes = jvmInternalsMemory(ManagementFactory.getMemoryPoolMXBeans());

        // 原生 AppMemoryUsage.estimatedTotalMemoryUsedBytes：已提交 + 线程栈 + 直接 + 内部
        long estimatedTotalBytes = roundUpTo(
                heapCommittedBytes + threadStackBytes + directTotalBytes + jvmInternalsBytes,
                100L * IOUtil.MiB);

        long usedMb = toMb(heapUsedBytes);
        long maxMb = toMb(heapMaxBytes);
        long estimatedMb = toMb(estimatedTotalBytes);

        // 显示样式回退（2026-09-22 用户定案「之前版本样式正确」）：
        // 自有 KB/MB/GB 纯文本 + 超 90% 警告色，不画仪表条、不用 UIBundle 文本
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        setText(format(used) + " / " + format(max));
        boolean critical = max > 0 && used * 100 / max > 90;
        setSeverity(critical ? StatusSeverity.WARNING : StatusSeverity.NORMAL);

        long committedMb = toMb(heapCommittedBytes);
        long fileCacheMb = toMb(fileCacheBytes);
        long directOthersMb = toMb(directOthersBytes);
        long internalsMb = toMb(jvmInternalsBytes);
        long threadStacksMb = toMb(threadStackBytes);

        setTooltip(buildTooltip(usedMb, committedMb, maxMb, fileCacheMb, directOthersMb,
                internalsMb, threadStacksMb, estimatedMb));
        setVisible(true);
    }

    /**
     * 与原生 memory.usage.panel.message.tooltip(-extended) 模板同构的 HTML
     * 表格（中文文案对齐原生语言包）。内存映射文件 / 操作系统提供段仅在
     * 2026.x 反射取数成功时展示（原生 241 同样没有）。
     */
    private static String buildTooltip(long usedMb, long committedMb, long maxMb,
                                       long fileCacheMb, long directOthersMb,
                                       long internalsMb, long threadStacksMb,
                                       long estimatedMb) {
        Long mappedFilesBytes = reflectiveStaticLong(
                "com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage", "totalBytesMapped");
        long[] osStats = reflectiveOsStats();

        StringBuilder html = new StringBuilder("<html><table>");
        section(html, "堆");
        valueRow(html, "已用:", usedMb + "M");
        valueRow(html, "已提交:", committedMb + "M");
        valueRow(html, "最大:", maxMb + "M");
        section(html, "字节缓冲区(直接)");
        valueRow(html, "文件缓存:", fileCacheMb + "M");
        valueRow(html, "其他:", directOthersMb + "M");
        section(html, "JVM");
        valueRow(html, "内部:", internalsMb + "M");
        valueRow(html, "线程堆栈:", "~" + threadStacksMb + "M");
        wideRow(html, "总计(预估):", "~" + estimatedMb + "M");
        if (mappedFilesBytes != null) {
            wideRow(html, "内存映射文件:", toMb(mappedFilesBytes) + "M");
        }
        if (osStats != null) {
            section(html, "操作系统提供");
            // MemoryStats 字段单位为字节（pmu.kt javadoc 核实），必须 toMb——
            // 2026-09-22 用户日志实证漏除时显示 284200960M（=271MB 的字节数）
            boldValueRow(html, "RAM 中的文件映射:", toMb(osStats[0]) + "M");
            boldValueRow(html, "总计1 (匿名 RAM):", toMb(osStats[1]) + "M");
            boldValueRow(html, "总计2 (总计1+交换):", toMb(osStats[2]) + "M");
        }
        html.append("</table></html>");
        return html.toString();
    }

    /**
     * 只读反射静态方法（null = 类不存在或调用失败，调用方省略对应行）。
     * MMappedFileStorage / PlatformMemoryUtil 是 2026.x 平台内部类，241
     * 编译基线无对应符号——反射是唯一跨版本途径；仅读数值，不触碰 UI。
     */
    @Nullable
    private static Long reflectiveStaticLong(String className, String methodName) {
        try {
            Class<?> clazz = Class.forName(className);
            Object result = clazz.getMethod(methodName).invoke(null);
            return result instanceof Long ? (Long) result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 原生 PlatformMemoryUtil.getCurrentProcessMemoryStats（@JvmStatic
     * getInstance + 实例方法）。返回 null = 不可用/Windows 旧版恒 0——
     * 原生同规则隐藏「操作系统提供」段。
     */
    @Nullable
    private static long[] reflectiveOsStats() {
        try {
            Class<?> util = Class.forName("com.intellij.diagnostic.PlatformMemoryUtil");
            Object instance = util.getMethod("getInstance").invoke(null);
            if (instance == null) {
                return null;
            }
            Object stats = util.getMethod("getCurrentProcessMemoryStats").invoke(instance);
            if (stats == null) {
                return null;
            }
            Class<?> statsClass = stats.getClass();
            long fileMappingsRam = (Long) statsClass.getMethod("getFileMappingsRam").invoke(stats);
            long ramMinusFileMappings = (Long) statsClass.getMethod("getRamMinusFileMappings").invoke(stats);
            long ramPlusSwapMinusFileMappings =
                    (Long) statsClass.getMethod("getRamPlusSwapMinusFileMappings").invoke(stats);
            if (ramMinusFileMappings == 0) {
                return null; // 原生：旧 Windows 上该值恒 0，隐藏整段
            }
            return new long[]{fileMappingsRam, ramMinusFileMappings, ramPlusSwapMinusFileMappings};
        } catch (Throwable t) {
            return null;
        }
    }

    private static void section(StringBuilder html, String title) {
        html.append("<tr><td colspan='3'><b>").append(title).append("</b></td></tr>");
    }

    private static void valueRow(StringBuilder html, String label, String value) {
        html.append("<tr><td> </td><td>").append(label).append("</td><td align='right'>")
                .append(value).append("</td></tr>");
    }

    private static void boldValueRow(StringBuilder html, String label, String value) {
        html.append("<tr><td> </td><td><b>").append(label).append("</b></td><td align='right'>")
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

    /** 回退版本的单元格文本格式（2026-09-22 用户定案沿用）：KB / MB / 1 位小数 GB。 */
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
