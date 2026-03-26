/**
 * ヒープ使用量の増加と GC（ガベージコレクション）の動作を観察するプログラム。
 *
 * <p>繰り返し大きなバイト配列をヒープ上に確保し、リストに保持し続けることで
 * ヒープの使用量が単調に増加する様子を観察する。最終的に
 * {@link OutOfMemoryError} が発生するまでの過程を追跡できる。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>{@code new byte[]} でヒープ上にメモリが確保される仕組み</li>
 *   <li>参照を保持し続けるとGCが回収できず、ヒープが枯渇すること</li>
 *   <li>{@code -verbose:gc} フラグでGCのログをリアルタイムに確認できること</li>
 *   <li>{@code -Xmx} フラグでヒープの最大サイズを制限できること</li>
 *   <li>{@link Runtime#totalMemory()} と {@link Runtime#freeMemory()} で使用量を計算する方法</li>
 * </ul>
 *
 * <h2>GC が回収できない理由</h2>
 * <p>GC はヒープ上の「誰からも参照されていないオブジェクト」を回収する。
 * このプログラムでは、確保した byte[] を {@code retainedObjects} リストに
 * {@code add()} しているため、すべてのオブジェクトが参照を持ち続ける。
 * そのため GC は一切回収できず、ヒープは一方的に消費されていく。
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * # ヒープを 64MB に制限し、GC ログを表示して実行
 * java -Xmx64m -verbose:gc src/main/java/HeapGcObserver.java
 *
 * # ヒープを 256MB にすれば、より多くの反復が成功する
 * java -Xmx256m -verbose:gc src/main/java/HeapGcObserver.java
 * }</pre>
 *
 * @author jdk-core
 * @see java.lang.Runtime
 * @see OutOfMemoryError
 */

/**
 * プログラムのエントリーポイント。
 *
 * <p>ループごとに約 2MB のバイト配列を確保し、リストに追加する。
 * ヒープの使用状況を毎回出力するので、使用量が増えていく様子を観察できる。
 * ヒープが満杯になると {@link OutOfMemoryError} が発生し、その瞬間を捕捉して
 * 何回目の確保で限界に達したかを報告する。
 *
 * <p>-Xmx64m で実行した場合、64MB ÷ 2MB = 約 32 回前後で OOM が発生する
 * （JVM 内部のオーバーヘッドがあるため、実際にはそれより少ない回数になる）。
 */
void main() {
    IO.println("=== ヒープ & GC 観察 ===");
    IO.println("推奨実行方法: -Xmx64m -verbose:gc オプション付きで実行");
    IO.println("（ヒープが枯渇するまでオブジェクトを確保し続けます）\n");

    // Runtime インスタンスを取得。
    // JVM プロセスにつき 1 つだけ存在するシングルトン。
    var runtime = Runtime.getRuntime();

    // 確保した byte[] をここに保持する。
    // リストが参照を持ち続けるため、GC は回収できない。
    var retainedObjects = new ArrayList<byte[]>();

    // ──────────────────────────────────────────────
    // OOM が発生するまで無限に確保し続ける。
    // try-catch で OutOfMemoryError を捕捉し、
    // 「何回目で限界に達したか」を報告する。
    // ──────────────────────────────────────────────
    int count = 0;
    try {
        while (true) {
            // ──────────────────────────────────────────────
            // 約 2MB のバイト配列をヒープ上に確保する。
            // new byte[] は常にヒープにアロケートされる。
            // ──────────────────────────────────────────────
            var chunk = new byte[2 * 1024 * 1024];

            // リストに追加することで、GC ルートからの参照を維持する。
            // これにより GC は chunk を「到達可能」と判定し、回収しない。
            retainedObjects.add(chunk);
            count++;

            // ──────────────────────────────────────────────
            // メモリ使用量の計算:
            //   totalMemory() = JVM が現在 OS から確保しているヒープ量
            //   freeMemory()  = そのうち未使用の量
            //   使用量 = totalMemory() - freeMemory()
            // ──────────────────────────────────────────────
            long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            long maxMB = runtime.maxMemory() / 1024 / 1024;
            IO.println("反復 " + count + ": ヒープ使用量 = " + usedMB + " MB / " + maxMB + " MB");
        }
    } catch (OutOfMemoryError e) {
        // ──────────────────────────────────────────────
        // OutOfMemoryError を捕捉。
        // 通常の Error は catch すべきではないが、
        // 学習目的でここでは意図的に catch している。
        //
        // 【重要】catch に入った時点ではヒープはほぼ満杯。
        // retainedObjects がすべての byte[] を参照しているため、
        // GC は何も回収できない。IO.println 内部の
        // アロケーション（StringBuilder 等）で再度 OOM が
        // 発生するリスクがある。
        // → 最初に参照を解放し、GC が回収できる状態にする。
        // ──────────────────────────────────────────────
        int succeeded = count;               // int はスタック上（ヒープ不要）
        retainedObjects.clear();             // 全 byte[] への参照を切断
        retainedObjects = null;              // ArrayList 自体も GC 対象に
        // ここから先は GC が走れば大量のヒープが回収されるため、
        // IO.println 内部のアロケーションも安全に成功する。

        IO.println("\n========================================");
        IO.println("  OutOfMemoryError 発生!");
        IO.println("========================================");
        IO.println("  メッセージ: " + e.getMessage());
        IO.println("  確保成功回数: " + succeeded + " 回 (各 2MB)");
        IO.println("  合計確保量:   約 " + (succeeded * 2) + " MB");
        IO.println("  ヒープ最大:   " + runtime.maxMemory() / 1024 / 1024 + " MB (-Xmx)");
        IO.println("");
        IO.println("  【なぜ OOM が発生したか】");
        IO.println("  retainedObjects (ArrayList) がすべての byte[] への");
        IO.println("  参照を保持しているため、GC は 1 つも回収できない。");
        IO.println("  ヒープの 100% が到達可能なオブジェクトで埋まり、");
        IO.println("  新しい byte[] を配置する空間がなくなった。");
        IO.println("");
        IO.println("  【-verbose:gc の出力に注目】");
        IO.println("  GC ログを見ると、GC が何度も実行されているのに");
        IO.println("  回収量がほぼ 0 であることがわかる。これが");
        IO.println("  「参照を持ち続ける = GC が無力化する」という証拠。");
    }
}
