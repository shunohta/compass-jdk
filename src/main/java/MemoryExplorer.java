/**
 * JVM メモリ可視化ツール -- スタックとヒープの対応関係を ASCII アートで描画する。
 *
 * <p>Java は設計上、生のメモリアドレスを公開しない。しかし、
 * {@link System#identityHashCode(Object)} を疑似アドレスとして活用し、
 * スタック上のローカル変数とヒープ上のオブジェクトの対応関係を
 * 矢印付きの ASCII アートで可視化することはできる。
 *
 * <h2>6 つのステップで学べること</h2>
 * <ol>
 *   <li>プリミティブ型の値はスタック上に直接格納される（ヒープは空）</li>
 *   <li>オブジェクト生成時、スタックには参照、ヒープには本体が置かれる</li>
 *   <li>配列もオブジェクトであり、ヒープに配置される</li>
 *   <li>メソッド呼び出しでスタックフレームが積み上がる（{@link StackWalker} で確認）</li>
 *   <li>同一オブジェクトを複数の変数から参照できる（参照の共有）</li>
 *   <li>参照を null にすると、GC の回収対象になる</li>
 * </ol>
 *
 * <h2>技術的背景</h2>
 * <p>{@code System.identityHashCode()} は、オブジェクトのデフォルトハッシュコードを返す。
 * HotSpot の実装では、これはオブジェクトヘッダ（mark word）に格納される値であり、
 * 実際のメモリアドレスそのものではないが、オブジェクトを一意に識別する
 * 「疑似アドレス」として十分に機能する。
 *
 * <h2>対象層</h2>
 * <ul>
 *   <li><strong>Java 層:</strong> IO.println、record、StackWalker などの API 利用</li>
 *   <li><strong>HotSpot C++ 層:</strong> identityHashCode は
 *       {@code src/hotspot/share/oops/markWord.hpp} 内の mark word から取得される</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java src/main/java/MemoryExplorer.java
 * }</pre>
 *
 * @author jdk-core
 * @see System#identityHashCode(Object)
 * @see StackWalker
 */

// -- 可視化エンジン用データモデル --

/**
 * スタックフレーム上のローカル変数を表すレコード。
 *
 * <p>スタック上のローカル変数は、プリミティブ型なら値そのものを、
 * 参照型ならヒープ上のオブジェクトを指す疑似アドレスを保持する。
 *
 * @param name    変数名（例: "count"）
 * @param value   表示用の値（例: "42" や "@0x1a2b3c"）
 * @param type    型ラベル（例: "int", "ref"）
 * @param heapRef この変数が参照するヒープオブジェクトの疑似アドレス。プリミティブ型の場合は null
 */
record StackVar(String name, String value, String type, String heapRef) {}

/**
 * ヒープ上に存在するオブジェクトを表すレコード。
 *
 * <p>Java のオブジェクトは全てヒープ領域に配置される。
 * このレコードは、疑似アドレスと人間が読める説明文をペアで保持し、
 * メモリマップの描画に使用される。
 *
 * @param address     疑似アドレス文字列（例: "@0x1a2b3c"）
 * @param description 人間が読める説明文（例: "Point { x=10, y=20 }"）
 */
record HeapObj(String address, String description) {}

/**
 * スタックフレーム1つ分の情報を表すレコード。フレーム名とローカル変数の一覧を保持する。
 *
 * <p>メソッドが呼び出されるたびに新しいスタックフレームが作られ、
 * そのフレーム内で宣言されたローカル変数が格納される。
 * メソッドが終了するとフレームは自動的に破棄される。
 *
 * @param frameName フレーム名（例: "main"）
 * @param vars      このフレーム内のローカル変数一覧
 */
record FrameInfo(String frameName, List<StackVar> vars) {}

/**
 * デモンストレーション用のサンプルヒープオブジェクトとして使用する座標レコード。
 *
 * <p>レコード（record）は Java 16 で正式導入された不変データキャリアで、
 * コンストラクタ・アクセサ・equals/hashCode/toString を自動生成する。
 * ここでは、ヒープに配置されるオブジェクトの具体例として利用している。
 *
 * @param x X 座標
 * @param y Y 座標
 */
record Point(int x, int y) {}


// ─────────────────────────────────────────────────────────────
// ASCII アート描画用ヘルパーメソッド群
// ─────────────────────────────────────────────────────────────

/**
 * オブジェクトを "@0x1a2b3c" 形式の疑似アドレス文字列に変換する。
 *
 * <p>{@link System#identityHashCode(Object)} を 16 進数に変換して使用する。
 * これは実際のメモリアドレスではないが、オブジェクトを一意に識別する値として
 * 十分に機能する。HotSpot 内部では、オブジェクトヘッダの mark word に格納される。
 */
String pseudoAddr(Object obj) {
    return "@0x" + Integer.toHexString(System.identityHashCode(obj));
}

/**
 * スタックフレームを左側、ヒープオブジェクトを右側に配置したメモリマップを描画する。
 *
 * <p>スタック上の参照型変数からヒープ上のオブジェクトへの対応関係を
 * 矢印（------&gt;）で接続して視覚的に表現する。
 * これにより、「参照はスタックに、オブジェクト本体はヒープに」という
 * Java メモリモデルの基本構造を直感的に理解できる。
 *
 * @param frames   スタックフレームの順序付きリスト（インデックス 0 が最下段）
 * @param heapObjs 現在ヒープ上に存在するオブジェクト群
 * @param notes    ダイアグラム下部に表示する補足ノート（省略可）
 */
void drawMemoryMap(List<FrameInfo> frames, List<HeapObj> heapObjs, List<String> notes) {
    // -- 列幅の計測 --
    int stackContentWidth = 34; // スタック側の最小内部幅
    int heapContentWidth = 34;  // ヒープ側の最小内部幅

    // コンテンツから実際に必要な幅を算出
    for (FrameInfo frame : frames) {
        for (StackVar v : frame.vars()) {
            // 書式: "  name  : value     [type]  "
            int lineLen = formatStackVar(v).length();
            stackContentWidth = Math.max(stackContentWidth, lineLen);
        }
        // フレームヘッダー
        int headerLen = (" " + frame.frameName() + " のフレーム ").length() + 6;
        stackContentWidth = Math.max(stackContentWidth, headerLen);
    }
    for (HeapObj h : heapObjs) {
        int lineLen = ("  " + h.address() + "  " + h.description() + "  ").length();
        heapContentWidth = Math.max(heapContentWidth, lineLen);
    }

    // スタックとヒープを繋ぐ矢印ブリッジ
    String arrow = "------>"; // ボックス枠線の外側に表示される
    int arrowLen = arrow.length();

    // -- スタック列の行を構築し、どの行が参照を持つか追跡する --
    var stackLines = new ArrayList<String>();
    var isRefLine = new ArrayList<Boolean>(); // 並列リスト: この行がヒープ参照を持つなら true

    // フレームを上から下へ描画（表示用に逆順: 最上位フレームが先頭）
    for (int fi = frames.size() - 1; fi >= 0; fi--) {
        FrameInfo frame = frames.get(fi);
        String header = " " + frame.frameName() + " のフレーム ";
        // このフレーム区間の上端ボーダー
        if (fi == frames.size() - 1) {
            stackLines.add(topBorder("スタック (" + header + ")", stackContentWidth));
        } else {
            stackLines.add(frameSeparator(header, stackContentWidth));
        }
        isRefLine.add(false);

        stackLines.add(box("", stackContentWidth));
        isRefLine.add(false);

        for (StackVar v : frame.vars()) {
            stackLines.add(box(formatStackVar(v), stackContentWidth));
            isRefLine.add(v.heapRef() != null);
        }
        stackLines.add(box("", stackContentWidth));
        isRefLine.add(false);
    }
    // 下端ボーダー
    stackLines.add(bottomBorder(stackContentWidth));
    isRefLine.add(false);

    // -- ヒープ列の行を構築 --
    var heapLines = new ArrayList<String>();
    heapLines.add(topBorder("ヒープ", heapContentWidth));
    heapLines.add(box("", heapContentWidth));
    if (heapObjs.isEmpty()) {
        heapLines.add(box("  (まだ何もない)", heapContentWidth));
    } else {
        for (HeapObj h : heapObjs) {
            heapLines.add(box("  " + h.address() + "  " + h.description(), heapContentWidth));
        }
    }
    heapLines.add(box("", heapContentWidth));
    heapLines.add(bottomBorder(heapContentWidth));

    // -- 2つの列を横に並べ、参照がマッチする箇所に矢印を挿入 --
    int maxLines = Math.max(stackLines.size(), heapLines.size());
    // 短い方の列をパディング
    while (stackLines.size() < maxLines) { stackLines.add(emptyLine(stackContentWidth)); isRefLine.add(false); }
    while (heapLines.size() < maxLines) heapLines.add(emptyLine(heapContentWidth));

    String bridgeArrow = " " + arrow + " ";
    String bridgeBlank = " ".repeat(bridgeArrow.length());

    for (int i = 0; i < maxLines; i++) {
        String bridge = (i < isRefLine.size() && isRefLine.get(i)) ? bridgeArrow : bridgeBlank;
        IO.println(stackLines.get(i) + bridge + heapLines.get(i));
    }

    // -- 補足ノートセクション --
    if (notes != null && !notes.isEmpty()) {
        IO.println("");
        for (String note : notes) {
            IO.println("  " + note);
        }
    }
}

/** スタック変数を表示用文字列にフォーマットする。 */
String formatStackVar(StackVar v) {
    String typeLabel = "[" + v.type() + "]";
    return "  " + padRight(v.name(), 8) + ": " + padRight(v.value(), 14) + typeLabel;
}

/** タイトルを埋め込んだ上端ボーダーを生成する。 */
String topBorder(String title, int width) {
    // 例: "+--- title ---...---+"
    String inner = "--- " + title + " ";
    int remaining = width - inner.length();
    if (remaining < 0) remaining = 0;
    return "+" + inner + "-".repeat(remaining) + "+";
}

/** ラベル付きのフレーム区切り線を生成する。 */
String frameSeparator(String label, int width) {
    String inner = "--- " + label + " ";
    int remaining = width - inner.length();
    if (remaining < 0) remaining = 0;
    return "|" + inner + "-".repeat(remaining) + "|";
}

/** 下端ボーダーを生成する。 */
String bottomBorder(int width) {
    return "+" + "-".repeat(width) + "+";
}

/** ボックス内の1行を生成する（指定幅までパディング）。 */
String box(String content, int width) {
    int padding = width - displayWidth(content);
    if (padding < 0) padding = 0;
    return "|" + content + " ".repeat(padding) + "|";
}

/** 空行を生成する（ボックス枠なし）。 */
String emptyLine(int width) {
    return " ".repeat(width + 2); // +2 はボーダー文字の分
}

/** 文字列を指定の長さまで右側にスペースでパディングする。 */
String padRight(String s, int len) {
    int diff = len - displayWidth(s);
    if (diff <= 0) return s;
    return s + " ".repeat(diff);
}

/** 指定の長さまでスペースでパディングする（padRight のエイリアス）。 */
String pad(String s, int len) {
    return padRight(s, len);
}

/**
 * 全角 CJK 文字を考慮したおおよその表示幅を算出する。
 *
 * <p>等幅ターミナルにおいて、CJK 文字（日本語・中国語・韓国語）は
 * 半角文字の2倍の幅を占有する。ASCII アートの位置揃えを正しく行うため、
 * この差分を考慮した幅計算が必要になる。
 */
int displayWidth(String s) {
    int w = 0;
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (isFullWidth(c)) {
            w += 2;
        } else {
            w += 1;
        }
    }
    return w;
}

/**
 * 指定された文字が全角（CJK または全角形）かどうかを判定する。
 *
 * <p>Unicode のブロック範囲に基づいて判定を行う。
 * ひらがな・カタカナ・漢字・全角記号などが対象となる。
 */
boolean isFullWidth(char c) {
    return (c >= '\u3000' && c <= '\u9FFF')   // CJK 統合漢字、ひらがな、カタカナ等
        || (c >= '\uF900' && c <= '\uFAFF')   // CJK 互換漢字
        || (c >= '\uFF01' && c <= '\uFF60')   // 全角形
        || (c >= '\uFFE0' && c <= '\uFFE6');  // 全角記号
}


// ─────────────────────────────────────────────────────────────
// ステップ表示ヘルパー
// ─────────────────────────────────────────────────────────────

/** メインタイトルバナーを出力する。 */
void printBanner() {
    IO.println("+" + "=".repeat(62) + "+");
    IO.println("|" + centerText("JVM メモリ可視化ツール", 62) + "|");
    IO.println("+" + "=".repeat(62) + "+");
    IO.println("");
}

/** ステップのヘッダーを出力する。 */
void printStep(int num, String title) {
    IO.println("");
    IO.println("=".repeat(3) + " Step " + num + ": " + title + " " + "=".repeat(3));
    IO.println("");
}

/** ステップ間の一時停止インジケーターを出力する。 */
void printPause() {
    IO.println("");
    IO.println("  [Enter を押して次のステップへ... (自動で進みます)]");
    IO.println("");
    IO.println("");
}

/** CJK 文字を考慮して、指定幅の中央にテキストを配置する。 */
String centerText(String text, int width) {
    int textWidth = displayWidth(text);
    int totalPad = width - textWidth;
    if (totalPad <= 0) return text;
    int leftPad = totalPad / 2;
    int rightPad = totalPad - leftPad;
    return " ".repeat(leftPad) + text + " ".repeat(rightPad);
}


// ─────────────────────────────────────────────────────────────
// デモンストレーション用ステップメソッド群
// ─────────────────────────────────────────────────────────────

/**
 * ステップ 4 のヘルパー: main から呼び出され、スタックフレームの積み上がりを実演する。
 *
 * <p>methodA が methodB を呼び出すことで、スタック上に main → methodA → methodB と
 * 3段のフレームが積み上がる様子を観察できる。
 */
void methodA(List<HeapObj> heapObjs) {
    int localA = 100;
    methodB(localA, heapObjs);
}

/**
 * ステップ 4 のヘルパー: methodA から呼び出され、さらにもう1段フレームを追加する。
 *
 * <p>この中で {@link StackWalker} を使って実際のスタックフレーム一覧を取得し、
 * JVM が管理するコールスタックの実態を確認する。
 */
void methodB(int inherited, List<HeapObj> heapObjs) {
    int localB = 200;

    // StackWalker を使って実際のスタックフレームを取得
    var walker = StackWalker.getInstance();
    var frames = walker.walk(s ->
        s.limit(10).map(f -> f.getMethodName() + "()").toList()
    );

    IO.println("  StackWalker が捉えた実際のフレーム一覧:");
    IO.println("  (上が現在のフレーム、下が最初のフレーム)");
    IO.println("");
    for (int i = 0; i < frames.size(); i++) {
        String marker = (i == 0) ? " <-- 現在実行中" : "";
        IO.println("    [" + i + "] " + frames.get(i) + marker);
    }
    IO.println("");

    // 複数フレームの可視化を構築
    var mainVars = List.of(
        new StackVar("(他の変数)", "...", "...", null)
    );
    var methodAVars = List.of(
        new StackVar("localA", "100", "int", null)
    );
    var methodBVars = List.of(
        new StackVar("inherited", String.valueOf(inherited), "int", null),
        new StackVar("localB", "200", "int", null)
    );
    var frameList = List.of(
        new FrameInfo("main", mainVars),
        new FrameInfo("methodA", methodAVars),
        new FrameInfo("methodB", methodBVars)
    );

    drawMemoryMap(frameList, heapObjs, List.of(
        "* メソッドを呼ぶたびにフレームが積み上がる。",
        "  methodB が終了すると、そのフレームは自動的に取り除かれる。",
        "* StackWalker API で実際のフレーム一覧を確認できる。"
    ));
}


/**
 * プログラムのエントリーポイント。
 *
 * <p>6つのステップを順に実行し、スタックとヒープのメモリ配置を
 * ASCII アートで可視化しながらデモンストレーションを行う。
 * 各ステップで変数の追加やオブジェクト生成を行い、
 * メモリマップがどう変化するかを段階的に確認できる。
 */
void main() {
    printBanner();

    // ステップをまたいでヒープオブジェクトを蓄積する
    var heapObjs = new ArrayList<HeapObj>();

    // ── ステップ 1: プリミティブ型 ──
    printStep(1, "プリミティブ変数を宣言");
    IO.println("  int count = 42;");
    IO.println("  double rate = 3.14;");
    IO.println("  boolean active = true;");
    IO.println("");

    int count = 42;
    double rate = 3.14;
    boolean active = true;

    var step1Vars = List.of(
        new StackVar("count", String.valueOf(count), "int", null),
        new StackVar("rate", String.valueOf(rate), "double", null),
        new StackVar("active", String.valueOf(active), "boolean", null)
    );
    drawMemoryMap(
        List.of(new FrameInfo("main", step1Vars)),
        heapObjs,
        List.of(
            "* プリミティブ型の値はスタックに直接格納される。",
            "  ヒープは使われない。GC の対象にもならない。"
        )
    );

    printPause();

    // ── ステップ 2: オブジェクト生成 ──
    printStep(2, "オブジェクトを生成");
    var point = new Point(10, 20);
    var name = "Alice";

    String pointAddr = pseudoAddr(point);
    String nameAddr = pseudoAddr(name);

    IO.println("  var point = new Point(10, 20);   // " + pointAddr);
    IO.println("  var name  = \"Alice\";              // " + nameAddr);
    IO.println("");

    heapObjs.add(new HeapObj(pointAddr, "Point { x=10, y=20 }"));
    heapObjs.add(new HeapObj(nameAddr, "String \"Alice\""));

    var step2Vars = List.of(
        new StackVar("count", String.valueOf(count), "int", null),
        new StackVar("rate", String.valueOf(rate), "double", null),
        new StackVar("active", String.valueOf(active), "boolean", null),
        new StackVar("point", pointAddr, "ref", pointAddr),
        new StackVar("name", nameAddr, "ref", nameAddr)
    );
    drawMemoryMap(
        List.of(new FrameInfo("main", step2Vars)),
        heapObjs,
        List.of(
            "* point, name はスタック上の「参照(Reference)」にすぎない。",
            "  オブジェクト本体はヒープに配置されている。"
        )
    );

    printPause();

    // ── ステップ 3: 配列生成 ──
    printStep(3, "配列を生成");
    var numbers = new int[]{1, 2, 3, 4, 5};
    var words = new String[]{"hello", "world"};

    String numbersAddr = pseudoAddr(numbers);
    String wordsAddr = pseudoAddr(words);

    IO.println("  var numbers = new int[]{1, 2, 3, 4, 5};  // " + numbersAddr);
    IO.println("  var words   = new String[]{\"hello\", \"world\"}; // " + wordsAddr);
    IO.println("");

    heapObjs.add(new HeapObj(numbersAddr, "int[5] {1, 2, 3, 4, 5}"));
    heapObjs.add(new HeapObj(wordsAddr, "String[2] {\"hello\", \"world\"}"));

    var step3Vars = List.of(
        new StackVar("count", String.valueOf(count), "int", null),
        new StackVar("rate", String.valueOf(rate), "double", null),
        new StackVar("active", String.valueOf(active), "boolean", null),
        new StackVar("point", pointAddr, "ref", pointAddr),
        new StackVar("name", nameAddr, "ref", nameAddr),
        new StackVar("numbers", numbersAddr, "ref", numbersAddr),
        new StackVar("words", wordsAddr, "ref", wordsAddr)
    );
    drawMemoryMap(
        List.of(new FrameInfo("main", step3Vars)),
        heapObjs,
        List.of(
            "* 配列も Java ではオブジェクトの一種。ヒープに配置される。",
            "  int[] も String[] も、参照はスタック、本体はヒープ。"
        )
    );

    printPause();

    // ── ステップ 4: メソッド呼び出し -- スタックフレームの成長 ──
    printStep(4, "メソッド呼び出し -- スタックフレームの成長");
    IO.println("  main() -> methodA() -> methodB() と呼び出す");
    IO.println("");

    methodA(heapObjs);

    printPause();

    // ── ステップ 5: 参照の共有 ──
    printStep(5, "同一オブジェクトへの複数参照");
    var ref1 = point;
    var ref2 = point;

    String ref1Addr = pseudoAddr(ref1);
    String ref2Addr = pseudoAddr(ref2);

    IO.println("  var ref1 = point;  // ref1 -> " + ref1Addr);
    IO.println("  var ref2 = point;  // ref2 -> " + ref2Addr);
    IO.println("  // point, ref1, ref2 は全て同じオブジェクトを指す");
    IO.println("");

    var step5Vars = List.of(
        new StackVar("point", pointAddr, "ref", pointAddr),
        new StackVar("ref1", ref1Addr, "ref", ref1Addr),
        new StackVar("ref2", ref2Addr, "ref", ref2Addr)
    );
    // 分かりやすくするため、Point オブジェクトのみ表示
    var step5Heap = List.of(
        new HeapObj(pointAddr, "Point { x=10, y=20 }")
    );
    drawMemoryMap(
        List.of(new FrameInfo("main", step5Vars)),
        step5Heap,
        List.of(
            "* 3 つの変数が同じヒープ上のオブジェクトを指している。",
            "  point, ref1, ref2 の疑似アドレスは全て " + pointAddr + " で一致。",
            "  Java の == 演算子は「参照が同じか」を比較するため、",
            "  point == ref1 も ref1 == ref2 も true になる。"
        )
    );

    printPause();

    // ── ステップ 6: 参照の null 化 -- GC 回収対象 ──
    printStep(6, "参照を null にする -- GC 回収対象の可視化");

    // GC 回収対象を実演するための一時オブジェクトを生成
    var temp = new Point(99, 99);
    String tempAddr = pseudoAddr(temp);

    IO.println("  var temp = new Point(99, 99);  // " + tempAddr);
    IO.println("");

    IO.println("  --- null 代入前 ---");
    IO.println("");
    var step6aVars = List.of(
        new StackVar("point", pointAddr, "ref", pointAddr),
        new StackVar("temp", tempAddr, "ref", tempAddr)
    );
    var step6aHeap = List.of(
        new HeapObj(pointAddr, "Point { x=10, y=20 }"),
        new HeapObj(tempAddr, "Point { x=99, y=99 }")
    );
    drawMemoryMap(
        List.of(new FrameInfo("main", step6aVars)),
        step6aHeap,
        List.of()
    );

    IO.println("");
    IO.println("  temp = null;  // 参照を切断!");
    IO.println("");

    // null 化のシミュレーション -- temp 変数は存在するが null を保持
    IO.println("  --- null 代入後 ---");
    IO.println("");
    var step6bVars = List.of(
        new StackVar("point", pointAddr, "ref", pointAddr),
        new StackVar("temp", "null", "ref", null)
    );
    var step6bHeap = List.of(
        new HeapObj(pointAddr, "Point { x=10, y=20 }"),
        new HeapObj(tempAddr, "Point { x=99, y=99 }  << GC 回収対象!")
    );
    drawMemoryMap(
        List.of(new FrameInfo("main", step6bVars)),
        step6bHeap,
        List.of(
            "* temp を null にしたことで、" + tempAddr + " の Point(99,99) を",
            "  指す参照がどこにもなくなった。",
            "* GC Roots から到達不能になったオブジェクトは、",
            "  次の GC サイクルで回収される(=メモリが解放される)。",
            "* これが「ガベージコレクション」の本質:",
            "  到達不能なオブジェクトを自動的に見つけて回収する仕組み。"
        )
    );

    IO.println("");
    IO.println("=".repeat(62));
    IO.println(centerText("可視化おわり -- お疲れさまでした!", 62));
    IO.println("=".repeat(62));
}
