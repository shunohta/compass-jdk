/**
 * JVM メモリ診断ユーティリティ。
 *
 * <p>JVM が管理するメモリプール（Eden、Survivor、Old Gen 等）、
 * ガベージコレクターの状態、スレッド情報を一覧表示する。
 * Spring Boot の Actuator が内部で使っている仕組みと同じ
 * {@link java.lang.management.ManagementFactory} API を直接使用する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>JVM のヒープは複数のメモリプール（Eden, Survivor, Old Gen 等）に分かれている</li>
 *   <li>{@link java.lang.management.MemoryMXBean} でヒープ全体の使用状況を取得できる</li>
 *   <li>{@link java.lang.management.MemoryPoolMXBean} で各プールの詳細を確認できる</li>
 *   <li>{@link java.lang.management.GarbageCollectorMXBean} でGCの実行回数と所要時間がわかる</li>
 *   <li>{@link java.lang.management.ThreadMXBean} でスレッド数を監視できる</li>
 * </ul>
 *
 * <h2>メモリプールとは</h2>
 * <p>HotSpot VM はヒープを世代別に管理する（Generational GC）。
 * 典型的な G1GC の場合、以下のプールが存在する:
 * <pre>{@code
 * ヒープ
 * ├── Young Generation（若い世代）
 * │   ├── Eden Space     -- 新規オブジェクトが最初に配置される場所
 * │   ├── Survivor Space -- Minor GC を生き延びたオブジェクトの一時待機場所
 * │   └── (Survivor は S0, S1 の 2 つがある)
 * └── Old Generation（老年世代）
 *     └── Old Gen        -- 長寿命オブジェクト（Spring Bean, キャッシュ等）
 * }</pre>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java src/main/java/MemoryDiagnostics.java
 * }</pre>
 *
 * @author jdk-core
 * @see java.lang.management.ManagementFactory
 * @see java.lang.management.MemoryMXBean
 * @see java.lang.management.MemoryPoolMXBean
 * @see java.lang.management.GarbageCollectorMXBean
 * @see java.lang.management.ThreadMXBean
 */

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * プログラムのエントリーポイント。
 *
 * <p>ヒープ情報、メモリプール、GC 状態、スレッド情報を順に出力する。
 * 出力結果を読むことで、JVM 内部のメモリ構造を実感できる。
 */
void main() {
    IO.println("╔══════════════════════════════════════════════════════════════╗");
    IO.println("║              JVM メモリ診断ユーティリティ                    ║");
    IO.println("╚══════════════════════════════════════════════════════════════╝\n");

    var runtime = Runtime.getRuntime();

    // ══════════════════════════════════════════════
    // 1. ヒープ全体のサマリー
    // ══════════════════════════════════════════════
    var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    long heapUsed = heap.getUsed();
    long heapMax = heap.getMax();
    int heapPercent = (heapMax > 0) ? (int) (heapUsed * 100 / heapMax) : 0;

    IO.println("[1. ヒープ全体]");
    IO.println("");
    IO.println("  初期値(Init):   " + mb(heap.getInit())
        + "  ← JVM 起動時に OS へ要求した初期サイズ (-Xms)");
    IO.println("  使用量(Used):   " + mb(heapUsed)
        + "  ← 現在オブジェクトが実際に使用している量");
    IO.println("  確保量(Commit): " + mb(heap.getCommitted())
        + "  ← JVM が OS から確保済みの量 (Used <= Commit <= Max)");
    IO.println("  最大値(Max):    " + mb(heapMax)
        + "  ← ヒープの上限 (-Xmx)");
    IO.println("");

    // ──────────────────────────────────────────────
    // ヒープ使用率のバーグラフと健全性判定
    // ──────────────────────────────────────────────
    IO.println("  使用率: " + renderBar(heapPercent) + " " + heapPercent + "%");
    IO.println("  判定:   " + heapHealthCheck(heapPercent));
    IO.println("");
    IO.println("  [各指標の読み方]");
    IO.println("  ・Init と Max が同じ → -Xms = -Xmx に設定されている（推奨設定）。");
    IO.println("    起動時にヒープ拡張の GC が発生しない。");
    IO.println("  ・Used が Max の 70% を常に超える → ヒープ不足の兆候。");
    IO.println("    -Xmx の増加かメモリリークの調査が必要。");
    IO.println("  ・Committed が Max より大幅に小さい → JVM がまだヒープを");
    IO.println("    フルに確保していない。負荷が上がれば拡張される。");

    // ══════════════════════════════════════════════
    // 2. 各メモリプールの詳細
    // ══════════════════════════════════════════════
    IO.println("\n[2. メモリプール詳細]");
    IO.println("");
    IO.println(String.format("  %-30s %10s %10s %6s  %s",
        "プール名", "使用量", "最大値", "使用率", "役割"));
    IO.println("  " + "-".repeat(90));

    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
        var usage = pool.getUsage();
        long used = usage.getUsed();
        long max = usage.getMax();
        String pct = (max > 0) ? ((int) (used * 100 / max)) + "%" : "-";
        String role = describePool(pool.getName());

        IO.println(String.format("  %-30s %10s %10s %6s  %s",
            pool.getName(), mb(used), mb(max), pct, role));
    }

    IO.println("");
    IO.println("  [各プールの読み方]");
    IO.println("  ・G1 Eden Space の使用率が高い → 新規オブジェクト生成が活発。正常。");
    IO.println("    Eden が満杯になると Minor GC が走る。");
    IO.println("  ・G1 Old Gen の使用率が 70% を超え続ける → メモリリークの疑い。");
    IO.println("    Old Gen はフル GC でしか大規模回収されないため、肥大化は危険信号。");
    IO.println("  ・Metaspace が継続的に増加 → クラスリークの疑い（動的プロキシの");
    IO.println("    過剰生成、ClassLoader リーク等）。-XX:MaxMetaspaceSize で上限を設定推奨。");
    IO.println("  ・CodeHeap が上限に近づく → JIT コンパイルが停止しパフォーマンス劣化。");
    IO.println("    -XX:ReservedCodeCacheSize で拡大可能。");

    // ══════════════════════════════════════════════
    // 3. ガベージコレクター情報
    // ══════════════════════════════════════════════
    IO.println("\n[3. ガベージコレクター]");
    IO.println("");

    long totalGcCount = 0;
    long totalGcTime = 0;

    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
        long count = gc.getCollectionCount();
        long time = gc.getCollectionTime();
        totalGcCount += count;
        totalGcTime += time;

        // 1回あたりの平均停止時間を計算
        String avgPause = (count > 0)
            ? String.format("%.1f ms", (double) time / count)
            : "-";

        String role = describeGc(gc.getName());

        IO.println("  " + gc.getName());
        IO.println("    実行回数:     " + count + " 回");
        IO.println("    累計停止時間: " + time + " ms");
        IO.println("    平均停止時間: " + avgPause + " /回");
        IO.println("    役割:         " + role);
        IO.println("");
    }

    // ──────────────────────────────────────────────
    // GC 全体のサマリーと健全性判定
    // ──────────────────────────────────────────────
    // JVM の起動からの経過時間（ミリ秒）
    long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
    double gcOverheadPct = (uptimeMs > 0)
        ? (double) totalGcTime / uptimeMs * 100
        : 0;

    IO.println("  [GC 全体サマリー]");
    IO.println("    合計 GC 回数:     " + totalGcCount + " 回");
    IO.println("    合計 GC 停止時間: " + totalGcTime + " ms");
    IO.println("    JVM 稼働時間:     " + uptimeMs + " ms");
    IO.println("    GC オーバーヘッド: " + String.format("%.2f%%", gcOverheadPct)
        + " (= GC 停止時間 / JVM 稼働時間)");
    IO.println("");
    IO.println("  [異常の判断基準]");
    IO.println("    " + gcHealthCheck(totalGcCount, totalGcTime, uptimeMs));
    IO.println("");
    IO.println("  [GC の読み方ガイド]");
    IO.println("  ・GC オーバーヘッドが 5% 以上 → GC に時間を取られすぎ。");
    IO.println("    ヒープ不足かメモリリークを疑う。98% を超えると OOM が発生する。");
    IO.println("  ・Old Generation の GC（Full GC）が頻発 → 深刻な兆候。");
    IO.println("    長寿命オブジェクトが多すぎるか、ヒープサイズが不足している。");
    IO.println("  ・平均停止時間が 200ms を超える → レスポンスタイムに影響。");
    IO.println("    ZGC への切り替えや -XX:MaxGCPauseMillis の調整を検討。");
    IO.println("  ・GC 回数が 0 → 起動直後なら正常。長時間 0 のままなら");
    IO.println("    ヒープが大きすぎる（メモリの無駄）か、オブジェクト生成が少ない。");

    // ──────────────────────────────────────────────
    // 4. スレッド情報（サマリー）
    // 各スレッドは専用のスタック領域を持つため、
    // スレッド数 x -Xss がスタックの総消費量になる。
    // ──────────────────────────────────────────────
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    int totalThreads = threadBean.getThreadCount();
    int daemonCount = threadBean.getDaemonThreadCount();
    int nonDaemonCount = totalThreads - daemonCount;

    IO.println("\n[スレッド サマリー]");
    IO.println("  現在のスレッド数: " + totalThreads
        + " (デーモン: " + daemonCount + ", 非デーモン: " + nonDaemonCount + ")");
    IO.println("  ピークスレッド数: " + threadBean.getPeakThreadCount());
    IO.println("  推定スタック総消費: " + totalThreads + " スレッド × -Xss (デフォルト ~1MB)"
        + " ≈ " + totalThreads + " MB");

    // ──────────────────────────────────────────────
    // 5. 全スレッドの詳細一覧
    //
    // ThreadMXBean.getThreadInfo() で各スレッドの情報を取得する。
    // スレッド名、デーモンかどうか、状態 (RUNNABLE 等)、
    // そしてそのスレッドが何をしているかの解説を表示する。
    // ──────────────────────────────────────────────
    IO.println("\n[スレッド 詳細一覧]");
    IO.println(String.format("  %-4s %-30s %-8s %-15s %s",
        "ID", "名前", "デーモン", "状態", "役割"));
    IO.println("  " + "-".repeat(95));

    long[] threadIds = threadBean.getAllThreadIds();
    ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds);

    for (ThreadInfo info : threadInfos) {
        if (info == null) continue;

        String name = info.getThreadName();
        boolean daemon = info.isDaemon();
        String state = info.getThreadState().toString();
        String role = describeThread(name);

        IO.println(String.format("  %-4d %-30s %-8s %-15s %s",
            info.getThreadId(),
            truncate(name, 30),
            daemon ? "はい" : "いいえ",
            state,
            role));
    }

    // ──────────────────────────────────────────────
    // 6. デーモンスレッドと非デーモンスレッドの解説
    // ──────────────────────────────────────────────
    IO.println("\n[デーモンスレッドとは？]");
    IO.println("  デーモンスレッド = JVM のバックグラウンドで働く裏方スレッド。");
    IO.println("  非デーモンスレッドがすべて終了すると、デーモンスレッドが");
    IO.println("  残っていても JVM は終了する。GC スレッドや JIT コンパイラ");
    IO.println("  スレッドは典型的なデーモンスレッドである。");
    IO.println("");
    IO.println("  非デーモンスレッド = アプリケーションの主要なスレッド。");
    IO.println("  main スレッドはデフォルトで非デーモン。Spring Boot では");
    IO.println("  Tomcat のリクエスト処理スレッドも非デーモンである。");

    IO.println("\n[ヒント] Spring Boot では application.properties に以下を追加:");
    IO.println("  management.endpoints.web.exposure.include=health,metrics");
    IO.println("  → /actuator/metrics/jvm.memory.used でランタイム監視が可能。");
}

/**
 * バイト数を MB 単位の文字列に変換するヘルパーメソッド。
 *
 * <p>負の値（{@code -1} など、最大値が未設定の場合）は "N/A" を返す。
 *
 * @param bytes バイト数
 * @return "N/A" または "数値 MB" の形式の文字列
 */
String mb(long bytes) {
    return (bytes < 0) ? "N/A" : (bytes / 1024 / 1024) + " MB";
}

/**
 * パーセンテージを視覚的なバーグラフに変換する。
 *
 * <p>20文字幅のバーで使用率を視覚的に表示する。
 * 例: 45% → {@code [=========           ]}
 *
 * @param percent 0〜100 のパーセンテージ
 * @return バーグラフの文字列
 */
String renderBar(int percent) {
    int barWidth = 20;
    int filled = percent * barWidth / 100;
    if (filled > barWidth) filled = barWidth;
    return "[" + "=".repeat(filled) + " ".repeat(barWidth - filled) + "]";
}

/**
 * ヒープ使用率に基づく健全性メッセージを返す。
 *
 * <p>初心者が「この数字は良いのか悪いのか」をすぐ判断できるように、
 * 使用率に応じた色分け的なメッセージを提供する。
 *
 * @param percent ヒープ使用率 (0〜100)
 * @return 健全性の判定メッセージ
 */
String heapHealthCheck(int percent) {
    if (percent < 30)      return "[ 良好 ] ヒープに十分な余裕がある。";
    else if (percent < 50) return "[ 正常 ] 一般的な使用範囲内。";
    else if (percent < 70) return "[ 注意 ] 使用率がやや高い。負荷テスト時にピークを確認すべき。";
    else if (percent < 85) return "[ 警告 ] ヒープ不足の兆候。-Xmx の増加またはメモリリークの調査を推奨。";
    else                   return "[ 危険 ] OOM 寸前。即座に -Xmx の増加とヒープダンプ解析を実施すべき。";
}

/**
 * メモリプール名から、そのプールの役割の日本語解説を返す。
 *
 * <p>G1GC のプール名を基準にしているが、他の GC アルゴリズム
 * （ZGC、Shenandoah 等）の場合はプール名が異なる。
 *
 * @param name メモリプール名
 * @return そのプールの役割の日本語解説
 */
String describePool(String name) {
    return switch (name) {
        // Eden: new で生成されたオブジェクトが最初に配置。大半は短命で Minor GC で回収
        case String s when s.contains("Eden") ->
            "新規オブジェクトの生成場所 (ここで大半が短命に終わる)";
        // Survivor: Minor GC を生き延びたオブジェクトの一時避難所。S0/S1 間を交互コピー
        case String s when s.contains("Survivor") ->
            "Minor GC を生き延びたオブジェクトの一時避難所";
        // Old Gen: 長寿命オブジェクト（Spring Bean, キャッシュ等）の本拠地
        case String s when s.contains("Old Gen") || s.contains("Tenured") ->
            "長寿命オブジェクトの本拠地 (Spring Bean, キャッシュ等)";
        // Metaspace: クラスのメタデータ（クラス定義, メソッド定義, 定数プール）
        case String s when s.contains("Metaspace") ->
            "クラスのメタデータ格納 (クラス定義, メソッド定義等)";
        // Compressed Class Space: 64bit VM の Klass ポインタ圧縮用
        case String s when s.contains("Compressed Class") ->
            "圧縮クラスポインタ用 (64bit VM の最適化)";
        // CodeHeap (non-nmethods): JVM 内部スタブコードやアダプター
        case String s when s.contains("non-nmethods") ->
            "JVM 内部スタブコード (JIT とは無関係の基盤部分)";
        // CodeHeap (non-profiled nmethods): C2 最適化済みコード ※ "profiled" より先に判定
        case String s when s.contains("non-profiled nmethods") ->
            "C2 JIT コンパイル済みコード (最適化済み、本番実行用)";
        // CodeHeap (profiled nmethods): C1 プロファイリング付きコード
        case String s when s.contains("profiled nmethods") ->
            "C1 JIT コンパイル済みコード (プロファイル付き、後に C2 に置換)";
        // Code Cache: Java 8 以前の単一コードキャッシュ名
        case String s when s.contains("Code Cache") || s.contains("CodeCache") ->
            "JIT コンパイル済みコードの格納領域";
        default -> "(その他のメモリプール)";
    };
}

/**
 * GC コレクター名から、その GC の役割の日本語解説を返す。
 *
 * @param name GC コレクター名
 * @return その GC の役割の日本語解説
 */
String describeGc(String name) {
    return switch (name) {
        // G1 Young Generation: Eden + Survivor の Minor GC。頻繁だが短時間
        case String s when s.contains("Young") ->
            "Minor GC（若い世代の回収）。頻繁だが高速。";
        // G1 Concurrent GC: アプリと並行して到達可能性を解析（STW 最小化）
        case String s when s.contains("Concurrent") ->
            "並行マーキング。アプリと並行して到達可能性を解析。";
        // G1 Old Generation: 他の GC で回収しきれない場合の最後の手段
        case String s when s.contains("Old") ->
            "Full GC（老年世代の回収）。頻発は危険信号！";
        case String s when s.contains("ZGC") ->
            "ZGC の低レイテンシ回収。停止時間 1ms 未満が目標。";
        case String s when s.contains("Shenandoah") ->
            "Shenandoah の並行コンパクション GC。";
        case String s when s.contains("Serial") || s.contains("Copy") ->
            "Serial GC（単一スレッドで回収）。";
        case String s when s.contains("Parallel") || s.contains("PS") ->
            "Parallel GC（複数スレッドで回収）。スループット重視。";
        default -> "(GC アルゴリズム固有のコレクター)";
    };
}

/**
 * GC の全体統計に基づく健全性メッセージを返す。
 *
 * @param totalCount 合計 GC 回数
 * @param totalTime  合計 GC 停止時間 (ms)
 * @param uptimeMs   JVM 稼働時間 (ms)
 * @return 健全性の判定メッセージ
 */
String gcHealthCheck(long totalCount, long totalTime, long uptimeMs) {
    if (uptimeMs <= 0 || totalCount == 0) {
        return "[ 情報 ] GC が未実行。起動直後なら正常。";
    }

    // 複数の指標を同時に判定するため、ローカルレコードとレコードパターンを活用
    record Stats(double overhead, double avgPause) {}

    var stats = new Stats(
        (double) totalTime / uptimeMs * 100,
        (double) totalTime / totalCount);

    return switch (stats) {
        case Stats(var o, _) when o > 10 ->
            "[ 危険 ] GC オーバーヘッドが " + String.format("%.1f%%", o)
                + "。ヒープ不足またはメモリリークの可能性が高い。";
        case Stats(var o, _) when o > 5 ->
            "[ 警告 ] GC オーバーヘッドが " + String.format("%.1f%%", o)
                + "。ヒープサイズの見直しを推奨。";
        case Stats(_, var p) when p > 200 ->
            "[ 注意 ] 平均停止時間が " + String.format("%.0f ms", p)
                + "。レスポンスに影響する可能性。ZGC の検討を推奨。";
        case Stats(var o, _) ->
            "[ 良好 ] GC は正常に動作している（オーバーヘッド "
                + String.format("%.2f%%", o) + "）。";
    };
}

/**
 * スレッド名から、そのスレッドが担う役割の日本語解説を返す。
 *
 * <p>HotSpot VM が内部で生成するスレッドにはそれぞれ固有の名前が付けられている。
 * この名前パターンからスレッドの役割を推定し、初学者にもわかる説明を返す。
 *
 * @param name スレッド名
 * @return そのスレッドの役割の日本語解説
 */
String describeThread(String name) {
    return switch (name) {
        // ── 完全一致パターン（固定名の HotSpot 内部スレッド）──
        case "main"                -> "プログラムのエントリーポイント (void main()) を実行";
        case "Reference Handler"   -> "GC 後の参照オブジェクト (WeakRef 等) を ReferenceQueue に転送";
        case "Finalizer"           -> "finalize() メソッドの実行 (非推奨、Cleaner に移行推奨)";
        case "Signal Dispatcher"   -> "OS シグナル (SIGTERM, Ctrl+C 等) の受信と中継";
        case "Notification Thread" -> "JMX 通知の配信 (GC イベント通知など)";
        case "Common-Cleaner"      -> "Cleaner API によるリソース解放 (DirectByteBuffer 等)";
        case "Service Thread"      -> "JVM 内部のサービスタスク処理";
        case "Attach Listener"     -> "外部ツール (jcmd, jstack 等) の接続受付";

        // ── ガード付きパターン（名前の部分一致で判定）──
        case String s when s.contains("GC") ->
            "ガベージコレクション処理";
        case String s when s.contains("C1 CompilerThread") ->
            "JIT C1 コンパイラ (軽量な最適化を担当)";
        case String s when s.contains("C2 CompilerThread") ->
            "JIT C2 コンパイラ (積極的な最適化を担当)";
        case String s when s.contains("CompilerThread") ->
            "JIT コンパイラスレッド";
        case String s when s.contains("Monitor Deflation") ->
            "使用済みモニター (synchronized ロック) の軽量化";

        default -> "(アプリケーション固有またはその他の JVM 内部スレッド)";
    };
}

/**
 * 文字列が指定幅を超える場合、末尾を "..." で切り詰める。
 *
 * @param s 元の文字列
 * @param maxLen 最大文字数
 * @return 切り詰められた文字列
 */
String truncate(String s, int maxLen) {
    if (s.length() <= maxLen) return s;
    return s.substring(0, maxLen - 3) + "...";
}
