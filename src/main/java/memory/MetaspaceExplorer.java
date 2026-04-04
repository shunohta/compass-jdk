/**
 * Metaspace（メタスペース）の使用状況を確認するプログラム。
 *
 * <p>Java 8 以降、クラスのメタデータ（クラス定義、メソッド定義、定数プール等）は
 * ヒープ外の <strong>Metaspace</strong> と呼ばれる領域に格納される。
 * Java 7 以前は "PermGen (Permanent Generation)" と呼ばれる固定サイズの
 * ヒープ内領域に置かれていたが、サイズ制限によるトラブルが多発したため、
 * Java 8 で Metaspace に刷新された。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>Metaspace はヒープとは別のネイティブメモリ領域である</li>
 *   <li>クラスをロードするたびに Metaspace の使用量が増加する</li>
 *   <li>{@link java.lang.management.MemoryPoolMXBean} で Metaspace 関連プールを確認できる</li>
 *   <li>{@link java.lang.management.ClassLoadingMXBean} でロード済みクラス数を取得できる</li>
 *   <li>Spring Boot のような大規模フレームワークでは数千〜数万クラスがロードされる</li>
 * </ul>
 *
 * <h2>Metaspace の構造（HotSpot C++ 層）</h2>
 * <p>HotSpot の内部では、Metaspace は以下のように管理される:
 * <pre>{@code
 * ネイティブメモリ（ヒープ外）
 * ┌─────────────────────────────────────────┐
 * │  Metaspace                               │
 * │  ├── Klass 構造体（クラス定義）           │ ← instanceKlass.cpp
 * │  ├── Method 構造体（メソッド定義）        │ ← method.cpp
 * │  ├── ConstantPool（定数プール）           │ ← constantPool.cpp
 * │  └── Bytecode（バイトコード本体）         │
 * │                                           │
 * │  Compressed Class Space                   │
 * │  └── 64bit VM で Klass ポインタ圧縮用     │
 * └─────────────────────────────────────────┘
 * }</pre>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java src/main/java/memory/MetaspaceExplorer.java
 * }</pre>
 *
 * @author jdk-core
 * @see java.lang.management.MemoryPoolMXBean
 * @see java.lang.management.ClassLoadingMXBean
 * @see java.lang.management.ManagementFactory
 */

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

/**
 * プログラムのエントリーポイント。
 *
 * <p>Metaspace 関連のメモリプール情報とクラスローダーの統計情報を表示する。
 */
void main() {
    IO.println("=== Metaspace エクスプローラー ===\n");

    // ──────────────────────────────────────────────
    // 1. Metaspace 関連のメモリプールを探索
    //
    // ManagementFactory.getMemoryPoolMXBeans() は JVM 内のすべての
    // メモリプールを返す。Metaspace 系のプールは NON_HEAP タイプ。
    // 典型的に以下のプールが見つかる:
    //   - "Metaspace"             : クラスメタデータの全体領域
    //   - "Compressed Class Space": 64bit VM での圧縮クラスポインタ用
    // ──────────────────────────────────────────────
    IO.println("[Metaspace 関連メモリプール]");
    IO.println("  (NON_HEAP タイプのプールからメタスペース系を抽出)\n");

    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
        // NON_HEAP タイプかつ名前に "Metaspace" または "Class" を含むプールを表示
        var name = pool.getName();
        if (pool.getType() == MemoryType.NON_HEAP
                && (name.contains("Metaspace") || name.contains("Class"))) {
            var usage = pool.getUsage();
            IO.println("  プール名: " + name);
            IO.println("    使用量(Used):   " + formatBytes(usage.getUsed()));
            IO.println("    確保量(Commit): " + formatBytes(usage.getCommitted()));
            IO.println("    最大値(Max):    " + formatBytes(usage.getMax()));
            IO.println();
        }
    }

    // ──────────────────────────────────────────────
    // 2. 全 NON_HEAP プールの一覧（参考用）
    // Metaspace 以外にも Code Cache などの NON_HEAP プールが存在する。
    // ──────────────────────────────────────────────
    IO.println("[全 NON_HEAP メモリプール]");
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
        if (pool.getType() == MemoryType.NON_HEAP) {
            var usage = pool.getUsage();
            IO.println("  " + pool.getName()
                + ": 使用=" + formatBytes(usage.getUsed())
                + ", 確保=" + formatBytes(usage.getCommitted())
                + ", 最大=" + formatBytes(usage.getMax()));
        }
    }

    // ──────────────────────────────────────────────
    // 3. クラスローディング情報
    //
    // ClassLoadingMXBean はクラスローダーの統計情報を提供する。
    // ロード済みクラス数が多いほど Metaspace の使用量が増える。
    // Spring Boot アプリでは起動時に数千クラスがロードされるのが普通。
    // ──────────────────────────────────────────────
    var classLoading = ManagementFactory.getClassLoadingMXBean();
    IO.println("\n[クラスローディング情報]");
    IO.println("  現在ロード済みクラス数:   " + classLoading.getLoadedClassCount());
    IO.println("  累計ロードクラス数:       " + classLoading.getTotalLoadedClassCount());
    IO.println("  累計アンロードクラス数:   " + classLoading.getUnloadedClassCount());

    // ──────────────────────────────────────────────
    // 4. 解説メッセージ
    // ──────────────────────────────────────────────
    IO.println("\n[解説]");
    IO.println("  Metaspace はヒープ外のネイティブメモリに確保される。");
    IO.println("  Java 7 以前の PermGen と異なり、デフォルトでサイズ上限がない。");
    IO.println("  -XX:MaxMetaspaceSize=256m で上限を設定可能。");
    IO.println("  クラスリークが発生すると Metaspace が肥大化し、");
    IO.println("  OutOfMemoryError: Metaspace が発生することがある。");
    IO.println("  特に WAR を再デプロイするアプリサーバーでは要注意。");
}

/**
 * バイト数を人間が読みやすい形式に変換するヘルパーメソッド。
 *
 * <p>KB 未満の場合はバイト数をそのまま表示し、
 * MB 以上の場合は MB 単位で表示する。
 * 負の値（上限が未設定の場合の {@code -1} 等）は "制限なし" を返す。
 *
 * @param bytes バイト数
 * @return 人間が読みやすい形式の文字列
 */
String formatBytes(long bytes) {
    if (bytes < 0) return "制限なし";
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
    return (bytes / 1024 / 1024) + " MB";
}
