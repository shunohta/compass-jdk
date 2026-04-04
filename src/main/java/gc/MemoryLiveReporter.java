/**
 * メモリ消費・実況中継プログラム。
 *
 * <p>プログラムの実行中にリアルタイムでメモリの増減（ヒープ使用量、各プールの変化、
 * GC の発生タイミング等）を継続的に表示し、初学者が「今まさに何が起きているか」を
 * 追体験できるようにする。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>ヒープ使用量がオブジェクト生成に伴ってリアルタイムで増加する様子</li>
 *   <li>Eden Space / Old Gen など各メモリプールの使用量がどう変化するか</li>
 *   <li>GC が発生するタイミングと、発生前後でメモリがどれだけ回収されるか</li>
 *   <li>短命オブジェクトと長命オブジェクトで GC の挙動がどう異なるか</li>
 *   <li>ヒープの逼迫から OOM に至るまでの過程</li>
 * </ul>
 *
 * <h2>対象層</h2>
 * <ul>
 *   <li><strong>Java 層:</strong> {@link java.lang.management.MemoryPoolMXBean},
 *       {@link java.lang.management.GarbageCollectorMXBean} による監視 API</li>
 *   <li><strong>HotSpot C++ 層:</strong> {@code src/hotspot/share/gc/g1/g1CollectedHeap.cpp}
 *       で管理されるリージョンベースのヒープ。各 MXBean の値は HotSpot 内部カウンターから取得</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java -Xmx128m -Xms128m src/main/java/gc/MemoryLiveReporter.java
 * }</pre>
 *
 * @author jdk-core
 * @see java.lang.management.MemoryPoolMXBean
 * @see java.lang.management.GarbageCollectorMXBean
 * @see com.sun.management.GarbageCollectionNotificationInfo
 */

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;

// -- GC イベント追跡 --
static final AtomicInteger gcEventCount = new AtomicInteger(0);
static final AtomicLong lastGcRecoveredBytes = new AtomicLong(0);
static final AtomicReference<String> lastGcDescription = new AtomicReference<>("");

// -- 定数 --
static final int BAR_WIDTH = 30;
static final String ANSI_RESET = "";
static final long MB = 1024L * 1024L;

/**
 * メモリ消費のリアルタイム実況中継を開始する。
 *
 * <p>GC リスナーを登録した後、5 つのフェーズを順に実行して段階的にメモリ負荷を
 * 生成する。各フェーズの実行中、バックグラウンドのモニタースレッドが 300ms 間隔で
 * メモリ使用量をバーグラフ付きで継続表示する。GC が発生した瞬間にはアラートが割り込む。
 */
void main() {
    IO.println("========================================================================");
    IO.println("              メモリ消費・実況中継プログラム");
    IO.println("========================================================================");
    IO.println("");
    IO.println("  ヒープ最大: " + formatMB(Runtime.getRuntime().maxMemory()));
    IO.println("  GC: " + ManagementFactory.getGarbageCollectorMXBeans().stream()
            .map(gc -> gc.getName())
            .reduce((a, b) -> a + ", " + b)
            .orElse("不明"));
    IO.println("");
    IO.println("  これから段階的にメモリ負荷を生成し、");
    IO.println("  ヒープの変化と GC の発生をリアルタイムで実況します。");
    IO.println("========================================================================\n");

    // GC 通知リスナーを登録
    registerGcListener();

    // 監視対象の主要メモリプールを検索
    var pools = findKeyPools();

    // バックグラウンドモニタースレッドを起動
    var monitorRunning = new AtomicReference<>(Boolean.TRUE);
    var monitorThread = Thread.ofPlatform().daemon(true)
            .name("memory-live-monitor")
            .start(() -> monitorLoop(pools, monitorRunning));

    // 各フェーズを実行
    sleep(500); // let the initial status display
    phase1_shortLivedObjects();
    phase2_gradualAccumulation();
    phase3_pressureAndRelease();
    phase4_mixedWorkload();
    phase5_heavyPressure();

    // モニターを停止
    monitorRunning.set(Boolean.FALSE);
    try { monitorThread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

    // 最終サマリーを表示
    printFinalSummary(pools);
}


// ═══════════════════════════════════════════════════════════════
// GC 通知リスナー
// ═══════════════════════════════════════════════════════════════

/**
 * 全 GarbageCollectorMXBean に通知リスナーを登録して GC イベントをリアルタイム検知する。
 * GC が発生するたびにアラート行を出力し、回収量を記録する。
 */
void registerGcListener() {
    for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
        if (gcBean instanceof NotificationEmitter emitter) {
            emitter.addNotificationListener((Notification n, Object handback) -> {
                if (!n.getType().equals(
                        GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                    return;
                }
                var info = GarbageCollectionNotificationInfo.from(
                        (CompositeData) n.getUserData());
                handleGcEvent(info);
            }, null, null);
        }
    }
}

/**
 * 単一の GC イベントを処理して実況アラートを表示する。
 * GC の種類（Minor/Major）、停止時間、回収量を算出し、初学者向けの解説を添える。
 *
 * @param info GC 通知情報
 */
void handleGcEvent(GarbageCollectionNotificationInfo info) {
    int eventNum = gcEventCount.incrementAndGet();
    GcInfo gcInfo = info.getGcInfo();
    long duration = gcInfo.getDuration();

    // GC 前後のメモリ合計を算出
    long beforeTotal = gcInfo.getMemoryUsageBeforeGc().values().stream()
            .mapToLong(MemoryUsage::getUsed).sum();
    long afterTotal = gcInfo.getMemoryUsageAfterGc().values().stream()
            .mapToLong(MemoryUsage::getUsed).sum();
    long recovered = beforeTotal - afterTotal;

    lastGcRecoveredBytes.set(recovered);

    // GC の種類を分類
    var action = info.getGcAction();
    var gcName = info.getGcName();
    boolean isMinor = action.contains("minor") || action.contains("young")
            || gcName.toLowerCase().contains("young");
    var typeLabel = isMinor ? "Minor GC" : "Major GC";

    // 説明文を構築
    var desc = String.format("%s (%s) -- 停止 %dms, 回収 %s",
            typeLabel, gcName, duration, formatMB(Math.max(recovered, 0)));
    lastGcDescription.set(desc);

    // GC アラートを枠付きで表示
    IO.println("");
    IO.println("  +---[ GC 発生! #" + eventNum + " ]" + "-".repeat(50) + "+");
    IO.println("  |  " + desc);

    // GC の種類に応じた学習用コメントを出力
    var commentary = switch (typeLabel) {
        case "Minor GC" -> "  |  --> Eden が満杯になり、若い世代の回収が走った";
        case "Major GC" -> "  |  --> Old Gen の圧迫により、全世代を対象とした回収が走った";
        default -> "  |  --> GC が実行された";
    };
    IO.println(commentary);
    IO.println("  +" + "-".repeat(65) + "+");
    IO.println("");
}


// ═══════════════════════════════════════════════════════════════
// バックグラウンドモニタースレッド
// ═══════════════════════════════════════════════════════════════

/**
 * 主要なメモリプール（Eden, Old Gen 等）を検索して返す。
 * G1GC の場合は "G1 Eden Space", "G1 Old Gen", "G1 Survivor Space" が該当する。
 *
 * @return 監視対象のメモリプール一覧
 */
List<MemoryPoolMXBean> findKeyPools() {
    var result = new ArrayList<MemoryPoolMXBean>();
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
        String name = pool.getName();
        if (name.contains("Eden") || name.contains("Old") || name.contains("Survivor")) {
            result.add(pool);
        }
    }
    return result;
}

/**
 * バックグラウンドで 300ms 間隔でメモリ使用量を表示し続けるモニターループ。
 * ヒープ全体と各主要プールの使用量をバーグラフで表示する。
 *
 * @param pools   監視対象のメモリプール
 * @param running false にするとループが終了する
 */
void monitorLoop(List<MemoryPoolMXBean> pools, AtomicReference<Boolean> running) {
    int tick = 0;
    while (running.get()) {
        printLiveStatus(pools, tick);
        tick++;
        sleep(300);
    }
}

/**
 * 1回分のライブステータスを表示する。
 * ヒープ全体のバーグラフ + 各プールの使用量を1行ずつ表示する。
 *
 * @param pools 監視対象プール
 * @param tick  表示回数（経過時間の目安表示に使用）
 */
void printLiveStatus(List<MemoryPoolMXBean> pools, int tick) {
    var runtime = Runtime.getRuntime();
    long used = runtime.totalMemory() - runtime.freeMemory();
    long max = runtime.maxMemory();
    int pct = (max > 0) ? (int) (used * 100 / max) : 0;

    // 経過時間の概算
    double elapsedSec = tick * 0.3;

    // ヒープ全体のバーグラフ
    var bar = renderBar(pct, BAR_WIDTH);
    var urgency = (pct >= 90) ? " [危険!!]"
               : (pct >= 70) ? " [注意]"
               : (pct >= 50) ? " [増加中]"
               :               " [余裕]";

    IO.println(String.format("  [%5.1fs] ヒープ全体: %s %3d%% (%s / %s)%s",
            elapsedSec, bar, pct, formatMB(used), formatMB(max), urgency));

    // 各プールのバーグラフ（1行ずつコンパクトに表示）
    for (MemoryPoolMXBean pool : pools) {
        MemoryUsage usage = pool.getUsage();
        long poolUsed = usage.getUsed();
        long poolMax = usage.getMax();
        // max が -1（無制限）のプールがある場合は committed をフォールバックに使用
        long poolCap = (poolMax > 0) ? poolMax : usage.getCommitted();
        int poolPct = (poolCap > 0) ? (int) (poolUsed * 100 / poolCap) : 0;
        var poolBar = renderBar(poolPct, 15);
        var shortName = shortenPoolName(pool.getName());

        IO.println(String.format("           %-12s %s %3d%% (%s)",
                shortName, poolBar, poolPct, formatMB(poolUsed)));
    }
}


// ═══════════════════════════════════════════════════════════════
// フェーズ実装
// ═══════════════════════════════════════════════════════════════

/**
 * フェーズ1: 短命オブジェクトを大量生成する。
 *
 * <p>Eden に大量のオブジェクトを生成するが、すぐに参照を手放す。
 * GC（特に Minor GC）が頻繁に発生し、効率的に回収される様子を観察できる。
 * ヒープ使用量は増減を繰り返すが、全体としては安定する。
 */
void phase1_shortLivedObjects() {
    printPhaseHeader(1, "短命オブジェクトの大量生成",
            "Eden にオブジェクトを作っては捨てる。GC が頻繁に走り、",
            "ヒープ使用量が増減を繰り返す様子を観察しよう。");

    for (int i = 0; i < 40; i++) {
        // 短命ゴミを生成: 512KB * 5 = 1回あたり約 2.5MB
        for (int j = 0; j < 5; j++) {
            var temp = new byte[512 * 1024];
        }
        sleep(100);
    }
    printPhaseFooter(1,
            "短命オブジェクトは Eden で生まれ、Eden で回収された。",
            "Minor GC は高速（数ミリ秒）で、ヒープ全体はほぼ安定していた。");
}

/**
 * フェーズ2: オブジェクトを徐々に蓄積する。
 *
 * <p>byte[] を ArrayList に保持し続けるため、GC が回収できない。
 * ヒープ使用量が右肩上がりに増加していく。オブジェクトが Survivor を経由して
 * Old Gen に昇格していく過程を観察できる。
 *
 */
void phase2_gradualAccumulation() {
    printPhaseHeader(2, "オブジェクトの蓄積 -- ヒープが埋まっていく",
            "オブジェクトをリストに保持し、GC に回収させない。",
            "ヒープ使用量が一方的に増加し、Old Gen が膨らむ様子を見よう。");

    var retained = new ArrayList<byte[]>();
    var runtime = Runtime.getRuntime();

    for (int i = 0; i < 40; i++) {
        // OOM 回避: 40% のヘッドルームを確保して停止
        long used = runtime.totalMemory() - runtime.freeMemory();
        if (used > runtime.maxMemory() * 0.55) {
            IO.println("\n  >> ヒープ使用率 55% 超過 -- フェーズ2を安全に停止\n");
            break;
        }
        try {
            retained.add(new byte[1024 * 1024]); // 1MB, retained
        } catch (OutOfMemoryError e) {
            IO.println("\n  >> OOM 接近 -- フェーズ2を安全に停止\n");
            break;
        }
        sleep(150);
    }

    // フェーズ終了時に保持オブジェクトを解放し、後続フェーズに影響させない
    retained.clear();
    System.gc();
    sleep(500);

    printPhaseFooter(2,
            "リストが参照を保持しているため、GC は 1 つも回収できなかった。",
            "Old Gen の使用量が着実に増加していることに注目。");
}

/**
 * フェーズ3: 蓄積したオブジェクトを解放して GC で回収させる。
 *
 * <p>フェーズ2で蓄積したオブジェクトの参照を一斉に切断し、System.gc() を呼ぶ。
 * Major GC が走り、Old Gen が一気に解放される劇的な瞬間を観察できる。
 */
void phase3_pressureAndRelease() {
    // フェーズ2の保持オブジェクトに加えてさらに蓄積し、ここで一斉に解放する
    printPhaseHeader(3, "蓄積 & 一斉解放 -- GC の大回収を目撃する",
            "フェーズ2で溜まったオブジェクトに加えてさらに蓄積し、",
            "参照を一斉に切断する。GC が一気に回収する瞬間を見届けよう。");

    var retained = new ArrayList<byte[]>();
    var runtime = Runtime.getRuntime();

    // オブジェクトを蓄積
    IO.println("\n  >> オブジェクトを蓄積中...\n");
    for (int i = 0; i < 30; i++) {
        long used = runtime.totalMemory() - runtime.freeMemory();
        if (used > runtime.maxMemory() * 0.65) {
            break;
        }
        try {
            retained.add(new byte[1024 * 1024]);
        } catch (OutOfMemoryError e) {
            break;
        }
        sleep(100);
    }

    IO.println("\n  >> 参照を一斉に切断! retained.clear() を実行\n");
    sleep(500);

    retained.clear();

    IO.println("  >> System.gc() を呼び出して GC を要求\n");
    System.gc();
    sleep(1000);

    printPhaseFooter(3,
            "参照を切断した瞬間、全オブジェクトが GC 回収対象になった。",
            "ヒープ使用量が劇的に減少したのが見えたはず。これが GC の力!");
}

/**
 * フェーズ4: 短命と長命が混在するワークロードをシミュレートする。
 *
 * <p>一部を保持し、一部を捨てるという現実的なアプリケーションパターンを再現する。
 * Minor GC と Mixed GC が混在して発生し、ヒープ使用量が波状に変動する。
 */
void phase4_mixedWorkload() {
    printPhaseHeader(4, "混合ワークロード -- 現実のアプリを模倣",
            "短命オブジェクトと長命オブジェクトを同時に生成する。",
            "実際の Web アプリケーションに近いパターンで GC の動きを観察。");

    var cache = new ArrayList<byte[]>();
    var runtime = Runtime.getRuntime();

    for (int i = 0; i < 50; i++) {
        // 短命: リクエスト処理のシミュレーション
        for (int j = 0; j < 3; j++) {
            var request = new byte[256 * 1024]; // 256KB "request data"
        }

        // 長命: キャッシュエントリ（5回に1回保持）
        if (i % 5 == 0) {
            long used = runtime.totalMemory() - runtime.freeMemory();
            if (used < runtime.maxMemory() * 0.6) {
                try {
                    cache.add(new byte[512 * 1024]); // 512KB "cache entry"
                } catch (OutOfMemoryError e) {
                    // 無視
                }
            }
        }

        // 定期的に最古のキャッシュエントリをエビクト
        if (i % 15 == 0 && !cache.isEmpty()) {
            cache.removeFirst();
            IO.println("  >> キャッシュエントリをエビクト (LRU 風)");
        }

        sleep(80);
    }

    cache.clear();
    System.gc();
    sleep(500);

    printPhaseFooter(4,
            "短命の「リクエスト」はすぐ回収され、長命の「キャッシュ」は残った。",
            "定期的なキャッシュエビクションが Old Gen の肥大化を防いだ。");
}

/**
 * フェーズ5: ヒープに高負荷を掛け、GC が懸命に回収する様子を観察する。
 *
 * <p>大量のオブジェクトを高速で生成し、GC が追いつくかどうかのギリギリのラインを探る。
 * GC オーバーヘッドの増大やヒープ逼迫のアラートが表示される。
 */
void phase5_heavyPressure() {
    printPhaseHeader(5, "高負荷テスト -- GC の限界を探る",
            "大量のオブジェクトを高速生成して GC に負荷を掛ける。",
            "GC が追いつけるか? ヒープ逼迫のアラートに注目しよう。");

    var runtime = Runtime.getRuntime();

    for (int i = 0; i < 60; i++) {
        // 高負荷割り当て: 大量の短命ゴミを生成
        for (int j = 0; j < 10; j++) {
            var heavy = new byte[512 * 1024]; // 512KB * 10 = 5MB per iteration
        }

        // 定期的な状況実況
        if (i % 20 == 0 && i > 0) {
            long used = runtime.totalMemory() - runtime.freeMemory();
            int pct = (int) (used * 100 / runtime.maxMemory());
            var comment = (pct >= 80) ? "GC が必死に回収している... ギリギリの攻防!"
                        : (pct >= 60) ? "ヒープが逼迫気味。GC の頻度が上がっている。"
                        :               "GC が余裕を持って回収できている。";
            IO.println("  >> [実況] " + comment);
        }

        sleep(50);
    }

    System.gc();
    sleep(500);

    printPhaseFooter(5,
            "高速なオブジェクト生成でも、短命なら GC が追いつく。",
            "GC のオーバーヘッドが増大すると、アプリの処理時間が削られる。");
}


// ═══════════════════════════════════════════════════════════════
// 最終サマリー
// ═══════════════════════════════════════════════════════════════

/**
 * 全フェーズ完了後の最終サマリーを表示する。
 * GC の合計回数・停止時間、ヒープ最終状態、学びのまとめを出力する。
 *
 * @param pools 監視していたメモリプール
 */
void printFinalSummary(List<MemoryPoolMXBean> pools) {
    IO.println("\n" + "=".repeat(72));
    IO.println("                     最終レポート");
    IO.println("=".repeat(72));

    // GC 統計
    long totalGcCount = 0;
    long totalGcTime = 0;
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
        long count = gc.getCollectionCount();
        long time = gc.getCollectionTime();
        totalGcCount += count;
        totalGcTime += time;

        IO.println(String.format("  %-30s  回数: %d, 停止時間: %d ms",
                gc.getName(), count, time));
    }

    long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
    double overhead = (uptimeMs > 0) ? (double) totalGcTime / uptimeMs * 100 : 0;

    IO.println("");
    IO.println("  合計 GC 回数:     " + totalGcCount + " 回");
    IO.println("  合計 GC 停止時間: " + totalGcTime + " ms");
    IO.println("  JVM 稼働時間:     " + uptimeMs + " ms");
    IO.println("  GC オーバーヘッド: " + String.format("%.2f%%", overhead));

    // ヒープの最終状態
    var runtime = Runtime.getRuntime();
    long used = runtime.totalMemory() - runtime.freeMemory();
    long max = runtime.maxMemory();
    int pct = (max > 0) ? (int) (used * 100 / max) : 0;

    IO.println("");
    IO.println("  最終ヒープ: " + renderBar(pct, BAR_WIDTH) + " " + pct + "% ("
            + formatMB(used) + " / " + formatMB(max) + ")");

    // 各プールの最終状態
    for (MemoryPoolMXBean pool : pools) {
        MemoryUsage usage = pool.getUsage();
        IO.println("  " + pool.getName() + ": " + formatMB(usage.getUsed()));
    }

    // 学びのまとめ
    IO.println("");
    IO.println("  +-" + "-".repeat(64) + "-+");
    IO.println("  |  今日の学び                                                     |");
    IO.println("  +-" + "-".repeat(64) + "-+");
    IO.println("  |  1. 短命オブジェクトは Eden で生まれ Eden で回収される (高速)    |");
    IO.println("  |  2. 参照を保持し続けると GC は回収できない                       |");
    IO.println("  |  3. 参照を切断すれば、次の GC で回収される                       |");
    IO.println("  |  4. Minor GC は高速、Major GC は重い                             |");
    IO.println("  |  5. GC オーバーヘッドが高い = ヒープ不足の兆候                   |");
    IO.println("  +-" + "-".repeat(64) + "-+");
    IO.println("");
    IO.println("=".repeat(72));
    IO.println("               実況中継おわり -- お疲れさまでした!");
    IO.println("=".repeat(72));
}


// ═══════════════════════════════════════════════════════════════
// ヘルパーメソッド
// ═══════════════════════════════════════════════════════════════

/**
 * パーセンテージをバーグラフ文字列に変換する。
 * 使用率に応じてバーの塗りつぶし文字が変化する:
 * 80% 以上は '#'、50% 以上は '='、それ以下は '-'。
 *
 * @param percent 使用率 (0-100)
 * @param width   バーの幅（文字数）
 * @return バーグラフ文字列（例: "[======----          ]"）
 */
String renderBar(int percent, int width) {
    int clamped = Math.clamp(percent, 0, 100);
    int filled = clamped * width / 100;

    // 逼迫度に応じて塗りつぶし文字を変える
    char fillChar = (clamped >= 80) ? '#'
                  : (clamped >= 50) ? '='
                  :                   '-';

    var sb = new StringBuilder(width + 2);
    sb.append('[');
    for (int i = 0; i < width; i++) {
        sb.append(i < filled ? fillChar : ' ');
    }
    sb.append(']');
    return sb.toString();
}

/**
 * バイト数を "X.X MB" 形式にフォーマットする。
 *
 * @param bytes バイト数
 * @return フォーマット済み文字列
 */
String formatMB(long bytes) {
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
}

/**
 * メモリプール名を表示用に短縮する。
 * 例: "G1 Eden Space" -> "Eden"、"G1 Old Gen" -> "Old Gen"
 *
 * @param name メモリプール名
 * @return 短縮名
 */
String shortenPoolName(String name) {
    return switch (name) {
        case String s when s.contains("Eden") -> "Eden";
        case String s when s.contains("Old") -> "Old Gen";
        case String s when s.contains("Survivor") -> "Survivor";
        default -> name.length() > 12 ? name.substring(0, 12) : name;
    };
}

/**
 * フェーズ開始のヘッダーを表示する。
 *
 * @param num   フェーズ番号
 * @param title フェーズのタイトル
 * @param desc1 説明文1行目
 * @param desc2 説明文2行目
 */
void printPhaseHeader(int num, String title, String desc1, String desc2) {
    IO.println("");
    IO.println("  " + "*".repeat(68));
    IO.println("  *  フェーズ " + num + ": " + title);
    IO.println("  *");
    IO.println("  *  " + desc1);
    IO.println("  *  " + desc2);
    IO.println("  " + "*".repeat(68));
    IO.println("");
}

/**
 * フェーズ完了後のフッターを表示する。
 *
 * @param num    フェーズ番号
 * @param lesson 学びのポイント1行目
 * @param detail 学びのポイント2行目
 */
void printPhaseFooter(int num, String lesson, String detail) {
    IO.println("");
    IO.println("  --- フェーズ " + num + " 完了 ---");
    IO.println("  [学び] " + lesson);
    IO.println("         " + detail);
    IO.println("");
    sleep(1000);
}

/**
 * 指定ミリ秒だけスリープする。割り込み時は割り込みフラグを復元して復帰する。
 *
 * @param ms スリープ時間（ミリ秒）
 */
void sleep(long ms) {
    try {
        Thread.sleep(ms);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
