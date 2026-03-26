import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * Tiered Compilation のウォームアップ過程を計測し、
 * インタプリタ → C1 → C2 の性能変化を観察するプログラム。
 *
 * <p>JVM は起動直後、すべてのメソッドをインタプリタで実行する。
 * メソッドが繰り返し呼ばれると、まず C1 コンパイラが軽い最適化で機械語に変換し、
 * さらに頻繁に呼ばれると C2 コンパイラが積極的な最適化で再コンパイルする。
 * このプログラムでは、同じメソッドを繰り返し実行しながら実行時間を計測し、
 * Tiered Compilation による段階的な性能向上を体感する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>Tiered Compilation の5レベル（Level 0〜4）の遷移を体感する</li>
 *   <li>ウォームアップ（JIT コンパイルが効くまで）の概念を理解する</li>
 *   <li>インタプリタ → C1 → C2 で実行時間がどう変化するか</li>
 *   <li>OSR（On-Stack Replacement）による実行中ループの最適化を確認する</li>
 *   <li>{@code -XX:+PrintCompilation} でコンパイル過程を可視化する方法</li>
 *   <li>マイクロベンチマークの注意点（デッドコード除去対策等）</li>
 * </ul>
 *
 * <h2>対象層</h2>
 * <ul>
 *   <li><strong>Java 層:</strong> {@code System.nanoTime()} による計測</li>
 *   <li><strong>HotSpot C++ 層:</strong> {@code src/hotspot/share/compiler/compileBroker.cpp}
 *       （コンパイルキュー管理）、{@code src/hotspot/share/runtime/tieredThresholdPolicy.cpp}
 *       （Tiered Compilation の閾値判定）</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * # 基本実行
 * java src/main/java/JitWarmupDemo.java
 *
 * # JIT コンパイルの過程を表示
 * java -XX:+PrintCompilation src/main/java/JitWarmupDemo.java
 *
 * # インタプリタのみ（JIT 無効）で比較
 * java -Xint src/main/java/JitWarmupDemo.java
 *
 * # C1 のみ（C2 無効）で比較
 * java -XX:TieredStopAtLevel=1 src/main/java/JitWarmupDemo.java
 * }</pre>
 *
 * @author jdk-core
 * @see java.lang.management.CompilationMXBean
 */

// ──────────────────────────────────────────────
// 定数定義
// ──────────────────────────────────────────────

/** 各バッチの反復回数。十分な計測精度を確保するために大きめに設定。 */
static final int ITERATIONS_PER_BATCH = 100_000;

/** バッチ数。ウォームアップ → 定常状態への遷移を観察するのに十分な回数。 */
static final int BATCH_COUNT = 30;

/** 結果を蓄積するフィールド。JIT のデッドコード除去を防ぐための「シンク」。 */
static long sink;

/**
 * プログラムのエントリーポイント。
 *
 * <p>3つの実験を順に実行し、JIT コンパイルによる性能変化を観察する。
 */
void main() {
    IO.println("=== JIT ウォームアップ観察 -- Tiered Compilation の段階的最適化 ===\n");

    experiment1_WarmupCurve();
    experiment2_InliningEffect();
    experiment3_EscapeAnalysis();

    printJvmFlagsGuide();
}

// ──── 実験1: ウォームアップカーブ ────

/**
 * 実験1: 同一メソッドを繰り返し実行し、実行時間の変化を観察する。
 *
 * <p>初期のバッチはインタプリタ実行のため遅く、C1/C2 コンパイルが適用されるにつれて
 * 高速化する。この「ウォームアップカーブ」を可視化する。
 */
void experiment1_WarmupCurve() {
    IO.println("──────────────────────────────────────────────────────────────");
    IO.println("[実験1] ウォームアップカーブ -- インタプリタ→C1→C2 の性能変化\n");
    IO.println("  同じ計算メソッドを " + BATCH_COUNT + " バッチ × "
            + formatCount(ITERATIONS_PER_BATCH) + " 回ずつ実行し、");
    IO.println("  1バッチあたりの実行時間を計測する。\n");

    IO.println(String.format("  %-8s  %-12s  %-8s  %s", "バッチ", "時間 (μs)", "速度比", "バー"));
    IO.println("  " + "─".repeat(60));

    long firstBatchNanos = 0;

    for (int batch = 1; batch <= BATCH_COUNT; batch++) {
        long start = System.nanoTime();

        // 計算メソッドを繰り返し実行
        for (int i = 0; i < ITERATIONS_PER_BATCH; i++) {
            sink += compute(i);
        }

        long elapsed = System.nanoTime() - start;
        double elapsedMicros = elapsed / 1_000.0;

        if (batch == 1) firstBatchNanos = elapsed;

        // 初回バッチとの速度比を計算
        double speedRatio = (double) firstBatchNanos / elapsed;
        String bar = renderBar(speedRatio, 20);

        IO.println(String.format("  %-8d  %,10.0f    %5.1fx   %s",
                batch, elapsedMicros, speedRatio, bar));
    }

    IO.println();
    IO.println("  → 最初の数バッチは遅い（インタプリタ実行 + C1 コンパイル中）");
    IO.println("  → バッチが進むと高速化（C1 → C2 コンパイルが適用される）");
    IO.println("  → 一定バッチ以降は安定（C2 の最終最適化コードで定常状態）\n");
    IO.println("  ★ -XX:+PrintCompilation を付けて実行すると、");
    IO.println("    いつコンパイルが起きたか確認できる\n");
}

/**
 * JIT 最適化の対象となる計算メソッド。
 *
 * <p>ループ内で算術演算を繰り返す。JIT コンパイラはこのメソッドを
 * インライン化し、ループ展開や定数畳み込み等の最適化を適用する。
 *
 * <p>※ 結果を返すことで、JIT のデッドコード除去を防いでいる。
 *
 * @param seed 計算の入力値
 * @return 計算結果
 */
int compute(int seed) {
    int result = seed;
    for (int j = 0; j < 10; j++) {
        result = (result * 31 + 17) ^ (result >>> 3);
    }
    return result;
}

// ──── 実験2: インライン化の効果 ────

/**
 * 実験2: メソッド呼び出しの深さによるインライン化の効果を比較する。
 *
 * <p>直接計算、1段の呼び出し、3段のネスト呼び出しを比較し、
 * JIT のインライン化がメソッド呼び出しのオーバーヘッドを消去することを確認する。
 */
void experiment2_InliningEffect() {
    IO.println("──────────────────────────────────────────────────────────────");
    IO.println("[実験2] インライン化の効果 -- メソッド呼び出しの深さと性能\n");

    // ウォームアップ: 各メソッドを十分に呼び出して JIT コンパイルさせる
    IO.println("  ウォームアップ中...");
    for (int i = 0; i < 50_000; i++) {
        sink += directCompute(i);
        sink += oneLevel(i);
        sink += threeLevel(i);
    }
    IO.println("  ウォームアップ完了\n");

    int iterations = 1_000_000;

    // 直接計算
    long start1 = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
        sink += directCompute(i);
    }
    long direct = System.nanoTime() - start1;

    // 1段の呼び出し
    long start2 = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
        sink += oneLevel(i);
    }
    long one = System.nanoTime() - start2;

    // 3段のネスト呼び出し
    long start3 = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
        sink += threeLevel(i);
    }
    long three = System.nanoTime() - start3;

    IO.println(String.format("  %-24s  %,10d ns", "直接計算（インライン不要）", direct));
    IO.println(String.format("  %-24s  %,10d ns", "1段呼び出し", one));
    IO.println(String.format("  %-24s  %,10d ns", "3段ネスト呼び出し", three));

    IO.println();
    IO.println("  → ウォームアップ後は、呼び出しの深さに関わらず性能がほぼ同じ");
    IO.println("  → JIT がメソッド呼び出しをインライン化し、呼び出しコストをゼロにした");
    IO.println("  → インライン化 = JIT 最適化の中で「最も重要」と言われる理由\n");
}

/** 直接計算（メソッド呼び出しなし）。 */
int directCompute(int x) {
    return (x * 31 + 17) ^ (x >>> 3);
}

/** 1段のメソッド呼び出しを経由する計算。 */
int oneLevel(int x) {
    return innerCompute(x);
}

/** 3段のネスト呼び出しを経由する計算。 */
int threeLevel(int x) {
    return level1(x);
}

int level1(int x) { return level2(x); }
int level2(int x) { return level3(x); }
int level3(int x) { return (x * 31 + 17) ^ (x >>> 3); }

/** innerCompute: oneLevel から呼ばれる内部計算。 */
int innerCompute(int x) {
    return (x * 31 + 17) ^ (x >>> 3);
}

// ──── 実験3: エスケープ解析 ────

/**
 * 実験3: エスケープ解析によるオブジェクト割り当て最適化を観察する。
 *
 * <p>メソッド内でしか使わないオブジェクト（NoEscape）を大量に生成し、
 * GC 負荷を観察する。C2 のエスケープ解析が効くと、
 * ヒープ割り当てが省略（スカラー置換）され、GC 圧力が劇的に減少する。
 */
void experiment3_EscapeAnalysis() {
    IO.println("──────────────────────────────────────────────────────────────");
    IO.println("[実験3] エスケープ解析 -- オブジェクト割り当ての省略\n");

    // ウォームアップ
    IO.println("  ウォームアップ中...");
    for (int i = 0; i < 50_000; i++) {
        sink += sumPoint(i, i + 1);
    }
    IO.println("  ウォームアップ完了\n");

    int iterations = 5_000_000;

    // GC 前のカウントを記録
    long gcCountBefore = getGcCount();

    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
        sink += sumPoint(i, i + 1);
    }
    long elapsed = System.nanoTime() - start;

    long gcCountAfter = getGcCount();
    long gcsDuringTest = gcCountAfter - gcCountBefore;

    IO.println(String.format("  Point オブジェクト生成回数:  %,d 回", iterations));
    IO.println(String.format("  実行時間:                   %,d μs", elapsed / 1_000));
    IO.println(String.format("  テスト中の GC 発生回数:     %d 回", gcsDuringTest));

    IO.println();
    if (gcsDuringTest == 0) {
        IO.println("  → GC が発生していない！");
        IO.println("    C2 のエスケープ解析が Point の割り当てを省略した（スカラー置換）");
        IO.println("    → " + formatCount(iterations) + " 個のオブジェクトが実際には生成されなかった");
    } else {
        IO.println("  → GC が " + gcsDuringTest + " 回発生した");
        IO.println("    エスケープ解析が効いていない可能性がある（-Xint で実行していないか確認）");
    }

    IO.println();
    IO.println("  ★ エスケープ解析の効果を確認する方法:");
    IO.println("    java -XX:-DoEscapeAnalysis src/main/java/JitWarmupDemo.java");
    IO.println("    → エスケープ解析を無効にすると GC 回数が増える\n");
}

/**
 * Point を生成して x + y を返す。エスケープ解析のテスト対象。
 *
 * <p>Point はこのメソッド内でしか使われない（NoEscape）ため、
 * C2 はヒープ割り当てを省略してスカラー置換できる。
 *
 * @param x X 座標
 * @param y Y 座標
 * @return x + y
 */
int sumPoint(int x, int y) {
    var p = new Point(x, y);
    return p.x() + p.y();
}

/** エスケープ解析テスト用の Point レコード。 */
record Point(int x, int y) {}

// ──── ユーティリティ ────

/**
 * GC の合計発生回数を取得する。
 *
 * @return 全 GC コレクターの合計回数
 */
long getGcCount() {
    long count = 0;
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
        long c = gc.getCollectionCount();
        if (c > 0) count += c;
    }
    return count;
}

/**
 * 速度比をバーチャートで表現する。
 *
 * @param ratio 速度比（1.0x 基準）
 * @param maxWidth バーの最大幅（文字数）
 * @return バーチャートの文字列
 */
String renderBar(double ratio, int maxWidth) {
    int width = (int) Math.min(ratio * 2, maxWidth);
    return "█".repeat(Math.max(1, width));
}

/**
 * 数値をカンマ区切りで表現する。
 *
 * @param count 数値
 * @return カンマ区切りの文字列
 */
String formatCount(int count) {
    return String.format("%,d", count);
}

/** JVM フラグのガイドを表示する。 */
void printJvmFlagsGuide() {
    IO.println("=== JIT 関連 JVM フラグガイド ===\n");

    IO.println(String.format("  %-42s %s", "フラグ", "効果"));
    IO.println("  " + "─".repeat(72));
    IO.println(String.format("  %-42s %s", "-XX:+PrintCompilation", "コンパイルイベントを表示"));
    IO.println(String.format("  %-42s %s", "-Xint", "インタプリタのみ（JIT 無効）"));
    IO.println(String.format("  %-42s %s", "-XX:TieredStopAtLevel=1", "C1 のみ（C2 無効）"));
    IO.println(String.format("  %-42s %s", "-XX:-DoEscapeAnalysis", "エスケープ解析を無効化"));
    IO.println(String.format("  %-42s %s", "-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining",
            "インライン化の判定結果を表示"));
    IO.println(String.format("  %-42s %s", "-XX:CompileThreshold=N", "JIT コンパイルの閾値を変更"));

    IO.println();
    IO.println("  ★ -XX:+PrintCompilation の出力の読み方:");
    IO.println("    123   4  3  JitWarmupDemo::compute (42 bytes)");
    IO.println("    │     │  │  │                       │");
    IO.println("    │     │  │  メソッド名               バイトコードサイズ");
    IO.println("    │     │  コンパイルレベル (1=C1, 4=C2)");
    IO.println("    │     コンパイル ID");
    IO.println("    タイムスタンプ (ms)");
    IO.println();
    IO.println("    % マークがあれば OSR (On-Stack Replacement) コンパイル");
    IO.println("    ! マークがあれば例外ハンドラを含むメソッド");
    IO.println("    \"made not entrant\" は脱最適化でコードが無効化された印");
}
