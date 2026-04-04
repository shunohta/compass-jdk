/**
 * Java 標準ライブラリ内部の native メソッドを探索・可視化するプログラム。
 *
 * <p>Java アプリケーションは「純粋な Java」で動いているように見えるが、実際には
 * ファイル I/O、ネットワーク通信、スレッド管理、時刻取得など、OS の機能を必要とする
 * 処理は JNI (Java Native Interface) 経由でネイティブコードを呼び出している。
 * このプログラムでは、リフレクション API を使って標準ライブラリ内の native メソッドを
 * 発見し、「Java が内部でどれだけネイティブコードに依存しているか」を可視化する。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>{@code native} キーワードの意味 -- JNI 境界を示す宣言</li>
 *   <li>標準ライブラリの主要クラスにおける native メソッドの分布</li>
 *   <li>ファイル I/O・ネットワーク・スレッドが最終的に OS に委譲される仕組み</li>
 *   <li>{@code System.currentTimeMillis()} 等の身近な API が native である事実</li>
 *   <li>FFM API (Foreign Function &amp; Memory API) による JNI 代替の可能性</li>
 * </ul>
 *
 * <h2>対象層</h2>
 * <ul>
 *   <li><strong>Java 層:</strong> {@link java.lang.reflect.Method} によるメタ情報取得</li>
 *   <li><strong>JNI 層:</strong> native 修飾子の検出と意味の解説</li>
 *   <li><strong>HotSpot C++ 層:</strong> {@code src/hotspot/share/prims/jni.cpp} の概念紹介</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java src/main/java/native/JniExplorer.java
 * }</pre>
 *
 * @author jdk-core
 * @see java.lang.reflect.Modifier#isNative(int)
 */


/** カテゴリ別の native メソッド集計用レコード。 */
record NativeMethodStat(String category, String className, int nativeCount, int totalCount) {

    /** native メソッドの割合をパーセントで返す。 */
    double percentage() {
        return totalCount == 0 ? 0.0 : (double) nativeCount / totalCount * 100;
    }
}

/**
 * プログラムのエントリーポイント。
 *
 * <p>4つのセクションで JNI の「見えない活躍」を探索する。
 */
void main() {
    IO.println("=== JNI エクスプローラー -- native メソッドの発見と可視化 ===\n");

    showNativeMethodsByCategory();
    showSystemNativeMethods();
    showFileIoNativeMethods();
    showNativeCallFlow();
}

// ──────────────────────────────────────────────
// セクション1: カテゴリ別 native メソッド統計
// ──────────────────────────────────────────────

/**
 * 主要クラスに含まれる native メソッドの数を集計・表示する。
 *
 * <p>「Java は純粋な Java で動いている」という誤解を解くため、
 * 各クラスにおける native メソッドの割合を可視化する。
 */
void showNativeMethodsByCategory() {
    IO.println("[1] 標準ライブラリの native メソッド統計\n");
    IO.println("    Java の標準クラスには、OS の機能を呼び出すための native メソッドが");
    IO.println("    多数含まれている。リフレクションで探索してみよう。\n");

    // 調査対象のクラス群: カテゴリ → クラスの配列
    record TargetEntry(String category, Class<?>[] classes) {}

    var targets = List.of(
        new TargetEntry("コア (java.lang)",     new Class<?>[]{ Object.class, Class.class, System.class,
                                                                 Runtime.class, Thread.class, String.class }),
        new TargetEntry("I/O (java.io)",        new Class<?>[]{ java.io.FileInputStream.class,
                                                                 java.io.FileOutputStream.class,
                                                                 java.io.FileDescriptor.class }),
        new TargetEntry("NIO (java.nio)",       new Class<?>[]{ java.nio.Buffer.class }),
        new TargetEntry("ネットワーク (java.net)", loadClassesSafely(
                                                     "java.net.InetAddress",
                                                     "java.net.Socket",
                                                     "java.net.NetworkInterface" )),
        new TargetEntry("数学・ユーティリティ",   new Class<?>[]{ Math.class, StrictMath.class }),
        new TargetEntry("リフレクション",         new Class<?>[]{ java.lang.reflect.Array.class })
    );

    IO.println(String.format("    %-28s %-28s %6s / %-6s  %s",
        "カテゴリ", "クラス", "native", "total", "割合"));
    IO.println("    " + "─".repeat(90));

    int grandNative = 0;
    int grandTotal = 0;

    for (TargetEntry target : targets) {
        for (Class<?> clazz : target.classes()) {
            NativeMethodStat stat = analyzeClass(target.category(), clazz);
            String bar = renderBar(stat.percentage(), 20);
            IO.println(String.format("    %-28s %-28s %6d / %-6d  %s %4.1f%%",
                target.category(), stat.className(),
                stat.nativeCount(), stat.totalCount(),
                bar, stat.percentage()));
            grandNative += stat.nativeCount();
            grandTotal += stat.totalCount();
        }
    }

    IO.println("    " + "─".repeat(90));
    double grandPct = grandTotal == 0 ? 0 : (double) grandNative / grandTotal * 100;
    IO.println(String.format("    %-28s %-28s %6d / %-6d        %4.1f%%",
        "合計", "", grandNative, grandTotal, grandPct));
    IO.println();
}

// ──────────────────────────────────────────────
// セクション2: System クラスの native メソッド詳細
// ──────────────────────────────────────────────

/**
 * {@link System} クラスの native メソッドを列挙し、用途を解説する。
 *
 * <p>{@code System.currentTimeMillis()} や {@code System.nanoTime()} など、
 * 日常的に使う API が native であることを確認する。
 */
void showSystemNativeMethods() {
    IO.println("[2] System クラスの native メソッド詳細\n");
    IO.println("    System クラスは JVM の「窓口」として OS と連携する。");
    IO.println("    そのため多くのメソッドが native 実装になっている。\n");

    // System クラスの native メソッドを列挙
    listNativeMethods(System.class);

    // 実際に呼んでみる
    IO.println("    【実測: native メソッドの呼び出し】");
    long millis = System.currentTimeMillis();
    long nanos = System.nanoTime();
    int hash = System.identityHashCode("test");
    IO.println("      currentTimeMillis() = " + millis + " (OS の時刻 API を JNI で呼出)");
    IO.println("      nanoTime()           = " + nanos + " (OS の高精度タイマーを JNI で呼出)");
    IO.println("      identityHashCode()   = " + hash + " (JVM 内部のオブジェクトアドレス系)");
    IO.println();
}

// ──────────────────────────────────────────────
// セクション3: ファイル I/O の native メソッド
// ──────────────────────────────────────────────

/**
 * ファイル I/O 関連クラスの native メソッドを列挙する。
 *
 * <p>{@link java.io.FileInputStream} の {@code read()} や {@code open()} が
 * native であることを確認し、Java のファイル操作が最終的に OS のシステムコール
 * に帰着することを示す。
 */
void showFileIoNativeMethods() {
    IO.println("[3] ファイル I/O の native メソッド\n");
    IO.println("    FileInputStream / FileOutputStream はファイル操作を行うが、");
    IO.println("    「ファイルを読む/書く」こと自体は OS の仕事である。");
    IO.println("    そのため、コアメソッドは native として宣言されている。\n");

    listNativeMethods(java.io.FileInputStream.class);
    listNativeMethods(java.io.FileOutputStream.class);
    listNativeMethods(java.io.FileDescriptor.class);

    IO.println("    【図解: FileInputStream.read() の処理フロー】\n");
    IO.println("      Java 層                  JNI 層               OS / HotSpot C++ 層");
    IO.println("      ─────────────────────────────────────────────────────────────");
    IO.println("      FileInputStream.read()");
    IO.println("            │");
    IO.println("            ▼");
    IO.println("      read0()  [native]  ──▶  JNI 境界越え  ──▶  io_util.c: readSingle()");
    IO.println("                                                       │");
    IO.println("                                                       ▼");
    IO.println("                                                  OS read() syscall");
    IO.println("                                                       │");
    IO.println("                                                       ▼");
    IO.println("                                                  カーネルがディスクから");
    IO.println("                                                  データを読み出す");
    IO.println("                                                       │");
    IO.println("      int (読み取ったバイト) ◀── 戻り値を変換 ◀────────┘");
    IO.println();
}

// ──────────────────────────────────────────────
// セクション4: JNI 呼び出しの全体フロー図
// ──────────────────────────────────────────────

/**
 * JNI 呼び出しの全体フローを ASCII art で図示する。
 *
 * <p>Java のメソッド呼び出しが JNI 境界を越えてネイティブコードに到達する
 * までの各ステップを、「通常の Java 呼び出し」と対比して説明する。
 */
void showNativeCallFlow() {
    IO.println("[4] JNI 呼び出しフローの全体像\n");

    IO.println("    ┌─────────────────────────────────────────────────────────────────┐");
    IO.println("    │  通常の Java メソッド呼び出し vs native メソッド呼び出し          │");
    IO.println("    ├───────────────────────────────┬─────────────────────────────────┤");
    IO.println("    │  通常のメソッド                 │  native メソッド               │");
    IO.println("    ├───────────────────────────────┼─────────────────────────────────┤");
    IO.println("    │  1. スタックフレーム作成        │  1. スタックフレーム作成        │");
    IO.println("    │  2. バイトコード実行            │  2. JNI 境界チェック            │");
    IO.println("    │     (または JIT 機械語)         │  3. 引数の型変換                │");
    IO.println("    │  3. 戻り値をスタックに積む      │     (Java 型 → C/C++ 型)       │");
    IO.println("    │                                │  4. ネイティブ関数を呼び出し     │");
    IO.println("    │                                │  5. 戻り値の型変換              │");
    IO.println("    │                                │     (C/C++ 型 → Java 型)       │");
    IO.println("    │                                │  6. 例外チェック                │");
    IO.println("    │  → JIT でインライン化可能       │  → インライン化不可             │");
    IO.println("    │  → 高速                        │  → オーバーヘッドあり           │");
    IO.println("    └───────────────────────────────┴─────────────────────────────────┘");
    IO.println();

    IO.println("    【身近な native メソッドの利用マップ】\n");
    IO.println("      あなたの Java コード");
    IO.println("           │");
    IO.println("           ├── System.currentTimeMillis()  ──▶ OS 時刻 API");
    IO.println("           ├── System.nanoTime()            ──▶ OS 高精度タイマー");
    IO.println("           ├── new FileInputStream(path)");
    IO.println("           │     └── read()                 ──▶ OS read() syscall");
    IO.println("           ├── new Socket(host, port)");
    IO.println("           │     └── connect()              ──▶ OS socket/connect()");
    IO.println("           ├── Thread.start()               ──▶ OS pthread_create()");
    IO.println("           ├── Object.hashCode()            ──▶ JVM 内部アドレス計算");
    IO.println("           └── Math.sin() / Math.cos()      ──▶ CPU 浮動小数点命令");
    IO.println();

    // Thread クラスの native メソッド
    IO.println("    【Thread クラスの native メソッド】\n");
    listNativeMethods(Thread.class);

    // Object クラスの native メソッド
    IO.println("    【Object クラスの native メソッド】\n");
    listNativeMethods(Object.class);
}

// ──────────────────────────────────────────────
// ヘルパーメソッド
// ──────────────────────────────────────────────

/**
 * 指定クラスの native メソッド数と全メソッド数を集計する。
 *
 * @param category 表示用のカテゴリ名
 * @param clazz    対象クラス
 * @return native メソッドの統計情報
 */
NativeMethodStat analyzeClass(String category, Class<?> clazz) {
    Method[] methods = clazz.getDeclaredMethods();
    int nativeCount = 0;
    for (Method m : methods) {
        if (Modifier.isNative(m.getModifiers())) {
            nativeCount++;
        }
    }
    return new NativeMethodStat(category, clazz.getSimpleName(), nativeCount, methods.length);
}

/**
 * 指定クラスの native メソッドを一覧表示する。
 *
 * @param clazz 対象クラス
 */
void listNativeMethods(Class<?> clazz) {
    IO.println("    " + clazz.getName() + " の native メソッド:");

    Method[] methods = clazz.getDeclaredMethods();
    // native メソッドのみ抽出してソート
    var nativeMethods = Arrays.stream(methods)
        .filter(m -> Modifier.isNative(m.getModifiers()))
        .sorted(Comparator.comparing(Method::getName))
        .toList();

    if (nativeMethods.isEmpty()) {
        IO.println("      (native メソッドなし)");
    } else {
        for (Method m : nativeMethods) {
            String params = Arrays.stream(m.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(java.util.stream.Collectors.joining(", "));
            IO.println("      native " + m.getReturnType().getSimpleName()
                + " " + m.getName() + "(" + params + ")");
        }
    }
    IO.println();
}

/**
 * クラス名の配列から安全にクラスをロードする。
 *
 * <p>ロードに失敗したクラスはスキップする。
 *
 * @param classNames 完全修飾クラス名の可変長引数
 * @return ロードに成功したクラスの配列
 */
Class<?>[] loadClassesSafely(String... classNames) {
    var loaded = new ArrayList<Class<?>>();
    for (String name : classNames) {
        try {
            loaded.add(Class.forName(name));
        } catch (ClassNotFoundException e) {
            // モジュール未解決等でロードできない場合はスキップ
        }
    }
    return loaded.toArray(new Class<?>[0]);
}

/**
 * パーセンテージを棒グラフで表現する。
 *
 * @param pct   パーセンテージ (0-100)
 * @param width 棒の最大文字数
 * @return 棒グラフ文字列（例: "████░░░░░░"）
 */
String renderBar(double pct, int width) {
    int filled = (int) Math.round(pct / 100 * width);
    filled = Math.clamp(filled, 0, width);
    return "█".repeat(filled) + "░".repeat(width - filled);
}
