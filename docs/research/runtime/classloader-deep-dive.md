# クラスローダー詳解 -- .class ファイルから実行可能なクラスができるまで

- [概要](#概要)
- [1. クラスローダーとは何か](#1-クラスローダーとは何か)
  - [1.1 「.class を読み込むだけ」ではない -- クラスローダーの本質的な役割](#11-class-を読み込むだけではない----クラスローダーの本質的な役割)
  - [1.2 クラスの同一性 -- 「クラス名 + クラスローダー」で一意に決まる](#12-クラスの同一性----クラス名--クラスローダーで一意に決まる)
- [2. 3階層のクラスローダー -- 責任範囲と実装の違い](#2-3階層のクラスローダー----責任範囲と実装の違い)
  - [2.1 Bootstrap ClassLoader -- C++ で実装された特別な存在](#21-bootstrap-classloader----c-で実装された特別な存在)
  - [2.2 Platform ClassLoader -- java.base 以外のプラットフォームモジュール](#22-platform-classloader----javabase-以外のプラットフォームモジュール)
  - [2.3 Application ClassLoader -- 開発者が最も関わるクラスローダー](#23-application-classloader----開発者が最も関わるクラスローダー)
  - [2.4 3階層の全体像と責任範囲](#24-3階層の全体像と責任範囲)
- [3. 双親委譲モデル (Parent Delegation Model) -- 安全で一貫したクラスロード](#3-双親委譲モデル-parent-delegation-model----安全で一貫したクラスロード)
  - [3.1 委譲の流れ -- ステップバイステップ](#31-委譲の流れ----ステップバイステップ)
  - [3.2 なぜ「親優先」なのか -- 3つの設計目的](#32-なぜ親優先なのか----3つの設計目的)
  - [3.3 双親委譲を「破る」ケース -- SPI と Context ClassLoader](#33-双親委譲を破るケース----spi-と-context-classloader)
- [4. クラスのライフサイクル -- ロード・リンク・初期化の全過程](#4-クラスのライフサイクル----ロードリンク初期化の全過程)
  - [4.1 ロード (Loading) -- バイトコードの読み込みと Class オブジェクトの生成](#41-ロード-loading----バイトコードの読み込みと-class-オブジェクトの生成)
  - [4.2 リンク (Linking) -- 3段階の検証・準備・解決](#42-リンク-linking----3段階の検証準備解決)
  - [4.3 初期化 (Initialization) -- clinit の実行](#43-初期化-initialization----clinit-の実行)
- [5. HotSpot 内部のクラスローダー実装 -- C++ 層を追跡する](#5-hotspot-内部のクラスローダー実装----c-層を追跡する)
  - [5.1 oop-klass モデル -- JVM 内部のオブジェクト・クラス表現](#51-oop-klass-モデル----jvm-内部のオブジェクトクラス表現)
  - [5.2 SystemDictionary -- ロード済みクラスの管理台帳](#52-systemdictionary----ロード済みクラスの管理台帳)
  - [5.3 ClassFileParser -- .class ファイルの解析エンジン](#53-classfileparser----class-ファイルの解析エンジン)
  - [5.4 Metaspace へのメタデータ配置](#54-metaspace-へのメタデータ配置)
- [6. モジュールシステム (JPMS) とクラスローダー -- Java 9 以降の世界](#6-モジュールシステム-jpms-とクラスローダー----java-9-以降の世界)
  - [6.1 モジュールシステムの基本](#61-モジュールシステムの基本)
  - [6.2 モジュールとクラスローダーの関係](#62-モジュールとクラスローダーの関係)
  - [6.3 暗黙的モジュール (Unnamed Module) と互換性](#63-暗黙的モジュール-unnamed-module-と互換性)
- [7. Spring Boot とクラスローダー -- 実務での活用](#7-spring-boot-とクラスローダー----実務での活用)
  - [7.1 LaunchedURLClassLoader -- Fat JAR の解決策](#71-launchedurlclassloader----fat-jar-の解決策)
  - [7.2 起動時間とクラスローディング](#72-起動時間とクラスローディング)
  - [7.3 DevTools とクラスローダーの再作成](#73-devtools-とクラスローダーの再作成)
  - [7.4 クラスローダーリーク -- メモリリークの原因](#74-クラスローダーリーク----メモリリークの原因)
- [8. クラスのアンロード -- GC との連携](#8-クラスのアンロード----gc-との連携)
  - [8.1 クラスがアンロードされる条件](#81-クラスがアンロードされる条件)
  - [8.2 Metaspace とクラスアンロードの関係](#82-metaspace-とクラスアンロードの関係)
  - [8.3 -verbose:class でロード/アンロードを観察する](#83--verboseclass-でロードアンロードを観察する)
- [9. トラブルシューティング -- よくあるクラスローダー関連のエラー](#9-トラブルシューティング----よくあるクラスローダー関連のエラー)
  - [9.1 ClassNotFoundException vs NoClassDefFoundError](#91-classnotfoundexception-vs-noclassdeffounderror)
  - [9.2 ClassCastException（同名クラスの型不一致）](#92-classcastexception同名クラスの型不一致)
  - [9.3 LinkageError と UnsupportedClassVersionError](#93-linkageerror-と-unsupportedclassversionerror)
  - [9.4 デバッグ手法まとめ](#94-デバッグ手法まとめ)
- [10. まとめ -- クラスローダーの全体像](#10-まとめ----クラスローダーの全体像)
- [参考リンク・ソースパス](#参考リンクソースパス)

---

## 概要

本ドキュメントは、JVM のクラスローダーサブシステムを詳細に解説するガイドである。[jvm-overview.md](../jvm-overview.md) のセクション3「クラスローダーサブシステム」で概要を把握した上で、本ドキュメントで内部動作の詳細を学ぶ構成になっている。

**対象層:** Java 層（`java.lang.ClassLoader`）/ HotSpot C++ 層（`src/hotspot/share/classfile/`）

**対象ランタイム:** Azul Zulu 25 (OpenJDK 25 ベース / HotSpot VM)

**前提知識:**
- [jvm-overview.md](../jvm-overview.md) -- JVM 全体像の把握（特にセクション3）
- [memory-basics.md](../memory/memory-basics.md) -- Metaspace の基礎（セクション5.3）

**位置づけ:**

```
jvm-overview.md（全体像の地図）
    │
    ├── 本ドキュメント（クラスローダーの詳細）  ← ここ
    │
    ├── memory-basics.md（メモリの詳細）
    │
    └── gc-deep-dive.md（GC の詳細）
```

---

## 1. クラスローダーとは何か

**対象層:** Java 層（`java.lang.ClassLoader`）

### 1.1 「.class を読み込むだけ」ではない -- クラスローダーの本質的な役割

「クラスローダー」という名前から、単にファイルを読み込むだけの仕組みを想像するかもしれない。しかし実際には、クラスローダーは以下の **4つの責務** を持つ大きなサブシステムである。

```
 ┌─────────────────────────────────────────────────────────────────────┐
 │                   クラスローダーサブシステムの責務                    │
 │                                                                     │
 │  1. 発見と読み込み   .class ファイルの場所を特定し、バイト列を取得   │
 │  2. 検証と安全性    バイトコードが JVM 仕様に違反していないか検証     │
 │  3. メモリ配置      クラスのメタ情報を Metaspace に配置              │
 │  4. 名前空間の分離  同名クラスの衝突を防ぎ、モジュール間を隔離      │
 └─────────────────────────────────────────────────────────────────────┘
```

特に重要なのは **4番目の「名前空間の分離」** である。これはクラスローダーが単なるファイル読み込み係ではなく、**型システムの安全性を支える基盤** であることを意味する。

クラスローダーの役割を比喩で説明すると:

| 比喩 | 説明 |
|------|------|
| **図書館の司書** | 本（.class）を探し出して棚（Metaspace）に配置する |
| **税関の検査官** | 持ち込まれた荷物（バイトコード）が安全かを検査する |
| **パスポート管理局** | 同姓同名でも国籍（クラスローダー）が違えば別人として扱う |

### 1.2 クラスの同一性 -- 「クラス名 + クラスローダー」で一意に決まる

**これはクラスローダーを理解する上で最も重要な概念である。**

JVM において、クラスの同一性は **完全修飾クラス名（FQCN）だけでは決まらない。** クラスを一意に識別するのは **「完全修飾クラス名 + それをロードしたクラスローダーのインスタンス」** の組み合わせである。

```
 ┌─────────────────────────────────────────────────────────────────┐
 │  JVM 内部でのクラスの同一性                                      │
 │                                                                 │
 │  同一のクラス:                                                   │
 │    com.example.User  ＋  ClassLoader A  ───→  型 X              │
 │    com.example.User  ＋  ClassLoader A  ───→  型 X  （同じ型）  │
 │                                                                 │
 │  異なるクラス（たとえ .class ファイルが同一でも！）:              │
 │    com.example.User  ＋  ClassLoader A  ───→  型 X              │
 │    com.example.User  ＋  ClassLoader B  ───→  型 Y  （別の型）  │
 │                                                                 │
 │  型 X のインスタンスを型 Y にキャストすると → ClassCastException │
 └─────────────────────────────────────────────────────────────────┘
```

**なぜこのような設計なのか:**

この設計があるからこそ、以下のことが可能になる:

1. **アプリケーションサーバーでの分離:** Tomcat や Jetty は、デプロイされた各 Web アプリケーションに専用のクラスローダーを割り当てる。これにより、アプリ A とアプリ B が同名のクラスを持っていても衝突しない。
2. **ホットリロード:** 古いクラスローダーを破棄して新しいクラスローダーでクラスを再ロードすることで、JVM を再起動せずにクラスを更新できる（Spring Boot DevTools が利用する仕組み）。
3. **プラグインシステム:** Eclipse や IntelliJ IDEA は、各プラグインを独自のクラスローダーで隔離し、プラグイン間の干渉を防いでいる。

**HotSpot C++ 層での表現:**

HotSpot 内部では、この「クラス名 + クラスローダー」のペアが **SystemDictionary** というハッシュテーブルで管理されている（詳細は [5.2節](#52-systemdictionary----ロード済みクラスの管理台帳) で解説）。

- 関連ソースパス: `src/hotspot/share/classfile/systemDictionary.cpp`

---

## 2. 3階層のクラスローダー -- 責任範囲と実装の違い

**対象層:** Java 層 / HotSpot C++ 層

Java のクラスローダーは3層構造になっている。[jvm-overview.md のセクション 3.1](../jvm-overview.md#31-クラスローダーの3階層) で概要を示したが、ここでは各層の実装詳細に踏み込む。

### 2.1 Bootstrap ClassLoader -- C++ で実装された特別な存在

**対象層:** HotSpot C++ 層

Bootstrap ClassLoader は、JVM の中で最も特殊なクラスローダーである。他の2つのクラスローダーが Java で実装されているのに対し、**Bootstrap ClassLoader だけは HotSpot の C++ コードで実装されている。**

```
 ┌───────────────────────────────────────────────────────────────┐
 │                   Bootstrap ClassLoader                       │
 │                                                               │
 │  実装言語:    C++ (HotSpot VM の一部)                          │
 │  Java 表現:  null (getClassLoader() が null を返す)            │
 │  担当範囲:   java.base モジュール                              │
 │              (String, Object, Class, System, Thread, ...)     │
 │  ソースパス: src/hotspot/share/classfile/classLoader.cpp       │
 │                                                               │
 │  【なぜ C++ で実装されているのか】                              │
 │  JVM が起動する最初期、Java コードはまだ一切動かない。          │
 │  java.lang.Object や java.lang.Class すらロードされていない。  │
 │  → Java で書かれたクラスローダーを動かすには、まず              │
 │    Java の基盤クラスが必要（鶏と卵の問題）                     │
 │  → だから最初のクラスローダーだけは C++ で実装する              │
 └───────────────────────────────────────────────────────────────┘
```

**getClassLoader() が null を返す理由:**

Bootstrap ClassLoader は Java のオブジェクトではない（C++ の内部実装）ため、Java 層からは直接参照できない。そのため、Bootstrap ClassLoader でロードされたクラスに対して `getClass().getClassLoader()` を呼ぶと `null` が返る。

```java
// java.lang.String は Bootstrap CL でロードされている
String.class.getClassLoader()    // → null

// 自分が書いたクラスは Application CL でロードされている
MyApp.class.getClassLoader()     // → jdk.internal.loader.ClassLoaders$AppClassLoader
```

**Bootstrap ClassLoader がロードする主なクラス:**

| パッケージ | 代表的なクラス |
|-----------|---------------|
| `java.lang` | `Object`, `String`, `Class`, `System`, `Thread`, `Math` |
| `java.util` | `ArrayList`, `HashMap`, `Optional`, `Stream` |
| `java.io` | `InputStream`, `OutputStream`, `File`, `IOException` |
| `java.nio` | `ByteBuffer`, `Path`, `Files`, `Channel` |
| `java.net` | `URL`, `URI`, `Socket`, `HttpClient` |
| `java.time` | `LocalDate`, `Instant`, `Duration`, `ZonedDateTime` |
| `java.util.concurrent` | `ExecutorService`, `CompletableFuture`, `AtomicInteger` |

これらはすべて `java.base` モジュールに属する。

**HotSpot C++ 層の起動シーケンス:**

```
 JVM 起動
    │
    ▼
 ClassLoader::initialize()          ← classLoader.cpp
    │
    ├─ java.base モジュールのパスを解決
    │
    ├─ 以下のコアクラスを最優先でロード:
    │   1. java.lang.Object            ← すべてのクラスの親
    │   2. java.lang.Class             ← クラス自体を表現するクラス
    │   3. java.lang.String            ← 文字列（クラス名の解決に必須）
    │   4. java.lang.System            ← System.out 等
    │   5. java.lang.Thread            ← メインスレッド
    │   6. java.lang.ClassLoader       ← Java 層のクラスローダー基底クラス
    │   :
    │
    └─ 以降、必要に応じてオンデマンドでロード
```

- 関連ソースパス: `src/hotspot/share/classfile/classLoader.cpp`
- 関連ソースパス: `src/hotspot/share/classfile/classLoader.hpp`

### 2.2 Platform ClassLoader -- java.base 以外のプラットフォームモジュール

**対象層:** Java 層

Platform ClassLoader は、`java.base` 以外の標準プラットフォームモジュールをロードする。

```
 ┌───────────────────────────────────────────────────────────────┐
 │                   Platform ClassLoader                        │
 │                                                               │
 │  実装言語:    Java                                             │
 │  実装クラス:  jdk.internal.loader.ClassLoaders$PlatformCL      │
 │  親:          Bootstrap ClassLoader                           │
 │  担当範囲:    java.base 以外のプラットフォームモジュール       │
 └───────────────────────────────────────────────────────────────┘
```

**担当するモジュールの例:**

| モジュール | 内容 |
|-----------|------|
| `java.sql` | JDBC API（`Connection`, `Statement`, `ResultSet`） |
| `java.xml` | XML パーサー（DOM, SAX, StAX） |
| `java.naming` | JNDI（Java Naming and Directory Interface） |
| `java.logging` | `java.util.logging`（JUL） |
| `java.compiler` | `javax.tools`（コンパイラ API） |
| `java.management` | JMX（`MXBean` 等） |
| `java.instrument` | Java Agent（`Instrumentation` API） |
| `java.scripting` | スクリプティング API |
| `java.desktop` | AWT, Swing |

**Java 8 以前との違い:**

Java 8 以前は **Extension ClassLoader** という名前で、`$JAVA_HOME/lib/ext/` ディレクトリに置かれた JAR を読み込む役割だった。Java 9 でモジュールシステム（JPMS）が導入されたことで、ディレクトリベースの拡張メカニズムは廃止され、モジュールベースの Platform ClassLoader に改称された。

```
 Java 8 以前:                         Java 9 以降:
 ┌─────────────────────────┐          ┌─────────────────────────┐
 │ Extension ClassLoader   │          │ Platform ClassLoader    │
 │ $JAVA_HOME/lib/ext/*.jar│   ──→    │ プラットフォームモジュール│
 │ (ディレクトリベース)     │          │ (モジュールベース)       │
 └─────────────────────────┘          └─────────────────────────┘
```

### 2.3 Application ClassLoader -- 開発者が最も関わるクラスローダー

**対象層:** Java 層

Application ClassLoader（System ClassLoader とも呼ばれる）は、**開発者が書いたクラスや、依存ライブラリの JAR をロードする** クラスローダーである。日常の開発で最も頻繁に関わる。

```
 ┌───────────────────────────────────────────────────────────────┐
 │                   Application ClassLoader                     │
 │                                                               │
 │  実装言語:    Java                                             │
 │  実装クラス:  jdk.internal.loader.ClassLoaders$AppClassLoader  │
 │  親:          Platform ClassLoader                            │
 │  担当範囲:    classpath / modulepath 上のクラス                │
 │  取得方法:    ClassLoader.getSystemClassLoader()               │
 └───────────────────────────────────────────────────────────────┘
```

**読み込み対象の指定方法:**

| オプション | 用途 | 例 |
|-----------|------|-----|
| `-cp` / `-classpath` | クラスパスを指定 | `java -cp lib/*.jar:. Main` |
| `--module-path` | モジュールパスを指定 | `java --module-path mods -m app/Main` |
| `-jar` | 実行可能 JAR を指定 | `java -jar app.jar` |
| ソースコードランチャー | .java を直接実行 | `java Main.java` |

**ソースコードランチャー（単一ファイルソースプログラム）の場合:**

本プロジェクトで使用している `java src/main/java/ファイル名.java` のような実行方法では、JVM が内部的に `javac` でコンパイルし、結果のバイトコードを Application ClassLoader 相当のクラスローダーでロードする。

### 2.4 3階層の全体像と責任範囲

3つのクラスローダーの関係と責任範囲を整理する。

```
 ┌──────────────────────────────────────────────────────────────────┐
 │                     クラスローダーの3階層                         │
 │                                                                  │
 │  ┌────────────────────────────────────────────────────────────┐  │
 │  │  Bootstrap ClassLoader                    [C++ / null]     │  │
 │  │                                                            │  │
 │  │  java.base モジュール:                                     │  │
 │  │    java.lang.*  java.util.*  java.io.*  java.nio.*         │  │
 │  │    java.net.*   java.time.*  java.math.*  ...              │  │
 │  │                                                            │  │
 │  │  ┌────────────────────────────────────────────────────┐    │  │
 │  │  │  Platform ClassLoader              [Java]          │    │  │
 │  │  │                                                    │    │  │
 │  │  │  java.sql  java.xml  java.logging  java.naming     │    │  │
 │  │  │  java.management  java.compiler  ...               │    │  │
 │  │  │                                                    │    │  │
 │  │  │  ┌──────────────────────────────────────────┐      │    │  │
 │  │  │  │  Application ClassLoader    [Java]       │      │    │  │
 │  │  │  │                                          │      │    │  │
 │  │  │  │  classpath 上のクラス:                    │      │    │  │
 │  │  │  │    com.example.*                         │      │    │  │
 │  │  │  │    org.springframework.*                 │      │    │  │
 │  │  │  │    自分が書いたクラス                     │      │    │  │
 │  │  │  │                                          │      │    │  │
 │  │  │  │  ┌────────────────────────────────┐      │      │    │  │
 │  │  │  │  │  カスタム ClassLoader          │      │      │    │  │
 │  │  │  │  │  (Tomcat, Spring Boot 等)      │      │      │    │  │
 │  │  │  │  └────────────────────────────────┘      │      │    │  │
 │  │  │  └──────────────────────────────────────────┘      │    │  │
 │  │  └────────────────────────────────────────────────────┘    │  │
 │  └────────────────────────────────────────────────────────────┘  │
 │                                                                  │
 │  内側のクラスローダーは外側の親に委譲する（双親委譲モデル）        │
 └──────────────────────────────────────────────────────────────────┘
```

**可視性のルール（重要）:**

```
 Bootstrap CL ──→ 自分がロードしたクラスだけが見える
      ▲
      │ 委譲
 Platform CL ───→ Bootstrap のクラス ＋ 自分のクラス が見える
      ▲
      │ 委譲
 Application CL → Bootstrap + Platform + 自分のクラス が見える

 ★ 親は子がロードしたクラスを見ることができない（一方向の可視性）
```

この一方向の可視性により、コアライブラリ（`java.lang.String` 等）がアプリケーション固有のクラスに依存することは構造的に不可能になる。これが JVM の型安全性の基盤の一つである。

---

## 3. 双親委譲モデル (Parent Delegation Model) -- 安全で一貫したクラスロード

**対象層:** Java 層（`java.lang.ClassLoader#loadClass`）

### 3.1 委譲の流れ -- ステップバイステップ

[jvm-overview.md のセクション 3.2](../jvm-overview.md#32-双親委譲モデル-parent-delegation-model) で概要を示した双親委譲モデルの内部動作を、より詳細に追跡する。

**ClassLoader.loadClass() の処理フロー:**

```
 loadClass("com.example.UserService") が呼ばれた

 ┌─ Application ClassLoader ────────────────────────────────────┐
 │                                                               │
 │  ① キャッシュ確認: 既にロード済みか？                          │
 │     findLoadedClass("com.example.UserService")               │
 │     → null (未ロード)                                        │
 │                                                               │
 │  ② 親に委譲:                                                  │
 │     parent.loadClass("com.example.UserService")              │
 │     │                                                         │
 │     ▼                                                         │
 │  ┌─ Platform ClassLoader ──────────────────────────────┐      │
 │  │                                                      │      │
 │  │  ① キャッシュ確認 → null (未ロード)                   │      │
 │  │                                                      │      │
 │  │  ② 親に委譲:                                         │      │
 │  │     parent.loadClass(...)                            │      │
 │  │     │                                                │      │
 │  │     ▼                                                │      │
 │  │  ┌─ Bootstrap ClassLoader (C++) ──────────┐         │      │
 │  │  │                                         │         │      │
 │  │  │  java.base に該当クラスなし              │         │      │
 │  │  │  → ClassNotFoundException              │         │      │
 │  │  │                                         │         │      │
 │  │  └─────────────────────────────────────────┘         │      │
 │  │                                                      │      │
 │  │  ③ 自分で探す:                                       │      │
 │  │     findClass("com.example.UserService")             │      │
 │  │     → プラットフォームモジュールに該当なし             │      │
 │  │     → ClassNotFoundException                         │      │
 │  │                                                      │      │
 │  └──────────────────────────────────────────────────────┘      │
 │                                                               │
 │  ③ 自分で探す:                                                │
 │     findClass("com.example.UserService")                     │
 │     → classpath 上に発見！                                    │
 │     → .class ファイルを読み込み                               │
 │     → defineClass() でクラスを定義                            │
 │                                                               │
 │  ④ ロード完了: Class<UserService> を返す                      │
 │                                                               │
 └───────────────────────────────────────────────────────────────┘
```

**ClassLoader.loadClass() の擬似コード:**

実際の `java.lang.ClassLoader.loadClass()` のロジックを簡略化すると、以下のようになる。

```java
// java.lang.ClassLoader の loadClass() を簡略化したもの
Class<?> loadClass(String name) throws ClassNotFoundException {
    // ① 既にロード済みならキャッシュから返す
    Class<?> c = findLoadedClass(name);
    if (c != null) return c;

    // ② 親クラスローダーに委譲
    try {
        c = parent.loadClass(name);
        return c;
    } catch (ClassNotFoundException e) {
        // 親が見つけられなかった → 自分で探す
    }

    // ③ 自分自身でクラスを探す
    return findClass(name);
}
```

**重要な設計ポイント:** `loadClass()` は `findLoadedClass()` → 親への委譲 → `findClass()` という順序を**必ず守る**。カスタムクラスローダーを作る場合も、`loadClass()` をオーバーライドするのではなく、`findClass()` をオーバーライドするのが推奨される。`loadClass()` をオーバーライドすると双親委譲モデルが壊れるリスクがある。

### 3.2 なぜ「親優先」なのか -- 3つの設計目的

双親委譲モデルが「親優先」で設計されている理由は3つある。

#### 1. セキュリティ -- コアクラスの偽装防止

もし「子優先」だったら、攻撃者が `java.lang.String` という名前の悪意あるクラスを classpath に置くだけで、JVM 全体を乗っ取ることができてしまう。

```
 ★ もし「子優先」だったら（危険！）:

 攻撃者の java.lang.String.class が classpath にある
     │
     ▼
 Application CL: 「自分の classpath にある！読み込む」  ← 偽物の String！
     │
     ▼
 JVM 全体が偽の String を使ってしまう → セキュリティ崩壊


 ★ 実際の「親優先」（安全）:

 攻撃者の java.lang.String.class が classpath にある
     │
     ▼
 Application CL: 「親に聞いてみよう」
     │
     ▼
 Bootstrap CL: 「java.base に本物の String がある！」 ← 本物をロード
     │
     ▼
 攻撃者の偽 String は無視される → セキュリティ維持
```

#### 2. 一意性 -- 同じクラスの重複ロード防止

親優先にすることで、同じクラスは常に**最も上位のクラスローダーでロードされる**。これにより、JVM 全体で同一のクラスインスタンスが共有される。

#### 3. 可視性 -- 階層的なアクセス制御

親がロードしたクラスは子から見えるが、子がロードしたクラスは親から見えない。この一方向の可視性により、コアライブラリが特定のアプリケーションに依存する事故を構造的に防ぐ。

### 3.3 双親委譲を「破る」ケース -- SPI と Context ClassLoader

双親委譲モデルは優れた設計だが、**すべてのユースケースに適合するわけではない。** 特に SPI (Service Provider Interface) パターンでは問題が発生する。

**SPI の問題:**

```
 ┌─ Bootstrap CL ─────────────────────────────────────────────┐
 │                                                             │
 │  java.sql.DriverManager (SPI のインタフェース側)             │
 │  「JDBC ドライバの実装クラスをロードしたい...」               │
 │  「でも実装は classpath 上にある...」                         │
 │  「自分の子（Application CL）のクラスは見えない！」           │
 │                                                             │
 │  ★ 双親委譲モデルでは親→子は不可視 → 行き詰まり            │
 └─────────────────────────────────────────────────────────────┘
```

`java.sql.DriverManager` は Bootstrap ClassLoader でロードされる。しかし、JDBC ドライバの実装（例: MySQL の `com.mysql.cj.jdbc.Driver`）は classpath 上にあり、Application ClassLoader の管轄である。親は子のクラスを見ることができないため、双親委譲モデルだけでは SPI パターンが成立しない。

**解決策: Thread Context ClassLoader**

この問題を解決するために、Java は **Thread Context ClassLoader** という仕組みを提供している。

```
 ┌─ Bootstrap CL ─────────────────────────────────────────────┐
 │                                                             │
 │  DriverManager.getConnection() の内部:                      │
 │                                                             │
 │  // 現在のスレッドに紐づいたクラスローダーを取得              │
 │  ClassLoader cl = Thread.currentThread()                    │
 │                         .getContextClassLoader();            │
 │  // → Application CL が返る                                 │
 │                                                             │
 │  // そのクラスローダーを使ってドライバをロード                │
 │  Class<?> driver = Class.forName(driverName, true, cl);     │
 │  // → classpath 上の JDBC ドライバがロードできる！           │
 │                                                             │
 └─────────────────────────────────────────────────────────────┘
```

**Thread Context ClassLoader のデフォルト値:**

メインスレッドの Context ClassLoader は、JVM 起動時に **Application ClassLoader** に設定される。子スレッドは親スレッドの Context ClassLoader を継承する。

```java
// デフォルトの Context ClassLoader を確認
Thread.currentThread().getContextClassLoader()
// → jdk.internal.loader.ClassLoaders$AppClassLoader
```

**SPI 以外の双親委譲を「破る」例:**

| ケース | 仕組み | 例 |
|--------|--------|-----|
| **JNDI** | Thread Context ClassLoader | JNDI プロバイダーの動的ロード |
| **JDBC** | Thread Context ClassLoader | JDBC ドライバのロード |
| **OSGi** | 独自の委譲グラフ | Eclipse プラグインシステム |
| **Tomcat** | 「子優先」の WebAppClassLoader | 各 Web アプリの分離 |

**Tomcat の「子優先」クラスローダー:**

Tomcat の `WebAppClassLoader` は双親委譲モデルを意図的に逆転させ、**子優先（Child-First）** でクラスをロードする。これにより、各 Web アプリケーションが異なるバージョンのライブラリ（例: アプリ A は Jackson 2.14、アプリ B は Jackson 2.17）を独立して使用できる。

```
 通常の双親委譲（親優先）:          Tomcat の WebAppClassLoader（子優先）:

 Application CL                     WebAppClassLoader
      │ 委譲↑                           │ まず自分で探す
      ▼                                 ▼
 Platform CL                        WEB-INF/classes/ と WEB-INF/lib/
      │ 委譲↑                           │ なければ親に委譲↑
      ▼                                 ▼
 Bootstrap CL                       Application CL → Platform CL → Bootstrap CL
```

ただし、`java.lang.*` 等のコアクラスは例外的に常に親に委譲される。セキュリティ上、コアクラスの偽装は許可されない。

---

## 4. クラスのライフサイクル -- ロード・リンク・初期化の全過程

**対象層:** Java 層 / HotSpot C++ 層

[jvm-overview.md のセクション 3.3](../jvm-overview.md#33-クラスのライフサイクル----ロードリンク初期化) で示した3フェーズを詳細に解説する。

```
 .class ファイル (バイト列)
       │
       ▼
 ┌───────────────────────────────────────────────────────────────────┐
 │                       クラスのライフサイクル                       │
 │                                                                   │
 │  ┌───────────┐   ┌─────────────────────────────┐   ┌──────────┐  │
 │  │  1.ロード  │──▶│       2. リンク              │──▶│ 3.初期化 │  │
 │  │ (Loading)  │   │                             │   │(Init)    │  │
 │  │           │   │ ┌──────┐ ┌─────┐ ┌───────┐  │   │          │  │
 │  │ .class を │   │ │ 検証 │→│準備 │→│ 解決  │  │   │ <clinit> │  │
 │  │ 読み込み  │   │ │Verify│ │Prep.│ │Resolve│  │   │ を実行   │  │
 │  │ + Class   │   │ └──────┘ └─────┘ └───────┘  │   │          │  │
 │  │   生成    │   │                             │   │          │  │
 │  └───────────┘   └─────────────────────────────┘   └──────────┘  │
 │                                                                   │
 │  ──── 使用中 (Using) ────                                         │
 │                                                                   │
 │  ┌──────────┐                                                     │
 │  │ アンロード │  クラスローダーが GC に回収されると発生              │
 │  │(Unloading)│  → 第8章で詳細解説                                 │
 │  └──────────┘                                                     │
 └───────────────────────────────────────────────────────────────────┘
```

### 4.1 ロード (Loading) -- バイトコードの読み込みと Class オブジェクトの生成

**対象層:** Java 層（`ClassLoader.defineClass`）/ HotSpot C++ 層（`ClassFileParser`）

ロードフェーズでは、以下の3つの処理が行われる。

1. **バイト列の取得:** クラスの完全修飾名をもとに、.class ファイルのバイト列を取得する
2. **Class オブジェクトの生成:** バイト列を解析し、`java.lang.Class` オブジェクトを生成する
3. **Metaspace への配置:** クラスのメタ情報（フィールド定義、メソッド定義、定数プール等）を Metaspace に配置する

```
 ClassLoader.loadClass("com.example.User")
       │
       ▼
 findClass("com.example.User")
       │
       ├─ クラスパス上のファイル／JAR を探索
       │  com/example/User.class を発見
       │
       ▼
 バイト列を読み込み: byte[] classData = readFile(...)
       │
       ▼
 defineClass("com.example.User", classData, ...)
       │
       │  ┌─ HotSpot C++ 層 ────────────────────────────────┐
       │  │                                                   │
       │  │  ClassFileParser::parse_stream()                  │
       │  │    │                                              │
       │  │    ├─ マジックナンバー確認 (0xCAFEBABE)           │
       │  │    ├─ バージョン番号確認                           │
       │  │    ├─ 定数プール (Constant Pool) を解析            │
       │  │    ├─ アクセスフラグ, クラス名, 親クラスを解析      │
       │  │    ├─ インタフェース一覧を解析                     │
       │  │    ├─ フィールド情報を解析                         │
       │  │    ├─ メソッド情報 + バイトコードを解析             │
       │  │    └─ アトリビュート（アノテーション等）を解析      │
       │  │                                                   │
       │  │  InstanceKlass を生成 → Metaspace に配置          │
       │  │  java.lang.Class オブジェクト(oop) をヒープに生成   │
       │  │                                                   │
       │  └───────────────────────────────────────────────────┘
       │
       ▼
 Class<User> オブジェクトを返す
```

**InstanceKlass と Class オブジェクトの関係:**

ここで重要なのは、JVM 内部には **2つの「クラスの表現」** が存在することである。

```
 ┌─ Metaspace (ネイティブメモリ) ─────────┐     ┌─ ヒープ ──────────────────┐
 │                                         │     │                          │
 │  InstanceKlass                          │     │  java.lang.Class (oop)   │
 │  ┌─────────────────────────────────┐    │     │  ┌──────────────────┐    │
 │  │ クラス名: com.example.User      │    │     │  │  Java の世界から  │    │
 │  │ 親クラス: java.lang.Object      │◀───┼─────┼──│  見えるクラス情報 │    │
 │  │ フィールド定義: [name, age]      │    │     │  │                  │    │
 │  │ メソッド定義: [getName, ...]     │    │     │  │  User.class で   │    │
 │  │ vtable (仮想メソッドテーブル)    │    │     │  │  取得できる      │    │
 │  │ 定数プール                      │    │     │  │                  │    │
 │  │ バイトコード                    │    │     │  └──────────────────┘    │
 │  └─────────────────────────────────┘    │     │                          │
 │                                         │     │                          │
 │  C++ の世界（JVM 内部用）                │     │  Java の世界（開発者用）  │
 └─────────────────────────────────────────┘     └──────────────────────────┘
```

- `InstanceKlass`（Metaspace）: JVM が内部的に使うクラスの完全な表現。C++ のオブジェクト。
- `java.lang.Class`（ヒープ）: Java コードからアクセスできるクラス情報のミラー。`User.class` や `getClass()` で取得できる。

両者は相互に参照を持ち、`java.lang.Class` オブジェクトは `InstanceKlass` へのポインタを内部に保持している。

- 関連ソースパス: `src/hotspot/share/classfile/classFileParser.cpp`
- 関連ソースパス: `src/hotspot/share/oops/instanceKlass.hpp`

### 4.2 リンク (Linking) -- 3段階の検証・準備・解決

リンクフェーズは3つのサブフェーズで構成される。

#### 4.2.1 検証 (Verification) -- バイトコードの安全性チェック

**対象層:** HotSpot C++ 層（`src/hotspot/share/classfile/verifier.cpp`）

検証フェーズでは、.class ファイルのバイトコードが JVM 仕様に違反していないかを4段階でチェックする。

```
 ┌───────────────────────────────────────────────────────────────┐
 │              検証の4段階 (JVM 仕様 §4.10)                      │
 │                                                               │
 │  ┌─────────────────────────────────────────────────────────┐  │
 │  │  第1段階: ファイル形式の検証                              │  │
 │  │  ・マジックナンバーが 0xCAFEBABE であること               │  │
 │  │  ・クラスファイルのバージョンが JVM サポート範囲内         │  │
 │  │  ・定数プールのエントリが正しい形式であること             │  │
 │  └─────────────────────┬───────────────────────────────────┘  │
 │                         ▼                                     │
 │  ┌─────────────────────────────────────────────────────────┐  │
 │  │  第2段階: メタデータの検証                                │  │
 │  │  ・クラスに親クラスがあること (Object を除く)              │  │
 │  │  ・final クラスを継承していないこと                       │  │
 │  │  ・final メソッドをオーバーライドしていないこと           │  │
 │  │  ・インタフェースの制約を満たしていること                 │  │
 │  └─────────────────────┬───────────────────────────────────┘  │
 │                         ▼                                     │
 │  ┌─────────────────────────────────────────────────────────┐  │
 │  │  第3段階: バイトコードの検証                              │  │
 │  │  ・各命令のオペランドスタックが溢れないこと               │  │
 │  │  ・ローカル変数のアクセスが範囲内であること               │  │
 │  │  ・型の整合性（int と参照型を混同していないか等）         │  │
 │  │  ・制御フローが不正な命令に到達しないこと                 │  │
 │  └─────────────────────┬───────────────────────────────────┘  │
 │                         ▼                                     │
 │  ┌─────────────────────────────────────────────────────────┐  │
 │  │  第4段階: シンボル参照の検証                              │  │
 │  │  ・参照先のクラスが存在すること                           │  │
 │  │  ・参照先のフィールド/メソッドが存在すること              │  │
 │  │  ・アクセス権限があること (private/protected 等)          │  │
 │  │  ※ この段階は解決 (Resolution) フェーズと重なる          │  │
 │  └─────────────────────────────────────────────────────────┘  │
 └───────────────────────────────────────────────────────────────┘
```

**マジックナンバー 0xCAFEBABE:**

すべての .class ファイルは、先頭4バイトが `0xCAFEBABE` でなければならない。これは Java の開発チームが **コーヒー好きだったこと** に由来する（Java 自体もコーヒーの銘柄から名付けられた）。HotSpot は最初にこの4バイトを確認し、異なる場合は即座に `ClassFormatError` を throw する。

**検証に失敗した場合:**

| エラー | 発生条件 |
|--------|----------|
| `ClassFormatError` | マジックナンバーが不正、定数プールの形式不正 |
| `VerifyError` | バイトコードの型不整合、スタック操作の不整合 |
| `UnsupportedClassVersionError` | クラスファイルバージョンが JVM より新しい |

- 関連ソースパス: `src/hotspot/share/classfile/verifier.cpp`

#### 4.2.2 準備 (Preparation) -- static フィールドのデフォルト値設定

**対象層:** HotSpot C++ 層

準備フェーズでは、クラスの static フィールドにデフォルト値を設定する。**ここで設定されるのはあくまで「デフォルト値」であり、プログラマーが書いた初期値ではない。**

```
 Java コード:
   static int count = 42;
   static String name = "Hello";
   static final int MAX = 100;   // コンパイル時定数

 ┌─ 準備フェーズ後の状態 ──────────────────────────────┐
 │                                                      │
 │  count = 0        ← int のデフォルト値（42 ではない！）│
 │  name  = null     ← 参照型のデフォルト値               │
 │  MAX   = 100      ← コンパイル時定数は例外的にここで設定│
 │                                                      │
 │  count に 42 が代入されるのは「初期化」フェーズ         │
 │  name に "Hello" が代入されるのも「初期化」フェーズ     │
 └──────────────────────────────────────────────────────┘
```

**コンパイル時定数（ConstantValue 属性）の特別扱い:**

`static final` で宣言されたプリミティブ型または `String` のフィールドで、かつコンパイル時に値が確定する場合は、**準備フェーズで最終値が設定される。** これは .class ファイルの `ConstantValue` 属性として埋め込まれているためである。

| 宣言 | 準備フェーズ後の値 | 理由 |
|------|-------------------|------|
| `static int x = 42` | `0` | final でない → 初期化フェーズで設定 |
| `static final int MAX = 100` | `100` | コンパイル時定数 → ConstantValue 属性 |
| `static final String S = "hi"` | `"hi"` | コンパイル時定数 → ConstantValue 属性 |
| `static final int Y = calc()` | `0` | メソッド呼び出し → コンパイル時に不確定 |

#### 4.2.3 解決 (Resolution) -- シンボル参照から直接参照への変換

**対象層:** HotSpot C++ 層（`src/hotspot/share/interpreter/linkResolver.cpp`）

解決フェーズでは、定数プール内の **シンボル参照**（クラス名やメソッド名の文字列）を、メモリ上の実体への **直接参照**（ポインタ）に変換する。

```
 ┌─ 解決前（シンボル参照）──────────────────────────────────┐
 │                                                          │
 │  定数プール:                                             │
 │    #5 = Class        "java/lang/StringBuilder"  ← 文字列 │
 │    #12 = Methodref   #5.#20                     ← 文字列 │
 │    #20 = NameAndType "append":"(I)Ljava/..."    ← 文字列 │
 │                                                          │
 │  バイトコード: invokevirtual #12                         │
 │  → 「#12 のメソッドを呼べ」（でも #12 はまだ文字列）     │
 │                                                          │
 └──────────────────────────────────────────────────────────┘
                          │
                          ▼ 解決
 ┌─ 解決後（直接参照）──────────────────────────────────────┐
 │                                                          │
 │  定数プール:                                             │
 │    #5 = → InstanceKlass* (0x7f3a..)   ← メモリアドレス   │
 │    #12 = → Method* (0x7f3b..)          ← メモリアドレス   │
 │                                                          │
 │  バイトコード: invokevirtual #12                         │
 │  → 直接メソッドのアドレスにジャンプできる → 高速！        │
 │                                                          │
 └──────────────────────────────────────────────────────────┘
```

**遅延解決 (Lazy Resolution):**

JVM 仕様では、解決は **遅延的 (lazy)** に行われることが許可されている。つまり、クラスのすべてのシンボル参照をロード時に一括解決するのではなく、**実際にその参照が使われる時点で初めて解決する** ことができる。HotSpot はこの遅延解決を採用しており、不要なクラスの連鎖的なロードを防いでいる。

- 関連ソースパス: `src/hotspot/share/interpreter/linkResolver.cpp`

### 4.3 初期化 (Initialization) -- \<clinit\> の実行

**対象層:** Java 層 / HotSpot C++ 層

初期化フェーズは、**クラスのライフサイクルにおいて唯一 Java コードが実行されるフェーズ** である。`javac` が生成した `<clinit>`（クラス初期化メソッド）が実行され、static フィールドにプログラマーが意図した初期値が設定される。

**\<clinit\> メソッドの生成:**

`javac` は、以下の要素を上から順にまとめて `<clinit>` メソッドを生成する。

```java
// Java ソースコード
static int count = 42;                    // ── (a)
static List<String> items = new ArrayList<>();  // ── (b)

static {                                  // ── (c)
    items.add("初期アイテム");
    IO.println("クラスが初期化されました");
}

static String label = "ラベル";           // ── (d)
```

```
 javac が生成する <clinit>:

   (a) count = 42;
   (b) items = new ArrayList<>();
   (c) items.add("初期アイテム");
       IO.println("クラスが初期化されました");
   (d) label = "ラベル";

 → ソースコード上の出現順に連結される
```

**クラスの初期化がトリガーされる条件 (JVM 仕様 §5.5):**

JVM 仕様では、以下のいずれかが最初に発生した時点でクラスが初期化されると定めている。

| トリガー | 例 |
|---------|-----|
| `new` でインスタンス生成 | `new User()` |
| static フィールドへのアクセス（定数を除く） | `User.count` |
| static メソッドの呼び出し | `User.create()` |
| リフレクション | `Class.forName("User")` |
| サブクラスの初期化 | `Admin extends User` → User が先に初期化 |
| JVM 起動時のメインクラス | `java Main.java` の Main |

**初期化されない場合（重要な区別）:**

| 操作 | 初期化されるか |
|------|---------------|
| `static final` コンパイル時定数のアクセス | **されない**（定数は定数プールに直接埋め込まれる） |
| 配列型の生成 `new User[10]` | **されない**（配列型は User クラス自体のインスタンス化ではない） |
| `Class.forName("User", false, cl)` | **されない**（第2引数 `false` で初期化を抑制） |

**スレッドセーフな初期化保証:**

JVM 仕様は、**クラスの初期化が1度だけ、1つのスレッドによって実行される** ことを保証している。複数のスレッドが同時に同じクラスの初期化をトリガーした場合、1つのスレッドだけが `<clinit>` を実行し、他のスレッドはその完了を待機する。

```
 スレッド A: User.count にアクセス
 スレッド B: new User() を同時に実行

 ┌─ HotSpot の初期化ロック ──────────────────────────────┐
 │                                                        │
 │  スレッド A: User の初期化ロックを取得 → <clinit> 実行  │
 │  スレッド B: 初期化ロック待ち... (ブロック)             │
 │                                                        │
 │  スレッド A: <clinit> 完了 → ロック解放                 │
 │  スレッド B: 初期化済みを確認 → そのまま続行            │
 │                                                        │
 │  ★ <clinit> は正確に1回だけ実行される                  │
 └────────────────────────────────────────────────────────┘
```

この保証は、**シングルトンパターンの「初期化オンデマンドホルダーイディオム」** の基盤でもある。

**ExceptionInInitializerError:**

`<clinit>` の実行中に例外が発生すると、その例外は `ExceptionInInitializerError` でラップされて throw される。一度初期化に失敗したクラスは **永久に使用不能** となり、以降のアクセスでは `NoClassDefFoundError` が throw される。

```
 <clinit> 内で例外発生
      │
      ▼
 1回目のアクセス → ExceptionInInitializerError
      │
      ▼
 クラスの状態が「初期化失敗 (Error)」にマークされる
      │
      ▼
 2回目以降のアクセス → NoClassDefFoundError
 （再度 <clinit> が実行されることはない）
```

- 関連ソースパス: `src/hotspot/share/oops/instanceKlass.cpp` (`InstanceKlass::initialize_impl`)

---

## 5. HotSpot 内部のクラスローダー実装 -- C++ 層を追跡する

**対象層:** HotSpot C++ 層

この章では、Java 層のクラスローダー API の背後で動いている HotSpot の C++ 実装を追跡する。

### 5.1 oop-klass モデル -- JVM 内部のオブジェクト・クラス表現

HotSpot VM 内部では、Java のオブジェクトとクラスを表現するために **oop-klass モデル** と呼ばれる2層構造を採用している。

```
 ┌─────────────────────────────────────────────────────────────┐
 │                     oop-klass モデル                         │
 │                                                             │
 │  oop (Ordinary Object Pointer):                             │
 │    Java オブジェクトのインスタンスを表すポインタ。            │
 │    ヒープ上に存在する。                                      │
 │                                                             │
 │  Klass:                                                     │
 │    Java クラスの「型情報」を表す C++ オブジェクト。           │
 │    Metaspace に存在する。                                    │
 │    Java の Class オブジェクトとは別物。                      │
 └─────────────────────────────────────────────────────────────┘
```

**Klass 階層の主要なクラス:**

```
 Klass (抽象基底)
   │
   ├── InstanceKlass          ← 通常の Java クラス (User, String, ...)
   │     │
   │     ├── InstanceMirrorKlass    ← java.lang.Class 自体のクラス
   │     ├── InstanceRefKlass       ← Reference のサブクラス (WeakRef 等)
   │     └── InstanceClassLoaderKlass ← ClassLoader のサブクラス
   │
   └── ArrayKlass             ← 配列型
         │
         ├── TypeArrayKlass   ← プリミティブ配列 (int[], byte[], ...)
         └── ObjArrayKlass    ← オブジェクト配列 (String[], User[], ...)
```

**InstanceKlass の主要フィールド:**

```
 ┌─ InstanceKlass (Metaspace 上) ──────────────────────────────┐
 │                                                              │
 │  _name:             "com/example/User"                      │
 │  _super:            → InstanceKlass* (java.lang.Object)     │
 │  _class_loader_data: → ClassLoaderData*                     │
 │  _constants:         → ConstantPool* (定数プール)            │
 │  _methods:           → Method*[] (メソッド情報の配列)        │
 │  _fields:            → フィールド情報                        │
 │  _vtable:            → 仮想メソッドテーブル                  │
 │  _itable:            → インタフェースメソッドテーブル        │
 │  _java_mirror:       → oop (java.lang.Class オブジェクト)   │
 │  _init_state:        initialized / linked / loaded / error  │
 │                                                              │
 └──────────────────────────────────────────────────────────────┘
```

- 関連ソースパス: `src/hotspot/share/oops/instanceKlass.hpp`
- 関連ソースパス: `src/hotspot/share/oops/klass.hpp`
- 関連ソースパス: `src/hotspot/share/oops/oop.hpp`

### 5.2 SystemDictionary -- ロード済みクラスの管理台帳

**対象層:** HotSpot C++ 層

SystemDictionary は、**JVM 内でロード済みのすべてのクラスを管理するハッシュテーブル** である。[1.2節](#12-クラスの同一性----クラス名--クラスローダーで一意に決まる) で説明した「クラス名 + クラスローダー」のペアによるクラスの一意性は、この SystemDictionary で実現されている。

```
 ┌─ SystemDictionary ──────────────────────────────────────────┐
 │                                                              │
 │  ハッシュキー: (クラス名, ClassLoader oop)                   │
 │  ハッシュ値:   InstanceKlass*                                │
 │                                                              │
 │  ┌────────────────────────────────┬────────────────────────┐ │
 │  │ キー                           │ 値                     │ │
 │  ├────────────────────────────────┼────────────────────────┤ │
 │  │ ("java/lang/String", null)     │ → InstanceKlass* 0x... │ │
 │  │ ("java/lang/Object", null)     │ → InstanceKlass* 0x... │ │
 │  │ ("com/example/User", AppCL)    │ → InstanceKlass* 0x... │ │
 │  │ ("com/example/User", WebAppCL) │ → InstanceKlass* 0x... │ │
 │  └────────────────────────────────┴────────────────────────┘ │
 │                                                              │
 │  ★ 同じ "com/example/User" でもクラスローダーが違えば        │
 │    別のエントリ → 別の型として管理される                     │
 └──────────────────────────────────────────────────────────────┘
```

**resolve_or_fail() の処理フロー:**

クラスの解決（ロード要求）は `SystemDictionary::resolve_or_fail()` から始まる。

```
 SystemDictionary::resolve_or_fail("com/example/User", classLoader)
       │
       ├─ SystemDictionary 内を検索
       │   → 見つかった場合: InstanceKlass* を返す（キャッシュヒット）
       │   → 見つからない場合:
       │         │
       │         ▼
       ├─ ClassLoader.loadClass() を Java 層で呼び出す
       │   （双親委譲モデルの処理がここで走る）
       │         │
       │         ▼
       ├─ defineClass() → ClassFileParser でバイトコードを解析
       │         │
       │         ▼
       ├─ InstanceKlass を生成
       │         │
       │         ▼
       └─ SystemDictionary に登録して返す
```

- 関連ソースパス: `src/hotspot/share/classfile/systemDictionary.cpp`
- 関連ソースパス: `src/hotspot/share/classfile/systemDictionary.hpp`

### 5.3 ClassFileParser -- .class ファイルの解析エンジン

**対象層:** HotSpot C++ 層

ClassFileParser は、.class ファイルのバイト列を解析し、InstanceKlass を生成する C++ クラスである。

**.class ファイルの構造と解析順序:**

```
 .class ファイルのバイナリ構造:

 オフセット  内容                    ClassFileParser のメソッド
 ────────────────────────────────────────────────────────────────
 0x00       マジックナンバー         parse_stream() の冒頭
            0xCA 0xFE 0xBA 0xBE
 0x04       マイナーバージョン       parse_stream()
 0x06       メジャーバージョン       parse_stream()
            (Java 25 = 69)
 0x08       定数プール数             parse_constant_pool()
 ...        定数プールエントリ群     parse_constant_pool_entries()
 ...        アクセスフラグ           parse_stream()
 ...        このクラスの参照         parse_stream()
 ...        親クラスの参照           parse_stream()
 ...        インタフェース群         parse_interfaces()
 ...        フィールド群             parse_fields()
 ...        メソッド群               parse_methods()
 ...        アトリビュート群         parse_classfile_attributes()
```

**定数プール (Constant Pool):**

定数プールは .class ファイルの中核的なデータ構造であり、クラス内で使用されるすべての定数（文字列、数値、クラス参照、メソッド参照等）を格納する。バイトコードの命令はインデックスで定数プール内のエントリを参照する。

```
 定数プールの例 (javap -v で確認可能):

 Constant pool:
    #1 = Methodref    #6.#15     // java/lang/Object."<init>":()V
    #2 = Fieldref     #16.#17    // java/lang/System.out:Ljava/io/PrintStream;
    #3 = String       #18        // "Hello"
    #4 = Methodref    #19.#20    // java/io/PrintStream.println:(...)V
    #5 = Class        #21        // MyClass
    #6 = Class        #22        // java/lang/Object
    ...
```

- 関連ソースパス: `src/hotspot/share/classfile/classFileParser.cpp`
- 関連ソースパス: `src/hotspot/share/classfile/classFileParser.hpp`

### 5.4 Metaspace へのメタデータ配置

**対象層:** HotSpot C++ 層

クラスのメタデータ（InstanceKlass, ConstantPool, Method 等）は **Metaspace** に配置される。Metaspace の詳細は [memory-basics.md のセクション 5.3](../memory/memory-basics.md#53-メソッドエリア--metaspace) で解説しているが、ここではクラスローダーとの関連に焦点を当てる。

**ClassLoaderData -- クラスローダーごとのメタデータ管理:**

HotSpot は、**各クラスローダーに対して ClassLoaderData (CLD) という管理構造** を割り当てる。そのクラスローダーがロードしたすべてのクラスのメタデータは、この CLD 配下の Metaspace チャンクに配置される。

```
 ┌─ ClassLoaderDataGraph ──────────────────────────────────────┐
 │                                                              │
 │  ┌─ CLD (Bootstrap) ──────────────────────────────────────┐ │
 │  │  Metaspace チャンク群:                                  │ │
 │  │    InstanceKlass (Object), InstanceKlass (String), ...  │ │
 │  └─────────────────────────────────────────────────────────┘ │
 │                                                              │
 │  ┌─ CLD (Platform CL) ────────────────────────────────────┐ │
 │  │  Metaspace チャンク群:                                  │ │
 │  │    InstanceKlass (DriverManager), ...                   │ │
 │  └─────────────────────────────────────────────────────────┘ │
 │                                                              │
 │  ┌─ CLD (Application CL) ─────────────────────────────────┐ │
 │  │  Metaspace チャンク群:                                  │ │
 │  │    InstanceKlass (UserService), InstanceKlass (Main)... │ │
 │  └─────────────────────────────────────────────────────────┘ │
 │                                                              │
 │  ┌─ CLD (WebAppClassLoader #1) ───────────────────────────┐ │
 │  │  Metaspace チャンク群:                                  │ │
 │  │    InstanceKlass (MyServlet), ...                       │ │
 │  └─────────────────────────────────────────────────────────┘ │
 │                                                              │
 │  ★ クラスローダーが GC されると → その CLD 全体が解放される  │
 │    → 配下のすべての Metaspace チャンクが回収される           │
 └──────────────────────────────────────────────────────────────┘
```

この設計により、**クラスローダーの GC と Metaspace の回収が連動する**。特定のクラスローダーが不要になり GC で回収されると、そのクラスローダーがロードした全クラスのメタデータが一括で回収される（詳細は [第8章](#8-クラスのアンロード----gc-との連携) で解説）。

- 関連ソースパス: `src/hotspot/share/classfile/classLoaderData.cpp`
- 関連ソースパス: `src/hotspot/share/memory/metaspace/`

---

## 6. モジュールシステム (JPMS) とクラスローダー -- Java 9 以降の世界

**対象層:** Java 層

### 6.1 モジュールシステムの基本

Java 9 で導入された **JPMS (Java Platform Module System)** は、従来の classpath ベースの「フラットな名前空間」を、**明示的な依存関係と公開制御を持つモジュール** で置き換える仕組みである。

```
 ┌─ module-info.java ──────────────────────────────────────┐
 │                                                          │
 │  module com.example.app {                                │
 │      requires java.sql;        // java.sql に依存        │
 │      requires java.logging;    // java.logging に依存    │
 │                                                          │
 │      exports com.example.api;  // このパッケージを公開    │
 │      // com.example.internal は公開しない → 外部からアクセス不可│
 │                                                          │
 │      opens com.example.model to spring.core;             │
 │      // spring.core にのみリフレクションアクセスを許可     │
 │  }                                                       │
 │                                                          │
 └──────────────────────────────────────────────────────────┘
```

**主要な指示子:**

| 指示子 | 意味 |
|--------|------|
| `requires` | 他のモジュールへの依存を宣言 |
| `requires transitive` | 推移的な依存（自分に依存するモジュールにも伝播） |
| `exports` | パッケージを他のモジュールに公開 |
| `exports ... to` | 特定のモジュールにのみ公開 |
| `opens` | リフレクションアクセスを許可 |
| `opens ... to` | 特定のモジュールにのみリフレクションを許可 |
| `uses` | サービスの利用を宣言（SPI） |
| `provides ... with` | サービスの実装を宣言（SPI） |

### 6.2 モジュールとクラスローダーの関係

JPMS の導入により、クラスローダーとモジュールの関係は以下のようになった。

```
 ┌─────────────────────────────────────────────────────────────┐
 │              クラスローダーとモジュールの対応                  │
 │                                                             │
 │  Bootstrap CL ─── java.base                                 │
 │                                                             │
 │  Platform CL ──── java.sql                                  │
 │                    java.xml                                  │
 │                    java.logging                              │
 │                    java.management                           │
 │                    ...                                       │
 │                                                             │
 │  Application CL ── com.example.app (アプリのモジュール)      │
 │                     com.example.lib (ライブラリのモジュール)  │
 │                     Unnamed Module (classpath 上のクラス)     │
 │                                                             │
 │  ★ 1つのクラスローダーが複数のモジュールを担当できる         │
 │  ★ 1つのモジュールは1つのクラスローダーに属する             │
 └─────────────────────────────────────────────────────────────┘
```

**ModuleLayer（モジュールレイヤー）:**

Java 9 以降、JVM はクラスのロードを **モジュールレイヤー** という概念で管理する。JVM 起動時に自動的に **ブートレイヤー (boot layer)** が作成され、すべての標準モジュールとアプリケーションモジュールがここに配置される。

```java
// ブートレイヤーの取得と情報表示
ModuleLayer bootLayer = ModuleLayer.boot();
bootLayer.modules().stream()
    .map(Module::getName)
    .sorted()
    .forEach(IO::println);
// → java.base, java.sql, java.xml, ...
```

### 6.3 暗黙的モジュール (Unnamed Module) と互換性

Java 9 以降でも、**classpath 上に置かれたクラス（module-info.java を持たないライブラリ）** は引き続き動作する。これらのクラスは **Unnamed Module（無名モジュール）** に属する。

```
 ┌─────────────────────────────────────────────────────────────┐
 │                     Unnamed Module の仕組み                  │
 │                                                             │
 │  classpath 上のクラス（module-info.java なし）               │
 │     │                                                       │
 │     ▼                                                       │
 │  Application ClassLoader の Unnamed Module に自動配置        │
 │     │                                                       │
 │     ├─ すべてのモジュールを requires（読み込み可能）         │
 │     ├─ すべてのパッケージを exports（公開状態）              │
 │     └─ すべてのパッケージを opens（リフレクション可能）      │
 │                                                             │
 │  → つまり従来の classpath と同じ動作になる                   │
 │  → 既存のライブラリが module-info.java なしでも動く          │
 └─────────────────────────────────────────────────────────────┘
```

**Automatic Module（自動モジュール）:**

classpath ではなく **module path** に置かれた JAR（module-info.java を持たない）は、**Automatic Module（自動モジュール）** として扱われる。モジュール名は JAR ファイル名から自動生成される。

```
 配置場所による違い:

 classpath  上の JAR → Unnamed Module（フルオープン）
 modulepath 上の JAR → Automatic Module（名前付き、フルオープン）
 modulepath 上 + module-info.java → Named Module（明示的な制御）
```

---

## 7. Spring Boot とクラスローダー -- 実務での活用

**対象層:** Java 層（Spring Boot のクラスローダー機構）

### 7.1 LaunchedURLClassLoader -- Fat JAR の解決策

[jvm-overview.md のセクション 3.4](../jvm-overview.md#34-spring-boot-との関連----動的クラスロードの世界) で触れた Spring Boot の Fat JAR とクラスローダーの関係を詳しく見る。

**問題:** 標準の Application ClassLoader は **「JAR の中に JAR がネストされた構造」** を読み込めない。

```
 spring-boot-app.jar                    ← 通常の JAR として読み込まれる
    │
    ├── META-INF/MANIFEST.MF
    │     Main-Class: org.springframework.boot.loader.JarLauncher
    │     Start-Class: com.example.MyApplication
    │
    ├── BOOT-INF/
    │   ├── classes/                     ← アプリケーションのクラス
    │   │   └── com/example/
    │   │       └── MyApplication.class
    │   │
    │   └── lib/                         ← 依存ライブラリ（JAR in JAR）
    │       ├── spring-core-6.x.jar      ← ★ JAR の中に JAR
    │       ├── spring-web-6.x.jar       ← ★ 標準 CL では読めない
    │       ├── jackson-databind-2.x.jar
    │       └── ...
    │
    └── org/springframework/boot/loader/
        ├── JarLauncher.class            ← エントリーポイント
        └── LaunchedURLClassLoader.class ← カスタムクラスローダー
```

**Spring Boot の起動フロー:**

```
 $ java -jar spring-boot-app.jar
       │
       ▼
 ① JVM が JarLauncher.main() を実行
    （MANIFEST.MF の Main-Class で指定）
       │
       ▼
 ② JarLauncher が LaunchedURLClassLoader を生成
    ├─ BOOT-INF/classes/ を URL として登録
    └─ BOOT-INF/lib/*.jar を URL として登録
       │
       ▼
 ③ Thread Context ClassLoader を LaunchedURLClassLoader に設定
       │
       ▼
 ④ LaunchedURLClassLoader を使って Start-Class をロード
    com.example.MyApplication.class をロード
       │
       ▼
 ⑤ MyApplication.main() をリフレクションで実行
    → Spring Boot アプリケーションが起動
```

### 7.2 起動時間とクラスローディング

Spring Boot アプリケーションの起動時間の大部分は、**クラスのロードと初期化** に費やされている。

**典型的な Spring Boot アプリの起動で何が起きているか:**

```
 Spring Boot 起動時のクラスロード数（目安）:

 小規模アプリ（REST API のみ）:        約 8,000 ～ 12,000 クラス
 中規模アプリ（JPA + Security + Web）: 約 15,000 ～ 20,000 クラス
 大規模アプリ（フル構成）:             約 25,000 ～ 35,000 クラス
```

**-verbose:class でクラスローディングを観察する:**

```bash
# クラスのロード状況を表示して Spring Boot を起動
java -verbose:class -jar myapp.jar 2>&1 | head -50

# 出力例:
# [0.001s][info][class,load] java.lang.Object source: jrt:/java.base
# [0.001s][info][class,load] java.io.Serializable source: jrt:/java.base
# [0.001s][info][class,load] java.lang.Comparable source: jrt:/java.base
# [0.002s][info][class,load] java.lang.CharSequence source: jrt:/java.base
# [0.002s][info][class,load] java.lang.String source: jrt:/java.base
# ...
# [1.234s][info][class,load] com.example.MyController source: ...BOOT-INF/classes/
```

**クラスデータ共有 (CDS / AppCDS) による高速化:**

CDS (Class Data Sharing) は、**ロード済みのクラスのメタデータをファイルにダンプし、次回起動時にそのまま Metaspace にマップする** ことで起動時間を短縮する仕組みである。

```
 通常の起動:
   .class ファイル → 解析 → 検証 → Metaspace に配置  ← 毎回実行

 CDS を使った起動:
   共有アーカイブ (.jsa) → mmap で Metaspace にマップ  ← 解析・検証をスキップ
```

```bash
# ステップ1: クラスリストを作成
java -Xshare:off -XX:DumpLoadedClassList=classes.lst -jar myapp.jar

# ステップ2: 共有アーカイブを作成
java -Xshare:dump -XX:SharedClassListFile=classes.lst \
     -XX:SharedArchiveFile=app-cds.jsa -jar myapp.jar

# ステップ3: 共有アーカイブを使って起動（高速！）
java -Xshare:on -XX:SharedArchiveFile=app-cds.jsa -jar myapp.jar
```

Spring Boot 3.3 以降では `spring-boot:build-image` で CDS 対応のコンテナイメージを自動生成する機能が提供されている。

### 7.3 DevTools とクラスローダーの再作成

Spring Boot DevTools は、**クラスローダーを破棄して再作成する** ことで、JVM を再起動せずにクラスの変更を反映する。

```
 ┌─────────────────────────────────────────────────────────────┐
 │            Spring Boot DevTools のホットリロード              │
 │                                                             │
 │  JVM 起動時:                                                │
 │  ┌─ Base ClassLoader ──────────────────────────────────┐    │
 │  │  変更されない依存ライブラリ群                         │    │
 │  │  (Spring Framework, Jackson, Hibernate, ...)         │    │
 │  │                                                      │    │
 │  │  ┌─ Restart ClassLoader (#1) ──────────────────┐     │    │
 │  │  │  開発者が書いたクラス                         │     │    │
 │  │  │  (UserController, UserService, ...)          │     │    │
 │  │  └──────────────────────────────────────────────┘     │    │
 │  └──────────────────────────────────────────────────────┘    │
 │                                                             │
 │  ソースコード変更検出！                                      │
 │                                                             │
 │  ┌─ Base ClassLoader ──────────────────────────────────┐    │
 │  │  ★ そのまま維持（再ロードしない）                    │    │
 │  │                                                      │    │
 │  │  ┌─ Restart ClassLoader (#2) ──────────────────┐     │    │
 │  │  │  ★ 新しいクラスローダーで再ロード             │     │    │
 │  │  │  (更新された UserController, ...)             │     │    │
 │  │  └──────────────────────────────────────────────┘     │    │
 │  └──────────────────────────────────────────────────────┘    │
 │                                                             │
 │  旧 Restart ClassLoader (#1) → GC で回収                    │
 │  → 旧クラスのメタデータも Metaspace から解放               │
 └─────────────────────────────────────────────────────────────┘
```

**高速リロードの秘密:** ライブラリ群（数千クラス）を再ロードせず、開発者のクラス（数十〜数百クラス）だけを再ロードするため、フル再起動（数秒〜数十秒）に対して 1 秒前後でリロードが完了する。

### 7.4 クラスローダーリーク -- メモリリークの原因

クラスローダーリークは、**アプリケーションのアンデプロイ後にクラスローダーが GC されない** ことで発生するメモリリークである。特に Tomcat 等のアプリケーションサーバーで問題になる。

**リークの原因パターン:**

```
 ┌─────────────────────────────────────────────────────────────┐
 │              クラスローダーリークの典型パターン               │
 │                                                             │
 │  パターン1: ThreadLocal による参照保持                       │
 │  ┌──────────────┐     ┌──────────────────┐                  │
 │  │  スレッド     │────▶│  ThreadLocal 値   │                  │
 │  │ (プール管理)  │     │  (アプリのクラス)  │                  │
 │  └──────────────┘     └────────┬─────────┘                  │
 │                                │ 参照                        │
 │                                ▼                             │
 │                       WebAppClassLoader ← GC できない！     │
 │                                                             │
 │  パターン2: JDBC ドライバの登録解除忘れ                      │
 │  ┌──────────────────┐     ┌──────────────────┐              │
 │  │  DriverManager   │────▶│  JDBC ドライバ    │              │
 │  │ (Bootstrap CL)   │     │  (WebApp CL)     │              │
 │  └──────────────────┘     └────────┬─────────┘              │
 │                                    │ 参照                    │
 │                                    ▼                         │
 │                           WebAppClassLoader ← GC できない！ │
 │                                                             │
 │  パターン3: シャットダウンフック/シグナルハンドラ             │
 │  ┌──────────────────┐     ┌──────────────────┐              │
 │  │  Runtime          │────▶│  ShutdownHook    │              │
 │  │ (Bootstrap CL)   │     │  (アプリのクラス)  │              │
 │  └──────────────────┘     └────────┬─────────┘              │
 │                                    │ 参照                    │
 │                                    ▼                         │
 │                           WebAppClassLoader ← GC できない！ │
 └─────────────────────────────────────────────────────────────┘
```

**なぜ深刻か:** クラスローダーが GC されないと、そのクラスローダーがロードした **すべてのクラスの Metaspace** が解放されない。アプリの再デプロイを繰り返すたびに Metaspace が増加し、最終的に `OutOfMemoryError: Metaspace` が発生する。

**対策:**

| 対策 | 詳細 |
|------|------|
| ThreadLocal の `remove()` | サーブレットフィルターの `finally` ブロックで確実に呼ぶ |
| JDBC ドライバの明示的な登録解除 | `DriverManager.deregisterDriver()` を `contextDestroyed()` で実行 |
| Tomcat の `JreMemoryLeakPreventionListener` | Tomcat 組み込みのリーク防止リスナー |
| `static` 変数でのアプリクラス参照を避ける | コアクラスからアプリクラスを参照しない設計 |

---

## 8. クラスのアンロード -- GC との連携

**対象層:** HotSpot C++ 層

### 8.1 クラスがアンロードされる条件

Java のクラスは「いつでも好きなタイミングでアンロードできる」わけではない。クラスのアンロードには **厳格な条件** がある。

```
 ┌─────────────────────────────────────────────────────────────┐
 │          クラスがアンロードされるための3つの条件               │
 │          （すべて満たす必要がある）                            │
 │                                                             │
 │  ① そのクラスのすべてのインスタンスが GC 対象であること       │
 │     → new User() で生成したオブジェクトがすべて到達不能      │
 │                                                             │
 │  ② そのクラスの java.lang.Class オブジェクトが到達不能        │
 │     → User.class への参照がどこにもない                      │
 │                                                             │
 │  ③ そのクラスをロードしたクラスローダーが GC 対象であること   │
 │     → クラスローダー自体への参照がどこにもない                │
 │                                                             │
 │  ★ 条件③が最も重要:                                        │
 │    クラスローダーが GC されると、そのクラスローダーが          │
 │    ロードしたすべてのクラスが一括でアンロードされる            │
 │                                                             │
 │  ★ Bootstrap/Platform/Application CL は GC されない          │
 │    → これらがロードしたクラスは JVM 終了まで存在し続ける     │
 └─────────────────────────────────────────────────────────────┘
```

**つまり:** 実際にクラスのアンロードが発生するのは、**カスタムクラスローダー** を使っている場合（アプリケーションサーバー、OSGi、Spring Boot DevTools 等）のみである。3つの標準クラスローダーがロードしたクラスがアンロードされることはない。

### 8.2 Metaspace とクラスアンロードの関係

クラスのアンロードは、Metaspace のメモリ回収と直結している。

```
 クラスアンロードの流れ:

 ① GC がクラスローダーの到達不能を検出
       │
       ▼
 ② ClassLoaderData (CLD) を「アンロード対象」にマーク
       │
       ▼
 ③ CLD 配下のすべての InstanceKlass を無効化
    ├─ SystemDictionary からエントリを削除
    ├─ 定数プール内の参照を解放
    └─ vtable / itable を無効化
       │
       ▼
 ④ Metaspace チャンクを空きリストに返却
    → 新しいクラスのロードで再利用可能になる
       │
       ▼
 ⑤ ヒープ上の java.lang.Class オブジェクト(mirror oop) も GC 対象に
```

**GC アルゴリズムとクラスアンロードの関係:**

| GC | クラスアンロードのタイミング |
|----|---------------------------|
| **G1GC** | Full GC 時、または Concurrent Cycle 後の Remark フェーズ |
| **ZGC** | コンカレントフェーズでアンロード |
| **Serial GC** | Full GC 時 |

クラスアンロードの詳細な GC 連携については [gc-deep-dive.md](../memory/gc-deep-dive.md) を参照。

- 関連ソースパス: `src/hotspot/share/classfile/classLoaderData.cpp`
- 関連ソースパス: `src/hotspot/share/oops/instanceKlass.cpp` (`InstanceKlass::release_C_heap_structures`)

### 8.3 -verbose:class でロード/アンロードを観察する

クラスのロードとアンロードは、JVM オプションで観察できる。

```bash
# クラスのロードを表示
java -verbose:class MyApp.java

# Java 9+ 統一ロギング（より詳細）
java -Xlog:class+load=info MyApp.java

# クラスのアンロードも表示
java -Xlog:class+unload=info MyApp.java

# ロードとアンロードの両方を表示
java -Xlog:class+load=info,class+unload=info MyApp.java
```

**出力例:**

```
[0.015s][info][class,load] java.lang.Object source: jrt:/java.base
[0.015s][info][class,load] java.io.Serializable source: jrt:/java.base
[0.016s][info][class,load] java.lang.Comparable source: jrt:/java.base
...
[2.451s][info][class,unload] unloading class com.example.OldPlugin 0x800100230
```

---

## 9. トラブルシューティング -- よくあるクラスローダー関連のエラー

**対象層:** Java 層

### 9.1 ClassNotFoundException vs NoClassDefFoundError

この2つは初学者が最も混同しやすいエラーである。

```
 ┌─────────────────────────────────────────────────────────────┐
 │   ClassNotFoundException vs NoClassDefFoundError             │
 │                                                             │
 │  ┌─ ClassNotFoundException ──────────────────────────────┐  │
 │  │                                                        │  │
 │  │  いつ:  ロードフェーズ                                  │  │
 │  │  種類:  checked 例外 (Exception の子)                   │  │
 │  │  原因:  クラスパス上にクラスが見つからない               │  │
 │  │  発生元: ClassLoader.loadClass()                        │  │
 │  │         Class.forName()                                │  │
 │  │  典型例: 依存 JAR が classpath にない                   │  │
 │  │         クラス名のタイポ                                │  │
 │  │                                                        │  │
 │  └────────────────────────────────────────────────────────┘  │
 │                                                             │
 │  ┌─ NoClassDefFoundError ────────────────────────────────┐  │
 │  │                                                        │  │
 │  │  いつ:  リンクフェーズ / 初期化フェーズ                  │  │
 │  │  種類:  Error (回復不能)                                │  │
 │  │  原因:  コンパイル時には存在したが、実行時に見つからない │  │
 │  │         または初期化に失敗した                          │  │
 │  │  発生元: JVM のリンカー                                 │  │
 │  │  典型例: コンパイル後に JAR を削除した                   │  │
 │  │         static イニシャライザで例外が発生した            │  │
 │  │         (2回目以降のアクセスで発生)                     │  │
 │  │                                                        │  │
 │  └────────────────────────────────────────────────────────┘  │
 │                                                             │
 │  覚え方:                                                    │
 │  ClassNotFoundException → 「探したけど見つからなかった」     │
 │  NoClassDefFoundError   → 「あるはずなのに見つからない」     │
 └─────────────────────────────────────────────────────────────┘
```

### 9.2 ClassCastException（同名クラスの型不一致）

[1.2節](#12-クラスの同一性----クラス名--クラスローダーで一意に決まる) で説明した「クラスの同一性」に関連するエラー。

```
 発生シナリオ:

 ClassLoader A が User.class をロード → 型 X
 ClassLoader B が User.class をロード → 型 Y

 // ClassLoader A でロードした User のインスタンス
 Object obj = classLoaderA_User_instance;

 // ClassLoader B でロードした User にキャスト → ClassCastException！
 User user = (User) obj;
 // → "com.example.User cannot be cast to com.example.User"
 //    同じ名前なのに別の型！
```

**エラーメッセージに同じクラス名が2回現れる** のが特徴。この場合、クラスローダーが異なることが原因である。

**デバッグ方法:**

```java
// どのクラスローダーでロードされたか確認
IO.println("obj のクラスローダー: " + obj.getClass().getClassLoader());
IO.println("User のクラスローダー: " + User.class.getClassLoader());
```

### 9.3 LinkageError と UnsupportedClassVersionError

| エラー | 原因 | 対策 |
|--------|------|------|
| `LinkageError` | 同じクラスが異なるクラスローダーで重複定義された | クラスパスの重複を排除 |
| `UnsupportedClassVersionError` | .class ファイルが現在の JVM より新しいバージョンでコンパイルされた | JDK バージョンを合わせる |

**UnsupportedClassVersionError のバージョン対応表:**

| クラスファイルバージョン | Java バージョン |
|------------------------|----------------|
| 52.0 | Java 8 |
| 55.0 | Java 11 |
| 61.0 | Java 17 |
| 65.0 | Java 21 |
| 69.0 | Java 25 |

### 9.4 デバッグ手法まとめ

| 手法 | コマンド / API | 用途 |
|------|---------------|------|
| クラスロードのログ | `-verbose:class` | どのクラスがいつロードされたか確認 |
| 詳細ログ（Java 9+） | `-Xlog:class+load=debug` | ロード元（JAR パス等）を含む詳細ログ |
| アンロードのログ | `-Xlog:class+unload=info` | クラスのアンロードを確認 |
| クラスのロード元確認 | `MyClass.class.getProtectionDomain().getCodeSource()` | どの JAR からロードされたか |
| クラスローダー確認 | `MyClass.class.getClassLoader()` | どのクラスローダーがロードしたか |
| リソースの場所確認 | `ClassLoader.getResource("com/example/User.class")` | クラスパス上のどこにあるか |
| Metaspace 使用量 | `-XX:+PrintMetaspaceStatistics`（JVM 終了時） | クラスメタデータのメモリ使用量 |

---

## 10. まとめ -- クラスローダーの全体像

### Q&A 形式の最重要概念

| 質問 | 回答 |
|------|------|
| クラスローダーの本質的な役割は？ | .class の読み込みだけでなく、検証・メモリ配置・名前空間の分離を担う型システムの基盤 |
| クラスの同一性は何で決まる？ | **完全修飾クラス名 + クラスローダーインスタンス** の組み合わせ |
| 標準のクラスローダーは何階層？ | 3階層: Bootstrap (C++) → Platform (Java) → Application (Java) |
| 双親委譲モデルの目的は？ | セキュリティ（コア偽装防止）、一意性（重複防止）、可視性（階層的アクセス制御） |
| Bootstrap CL の getClassLoader() が null を返すのは？ | C++ で実装されており、Java オブジェクトとして存在しないため |
| クラスのライフサイクルは？ | ロード → リンク（検証→準備→解決）→ 初期化 → 使用 → アンロード |
| `<clinit>` はいつ実行される？ | 初期化フェーズ。new, static フィールドアクセス, static メソッド呼び出し等がトリガー |
| `<clinit>` はスレッドセーフか？ | はい。JVM が初期化ロックで保証する（正確に1回だけ実行） |
| クラスはいつアンロードされる？ | そのクラスをロードしたクラスローダーが GC されたとき |
| Bootstrap/Platform/Application CL がロードしたクラスはアンロードされる？ | **されない。** JVM 終了まで存在し続ける |
| SPI で双親委譲が問題になるのは？ | 親（Bootstrap）が子（Application）のクラスを見られないため |
| SPI の解決策は？ | Thread Context ClassLoader |
| ClassNotFoundException と NoClassDefFoundError の違いは？ | 前者はロード時（探しても見つからない）、後者はリンク/初期化時（あるはずなのに見つからない） |
| CDS / AppCDS の効果は？ | クラスの解析・検証をスキップし、Metaspace に直接マップすることで起動時間を短縮 |

### 関連する実験コード

| プログラム | 内容 | 実行方法 |
|-----------|------|---------|
| `ClassLoaderExplorer.java` | 3階層の親チェーン可視化、代表クラスのロード元確認、モジュールとの関係、Thread Context CL | `java src/main/java/classloader/ClassLoaderExplorer.java` |
| `ClassLifecycleDemo.java` | 初期化トリガー条件の実験（new, static フィールド, Class.forName）、ExceptionInInitializerError → NoClassDefFoundError の連鎖 | `java src/main/java/classloader/ClassLifecycleDemo.java` |
| `ClassIdentityDemo.java` | .class バイトコードの手動生成、カスタムクラスローダーの実装、同名クラスの型不一致（ClassCastException）の再現 | `java src/main/java/classloader/ClassIdentityDemo.java` |

---

## 参考リンク・ソースパス

### JVM 仕様・公式ドキュメント

- [The Java Virtual Machine Specification (Java SE 25) - Chapter 5: Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html) -- クラスのライフサイクルの公式仕様
- [The Java Virtual Machine Specification (Java SE 25) - §4.10: Verification of class Files](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10) -- バイトコード検証の仕様
- [The Java Virtual Machine Specification (Java SE 25) - §5.3: Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3) -- クラスの生成とロード
- [The Java Virtual Machine Specification (Java SE 25) - §5.5: Initialization](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.5) -- クラスの初期化条件

### HotSpot ソースパス（OpenJDK mainline）

| コンポーネント | ソースパス |
|--------------|-----------|
| Bootstrap ClassLoader (C++ 実装) | `src/hotspot/share/classfile/classLoader.cpp` |
| クラスファイルパーサー | `src/hotspot/share/classfile/classFileParser.cpp` |
| バイトコード検証器 | `src/hotspot/share/classfile/verifier.cpp` |
| SystemDictionary | `src/hotspot/share/classfile/systemDictionary.cpp` |
| ClassLoaderData | `src/hotspot/share/classfile/classLoaderData.cpp` |
| InstanceKlass | `src/hotspot/share/oops/instanceKlass.hpp` |
| Klass 基底クラス | `src/hotspot/share/oops/klass.hpp` |
| oop（オブジェクトポインタ） | `src/hotspot/share/oops/oop.hpp` |
| リンク解決 | `src/hotspot/share/interpreter/linkResolver.cpp` |
| Metaspace | `src/hotspot/share/memory/metaspace/` |

### Java 層の主要クラス

| クラス | 役割 |
|--------|------|
| `java.lang.ClassLoader` | クラスローダーの抽象基底クラス |
| `java.lang.Class` | ロード済みクラスの Java 層表現 |
| `java.lang.Module` | モジュールの Java 層表現 |
| `java.lang.ModuleLayer` | モジュールレイヤー |
| `jdk.internal.loader.ClassLoaders` | Bootstrap/Platform/Application CL の実装 |
| `jdk.internal.loader.BuiltinClassLoader` | Platform/Application CL の基底クラス |

### 本プロジェクト内の関連ドキュメント

- [jvm-overview.md](../jvm-overview.md) -- JVM 全体像（本ドキュメントの概要版はセクション3）
- [memory-basics.md](../memory/memory-basics.md) -- JVM メモリの基礎（Metaspace の詳細はセクション5.3）
- [gc-deep-dive.md](../memory/gc-deep-dive.md) -- GC ディープダイブ（クラスアンロードと GC の連携）
