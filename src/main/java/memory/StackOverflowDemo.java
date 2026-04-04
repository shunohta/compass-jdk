/**
 * StackOverflowError の発生を実際に体験するプログラム。
 *
 * <p>無限再帰（終了条件のない再帰呼び出し）を意図的に行い、
 * スタック領域が枯渇して {@link StackOverflowError} が発生する様子を観察する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>再帰呼び出しのたびにスタックフレームが積まれ、スタック領域を消費する</li>
 *   <li>スタックサイズは有限であり、{@code -Xss} フラグで制御できる</li>
 *   <li>スタックが満杯になると {@link StackOverflowError} がスローされる</li>
 *   <li>{@code -Xss} を小さくすると、より少ない再帰回数でエラーが発生する</li>
 * </ul>
 *
 * <h2>なぜ StackOverflowError が起きるのか</h2>
 * <p>各スレッドのスタックサイズは、スレッド生成時に固定で確保される
 * （HotSpot C++ 層の {@code os::create_thread} 内で {@code pthread_attr_setstacksize} 等を使用）。
 * メソッドを呼び出すたびに新しいフレームが積まれるが、スタックの天井に到達すると
 * これ以上フレームを積めなくなり、JVM が {@link StackOverflowError} をスローする。
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * # デフォルトのスタックサイズで実行
 * java src/main/java/memory/StackOverflowDemo.java
 *
 * # スタックサイズを 256KB に制限して、早くオーバーフローさせる
 * java -Xss256k src/main/java/memory/StackOverflowDemo.java
 * }</pre>
 *
 * @author jdk-core
 * @see StackOverflowError
 */

// ──────────────────────────────────────────────
// 再帰の深さを記録するカウンター。
// JEP 477 の暗黙クラスでは、トップレベルのフィールド宣言が可能。
// この変数は暗黙クラスのインスタンスフィールドとして扱われる。
// ──────────────────────────────────────────────
int depth = 0;

/**
 * プログラムのエントリーポイント。
 *
 * <p>無限再帰を呼び出し、{@link StackOverflowError} を {@code catch} して
 * 到達した深さを表示する。Error は通常 catch すべきではないが、
 * 学習目的でここでは意図的に catch している。
 */
void main() {
    IO.println("=== StackOverflowError デモ ===");
    IO.println("スタックサイズ: -Xss フラグで変更可能（-Xss256k で素早くオーバーフロー）\n");

    try {
        infiniteRecursion();
    } catch (StackOverflowError e) {
        // StackOverflowError は Error のサブクラス。
        // 通常のアプリケーションでは catch せず、再帰のロジックを修正すべき。
        // ここでは学習のために意図的に catch している。
        IO.println("StackOverflowError を深さ " + depth + " でキャッチしました");
        IO.println("再帰呼び出しのたびに新しいフレームがスタックに積まれました。");
        IO.println("スタックは固定サイズ(-Xss)なので、領域が尽きてエラーになりました。");
    }
}

/**
 * 終了条件のない再帰メソッド。
 *
 * <p>呼び出されるたびに {@code depth} をインクリメントし、
 * 自分自身を再度呼び出す。各呼び出しで新しいスタックフレームが積まれるため、
 * いずれスタック領域が枯渇して {@link StackOverflowError} が発生する。
 *
 * <p><strong>注意:</strong> これは「やってはいけないコード」の見本。
 * 実際のプログラムでは必ず再帰の終了条件を設けること。
 */
void infiniteRecursion() {
    depth++;
    // 自分自身を呼び出す。新しいフレームがスタックに push される。
    infiniteRecursion();
}
