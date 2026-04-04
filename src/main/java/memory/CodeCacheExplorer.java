/**
 * コードキャッシュ（Code Cache）の使用状況を確認するプログラム。
 *
 * <p>JIT（Just-In-Time）コンパイラがバイトコードを機械語に変換した結果は、
 * <strong>Code Cache</strong> と呼ばれるヒープ外のメモリ領域に格納される。
 * このプログラムでは、Code Cache の使用状況と JIT コンパイラの動作情報を
 * {@link java.lang.management.ManagementFactory} API を通じて取得・表示する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>Code Cache はヒープ外のネイティブメモリに配置される</li>
 *   <li>JIT コンパイルされた機械語コードが Code Cache に格納される</li>
 *   <li>HotSpot は Code Cache を 3 つのセグメントに分割して管理する（Java 9 以降）</li>
 *   <li>{@link java.lang.management.CompilationMXBean} でJITコンパイラの情報を取得できる</li>
 *   <li>Code Cache が満杯になると JIT コンパイルが停止し、パフォーマンスが劣化する</li>
 * </ul>
 *
 * <h2>Code Cache の 3 セグメント構造（Java 9+ / HotSpot C++ 層）</h2>
 * <p>Java 9 以降の HotSpot では、Code Cache は以下の 3 セグメントに分割されている:
 * <pre>{@code
 * Code Cache（ネイティブメモリ、ヒープ外）
 * ┌─────────────────────────────────────────────────┐
 * │  CodeHeap 'non-nmethods'                         │
 * │  └── JVM 内部のスタブコード、アダプター等          │
 * ├─────────────────────────────────────────────────┤
 * │  CodeHeap 'profiled nmethods'                    │
 * │  └── C1 コンパイル済みコード（プロファイリング付き）│
 * ├─────────────────────────────────────────────────┤
 * │  CodeHeap 'non-profiled nmethods'                │
 * │  └── C2 コンパイル済みコード（最適化済み）         │
 * └─────────────────────────────────────────────────┘
 * }</pre>
 *
 * <h2>JIT コンパイルの流れ</h2>
 * <pre>{@code
 * バイトコード
 *   │
 *   ▼
 * インタプリタで実行（最初の数千回）
 *   │
 *   │ ← 「このメソッドはホットだ」と判定
 *   ▼
 * C1 コンパイラ（軽量な最適化）→ profiled nmethods に格納
 *   │
 *   │ ← さらに頻繁に実行される
 *   ▼
 * C2 コンパイラ（積極的な最適化）→ non-profiled nmethods に格納
 * }</pre>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java src/main/java/memory/CodeCacheExplorer.java
 * }</pre>
 *
 * @author jdk-core
 * @see java.lang.management.MemoryPoolMXBean
 * @see java.lang.management.CompilationMXBean
 * @see java.lang.management.ManagementFactory
 */

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

/**
 * プログラムのエントリーポイント。
 *
 * <p>Code Cache 関連のメモリプール情報と JIT コンパイラの統計を表示する。
 */
void main() {
    IO.println("=== Code Cache エクスプローラー ===\n");

    // ──────────────────────────────────────────────
    // 1. Code Cache 関連のメモリプールを探索
    //
    // Code Cache のプール名は HotSpot のバージョンによって異なる。
    // Java 9+ では "CodeHeap" という名前で 3 セグメントに分割されている。
    // Java 8 以前では "Code Cache" という単一のプール名だった。
    // ──────────────────────────────────────────────
    IO.println("[Code Cache 関連メモリプール]");
    IO.println("  (NON_HEAP タイプからコードキャッシュ系を抽出)\n");

    long totalUsed = 0;
    long totalCommitted = 0;

    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
        var name = pool.getName();
        // "CodeHeap" (Java 9+) または "Code Cache" (Java 8) を検索
        if (pool.getType() == MemoryType.NON_HEAP
                && (name.contains("CodeHeap") || name.contains("Code Cache"))) {
            var usage = pool.getUsage();
            IO.println("  プール名: " + name);
            IO.println("    使用量(Used):   " + formatBytes(usage.getUsed()));
            IO.println("    確保量(Commit): " + formatBytes(usage.getCommitted()));
            IO.println("    最大値(Max):    " + formatBytes(usage.getMax()));
            IO.println();

            totalUsed += usage.getUsed();
            totalCommitted += usage.getCommitted();
        }
    }

    IO.println("  [合計] 使用=" + formatBytes(totalUsed)
        + ", 確保=" + formatBytes(totalCommitted));

    // ──────────────────────────────────────────────
    // 2. JIT コンパイラ情報
    //
    // CompilationMXBean からコンパイラ名とコンパイル時間を取得する。
    // HotSpot のデフォルトでは "HotSpot 64-Bit Tiered Compilers" が返される。
    // "Tiered" は C1 と C2 の二段階コンパイルを意味する。
    // ──────────────────────────────────────────────
    var compilation = ManagementFactory.getCompilationMXBean();
    IO.println("\n[JIT コンパイラ情報]");
    IO.println("  コンパイラ名: " + compilation.getName());

    // コンパイル時間の監視がサポートされているか確認
    if (compilation.isCompilationTimeMonitoringSupported()) {
        IO.println("  累計コンパイル時間: " + compilation.getTotalCompilationTime() + " ms");
    } else {
        IO.println("  累計コンパイル時間: (監視未サポート)");
    }

    // ──────────────────────────────────────────────
    // 3. コンパイルを誘発する計算処理
    //
    // 短いループを実行して、JIT コンパイルが発生する状況を作り出す。
    // ループ実行後にコンパイル時間が増加していれば、
    // JIT が動作した証拠となる。
    // ──────────────────────────────────────────────
    IO.println("\n[JIT コンパイルを誘発する計算を実行中...]");
    long beforeCompileTime = compilation.isCompilationTimeMonitoringSupported()
        ? compilation.getTotalCompilationTime() : 0;

    // ホットスポット検出を誘発するために、十分な回数ループを回す
    long sum = 0;
    for (int i = 0; i < 100_000; i++) {
        sum += computeSomething(i);
    }
    IO.println("  計算結果(参考): " + sum);

    if (compilation.isCompilationTimeMonitoringSupported()) {
        long afterCompileTime = compilation.getTotalCompilationTime();
        long delta = afterCompileTime - beforeCompileTime;
        IO.println("  計算中に発生したコンパイル時間: " + delta + " ms");
    }

    // ──────────────────────────────────────────────
    // 4. コンパイル後の Code Cache を再確認
    // ──────────────────────────────────────────────
    IO.println("\n[計算後の Code Cache 使用量]");
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
        var name = pool.getName();
        if (pool.getType() == MemoryType.NON_HEAP
                && (name.contains("CodeHeap") || name.contains("Code Cache"))) {
            var usage = pool.getUsage();
            IO.println("  " + name + ": 使用=" + formatBytes(usage.getUsed()));
        }
    }

    // ──────────────────────────────────────────────
    // 5. 解説メッセージ
    // ──────────────────────────────────────────────
    IO.println("\n[解説]");
    IO.println("  Code Cache が満杯になると JIT コンパイルが停止し、");
    IO.println("  新しいメソッドはインタプリタで実行される（性能劣化）。");
    IO.println("  -XX:ReservedCodeCacheSize=512m でサイズを拡大可能。");
    IO.println("  -XX:+PrintCompilation でコンパイル対象メソッドを確認できる。");
}

/**
 * JIT コンパイルを誘発するための簡単な計算メソッド。
 *
 * <p>このメソッドが数千回以上呼ばれると、HotSpot の
 * ホットスポット検出機構が「このメソッドはホットだ」と判定し、
 * C1 → C2 と段階的に JIT コンパイルを行う。
 *
 * @param n 入力値
 * @return 計算結果
 */
long computeSomething(int n) {
    // 意図的に少し複雑な計算を行い、JIT の最適化対象にする
    long result = n;
    result = result * 31 + 7;
    result = result ^ (result >>> 16);
    return result;
}

/**
 * バイト数を人間が読みやすい形式に変換するヘルパーメソッド。
 *
 * @param bytes バイト数
 * @return 人間が読みやすい形式の文字列
 */
String formatBytes(long bytes) {
    if (bytes < 0) return "制限なし";
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
    return (bytes / 1024 / 1024) + " MB";
}
