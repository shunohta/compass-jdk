/**
 * クラスのライフサイクル（ロード・リンク・初期化）を実験で観察するプログラム。
 *
 * <p>JVM はクラスを「必要になった瞬間」に初期化する（遅延初期化）。
 * このプログラムでは、さまざまな操作がクラスの {@code <clinit>}（静的初期化ブロック）を
 * トリガーするかどうかを実験し、JVM 仕様 §5.5 の初期化条件を体感する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>クラスの初期化がトリガーされる条件（new, static フィールドアクセス等）</li>
 *   <li>コンパイル時定数へのアクセスでは初期化が起きないこと</li>
 *   <li>配列型の生成では要素型が初期化されないこと</li>
 *   <li>{@code Class.forName()} の第2引数で初期化を制御できること</li>
 *   <li>{@link ExceptionInInitializerError} と {@link NoClassDefFoundError} の連鎖</li>
 *   <li>{@code <clinit>} のスレッドセーフ保証（JVM が1回だけの実行を保証）</li>
 * </ul>
 *
 * <h2>対象層</h2>
 * <ul>
 *   <li><strong>Java 層:</strong> static イニシャライザ, {@code Class.forName()}</li>
 *   <li><strong>HotSpot C++ 層:</strong> {@code InstanceKlass::initialize_impl}
 *       ({@code src/hotspot/share/oops/instanceKlass.cpp}) が初期化ロックを管理</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java src/main/java/classloader/ClassLifecycleDemo.java
 * }</pre>
 *
 * @author jdk-core
 * @see Class#forName(String, boolean, ClassLoader)
 */

// ──────────────────────────────────────────────
// 実験用レコード定義
//
// 各レコードの static 初期化ブロックに目印を出力することで、
// いつ初期化が実行されるかを視覚的に確認する。
// レコードは暗黙クラス内で static メンバとして扱われる。
// ──────────────────────────────────────────────

/** 実験1・2で使用: コンパイル時定数 vs 非定数 static フィールド。 */
record Alpha(String value) {
    // <clinit> が実行されるとこの行が出力される
    static { IO.println("    ★ Alpha の <clinit> が実行されました！"); }

    // 非定数 static フィールド -- 初期化フェーズで値が設定される
    static String LABEL = "アルファ";

    // コンパイル時定数 (static final + リテラル)
    // → javac がコンパイル時に使用箇所にリテラル値を直接埋め込む
    // → 実行時に Alpha クラスに触れる必要がないため、初期化は起きない
    static final String TYPE = "ALPHA";
}

/** 実験3・4で使用: インスタンス生成 vs 配列生成。 */
record Beta(int id) {
    static { IO.println("    ★ Beta の <clinit> が実行されました！"); }
}

/** 実験5で使用: {@code Class.forName()} の初期化制御。 */
record Gamma(String data) {
    static { IO.println("    ★ Gamma の <clinit> が実行されました！"); }
}

/** 実験6・7で使用: 初期化失敗のデモ。 */
record Delta(String info) {
    // static フィールドの初期化メソッドが例外を投げるため、
    // Delta の <clinit> は必ず失敗する
    static int BROKEN = breakInit();

    /** 意図的に初期化を失敗させるメソッド。 */
    static int breakInit() {
        IO.println("    ★ Delta の <clinit> 開始... → 例外発生！");
        throw new RuntimeException("Delta の初期化に意図的に失敗");
    }
}

/**
 * プログラムのエントリーポイント。
 *
 * <p>7つの実験を順に実行し、各操作がクラスの初期化をトリガーするかを観察する。
 * 各実験の前に操作内容を出力し、{@code <clinit>} の出力が
 * 間に挿入されるかどうかで初期化の有無がわかる。
 */
void main() throws Exception {
    IO.println("=== クラスライフサイクル実験 ===");
    IO.println("（★ マークが出たらそのクラスの <clinit> が実行された証拠）\n");

    // ──── 実験1: コンパイル時定数のアクセス ────
    IO.println("[実験1] コンパイル時定数 (static final + リテラル) のアクセス");
    IO.println("  → Alpha.TYPE にアクセスします...");
    String type = Alpha.TYPE;
    IO.println("  → Alpha.TYPE = \"" + type + "\"");
    IO.println("  → ★ は出ていない！Alpha は初期化されていない");
    IO.println("    （javac がリテラル \"ALPHA\" を直接埋め込んだため）\n");

    // ──── 実験2: 非定数 static フィールドのアクセス ────
    IO.println("[実験2] 非定数 static フィールドのアクセス");
    IO.println("  → Alpha.LABEL にアクセスします...");
    String label = Alpha.LABEL;
    IO.println("  → Alpha.LABEL = \"" + label + "\"");
    IO.println("  → ★ が出た！今度は Alpha が初期化された\n");

    // ──── 実験3: new によるインスタンス生成 ────
    IO.println("[実験3] new によるインスタンス生成");
    IO.println("  → new Beta(1) を実行します...");
    var beta = new Beta(1);
    IO.println("  → " + beta);
    IO.println("  → ★ が出た！new はクラスの初期化をトリガーする\n");

    // ──── 実験4: 配列型の生成 ────
    IO.println("[実験4] 配列型の生成 (Beta が再度初期化されるか？)");
    IO.println("  → new Beta[10] を実行します...");
    var betas = new Beta[10];
    IO.println("  → Beta[" + betas.length + "] を生成");
    IO.println("  → ★ は出ていない（2回目）");
    IO.println("    ※ Beta は実験3で初期化済み。配列生成では要素型の初期化は不要");
    IO.println("    ※ 仮に Beta が未初期化でも、配列生成だけでは初期化されない\n");

    // ──── 実験5: Class.forName() の初期化制御 ────
    IO.println("[実験5] Class.forName() の初期化制御");

    // 暗黙クラスの名前を取得し、メンバレコードの完全修飾名を構築する
    var lookup = MethodHandles.lookup();
    String enclosing = lookup.lookupClass().getName();
    String gammaName = enclosing + "$Gamma";
    var cl = lookup.lookupClass().getClassLoader();

    IO.println("  → Class.forName(\"...$Gamma\", false, cl) ... initialize=false");
    Class.forName(gammaName, false, cl);
    IO.println("  → ★ は出ていない！initialize=false ではロードのみで初期化しない\n");

    IO.println("  → Class.forName(\"...$Gamma\", true, cl) ... initialize=true");
    Class.forName(gammaName, true, cl);
    IO.println("  → ★ が出た！initialize=true で明示的に初期化をトリガーした\n");

    // ──── 実験6: ExceptionInInitializerError ────
    IO.println("[実験6] ExceptionInInitializerError -- 初期化失敗");
    IO.println("  → new Delta(\"test\") を実行します...");
    try {
        var delta = new Delta("test");
    } catch (ExceptionInInitializerError e) {
        IO.println("  → ExceptionInInitializerError を捕捉！");
        IO.println("    原因: " + e.getCause());
        IO.println("    → <clinit> 内の例外が ExceptionInInitializerError でラップされた\n");
    }

    // ──── 実験7: NoClassDefFoundError ────
    IO.println("[実験7] NoClassDefFoundError -- 初期化失敗後の再アクセス");
    IO.println("  → 再度 new Delta(\"test2\") を実行します...");
    try {
        var delta2 = new Delta("test2");
    } catch (NoClassDefFoundError e) {
        IO.println("  → NoClassDefFoundError を捕捉！");
        IO.println("    メッセージ: " + e.getMessage());
        IO.println("    → 一度初期化に失敗したクラスは永久に使用不能");
        IO.println("    → 2回目以降は ExceptionInInitializerError ではなく");
        IO.println("      NoClassDefFoundError が throw される");
        IO.println("    → <clinit> の再実行は行われない（1回きり）\n");
    }

    // ──── まとめ ────
    IO.println("=== 初期化トリガー早見表 ===\n");
    IO.println(String.format("  %-44s %s", "操作", "初期化される？"));
    IO.println("  " + "─".repeat(60));
    IO.println(String.format("  %-44s %s", "new でインスタンス生成", "✓ はい"));
    IO.println(String.format("  %-44s %s", "非定数 static フィールドへのアクセス", "✓ はい"));
    IO.println(String.format("  %-44s %s", "static メソッドの呼び出し", "✓ はい"));
    IO.println(String.format("  %-44s %s", "Class.forName(name) (initialize=true)", "✓ はい"));
    IO.println(String.format("  %-44s %s", "コンパイル時定数 (static final) のアクセス", "✗ いいえ"));
    IO.println(String.format("  %-44s %s", "配列型の生成 (new Type[n])", "✗ いいえ"));
    IO.println(String.format("  %-44s %s", "Class.forName(name, false, cl)", "✗ いいえ"));
}
