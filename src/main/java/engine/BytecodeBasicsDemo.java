/**
 * バイトコードのスタック計算モデルをシミュレーションし、javap 出力の読み方を学ぶプログラム。
 *
 * <p>JVM はスタックマシンであり、すべての計算を「オペランドスタック」上で行う。
 * このプログラムでは、Java のスタックを使って JVM のバイトコード命令を模擬的に実行し、
 * 各命令がスタックをどのように操作するかを可視化する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>JVM がスタックマシンであること -- すべての計算は push/pop で行われる</li>
 *   <li>バイトコード命令の分類（ロード/ストア、算術演算、制御フロー等）</li>
 *   <li>javap で逆アセンブルしたバイトコードの読み方</li>
 *   <li>メソッド呼び出し命令（invoke 系）の違い</li>
 *   <li>実際の Java コードがどのようなバイトコードに変換されるか</li>
 * </ul>
 *
 * <h2>対象層</h2>
 * <ul>
 *   <li><strong>Java 層:</strong> オペランドスタックの概念、javap ツール</li>
 *   <li><strong>HotSpot C++ 層:</strong> {@code src/hotspot/share/interpreter/bytecodes.hpp}
 *       （バイトコード定義）、{@code templateInterpreter.cpp}（Template Interpreter）</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java src/main/java/engine/BytecodeBasicsDemo.java
 * }</pre>
 *
 * <h2>javap で本プログラム自身のバイトコードを確認する方法</h2>
 * <pre>{@code
 * # コンパイルして javap で逆アセンブル
 * javac -d /tmp src/main/java/engine/BytecodeBasicsDemo.java
 * javap -c -p /tmp/BytecodeBasicsDemo.class
 * }</pre>
 *
 * @author jdk-core
 * @see java.util.Deque
 */

// ──────────────────────────────────────────────
// バイトコードシミュレーター
//
// JVM のオペランドスタックを ArrayDeque で模擬し、
// 各バイトコード命令の実行過程を可視化する。
// ──────────────────────────────────────────────

/**
 * JVM のオペランドスタックをシミュレートするレコード。
 *
 * <p>内部に {@link ArrayDeque} を保持し、バイトコード命令に対応する
 * メソッド（{@code iconst}, {@code iadd} 等）を提供する。
 * 各操作でスタックの変化を視覚的に出力する。
 *
 * @param name  シミュレーション名（表示用）
 * @param stack オペランドスタック本体
 */
record OperandStack(String name, ArrayDeque<Integer> stack) {

    /** 新しいスタックを作成する。 */
    static OperandStack create(String name) {
        return new OperandStack(name, new ArrayDeque<>());
    }

    // ──── ロード/ストア命令 ────

    /**
     * iconst_N: 定数 N をスタックに積む。
     *
     * @param value 積む定数値
     */
    void iconst(int value) {
        stack.push(value);
        log("iconst_" + value, "定数 " + value + " をスタックに積む");
    }

    /**
     * iload_N: ローカル変数 N の値をスタックに積む。
     *
     * @param index ローカル変数のインデックス
     * @param value 変数の値（シミュレーション用に直接渡す）
     */
    void iload(int index, int value) {
        stack.push(value);
        log("iload_" + index, "ローカル変数 " + index + " (=" + value + ") をスタックに積む");
    }

    /**
     * istore_N: スタックトップの値をローカル変数 N に保存する。
     *
     * @param index ローカル変数のインデックス
     * @return 保存された値
     */
    int istore(int index) {
        int value = stack.pop();
        log("istore_" + index, "スタックから取り出し → ローカル変数 " + index + " に保存 (=" + value + ")");
        return value;
    }

    // ──── 算術演算命令 ────

    /** iadd: スタックから2つ取り出し、加算結果を積む。 */
    void iadd() {
        int b = stack.pop();
        int a = stack.pop();
        stack.push(a + b);
        log("iadd", a + " + " + b + " = " + (a + b));
    }

    /** isub: スタックから2つ取り出し、減算結果を積む。 */
    void isub() {
        int b = stack.pop();
        int a = stack.pop();
        stack.push(a - b);
        log("isub", a + " - " + b + " = " + (a - b));
    }

    /** imul: スタックから2つ取り出し、乗算結果を積む。 */
    void imul() {
        int b = stack.pop();
        int a = stack.pop();
        stack.push(a * b);
        log("imul", a + " * " + b + " = " + (a * b));
    }

    // ──── スタック操作命令 ────

    /** dup: スタックトップの値を複製する。 */
    void dup() {
        int value = stack.peek();
        stack.push(value);
        log("dup", "スタックトップ (=" + value + ") を複製");
    }

    /** pop: スタックトップの値を破棄する。 */
    void ipop() {
        int value = stack.pop();
        log("pop", "スタックトップ (=" + value + ") を破棄");
    }

    /** swap: スタックの上位2つの値を入れ替える。 */
    void swap() {
        int top = stack.pop();
        int second = stack.pop();
        stack.push(top);
        stack.push(second);
        log("swap", top + " と " + second + " を入れ替え");
    }

    /** スタックトップの値を取得する（ireturn のシミュレーション用）。 */
    int peek() {
        return stack.peek();
    }

    /** スタックの現在の状態を文字列で表現する。 */
    private String stackView() {
        if (stack.isEmpty()) return "[]";
        var sb = new StringBuilder("[");
        // ArrayDeque の iterator は LIFO 順（トップが先）
        var it = stack.iterator();
        var first = true;
        while (it.hasNext()) {
            if (!first) sb.append(", ");
            sb.append(it.next());
            first = false;
        }
        sb.append("] ← top");
        return sb.toString();
    }

    /** 命令の実行をログ出力する。 */
    private void log(String instruction, String description) {
        IO.println(String.format("    %-14s → %-36s スタック: %s",
                instruction, description, stackView()));
    }
}

/**
 * プログラムのエントリーポイント。
 *
 * <p>4つのシミュレーションを順に実行し、バイトコードのスタック操作を可視化する。
 * 各シミュレーションは実際の javap 出力に対応するバイトコード列を模擬実行する。
 */
void main() {
    IO.println("=== バイトコード基礎 -- スタックマシンシミュレーション ===\n");

    simulation1_SimpleAddition();
    simulation2_ComplexExpression();
    simulation3_StackManipulation();
    simulation4_JavapReading();

    printBytecodeReference();
}

// ──── シミュレーション1: 単純な加算 ────

/**
 * シミュレーション1: {@code int c = a + b} のバイトコードを模擬実行する。
 *
 * <p>最も基本的なスタック操作。2つのローカル変数をスタックに積み、
 * {@code iadd} で加算し、結果をローカル変数に保存する。
 *
 * <p>対応する javap 出力:
 * <pre>{@code
 *   0: iload_1        // a をスタックに積む
 *   1: iload_2        // b をスタックに積む
 *   2: iadd           // 加算
 *   3: istore_3       // 結果を c に保存
 * }</pre>
 */
void simulation1_SimpleAddition() {
    IO.println("──────────────────────────────────────────────────────────────");
    IO.println("[シミュレーション1] int c = a + b  (a=3, b=5)");
    IO.println("  Java コード: int c = a + b;");
    IO.println("  バイトコード:\n");

    var stack = OperandStack.create("加算");

    // ローカル変数: [this, a=3, b=5]
    stack.iload(1, 3);    // iload_1: a(=3) をスタックへ
    stack.iload(2, 5);    // iload_2: b(=5) をスタックへ
    stack.iadd();         // iadd: 3+5=8
    int c = stack.istore(3);  // istore_3: 結果をローカル変数 3 (c) へ

    IO.println("\n  → 結果: c = " + c + "\n");
}

// ──── シミュレーション2: 複雑な式 ────

/**
 * シミュレーション2: {@code int result = (a + b) * (a - b)} のバイトコードを模擬実行する。
 *
 * <p>和差の積を計算する。中間結果がスタック上でどのように管理されるかを観察する。
 * コンパイラは部分式の計算順序を制御し、スタックの深さを最小化する。
 */
void simulation2_ComplexExpression() {
    IO.println("──────────────────────────────────────────────────────────────");
    IO.println("[シミュレーション2] int result = (a + b) * (a - b)  (a=7, b=3)");
    IO.println("  Java コード: int result = (a + b) * (a - b);");
    IO.println("  バイトコード:\n");

    var stack = OperandStack.create("和差積");

    // (a + b) を計算
    stack.iload(1, 7);    // a=7
    stack.iload(2, 3);    // b=3
    stack.iadd();         // 7+3=10 ... (a+b) がスタックに残る

    // (a - b) を計算
    stack.iload(1, 7);    // a=7
    stack.iload(2, 3);    // b=3
    stack.isub();         // 7-3=4 ... (a-b) がスタックに残る

    // (a+b) * (a-b)
    stack.imul();         // 10*4=40

    int result = stack.istore(3);

    IO.println("\n  → 結果: result = " + result);
    IO.println("  → スタック最大深度は 3（imul 直前に [10, 7, 3] が積まれていた時点）\n");
}

// ──── シミュレーション3: スタック操作命令 ────

/**
 * シミュレーション3: dup, pop, swap 命令の動作を可視化する。
 *
 * <p>{@code dup} は特に重要で、{@code new} 命令の後に必ず使われる。
 * オブジェクト生成 ({@code new}) は参照をスタックに1つ置くが、
 * コンストラクタ ({@code invokespecial}) が参照を消費するため、
 * 事前に {@code dup} で複製しておく必要がある。
 */
void simulation3_StackManipulation() {
    IO.println("──────────────────────────────────────────────────────────────");
    IO.println("[シミュレーション3] スタック操作命令: dup, pop, swap");
    IO.println("  new Object() のバイトコードで dup が不可欠な理由を学ぶ\n");

    var stack = OperandStack.create("スタック操作");

    IO.println("  --- dup の動作 ---");
    stack.iconst(42);
    stack.dup();
    IO.println("  → スタック上に 42 が2つ。new 後の dup も同じ仕組み\n");

    IO.println("  --- swap の動作 ---");
    stack.swap();
    IO.println("  → 上位2つの順序が入れ替わる（値は同じ 42 なので見た目は変わらない）\n");

    IO.println("  --- pop の動作 ---");
    stack.ipop();
    stack.ipop();
    IO.println("  → スタックが空になった\n");

    // new Object() のバイトコード擬似実行
    IO.println("  --- new Object() のバイトコード擬似実行 ---");
    IO.println("""
      実際のバイトコード:
        new           #2    // ヒープにオブジェクト領域を確保 → 参照をスタックへ
        dup                 // 参照を複製（コンストラクタが1つ消費するため）
        invokespecial #3    // <init>() を呼び出し（参照を1つ消費）
        astore_1            // 残った参照をローカル変数に保存

      dup がないと:
        new           #2    // 参照がスタックに1つ
        invokespecial #3    // コンストラクタが参照を消費 → スタック空！
        astore_1            // ★ 保存すべき参照がない！→ VerifyError
    """);
}

// ──── シミュレーション4: javap 出力の読み方 ────

/**
 * シミュレーション4: javap 出力の読み方ガイドを表示する。
 *
 * <p>実際の javap 出力例を示し、各要素の意味を解説する。
 * このプログラム自身を javap で確認することで、理解を深められる。
 */
void simulation4_JavapReading() {
    IO.println("──────────────────────────────────────────────────────────────");
    IO.println("[シミュレーション4] javap 出力の読み方\n");

    IO.println("""
      javap -c の出力フォーマット:

        int add(int, int);
          Code:
             0: iload_1        ← オフセット0: 第1引数をロード
             1: iload_2        ← オフセット1: 第2引数をロード
             2: iadd           ← オフセット2: 加算
             3: ireturn        ← オフセット3: int を返す

      各行の読み方:
        ┌─ オフセット（バイト位置）
        │
        │    ┌─ オペコード（命令名）
        │    │
        │    │              ┌─ オペランド（引数、ある場合のみ）
        │    │              │
        0: iload_1         // コメント
        │
        └── このオフセットから次のオフセットまでが1命令分
            （iload_1 は 1バイト、ldc は 2バイト、goto は 3バイト等）""");

    IO.println();
    IO.println("""
      javap -c -p で private メソッドも表示。
      javap -v で定数プール、フラグ、スタックマップテーブル等の詳細情報も表示。

      ★ 実践: 本プログラム自身のバイトコードを確認してみよう:
         javac -d /tmp src/main/java/engine/BytecodeBasicsDemo.java
         javap -c -p /tmp/BytecodeBasicsDemo.class
    """);
}

// ──── バイトコード早見表 ────

/** バイトコード命令の分類早見表を表示する。 */
void printBytecodeReference() {
    IO.println("=== バイトコード命令 分類早見表 ===\n");

    IO.println(String.format("  %-16s %-28s %s", "カテゴリ", "代表的な命令", "説明"));
    IO.println("  " + "─".repeat(72));
    IO.println(String.format("  %-16s %-28s %s", "ロード/ストア", "iload, aload, istore, astore", "ローカル変数 ↔ スタック"));
    IO.println(String.format("  %-16s %-28s %s", "定数", "iconst, ldc, bipush", "定数をスタックに積む"));
    IO.println(String.format("  %-16s %-28s %s", "算術演算", "iadd, isub, imul, idiv", "スタック上で演算"));
    IO.println(String.format("  %-16s %-28s %s", "型変換", "i2l, i2f, i2d, l2i", "プリミティブ型の変換"));
    IO.println(String.format("  %-16s %-28s %s", "オブジェクト", "new, getfield, putfield", "オブジェクトの生成・操作"));
    IO.println(String.format("  %-16s %-28s %s", "スタック操作", "dup, pop, swap", "スタック自体の操作"));
    IO.println(String.format("  %-16s %-28s %s", "制御フロー", "if_icmpeq, goto, return", "条件分岐・ジャンプ"));
    IO.println(String.format("  %-16s %-28s %s", "メソッド呼出", "invokevirtual, invokestatic", "メソッドの呼び出し"));
    IO.println();

    IO.println("=== invoke 命令の使い分け ===\n");

    IO.println(String.format("  %-20s %-16s %s", "命令", "ディスパッチ", "使われる場面"));
    IO.println("  " + "─".repeat(64));
    IO.println(String.format("  %-20s %-16s %s", "invokevirtual", "動的 (vtable)", "通常のメソッド: obj.method()"));
    IO.println(String.format("  %-20s %-16s %s", "invokeinterface", "動的 (itable)", "インタフェース: list.size()"));
    IO.println(String.format("  %-20s %-16s %s", "invokespecial", "静的", "コンストラクタ, private, super"));
    IO.println(String.format("  %-20s %-16s %s", "invokestatic", "静的", "static メソッド: Math.max()"));
    IO.println(String.format("  %-20s %-16s %s", "invokedynamic", "ブートストラップ", "ラムダ式, 文字列結合"));
}
