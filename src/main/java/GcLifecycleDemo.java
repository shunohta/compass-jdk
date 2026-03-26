/**
 * オブジェクトの一生を観察するデモプログラム。
 *
 * <p>Eden で生成されたオブジェクトが Minor GC を経て Survivor に移動し、
 * 最終的に Old Generation に昇格する過程を、{@link java.lang.ref.WeakReference} を
 * 使って観察する。GC による回収タイミングを検出し、オブジェクトの状態遷移を可視化する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>オブジェクトが Eden → Survivor → Old と移動する過程</li>
 *   <li>{@link java.lang.ref.WeakReference} で GC 回収タイミングを検出する方法</li>
 *   <li>Minor GC と Major GC のタイミングの違い</li>
 *   <li>GC ログと WeakReference の状態変化の相関</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java -Xmx128m -Xms128m -Xmn64m -XX:+UseG1GC \
 *       -Xlog:gc*,gc+age=debug src/main/java/GcLifecycleDemo.java
 * }</pre>
 *
 * @author jdk-core
 * @see java.lang.ref.WeakReference
 * @see java.lang.ref.ReferenceQueue
 */

/**
 * オブジェクトのライフサイクルを観察する: Eden → Survivor → Old → GC による回収。
 * {@link java.lang.ref.WeakReference} を使い、オブジェクトが回収されたタイミングを検出する。
 */
void main() {
    IO.println("=== オブジェクトの一生を観察するデモ ===");
    IO.println("推奨: -Xmx128m -Xms128m -Xmn64m -XX:+UseG1GC -Xlog:gc*,gc+age=debug\n");

    var runtime = Runtime.getRuntime();
    var queue = new ReferenceQueue<byte[]>();

    // -- Phase 1: 短命オブジェクト（Eden で死ぬ） --
    IO.println("--- Phase 1: 短命オブジェクト（Eden で死ぬ）---");
    for (int i = 0; i < 5; i++) {
        var data = new byte[1024 * 1024]; // 1MB、強参照を保持しない
        var ref = new WeakReference<>(data, queue);
        data = null; // 強参照を解放
        IO.println("  作成 #" + i + " → 強参照を即座に解放（Eden で回収されるはず）");
    }
    System.gc();
    sleep(200);
    drainQueue(queue, "Phase 1");

    // -- Phase 2: 中寿命オブジェクト（複数回の Minor GC を生き延びる） --
    IO.println("\n--- Phase 2: 中寿命オブジェクト（Survivor を経由）---");
    var survivors = new ArrayList<byte[]>();
    var survivorRefs = new ArrayList<WeakReference<byte[]>>();

    for (int i = 0; i < 5; i++) {
        var data = new byte[512 * 1024]; // 512KB
        survivorRefs.add(new WeakReference<>(data, queue));
        survivors.add(data); // 強参照を保持する
        IO.println("  作成 #" + i + " → 強参照を保持（Survivor に移動するはず）");
    }

    // 複数回の Minor GC を誘発してオブジェクトの age を増加させる
    IO.println("\n  複数回の Minor GC を誘発して age を増加させる...");
    for (int gc = 0; gc < 5; gc++) {
        // Eden を埋めて Minor GC を誘発するために割り当てて即破棄する
        for (int j = 0; j < 30; j++) {
            var trash = new byte[1024 * 1024]; // 1MB、即座に破棄される
        }
        IO.println("  GC サイクル " + (gc + 1) + " 後: WeakRef 生存数 = "
                + survivorRefs.stream().filter(r -> r.get() != null).count());
    }

    // 強参照を解放する -- 次の GC でオブジェクトが回収されるはず
    IO.println("\n  強参照を解放...");
    survivors.clear();
    System.gc();
    sleep(200);
    drainQueue(queue, "Phase 2");

    // -- Phase 3: 長寿命オブジェクト（Old Generation に昇格） --
    IO.println("\n--- Phase 3: 長寿命オブジェクト（Old Generation に昇格）---");
    var longLived = new ArrayList<byte[]>();
    var longLivedRefs = new ArrayList<WeakReference<byte[]>>();

    for (int i = 0; i < 3; i++) {
        var data = new byte[256 * 1024]; // 256KB
        longLivedRefs.add(new WeakReference<>(data, queue));
        longLived.add(data);
    }
    IO.println("  3 個のオブジェクトを作成し、長期間保持する");

    // 大量の GC サイクルを回してオブジェクトを Old に昇格させる
    IO.println("  大量の GC サイクルを誘発して Old への昇格を促す...");
    for (int gc = 0; gc < 20; gc++) {
        for (int j = 0; j < 20; j++) {
            var trash = new byte[1024 * 1024]; // Eden を埋める
        }
    }
    IO.println("  GC サイクル後: WeakRef 生存数 = "
            + longLivedRefs.stream().filter(r -> r.get() != null).count()
            + " (Old に昇格して生存しているはず)");

    // 強参照を解放し Full GC を誘発する
    IO.println("\n  強参照を解放し、Full GC を誘発...");
    longLived.clear();
    System.gc();
    sleep(200);
    drainQueue(queue, "Phase 3");

    // -- まとめ --
    IO.println("\n=== メモリ状況 ===");
    IO.println("  ヒープ最大: " + formatBytes(runtime.maxMemory()));
    IO.println("  ヒープ合計: " + formatBytes(runtime.totalMemory()));
    IO.println("  使用中:     " + formatBytes(runtime.totalMemory() - runtime.freeMemory()));
    IO.println("  空き:       " + formatBytes(runtime.freeMemory()));
    IO.println("\n=== デモ完了 ===");
}

/**
 * {@link ReferenceQueue} に溜まった参照をすべて取り出し、エンキューされた数を報告する。
 * GC がオブジェクトを回収すると、対応する {@link WeakReference} がキューに入るため、
 * この数が「回収されたオブジェクト数」に相当する。
 *
 * @param queue 監視対象の参照キュー
 * @param phase 現在のフェーズ名（ログ出力用）
 */
void drainQueue(ReferenceQueue<byte[]> queue, String phase) {
    int count = 0;
    while (queue.poll() != null) {
        count++;
    }
    IO.println("  [" + phase + "] ReferenceQueue に到着した WeakRef 数: " + count
            + " (= GC に回収されたオブジェクト数)");
}

/**
 * バイト数を人間が読みやすい文字列（MB 単位）にフォーマットする。
 *
 * @param bytes バイト数
 * @return 「X.X MB」形式の文字列
 */
String formatBytes(long bytes) {
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
}

/**
 * 指定されたミリ秒だけスレッドをスリープさせる。
 * 割り込みが発生した場合は割り込みフラグを復元して即座に返る。
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
