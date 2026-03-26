/**
 * クラスローダーの3階層と双親委譲モデルを探索・可視化するプログラム。
 *
 * <p>JVM には Bootstrap / Platform / Application の3つの標準クラスローダーが
 * 階層構造で存在する。このプログラムでは各クラスローダーの正体を調べ、
 * 代表的なクラスがどのクラスローダーによってロードされたかを可視化する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>Bootstrap / Platform / Application の3階層とその親子関係</li>
 *   <li>各クラスがどのクラスローダーでロードされたか確認する方法</li>
 *   <li>Bootstrap ClassLoader の {@code getClassLoader()} が null を返す理由</li>
 *   <li>モジュールシステム (JPMS) とクラスローダーの対応関係</li>
 *   <li>Thread Context ClassLoader の役割と確認方法</li>
 *   <li>{@code getResource()} によるクラスのロード元の調べ方</li>
 * </ul>
 *
 * <h2>対象層</h2>
 * <ul>
 *   <li><strong>Java 層:</strong> {@link ClassLoader}, {@link Module}, {@link Class} API</li>
 *   <li><strong>HotSpot C++ 層:</strong> Bootstrap ClassLoader は
 *       {@code src/hotspot/share/classfile/classLoader.cpp} で C++ 実装</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java src/main/java/ClassLoaderExplorer.java
 * }</pre>
 *
 * @author jdk-core
 * @see ClassLoader
 * @see Module
 */

/** クラス情報の表示用レコード。 */
record ClassEntry(String label, Class<?> clazz) {}

/**
 * プログラムのエントリーポイント。
 *
 * <p>4つのセクションでクラスローダーの構造を探索する。
 */
void main() {
    IO.println("=== クラスローダーエクスプローラー ===\n");

    showHierarchy();
    showClassOrigins();
    showModuleRelation();
    showContextClassLoader();
}

// ──────────────────────────────────────────────
// セクション1: 3階層の親チェーン
// ──────────────────────────────────────────────

/**
 * Application ClassLoader から親をたどり、3階層を可視化する。
 *
 * <p>{@link ClassLoader#getSystemClassLoader()} は Application ClassLoader を返す。
 * そこから {@link ClassLoader#getParent()} を繰り返し呼ぶと
 * Platform → null (Bootstrap) の順にたどれる。
 * Bootstrap ClassLoader は C++ 実装であり Java オブジェクトが存在しないため
 * null が返る。
 */
void showHierarchy() {
    IO.println("[1] 3階層のクラスローダー -- 親チェーンの可視化\n");

    // ClassLoader.getSystemClassLoader() で Application CL を取得する。
    // これは java コマンドの -cp / --module-path 上のクラスをロードする。
    var appCL = ClassLoader.getSystemClassLoader();

    // 親チェーンをたどって表示する
    var current = appCL;
    int depth = 0;
    while (current != null) {
        String role = switch (depth) {
            case 0 -> "Application ClassLoader (自分のクラス)";
            case 1 -> "Platform ClassLoader (java.sql 等)";
            default -> "不明";
        };
        IO.println("  " + "│ ".repeat(depth) + "├─ " + current.getClass().getName());
        IO.println("  " + "│ ".repeat(depth) + "│    役割: " + role);
        current = current.getParent();
        depth++;
    }
    // Bootstrap CL は null として返る
    IO.println("  " + "│ ".repeat(depth) + "└─ null (Bootstrap ClassLoader)");
    IO.println("  " + "│ ".repeat(depth) + "     役割: java.base のコアクラス (C++ 実装)");
    IO.println();
}

// ──────────────────────────────────────────────
// セクション2: 代表クラスのロード元
// ──────────────────────────────────────────────

/**
 * 代表的なクラスがどのクラスローダーでロードされたかを一覧表示する。
 *
 * <p>Bootstrap CL でロードされたクラスの {@code getClassLoader()} は null を返す。
 * これは Bootstrap CL が Java オブジェクトではなく C++ の内部実装であるため。
 */
void showClassOrigins() {
    IO.println("[2] 代表クラスのロード元\n");

    IO.println(String.format("  %-32s %-24s %s", "クラス", "クラスローダー", "モジュール"));
    IO.println("  " + "─".repeat(80));

    // ── Bootstrap ClassLoader がロードするクラス ──
    // java.base モジュールのコアクラスはすべて Bootstrap CL
    var bootstrapClasses = List.of(
        new ClassEntry("java.lang.String", String.class),
        new ClassEntry("java.lang.Object", Object.class),
        new ClassEntry("java.util.ArrayList", ArrayList.class),
        new ClassEntry("java.util.HashMap", HashMap.class)
    );
    for (ClassEntry entry : bootstrapClasses) {
        printClassLine(entry.label(), entry.clazz());
    }

    // ── Platform ClassLoader がロードするクラス ──
    // java.sql, java.xml 等の java.base 以外のモジュール
    tryShowClass("javax.sql.DataSource");
    tryShowClass("javax.xml.parsers.DocumentBuilder");

    // ── Application ClassLoader がロードするクラス ──
    // 本プログラム自身（暗黙クラス）
    var thisClass = MethodHandles.lookup().lookupClass();
    printClassLine("(本プログラム)", thisClass);

    IO.println();
}

/** クラス名から Class.forName で動的にロードし、情報を表示する。 */
void tryShowClass(String fqcn) {
    try {
        printClassLine(fqcn, Class.forName(fqcn));
    } catch (ClassNotFoundException e) {
        IO.println(String.format("  %-32s (モジュール未解決)", fqcn));
    }
}

/** 1行分のクラス情報をフォーマットして出力する。 */
void printClassLine(String label, Class<?> clazz) {
    var cl = clazz.getClassLoader();
    String clName;
    if (cl == null) {
        clName = "null (Bootstrap)";
    } else {
        // 実装クラス名からパッケージを除いた短縮名を表示
        String full = cl.getClass().getName();
        clName = full.substring(full.lastIndexOf('.') + 1);
    }

    String moduleName = clazz.getModule().getName();
    if (moduleName == null) moduleName = "(unnamed)";

    IO.println(String.format("  %-32s %-24s %s", label, clName, moduleName));
}

// ──────────────────────────────────────────────
// セクション3: モジュールとクラスローダーの関係
// ──────────────────────────────────────────────

/**
 * モジュールレイヤーから、各モジュールがどのクラスローダーに属するか表示する。
 *
 * <p>Java 9 以降、クラスは必ずモジュールに属する。{@link ModuleLayer#boot()} から
 * ブートレイヤーのモジュール一覧を取得し、クラスローダーとの対応を確認できる。
 */
void showModuleRelation() {
    IO.println("[3] モジュールとクラスローダーの関係\n");
    IO.println("  ブートレイヤーの主要モジュール:\n");

    // 表示するモジュール名を絞り込む（全量だと多すぎるため）
    var interesting = Set.of(
        "java.base", "java.sql", "java.xml",
        "java.logging", "java.management", "java.compiler"
    );

    ModuleLayer.boot().modules().stream()
        .filter(m -> interesting.contains(m.getName()))
        .sorted(Comparator.comparing(Module::getName))
        .forEach(m -> {
            var cl = m.getClassLoader();
            String clName = (cl == null) ? "Bootstrap" : cl.getClass().getSimpleName();
            IO.println(String.format("    %-20s → %s", m.getName(), clName));
        });

    IO.println();
}

// ──────────────────────────────────────────────
// セクション4: Thread Context ClassLoader
// ──────────────────────────────────────────────

/**
 * Thread Context ClassLoader の役割と現在の設定を確認する。
 *
 * <p>SPI (Service Provider Interface) パターンでは、Bootstrap CL でロードされた
 * コアクラス（例: {@code DriverManager}）がアプリケーション側の実装クラスを
 * ロードする必要がある。通常の双親委譲では親→子のクラスは不可視であるため、
 * Thread Context ClassLoader で迂回する。
 */
void showContextClassLoader() {
    IO.println("[4] Thread Context ClassLoader\n");

    var contextCL = Thread.currentThread().getContextClassLoader();
    var systemCL = ClassLoader.getSystemClassLoader();

    IO.println("  Thread Context CL: " + contextCL.getClass().getName());
    IO.println("  System CL:         " + systemCL.getClass().getName());
    IO.println("  同一インスタンス?:  " + (contextCL == systemCL));
    IO.println();

    // ── なぜ必要かの図解 ──
    IO.println("  【双親委譲の限界と Context CL の役割】");
    IO.println("  ┌─ Bootstrap CL ────────────────────────────────┐");
    IO.println("  │ DriverManager (SPI インタフェース側)            │");
    IO.println("  │   → 「JDBC ドライバの実装をロードしたい...」    │");
    IO.println("  │   → 子のクラスは見えない！                     │");
    IO.println("  │   → Thread.currentThread()                    │");
    IO.println("  │      .getContextClassLoader() で迂回           │");
    IO.println("  └───────────────────────────────────────────────┘");
    IO.println();

    // ── デバッグ用: クラスのロード元を調べる方法 ──
    IO.println("  【デバッグTips: クラスのロード元を調べる】");
    var resource = ClassLoader.getSystemResource(
        "java/lang/String.class"
    );
    IO.println("  String.class の場所: " + resource);
    IO.println("  (jrt:/ は JDK のランタイムイメージ内を示す)");
}
