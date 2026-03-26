/**
 * 各種 OutOfMemoryError を意図的に発生させるデモプログラム。
 *
 * <p>メニュー形式で以下のシナリオを選択・実行できる。
 * <ol>
 *   <li>ヒープ枯渇 ({@code OutOfMemoryError: Java heap space})</li>
 *   <li>GC オーバーヘッド超過 ({@code OutOfMemoryError: GC overhead limit exceeded})</li>
 *   <li>スタックオーバーフロー ({@code StackOverflowError})</li>
 * </ol>
 *
 * <h2>各シナリオの発生メカニズム</h2>
 * <ul>
 *   <li><b>ヒープ枯渇</b>: 参照を保持し続けて GC が回収できない状態を作る</li>
 *   <li><b>GC オーバーヘッド超過</b>: ヒープのほぼ全体を到達可能オブジェクトで埋め、
 *       GC が頻繁に走るが回収できない状態を作る</li>
 *   <li><b>スタックオーバーフロー</b>: 無限再帰でスタックフレームを枯渇させる</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * # ヒープ枯渇シナリオ（少ないヒープで実行）
 * java -Xmx32m -Xlog:gc* src/main/java/OomScenarios.java
 *
 * # GC オーバーヘッドシナリオ（とても小さいヒープで実行）
 * java -Xmx16m -Xlog:gc* src/main/java/OomScenarios.java
 *
 * # スタックオーバーフローシナリオ（小さいスタックで実行）
 * java -Xss256k src/main/java/OomScenarios.java
 * }</pre>
 *
 * @author jdk-core
 * @see OutOfMemoryError
 * @see StackOverflowError
 */

/**
 * メニュー形式で OOM シナリオを選択・実行するエントリポイント。
 * 標準入力からシナリオ番号を読み取り、対応するメソッドを呼び出す。
 */
void main() {
    IO.println("=== OOM シナリオ デモ ===\n");
    IO.println("以下のシナリオから選んでください:");
    IO.println("  1) ヒープ枯渇       (OutOfMemoryError: Java heap space)");
    IO.println("  2) GC オーバーヘッド  (OutOfMemoryError: GC overhead limit exceeded)");
    IO.println("  3) スタックオーバーフロー (StackOverflowError)");
    IO.println("  q) 終了\n");

    var scanner = new Scanner(System.in);
    IO.print("選択 > ");

    if (!scanner.hasNextLine()) {
        IO.println("入力がありません。シナリオ 1 をデフォルトで実行します。\n");
        runHeapExhaustion();
        return;
    }

    var choice = scanner.nextLine().trim();

    switch (choice) {
        case "1" -> runHeapExhaustion();
        case "2" -> runGcOverhead();
        case "3" -> runStackOverflow();
        case "q", "Q" -> IO.println("終了します。");
        default -> {
            IO.println("不明な選択: " + choice + "。シナリオ 1 を実行します。\n");
            runHeapExhaustion();
        }
    }
}

/**
 * シナリオ 1: ヒープ枯渇。
 * 1MB の byte 配列を繰り返し確保し、ArrayList で強参照を保持し続けることで
 * GC がメモリを回収できない状態を作り、{@code OutOfMemoryError: Java heap space} を発生させる。
 */
void runHeapExhaustion() {
    IO.println("--- シナリオ 1: ヒープ枯渇 ---");
    IO.println("【原因】参照を保持し続ける ArrayList に byte[] を追加し続ける。");
    IO.println("  GC は「到達可能なオブジェクト」を回収できないため、");
    IO.println("  ヒープが一方的に消費される。\n");

    var runtime = Runtime.getRuntime();
    var retainedData = new ArrayList<byte[]>();
    int count = 0;

    try {
        while (true) {
            retainedData.add(new byte[1024 * 1024]); // 1MB each
            count++;
            long used = runtime.totalMemory() - runtime.freeMemory();
            IO.println("  確保 #" + count + " (1MB) → ヒープ使用: "
                    + formatMB(used) + " / " + formatMB(runtime.maxMemory()));
        }
    } catch (OutOfMemoryError e) {
        IO.println("\n  *** OutOfMemoryError 発生! ***");
        IO.println("  メッセージ: " + e.getMessage());
        IO.println("  確保済みオブジェクト数: " + count);
        IO.println("\n  【解説】");
        IO.println("  retainedData (ArrayList) がすべての byte[] への参照を保持しているため、");
        IO.println("  GC は 1 つも回収できない。ヒープの 100% が到達可能なオブジェクトで埋まり、");
        IO.println("  新しい byte[] を配置する空間がなくなって OOM が発生した。");
        IO.println("  【対処法】");
        IO.println("  - 不要な参照を速やかに null にする / コレクションから remove する");
        IO.println("  - ヒープサイズ (-Xmx) の見直し");
        IO.println("  - ヒープダンプで何がメモリを占有しているか調査する");
    }
}

/**
 * シナリオ 2: GC オーバーヘッド超過。
 * HashMap に大量の小さなエントリを追加し続け、すべてが到達可能な状態にすることで
 * GC が頻繁に実行されるがほぼ何も回収できない状態を作る。
 * JVM が「CPU 時間の 98% 以上を GC に費やしても 2% 未満しか回収できない」と判定すると
 * {@code OutOfMemoryError: GC overhead limit exceeded} を投げる。
 */
void runGcOverhead() {
    IO.println("--- シナリオ 2: GC オーバーヘッド超過 ---");
    IO.println("【原因】HashMap に大量の小さなエントリを追加し続ける。");
    IO.println("  ヒープのほぼ全体が到達可能オブジェクトで埋まり、");
    IO.println("  GC が CPU 時間の 98% 以上を消費しても 2% 未満しか回収できない状態。\n");
    IO.println("  ※ -Xmx16m 程度の小さいヒープで実行すると発生しやすい\n");

    var map = new HashMap<Integer, String>();
    int count = 0;

    try {
        while (true) {
            // 小さなオブジェクトを大量に生成 -- HashMap エントリ、Integer キー、String 値
            for (int i = 0; i < 1000; i++) {
                map.put(count, "value-" + count + "-padding-to-consume-memory");
                count++;
            }
            if (count % 10000 == 0) {
                IO.println("  エントリ数: " + count
                        + " → ヒープ使用: " + formatMB(
                                Runtime.getRuntime().totalMemory()
                                - Runtime.getRuntime().freeMemory()));
            }
        }
    } catch (OutOfMemoryError e) {
        IO.println("\n  *** OutOfMemoryError 発生! ***");
        IO.println("  メッセージ: " + e.getMessage());
        IO.println("  格納エントリ数: " + count);
        IO.println("\n  【解説】");
        IO.println("  HashMap の全エントリが到達可能なため、GC はほとんど回収できない。");
        IO.println("  JVM は「GC に 98% 以上の CPU 時間を費やしても 2% 未満しか回収できない」");
        IO.println("  状態を検知すると、無限ループを避けるために OOM を投げる。");
        IO.println("  これは \"GC overhead limit exceeded\" として現れる。");
        IO.println("  【対処法】");
        IO.println("  - メモリリークの調査（ヒープダンプ解析）");
        IO.println("  - データ構造の見直し（すべてをメモリに持つ必要があるか？）");
        IO.println("  - ヒープサイズの増加（根本解決ではないが応急処置として）");
    }
}

/**
 * シナリオ 3: 無限再帰によるスタックオーバーフロー。
 * 再帰呼び出しのたびにスタックフレーム（ローカル変数・引数・戻りアドレス）が積まれ、
 * スレッドに割り当てられたスタック領域（{@code -Xss}、デフォルト約 1MB）を超えると
 * {@link StackOverflowError} が発生する。
 */
void runStackOverflow() {
    IO.println("--- シナリオ 3: スタックオーバーフロー ---");
    IO.println("【原因】無限再帰でスタックフレームを際限なく積み上げる。");
    IO.println("  各メソッド呼び出しがスタックフレーム（ローカル変数 + 戻りアドレス）を消費し、");
    IO.println("  スレッドに割り当てられたスタック領域 (-Xss) を超えると StackOverflowError。\n");

    try {
        infiniteRecursion(0);
    } catch (StackOverflowError e) {
        IO.println("\n  *** StackOverflowError 発生! ***");
        IO.println("  スタックトレースの深さ: " + e.getStackTrace().length + " フレーム");
        IO.println("\n  【解説】");
        IO.println("  各再帰呼び出しで約 1 フレーム分（ローカル変数 + 引数 + 戻りアドレス）の");
        IO.println("  スタックメモリが消費される。スレッドのスタックサイズ (-Xss、デフォルト ~1MB)");
        IO.println("  を超えた時点で StackOverflowError が投げられる。");
        IO.println("  ※ これは OutOfMemoryError とは異なり、ヒープではなくスタックの問題。");
        IO.println("  【対処法】");
        IO.println("  - 再帰をループに書き換える（末尾再帰の最適化は JVM では保証されない）");
        IO.println("  - 再帰の深さに上限を設ける");
        IO.println("  - 必要であれば -Xss でスタックサイズを増やす（根本解決ではない）");
    }
}

/**
 * スタックを枯渇させるための無限再帰メソッド。
 * 1000 回ごとに現在の再帰の深さを表示する。
 *
 * @param depth 現在の再帰の深さ
 */
void infiniteRecursion(int depth) {
    if (depth % 1000 == 0) {
        IO.println("  再帰の深さ: " + depth);
    }
    infiniteRecursion(depth + 1);
}

/**
 * バイト数をメガバイト表記の文字列にフォーマットする。
 * 例: {@code 1048576} → {@code "1.0 MB"}
 *
 * @param bytes フォーマット対象のバイト数
 * @return "X.X MB" 形式の文字列
 */
String formatMB(long bytes) {
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
}
