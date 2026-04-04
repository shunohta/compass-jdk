# FFM API ディープダイブ -- Java からネイティブの世界へ安全にアクセスする

- [概要](#概要)
- [第1章: FFM API の設計思想と歴史](#第1章-ffm-api-の設計思想と歴史)
  - [1.1 JNI の何が問題だったのか](#11-jni-の何が問題だったのか)
  - [1.2 Project Panama の全体像](#12-project-panama-の全体像)
  - [1.3 FFM API の設計目標](#13-ffm-api-の設計目標)
  - [1.4 JEP の変遷 -- Incubator から正式化まで](#14-jep-の変遷----incubator-から正式化まで)
  - [1.5 Java 25 時点の位置づけ](#15-java-25-時点の位置づけ)
- [第2章: メモリ管理 API (Foreign Memory Access)](#第2章-メモリ管理-api-foreign-memory-access)
  - [2.1 MemorySegment -- メモリの統一的抽象化](#21-memorysegment----メモリの統一的抽象化)
  - [2.2 Arena -- メモリのライフサイクル管理](#22-arena----メモリのライフサイクル管理)
  - [2.3 MemoryLayout -- メモリレイアウトの宣言的定義](#23-memorylayout----メモリレイアウトの宣言的定義)
  - [2.4 VarHandle を使ったメモリアクセス](#24-varhandle-を使ったメモリアクセス)
  - [2.5 SegmentAllocator -- 効率的なメモリ割り当て](#25-segmentallocator----効率的なメモリ割り当て)
- [第3章: ネイティブ関数呼び出し API (Foreign Function)](#第3章-ネイティブ関数呼び出し-api-foreign-function)
  - [3.1 Linker -- ネイティブ関数の呼び出し](#31-linker----ネイティブ関数の呼び出し)
  - [3.2 FunctionDescriptor -- 関数シグネチャの定義](#32-functiondescriptor----関数シグネチャの定義)
  - [3.3 SymbolLookup -- ネイティブシンボルの検索](#33-symbollookup----ネイティブシンボルの検索)
  - [3.4 MethodHandle -- 呼び出しの実行](#34-methodhandle----呼び出しの実行)
  - [3.5 downcallHandle -- Java からネイティブへ](#35-downcallhandle----java-からネイティブへ)
  - [3.6 upcallStub -- ネイティブから Java へ（コールバック）](#36-upcallstub----ネイティブから-java-へコールバック)
- [第4章: 実践的な使用例](#第4章-実践的な使用例)
  - [4.1 strlen -- 最もシンプルな呼び出し](#41-strlen----最もシンプルな呼び出し)
  - [4.2 printf -- 可変長引数関数の呼び出し](#42-printf----可変長引数関数の呼び出し)
  - [4.3 構造体のマッピング -- struct timeval](#43-構造体のマッピング----struct-timeval)
  - [4.4 qsort -- コールバック関数の実装](#44-qsort----コールバック関数の実装)
  - [4.5 POSIX API -- getpid, gethostname](#45-posix-api----getpid-gethostname)
  - [4.6 配列操作 -- ネイティブバッファの読み書き](#46-配列操作----ネイティブバッファの読み書き)
- [第5章: JNI との詳細比較](#第5章-jni-との詳細比較)
  - [5.1 コード量の比較 -- 同じ機能の実装](#51-コード量の比較----同じ機能の実装)
  - [5.2 安全性の比較](#52-安全性の比較)
  - [5.3 パフォーマンスの比較](#53-パフォーマンスの比較)
  - [5.4 移行ガイド](#54-移行ガイド)
- [第6章: jextract ツール](#第6章-jextract-ツール)
  - [6.1 jextract とは](#61-jextract-とは)
  - [6.2 使い方と生成されるコード](#62-使い方と生成されるコード)
  - [6.3 Java 25 時点での状態](#63-java-25-時点での状態)
- [第7章: セキュリティモデル](#第7章-セキュリティモデル)
  - [7.1 --enable-native-access フラグ](#71---enable-native-access-フラグ)
  - [7.2 モジュールシステムとの連携](#72-モジュールシステムとの連携)
  - [7.3 Restricted Methods の扱い](#73-restricted-methods-の扱い)
- [第8章: HotSpot 内部の実装](#第8章-hotspot-内部の実装)
  - [8.1 関連するソースパス](#81-関連するソースパス)
  - [8.2 downcall の最適化 -- JIT によるインライン化](#82-downcall-の最適化----jit-によるインライン化)
  - [8.3 upcall の実装メカニズム](#83-upcall-の実装メカニズム)
  - [8.4 メモリセグメントの境界チェック](#84-メモリセグメントの境界チェック)
- [第9章: パフォーマンス特性とベストプラクティス](#第9章-パフォーマンス特性とベストプラクティス)
  - [9.1 JNI との速度比較](#91-jni-との速度比較)
  - [9.2 メモリオーバーヘッド](#92-メモリオーバーヘッド)
  - [9.3 ベストプラクティス](#93-ベストプラクティス)
- [第10章: Vector API（概要）](#第10章-vector-api概要)
  - [10.1 SIMD 操作の Java からの利用](#101-simd-操作の-java-からの利用)
  - [10.2 Java 25 時点の状態](#102-java-25-時点の状態)
  - [10.3 FFM API との関係](#103-ffm-api-との関係)
- [第11章: まとめ -- Q&A 形式の早見表](#第11章-まとめ----qa-形式の早見表)
- [参考リンク・ソースパス](#参考リンクソースパス)

---

## 概要

本ドキュメントは、**FFM API (Foreign Function & Memory API)** の内部構造と実践的な使い方を詳細に解説するガイドである。[jni-deep-dive.md](jni-deep-dive.md) のセクション6で概要を把握した上で、本ドキュメントで FFM API のアーキテクチャ、メモリ管理モデル、ネイティブ関数呼び出しの仕組み、HotSpot 内部での実装を深掘りする。

**対象層:** Java 層 / HotSpot C++ 層（FFM API は純粋 Java で記述されるが、内部的には HotSpot の最適化に依存する）

**対象ランタイム:** Azul Zulu 25 (OpenJDK 25 ベース / HotSpot VM)

**前提知識:**
- [jni-deep-dive.md](jni-deep-dive.md) -- JNI の仕組みの理解（FFM API が解決する問題を知るため）
- [jvm-overview.md](../jvm-overview.md) -- JVM 全体像の把握
- [memory-basics.md](../memory/memory-basics.md) -- Java ヒープとネイティブメモリの基礎

**位置づけ:**

```
jvm-overview.md（全体像の地図）
    │
    ├── classloader-deep-dive.md（クラスローダーの詳細）
    │
    ├── execution-engine-deep-dive.md（実行エンジンの詳細）
    │
    ├── jni-deep-dive.md（JNI の詳細）
    │       │
    │       └── 本ドキュメント（FFM API の詳細）  ← ここ
    │           JNI の後継技術として位置づけ
    │
    ├── memory-basics.md（メモリの詳細）
    │
    └── gc-deep-dive.md（GC の詳細）
```

---

## 第1章: FFM API の設計思想と歴史

### 1.1 JNI の何が問題だったのか

**対象層:** Java 層 / JNI 層

JNI (Java Native Interface) は 1997 年の JDK 1.1 で導入されて以来、27 年以上にわたって Java とネイティブコードの唯一の公式な橋渡しを担ってきた。しかし、JNI には以下の根本的な問題が存在する（詳細は [jni-deep-dive.md](jni-deep-dive.md) セクション5参照）。

```
┌──────────────────────────────────────────────────────────────────────┐
│  JNI の 5 つの根本的問題                                              │
│                                                                      │
│  ① ボイラープレートの多さ                                             │
│     Java ファイル + C ヘッダー + C 実装 + Makefile = 最低 4 ファイル    │
│     ↓                                                                │
│     小さな機能でも大量のコードが必要                                    │
│                                                                      │
│  ② メモリ安全性の欠如                                                 │
│     ネイティブコードはバッファオーバーフロー、use-after-free、          │
│     ダングリングポインタ等の危険にさらされる                            │
│     ↓                                                                │
│     Java の安全性保証が台無し                                          │
│                                                                      │
│  ③ パフォーマンスのオーバーヘッド                                      │
│     JNI 境界越え = スレッド状態遷移 + 参照管理 + GC セーフポイント      │
│     ↓                                                                │
│     数十〜数百ナノ秒のコスト（頻繁な呼び出しでは無視できない）          │
│                                                                      │
│  ④ JIT 最適化の壁                                                     │
│     native メソッドはインライン化不可 → 最適化の境界になる              │
│     ↓                                                                │
│     Java コード全体の最適化を阻害                                      │
│                                                                      │
│  ⑤ プラットフォーム依存のビルド                                        │
│     C/C++ コンパイラ、リンカー、OS 固有のヘッダーが必要                 │
│     ↓                                                                │
│     「Write Once, Run Anywhere」が破綻                                 │
└──────────────────────────────────────────────────────────────────────┘
```

特に問題なのは、**安全でない操作と面倒な手順の組み合わせ**である。JNI は開発者に多大な労力を要求するにもかかわらず、その労力をかけても安全性の保証が得られない。「苦労して危険なコードを書く」という最悪の組み合わせだ。

また、JNI には **ネイティブメモリを安全に操作する標準的な手段がない** という問題もある。`java.nio.ByteBuffer` は部分的な解決策だが、サイズ上限（約 2GB）、確定的な解放手段の欠如、構造体のマッピング機能の不在など、多くの制限がある。

### 1.2 Project Panama の全体像

**対象層:** Java 層

**Project Panama** は、Java とネイティブコードの相互運用を根本的に改善するための OpenJDK プロジェクトである。2014 年に開始され、以下の 3 つの主要コンポーネントを開発している。

```
┌──────────────────────────────────────────────────────────────────────┐
│  Project Panama の全体像                                              │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────┐         │
│  │                   Project Panama                        │         │
│  │                                                         │         │
│  │  ┌─────────────────┐  ┌──────────────┐  ┌───────────┐  │         │
│  │  │  FFM API         │  │  Vector API  │  │ jextract  │  │         │
│  │  │  (JEP 454)       │  │  (Incubator) │  │ (ツール)  │  │         │
│  │  │                  │  │              │  │           │  │         │
│  │  │ ネイティブ関数   │  │ SIMD 操作を  │  │ C ヘッダー│  │         │
│  │  │ 呼び出し +       │  │ Java から    │  │ から Java │  │         │
│  │  │ メモリ操作       │  │ 利用する     │  │ バインディ│  │         │
│  │  │                  │  │              │  │ ングを自動│  │         │
│  │  │ Java 22 で正式   │  │ Java 25 でも │  │ 生成      │  │         │
│  │  │                  │  │ Incubator    │  │           │  │         │
│  │  └─────────────────┘  └──────────────┘  └───────────┘  │         │
│  │         ▲                    ▲               ▲          │         │
│  │         │                    │               │          │         │
│  │         └────────────────────┼───────────────┘          │         │
│  │              連携して Java のネイティブ相互運用を        │         │
│  │              根本的に改善する                            │         │
│  └─────────────────────────────────────────────────────────┘         │
│                                                                      │
│  目標: JNI を使わずに、安全かつ効率的にネイティブの世界と              │
│        やり取りする                                                   │
└──────────────────────────────────────────────────────────────────────┘
```

| コンポーネント | 説明 | Java 25 時点の状態 |
|--------------|------|-------------------|
| **FFM API** | ネイティブ関数の呼び出し + ネイティブメモリの操作 | **正式機能** (Java 22, JEP 454) |
| **Vector API** | SIMD (Single Instruction, Multiple Data) 操作を Java から利用 | **Incubator** (JEP 489, 9th) |
| **jextract** | C ヘッダーファイルから Java バインディングを自動生成するツール | **開発中** (別リポジトリ) |

### 1.3 FFM API の設計目標

FFM API は以下の 4 つの設計目標を掲げている。

**① 安全性 (Safety)**

ネイティブメモリへのアクセスは、デフォルトで安全でなければならない。境界チェック、ライフサイクル管理、use-after-free の防止が組み込まれている。危険な操作を行うには明示的なオプトイン（`--enable-native-access`）が必要。

**② 効率性 (Efficiency)**

JNI と同等以上のパフォーマンスを実現する。JIT コンパイラがネイティブ呼び出しを最適化できる設計。`MethodHandle` ベースの呼び出しにより、インライン化や定数畳み込みが可能。

**③ 使いやすさ (Usability)**

純粋な Java コードだけでネイティブ関数を呼び出せる。C/C++ コード、ヘッダーファイル、ネイティブビルドツールチェーンは一切不要。IDE のサポート（補完、型チェック）が完全に機能する。

**④ 汎用性 (Generality)**

特定の ABI (Application Binary Interface) に限定されない。Windows (x64, Aarch64)、Linux (x64, Aarch64)、macOS (x64, Aarch64) の各プラットフォームの C ABI をサポート。将来的には C++ や Fortran 等の ABI もサポート可能な設計。

```
┌──────────────────────────────────────────────────────────────────────┐
│  FFM API の設計思想: 「安全をデフォルトに、危険はオプトインで」        │
│                                                                      │
│             安全な領域（デフォルト）                                   │
│  ┌───────────────────────────────────────────┐                       │
│  │  MemorySegment -- 境界チェック付きメモリ    │                       │
│  │  Arena -- ライフサイクル管理                │                       │
│  │  MemoryLayout -- 型安全なアクセス           │                       │
│  └───────────────────────────────────────────┘                       │
│                        │                                              │
│                        │  --enable-native-access（オプトイン）         │
│                        ▼                                              │
│  ┌───────────────────────────────────────────┐                       │
│  │  Linker.downcallHandle() -- ネイティブ呼出 │                       │
│  │  MemorySegment.reinterpret() -- 再解釈     │                       │
│  │  upcallStub() -- コールバック               │                       │
│  └───────────────────────────────────────────┘                       │
│             危険な領域（明示的な許可が必要）                            │
└──────────────────────────────────────────────────────────────────────┘
```

### 1.4 JEP の変遷 -- Incubator から正式化まで

FFM API は 6 回の JEP を経て正式化された。これは Java プラットフォームにおいて最も慎重に設計された API のひとつである。

```
┌──────────────────────────────────────────────────────────────────────┐
│  FFM API の JEP 変遷                                                  │
│                                                                      │
│  Java 17   JEP 412  Foreign Function & Memory API (Incubator)        │
│     │       └── 初の統合 API。Foreign Linker + Foreign Memory を統合   │
│     │                                                                 │
│  Java 18   JEP 419  Foreign Function & Memory API (2nd Incubator)    │
│     │       └── API の簡素化。MemoryAddress の廃止を検討               │
│     │                                                                 │
│  Java 19   JEP 424  Foreign Function & Memory API (Preview)          │
│     │       └── Preview に昇格。MemoryAddress を MemorySegment に統合  │
│     │                                                                 │
│  Java 20   JEP 434  Foreign Function & Memory API (2nd Preview)      │
│     │       └── Arena API の導入。MemorySession → Arena に変更         │
│     │                                                                 │
│  Java 21   JEP 442  Foreign Function & Memory API (3rd Preview)      │
│     │       └── SequenceLayout の改善。Linker Option の追加            │
│     │                                                                 │
│  Java 22   JEP 454  Foreign Function & Memory API ★正式化★           │
│     │       └── 4回の Preview/Incubator を経て正式機能に               │
│     │                                                                 │
│  Java 23   (変更なし -- 安定期)                                       │
│     │                                                                 │
│  Java 24   (変更なし -- 安定期)                                       │
│     │                                                                 │
│  Java 25   (変更なし -- 安定期)  ← 現在                               │
│             FFM API は安定した正式機能として利用可能                    │
└──────────────────────────────────────────────────────────────────────┘
```

各 JEP で行われた主要な変更:

| JEP | Java | ステージ | 主要な変更 |
|-----|------|---------|-----------|
| **412** | 17 | Incubator | Foreign Linker API と Foreign Memory Access API を統合 |
| **419** | 18 | 2nd Incubator | `MemoryAddress` の見直し、`MemorySession` の導入 |
| **424** | 19 | Preview | `MemoryAddress` を `MemorySegment` に統合（ゼロ長セグメント） |
| **434** | 20 | 2nd Preview | `MemorySession` → `Arena` に改名、API の簡素化 |
| **442** | 21 | 3rd Preview | `Linker.Option` の追加、`SegmentAllocator` の改善 |
| **454** | 22 | **正式** | 最終的な API の確定、`--enable-native-access` の厳格化 |

### 1.5 Java 25 時点の位置づけ

Java 25 (2025年9月 GA、LTS) において、FFM API は完全に安定した正式機能である。Java 22 での正式化以降、API の互換性は保証されている。

主な状況:
- **FFM API**: 正式機能。`java.lang.foreign` パッケージとして利用可能
- **`--enable-native-access`**: ネイティブアクセスを行うモジュール/コードに対して必須
- **jextract**: OpenJDK の別リポジトリで開発中。公式ビルドは JDK に同梱されていない
- **Vector API**: 依然として Incubator (JEP 489, 9th Incubator in Java 25)

---

## 第2章: メモリ管理 API (Foreign Memory Access)

**対象層:** Java 層

FFM API の「メモリ管理」部分は、ネイティブメモリ（Java ヒープの外のメモリ）を安全に操作するための API を提供する。これは、従来の `sun.misc.Unsafe` や `java.nio.ByteBuffer` の問題を解決する。

### 2.1 MemorySegment -- メモリの統一的抽象化

`MemorySegment` は FFM API の中核概念であり、**連続したメモリ領域への安全なアクセス**を提供する。ネイティブメモリと Java ヒープメモリの両方を同じインターフェースで扱える。

```
┌──────────────────────────────────────────────────────────────────────┐
│  MemorySegment -- メモリ領域の統一的な抽象化                          │
│                                                                      │
│  Java ヒープ                    ネイティブメモリ（off-heap）          │
│  ┌──────────────┐              ┌──────────────────────┐              │
│  │ byte[] 配列  │              │  malloc() で確保     │              │
│  │ int[] 配列   │              │  mmap() で確保       │              │
│  │ レコード     │              │  共有メモリ           │              │
│  └──────┬───────┘              └──────────┬───────────┘              │
│         │                                 │                          │
│         └──────────┐          ┌───────────┘                          │
│                    ▼          ▼                                      │
│              ┌────────────────────┐                                  │
│              │   MemorySegment    │  ← 統一インターフェース           │
│              │                    │                                  │
│              │  ・開始アドレス     │                                  │
│              │  ・サイズ（バイト） │                                  │
│              │  ・所有 Arena       │                                  │
│              │  ・アクセス境界     │                                  │
│              └────────────────────┘                                  │
│                        │                                             │
│                        ▼                                             │
│              ┌────────────────────────────────┐                      │
│              │  安全なアクセスメソッド          │                      │
│              │  ・get(ValueLayout, offset)     │                      │
│              │  ・set(ValueLayout, offset, val)│                      │
│              │  ・asSlice(offset, size)        │                      │
│              │  ・copyFrom(src)                │                      │
│              └────────────────────────────────┘                      │
│                                                                      │
│  すべてのアクセスに対して:                                             │
│  ✓ 境界チェック（IndexOutOfBoundsException）                          │
│  ✓ ライフサイクルチェック（IllegalStateException -- 解放済みアクセス）  │
│  ✓ スレッド制約チェック（WrongThreadException -- confined の場合）     │
└──────────────────────────────────────────────────────────────────────┘
```

**MemorySegment の 3 つの安全性保証:**

| 保証 | 説明 | 違反時の例外 |
|------|------|------------|
| **空間的安全性** | セグメントの境界外にアクセスできない | `IndexOutOfBoundsException` |
| **時間的安全性** | 解放済みのセグメントにアクセスできない | `IllegalStateException` |
| **スレッド安全性** | confined セグメントは所有スレッド以外からアクセスできない | `WrongThreadException` |

```java
// MemorySegment の基本的な使い方
import java.lang.foreign.*;

void main() {
    // ネイティブメモリの確保（100 バイト）
    try (var arena = Arena.ofConfined()) {
        MemorySegment segment = arena.allocate(100);

        // 値の書き込みと読み取り
        segment.set(ValueLayout.JAVA_INT, 0, 42);       // オフセット 0 に int を書く
        segment.set(ValueLayout.JAVA_INT, 4, 100);      // オフセット 4 に int を書く

        int val1 = segment.get(ValueLayout.JAVA_INT, 0); // 42
        int val2 = segment.get(ValueLayout.JAVA_INT, 4); // 100

        IO.println("val1 = " + val1 + ", val2 = " + val2);

        // 境界外アクセスは例外で安全に防止される
        // segment.get(ValueLayout.JAVA_INT, 100); // → IndexOutOfBoundsException
    }
    // try ブロックを抜けると自動的に解放される
    // segment.get(...); // → IllegalStateException（解放済み）
}
```

**ヒープセグメント -- Java 配列のラップ:**

```java
void main() {
    // Java 配列を MemorySegment としてラップ
    int[] javaArray = {10, 20, 30, 40, 50};
    MemorySegment heapSegment = MemorySegment.ofArray(javaArray);

    // 同じ API でアクセスできる
    int third = heapSegment.get(ValueLayout.JAVA_INT, 8); // インデックス 2 = オフセット 8
    IO.println("3番目の要素: " + third); // 30

    // ヒープセグメントのライフタイムは GC が管理する（Arena 不要）
}
```

### 2.2 Arena -- メモリのライフサイクル管理

**Arena** は、`MemorySegment` のライフサイクル（確保と解放）を管理するオブジェクトである。C の `malloc/free` に相当するが、グループ化された解放（一括解放）をサポートし、use-after-free を防止する。

```
┌──────────────────────────────────────────────────────────────────────┐
│  Arena の種類と特性                                                   │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐      │
│  │  Arena.ofConfined()  -- 限定的 Arena                       │      │
│  │  ・1つのスレッドからのみアクセス可能                        │      │
│  │  ・最も高速（同期不要）                                    │      │
│  │  ・try-with-resources で確定的に解放                       │      │
│  │  ・★ 最も推奨される選択肢                                 │      │
│  └────────────────────────────────────────────────────────────┘      │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐      │
│  │  Arena.ofShared()  -- 共有 Arena                           │      │
│  │  ・複数スレッドから安全にアクセス可能                       │      │
│  │  ・同期オーバーヘッドあり                                  │      │
│  │  ・try-with-resources で確定的に解放                       │      │
│  │  ・マルチスレッド環境で使用                                │      │
│  └────────────────────────────────────────────────────────────┘      │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐      │
│  │  Arena.ofAuto()  -- 自動 Arena                             │      │
│  │  ・GC が到達不能と判断したタイミングで解放                  │      │
│  │  ・close() を呼べない（AutoCloseable でない）              │      │
│  │  ・解放タイミングが非確定的                                │      │
│  │  ・ライフサイクルが不明確な場合に使用                       │      │
│  └────────────────────────────────────────────────────────────┘      │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐      │
│  │  Arena.global()  -- グローバル Arena                       │      │
│  │  ・プログラム終了まで解放されない                          │      │
│  │  ・close() を呼ぶと UnsupportedOperationException          │      │
│  │  ・定数的なネイティブメモリに使用                          │      │
│  │  ・メモリリークに注意                                     │      │
│  └────────────────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────────────────┘
```

**Arena の選択基準:**

| ユースケース | 推奨 Arena | 理由 |
|------------|-----------|------|
| 1つのメソッド内で完結 | `Arena.ofConfined()` | 最も高速、try-with-resources で安全 |
| 複数スレッドで共有 | `Arena.ofShared()` | スレッドセーフ |
| ライフサイクルが不明確 | `Arena.ofAuto()` | GC に任せる |
| アプリケーション全体で保持 | `Arena.global()` | 解放不要の定数データ |

```java
import java.lang.foreign.*;

void main() {
    // ① confined: 最も一般的なパターン
    try (var arena = Arena.ofConfined()) {
        MemorySegment seg1 = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment seg2 = arena.allocate(ValueLayout.JAVA_LONG);
        MemorySegment seg3 = arena.allocate(100); // 100 バイトの領域

        seg1.set(ValueLayout.JAVA_INT, 0, 42);
        seg2.set(ValueLayout.JAVA_LONG, 0, Long.MAX_VALUE);

        IO.println("int = " + seg1.get(ValueLayout.JAVA_INT, 0));
        IO.println("long = " + seg2.get(ValueLayout.JAVA_LONG, 0));
    } // ← seg1, seg2, seg3 がまとめて解放される

    // ② global: プログラム全体で有効
    MemorySegment permanent = Arena.global().allocate(ValueLayout.JAVA_INT);
    permanent.set(ValueLayout.JAVA_INT, 0, 999);
    // close() できない -- プログラム終了まで有効
}
```

**Arena の一括解放の図解:**

```
  Arena.ofConfined() での一括解放:

  try (var arena = Arena.ofConfined()) {
      seg1 = arena.allocate(...)  ──┐
      seg2 = arena.allocate(...)  ──┼── Arena が所有
      seg3 = arena.allocate(...)  ──┘
  }
       │
       ▼  arena.close() が呼ばれる
  ┌──────────────────────────────┐
  │  seg1 → 解放 ✓              │
  │  seg2 → 解放 ✓              │  一括解放
  │  seg3 → 解放 ✓              │
  └──────────────────────────────┘
       │
       ▼  以降のアクセスは例外
  seg1.get(...) → IllegalStateException
```

### 2.3 MemoryLayout -- メモリレイアウトの宣言的定義

`MemoryLayout` は、メモリ上のデータ構造を**宣言的**に定義するための API である。C の構造体（`struct`）、共用体（`union`）、配列をJava 側で型安全に表現できる。

```
┌──────────────────────────────────────────────────────────────────────┐
│  MemoryLayout の種類                                                  │
│                                                                      │
│  ┌──────────────────────┐                                            │
│  │    MemoryLayout       │  ← 抽象基底                               │
│  └───────┬──────────────┘                                            │
│          │                                                           │
│    ┌─────┼────────────┬────────────────┐                             │
│    ▼     ▼            ▼                ▼                             │
│  Value  Struct      Union           Sequence                         │
│  Layout Layout      Layout          Layout                           │
│                                                                      │
│  プリミティブ  C の struct  C の union    C の配列                     │
│  (int, long,  (フィールドが (フィールドが (同じ型の                    │
│   double,     順番に並ぶ)  重なる)       繰り返し)                     │
│   pointer)                                                           │
└──────────────────────────────────────────────────────────────────────┘
```

**ValueLayout -- プリミティブ型のレイアウト:**

| Java 型 | ValueLayout 定数 | サイズ | 説明 |
|---------|-----------------|-------|------|
| `byte` | `JAVA_BYTE` | 1 バイト | 符号付き 8 ビット整数 |
| `short` | `JAVA_SHORT` | 2 バイト | 符号付き 16 ビット整数 |
| `int` | `JAVA_INT` | 4 バイト | 符号付き 32 ビット整数 |
| `long` | `JAVA_LONG` | 8 バイト | 符号付き 64 ビット整数 |
| `float` | `JAVA_FLOAT` | 4 バイト | 32 ビット浮動小数点数 |
| `double` | `JAVA_DOUBLE` | 8 バイト | 64 ビット浮動小数点数 |
| `char` | `JAVA_CHAR` | 2 バイト | 16 ビット Unicode 文字 |
| ポインタ | `ADDRESS` | 8 バイト (64bit) | ネイティブポインタ |

**StructLayout -- C 構造体のマッピング:**

C の構造体を Java で表現する例を示す。

```c
// C の構造体
struct Point {
    int x;
    int y;
};

struct Person {
    char name[32];
    int age;
    double height;
};
```

```java
import java.lang.foreign.*;
import java.lang.invoke.*;

void main() {
    // C の struct Point に対応するレイアウト
    StructLayout pointLayout = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("x"),
        ValueLayout.JAVA_INT.withName("y")
    );
    IO.println("Point サイズ: " + pointLayout.byteSize() + " バイト"); // 8

    // VarHandle を取得してフィールドにアクセス
    VarHandle xHandle = pointLayout.varHandle(
        MemoryLayout.PathElement.groupElement("x")
    );
    VarHandle yHandle = pointLayout.varHandle(
        MemoryLayout.PathElement.groupElement("y")
    );

    try (var arena = Arena.ofConfined()) {
        MemorySegment point = arena.allocate(pointLayout);

        // フィールドに値を設定
        xHandle.set(point, 0L, 10);
        yHandle.set(point, 0L, 20);

        // フィールドから値を読み取り
        int x = (int) xHandle.get(point, 0L);
        int y = (int) yHandle.get(point, 0L);

        IO.println("Point(" + x + ", " + y + ")"); // Point(10, 20)
    }
}
```

**SequenceLayout -- 配列のマッピング:**

```java
import java.lang.foreign.*;
import java.lang.invoke.*;

void main() {
    // int[10] に相当するレイアウト
    SequenceLayout arrayLayout = MemoryLayout.sequenceLayout(
        10, ValueLayout.JAVA_INT
    );
    IO.println("int[10] サイズ: " + arrayLayout.byteSize() + " バイト"); // 40

    // 要素アクセス用の VarHandle
    VarHandle elemHandle = arrayLayout.varHandle(
        MemoryLayout.PathElement.sequenceElement()
    );

    try (var arena = Arena.ofConfined()) {
        MemorySegment array = arena.allocate(arrayLayout);

        // 配列に値を設定
        for (long i = 0; i < 10; i++) {
            elemHandle.set(array, 0L, i, (int)(i * i));
        }

        // 配列から値を読み取り
        IO.println("=== int[10] の中身 ===");
        for (long i = 0; i < 10; i++) {
            int val = (int) elemHandle.get(array, 0L, i);
            IO.println("  [" + i + "] = " + val);
        }
    }
}
```

**パディングとアラインメント:**

C コンパイラはパフォーマンスのために構造体フィールド間にパディング（隙間）を挿入する。FFM API ではこれを `MemoryLayout.paddingLayout()` で明示的に表現する。

```
  C の構造体のメモリレイアウト例:

  struct Example {
      char  c;    // 1 バイト
                  // 3 バイトのパディング（int のアラインメントのため）
      int   i;    // 4 バイト
      short s;    // 2 バイト
                  // 6 バイトのパディング（構造体全体のアラインメントのため）
  };

  メモリ上の配置:
  ┌───┬───────────┬───────────────┬───────┬───────────────────────┐
  │ c │ pad (3B)  │      i        │   s   │     pad (6B)          │
  ├───┼───────────┼───────────────┼───────┼───────────────────────┤
  │ 0 │ 1  2  3   │ 4  5  6  7   │ 8  9  │ 10 11 12 13 14 15    │
  └───┴───────────┴───────────────┴───────┴───────────────────────┘
  合計: 16 バイト
```

```java
import java.lang.foreign.*;

void main() {
    // パディングを含む構造体レイアウト
    StructLayout exampleLayout = MemoryLayout.structLayout(
        ValueLayout.JAVA_BYTE.withName("c"),
        MemoryLayout.paddingLayout(3),        // 3 バイトのパディング
        ValueLayout.JAVA_INT.withName("i"),
        ValueLayout.JAVA_SHORT.withName("s"),
        MemoryLayout.paddingLayout(6)         // 6 バイトのパディング
    );

    IO.println("Example サイズ: " + exampleLayout.byteSize() + " バイト"); // 16
}
```

### 2.4 VarHandle を使ったメモリアクセス

`VarHandle` は `MemorySegment` 上のデータにアクセスするための**高性能なアクセサ**である。`java.lang.invoke.VarHandle` は JEP 193 (Java 9) で導入されたが、FFM API では `MemoryLayout` から自動生成される。

```
┌──────────────────────────────────────────────────────────────────────┐
│  VarHandle によるメモリアクセスの流れ                                  │
│                                                                      │
│  MemoryLayout                                                        │
│     │                                                                │
│     │ .varHandle(PathElement...)                                      │
│     ▼                                                                │
│  VarHandle                                                           │
│     │                                                                │
│     │ .get(segment, offset, ...)                                     │
│     │ .set(segment, offset, ..., value)                              │
│     ▼                                                                │
│  MemorySegment 上のバイト列                                          │
│     │                                                                │
│     │  ① 境界チェック                                                │
│     │  ② アラインメントチェック                                       │
│     │  ③ ライフサイクルチェック                                       │
│     ▼                                                                │
│  実際のメモリ読み書き                                                 │
│  （JIT がインライン化して直接メモリアクセスに最適化）                  │
└──────────────────────────────────────────────────────────────────────┘
```

**PathElement -- ネストした構造体へのアクセス:**

```java
import java.lang.foreign.*;
import java.lang.invoke.*;

// ネストした構造体の例
// struct Line {
//     struct Point start;
//     struct Point end;
// };

void main() {
    StructLayout pointLayout = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("x"),
        ValueLayout.JAVA_INT.withName("y")
    );

    StructLayout lineLayout = MemoryLayout.structLayout(
        pointLayout.withName("start"),
        pointLayout.withName("end")
    );

    // ネストしたフィールドへの VarHandle
    VarHandle startX = lineLayout.varHandle(
        MemoryLayout.PathElement.groupElement("start"),
        MemoryLayout.PathElement.groupElement("x")
    );
    VarHandle endY = lineLayout.varHandle(
        MemoryLayout.PathElement.groupElement("end"),
        MemoryLayout.PathElement.groupElement("y")
    );

    try (var arena = Arena.ofConfined()) {
        MemorySegment line = arena.allocate(lineLayout);

        startX.set(line, 0L, 0);
        endY.set(line, 0L, 100);

        IO.println("start.x = " + (int) startX.get(line, 0L));   // 0
        IO.println("end.y   = " + (int) endY.get(line, 0L));     // 100
    }
}
```

### 2.5 SegmentAllocator -- 効率的なメモリ割り当て

`Arena` は `SegmentAllocator` インターフェースを実装しており、メモリ割り当ての戦略をカスタマイズできる。特に、大量の小さなセグメントを割り当てる場合に `SegmentAllocator.slicingAllocator()` が有効である。

```java
import java.lang.foreign.*;

void main() {
    try (var arena = Arena.ofConfined()) {
        // スライシングアロケータ: 1つの大きなセグメントを切り分ける
        MemorySegment bulk = arena.allocate(1024);
        var slicing = SegmentAllocator.slicingAllocator(bulk);

        // 個別の allocate() がそれぞれ malloc() を呼ぶ代わりに
        // 1つの大きなメモリブロックをスライスして返す
        MemorySegment a = slicing.allocate(ValueLayout.JAVA_INT);  // 4 バイト
        MemorySegment b = slicing.allocate(ValueLayout.JAVA_INT);  // 4 バイト
        MemorySegment c = slicing.allocate(ValueLayout.JAVA_LONG); // 8 バイト

        a.set(ValueLayout.JAVA_INT, 0, 1);
        b.set(ValueLayout.JAVA_INT, 0, 2);
        c.set(ValueLayout.JAVA_LONG, 0, 3L);

        IO.println("a=" + a.get(ValueLayout.JAVA_INT, 0)
            + " b=" + b.get(ValueLayout.JAVA_INT, 0)
            + " c=" + c.get(ValueLayout.JAVA_LONG, 0));
    }
}
```

```
  スライシングアロケータの動作:

  bulk (1024 バイト):
  ┌────┬────┬────────┬──────────────────────────────────┐
  │ a  │ b  │   c    │         未使用領域                │
  │4B  │4B  │  8B    │         1008 バイト               │
  ├────┼────┼────────┼──────────────────────────────────┤
  │ 0  │ 4  │  8     │ 16                          1024 │
  └────┴────┴────────┴──────────────────────────────────┘

  利点: malloc() の呼び出し回数を削減（1回だけで済む）
```

---

## 第3章: ネイティブ関数呼び出し API (Foreign Function)

**対象層:** Java 層 / HotSpot C++ 層

FFM API の「関数呼び出し」部分は、Java からネイティブ関数（C ライブラリ等）を直接呼び出す機能を提供する。JNI のように C コードを書く必要がなく、純粋な Java コードだけでネイティブ関数を利用できる。

### 3.1 Linker -- ネイティブ関数の呼び出し

`Linker` は FFM API における**ネイティブ呼び出しの中核**である。プラットフォームの C ABI (Application Binary Interface) に準拠した呼び出し規約を処理する。

```
┌──────────────────────────────────────────────────────────────────────┐
│  Linker の役割                                                       │
│                                                                      │
│  Java コード                                                         │
│     │                                                                │
│     │  Linker.nativeLinker()                                         │
│     ▼                                                                │
│  ┌────────────────────────────────────────┐                          │
│  │              Linker                    │                          │
│  │                                        │                          │
│  │  ┌──────────────────────────────────┐  │                          │
│  │  │  downcallHandle()               │  │                          │
│  │  │  Java → ネイティブ              │  │                          │
│  │  │  (C 関数を呼ぶ)                 │  │                          │
│  │  └──────────────────────────────────┘  │                          │
│  │                                        │                          │
│  │  ┌──────────────────────────────────┐  │                          │
│  │  │  upcallStub()                   │  │                          │
│  │  │  ネイティブ → Java              │  │                          │
│  │  │  (C からのコールバック)          │  │                          │
│  │  └──────────────────────────────────┘  │                          │
│  │                                        │                          │
│  │  ┌──────────────────────────────────┐  │                          │
│  │  │  defaultLookup()                │  │                          │
│  │  │  標準 C ライブラリのシンボル検索  │  │                          │
│  │  └──────────────────────────────────┘  │                          │
│  └────────────────────────────────────────┘                          │
│     │                                                                │
│     │  ABI に準拠した呼び出し                                        │
│     ▼                                                                │
│  ネイティブ関数（C ライブラリ等）                                     │
│                                                                      │
│  対応 ABI:                                                           │
│  ・System V AMD64 ABI (Linux/macOS x64)                              │
│  ・Windows x64 calling convention                                    │
│  ・AAPCS64 (Linux/macOS AArch64)                                     │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.2 FunctionDescriptor -- 関数シグネチャの定義

`FunctionDescriptor` は、呼び出すネイティブ関数の**引数の型と戻り値の型**を定義する。C の関数プロトタイプに相当する。

```java
import java.lang.foreign.*;

void main() {
    // C の関数プロトタイプとの対応
    //
    // size_t strlen(const char *s);
    //   → FunctionDescriptor.of(JAVA_LONG, ADDRESS)
    //
    // int printf(const char *format, ...);
    //   → FunctionDescriptor.of(JAVA_INT, ADDRESS)
    //
    // void free(void *ptr);
    //   → FunctionDescriptor.ofVoid(ADDRESS)
    //
    // pid_t getpid(void);
    //   → FunctionDescriptor.of(JAVA_INT)

    // 戻り値ありの場合: FunctionDescriptor.of(戻り値型, 引数型...)
    FunctionDescriptor strlenDesc = FunctionDescriptor.of(
        ValueLayout.JAVA_LONG,    // 戻り値: size_t (long)
        ValueLayout.ADDRESS       // 引数: const char* (ポインタ)
    );

    // 戻り値なし (void) の場合: FunctionDescriptor.ofVoid(引数型...)
    FunctionDescriptor freeDesc = FunctionDescriptor.ofVoid(
        ValueLayout.ADDRESS       // 引数: void* (ポインタ)
    );

    // 引数なしの場合: FunctionDescriptor.of(戻り値型)
    FunctionDescriptor getpidDesc = FunctionDescriptor.of(
        ValueLayout.JAVA_INT      // 戻り値: pid_t (int)
    );

    IO.println("FunctionDescriptor の例を定義しました");
    IO.println("  strlen: " + strlenDesc);
    IO.println("  free:   " + freeDesc);
    IO.println("  getpid: " + getpidDesc);
}
```

**C 型と ValueLayout の対応表:**

| C の型 | ValueLayout | サイズ | 備考 |
|--------|------------|-------|------|
| `char` | `JAVA_BYTE` | 1 | C の `char` は 1 バイト |
| `short` | `JAVA_SHORT` | 2 | |
| `int` | `JAVA_INT` | 4 | |
| `long` (LP64) | `JAVA_LONG` | 8 | Linux/macOS の 64bit 環境 |
| `long` (LLP64) | `JAVA_INT` | 4 | Windows の 64bit 環境 |
| `long long` | `JAVA_LONG` | 8 | |
| `size_t` | `JAVA_LONG` | 8 | 64bit 環境 |
| `float` | `JAVA_FLOAT` | 4 | |
| `double` | `JAVA_DOUBLE` | 8 | |
| `void*` / 任意のポインタ | `ADDRESS` | 8 (64bit) | |

### 3.3 SymbolLookup -- ネイティブシンボルの検索

`SymbolLookup` は、ネイティブライブラリからシンボル（関数や変数のアドレス）を検索するための API である。

```
┌──────────────────────────────────────────────────────────────────────┐
│  SymbolLookup の 3 つの取得方法                                      │
│                                                                      │
│  ① Linker.defaultLookup()                                           │
│     標準 C ライブラリ (libc) のシンボルを検索                         │
│     例: strlen, printf, malloc, free, abs, qsort                     │
│                                                                      │
│  ② SymbolLookup.libraryLookup(path, arena)                          │
│     指定したライブラリファイルからシンボルを検索                       │
│     例: libraryLookup("libcurl.so", arena)                           │
│                                                                      │
│  ③ SymbolLookup.loaderLookup()                                      │
│     System.loadLibrary() でロード済みのライブラリからシンボルを検索    │
│     JNI ライブラリとの互換性のために使用                              │
│                                                                      │
│  検索結果は Optional<MemorySegment> で返される                       │
│  （シンボルが見つからない場合は empty）                               │
└──────────────────────────────────────────────────────────────────────┘
```

```java
import java.lang.foreign.*;
import java.lang.invoke.*;

void main() {
    var linker = Linker.nativeLinker();

    // ① 標準 C ライブラリからシンボルを検索
    SymbolLookup stdlib = linker.defaultLookup();

    // strlen のアドレスを取得
    MemorySegment strlenAddr = stdlib.find("strlen")
        .orElseThrow(() -> new RuntimeException("strlen が見つからない"));

    IO.println("strlen のアドレス: " + strlenAddr);

    // ② 特定のライブラリからシンボルを検索（例: macOS の libSystem）
    // try (var arena = Arena.ofConfined()) {
    //     SymbolLookup lib = SymbolLookup.libraryLookup("/usr/lib/libSystem.B.dylib", arena);
    //     lib.find("getpid").ifPresent(addr -> IO.println("getpid: " + addr));
    // }
}
```

### 3.4 MethodHandle -- 呼び出しの実行

`Linker.downcallHandle()` は `java.lang.invoke.MethodHandle` を返す。このハンドルを `invoke()` または `invokeExact()` で呼び出すことで、ネイティブ関数が実行される。

```
┌──────────────────────────────────────────────────────────────────────┐
│  downcall の完全な流れ                                                │
│                                                                      │
│  ① シンボル検索                                                      │
│     SymbolLookup.find("strlen")  → MemorySegment (関数アドレス)      │
│                                                                      │
│  ② 関数記述子の作成                                                   │
│     FunctionDescriptor.of(JAVA_LONG, ADDRESS)                        │
│                                                                      │
│  ③ MethodHandle の取得                                               │
│     Linker.downcallHandle(addr, desc)  → MethodHandle                │
│                                                                      │
│  ④ 引数の準備                                                        │
│     Arena.ofConfined() で文字列をネイティブメモリに変換               │
│                                                                      │
│  ⑤ 呼び出し                                                          │
│     handle.invokeExact(args...)  → 戻り値                            │
│                                                                      │
│  内部的な処理:                                                        │
│  ┌──────────────────────────────────────────────┐                    │
│  │  Java 側                                     │                    │
│  │  MethodHandle.invokeExact()                  │                    │
│  │     │                                        │                    │
│  │     ▼  引数のマーシャリング                   │                    │
│  │  レジスタ/スタックに引数を配置                │                    │
│  │  (ABI に準拠)                                │                    │
│  │     │                                        │                    │
│  │     ▼  ネイティブ関数呼び出し                 │                    │
│  │  ─────────── ABI 境界 ───────────            │                    │
│  │     │                                        │                    │
│  │     ▼  戻り値のアンマーシャリング             │                    │
│  │  ネイティブの戻り値を Java の型に変換          │                    │
│  └──────────────────────────────────────────────┘                    │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.5 downcallHandle -- Java からネイティブへ

`downcallHandle()` は最も頻繁に使用される機能で、Java からネイティブ関数を呼び出す `MethodHandle` を生成する。

```java
import java.lang.foreign.*;
import java.lang.invoke.*;

void main() throws Throwable {
    var linker = Linker.nativeLinker();
    SymbolLookup stdlib = linker.defaultLookup();

    // C の strlen を呼び出す完全な例
    // size_t strlen(const char *s);
    MethodHandle strlen = linker.downcallHandle(
        stdlib.find("strlen").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    );

    try (var arena = Arena.ofConfined()) {
        // Java の String をネイティブの C 文字列（null 終端）に変換
        MemorySegment cString = arena.allocateFrom("Hello, FFM API!");

        // ネイティブ関数を呼び出す
        long length = (long) strlen.invokeExact(cString);
        IO.println("strlen(\"Hello, FFM API!\") = " + length); // 15
    }
}
```

### 3.6 upcallStub -- ネイティブから Java へ（コールバック）

`upcallStub()` は、ネイティブコードから Java のメソッドをコールバックするための**関数ポインタ**を生成する。C の関数ポインタを引数に取る API（`qsort`, `pthread_create` 等）で使用する。

```
┌──────────────────────────────────────────────────────────────────────┐
│  upcall の仕組み                                                     │
│                                                                      │
│  Java 側                        ネイティブ側                         │
│  ┌──────────────────┐          ┌──────────────────┐                 │
│  │  Java メソッド    │          │  C の関数         │                 │
│  │  (コールバック)   │◄─────── │  (qsort 等)      │                 │
│  │                  │  upcall  │                  │                 │
│  └──────────────────┘          └──────────────────┘                 │
│         ▲                              ▲                            │
│         │                              │                            │
│         │ MethodHandle                 │ downcallHandle             │
│         │                              │                            │
│  ┌──────┴───────────────────────────────┴────────┐                  │
│  │           Linker                              │                  │
│  │                                               │                  │
│  │  upcallStub(handle, desc, arena)              │                  │
│  │  → MemorySegment（関数ポインタ）              │                  │
│  │                                               │                  │
│  │  このアドレスをネイティブ関数に渡すと、         │                  │
│  │  ネイティブ側から Java メソッドが呼ばれる       │                  │
│  └───────────────────────────────────────────────┘                  │
└──────────────────────────────────────────────────────────────────────┘
```

upcall の具体例は第4章のセクション 4.4 (qsort) で示す。

---

## 第4章: 実践的な使用例

**対象層:** Java 層

本章では、FFM API を使った実践的なコード例を示す。すべての例は Java 25 スタイル（`void main()`, `IO.println`, 暗黙的 import）で記述する。

### 4.1 strlen -- 最もシンプルな呼び出し

```java
import java.lang.foreign.*;
import java.lang.invoke.*;

/**
 * FFM API で C の strlen を呼び出す最もシンプルな例。
 *
 * <p>FFM API の基本パターン（シンボル検索 → 記述子定義 → ハンドル取得 → 呼び出し）
 * を学ぶための入門例。
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java --enable-native-access=ALL-UNNAMED src/main/java/FfmStrlen.java
 * }</pre>
 */
void main() throws Throwable {
    var linker = Linker.nativeLinker();

    // strlen のシンボルを検索
    MemorySegment strlenAddr = linker.defaultLookup()
        .find("strlen")
        .orElseThrow();

    // size_t strlen(const char *s);
    MethodHandle strlen = linker.downcallHandle(
        strlenAddr,
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    );

    try (var arena = Arena.ofConfined()) {
        // テスト文字列をネイティブメモリに配置
        String[] testStrings = {"", "Hello", "FFM API は素晴らしい", "Java 25"};

        for (String s : testStrings) {
            MemorySegment cStr = arena.allocateFrom(s);
            long len = (long) strlen.invokeExact(cStr);
            IO.println("strlen(\"" + s + "\") = " + len + " バイト");
        }
    }
}
```

### 4.2 printf -- 可変長引数関数の呼び出し

C の `printf` のような可変長引数（variadic）関数の呼び出しには、`Linker.Option.captureCallState()` や通常の downcall で対応する。ただし、**variadic 関数の呼び出しには `FunctionDescriptor` に固定引数部分のみを定義し、`Linker.Option.firstVariadicArg()` で可変部分の開始位置を指定する**。

```java
import java.lang.foreign.*;
import java.lang.invoke.*;

void main() throws Throwable {
    var linker = Linker.nativeLinker();

    // int printf(const char *format, ...);
    // 可変長引数: format + 1つの int 引数のバージョン
    MethodHandle printf = linker.downcallHandle(
        linker.defaultLookup().find("printf").orElseThrow(),
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,     // 戻り値: int
            ValueLayout.ADDRESS,      // format: const char*
            ValueLayout.JAVA_INT      // 可変引数: int
        ),
        Linker.Option.firstVariadicArg(1) // インデックス 1 以降が可変長引数
    );

    try (var arena = Arena.ofConfined()) {
        MemorySegment format = arena.allocateFrom("FFM から printf: %d\n");
        int printed = (int) printf.invokeExact(format, 42);
        IO.println("(printf が出力した文字数: " + printed + ")");
    }
}
```

### 4.3 構造体のマッピング -- struct timeval

POSIX の `gettimeofday()` を呼び出して現在時刻を取得する例を示す。C の構造体 `struct timeval` を `StructLayout` でマッピングする。

```c
// C の定義（参考）
struct timeval {
    time_t      tv_sec;   // 秒
    suseconds_t tv_usec;  // マイクロ秒
};

int gettimeofday(struct timeval *tv, struct timezone *tz);
```

```java
import java.lang.foreign.*;
import java.lang.invoke.*;

void main() throws Throwable {
    // struct timeval のレイアウト定義（LP64 環境: macOS/Linux 64bit）
    StructLayout timevalLayout = MemoryLayout.structLayout(
        ValueLayout.JAVA_LONG.withName("tv_sec"),    // time_t = long (8 バイト)
        ValueLayout.JAVA_LONG.withName("tv_usec")    // suseconds_t = long (8 バイト)
    );

    VarHandle tvSec = timevalLayout.varHandle(
        MemoryLayout.PathElement.groupElement("tv_sec")
    );
    VarHandle tvUsec = timevalLayout.varHandle(
        MemoryLayout.PathElement.groupElement("tv_usec")
    );

    var linker = Linker.nativeLinker();

    // int gettimeofday(struct timeval *tv, struct timezone *tz);
    MethodHandle gettimeofday = linker.downcallHandle(
        linker.defaultLookup().find("gettimeofday").orElseThrow(),
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,     // 戻り値: int
            ValueLayout.ADDRESS,      // tv: struct timeval*
            ValueLayout.ADDRESS       // tz: struct timezone* (NULL を渡す)
        )
    );

    try (var arena = Arena.ofConfined()) {
        MemorySegment tv = arena.allocate(timevalLayout);

        // gettimeofday(&tv, NULL) を呼び出す
        int result = (int) gettimeofday.invokeExact(tv, MemorySegment.NULL);

        if (result == 0) {
            long sec = (long) tvSec.get(tv, 0L);
            long usec = (long) tvUsec.get(tv, 0L);
            IO.println("現在時刻 (Unix epoch): " + sec + " 秒 " + usec + " マイクロ秒");
        }
    }
}
```

### 4.4 qsort -- コールバック関数の実装

C の `qsort()` は関数ポインタ（コンパレータ）を引数に取る。FFM API の `upcallStub()` を使って、Java のメソッドをコールバックとして渡す例を示す。

```c
// C の定義（参考）
void qsort(void *base, size_t nmemb, size_t size,
           int (*compar)(const void *, const void *));
```

```java
import java.lang.foreign.*;
import java.lang.invoke.*;

// int 型のコンパレータ（upcall で呼ばれる）
int compareInts(MemorySegment a, MemorySegment b) {
    int va = a.reinterpret(ValueLayout.JAVA_INT.byteSize())
              .get(ValueLayout.JAVA_INT, 0);
    int vb = b.reinterpret(ValueLayout.JAVA_INT.byteSize())
              .get(ValueLayout.JAVA_INT, 0);
    return Integer.compare(va, vb);
}

void main() throws Throwable {
    var linker = Linker.nativeLinker();

    // void qsort(void *base, size_t nmemb, size_t size,
    //            int (*compar)(const void *, const void *));
    MethodHandle qsort = linker.downcallHandle(
        linker.defaultLookup().find("qsort").orElseThrow(),
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,      // base
            ValueLayout.JAVA_LONG,    // nmemb
            ValueLayout.JAVA_LONG,    // size
            ValueLayout.ADDRESS       // compar (関数ポインタ)
        )
    );

    // コールバック関数の FunctionDescriptor
    // int (*compar)(const void *, const void *)
    FunctionDescriptor comparDesc = FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS
    );

    // Java メソッドへの MethodHandle を取得
    MethodHandle comparHandle = MethodHandles.lookup().findStatic(
        MethodHandles.lookup().lookupClass(),
        "compareInts",
        MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class)
    );

    try (var arena = Arena.ofConfined()) {
        // int 配列をネイティブメモリに作成
        int[] data = {50, 20, 80, 10, 40, 90, 30, 70, 60};

        SequenceLayout arrayLayout = MemoryLayout.sequenceLayout(
            data.length, ValueLayout.JAVA_INT
        );
        MemorySegment nativeArray = arena.allocate(arrayLayout);

        // Java 配列からネイティブメモリにコピー
        MemorySegment.copy(data, 0, nativeArray, ValueLayout.JAVA_INT, 0, data.length);

        IO.println("ソート前: " + arrayToString(nativeArray, data.length));

        // upcall スタブを作成（Java メソッドを関数ポインタ化）
        MemorySegment comparStub = linker.upcallStub(
            comparHandle, comparDesc, arena
        );

        // qsort を呼び出す
        qsort.invokeExact(nativeArray, (long) data.length, 4L, comparStub);

        IO.println("ソート後: " + arrayToString(nativeArray, data.length));
    }
}

/** ネイティブ int 配列を文字列に変換する。 */
String arrayToString(MemorySegment array, int length) {
    var sb = new StringBuilder("[");
    for (int i = 0; i < length; i++) {
        if (i > 0) sb.append(", ");
        sb.append(array.getAtIndex(ValueLayout.JAVA_INT, i));
    }
    return sb.append("]").toString();
}
```

```
  qsort + upcall の処理フロー:

  Java                             C (libc)
  ─────                            ────────
  qsort.invokeExact(array,         
    nmemb, size, comparStub)
         │
         │  downcall
         ▼
                                   qsort() が実行される
                                       │
                                       │  要素比較のたびに
                                       │  comparStub を呼ぶ
                                       ▼
  compareInts(a, b) が呼ばれる  ◄── upcall
  Java 側で比較結果を返す ─────────▶ qsort() が比較結果を受け取る
         │                             │
         │  (これを繰り返す)            │
         ▼                             ▼
  ソート完了                        ソート完了
```

### 4.5 POSIX API -- getpid, gethostname

```java
import java.lang.foreign.*;
import java.lang.invoke.*;

void main() throws Throwable {
    var linker = Linker.nativeLinker();
    SymbolLookup stdlib = linker.defaultLookup();

    // pid_t getpid(void);
    MethodHandle getpid = linker.downcallHandle(
        stdlib.find("getpid").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_INT)
    );

    // int gethostname(char *name, size_t len);
    MethodHandle gethostname = linker.downcallHandle(
        stdlib.find("gethostname").orElseThrow(),
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG
        )
    );

    // getpid() の呼び出し
    int pid = (int) getpid.invokeExact();
    IO.println("プロセス ID: " + pid);

    // gethostname() の呼び出し
    try (var arena = Arena.ofConfined()) {
        MemorySegment buf = arena.allocate(256);
        int result = (int) gethostname.invokeExact(buf, 256L);
        if (result == 0) {
            String hostname = buf.getString(0);
            IO.println("ホスト名: " + hostname);
        }
    }
}
```

### 4.6 配列操作 -- ネイティブバッファの読み書き

```java
import java.lang.foreign.*;

void main() {
    try (var arena = Arena.ofConfined()) {
        // 1000 個の double 配列をネイティブメモリに作成
        int count = 1000;
        MemorySegment buffer = arena.allocate(
            ValueLayout.JAVA_DOUBLE, count
        );

        // 値の書き込み（sin 波を生成）
        for (int i = 0; i < count; i++) {
            double value = Math.sin(2.0 * Math.PI * i / count);
            buffer.setAtIndex(ValueLayout.JAVA_DOUBLE, i, value);
        }

        // 値の読み取りと集計
        double sum = 0;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            double value = buffer.getAtIndex(ValueLayout.JAVA_DOUBLE, i);
            sum += value;
            max = Math.max(max, value);
        }

        IO.println("要素数: " + count);
        IO.println("合計: " + String.format("%.6f", sum));   // ≈ 0 (sin 波の合計)
        IO.println("最大値: " + String.format("%.6f", max)); // ≈ 1.0

        // ネイティブメモリから Java 配列にコピー
        double[] javaArray = buffer.toArray(ValueLayout.JAVA_DOUBLE);
        IO.println("Java 配列のサイズ: " + javaArray.length);
    }
}
```

---

## 第5章: JNI との詳細比較

**対象層:** Java 層 / JNI 層

### 5.1 コード量の比較 -- 同じ機能の実装

C の `strlen` を呼び出す機能を、JNI と FFM API の両方で実装して比較する。

```
┌──────────────────────────────────────────────────────────────────────┐
│  JNI の場合: 3 ファイル + ビルド手順                                  │
│                                                                      │
│  ファイル1: StrlenJni.java (Java ソース)                              │
│  ────────────────────────────────────                                │
│  public class StrlenJni {                                            │
│      static { System.loadLibrary("strlenjni"); }                     │
│      public static native long strlen(String s);                     │
│      public static void main(String[] args) {                        │
│          System.out.println(strlen("Hello"));                        │
│      }                                                               │
│  }                                                                   │
│                                                                      │
│  ファイル2: StrlenJni.h (javac -h で自動生成)                         │
│  ────────────────────────────────────                                │
│  JNIEXPORT jlong JNICALL Java_StrlenJni_strlen(JNIEnv*, jclass,      │
│                                                 jstring);            │
│                                                                      │
│  ファイル3: StrlenJni.c (C ソース -- 手書き)                          │
│  ────────────────────────────────────                                │
│  #include <jni.h>                                                    │
│  #include <string.h>                                                 │
│  #include "StrlenJni.h"                                              │
│  JNIEXPORT jlong JNICALL Java_StrlenJni_strlen(                      │
│      JNIEnv *env, jclass cls, jstring s) {                           │
│      const char *str = (*env)->GetStringUTFChars(env, s, NULL);      │
│      jlong len = (jlong)strlen(str);                                 │
│      (*env)->ReleaseStringUTFChars(env, s, str);                     │
│      return len;                                                     │
│  }                                                                   │
│                                                                      │
│  ビルド手順:                                                          │
│  $ javac -h . StrlenJni.java                                         │
│  $ gcc -shared -o libstrlenjni.so -I$JAVA_HOME/include ...           │
│  $ java -Djava.library.path=. StrlenJni                              │
│                                                                      │
│  合計: 約 25 行 + C ビルド環境が必要                                  │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│  FFM API の場合: 1 ファイルのみ                                       │
│                                                                      │
│  ファイル1: StrlenFfm.java (Java ソースのみ)                          │
│  ────────────────────────────────────                                │
│  import java.lang.foreign.*;                                         │
│  import java.lang.invoke.*;                                          │
│                                                                      │
│  void main() throws Throwable {                                      │
│      var linker = Linker.nativeLinker();                              │
│      MethodHandle strlen = linker.downcallHandle(                    │
│          linker.defaultLookup().find("strlen").orElseThrow(),        │
│          FunctionDescriptor.of(                                      │
│              ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));           │
│      try (var arena = Arena.ofConfined()) {                          │
│          var cStr = arena.allocateFrom("Hello");                     │
│          IO.println((long) strlen.invokeExact(cStr));                │
│      }                                                               │
│  }                                                                   │
│                                                                      │
│  実行:                                                                │
│  $ java --enable-native-access=ALL-UNNAMED StrlenFfm.java           │
│                                                                      │
│  合計: 約 12 行 + C ビルド環境不要                                    │
└──────────────────────────────────────────────────────────────────────┘
```

### 5.2 安全性の比較

| 問題 | JNI | FFM API |
|------|-----|---------|
| **バッファオーバーフロー** | C コードで自由にメモリ操作できるため発生し得る | `MemorySegment` の境界チェックで防止 |
| **use-after-free** | `free()` 後のポインタ使用は未定義動作 | `Arena.close()` 後のアクセスは `IllegalStateException` |
| **メモリリーク** | `ReleaseStringUTFChars` 等の呼び忘れ | `try-with-resources` で自動解放 |
| **型の不一致** | `jlong` と `int` の取り違えは実行時に発見 | `ValueLayout` の不一致はコンパイル/実行時に検出 |
| **ダングリングポインタ** | GC がオブジェクトを移動してもネイティブポインタは更新されない | `MemorySegment` は GC と協調する |
| **スレッド安全性** | `JNIEnv*` はスレッドローカルだが、グローバル参照は共有される | `Arena.ofConfined()` はスレッド制約を強制 |

```
┌──────────────────────────────────────────────────────────────────────┐
│  安全性の違いを図解                                                   │
│                                                                      │
│  JNI -- 危険な操作が暗黙的に許可される:                               │
│                                                                      │
│  char *buf = malloc(10);                                             │
│  buf[100] = 'x';         // ← バッファオーバーフロー（検出されない）  │
│  free(buf);                                                          │
│  buf[0] = 'y';           // ← use-after-free（検出されない）         │
│                                                                      │
│  FFM API -- 危険な操作は例外で即座にブロックされる:                    │
│                                                                      │
│  try (var arena = Arena.ofConfined()) {                               │
│      var seg = arena.allocate(10);                                    │
│      seg.set(JAVA_BYTE, 100, (byte)'x');                             │
│      // → IndexOutOfBoundsException ★ 即座に検出                     │
│  }                                                                   │
│  seg.get(JAVA_BYTE, 0);                                              │
│  // → IllegalStateException ★ 即座に検出                             │
└──────────────────────────────────────────────────────────────────────┘
```

### 5.3 パフォーマンスの比較

FFM API のパフォーマンスは JNI と同等以上である。特に以下の点で優位性がある。

```
┌──────────────────────────────────────────────────────────────────────┐
│  JNI vs FFM API: パフォーマンス特性の比較                             │
│                                                                      │
│  ┌─────────────────────────────────────┐                             │
│  │  JNI の呼び出しコスト                │                             │
│  │                                     │                             │
│  │  1. Java → native の状態遷移        │  ~20ns                      │
│  │  2. JNIEnv* のセットアップ          │  ~5ns                       │
│  │  3. ローカル参照フレーム作成         │  ~10ns                      │
│  │  4. 実際のネイティブ関数実行         │  (関数依存)                  │
│  │  5. ローカル参照フレーム解放         │  ~10ns                      │
│  │  6. native → Java の状態遷移        │  ~20ns                      │
│  │  7. セーフポイントチェック           │  ~5ns                       │
│  │                                     │                             │
│  │  合計オーバーヘッド: ~70ns           │                             │
│  │  ★ JIT がインライン化できない       │                             │
│  └─────────────────────────────────────┘                             │
│                                                                      │
│  ┌─────────────────────────────────────┐                             │
│  │  FFM API の呼び出しコスト            │                             │
│  │                                     │                             │
│  │  1. MethodHandle 呼び出し           │  ~5ns (JIT 最適化後)        │
│  │  2. 引数のマーシャリング             │  ~5ns                       │
│  │  3. 実際のネイティブ関数実行         │  (関数依存)                  │
│  │  4. 戻り値のアンマーシャリング       │  ~5ns                       │
│  │                                     │                             │
│  │  合計オーバーヘッド: ~15ns           │                             │
│  │  ★ JIT がインライン化・最適化可能   │                             │
│  └─────────────────────────────────────┘                             │
│                                                                      │
│  ※ 数値は概算であり、実際の値は環境・関数・JIT 最適化の度合いによる   │
│  ※ FFM API は JIT の定数畳み込み・インライン化の恩恵を受ける          │
└──────────────────────────────────────────────────────────────────────┘
```

| 指標 | JNI | FFM API | 備考 |
|------|-----|---------|------|
| 呼び出しオーバーヘッド | ~70ns | ~15ns | FFM は JIT 最適化が効く |
| 大量データ受け渡し | コピーが必要な場合あり | `MemorySegment` で直接共有 | コピー不要 |
| JIT 最適化 | インライン化不可 | インライン化可能 | 繰り返し呼び出しで差が拡大 |
| スタートアップ | ライブラリロードが必要 | MethodHandle 生成のコスト | 初回のみ |

### 5.4 移行ガイド

| 状況 | 推奨アクション |
|------|--------------|
| **新規開発** でネイティブ関数を呼びたい | FFM API を使う |
| **既存の JNI コード**が安定稼働している | そのまま維持（無理に移行しない） |
| 既存の JNI コードに**メモリリーク等の問題**がある | FFM API への移行を検討 |
| JDK 21 以前をサポートする必要がある | JNI を使う（FFM API は Java 22 から正式） |
| C/C++ ライブラリの**大規模なバインディング**が必要 | jextract を使う（セクション6参照） |
| **パフォーマンスが最重要** | FFM API を使う（JIT 最適化が効く） |

**移行のステップ:**

```
  JNI → FFM API 移行のステップ:

  Step 1: 対象の特定
  ┌──────────────────────────────────────┐
  │  native メソッドの一覧を作成         │
  │  ・どの C 関数を呼んでいるか         │
  │  ・引数と戻り値の型は何か            │
  │  ・データのやり取りはどうなっているか │
  └──────────────────────────────────────┘
           │
           ▼
  Step 2: FunctionDescriptor の定義
  ┌──────────────────────────────────────┐
  │  JNI の型マッピングを                 │
  │  FFM API の ValueLayout に置き換える  │
  └──────────────────────────────────────┘
           │
           ▼
  Step 3: downcallHandle の作成
  ┌──────────────────────────────────────┐
  │  Linker.downcallHandle() で           │
  │  MethodHandle を取得                 │
  └──────────────────────────────────────┘
           │
           ▼
  Step 4: メモリ管理の移行
  ┌──────────────────────────────────────┐
  │  ByteBuffer → MemorySegment          │
  │  malloc/free → Arena                 │
  │  JNI 参照管理 → 不要に               │
  └──────────────────────────────────────┘
           │
           ▼
  Step 5: C コードの削除
  ┌──────────────────────────────────────┐
  │  C/C++ ソースファイルの削除           │
  │  ネイティブビルドスクリプトの削除     │
  │  JNI ヘッダーの削除                  │
  └──────────────────────────────────────┘
```

---

## 第6章: jextract ツール

**対象層:** Java 層

### 6.1 jextract とは

**jextract** は、C のヘッダーファイル（`.h`）を解析し、対応する FFM API の Java バインディングコードを**自動生成**するツールである。大規模な C ライブラリ（OpenSSL, libcurl, SQLite 等）のバインディングを手書きする手間を大幅に削減する。

```
┌──────────────────────────────────────────────────────────────────────┐
│  jextract の役割                                                     │
│                                                                      │
│  C ヘッダーファイル              自動生成された Java コード             │
│  ┌─────────────────┐           ┌──────────────────────────┐         │
│  │  #include <...>  │           │  class stdio_h {          │         │
│  │                  │           │    static MethodHandle    │         │
│  │  int printf(...);│  ──────▶ │      printf$MH = ...;     │         │
│  │  FILE* fopen(...);│ jextract │    static int printf(...) │         │
│  │  size_t strlen();│           │      { ... }             │         │
│  │  ...             │           │    ...                   │         │
│  └─────────────────┘           └──────────────────────────┘         │
│                                                                      │
│  入力: C のヘッダーファイル (.h)                                      │
│  出力: FFM API を使った Java クラス群                                 │
│                                                                      │
│  内部的には libclang (LLVM) を使って C の型情報を解析している          │
└──────────────────────────────────────────────────────────────────────┘
```

### 6.2 使い方と生成されるコード

**基本的な使い方:**

```bash
# C ヘッダーファイルから Java バインディングを生成
jextract --output src/gen \
         --target-package org.example.stdio \
         /usr/include/stdio.h

# 特定の関数のみを抽出
jextract --output src/gen \
         --target-package org.example.string \
         --include-function strlen \
         --include-function strcmp \
         /usr/include/string.h

# ライブラリを指定して生成
jextract --output src/gen \
         --target-package org.example.curl \
         -l curl \
         /usr/include/curl/curl.h
```

**生成されるコードの例（概念）:**

jextract が `strlen` に対して生成するコードは、概念的には以下のようなものである。

```java
// jextract が自動生成するコード（概念的な擬似コード）
package org.example.string;

import java.lang.foreign.*;
import java.lang.invoke.*;

public class string_h {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = LINKER.defaultLookup();

    // size_t strlen(const char *s);
    private static final FunctionDescriptor strlen$DESC = FunctionDescriptor.of(
        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS
    );

    private static final MethodHandle strlen$MH = LINKER.downcallHandle(
        LOOKUP.find("strlen").orElseThrow(), strlen$DESC
    );

    public static long strlen(MemorySegment s) {
        try {
            return (long) strlen$MH.invokeExact(s);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }
}
```

これにより、利用者は以下のように簡潔に呼び出せる。

```java
// jextract が生成したバインディングの利用
import org.example.string.string_h;

void main() {
    try (var arena = Arena.ofConfined()) {
        var cStr = arena.allocateFrom("Hello");
        long len = string_h.strlen(cStr);
        IO.println("長さ: " + len);
    }
}
```

### 6.3 Java 25 時点での状態

- **jextract は JDK に同梱されていない。** 別リポジトリ (`https://github.com/openjdk/jextract`) で開発中
- EA (Early Access) ビルドが提供されている
- 内部的に **libclang (LLVM/Clang のライブラリ)** を使って C の型情報を解析する
- C++ のバインディング生成は**サポート対象外**（C のみ）
- 将来的には JDK に同梱される可能性がある

```
  jextract の開発状況 (Java 25 時点):

  ┌──────────────────────────────────────────────────────┐
  │  リポジトリ: https://github.com/openjdk/jextract     │
  │  ステータス: 開発中（EA ビルド提供）                  │
  │  JDK 同梱: なし（別途インストールが必要）             │
  │  対応言語: C のみ（C++ は対象外）                     │
  │  依存: libclang (LLVM)                               │
  │  ライセンス: GPLv2 with Classpath Exception           │
  └──────────────────────────────────────────────────────┘
```

---

## 第7章: セキュリティモデル

**対象層:** Java 層

ネイティブコードへのアクセスは本質的に危険である（任意のメモリにアクセスでき、JVM をクラッシュさせることも可能）。FFM API はこの危険性を管理するために、明示的なオプトインモデルを採用している。

### 7.1 --enable-native-access フラグ

ネイティブ関数の呼び出しやネイティブメモリの操作（restricted メソッド）を行うには、実行時に `--enable-native-access` フラグを指定する必要がある。

```bash
# 名前なしモジュール（クラスパス上のコード）の場合
java --enable-native-access=ALL-UNNAMED FfmDemo.java

# 特定のモジュールの場合
java --enable-native-access=my.module -m my.module/com.example.Main

# 複数モジュールの場合
java --enable-native-access=mod.a,mod.b -m mod.a/com.example.Main
```

**フラグなしで restricted メソッドを呼んだ場合:**

Java 22〜23 では、`--enable-native-access` なしで restricted メソッドを呼び出すと**警告が表示**されていた。Java 24 (JEP 472) でネイティブアクセス制限が強化され、`--enable-native-access` 未指定時には実行ごとに警告が表示されるとともに、将来のリリースでデフォルトが例外 (`IllegalCallerException`) スローに変更される方針が明確化された。Java 25 時点ではまだ警告段階だが、`--illegal-native-access=deny` を指定することで例外スローの動作を先行して有効化できる。

```
WARNING: A restricted method in java.lang.foreign.Linker has been called
WARNING: java.lang.foreign.Linker::nativeLinker has been called by com.example.Main
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning at run time
```

### 7.2 モジュールシステムとの連携

`module-info.java` でモジュールを定義している場合、`--enable-native-access` はモジュール名で指定する。

```java
// module-info.java
module my.native.app {
    // FFM API を使用するために必要
    // (java.base は暗黙的に requires されるが、
    //  native access の許可は別途必要)
}
```

```bash
# モジュール化されたアプリケーションの実行
java --enable-native-access=my.native.app \
     --module-path out \
     -m my.native.app/com.example.Main
```

### 7.3 Restricted Methods の扱い

FFM API において「restricted（制限付き）」に分類されるメソッドの一覧:

| クラス | メソッド | 理由 |
|-------|---------|------|
| `Linker` | `nativeLinker()` | ネイティブ関数呼び出しの起点 |
| `Linker` | `downcallHandle()` | ネイティブ関数の呼び出し |
| `Linker` | `upcallStub()` | ネイティブからのコールバック |
| `SymbolLookup` | `libraryLookup()` | ネイティブライブラリのロード |
| `MemorySegment` | `reinterpret()` | セグメントの再解釈（型安全性を迂回） |
| `Arena` | `ofAuto()` のセグメントの `reinterpret()` | GC 管理下のセグメントの再解釈 |

```
┌──────────────────────────────────────────────────────────────────────┐
│  セキュリティモデルの段階                                             │
│                                                                      │
│  レベル1: 完全に安全（制限なし）                                      │
│  ┌──────────────────────────────────────────┐                        │
│  │  MemorySegment.ofArray(arr)              │  Java 配列のラップ     │
│  │  Arena.ofConfined() + allocate()          │  メモリ確保・解放      │
│  │  segment.get() / set()                   │  境界チェック付き      │
│  │  MemoryLayout.structLayout()             │  レイアウト定義        │
│  └──────────────────────────────────────────┘                        │
│                                                                      │
│  レベル2: 制限付き（--enable-native-access が必要）                   │
│  ┌──────────────────────────────────────────┐                        │
│  │  Linker.nativeLinker()                   │  ネイティブ呼び出し    │
│  │  downcallHandle() / upcallStub()         │  関数呼び出し         │
│  │  SymbolLookup.libraryLookup()            │  ライブラリロード     │
│  │  MemorySegment.reinterpret()             │  セグメント再解釈     │
│  └──────────────────────────────────────────┘                        │
│                                                                      │
│  ★ レベル1は --enable-native-access なしで使用可能                   │
│  ★ レベル2は --enable-native-access が必須                           │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 第8章: HotSpot 内部の実装

**対象層:** HotSpot C++ 層

FFM API は Java ライブラリ（`java.lang.foreign` パッケージ）として実装されているが、高パフォーマンスを実現するために HotSpot VM の深い部分と連携している。

### 8.1 関連するソースパス

**Java 層 (JDK ライブラリ):**

| パス | 説明 |
|------|------|
| `src/java.base/share/classes/java/lang/foreign/` | FFM API の公開インターフェース |
| `src/java.base/share/classes/java/lang/foreign/MemorySegment.java` | MemorySegment インターフェース |
| `src/java.base/share/classes/java/lang/foreign/Arena.java` | Arena インターフェース |
| `src/java.base/share/classes/java/lang/foreign/Linker.java` | Linker インターフェース |
| `src/java.base/share/classes/java/lang/foreign/MemoryLayout.java` | MemoryLayout |
| `src/java.base/share/classes/java/lang/foreign/FunctionDescriptor.java` | FunctionDescriptor |
| `src/java.base/share/classes/java/lang/foreign/SymbolLookup.java` | SymbolLookup |
| `src/java.base/share/classes/jdk/internal/foreign/` | FFM API の内部実装 |
| `src/java.base/share/classes/jdk/internal/foreign/abi/` | ABI 固有の実装 |

**HotSpot C++ 層:**

| パス | 説明 |
|------|------|
| `src/hotspot/share/prims/foreign_globals.cpp` | FFM のグローバル設定 |
| `src/hotspot/share/prims/foreignGlobals.hpp` | FFM 関連ヘッダー |
| `src/hotspot/share/prims/upcallLinker.cpp` | upcall スタブの生成 |
| `src/hotspot/cpu/x86/upcallLinker_x86_64.cpp` | x86-64 固有の upcall |
| `src/hotspot/cpu/aarch64/upcallLinker_aarch64.cpp` | AArch64 固有の upcall |
| `src/hotspot/share/code/nmethod.cpp` | ネイティブメソッドのコンパイル |
| `src/hotspot/share/opto/library_call.cpp` | intrinsic 化された呼び出し |

### 8.2 downcall の最適化 -- JIT によるインライン化

FFM API の downcall は `MethodHandle` ベースで実装されている。これにより、JIT コンパイラ（C2）が呼び出しを**インライン化**し、不要なオーバーヘッドを除去できる。

```
┌──────────────────────────────────────────────────────────────────────┐
│  downcall の JIT 最適化                                              │
│                                                                      │
│  解釈実行時（最適化前）:                                              │
│                                                                      │
│  Java コード                                                         │
│    │                                                                 │
│    ▼  MethodHandle.invokeExact()                                     │
│  MethodHandle チェーン                                                │
│    │  引数の型チェック                                                │
│    │  引数のマーシャリング                                            │
│    ▼                                                                 │
│  NativeEntryPoint (ネイティブ呼び出しスタブ)                         │
│    │  レジスタへの引数配置                                            │
│    │  スタックフレームの構築                                          │
│    ▼                                                                 │
│  ネイティブ関数                                                      │
│                                                                      │
│  ─────────────────────────────────────────────                       │
│                                                                      │
│  JIT 最適化後:                                                       │
│                                                                      │
│  Java コード                                                         │
│    │                                                                 │
│    │  ★ MethodHandle チェーンがインライン化される                     │
│    │  ★ 型チェックが定数畳み込みで除去される                          │
│    │  ★ マーシャリングが最適化される                                  │
│    ▼                                                                 │
│  直接のネイティブ呼び出し命令                                        │
│    │  call <native_function_address>                                 │
│    ▼                                                                 │
│  ネイティブ関数                                                      │
│                                                                      │
│  → JNI の 7 ステップが 1 命令に圧縮される                            │
└──────────────────────────────────────────────────────────────────────┘
```

この最適化が可能な理由:

1. **`MethodHandle` は JIT に理解される**: C2 コンパイラは `MethodHandle` のチェーンを展開・インライン化できる
2. **定数引数の畳み込み**: `FunctionDescriptor` や関数アドレスが定数であれば、呼び出しスタブを特殊化できる
3. **型チェックの除去**: 型が静的に確定していれば、ランタイムチェックを除去できる
4. **JNI の状態遷移が不要**: FFM API は JNI のようなスレッド状態遷移（`_thread_in_Java` → `_thread_in_native`）を最適化できる

### 8.3 upcall の実装メカニズム

upcall（ネイティブ → Java のコールバック）は、ネイティブコードから呼び出せる**スタブ関数**を動的に生成することで実現される。

```
┌──────────────────────────────────────────────────────────────────────┐
│  upcall スタブの生成と実行                                           │
│                                                                      │
│  Linker.upcallStub(handle, desc, arena)                              │
│     │                                                                │
│     ▼                                                                │
│  ┌────────────────────────────────────────┐                          │
│  │  UpcallLinker (C++)                    │                          │
│  │                                        │                          │
│  │  1. ネイティブスタブコードを生成        │                          │
│  │     (プラットフォーム固有のアセンブリ)   │                          │
│  │                                        │                          │
│  │  2. スタブは以下を行う:                 │                          │
│  │     a. ネイティブの引数をレジスタから取得│                          │
│  │     b. Java の MethodHandle を呼び出す  │                          │
│  │     c. 戻り値をネイティブの規約で返す   │                          │
│  └────────────────────────────────────────┘                          │
│     │                                                                │
│     ▼                                                                │
│  MemorySegment（スタブのアドレス）                                   │
│     │                                                                │
│     │  ネイティブ関数に渡す                                          │
│     ▼                                                                │
│  ネイティブコードがこのアドレスを呼ぶ                                │
│     │                                                                │
│     │  スタブコードが実行される                                      │
│     ▼                                                                │
│  Java の MethodHandle が呼ばれる                                     │
└──────────────────────────────────────────────────────────────────────┘
```

**HotSpot ソースパス:**
- `src/hotspot/share/prims/upcallLinker.cpp` -- upcall スタブのプラットフォーム非依存部分
- `src/hotspot/cpu/x86/upcallLinker_x86_64.cpp` -- x86-64 のスタブ生成
- `src/hotspot/cpu/aarch64/upcallLinker_aarch64.cpp` -- AArch64 のスタブ生成

### 8.4 メモリセグメントの境界チェック

`MemorySegment` の `get()`/`set()` メソッドは、**毎回の呼び出しで境界チェック**を行う。しかし、JIT コンパイラがこのチェックを最適化する。

```
┌──────────────────────────────────────────────────────────────────────┐
│  境界チェックの最適化                                                 │
│                                                                      │
│  最適化前（毎回チェック）:                                            │
│                                                                      │
│  for (int i = 0; i < 1000; i++) {                                    │
│      segment.set(JAVA_INT, i * 4L, i);                               │
│      // ↑ 毎回: offset + 4 <= segment.byteSize() をチェック         │
│  }                                                                   │
│                                                                      │
│  JIT 最適化後（ループ外に移動）:                                     │
│                                                                      │
│  if (1000 * 4 <= segment.byteSize()) { // ← ループ外で1回だけ       │
│      for (int i = 0; i < 1000; i++) {                                │
│          // 境界チェックなしで直接メモリアクセス                      │
│          UNSAFE.putInt(segment.address() + i * 4L, i);               │
│      }                                                               │
│  }                                                                   │
│                                                                      │
│  ★ JIT がループ不変条件を検出し、チェックをホイスティングする        │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 第9章: パフォーマンス特性とベストプラクティス

**対象層:** Java 層 / HotSpot C++ 層

### 9.1 JNI との速度比較

JMH (Java Microbenchmark Harness) を使った典型的なベンチマーク結果の傾向（環境依存のため概算）:

| 操作 | JNI | FFM API | 比率 |
|------|-----|---------|------|
| 引数なし関数呼び出し | ~70ns | ~15ns | FFM が **約4.7倍速** |
| int 引数1つの関数呼び出し | ~75ns | ~18ns | FFM が **約4.2倍速** |
| 構造体を引数に渡す | ~120ns | ~30ns | FFM が **約4倍速** |
| 文字列の受け渡し | ~200ns | ~50ns | FFM が **約4倍速** |
| コールバック (upcall) | ~150ns | ~80ns | FFM が **約1.9倍速** |

**注意:** これらの数値は JIT が十分にウォームアップした後の定常状態での値である。実際のパフォーマンスはワークロード、プラットフォーム、JVM バージョンによって大きく異なる。

### 9.2 メモリオーバーヘッド

| 項目 | JNI | FFM API |
|------|-----|---------|
| `MethodHandle` / JNI 関数テーブル | JNI 関数テーブル: 約 230 エントリ | MethodHandle: 呼び出しごとに 1 つ |
| ネイティブメモリ管理 | `malloc/free` を手動で呼ぶ | `Arena` がグループ管理 |
| 参照管理 | ローカル参照フレーム (~16-32 エントリ) | 不要 |
| メモリセグメント | `ByteBuffer` (ヘッダー ~48 バイト) | `MemorySegment` (ヘッダー ~32 バイト) |

### 9.3 ベストプラクティス

**① MethodHandle を再利用する**

`downcallHandle()` の結果は `static final` フィールドに保存して再利用する。MethodHandle の生成は比較的コストが高い。

```java
import java.lang.foreign.*;
import java.lang.invoke.*;

// MethodHandle は static final で保持する（推奨パターン）
static final Linker LINKER = Linker.nativeLinker();

static final MethodHandle STRLEN = LINKER.downcallHandle(
    LINKER.defaultLookup().find("strlen").orElseThrow(),
    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
);

void main() throws Throwable {
    try (var arena = Arena.ofConfined()) {
        // STRLEN を繰り返し呼び出す -- MethodHandle の生成コストは初回のみ
        for (String s : new String[]{"Hello", "World", "FFM"}) {
            MemorySegment cStr = arena.allocateFrom(s);
            long len = (long) STRLEN.invokeExact(cStr);
            IO.println(s + " → " + len + " バイト");
        }
    }
}
```

**② Arena のスコープを最小限にする**

```java
// 良い例: Arena のスコープが最小限
void processItems(List<String> items) throws Throwable {
    for (String item : items) {
        try (var arena = Arena.ofConfined()) { // 各反復で新しい Arena
            MemorySegment cStr = arena.allocateFrom(item);
            // ... ネイティブ呼び出し ...
        } // すぐに解放
    }
}

// 悪い例: Arena のスコープが不必要に広い
// void processItems(List<String> items) throws Throwable {
//     try (var arena = Arena.ofConfined()) {
//         for (String item : items) {
//             MemorySegment cStr = arena.allocateFrom(item); // 蓄積し続ける
//             // ... ネイティブ呼び出し ...
//         }
//     } // ここまで全セグメントが生存 → メモリ圧迫
// }
```

**③ 大量の小さな割り当てにはスライシングアロケータを使う**

セクション 2.5 で示した `SegmentAllocator.slicingAllocator()` を使うことで、`malloc()` の呼び出し回数を削減できる。

**④ invokeExact を使う**

`MethodHandle.invoke()` は暗黙の型変換を行うが、`invokeExact()` はその変換を行わない。`invokeExact()` の方が高速であり、型の不一致を即座に検出できる。

**⑤ confined Arena をデフォルトにする**

マルチスレッドアクセスが必要でない限り、`Arena.ofConfined()` を使う。同期オーバーヘッドがなく最も高速。

---

## 第10章: Vector API（概要）

**対象層:** Java 層

### 10.1 SIMD 操作の Java からの利用

**Vector API** は、CPU の SIMD (Single Instruction, Multiple Data) 命令を Java から利用するための API である。1 つの命令で複数のデータ要素を同時に処理することで、数値計算、画像処理、機械学習等のワークロードを高速化する。

```
┌──────────────────────────────────────────────────────────────────────┐
│  SIMD の概念                                                         │
│                                                                      │
│  スカラー演算（通常の Java コード）:                                  │
│  a[0] + b[0] → c[0]   ← 1命令                                      │
│  a[1] + b[1] → c[1]   ← 1命令                                      │
│  a[2] + b[2] → c[2]   ← 1命令                                      │
│  a[3] + b[3] → c[3]   ← 1命令                                      │
│  合計: 4命令                                                         │
│                                                                      │
│  SIMD 演算（Vector API）:                                            │
│  ┌────────────────────────┐                                         │
│  │ a[0] a[1] a[2] a[3]   │  ← ベクトルレジスタ A                    │
│  │  +    +    +    +      │  ← 1つの SIMD 命令                      │
│  │ b[0] b[1] b[2] b[3]   │  ← ベクトルレジスタ B                    │
│  │  ↓    ↓    ↓    ↓     │                                         │
│  │ c[0] c[1] c[2] c[3]   │  ← 結果レジスタ C                       │
│  └────────────────────────┘                                         │
│  合計: 1命令 ← 4倍速                                                │
│                                                                      │
│  対応 CPU 命令セット:                                                │
│  ・x86: SSE (128bit), AVX (256bit), AVX-512 (512bit)                │
│  ・AArch64: NEON (128bit), SVE (可変長)                              │
└──────────────────────────────────────────────────────────────────────┘
```

**Vector API のコード例（概念）:**

```java
// ★ 注意: Vector API は Incubator であり、--add-modules が必要
// java --add-modules jdk.incubator.vector VectorDemo.java

// import jdk.incubator.vector.*;

// void main() {
//     var species = FloatVector.SPECIES_256; // 256 ビット = float 8 個
//     float[] a = new float[]{1, 2, 3, 4, 5, 6, 7, 8};
//     float[] b = new float[]{8, 7, 6, 5, 4, 3, 2, 1};
//     float[] c = new float[8];
//
//     var va = FloatVector.fromArray(species, a, 0);
//     var vb = FloatVector.fromArray(species, b, 0);
//     var vc = va.add(vb); // SIMD 加算
//     vc.intoArray(c, 0);
//
//     // c = {9, 9, 9, 9, 9, 9, 9, 9}
// }
```

### 10.2 Java 25 時点の状態

Vector API は **Java 25 でもまだ Incubator** (JEP 489, 9th Incubator) である。正式化が遅れている主な理由:

1. **Valhalla プロジェクトとの連携**: Vector API の最終形態は、Value Classes (JEP 401) の導入に依存する。ベクトル型を value class として実装することで、ヒープ割り当てを完全に除去できる
2. **プラットフォーム固有の最適化**: 各 CPU アーキテクチャの SIMD 命令セットの違いを適切に抽象化するのが困難
3. **API の安定化**: 9 回の Incubator を経てもなお、API 設計の改善が続いている

```
  Vector API の正式化への道:

  現在 (Java 25)           将来
  ┌──────────┐           ┌──────────────────────────┐
  │ Incubator │  ──────▶ │ Valhalla (Value Classes)  │
  │ (9th)    │   待ち    │ が導入された後に正式化    │
  └──────────┘           └──────────────────────────┘
```

### 10.3 FFM API との関係

Vector API と FFM API は Project Panama の一部として連携する:

- **FFM API**: ネイティブメモリへのアクセスを提供
- **Vector API**: ネイティブメモリ上のデータに対する SIMD 演算を提供
- **組み合わせ**: FFM API でネイティブバッファを確保し、Vector API で高速処理する

```
┌──────────────────────────────────────────────────────────────────────┐
│  FFM API + Vector API の連携（将来像）                                │
│                                                                      │
│  ① FFM API でネイティブバッファを確保                                │
│     var arena = Arena.ofConfined();                                   │
│     var buffer = arena.allocate(JAVA_FLOAT, 1024);                   │
│                                                                      │
│  ② ネイティブ関数でデータを読み込み                                  │
│     readData.invokeExact(buffer, 1024L);                             │
│                                                                      │
│  ③ Vector API で SIMD 処理                                          │
│     var species = FloatVector.SPECIES_256;                           │
│     // MemorySegment から直接ベクトルをロード（ゼロコピー）           │
│     var vec = FloatVector.fromMemorySegment(species, buffer, 0, ...);│
│     var result = vec.mul(2.0f).add(1.0f); // SIMD 演算              │
│                                                                      │
│  ④ 結果をネイティブメモリに書き戻し                                  │
│     result.intoMemorySegment(buffer, 0, ...);                        │
│                                                                      │
│  → ネイティブメモリ上のデータをゼロコピーで SIMD 処理できる          │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 第11章: まとめ -- Q&A 形式の早見表

| 質問 | 回答 |
|------|------|
| FFM API は何を解決する？ | JNI の 5 つの問題（ボイラープレート、安全性、パフォーマンス、JIT 最適化、プラットフォーム依存ビルド）を解決する |
| いつ正式になった？ | Java 22 (JEP 454, 2024年3月) |
| C コードは必要？ | 不要。純粋な Java だけでネイティブ関数を呼び出せる |
| パフォーマンスは？ | JNI と同等以上。JIT がインライン化・最適化できるため、多くの場合 FFM API の方が高速 |
| メモリは安全？ | MemorySegment は境界チェック・ライフサイクル管理・スレッド制約を提供する |
| どの Arena を使うべき？ | 基本は `Arena.ofConfined()`。マルチスレッドなら `ofShared()`、GC 管理なら `ofAuto()` |
| MethodHandle の生成コストは？ | 高め。`static final` に保存して再利用すべき |
| jextract は JDK に含まれる？ | Java 25 時点では含まれない。別途ダウンロードが必要 |
| Vector API はいつ正式化？ | 未定。Valhalla (Value Classes) の導入後が有力 |
| JNI から移行すべき？ | 新規開発は FFM API を推奨。既存の安定した JNI コードは無理に移行しなくてよい |
| `--enable-native-access` は必須？ | ネイティブ関数呼び出し (restricted メソッド) には必須 |
| Windows でも動く？ | はい。Linker が Windows x64 / AArch64 の ABI をサポート |
| 構造体の受け渡しは？ | `MemoryLayout.structLayout()` で C の struct を定義し、VarHandle でフィールドにアクセス |
| コールバックは？ | `Linker.upcallStub()` で Java メソッドをネイティブの関数ポインタに変換 |

---

## 参考リンク・ソースパス

### JEP

| JEP | タイトル | バージョン |
|-----|---------|-----------|
| [JEP 412](https://openjdk.org/jeps/412) | Foreign Function & Memory API (Incubator) | Java 17 |
| [JEP 419](https://openjdk.org/jeps/419) | Foreign Function & Memory API (2nd Incubator) | Java 18 |
| [JEP 424](https://openjdk.org/jeps/424) | Foreign Function & Memory API (Preview) | Java 19 |
| [JEP 434](https://openjdk.org/jeps/434) | Foreign Function & Memory API (2nd Preview) | Java 20 |
| [JEP 442](https://openjdk.org/jeps/442) | Foreign Function & Memory API (3rd Preview) | Java 21 |
| [JEP 454](https://openjdk.org/jeps/454) | Foreign Function & Memory API | Java 22 |
| [JEP 489](https://openjdk.org/jeps/489) | Vector API (9th Incubator) | Java 25 |

### OpenJDK ソースパス

| パス | 説明 |
|------|------|
| `src/java.base/share/classes/java/lang/foreign/` | FFM API の公開インターフェース |
| `src/java.base/share/classes/jdk/internal/foreign/` | FFM API の内部実装 |
| `src/java.base/share/classes/jdk/internal/foreign/abi/` | ABI 固有の実装 |
| `src/hotspot/share/prims/foreign_globals.cpp` | HotSpot の FFM サポート |
| `src/hotspot/share/prims/upcallLinker.cpp` | upcall スタブ生成 |
| `src/hotspot/cpu/x86/upcallLinker_x86_64.cpp` | x86-64 の upcall 実装 |
| `src/hotspot/cpu/aarch64/upcallLinker_aarch64.cpp` | AArch64 の upcall 実装 |

### 外部リソース

| リソース | URL |
|---------|-----|
| Project Panama 公式ページ | https://openjdk.org/projects/panama/ |
| jextract リポジトリ | https://github.com/openjdk/jextract |
| FFM API Javadoc | https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/foreign/package-summary.html |

### 関連ドキュメント

| ドキュメント | 関連 |
|-------------|------|
| [jni-deep-dive.md](jni-deep-dive.md) | JNI の詳細解説（FFM API の前身技術） |
| [jvm-overview.md](../jvm-overview.md) | JVM 全体像 |
| [memory-basics.md](../memory/memory-basics.md) | Java のメモリ管理の基礎 |
| [execution-engine-deep-dive.md](../runtime/execution-engine-deep-dive.md) | JIT コンパイラとネイティブ呼び出しの最適化 |
