/**
 * GC Roots の仕組みを体感するデモプログラム。
 *
 * <p>ローカル変数、static フィールド、スレッドからの参照が
 * それぞれ GC Root として機能することを実験的に確認する。
 * {@link java.lang.ref.PhantomReference} と {@link java.lang.ref.ReferenceQueue} を
 * 使って、オブジェクトが GC に回収された正確なタイミングを検出する。
 *
 * <h2>GC Roots の種類と本デモでの検証</h2>
 * <ul>
 *   <li><b>ローカル変数</b>: メソッドのスタックフレーム上の参照は GC Root</li>
 *   <li><b>static フィールド</b>: クラスの静的フィールドからの参照は GC Root</li>
 *   <li><b>スレッド参照</b>: 実行中スレッドのスタック上の参照は GC Root</li>
 * </ul>
 *
 * <h2>PhantomReference の特徴</h2>
 * <p>{@code PhantomReference.get()} は常に {@code null} を返す。
 * オブジェクトが GC に回収された後、{@link java.lang.ref.ReferenceQueue} に
 * エンキューされることで、回収を検出できる。WeakReference より厳密な
 * 回収検出が可能であり、{@link java.lang.ref.Cleaner} の内部実装にも使われている。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>GC Roots の3つの種類（ローカル変数、static フィールド、スレッド参照）の違いと役割</li>
 *   <li>{@link java.lang.ref.PhantomReference} による回収タイミングの正確な検出方法</li>
 *   <li>到達可能性解析（Reachability Analysis）の仕組み -- GC Root から辿れるかどうかが生死を決める</li>
 *   <li>参照の切断（{@code null} 代入）によるオブジェクトの GC 対象化</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java -Xmx64m -XX:+UseG1GC -Xlog:gc* \
 *       src/main/java/gc/GcRootsDemo.java
 * }</pre>
 *
 * @author jdk-core
 * @see java.lang.ref.PhantomReference
 * @see java.lang.ref.ReferenceQueue
 */

// GC Root として機能する static フィールド
static byte[] staticRoot = null;

/**
 * 3 種類の GC Root を実験的に確認する:
 * <ol>
 *   <li>ローカル変数からの参照</li>
 *   <li>static フィールドからの参照</li>
 *   <li>スレッドが保持する参照</li>
 * </ol>
 */
void main() {
    IO.println("=== GC Roots を体感するデモ ===");
    IO.println("推奨: -Xmx64m -XX:+UseG1GC -Xlog:gc*\n");

    demonstrateLocalVariableRoot();
    demonstrateStaticFieldRoot();
    demonstrateThreadRoot();

    IO.println("\n=== デモ完了 ===");
}

/**
 * 実験 1: ローカル変数による GC Root。
 * ローカル変数が参照を保持している間、オブジェクトは到達可能（reachable）である。
 * 参照を null に設定すると、GC がオブジェクトを回収できるようになる。
 */
void demonstrateLocalVariableRoot() {
    IO.println("--- 実験 1: ローカル変数による GC Root ---");
    var queue = new ReferenceQueue<byte[]>();

    byte[] localData = new byte[4 * 1024 * 1024]; // 4MB
    var phantom = new PhantomReference<>(localData, queue);

    IO.println("  [1] 4MB オブジェクトを作成し、ローカル変数 localData が参照");
    IO.println("      → localData は GC Root（スタックフレーム上のローカル変数）");

    System.gc();
    sleep(200);
    boolean enqueuedStep2 = (queue.poll() != null);
    IO.println("  [2] System.gc() 実行 → 回収された? " + enqueuedStep2
            + " (false = まだ生存)");

    // 強参照を解放する
    localData = null;
    IO.println("  [3] localData = null; → 強参照を解放");

    System.gc();
    sleep(200);
    boolean enqueuedStep4 = (queue.poll() != null);
    IO.println("  [4] System.gc() 実行 → 回収された? " + enqueuedStep4
            + " (true = GC に回収された)");

    IO.println("  [結論] ローカル変数が null になった途端、GC Root から外れ、回収された\n");
}

/**
 * 実験 2: static フィールドによる GC Root。
 * static フィールドは強力な GC Root であり、クラスがロードされている限り存続する。
 * つまり、static フィールドが参照するオブジェクトは通常の GC では回収されない。
 */
void demonstrateStaticFieldRoot() {
    IO.println("--- 実験 2: static フィールドによる GC Root ---");
    var queue = new ReferenceQueue<byte[]>();

    staticRoot = new byte[4 * 1024 * 1024]; // 4MB
    var phantom = new PhantomReference<>(staticRoot, queue);

    IO.println("  [1] 4MB オブジェクトを static フィールドに格納");
    IO.println("      → static フィールドは GC Root（クラスがロードされている限り有効）");

    System.gc();
    sleep(200);
    boolean enqueuedStatic2 = (queue.poll() != null);
    IO.println("  [2] System.gc() 実行 → 回収された? " + enqueuedStatic2
            + " (false = static 参照が保護)");

    // static 参照を解放する
    staticRoot = null;
    IO.println("  [3] staticRoot = null; → static 参照を解放");

    System.gc();
    sleep(200);
    boolean enqueuedStatic4 = (queue.poll() != null);
    IO.println("  [4] System.gc() 実行 → 回収された? " + enqueuedStatic4
            + " (true = static 参照解放後に回収)");

    IO.println("  [結論] static フィールドが null になるまで、オブジェクトは GC Root 経由で保護される\n");
}

/**
 * 実験 3: スレッド参照による GC Root。
 * 実行中のスレッドのスタックから参照されているオブジェクトは GC Root となる。
 * スレッドが終了すると、そのスタックフレームが破棄され、参照は GC Root ではなくなる。
 */
void demonstrateThreadRoot() {
    IO.println("--- 実験 3: スレッド参照による GC Root ---");
    var queue = new ReferenceQueue<byte[]>();

    // ラムダでキャプチャするために、実質的 final（effectively final）な変数にオブジェクトを保持する
    var holder = new byte[4 * 1024 * 1024]; // 4MB
    var phantom = new PhantomReference<>(holder, queue);

    IO.println("  [1] 4MB オブジェクトを作成");

    // 実質的 final の 'holder' を介して新しいスレッドに参照を渡す
    var thread = Thread.ofPlatform().name("gc-root-holder").start(() -> {
        // スレッドはラムダ経由でキャプチャした 'holder' をスタック上に保持している
        var kept = holder;
        IO.println("  [Thread] オブジェクトを保持しています（スレッドスタックが GC Root）");
        sleep(2000);
        IO.println("  [Thread] スレッド終了 → スタックフレームが破棄される");
        // スレッド終了時に kept はスコープ外になる
    });

    // 'holder' は実質的 final なので null に代入できないが、このデモでは
    // スレッドのスタックがオブジェクトを生存させ続けることを示している。
    IO.println("  [2] main スレッドの holder はスコープ内だが、スレッドも参照を保持中");

    System.gc();
    sleep(200);
    boolean enqueuedStep3 = (queue.poll() != null);
    IO.println("  [3] System.gc() 実行 → 回収された? " + enqueuedStep3
            + " (false = スレッド + main の両方が保持中)");

    // スレッドの終了を待機する
    try {
        thread.join();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
    IO.println("  [4] スレッド終了を確認（スレッド側の参照は消滅）");
    IO.println("  [結論] 別スレッドのスタックに参照がある限り、オブジェクトは GC Root 経由で保護される\n");
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
