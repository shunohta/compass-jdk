/**
 * GC イベントをリアルタイムで購読・表示する簡易アナライザ。
 *
 * <p>{@link java.lang.management.GarbageCollectorMXBean} と
 * {@link javax.management.NotificationEmitter} を使って GC 通知を購読し、
 * GC の種類、停止時間、前後のメモリ使用量をリアルタイムで表示する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>GarbageCollectorMXBean で GC イベントを監視する方法</li>
 *   <li>GC 通知の構造（GcInfo: 開始時刻、所要時間、メモリプール変化）</li>
 *   <li>Minor GC と Major GC の頻度や停止時間の違い</li>
 *   <li>MXBean による JVM 内部情報へのアクセス</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java -Xmx128m -Xms128m -XX:+UseG1GC \
 *       src/main/java/GcLogAnalyzer.java
 * }</pre>
 *
 * @author jdk-core
 * @see java.lang.management.GarbageCollectorMXBean
 * @see com.sun.management.GarbageCollectionNotificationInfo
 * @see javax.management.NotificationEmitter
 */

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

// GC 統計用カウンター（複数スレッドから安全にアクセスするため Atomic 型を使用）
static final AtomicInteger minorGcCount = new AtomicInteger(0);
static final AtomicInteger majorGcCount = new AtomicInteger(0);
static final AtomicLong totalMinorPause = new AtomicLong(0);
static final AtomicLong totalMajorPause = new AtomicLong(0);

/**
 * GC 通知を購読し、メモリ負荷を生成して GC イベントをリアルタイムに観察する。
 * GarbageCollectorMXBean にリスナーを登録した後、3 フェーズのメモリ負荷を生成し、
 * 最後に GC 統計のサマリーを表示する。
 */
void main() {
    IO.println("=== GC ログ簡易アナライザ ===");
    IO.println("推奨: -Xmx128m -Xms128m -XX:+UseG1GC\n");

    // GC 通知リスナーを登録
    registerGcListeners();

    IO.println("[Info] GC 通知リスナーを登録しました。メモリ負荷を生成します...\n");
    IO.println(String.format("%-6s %-28s %-10s %-22s %-22s",
            "GC#", "種類", "停止(ms)", "使用量(前)", "使用量(後)"));
    IO.println("-".repeat(90));

    // メモリ負荷を生成して GC イベントを誘発
    generateMemoryPressure();

    // 統計サマリーを表示
    IO.println("\n" + "=".repeat(90));
    IO.println("=== GC 統計サマリー ===");
    IO.println("  Minor GC 回数:     " + minorGcCount.get());
    IO.println("  Minor GC 合計停止: " + totalMinorPause.get() + " ms");
    IO.println("  Major GC 回数:     " + majorGcCount.get());
    IO.println("  Major GC 合計停止: " + totalMajorPause.get() + " ms");
    IO.println("  全 GC 合計停止:    " + (totalMinorPause.get() + totalMajorPause.get()) + " ms");

    var runtime = Runtime.getRuntime();
    IO.println("\n=== 最終メモリ状況 ===");
    IO.println("  ヒープ最大: " + formatMB(runtime.maxMemory()));
    IO.println("  ヒープ合計: " + formatMB(runtime.totalMemory()));
    IO.println("  使用中:     " + formatMB(runtime.totalMemory() - runtime.freeMemory()));
    IO.println("\n=== 完了 ===");
}

/**
 * すべての GarbageCollectorMXBean に NotificationListener を登録して GC イベントを捕捉する。
 * GarbageCollectorMXBean が {@link NotificationEmitter} を実装している場合のみリスナーを登録する。
 * GC 通知を受信すると {@link #handleGcNotification} に処理を委譲する。
 */
void registerGcListeners() {
    List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

    for (GarbageCollectorMXBean gcBean : gcBeans) {
        IO.println("[Info] GC Bean 検出: " + gcBean.getName()
                + " (メモリプール: " + String.join(", ", gcBean.getMemoryPoolNames()) + ")");

        if (gcBean instanceof NotificationEmitter emitter) {
            NotificationListener listener = (Notification notification, Object handback) -> {
                if (notification.getType().equals(
                        GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                    var gcNotifInfo = GarbageCollectionNotificationInfo.from(
                            (CompositeData) notification.getUserData());
                    handleGcNotification(gcNotifInfo);
                }
            };
            emitter.addNotificationListener(listener, null, null);
        }
    }
    IO.println();
}

/**
 * 単一の GC 通知を処理し、フォーマットされた情報を表示する。
 * GC の種類（Minor/Major）を分類し、停止時間を累計カウンターに加算した上で、
 * GC 前後の全メモリプールの使用量合計を計算して表形式で出力する。
 *
 * @param info GC 通知情報（GC 名、アクション、原因、前後のメモリ使用量を含む）
 */
void handleGcNotification(GarbageCollectionNotificationInfo info) {
    var gcAction = info.getGcAction();
    var gcCause = info.getGcCause();
    var gcName = info.getGcName();
    GcInfo gcInfo = info.getGcInfo();

    long duration = gcInfo.getDuration();

    // Minor GC か Major GC かを分類（アクション名または GC 名に "minor"/"young" を含むかで判定）
    boolean isMinor = gcAction.contains("minor") || gcAction.contains("young")
            || gcName.toLowerCase().contains("young");

    if (isMinor) {
        minorGcCount.incrementAndGet();
        totalMinorPause.addAndGet(duration);
    } else {
        majorGcCount.incrementAndGet();
        totalMajorPause.addAndGet(duration);
    }

    int gcNum = minorGcCount.get() + majorGcCount.get();

    // 全メモリプールの GC 前後の使用量合計を計算
    long beforeTotal = gcInfo.getMemoryUsageBeforeGc().values().stream()
            .mapToLong(MemoryUsage::getUsed).sum();
    long afterTotal = gcInfo.getMemoryUsageAfterGc().values().stream()
            .mapToLong(MemoryUsage::getUsed).sum();

    var kind = (isMinor ? "Minor" : "Major") + " (" + gcName + ")";
    IO.println(String.format("%-6d %-28s %-10d %-22s %-22s",
            gcNum, kind, duration,
            formatMB(beforeTotal), formatMB(afterTotal)));

    // 一般的でない GC 原因の場合は追加情報を表示
    if (!gcCause.equals("G1 Evacuation Pause") && !gcCause.equals("Allocation Failure")) {
        IO.println("       原因: " + gcCause);
    }
}

/**
 * 3 フェーズでメモリ負荷を生成して各種 GC を誘発する。
 * <ul>
 *   <li>フェーズ 1: 短命オブジェクトを大量生成し Minor GC を誘発</li>
 *   <li>フェーズ 2: オブジェクトを保持して Old 世代を埋め、Major/Mixed GC を誘発</li>
 *   <li>フェーズ 3: 保持オブジェクトを解放して最終 GC を実行</li>
 * </ul>
 */
void generateMemoryPressure() {
    var retained = new ArrayList<byte[]>();

    // フェーズ 1: 短命オブジェクトの割り当てで Minor GC を誘発
    IO.println("\n[Phase 1] 短命オブジェクトを大量生成して Minor GC を誘発...\n");
    for (int i = 0; i < 50; i++) {
        for (int j = 0; j < 10; j++) {
            var temp = new byte[512 * 1024]; // 512KB、すぐに破棄される
        }
        sleep(20); // small delay so listener can print
    }

    // フェーズ 2: オブジェクトを保持して Old 世代を埋める（安全な範囲内にとどめる）
    IO.println("\n[Phase 2] オブジェクトを保持して Old Generation を埋める...\n");
    var runtime = Runtime.getRuntime();
    for (int i = 0; i < 60; i++) {
        // OOM 回避 -- 20% のヘッドルームを確保して停止
        long used = runtime.totalMemory() - runtime.freeMemory();
        if (used > runtime.maxMemory() * 0.75) {
            IO.println("  [Info] ヒープ使用率 75% 超過 -- Phase 2 を安全に停止");
            break;
        }
        try {
            retained.add(new byte[1024 * 1024]); // 1MB, retained
            // 短命のゴミオブジェクトも同時に生成
            for (int j = 0; j < 3; j++) {
                var temp = new byte[128 * 1024];
            }
        } catch (OutOfMemoryError e) {
            IO.println("  [!] OOM 接近 -- Phase 2 を安全に停止");
            break;
        }
        sleep(30);
    }

    // フェーズ 3: 保持オブジェクトを解放し GC を要求
    IO.println("\n[Phase 3] 保持オブジェクトを解放して最終 GC を実行...\n");
    retained.clear();
    System.gc();
    sleep(500);
}

/**
 * バイト数を人間が読みやすいメガバイト表記の文字列にフォーマットする。
 * 例: {@code 1048576} → {@code "1.0 MB"}
 *
 * @param bytes フォーマット対象のバイト数
 * @return "X.X MB" 形式の文字列
 */
String formatMB(long bytes) {
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
}

/**
 * 指定ミリ秒数だけスレッドをスリープさせる。
 * 割り込み（{@link InterruptedException}）が発生した場合は割り込みフラグを復元して復帰する。
 *
 * @param ms スリープするミリ秒数
 */
void sleep(long ms) {
    try {
        Thread.sleep(ms);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
