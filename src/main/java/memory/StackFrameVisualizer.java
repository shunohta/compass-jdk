/**
 * スタックフレームの可視化プログラム。
 *
 * <p>メソッドを連鎖的に呼び出すことで、スタック上にフレームが積み上がる様子を
 * 視覚的に確認する。Java 9 以降の {@link StackWalker} API を使って、
 * 実際のスタックフレーム情報をプログラムから取得する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>メソッド呼び出しのたびにスタックフレームが新たに積まれる（LIFO構造）</li>
 *   <li>各フレームにはローカル変数・戻りアドレス等が格納される</li>
 *   <li>{@link StackWalker} API でスタックフレームをプログラムから走査できる</li>
 *   <li>メソッドが終了するとフレームは自動的に取り除かれる（GC不要）</li>
 * </ul>
 *
 * <h2>スタックの積まれ方</h2>
 * <pre>{@code
 * main() が methodA() を呼ぶ → methodA() が methodB() を呼ぶ → methodB() が methodC() を呼ぶ
 *
 * ┌──────────────────────┐
 * │ methodC() のフレーム  │ ← 一番上（現在実行中）
 * ├──────────────────────┤
 * │ methodB() のフレーム  │
 * ├──────────────────────┤
 * │ methodA() のフレーム  │
 * ├──────────────────────┤
 * │ main() のフレーム     │ ← 一番下（最初に積まれた）
 * └──────────────────────┘
 * }</pre>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java src/main/java/memory/StackFrameVisualizer.java
 * }</pre>
 *
 * @author jdk-core
 * @see StackWalker
 * @see StackWalker.StackFrame
 */

/**
 * プログラムのエントリーポイント。
 *
 * <p>ここから methodA → methodB → methodC と順に呼び出し、
 * スタックフレームが積み上がる過程を観察する。
 */
void main() {
    IO.println("=== スタックフレーム可視化 ===\n");
    methodA();
}

/**
 * 呼び出しチェーンの最初のメソッド。
 *
 * <p>ローカル変数 {@code localA} は、このメソッドのスタックフレーム上に格納される。
 * メソッドが終了すれば、このフレームごと自動的に破棄される。
 */
void methodA() {
    // localA はこのメソッドのスタックフレーム上に存在する
    int localA = 100;
    IO.println("methodA: localA = " + localA);
    printCurrentStack("methodA");

    // methodB を呼び出すと、新しいフレームがスタック上に積まれる
    methodB();
}

/**
 * 呼び出しチェーンの 2 番目のメソッド。
 *
 * <p>methodA のフレームの上に、methodB のフレームが積まれる。
 * methodA のローカル変数はまだスタック上に存在している（methodA は終了していないため）。
 */
void methodB() {
    // localB は methodB 専用のフレームに格納される
    int localB = 200;
    IO.println("\nmethodB: localB = " + localB);
    printCurrentStack("methodB");

    // さらに methodC を呼び出す
    methodC();
}

/**
 * 呼び出しチェーンの最深部。ここで StackWalker を使ってスタック全体を走査する。
 *
 * <p>{@link StackWalker} は Java 9 で導入された API で、
 * 従来の {@code Thread.getStackTrace()} よりも効率的にスタックを走査できる。
 * 内部的には、HotSpot C++ 層でスタックポインタを辿ってフレーム情報を収集する。
 */
void methodC() {
    // localC は methodC 専用のフレームに格納される
    int localC = 300;
    IO.println("\nmethodC: localC = " + localC);
    printCurrentStack("methodC");

    IO.println("\n--- スタックフレーム一覧（深い順）---");

    // StackWalker API でスタック上の全フレームを走査する。
    // forEach に渡される各 frame は StackWalker.StackFrame インスタンス。
    // メソッド名、ファイル名、行番号などの情報を取得できる。
    StackWalker.getInstance().forEach(frame ->
        IO.println("  " + frame.getMethodName()
            + " (" + frame.getFileName() + ":" + frame.getLineNumber() + ")")
    );
}

/**
 * 現在どのメソッドのフレームがスタックの最上部にあるかを表示するヘルパー。
 *
 * @param methodName 表示するメソッド名
 */
void printCurrentStack(String methodName) {
    IO.println("  [" + methodName + " のフレームがスタック最上部にあります]");
}
