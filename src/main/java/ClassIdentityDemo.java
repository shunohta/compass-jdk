/**
 * クラスの同一性とカスタムクラスローダーを実験するプログラム。
 *
 * <p>JVM において、クラスの同一性は「完全修飾クラス名 + クラスローダーインスタンス」で
 * 決まる。同じ名前の .class ファイルでも、異なるクラスローダーでロードすれば
 * JVM にとっては「別の型」になる。このプログラムでは、バイトコードを手動生成して
 * 2つのカスタムクラスローダーから同名クラスをロードし、型の不一致を体感する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>.class ファイルのバイナリ構造（マジックナンバー 0xCAFEBABE）</li>
 *   <li>カスタム {@link ClassLoader} の実装方法（{@code findClass} と {@code defineClass}）</li>
 *   <li>同名クラスでもクラスローダーが異なれば別の型として扱われること</li>
 *   <li>{@link ClassCastException} が「同じクラス名なのに」発生するシナリオ</li>
 *   <li>アプリケーションサーバー（Tomcat 等）がクラスを分離する仕組みの基盤</li>
 * </ul>
 *
 * <h2>対象層</h2>
 * <ul>
 *   <li><strong>Java 層:</strong> {@link ClassLoader#defineClass} によるクラスの動的定義</li>
 *   <li><strong>HotSpot C++ 層:</strong> {@code SystemDictionary}
 *       ({@code src/hotspot/share/classfile/systemDictionary.cpp}) が
 *       「クラス名 + ClassLoader」のペアでクラスを管理</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java src/main/java/ClassIdentityDemo.java
 * }</pre>
 *
 * @author jdk-core
 * @see ClassLoader#defineClass(String, byte[], int, int)
 * @see ClassLoader#findClass(String)
 */

// ──────────────────────────────────────────────
// カスタムクラスローダー
//
// 渡されたバイトコード（byte[]）からクラスを定義する。
// super(null) で親を null にすることで、Bootstrap CL への
// 委譲のみ行い、同じ名前のクラスが Application CL 経由で
// 見つかることを防ぐ。
// ──────────────────────────────────────────────

/**
 * バイトコードを直接受け取ってクラスを定義するカスタムクラスローダー。
 *
 * <p>コンストラクタで渡された {@code className} と一致する名前が要求された場合に、
 * {@code classBytes} から {@link ClassLoader#defineClass} でクラスを定義する。
 * 親に null を指定しているため、Bootstrap CL にのみ委譲する（Application CL は使わない）。
 *
 * @param loaderName  デバッグ用のローダー識別名
 * @param className   ロード対象のクラスの完全修飾名
 * @param classBytes  .class ファイルのバイト列
 */
record BytecodeLoader(String loaderName, String className, byte[] classBytes) {

    /**
     * ClassLoader を生成して返す。
     *
     * <p>record は ClassLoader を直接継承できないため、
     * 匿名クラスとして ClassLoader のサブクラスを生成する。
     *
     * @return カスタム ClassLoader インスタンス
     */
    ClassLoader toClassLoader() {
        return new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(className)) {
                    return defineClass(name, classBytes, 0, classBytes.length);
                }
                throw new ClassNotFoundException(name);
            }

            @Override
            public String toString() {
                return "BytecodeLoader[" + loaderName + "]";
            }
        };
    }
}

/**
 * プログラムのエントリーポイント。
 *
 * <p>バイトコードを手動生成し、2つの異なるクラスローダーから同名クラスを
 * ロードしてクラスの同一性を実験する。
 */
void main() throws Exception {
    IO.println("=== クラスの同一性 実験 ===\n");

    showBytecodeStructure();
    experimentClassIdentity();
    showPracticalImplication();
}

// ──────────────────────────────────────────────
// セクション1: .class ファイルのバイナリ構造
// ──────────────────────────────────────────────

/**
 * .class ファイルのバイナリ構造を解説し、実際に生成したバイト列を表示する。
 */
void showBytecodeStructure() {
    IO.println("[1] .class ファイルのバイナリ構造\n");
    IO.println("  HotSpot の ClassFileParser は .class ファイルを以下の順序でパースする:");
    IO.println("  (src/hotspot/share/classfile/classFileParser.cpp)\n");
    IO.println("  ┌─────────────────────────────────────────────────────────┐");
    IO.println("  │  0xCAFEBABE          マジックナンバー（必須）           │");
    IO.println("  │  バージョン番号      (例: 65.0 = Java 21)              │");
    IO.println("  │  定数プール          クラス名、メソッド名、文字列等    │");
    IO.println("  │  アクセスフラグ      public, final, abstract 等        │");
    IO.println("  │  クラス/親クラス     this_class, super_class           │");
    IO.println("  │  インタフェース群    implements 一覧                   │");
    IO.println("  │  フィールド群        フィールド定義                    │");
    IO.println("  │  メソッド群          メソッド定義 + バイトコード       │");
    IO.println("  │  アトリビュート群    アノテーション等の付加情報        │");
    IO.println("  └─────────────────────────────────────────────────────────┘\n");

    byte[] classBytes = generateMinimalClass("Target");
    IO.println("  生成したクラスのサイズ: " + classBytes.length + " バイト");

    // 先頭のマジックナンバーを確認
    IO.println("  先頭4バイト: " + String.format("0x%02X%02X%02X%02X",
        classBytes[0], classBytes[1], classBytes[2], classBytes[3]));
    IO.println("  → 0xCAFEBABE (Java の開発チームがコーヒー好きだったことに由来)\n");
}

// ──────────────────────────────────────────────
// セクション2: クラスの同一性実験
// ──────────────────────────────────────────────

/**
 * 同名クラスを2つの異なるクラスローダーからロードし、型の同一性を実験する。
 */
void experimentClassIdentity() throws Exception {
    IO.println("[2] クラスの同一性実験 -- 同名クラスが「別の型」になる\n");

    // 同じバイトコードから2つのクラスローダーを作成
    byte[] classBytes = generateMinimalClass("Target");
    var loader1 = new BytecodeLoader("Loader-A", "Target", classBytes).toClassLoader();
    var loader2 = new BytecodeLoader("Loader-B", "Target", classBytes).toClassLoader();

    // 各クラスローダーで "Target" をロード
    Class<?> class1 = loader1.loadClass("Target");
    Class<?> class2 = loader2.loadClass("Target");

    IO.println("  クラス名の比較:");
    IO.println("    class1.getName() = " + class1.getName());
    IO.println("    class2.getName() = " + class2.getName());
    IO.println("    名前は同じ!\n");

    IO.println("  クラスローダーの比較:");
    IO.println("    class1.getClassLoader() = " + class1.getClassLoader());
    IO.println("    class2.getClassLoader() = " + class2.getClassLoader());
    IO.println("    異なるクラスローダー!\n");

    // ── 型の同一性チェック ──
    IO.println("  型の同一性 (class1 == class2): " + (class1 == class2));
    IO.println("  → false！同名でもクラスローダーが違えば別の型\n");

    // ── instanceof チェック ──
    Object obj1 = class1.getDeclaredConstructor().newInstance();
    IO.println("  class1 のインスタンスを生成: " + obj1.getClass().getName());
    IO.println("  class1.isInstance(obj1) = " + class1.isInstance(obj1) + " (当然 true)");
    IO.println("  class2.isInstance(obj1) = " + class2.isInstance(obj1) + " (false！)");
    IO.println("  → 同じ名前 \"Target\" なのに、異なる型として判定される\n");

    // ── ClassCastException の発生 ──
    IO.println("  class2.cast(obj1) を試行...");
    try {
        class2.cast(obj1);
    } catch (ClassCastException e) {
        IO.println("  → ClassCastException: " + e.getMessage());
        IO.println("    同名クラスなのにキャストできない！\n");
    }

    // ── HotSpot 内部の表現 ──
    IO.println("  【HotSpot 内部 (SystemDictionary) での管理】");
    IO.println("  ┌────────────────────────────────────┬───────────────────┐");
    IO.println("  │ キー (クラス名 + ClassLoader)       │ 値 (InstanceKlass) │");
    IO.println("  ├────────────────────────────────────┼───────────────────┤");
    IO.println("  │ (\"Target\", Loader-A)               │ → Klass* 0x...A  │");
    IO.println("  │ (\"Target\", Loader-B)               │ → Klass* 0x...B  │");
    IO.println("  └────────────────────────────────────┴───────────────────┘");
    IO.println("  → 同名でも ClassLoader が違えば SystemDictionary の別エントリ\n");
}

// ──────────────────────────────────────────────
// セクション3: 実務での応用
// ──────────────────────────────────────────────

/** クラスの同一性がどのような場面で活用されるか解説する。 */
void showPracticalImplication() {
    IO.println("[3] 実務での応用\n");
    IO.println("  この仕組みが使われている場面:");
    IO.println("  ┌──────────────────────────────────────────────────────┐");
    IO.println("  │ アプリケーションサーバー (Tomcat, Jetty)              │");
    IO.println("  │   各 Web アプリに専用の ClassLoader を割り当てる      │");
    IO.println("  │   → 同名クラスがあっても衝突しない                   │");
    IO.println("  │                                                      │");
    IO.println("  │ Spring Boot DevTools                                 │");
    IO.println("  │   古い ClassLoader を破棄し、新しい CL で再ロード     │");
    IO.println("  │   → JVM 再起動なしでクラスを更新できる               │");
    IO.println("  │                                                      │");
    IO.println("  │ IDE プラグインシステム (Eclipse, IntelliJ)            │");
    IO.println("  │   各プラグインを独自の ClassLoader で隔離する         │");
    IO.println("  │   → プラグイン間の干渉を防ぐ                        │");
    IO.println("  └──────────────────────────────────────────────────────┘");
}

// ──────────────────────────────────────────────
// バイトコード生成
//
// .class ファイルのバイナリ構造に従い、
// 最小限の有効なクラスファイルをプログラムで生成する。
// これにより ClassFileParser が解析する構造を直接体験できる。
// ──────────────────────────────────────────────

/**
 * 指定した名前の最小限の .class ファイル（バイトコード）を生成する。
 *
 * <p>生成されるクラスは {@code public class <className> extends Object}
 * に相当し、デフォルトコンストラクタ（{@code <init>}）のみを持つ。
 *
 * <p>定数プールの構成:
 * <pre>
 * #1: Methodref  → #2.#3 (Object.&lt;init&gt;)
 * #2: Class      → #4    (java/lang/Object)
 * #3: NameAndType → #5:#6 (&lt;init&gt;:()V)
 * #4: Utf8       "java/lang/Object"
 * #5: Utf8       "&lt;init&gt;"
 * #6: Utf8       "()V"
 * #7: Class      → #8    (className)
 * #8: Utf8       className
 * #9: Utf8       "Code"
 * </pre>
 *
 * @param className クラス名（パッケージなしの単純名）
 * @return .class ファイルの完全なバイト列
 */
byte[] generateMinimalClass(String className) {
    // クラス名を内部形式に変換（. → /）
    byte[] nameBytes = className.replace('.', '/').getBytes(StandardCharsets.UTF_8);

    var buf = ByteBuffer.allocate(256);

    // ── マジックナンバー: すべての .class ファイルの先頭 ──
    buf.putInt(0xCAFEBABE);

    // ── バージョン: Java 21 (65.0) ──
    buf.putShort((short) 0);    // minor_version
    buf.putShort((short) 65);   // major_version

    // ── 定数プール (9 エントリ → count = 10) ──
    // JVM 仕様では定数プールのインデックスは 1 から始まる
    buf.putShort((short) 10);   // constant_pool_count

    // #1: CONSTANT_Methodref → Object.<init>()V
    buf.put((byte) 10);         // tag: Methodref
    buf.putShort((short) 2);    // class_index: #2
    buf.putShort((short) 3);    // name_and_type_index: #3

    // #2: CONSTANT_Class → java/lang/Object
    buf.put((byte) 7);          // tag: Class
    buf.putShort((short) 4);    // name_index: #4

    // #3: CONSTANT_NameAndType → <init>:()V
    buf.put((byte) 12);         // tag: NameAndType
    buf.putShort((short) 5);    // name_index: #5
    buf.putShort((short) 6);    // descriptor_index: #6

    // #4: CONSTANT_Utf8 → "java/lang/Object"
    putUtf8(buf, "java/lang/Object");

    // #5: CONSTANT_Utf8 → "<init>"
    putUtf8(buf, "<init>");

    // #6: CONSTANT_Utf8 → "()V"
    putUtf8(buf, "()V");

    // #7: CONSTANT_Class → (生成するクラス名)
    buf.put((byte) 7);          // tag: Class
    buf.putShort((short) 8);    // name_index: #8

    // #8: CONSTANT_Utf8 → className
    putUtf8(buf, className);

    // #9: CONSTANT_Utf8 → "Code"
    putUtf8(buf, "Code");

    // ── アクセスフラグ: ACC_PUBLIC | ACC_SUPER ──
    buf.putShort((short) 0x0021);

    // ── this_class: #7, super_class: #2 ──
    buf.putShort((short) 7);    // this_class
    buf.putShort((short) 2);    // super_class (Object)

    // ── インタフェース: なし ──
    buf.putShort((short) 0);

    // ── フィールド: なし ──
    buf.putShort((short) 0);

    // ── メソッド: デフォルトコンストラクタ 1つ ──
    buf.putShort((short) 1);    // methods_count

    // public void <init>() { super(); }
    buf.putShort((short) 0x0001); // access_flags: ACC_PUBLIC
    buf.putShort((short) 5);      // name_index: #5 (<init>)
    buf.putShort((short) 6);      // descriptor_index: #6 (()V)
    buf.putShort((short) 1);      // attributes_count: 1 (Code)

    // Code 属性
    buf.putShort((short) 9);      // attribute_name_index: #9 ("Code")
    buf.putInt(17);               // attribute_length
    buf.putShort((short) 1);      // max_stack: 1
    buf.putShort((short) 1);      // max_locals: 1 (this)
    buf.putInt(5);                // code_length

    // バイトコード: aload_0 → invokespecial Object.<init> → return
    buf.put((byte) 0x2A);        // aload_0 (this をスタックに積む)
    buf.put((byte) 0xB7);        // invokespecial
    buf.putShort((short) 1);     // → #1 (Object.<init>)
    buf.put((byte) 0xB1);        // return

    buf.putShort((short) 0);     // exception_table_length: 0
    buf.putShort((short) 0);     // code_attributes_count: 0

    // ── クラス属性: なし ──
    buf.putShort((short) 0);

    // ByteBuffer の書き込み位置までを byte[] として返す
    byte[] result = new byte[buf.position()];
    buf.flip();
    buf.get(result);
    return result;
}

/**
 * CONSTANT_Utf8 エントリを ByteBuffer に書き込む。
 *
 * @param buf   書き込み先
 * @param value UTF-8 文字列
 */
void putUtf8(ByteBuffer buf, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    buf.put((byte) 1);                  // tag: Utf8
    buf.putShort((short) bytes.length); // length
    buf.put(bytes);                     // bytes
}
