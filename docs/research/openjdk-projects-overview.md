# OpenJDK プロジェクト概要 -- Java プラットフォームの進化を牽引する取り組み

- [概要](#概要)
- [1. OpenJDK プロジェクトとは](#1-openjdk-プロジェクトとは)
  - [1.1 プロジェクトの位置づけ](#11-プロジェクトの位置づけ)
  - [1.2 JEP (JDK Enhancement Proposal) との関係](#12-jep-jdk-enhancement-proposal-との関係)
  - [1.3 プロジェクトのライフサイクル](#13-プロジェクトのライフサイクル)
- [2. プロジェクト早見表](#2-プロジェクト早見表)
- [3. 言語・ライブラリ系プロジェクト](#3-言語ライブラリ系プロジェクト)
  - [3.1 Project Amber -- Java 言語の進化](#31-project-amber----java-言語の進化)
  - [3.2 Project Valhalla -- 値型とジェネリクスの刷新](#32-project-valhalla----値型とジェネリクスの刷新)
  - [3.3 Project Panama -- ネイティブコードとの接続](#33-project-panama----ネイティブコードとの接続)
  - [3.4 Project Babylon -- コードリフレクション](#34-project-babylon----コードリフレクション)
- [4. ランタイム・パフォーマンス系プロジェクト](#4-ランタイムパフォーマンス系プロジェクト)
  - [4.1 Project Loom -- 軽量並行処理](#41-project-loom----軽量並行処理)
  - [4.2 Project Leyden -- 起動時間とウォームアップの最適化](#42-project-leyden----起動時間とウォームアップの最適化)
  - [4.3 Project Galahad -- GraalVM JIT の統合](#43-project-galahad----graalvm-jit-の統合)
  - [4.4 Project Lilliput -- オブジェクトヘッダの圧縮](#44-project-lilliput----オブジェクトヘッダの圧縮)
- [5. GC プロジェクト](#5-gc-プロジェクト)
  - [5.1 Project ZGC -- 超低レイテンシ GC](#51-project-zgc----超低レイテンシ-gc)
  - [5.2 Project Shenandoah -- 低ポーズ GC](#52-project-shenandoah----低ポーズ-gc)
- [6. プラットフォーム・UI 系プロジェクト](#6-プラットフォームui-系プロジェクト)
  - [6.1 Project Wakefield -- Wayland サポート](#61-project-wakefield----wayland-サポート)
  - [6.2 Project Lanai -- macOS Metal レンダリング](#62-project-lanai----macos-metal-レンダリング)
  - [6.3 Project Detroit -- JavaFX の次世代](#63-project-detroit----javafx-の次世代)
- [7. その他の注目プロジェクト](#7-その他の注目プロジェクト)
  - [7.1 Project Coin (完了)](#71-project-coin-完了)
  - [7.2 Project Jigsaw (完了)](#72-project-jigsaw-完了)
  - [7.3 Project Portola](#73-project-portola)
  - [7.4 Project Sumatra](#74-project-sumatra)
- [8. プロジェクト間の関係図](#8-プロジェクト間の関係図)
- [9. Java 25 で利用可能な機能まとめ](#9-java-25-で利用可能な機能まとめ)
- [参考リンク](#参考リンク)

---

## 概要

本ドキュメントは、OpenJDK コミュニティで進行中の主要プロジェクトを俯瞰するためのガイドである。各プロジェクトの目的・成果物・Java 25 時点でのステータスを整理し、Java プラットフォーム全体の進化の方向性を理解することを目的とする。

**基準バージョン:** Java 25（2025年9月 GA、LTS）

**対象ランタイム:** Azul Zulu 25 (OpenJDK 25 ベース / HotSpot VM)

**位置づけ:** このドキュメントは各プロジェクトの「概要地図」であり、個別の深掘りは以下の詳細ドキュメントに委譲する。

| 対象 | 詳細ドキュメント |
|------|-----------------|
| GC 全般 | [gc-deep-dive.md](memory/gc-deep-dive.md) |
| G1GC | [g1gc-deep-dive.md](memory/g1gc-deep-dive.md) |
| ZGC | [zgc-deep-dive.md](memory/zgc-deep-dive.md) |
| JNI / ネイティブ連携 | [jni-deep-dive.md](native/jni-deep-dive.md) |
| Virtual Threads (Loom) | [virtual-threads-deep-dive.md](runtime/virtual-threads-deep-dive.md) |
| Leyden / AOT Cache | [leyden-aot-cache-deep-dive.md](startup/leyden-aot-cache-deep-dive.md) |
| FFM API (Panama) | [ffm-api-deep-dive.md](native/ffm-api-deep-dive.md) |

---

## 1. OpenJDK プロジェクトとは

### 1.1 プロジェクトの位置づけ

OpenJDK における「プロジェクト」は、**Java プラットフォームの特定の領域を改善・拡張するための長期的な取り組み**である。各プロジェクトは OpenJDK コミュニティの中で提案・承認され、1 名以上のリード（Lead）が推進する。

プロジェクトは単なる「機能追加」ではなく、**Java の根本的なアーキテクチャや設計思想に関わる大規模な変更**を扱うことが多い。1 つのプロジェクトが複数の JEP を生み出し、それらが複数の Java バージョンにまたがって段階的にリリースされる。

### 1.2 JEP (JDK Enhancement Proposal) との関係

```
Project（長期的な取り組み）
  └── JEP（具体的な提案・仕様）
        └── Preview / Incubator（段階的導入）
              └── Standard（正式リリース）
```

- **Project** はビジョンと方向性を定義する
- **JEP** はそのビジョンを実現する具体的な個別提案
- **Preview Feature** は正式化前のフィードバック収集段階（`--enable-preview` で有効化）
- **Incubator Module** は API の試験的導入（`jdk.incubator.*` パッケージ）
- **Standard Feature** は正式に Java SE 仕様に組み込まれた機能

### 1.3 プロジェクトのライフサイクル

```
提案 → 承認 → 開発（JEP の提出と実装）→ プレビュー → 正式化 → 完了/継続
```

プロジェクトによっては完了するもの（Project Coin, Jigsaw）もあれば、継続的に進化するもの（Amber, Valhalla）もある。

---

## 2. プロジェクト早見表

| プロジェクト | 目標 | 領域 | Java 25 時点の状態 |
|:---|:---|:---|:---|
| **Amber** | Java 言語の段階的進化 | 言語 | 継続中（多数正式化済み） |
| **Valhalla** | 値型・プリミティブクラス・特殊化ジェネリクス | 言語/VM | 進行中（プレビュー段階） |
| **Panama** | ネイティブコードとの安全な接続 | ライブラリ/VM | ほぼ完了（FFM API 正式化済み） |
| **Babylon** | コードリフレクションによる GPU/ML 対応 | 言語/VM | 初期段階（プロトタイプ） |
| **Loom** | 軽量スレッドと構造化並行処理 | VM/ライブラリ | ほぼ完了（VT 正式化済み） |
| **Leyden** | 起動時間・ウォームアップの最適化 | VM | 進行中（JEP 483 は Java 24 で正式化） |
| **Galahad** | GraalVM JIT の OpenJDK 統合 | VM/JIT | 進行中（実験的） |
| **Lilliput** | オブジェクトヘッダの圧縮（64-bit ヘッダ） | VM | 進行中（実験的） |
| **ZGC** | サブミリ秒 GC ポーズ | GC | 完了（正式化済み） |
| **Shenandoah** | 低ポーズ GC（Red Hat 主導） | GC | 安定稼働中 |
| **Wakefield** | Linux Wayland ネイティブサポート | UI/AWT | 進行中 |
| **Lanai** | macOS Metal レンダリングパイプライン | UI/2D | 進行中 |
| **Detroit** | JavaFX の次世代 | UI | 調査段階 |
| **Coin** | 小規模な言語改善 | 言語 | **完了**（Java 7 で成果統合） |
| **Jigsaw** | モジュールシステム | 言語/VM | **完了**（Java 9 で正式化） |
| **Portola** | Alpine Linux (musl) サポート | ポート | 統合済み |

---

## 3. 言語・ライブラリ系プロジェクト

### 3.1 Project Amber -- Java 言語の進化

**リード:** Brian Goetz (Java Language Architect)

**目標:** Java 言語に小規模から中規模の改善を段階的に導入し、**コードの可読性と記述力を向上**させる。「大きなビジョン」を掲げるのではなく、開発者が日常的に直面するボイラープレートや表現力の限界を、プラグマティックに解消する。

**解決する問題:**
- Java コードの冗長さ（getter/setter、型宣言の繰り返し）
- パターンマッチングの欠如
- switch 文の柔軟性不足
- 小さなプログラムを書く際の儀式的コード（`public static void main(String[] args)` 等）

#### 主要 JEP 一覧

| JEP | タイトル | 導入 | 正式化 | 状態 |
|:---:|:---|:---:|:---:|:---|
| 286 | Local-Variable Type Inference (`var`) | 10 | 10 | **正式** |
| 361 | Switch Expressions | 12P | 14 | **正式** |
| 394 | Pattern Matching for instanceof | 14P | 16 | **正式** |
| 395 | Records | 14P | 16 | **正式** |
| 409 | Sealed Classes | 15P | 17 | **正式** |
| 440 | Record Patterns | 19P | 21 | **正式** |
| 441 | Pattern Matching for switch | 17P | 21 | **正式** |
| 456 | Unnamed Variables & Patterns (`_`) | 21P | 22 | **正式** |
| 477 | Implicitly Declared Classes and Instance Main Methods | 21P | 25 | **正式** |
| 492 | Flexible Constructor Bodies | 22P | 25 | **正式** |
| 455 | Primitive Types in Patterns, instanceof, and switch | 23P | -- | Preview (Java 25) |
| 488 | Primitive Types in Patterns, instanceof, and switch (2nd) | 24P | -- | Preview (Java 25) |
| 494 | Module Import Declarations | 23P | 25 | **正式** |
| 495 | Simple Source Files and Instance Main Methods | 25 | 25 | **正式** |
| 482 | Flexible Constructor Bodies (2nd Preview) | 24P | 25 | **正式** |

> **P** = Preview として導入されたバージョン

**Java 25 時点のステータス:** 継続中。多数の成果が正式化済み。プリミティブ型のパターンマッチング（JEP 455/488）がまだプレビュー段階。

**実用上のインパクト:**
- レコード、シールドクラス、パターンマッチングにより**代数的データ型プログラミング**が可能に
- `var` による簡潔な型宣言
- `void main()` と暗黙クラスにより、学習の初期障壁が劇的に低下
- switch 式 + パターンマッチングで複雑な条件分岐がスキャナブルに

> **本プロジェクト (jdk-core) との関連:** すべての実験用 Java ファイルが Amber の成果（JEP 477/495、レコード、パターンマッチング等）を活用している。

---

### 3.2 Project Valhalla -- 値型とジェネリクスの刷新

**リード:** Brian Goetz

**目標:** Java のオブジェクトモデルとジェネリクスシステムを根本から拡張し、**「コードはジェネリクスのように、データはフラットに（Codes like a class, works like an int）」** を実現する。

**解決する問題:**
- プリミティブ型（`int`, `double` 等）とオブジェクト型の断絶
- ジェネリクスでプリミティブ型を扱えない（`List<int>` が不可能）
- 小さな値の集合（座標、通貨金額等）にオブジェクトヘッダのオーバーヘッドがかかる
- autoboxing のパフォーマンスコスト（`Integer` ↔ `int`）

#### 主要 JEP 一覧

| JEP | タイトル | 状態 (Java 25) |
|:---:|:---|:---|
| 401 | Value Classes and Objects (Preview) | Preview (Java 24 で導入) |
| 402 | Enhanced Primitive Boxing (Preview) | Preview (Java 24 で導入) |
| -- | Null-Restricted and Nullable Types | 未 JEP 化（設計中） |
| -- | Universal Generics (`List<int>`) | 未 JEP 化（長期目標） |

**Java 25 時点のステータス:** 進行中。値クラス（Value Classes）が Java 24 で Preview として導入され、Java 25 でも引き続き Preview 段階にある（JEP 401）。Universal Generics は長期目標として研究が続いている。

**技術的な背景:**

値クラス（Value Class）は、`identity` を持たないクラスである。通常の Java オブジェクトは「同一性（identity）」を持ち、`==` 演算子はオブジェクトの参照が同じかどうかを比較する。値クラスはこの同一性を放棄することで、JVM がメモリレイアウトを最適化できる余地を得る。

```
通常のオブジェクト:  [mark word | klass pointer | fields...]  ← ヒープ上に確保
値クラス:            [fields...]                               ← フラット化の余地あり
```

**実用上のインパクト:**
- `List<int>` のような真のプリミティブジェネリクスが将来的に可能に
- 座標 (`Point`)、通貨金額 (`Money`)、複素数 (`Complex`) 等の小さな値型が高効率に
- autoboxing/unboxing のオーバーヘッド削減
- 科学計算・金融・ゲーム開発等でのパフォーマンス向上

> **注意:** Valhalla は Java プラットフォーム史上最も野心的なプロジェクトの一つであり、JVM のバイトコード仕様、メモリモデル、ジェネリクスの型消去（Type Erasure）といった根本的な部分に手を入れる。完全な実現には複数バージョンを要する見込み。

---

### 3.3 Project Panama -- ネイティブコードとの接続

**リード:** Maurizio Cimadamore

**目標:** Java からネイティブコード（C/C++ ライブラリ）やネイティブデータ（off-heap メモリ）に**安全かつ高効率に**アクセスする手段を提供する。JNI の複雑さと危険性を解消する。

**解決する問題:**
- JNI が複雑すぎる（C/C++ コードの記述、ヘッダ生成、ネイティブビルド）
- JNI 経由のメモリ管理が危険（ダングリングポインタ、メモリリーク）
- ネイティブライブラリの呼び出しオーバーヘッド
- off-heap メモリの安全な管理手段の欠如

#### 主要 JEP 一覧

| JEP | タイトル | 導入 | 正式化 | 状態 |
|:---:|:---|:---:|:---:|:---|
| 412 | Foreign Function & Memory API (Incubator) | 17I | -- | 廃止（後継へ統合） |
| 434 | Foreign Function & Memory API (2nd Preview) | 20P | -- | 廃止（後継へ統合） |
| 454 | Foreign Function & Memory API | 19I | 22 | **正式** |
| 442 | Foreign Function & Memory API (3rd Preview) | 21P | 22 | **正式**（JEP 454 で正式化） |
| 338 | Vector API (Incubator) | 16I | -- | Incubator (Java 25) |
| 489 | Vector API (9th Incubator) | 25I | -- | Incubator (Java 25) |

> **I** = Incubator として導入されたバージョン

**Java 25 時点のステータス:**
- **FFM API（Foreign Function & Memory API）:** Java 22 で正式化済み。JNI に代わるネイティブアクセス手段として利用可能
- **Vector API:** Java 25 時点でまだ Incubator（9th Incubator、JEP 489）。Valhalla の値型が安定するまで正式化は困難とされている

**処理フロー（FFM API）:**

```
Java 層: MethodHandle を使用してネイティブ関数を呼び出す
   │
   ├── Linker.nativeLinker().downcallHandle(...)
   │      ネイティブ関数のメソッドハンドルを取得
   │
   ├── Arena.ofConfined() / Arena.ofAuto()
   │      off-heap メモリのライフサイクル管理
   │
   └── MemorySegment
          ネイティブメモリへの安全なアクセス
```

**実用上のインパクト:**
- JNI を書かずに C ライブラリを直接呼び出せる
- `jextract` ツールで C ヘッダから Java バインディングを自動生成
- Arena ベースのメモリ管理で off-heap メモリのリーク防止
- Vector API が正式化されれば、SIMD 命令による高速データ処理が標準 API で可能に

> **関連ドキュメント:** [jni-deep-dive.md](native/jni-deep-dive.md) で JNI との比較を解説。FFM API の詳細は [ffm-api-deep-dive.md](native/ffm-api-deep-dive.md) に委譲。

---

### 3.4 Project Babylon -- コードリフレクション

**リード:** Paul Sandoz

**目標:** Java コード（ラムダ式やメソッド参照等）の**中間表現（IR: Intermediate Representation）にプログラムからアクセス**できるようにし、GPU オフロード、分散コンピューティング、機械学習フレームワーク等への変換を可能にする。

**解決する問題:**
- Java のラムダ式の「中身」にプログラムからアクセスできない
- GPU カーネルへの変換（CUDA/OpenCL）を Java から自然に行えない
- LINQ のようなクエリ式を Java で表現できない
- ML フレームワーク（PyTorch 等）の計算グラフを Java コードから構築できない

**Java 25 時点のステータス:** 初期段階。JEP として正式提出された提案はまだない。プロトタイプ実装が OpenJDK リポジトリの Babylon ブランチで開発されている。

**技術的な概要:**

Babylon は「コードモデル（Code Model）」という概念を導入する。メソッドやラムダ式をコンパイル時に IR として保存し、実行時にその IR をプログラムから読み取り・変換できるようにする。

```
Java ソース → javac → バイトコード + コードモデル（IR）
                                         │
                                         ├── GPU カーネルに変換
                                         ├── SQL クエリに変換
                                         └── 計算グラフに変換
```

**実用上のインパクト（将来）:**
- Java でのGPU プログラミングが自然な構文で可能に
- LINQ のようなデータベースクエリ式
- ML フレームワークとの深い統合
- 分散コンピューティングでのコードシリアライゼーション

> **Panama との関係:** Panama がネイティブ「関数呼び出し」を扱うのに対し、Babylon は Java「コード自体」の変換・リフレクションを扱う。GPU オフロードのシナリオでは両者が補完的に機能する可能性がある。

---

## 4. ランタイム・パフォーマンス系プロジェクト

### 4.1 Project Loom -- 軽量並行処理

**リード:** Ron Pressler

**目標:** JVM に**仮想スレッド（Virtual Threads）** を導入し、スレッドベースの並行処理モデルを維持しながら、リアクティブプログラミングに匹敵するスケーラビリティを実現する。

**解決する問題:**
- OS スレッドは重い（1 スレッドあたり約 1MB のスタック、コンテキストスイッチのコスト）
- 高スループットサーバーで「1 リクエスト = 1 スレッド」モデルがスケールしない
- リアクティブプログラミング（CompletableFuture チェーン、RxJava）のコードが読みにくい
- スタックトレースが分断され、デバッグが困難

#### 主要 JEP 一覧

| JEP | タイトル | 導入 | 正式化 | 状態 |
|:---:|:---|:---:|:---:|:---|
| 425 | Virtual Threads (Preview) | 19P | 21 | **正式** |
| 444 | Virtual Threads | 21 | 21 | **正式** |
| 453 | Structured Concurrency (Preview) | 19I | 25 | **正式** (JEP 505) |
| 505 | Structured Concurrency | 25 | 25 | **正式** |
| 446 | Scoped Values (Preview) | 20I | 25 | **正式** (JEP 502) |
| 502 | Scoped Values | 25 | 25 | **正式** |
| 491 | Synchronize Virtual Threads without Pinning | 24 | 25 | **正式** |

**Java 25 時点のステータス:**
- **Virtual Threads:** Java 21 で正式化済み。本番利用可能
- **Structured Concurrency:** Java 25 で JEP 505 として正式化 (GA)。本番利用可能
- **Scoped Values:** Java 25 で JEP 502 として正式化 (GA)。本番利用可能
- **Virtual Thread Pinning の改善:** JEP 491 で synchronized ブロック内でのピン止め問題が Java 25 で解消

**仮想スレッドの動作概要:**

```
Java 層: Thread.ofVirtual().start(runnable)
   │
   ├── 仮想スレッドは ForkJoinPool の「キャリアスレッド」上で実行
   │
HotSpot C++ 層:
   ├── ブロッキング操作時に自動的に「アンマウント」（キャリアから退避）
   │      → キャリアスレッドは別の仮想スレッドに再利用される
   │
   └── ブロッキング解除時に「マウント」（キャリアに復帰）
```

**実用上のインパクト:**
- 数百万の同時接続を「1 リクエスト = 1 スレッド」で処理可能
- 既存のブロッキング API（JDBC、HTTP Client 等）がそのまま活用できる
- リアクティブフレームワーク不要で高スループットを実現
- デバッグ・プロファイリングが従来のスレッドと同様に可能

> **関連ドキュメント:** 仮想スレッドの内部実装詳細は [virtual-threads-deep-dive.md](runtime/virtual-threads-deep-dive.md) に委譲。

---

### 4.2 Project Leyden -- 起動時間とウォームアップの最適化

**リード:** Mark Reinhold

**目標:** Java アプリケーションの**起動時間、ウォームアップ時間、フットプリント（メモリ使用量）** を大幅に改善する。GraalVM Native Image のような AOT アプローチを、HotSpot VM の中で段階的に実現する。

**解決する問題:**
- Java アプリケーションの起動が遅い（特にフレームワーク利用時）
- JIT コンパイルのウォームアップに時間がかかる
- クラウドネイティブ環境（コンテナ、サーバーレス）では起動速度が重要
- CDS (Class Data Sharing) だけでは限界がある

#### 主要 JEP 一覧

| JEP | タイトル | 導入 | 状態 (Java 25) |
|:---:|:---|:---:|:---|
| 483 | Ahead-of-Time Class Loading & Linking | 24 | **正式** (Java 24) |
| 490 | ZGC: Remove the Non-Generational Mode | 24 | **正式** (Java 24) |
| -- | AOT Code Cache | -- | 設計中（将来の JEP） |
| -- | AOT Compilation | -- | 長期目標 |

**Java 25 時点のステータス:** 進行中。JEP 483（AOT Class Loading & Linking）は Java 24 で正式化 (GA) された。これは Leyden の最初の成果物であり、CDS の進化形として位置づけられる。

**技術的な概要:**

Leyden は「制約の凝縮（Condensation of Constraints）」というアプローチを取る。アプリケーションの実行前に可能な限り多くの情報を確定させ、起動時のワークロードを削減する。

```
従来の起動フロー:
  JVM 起動 → クラスロード → リンク → 初期化 → インタプリタ → JIT → 最適化

Leyden の目標:
  ビルド時:  クラスロード → リンク → (一部)初期化 → AOT コンパイル
  実行時:    [キャッシュからロード] → 即座に最適化されたコードで実行
```

**実用上のインパクト:**
- Spring Boot アプリケーション等の起動時間の大幅短縮
- コンテナ環境でのコールドスタート改善
- サーバーレス環境での実用性向上
- GraalVM Native Image の制約（リフレクション制限等）なしに高速起動を実現

> **関連ドキュメント:** Leyden の AOT Cache メカニズム詳細は [leyden-aot-cache-deep-dive.md](startup/leyden-aot-cache-deep-dive.md) に委譲。

---

### 4.3 Project Galahad -- GraalVM JIT の統合

**リード:** Doug Simon (Oracle)

**目標:** GraalVM の JIT コンパイラ（Graal）を OpenJDK のメインラインに**統合**し、HotSpot の C2 コンパイラの代替として利用可能にする。

**解決する問題:**
- C2 コンパイラは C++ で書かれており、保守・拡張が困難
- Graal は Java で書かれた最新の最適化コンパイラだが、OpenJDK とは別プロジェクト
- GraalVM と OpenJDK のユーザーが分断されている
- 新しい最適化手法を C2 に導入するコストが高い

**Java 25 時点のステータス:** 進行中（実験的段階）。具体的な JEP はまだ提出されていない。OpenJDK 内で Graal をビルド可能にするための基盤整備が進められている。

**技術的な背景:**

HotSpot の JIT コンパイラは現在 2 段構成になっている:
- **C1 (Client Compiler):** 高速コンパイル、基本的な最適化
- **C2 (Server Compiler):** 低速だが高度に最適化されたコードを生成

Galahad は、C2 を Graal で置き換えることを目指す:
- **C1:** 変更なし（高速コンパイルは引き続き C1 が担当）
- **Graal:** C2 の代わりに高度な最適化を担当。Java で書かれているため保守性が高い

**実用上のインパクト:**
- 最新の最適化技術（Partial Escape Analysis, Speculative Optimization 等）を標準 JDK で利用可能に
- JIT コンパイラ自体が Java で書かれるため、Java 開発者がコンパイラに貢献しやすくなる
- 長期的には C2 の技術的負債を解消

> **注意:** Galahad は長期プロジェクトであり、Java 25 時点で実用的な成果は標準 JDK に含まれていない。

---

### 4.4 Project Lilliput -- オブジェクトヘッダの圧縮

**リード:** Roman Kennke (Red Hat)

**目標:** Java オブジェクトのヘッダサイズを**128 ビット（16 バイト）から 64 ビット（8 バイト）に圧縮**し、ヒープメモリの使用効率を向上させる。

**解決する問題:**
- すべての Java オブジェクトに 128 ビットのヘッダが付与される（mark word 64bit + klass pointer 64bit）
- CompressedOops 使用時でも 96 ビット（12 バイト + パディングで 16 バイト）
- 小さなオブジェクト（`Boolean`, `Byte`, 空の `Object` 等）ではヘッダのオーバーヘッドがデータより大きい
- 大量のオブジェクトを扱うアプリケーション（コレクション等）でメモリ効率が悪い

**Java 25 時点のステータス:** 進行中（実験的段階）。JEP として正式提出された提案はまだないが、OpenJDK のメインラインに実験的なコードが徐々にマージされている。

**技術的な概要:**

```
現在のオブジェクトレイアウト (64-bit JVM, CompressedOops):
  [mark word: 64bit | compressed klass: 32bit | gap: 32bit | fields...]
  = 最小 16 バイト

Lilliput の目標:
  [compact header: 64bit | fields...]
  = 最小 8 バイト
```

mark word にはロック情報、GC 年齢、ハッシュコード等が格納されている。Lilliput はこれらの情報を 64 ビットに凝縮するために、ロック実装の見直しやハッシュコードの格納方法の変更が必要になる。

**HotSpot C++ 層での変更:**
- `src/hotspot/share/oops/markWord.hpp` -- mark word のビットレイアウト再設計
- `src/hotspot/share/oops/oop.hpp` -- オブジェクトヘッダのアクセス方法
- ロック実装（thin lock / inflated lock）の刷新

**実用上のインパクト:**
- ヒープメモリの使用量が 10-20% 削減される見込み
- 特にオブジェクト数が多いワークロード（キャッシュ、コレクション）で効果大
- GC の効率向上（スキャンするメモリ量が減少）
- 変更は JVM 内部に閉じるため、**既存の Java コードの変更は不要**

---

## 5. GC プロジェクト

### 5.1 Project ZGC -- 超低レイテンシ GC

**リード:** Per Lidén (Oracle)

**目標:** GC のポーズ時間をワーキングセットのサイズに関係なく**サブミリ秒に抑える**スケーラブルな GC を実現する。

#### 主要 JEP 一覧

| JEP | タイトル | 導入 | 正式化 | 状態 |
|:---:|:---|:---:|:---:|:---|
| 333 | ZGC: A Scalable Low-Latency GC (Experimental) | 11E | -- | 廃止（後継へ統合） |
| 377 | ZGC: A Scalable Low-Latency GC | 15 | 15 | **正式** |
| 439 | Generational ZGC | 21 | -- | デフォルト化 |
| 474 | ZGC: Generational Mode by Default | 23 | 23 | **正式** |
| 490 | ZGC: Remove the Non-Generational Mode | 24 | 24 | **正式** |

> **E** = Experimental として導入

**Java 25 時点のステータス:** 完了（実質的に安定）。Generational ZGC がデフォルトモードとなり、Non-Generational モードは Java 24 で削除された（JEP 490）。

**特徴:**
- **Colored Pointers（着色ポインタ）:** オブジェクト参照の未使用ビットに GC メタデータを埋め込む
- **Load Barrier:** オブジェクトアクセス時にバリアを挿入し、ポインタの修正を遅延実行
- **Concurrent（並行処理）:** ほぼすべてのフェーズがアプリケーションスレッドと並行して動作
- **Generational:** 若い世代と古い世代を分離し、効率的に収集

> **関連ドキュメント:** 内部実装の詳細は [zgc-deep-dive.md](memory/zgc-deep-dive.md) を参照。

---

### 5.2 Project Shenandoah -- 低ポーズ GC

**リード:** Red Hat チーム

**目標:** ZGC と同様に低ポーズ GC を実現するが、**Red Hat 主導のコミュニティドリブン**な開発モデルで進める。

**Java 25 時点のステータス:** 安定稼働中。OpenJDK mainline に統合済み。Oracle JDK には含まれないが、Red Hat 系ディストリビューション（RHEL, Fedora）や一部のベンダー JDK で利用可能。

**ZGC との違い:**
| 特性 | ZGC | Shenandoah |
|:---|:---|:---|
| ポインタ方式 | Colored Pointers | Brooks Pointers (転送ポインタ) |
| バリア方式 | Load Barrier | Load + Store Barrier |
| 世代 | Generational (Java 23+) | Non-Generational（世代化は開発中） |
| 主な推進者 | Oracle | Red Hat |
| Oracle JDK | 含む | **含まない** |

> **Zulu での利用:** Azul Zulu では ZGC と Shenandoah の両方が利用可能。

> **関連ドキュメント:** GC 全般の解説は [gc-deep-dive.md](memory/gc-deep-dive.md) を参照。

---

## 6. プラットフォーム・UI 系プロジェクト

### 6.1 Project Wakefield -- Wayland サポート

**リード:** Philip Race (Oracle)

**目標:** Linux デスクトップの新しいディスプレイサーバープロトコル **Wayland** を AWT/Swing でネイティブにサポートする。

**解決する問題:**
- Linux デスクトップが X11 から Wayland に移行中
- 現在の AWT/Swing は XWayland（互換レイヤー）経由で動作しており、パフォーマンスや機能に制限がある
- ハイ DPI、フラクショナルスケーリング、タッチ入力等の最新機能に対応できない

**Java 25 時点のステータス:** 進行中。OpenJDK の Wakefield ブランチで開発が進められている。Java 25 時点では標準 JDK にマージされる段階には至っていない。

---

### 6.2 Project Lanai -- macOS Metal レンダリング

**リード:** Philip Race (Oracle)

**目標:** macOS での 2D レンダリングパイプラインを **OpenGL から Apple Metal に移行**する。

**解決する問題:**
- Apple が macOS から OpenGL を非推奨化（deprecated）した
- OpenGL ベースのレンダリングが将来的に macOS で動作しなくなる可能性
- Metal はより高性能で、macOS の GPU 機能を最大限活用できる

| JEP | タイトル | 導入 | 状態 |
|:---:|:---|:---:|:---|
| 382 | New macOS Rendering Pipeline | 17 | **正式** |

**Java 25 時点のステータス:** 主要な成果は JEP 382 として Java 17 で統合済み。Metal ベースのレンダリングパイプラインが利用可能。プロジェクト自体は追加の最適化・バグ修正を継続。

---

### 6.3 Project Detroit -- JavaFX の次世代

**目標:** JavaFX の次世代フレームワークを模索する。JavaFX 自体は Java 11 以降 OpenJDK から分離され、OpenJFX として独立プロジェクトになっている。

**Java 25 時点のステータス:** 調査・議論段階。具体的な JEP や成果物は出ていない。JavaFX の将来的な方向性について議論が続いているが、活発な開発活動は確認できない。

---

## 7. その他の注目プロジェクト

### 7.1 Project Coin (完了)

**目標:** Java 言語への小規模な変更（「小銭 = coin」のような小さな改善）を集めて導入する。

**成果（Java 7）:**
- ダイヤモンド演算子 (`<>`)
- try-with-resources
- マルチキャッチ (`catch (A | B e)`)
- 文字列 switch
- バイナリリテラル、数値リテラルのアンダースコア

**ステータス:** **完了**。Project Coin の役割は実質的に Project Amber に引き継がれた。

---

### 7.2 Project Jigsaw (完了)

**リード:** Mark Reinhold

**目標:** Java プラットフォームにモジュールシステム（Java Platform Module System, JPMS）を導入する。

| JEP | タイトル | 正式化 |
|:---:|:---|:---:|
| 261 | Module System | 9 |
| 282 | jlink: The Java Linker | 9 |

**成果（Java 9）:**
- `module-info.java` によるモジュール宣言
- `jlink` によるカスタムランタイムイメージの作成
- JDK 自体のモジュール化（`java.base`, `java.sql` 等）

**ステータス:** **完了**。モジュールシステムは Java 9 で正式導入済み。

---

### 7.3 Project Portola

**目標:** **Alpine Linux**（musl libc）上での OpenJDK の動作をサポートする。

**ステータス:** 統合済み。Alpine Linux / musl libc 向けのビルドが OpenJDK で正式サポートされている。Docker の軽量コンテナイメージ（`eclipse-temurin:*-alpine`）で利用されている。

---

### 7.4 Project Sumatra

**目標:** Java から **GPU を活用した並列計算**を行えるようにする。

**ステータス:** 活動停止。GPU 活用の方向性は Project Babylon のコードリフレクションアプローチに引き継がれている。

---

## 8. プロジェクト間の関係図

```
┌─────────────────────────────────────────────────────────────────┐
│                    Java プラットフォームの進化                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌── 言語 ──────────────────────────────────────────────┐       │
│  │                                                      │       │
│  │  Amber ──────────→ Valhalla                          │       │
│  │  (レコード,           (値型が Amber の                 │       │
│  │   パターンマッチ)      パターンマッチと統合)            │       │
│  │                                                      │       │
│  │  Babylon ←──────── Panama                            │       │
│  │  (コードリフレクション)  (FFM API が                   │       │
│  │        ↑              Babylon の基盤に)               │       │
│  │        │                                             │       │
│  │     Sumatra (活動停止、Babylon に継承)                 │       │
│  └──────────────────────────────────────────────────────┘       │
│                                                                 │
│  ┌── ランタイム ────────────────────────────────────────┐       │
│  │                                                      │       │
│  │  Loom ───────────→ Leyden                            │       │
│  │  (仮想スレッド)       (VT + AOT で                    │       │
│  │                       クラウドネイティブ最適化)        │       │
│  │                                                      │       │
│  │  Galahad ──────── Leyden                             │       │
│  │  (Graal JIT 統合)   (AOT コンパイルの                 │       │
│  │                       基盤技術として連携)              │       │
│  │                                                      │       │
│  │  Lilliput                                            │       │
│  │  (オブジェクトヘッダ圧縮 → 全体のメモリ効率向上)       │       │
│  └──────────────────────────────────────────────────────┘       │
│                                                                 │
│  ┌── GC ────────────────────────────────────────────────┐       │
│  │                                                      │       │
│  │  ZGC ←──────────→ Shenandoah                        │       │
│  │  (Oracle 主導)        (Red Hat 主導)                  │       │
│  │  低レイテンシ GC の 2 つのアプローチ                   │       │
│  │                                                      │       │
│  │       ↕ Valhalla の値型は GC に影響                   │       │
│  └──────────────────────────────────────────────────────┘       │
│                                                                 │
│  ┌── UI / プラットフォーム ─────────────────────────────┐       │
│  │  Wakefield (Wayland) / Lanai (Metal) / Detroit (FX)  │       │
│  │  Portola (Alpine Linux)                              │       │
│  └──────────────────────────────────────────────────────┘       │
│                                                                 │
│  ┌── 完了 ─────────────────────────────────────────────┐       │
│  │  Coin (Java 7) → Amber に継承                        │       │
│  │  Jigsaw (Java 9) → モジュールシステムとして定着        │       │
│  └──────────────────────────────────────────────────────┘       │
│                                                                 │
│  ※ Valhalla は言語・VM・GC すべてに影響する横断的プロジェクト    │
│  ※ Panama の Vector API は Valhalla の値型正式化を待っている     │
│  ※ Leyden は Loom (VT) と Galahad (Graal) の成果を活用する     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**主要な依存関係:**

1. **Valhalla → Panama (Vector API):** Vector API が Incubator から抜け出すには、Valhalla の値型が必要。Vector のような小さな固定サイズのデータを値型で表現することで、SIMD 最適化が完全に機能する。

2. **Amber → Valhalla:** Amber で導入されたパターンマッチングやレコードの仕組みが、Valhalla の値クラスと統合される。値クラスもパターンマッチングの対象となる。

3. **Sumatra → Babylon:** GPU オフロードの目標が Sumatra から Babylon のコードリフレクションアプローチに引き継がれた。

4. **Panama → Babylon:** FFM API によるネイティブメモリ管理が、Babylon の GPU カーネル実行基盤として活用される可能性がある。

5. **Loom + Leyden:** 仮想スレッドのクラウドネイティブ利用と、Leyden の起動高速化が組み合わさることで、Java のサーバーレス環境での競争力が向上する。

6. **Galahad → Leyden:** Graal JIT の AOT コンパイル能力が、Leyden の起動時間最適化に活用される可能性がある。

7. **Valhalla → GC (ZGC / Shenandoah / G1GC):** 値型は identity を持たないため、GC のオブジェクトスキャンやレイアウトに影響を与える。各 GC は値型に対応する必要がある。

---

## 9. Java 25 で利用可能な機能まとめ

Java 25 は LTS（Long-Term Support）リリースであり、以下の機能が**正式に利用可能**である。

### 正式化済み機能（Standard）

| 機能 | 由来プロジェクト | 正式化バージョン |
|:---|:---|:---:|
| Local-Variable Type Inference (`var`) | Amber | 10 |
| Switch Expressions | Amber | 14 |
| Records | Amber | 16 |
| Pattern Matching for instanceof | Amber | 16 |
| Sealed Classes | Amber | 17 |
| Metal Rendering Pipeline (macOS) | Lanai | 17 |
| Virtual Threads | Loom | 21 |
| Record Patterns | Amber | 21 |
| Pattern Matching for switch | Amber | 21 |
| Foreign Function & Memory API | Panama | 22 |
| Unnamed Variables & Patterns (`_`) | Amber | 22 |
| Generational ZGC (default) | ZGC | 23 |
| Module Import Declarations | Amber | 25 |
| Simple Source Files and Instance Main Methods | Amber | 25 |
| Flexible Constructor Bodies | Amber | 25 |
| Synchronize Virtual Threads without Pinning | Loom | 25 |
| AOT Class Loading & Linking | Leyden | 24 |
| Structured Concurrency | Loom | 25 |
| Scoped Values | Loom | 25 |
| ZGC: Non-Generational Mode 削除 | ZGC | 24 |

### プレビュー段階（`--enable-preview` が必要）

| 機能 | 由来プロジェクト | 状態 |
|:---|:---|:---|
| Primitive Types in Patterns | Amber | Preview (2nd) |
| Value Classes and Objects | Valhalla | Preview |

### Incubator 段階（`--add-modules` が必要）

| 機能 | 由来プロジェクト | 状態 |
|:---|:---|:---|
| Vector API | Panama | Incubator (9th) |

### 実験的・未リリース

| 機能 | 由来プロジェクト | 状態 |
|:---|:---|:---|
| Universal Generics (`List<int>`) | Valhalla | 未 JEP 化（長期目標） |
| Graal JIT 統合 | Galahad | 実験的 |
| 64-bit Object Headers | Lilliput | 実験的 |
| Code Reflection | Babylon | プロトタイプ |
| Wayland サポート | Wakefield | 開発中 |

> **注意:** 上記は Java 25 GA (2025年9月) 時点の情報に基づく。

---

## 参考リンク

- [OpenJDK Projects 一覧](https://openjdk.org/projects/)
- [JEP Index](https://openjdk.org/jeps/0)
- [Project Amber](https://openjdk.org/projects/amber/)
- [Project Valhalla](https://openjdk.org/projects/valhalla/)
- [Project Panama](https://openjdk.org/projects/panama/)
- [Project Babylon](https://openjdk.org/projects/babylon/)
- [Project Loom](https://openjdk.org/projects/loom/)
- [Project Leyden](https://openjdk.org/projects/leyden/)
- [Project Galahad](https://openjdk.org/projects/galahad/)
- [Project Lilliput](https://openjdk.org/projects/lilliput/)
- [Project ZGC](https://openjdk.org/projects/zgc/)
- [Project Shenandoah](https://openjdk.org/projects/shenandoah/)
- [Project Wakefield](https://openjdk.org/projects/wakefield/)
- [Project Lanai](https://openjdk.org/projects/lanai/)
- [Inside Java (Oracle)](https://inside.java/) -- OpenJDK の最新情報ブログ
