# JNI 詳解 -- Java とネイティブコードの境界を深掘りする

- [概要](#概要)
- [1. JNI とは何か -- なぜ Java にネイティブコードが必要なのか](#1-jni-とは何か----なぜ-java-にネイティブコードが必要なのか)
  - [1.1 「Write Once, Run Anywhere」の裏側](#11-write-once-run-anywhere-の裏側)
  - [1.2 JNI の定義と歴史](#12-jni-の定義と歴史)
  - [1.3 JNI が必要とされる4つの理由](#13-jni-が必要とされる4つの理由)
- [2. JNI のアーキテクチャ -- 3層構造の全体像](#2-jni-のアーキテクチャ----3層構造の全体像)
  - [2.1 3層構造の図解](#21-3層構造の図解)
  - [2.2 JNIEnv ポインタ -- ネイティブコードから Java 世界にアクセスする窓口](#22-jnienv-ポインタ----ネイティブコードから-java-世界にアクセスする窓口)
  - [2.3 ローカル参照とグローバル参照 -- GC との協調](#23-ローカル参照とグローバル参照----gc-との協調)
  - [2.4 JNI 関数テーブル -- 約230個の関数群](#24-jni-関数テーブル----約230個の関数群)
- [3. 身近な JNI の利用例 -- 標準ライブラリの内部](#3-身近な-jni-の利用例----標準ライブラリの内部)
  - [3.1 ファイル I/O -- OS のシステムコールへの橋渡し](#31-ファイル-io----os-のシステムコールへの橋渡し)
  - [3.2 ネットワーク通信 -- ソケット API への接続](#32-ネットワーク通信----ソケット-api-への接続)
  - [3.3 DB 操作 (JDBC) -- Type 2 vs Type 4 ドライバ](#33-db-操作-jdbc----type-2-vs-type-4-ドライバ)
  - [3.4 スレッド -- OS スレッドの生成と管理](#34-スレッド----os-スレッドの生成と管理)
  - [3.5 時刻・乱数 -- OS のタイマーとエントロピー源](#35-時刻乱数----os-のタイマーとエントロピー源)
  - [3.6 標準ライブラリの native メソッド統計](#36-標準ライブラリの-native-メソッド統計)
- [4. native メソッドの宣言と実装 -- 開発者が JNI を書く場合](#4-native-メソッドの宣言と実装----開発者が-jni-を書く場合)
  - [4.1 開発の流れ -- 5ステップ](#41-開発の流れ----5ステップ)
  - [4.2 型マッピング -- Java 型と C 型の対応](#42-型マッピング----java-型と-c-型の対応)
  - [4.3 JNI の命名規則](#43-jni-の命名規則)
- [5. JNI のコストとオーバーヘッド](#5-jni-のコストとオーバーヘッド)
  - [5.1 境界越えのコスト -- 何が起きているのか](#51-境界越えのコスト----何が起きているのか)
  - [5.2 JIT 最適化の限界 -- インライン化不可の壁](#52-jit-最適化の限界----インライン化不可の壁)
  - [5.3 GC セーフポイントとの関係](#53-gc-セーフポイントとの関係)
  - [5.4 コスト削減のベストプラクティス](#54-コスト削減のベストプラクティス)
- [6. JNI の代替技術 -- Foreign Function & Memory API (FFM API)](#6-jni-の代替技術----foreign-function--memory-api-ffm-api)
  - [6.1 FFM API とは -- Panama プロジェクトの成果](#61-ffm-api-とは----panama-プロジェクトの成果)
  - [6.2 JNI vs FFM API の比較](#62-jni-vs-ffm-api-の比較)
  - [6.3 FFM API のコード例](#63-ffm-api-のコード例)
  - [6.4 移行の指針](#64-移行の指針)
- [7. HotSpot C++ 層での JNI 実装](#7-hotspot-c-層での-jni-実装)
  - [7.1 jni.cpp -- JNI 関数テーブルの実装](#71-jnicpp----jni-関数テーブルの実装)
  - [7.2 native メソッドの解決プロセス](#72-native-メソッドの解決プロセス)
  - [7.3 JNI ハンドルとオブジェクト参照](#73-jni-ハンドルとオブジェクト参照)
- [8. Spring Boot と JNI -- 間接的な JNI 利用の実態](#8-spring-boot-と-jni----間接的な-jni-利用の実態)
  - [8.1 Spring Boot アプリケーションの JNI 依存マップ](#81-spring-boot-アプリケーションの-jni-依存マップ)
  - [8.2 DB 接続 -- HikariCP → JDBC → JNI の流れ](#82-db-接続----hikaricp--jdbc--jni-の流れ)
  - [8.3 ログ出力 -- Logback → FileOutputStream → JNI](#83-ログ出力----logback--fileoutputstream--jni)
  - [8.4 HTTP サーバー -- Tomcat のソケット処理](#84-http-サーバー----tomcat-のソケット処理)
- [9. まとめ -- Q&A 形式の早見表](#9-まとめ----qa-形式の早見表)
- [10. 実験用 Java コード](#10-実験用-java-コード)
- [参考リンク・ソースパス](#参考リンクソースパス)

---

## 概要

本ドキュメントは、JNI (Java Native Interface) の内部構造と実際の利用場面を詳細に解説するガイドである。[jvm-overview.md](../jvm-overview.md) のセクション6「ネイティブメソッドインタフェース (JNI)」で概要を把握した上で、本ドキュメントで JNI のアーキテクチャ、身近な利用例、HotSpot 内部での実装を深掘りする。

**対象層:** Java 層 / JNI 層 / HotSpot C++ 層（JNI は3層すべてにまたがる技術）

**対象ランタイム:** Azul Zulu 25 (OpenJDK 25 ベース / HotSpot VM)

**前提知識:**
- [jvm-overview.md](../jvm-overview.md) -- JVM 全体像の把握（特にセクション6）
- [execution-engine-deep-dive.md](../runtime/execution-engine-deep-dive.md) -- JIT コンパイラと JNI の関係

**位置づけ:**

```
jvm-overview.md（全体像の地図）
    │
    ├── classloader-deep-dive.md（クラスローダーの詳細）
    │
    ├── execution-engine-deep-dive.md（実行エンジンの詳細）
    │
    ├── 本ドキュメント（JNI の詳細）  ← ここ
    │
    ├── memory-basics.md（メモリの詳細）
    │
    └── gc-deep-dive.md（GC の詳細）
```

---

## 1. JNI とは何か -- なぜ Java にネイティブコードが必要なのか

**対象層:** Java 層 / JNI 層

### 1.1 「Write Once, Run Anywhere」の裏側

Java の最大の特徴は「Write Once, Run Anywhere（一度書けばどこでも動く）」というポータビリティである。Java ソースコードはバイトコードにコンパイルされ、JVM がそのバイトコードを各 OS 上で実行する。これにより、開発者は OS の違いを意識せずにプログラムを書ける。

しかし、**バイトコードだけでは OS の機能にアクセスできない** という根本的な制約がある。

```
  ┌──────────────────────────────────────────────────────────────────┐
  │  「Java だけでは何もできない」問題                                 │
  │                                                                  │
  │  Java バイトコード                                                │
  │    ├── 計算（足し算、比較）       → バイトコードで完結              │
  │    ├── オブジェクト生成           → JVM が管理                     │
  │    ├── メソッド呼び出し           → バイトコードで完結              │
  │    │                                                              │
  │    ├── ファイルを読む             → OS のシステムコールが必要 ★    │
  │    ├── ネットワーク通信           → OS のソケット API が必要   ★    │
  │    ├── スレッドを作る             → OS のスレッド API が必要  ★    │
  │    ├── 現在時刻を取得             → OS のタイマー API が必要  ★    │
  │    └── 画面に文字を表示           → OS の出力 API が必要     ★    │
  │                                                                  │
  │  ★ の処理を実現するのが JNI である                                │
  └──────────────────────────────────────────────────────────────────┘
```

つまり、**Java の「クロスプラットフォーム」は、JNI が各 OS のネイティブコードを抽象化しているからこそ成り立っている**。Java 開発者が OS の違いを意識しなくてよいのは、JDK の開発者が JNI 経由で各 OS 向けのネイティブコードを実装してくれているおかげなのである。

### 1.2 JNI の定義と歴史

**JNI (Java Native Interface)** は、Java コードとネイティブコード（C, C++ 等）の間で相互に呼び出しを行うための**標準的なプログラミングインタフェース**である。

| 項目 | 内容 |
|------|------|
| 正式名称 | Java Native Interface |
| 導入時期 | JDK 1.1 (1997年) |
| 仕様 | JNI Specification（JDK のドキュメントに含まれる） |
| 目的 | Java ↔ ネイティブコードの双方向呼び出し |
| 主な用途 | OS 機能アクセス、既存 C/C++ ライブラリ利用、性能最適化 |
| 後継技術 | FFM API (Foreign Function & Memory API, JEP 454, Java 22 で正式) |

JNI が「インタフェース」と呼ばれるのは、**Java と C/C++ という2つの異なる言語/ランタイムの間に「共通の約束事（プロトコル）」を定めている** からである。Java 側は `native` キーワードでメソッドを宣言し、C/C++ 側はその約束に従った関数名・シグネチャで実装する。この「約束事」があるからこそ、JVM は実行時にネイティブ関数を正しく見つけて呼び出せる。

### 1.3 JNI が必要とされる4つの理由

```
  ┌─────────────────────────────────────────────────────────────┐
  │              JNI が必要とされる理由                           │
  │                                                             │
  │  1. OS 機能へのアクセス                                      │
  │     ファイル I/O、ネットワーク、スレッド、時刻取得             │
  │     → バイトコードでは表現できない OS 固有の操作               │
  │                                                             │
  │  2. 既存のネイティブライブラリの利用                          │
  │     OpenSSL, SQLite, 画像処理ライブラリ等                    │
  │     → 数十年の実績ある C/C++ 資産を再利用                    │
  │                                                             │
  │  3. パフォーマンスクリティカルな処理                          │
  │     暗号化、圧縮、行列計算等                                  │
  │     → CPU の特殊命令 (SIMD 等) を直接利用                    │
  │                                                             │
  │  4. ハードウェアの直接操作                                    │
  │     GPU, シリアルポート, USB デバイス等                       │
  │     → Java では直接アクセスできないハードウェア               │
  └─────────────────────────────────────────────────────────────┘
```

重要なのは、**理由1（OS 機能へのアクセス）がすべての Java プログラムに関係する** という点である。`IO.println("Hello")` でさえ、最終的には OS の `write()` システムコールを JNI 経由で呼んでいる。つまり、**JNI を直接書いたことがなくても、すべての Java 開発者は間接的に JNI を使っている**。

---

## 2. JNI のアーキテクチャ -- 3層構造の全体像

**対象層:** Java 層 / JNI 層 / HotSpot C++ 層

### 2.1 3層構造の図解

JNI は、Java 層とネイティブ層の間に「境界（boundary）」を設ける仕組みである。この境界を越えるためには、型変換や参照管理などの処理が必要になる。

```
  ┌───────────────────────────────────────────────────────────────────────────┐
  │                       JNI の3層構造                                       │
  │                                                                           │
  │  ┌──────────────────────────────────────────┐                             │
  │  │         Java 層                           │                             │
  │  │                                           │                             │
  │  │  Java コード                               │                             │
  │  │    native void write(byte[] b);           │  ← native 修飾子で宣言      │
  │  │    System.loadLibrary("mylib");           │  ← ネイティブライブラリのロード│
  │  │                                           │                             │
  │  └──────────────┬───────────────────────────┘                             │
  │                  │ JNI 境界 (boundary crossing)                            │
  │  ┌──────────────▼───────────────────────────┐                             │
  │  │         JNI 層（フレームワーク）            │                             │
  │  │                                           │                             │
  │  │  JNIEnv* env  ← ネイティブから Java を操作  │                             │
  │  │  ├─ 型変換: jint ↔ int, jstring ↔ String  │                             │
  │  │  ├─ 参照管理: ローカル参照 / グローバル参照  │                             │
  │  │  ├─ 例外処理: ExceptionOccurred() 等       │                             │
  │  │  └─ 関数テーブル: 約230個の JNI 関数        │                             │
  │  │                                           │                             │
  │  └──────────────┬───────────────────────────┘                             │
  │                  │                                                         │
  │  ┌──────────────▼───────────────────────────┐                             │
  │  │         ネイティブ層 (C/C++)               │                             │
  │  │                                           │                             │
  │  │  JNIEXPORT void JNICALL                   │                             │
  │  │  Java_com_example_MyClass_write(          │                             │
  │  │      JNIEnv *env,                         │  ← JVM が提供する環境        │
  │  │      jobject this,                        │  ← Java の this 参照         │
  │  │      jbyteArray b) {                      │  ← Java の byte[] に対応     │
  │  │      // OS のシステムコールを呼ぶ           │                             │
  │  │      write(fd, buf, len);                 │                             │
  │  │  }                                        │                             │
  │  └──────────────────────────────────────────┘                             │
  └───────────────────────────────────────────────────────────────────────────┘
```

### 2.2 JNIEnv ポインタ -- ネイティブコードから Java 世界にアクセスする窓口

`JNIEnv` は、ネイティブコードが Java の世界にアクセスするための**唯一の窓口**である。ネイティブ関数は第1引数として必ず `JNIEnv*` を受け取る。

```
  ┌─────────────────────────────────────────────────────────────┐
  │  JNIEnv の主要機能カテゴリ                                    │
  │                                                             │
  │  ┌──────────────────┐                                       │
  │  │   JNIEnv* env    │                                       │
  │  ├──────────────────┤                                       │
  │  │ クラス操作        │  FindClass, GetMethodID,              │
  │  │                  │  GetFieldID, NewObject                │
  │  ├──────────────────┤                                       │
  │  │ 文字列操作        │  NewStringUTF, GetStringUTFChars,     │
  │  │                  │  ReleaseStringUTFChars                │
  │  ├──────────────────┤                                       │
  │  │ 配列操作          │  NewIntArray, GetIntArrayElements,    │
  │  │                  │  SetIntArrayRegion                    │
  │  ├──────────────────┤                                       │
  │  │ 例外処理          │  Throw, ExceptionOccurred,            │
  │  │                  │  ExceptionClear                       │
  │  ├──────────────────┤                                       │
  │  │ 参照管理          │  NewGlobalRef, DeleteGlobalRef,       │
  │  │                  │  NewLocalRef, DeleteLocalRef          │
  │  ├──────────────────┤                                       │
  │  │ モニター          │  MonitorEnter, MonitorExit            │
  │  └──────────────────┘                                       │
  └─────────────────────────────────────────────────────────────┘
```

**重要:** `JNIEnv` は**スレッドローカル**である。各スレッドは固有の `JNIEnv` を持ち、スレッド間で共有してはならない。これは、JVM がスレッドごとに異なるスタックやローカル参照フレームを管理しているためである。

### 2.3 ローカル参照とグローバル参照 -- GC との協調

JNI で Java オブジェクトをネイティブ側から操作する際、**参照の管理**が極めて重要になる。GC はヒープ上のオブジェクトを移動させる可能性があるため、ネイティブコードが生のポインタでオブジェクトを保持すると、GC 後にダングリングポインタになりかねない。

| 参照の種類 | 寿命 | GC からの保護 | 用途 |
|-----------|------|-------------|------|
| **ローカル参照** | ネイティブメソッドの呼び出し期間中のみ | あり（呼び出し中は GC が回収しない） | 一時的なオブジェクト操作 |
| **グローバル参照** | 明示的に削除するまで永続 | あり（削除するまで GC が回収しない） | キャッシュ、コールバック用 |
| **弱グローバル参照** | 明示的に削除するまで存在 | なし（GC がいつでも回収可能） | キャッシュ（メモリ不足時は回収許容） |

```
  ローカル参照のライフサイクル:

  Java コード                      ネイティブコード
  ─────────────────────────────────────────────────
  obj.nativeMethod()  ─────▶  JNI フレーム作成
                              │
                              │  jobject localRef = ...; ← ローカル参照が有効
                              │  // オブジェクトを使った処理
                              │
                              ◀── return ── ここでローカル参照は自動的に解放される
                                             GC がオブジェクトを回収可能になる
```

### 2.4 JNI 関数テーブル -- 約230個の関数群

JNI の実体は、約230個の関数ポインタを持つ **関数テーブル（function table）** である。`JNIEnv` はこのテーブルへのポインタであり、C++ からは `env->FindClass(...)` のようにメソッド風に呼び出せる。

この設計は **COM (Component Object Model)** の vtable（仮想関数テーブル）にインスピレーションを受けている。関数テーブル方式を採用することで、JNI のバイナリ互換性が保たれる -- JVM の内部実装が変わっても、テーブルのインデックスが変わらなければ既存のネイティブライブラリは再コンパイルなしで動作する。

---

## 3. 身近な JNI の利用例 -- 標準ライブラリの内部

**対象層:** Java 層 → JNI 層 → HotSpot C++ 層 / OS 層

「JNI は特殊な技術で、普通の Java 開発者には関係ない」と思われがちである。しかし実際には、**Java で最も基本的な操作（ファイル読み書き、ネットワーク通信、スレッド生成、時刻取得）はすべて JNI を経由している**。このセクションでは、各カテゴリについて「Java API → JNI 境界 → OS システムコール」の流れを詳しく追跡する。

### 3.1 ファイル I/O -- OS のシステムコールへの橋渡し

#### Java API から OS まで

Java の `FileInputStream.read()` がどのようにして OS の `read()` システムコールに到達するか、全経路を追跡する。

```
  Java 層                    JNI 層                 OS / ネイティブ層
  ──────────────────────────────────────────────────────────────────────

  FileInputStream.read()
        │
        ▼
  read0() [native]  ──────▶  JNI 境界越え          JDK ネイティブコード:
                              型変換                  src/java.base/unix/native/
                              セキュリティチェック       libjava/io_util_md.c
                                    │
                                    ▼
                              handleRead()
                                    │
                                    ▼
                              OS read() syscall ──▶  カーネル空間
                                                      ディスクドライバ
                                                      HDD / SSD
                                                           │
  int (読み取ったバイト)  ◀── jint → int 変換 ◀───────────┘
```

#### 関連する native メソッド

`FileInputStream` クラスには以下の native メソッドが存在する（JniExplorer で確認可能）:

| native メソッド | 対応する OS 操作 | 説明 |
|----------------|-----------------|------|
| `open0(String)` | `open()` syscall | ファイルを開く |
| `read0()` | `read()` syscall | 1バイト読み取り |
| `readBytes(byte[], int, int)` | `read()` syscall | バイト配列に複数バイト読み取り |
| `skip0(long)` | `lseek()` syscall | 指定バイト数スキップ |
| `available0()` | `ioctl()` / `fstat()` | 読み取り可能なバイト数を取得 |
| `length0()` | `fstat()` syscall | ファイルサイズ取得 |
| `close0()` | `close()` syscall | ファイルを閉じる（FileDescriptor 経由） |

#### NIO (java.nio) の場合

`java.nio` パッケージは `java.io` よりも高性能なファイル操作を提供する。NIO も最終的にはネイティブコードを呼び出すが、Java ヒープとネイティブメモリの間でデータをコピーしない **ダイレクトバッファ** を使うことで、JNI 境界越えのコストを削減している。

```
  java.io のファイル読み取り:
    Java byte[] ←── JNI でコピー ←── OS バッファ ←── ディスク
                  (コピーが発生)

  java.nio (DirectByteBuffer) のファイル読み取り:
    DirectByteBuffer (ネイティブメモリ) ◀── OS バッファ ◀── ディスク
    Java コードはネイティブメモリを直接参照（コピーなし）
```

**HotSpot C++ 層のソースパス:**
- `src/java.base/unix/native/libjava/io_util_md.c` -- Unix 向けファイル I/O 実装
- `src/java.base/windows/native/libjava/io_util_md.c` -- Windows 向けファイル I/O 実装
- `src/java.base/share/native/libjava/io_util.c` -- 共通ファイル I/O ユーティリティ

### 3.2 ネットワーク通信 -- ソケット API への接続

#### java.net.Socket の内部

Java のネットワーク通信も、最終的には OS のソケット API を JNI 経由で呼び出している。

```
  Java 層                     JNI 層               OS / ネイティブ層
  ──────────────────────────────────────────────────────────────────────

  new Socket(host, port)
        │
        ▼
  PlainSocketImpl.connect()
        │
        ▼
  socketConnect() [native] ──▶  JNI 境界越え       JDK ネイティブコード:
                                型変換               src/java.base/unix/native/
                                                       libnet/PlainSocketImpl.c
                                     │
                                     ▼
                               socket() syscall ──▶  ソケットを作成
                                     │
                                     ▼
                               connect() syscall ──▶  サーバーに接続
                                     │
                                     ▼
                               TCP 3-way handshake
                                  (SYN → SYN-ACK → ACK)
                                     │
  Socket (接続済み)  ◀──────────────┘
```

#### java.net.http.HttpClient の場合

Java 11 で導入された `HttpClient` はモダンな HTTP クライアントだが、内部のソケット通信層では同様に JNI を使用している。ただし `HttpClient` 自体は**純粋な Java**で実装されており、HTTP プロトコルの解析やヘッダー処理はすべて Java 層で行われる。

```
  HttpClient のレイヤー構成:

  ┌────────────────────────────────────────────────┐
  │  HttpClient API (Java 層 -- 純粋 Java)          │
  │    HTTP/1.1, HTTP/2 プロトコル処理               │
  │    ヘッダー解析、リダイレクト処理                  │
  ├────────────────────────────────────────────────┤
  │  java.nio.channels.SocketChannel (Java 層)      │
  │    非同期 I/O、セレクター                         │
  ├────────────────────────────────────────────────┤
  │  sun.nio.ch.Net (JNI 層)                        │
  │    socket(), connect(), read(), write()         │
  ├────────────────────────────────────────────────┤
  │  OS ソケット API (ネイティブ層)                   │
  │    TCP/IP スタック、カーネルバッファ               │
  └────────────────────────────────────────────────┘
```

**HotSpot C++ 層のソースパス:**
- `src/java.base/unix/native/libnet/PlainSocketImpl.c` -- Unix ソケット実装
- `src/java.base/unix/native/libnet/net_util_md.c` -- ネットワークユーティリティ
- `src/java.base/share/native/libnet/InetAddress.c` -- InetAddress の native 実装

### 3.3 DB 操作 (JDBC) -- Type 2 vs Type 4 ドライバ

JDBC (Java Database Connectivity) は、Java からデータベースを操作するための標準 API である。JDBC ドライバには複数のタイプがあり、**JNI を使うものと使わないものがある**。

#### JDBC ドライバの4タイプ

| タイプ | 名称 | JNI の使用 | 説明 |
|-------|------|----------|------|
| **Type 1** | JDBC-ODBC Bridge | **使う** | ODBC ドライバを JNI で呼び出す。Java 8 で廃止 |
| **Type 2** | Native-API | **使う** | DB ベンダーのネイティブクライアントを JNI で呼び出す |
| **Type 3** | Network Protocol | **使わない** | ミドルウェアサーバーと純粋 Java で通信 |
| **Type 4** | Thin Driver | **使わない** | DB プロトコルを純粋 Java で実装 |

#### Type 2 (JNI あり) vs Type 4 (JNI なし) の比較

```
  Type 2: Native-API ドライバ (JNI を使う)

  Java アプリ
     │
     ▼
  JDBC API (java.sql.*)
     │
     ▼
  Type 2 ドライバ (Java 部分)
     │
     ▼ ★ JNI 境界越え
  ネイティブクライアントライブラリ (例: Oracle OCI, libpq)
     │
     ▼
  DB サーバー


  Type 4: Thin ドライバ (JNI を使わない)

  Java アプリ
     │
     ▼
  JDBC API (java.sql.*)
     │
     ▼
  Type 4 ドライバ (全て Java で実装)
     │
     ▼ TCP/IP ソケット通信 (これ自体は JNI を使う ※)
  DB サーバー

  ※ Type 4 は「JDBC ドライバ自体は JNI を使わない」が、
     内部のソケット通信は結局 JNI で OS のソケット API を呼んでいる。
     つまり「JNI フリー」ではなく「ドライバ固有の JNI がない」のが正確。
```

#### 現代の主流: Type 4

現代の JDBC ドライバはほぼ **Type 4 (Thin Driver)** が主流である。

| ドライバ | タイプ | JNI の使用 |
|---------|-------|----------|
| PostgreSQL JDBC (pgjdbc) | Type 4 | なし（純粋 Java） |
| MySQL Connector/J | Type 4 | なし（純粋 Java） |
| H2 Database | Type 4 | なし（純粋 Java） |
| Oracle JDBC Thin | Type 4 | なし（純粋 Java） |
| Oracle JDBC OCI | Type 2 | **あり（OCI ライブラリを JNI で呼出）** |
| SQL Server JDBC | Type 4 | なし（純粋 Java） |

Type 4 が主流になった理由:
1. **デプロイの容易さ**: ネイティブライブラリのインストールが不要（JAR を追加するだけ）
2. **ポータビリティ**: 純粋 Java なので OS を問わず動作
3. **コンテナとの相性**: Docker コンテナにネイティブライブラリを含める必要がない
4. **性能の向上**: JIT コンパイラがドライバコードを最適化できる（JNI は最適化不可）

### 3.4 スレッド -- OS スレッドの生成と管理

Java のスレッドは、JVM 内部のスケジューラではなく **OS のスレッド機構** を使って実現されている。`Thread.start()` は最終的に OS の `pthread_create()` (Linux/macOS) や `CreateThread()` (Windows) を JNI 経由で呼び出す。

```
  Java 層                     JNI 層               OS / HotSpot C++ 層
  ──────────────────────────────────────────────────────────────────────

  Thread t = new Thread(() -> ...);
  t.start();
        │
        ▼
  Thread.start()
        │
        ▼
  start0() [native]  ────▶   JNI 境界越え        HotSpot C++:
                                                    src/hotspot/share/
                                                    runtime/thread.cpp
                                   │
                                   ▼
                              JavaThread::create()
                                   │
                                   ▼
                              os::create_thread()
                                   │
                                   ├──▶ Linux:   pthread_create()
                                   ├──▶ macOS:   pthread_create()
                                   └──▶ Windows: CreateThread()
                                        │
  新しい OS スレッドが              ◀────┘
  run() を実行開始
```

**Thread クラスの主要 native メソッド** (JniExplorer で確認可能):

| native メソッド | OS 操作 | 説明 |
|----------------|---------|------|
| `start0()` | `pthread_create()` | 新しい OS スレッドを生成 |
| `sleepNanos0(long)` | `nanosleep()` / `Sleep()` | スレッドをスリープ |
| `yield0()` | `sched_yield()` | CPU を他のスレッドに譲る |
| `interrupt0()` | シグナル / イベント | スレッドに割り込みを送る |
| `currentThread()` | TLS (Thread Local Storage) | 現在のスレッドオブジェクトを取得 |
| `holdsLock(Object)` | JVM 内部のモニター確認 | ロックを保持しているか確認 |

### 3.5 時刻・乱数 -- OS のタイマーとエントロピー源

#### 時刻取得

```
  Java 層                     JNI 層               OS / HotSpot C++ 層
  ──────────────────────────────────────────────────────────────────────

  System.currentTimeMillis()
        │
        ▼
  currentTimeMillis() [native] ──▶  HotSpot:
                                     os::javaTimeMillis()
                                          │
                                          ├──▶ Linux:   clock_gettime(CLOCK_REALTIME)
                                          ├──▶ macOS:   gettimeofday()
                                          └──▶ Windows: GetSystemTimeAsFileTime()

  System.nanoTime()
        │
        ▼
  nanoTime() [native]  ──────────▶  HotSpot:
                                     os::javaTimeNanos()
                                          │
                                          ├──▶ Linux:   clock_gettime(CLOCK_MONOTONIC)
                                          ├──▶ macOS:   mach_absolute_time()
                                          └──▶ Windows: QueryPerformanceCounter()
```

`currentTimeMillis()` と `nanoTime()` の違い:

| メソッド | 基準点 | 精度 | 用途 |
|---------|-------|------|------|
| `currentTimeMillis()` | 1970年1月1日 UTC | ミリ秒 | タイムスタンプ、日時表現 |
| `nanoTime()` | 任意（JVM 起動時等） | ナノ秒 | 経過時間計測、ベンチマーク |

#### 乱数生成

`SecureRandom` は暗号学的に安全な乱数を生成するが、そのエントロピー源は OS に依存する。

```
  SecureRandom.nextBytes()
        │
        ▼
  NativePRNG (Java 層)
        │
        ▼
  FileInputStream("/dev/urandom") [native]  ──▶  Linux カーネルのエントロピープール
```

### 3.6 標準ライブラリの native メソッド統計

JniExplorer プログラムで実際に計測した結果（Java 25 / Azul Zulu 25）:

| カテゴリ | クラス | native / total | 割合 |
|---------|--------|-------------|------|
| コア | Object | 6 / 12 | 50.0% |
| コア | Class | 28 / 167 | 16.8% |
| コア | System | 9 / 40 | 22.5% |
| コア | Thread | 20 / 103 | 19.4% |
| I/O | FileInputStream | 9 / 26 | 34.6% |
| I/O | FileOutputStream | 4 / 13 | 30.8% |
| I/O | FileDescriptor | 5 / 14 | 35.7% |
| ネットワーク | NetworkInterface | 12 / 40 | 30.0% |
| リフレクション | Array | 21 / 23 | 91.3% |

特筆すべきは **`Object` クラスの50%、`Array` クラスの91%が native** という事実である。Java の最も基本的なクラスが、実は半分以上ネイティブコードで実装されていることが分かる。

---

## 4. native メソッドの宣言と実装 -- 開発者が JNI を書く場合

**対象層:** Java 層 → JNI 層 → ネイティブ (C/C++) 層

### 4.1 開発の流れ -- 5ステップ

自分で JNI コードを書く場合の開発フローを示す。なお、新規開発では後述の FFM API (セクション6) を推奨する。

```
  ┌──────────────────────────────────────────────────────────────────┐
  │  JNI 開発の5ステップ                                             │
  │                                                                  │
  │  Step 1: Java 側で native メソッドを宣言                         │
  │  ─────────────────────────────────                               │
  │  public class NativeDemo {                                       │
  │      public native int add(int a, int b);  // 実装は C/C++ 側    │
  │      static { System.loadLibrary("nativedemo"); }                │
  │  }                                                               │
  │                                                                  │
  │  Step 2: javac -h でヘッダーファイルを生成                        │
  │  ─────────────────────────────────────                           │
  │  $ javac -h . NativeDemo.java                                    │
  │  → NativeDemo.h が生成される                                      │
  │                                                                  │
  │  Step 3: C/C++ で native メソッドを実装                           │
  │  ─────────────────────────────────                               │
  │  #include "NativeDemo.h"                                         │
  │  JNIEXPORT jint JNICALL                                          │
  │  Java_NativeDemo_add(JNIEnv *env, jobject obj,                   │
  │                      jint a, jint b) {                           │
  │      return a + b;                                               │
  │  }                                                               │
  │                                                                  │
  │  Step 4: 共有ライブラリにコンパイル                                │
  │  ─────────────────────────────                                   │
  │  $ gcc -shared -o libnativedemo.so NativeDemo.c \                │
  │        -I${JAVA_HOME}/include \                                  │
  │        -I${JAVA_HOME}/include/linux                              │
  │                                                                  │
  │  Step 5: Java プログラムを実行                                    │
  │  ──────────────────────────                                      │
  │  $ java -Djava.library.path=. NativeDemo                         │
  └──────────────────────────────────────────────────────────────────┘
```

かつては `javah` コマンドでヘッダーを生成していたが、Java 10 で `javah` は廃止され、`javac -h` に統合された。

### 4.2 型マッピング -- Java 型と C 型の対応

JNI は Java の型とネイティブの型の間に明確なマッピングを定義している。

| Java 型 | JNI 型 | C/C++ 型 | サイズ |
|---------|--------|---------|--------|
| `boolean` | `jboolean` | `unsigned char` | 8 bit |
| `byte` | `jbyte` | `signed char` | 8 bit |
| `char` | `jchar` | `unsigned short` | 16 bit |
| `short` | `jshort` | `short` | 16 bit |
| `int` | `jint` | `int` | 32 bit |
| `long` | `jlong` | `long long` | 64 bit |
| `float` | `jfloat` | `float` | 32 bit |
| `double` | `jdouble` | `double` | 64 bit |
| `void` | `void` | `void` | - |
| `Object` | `jobject` | (ポインタ) | ポインタサイズ |
| `String` | `jstring` | (ポインタ) | ポインタサイズ |
| `int[]` | `jintArray` | (ポインタ) | ポインタサイズ |
| `Class` | `jclass` | (ポインタ) | ポインタサイズ |

### 4.3 JNI の命名規則

JNI のネイティブ関数名は厳密な命名規則に従う。これにより、JVM は `native` メソッドに対応するネイティブ関数を自動的に発見できる。

```
  命名規則: Java_<パッケージ名>_<クラス名>_<メソッド名>

  例:
  Java 側:  com.example.MyClass.nativeMethod(int, String)
  C 側:     Java_com_example_MyClass_nativeMethod(JNIEnv*, jobject, jint, jstring)

  ルール:
  ├── "Java_" プレフィックス（固定）
  ├── パッケージの "." を "_" に置換
  ├── クラス名をそのまま
  └── メソッド名をそのまま
      （オーバーロードがある場合は引数のシグネチャも追加）
```

この自動解決の仕組みを **動的リンケージ（dynamic linkage）** と呼ぶ。代替として `RegisterNatives()` による **静的リンケージ** もある。JDK 内部のネイティブメソッドの多くは `RegisterNatives()` を使って明示的に登録している。

---

## 5. JNI のコストとオーバーヘッド

**対象層:** JNI 層 / HotSpot C++ 層

### 5.1 境界越えのコスト -- 何が起きているのか

Java から native メソッドを呼び出すとき、単純にネイティブ関数を呼ぶだけではなく、複数のステップが発生する。

```
  ┌──────────────────────────────────────────────────────────────────────┐
  │  JNI 境界越えで発生する処理                                          │
  │                                                                      │
  │  1. スレッドの状態遷移                                                │
  │     Java スレッド状態 (_thread_in_Java) → ネイティブ状態              │
  │     (_thread_in_native) へ遷移                                       │
  │                                                                      │
  │  2. ハンドルの作成                                                    │
  │     Java オブジェクト参照をハンドル（間接参照）に変換                  │
  │     → GC がオブジェクトを移動してもハンドル経由で追跡可能にする       │
  │                                                                      │
  │  3. 引数の変換                                                        │
  │     Java のオブジェクト参照 → JNI ハンドル                            │
  │     Java の配列 → ピン留め or コピー                                  │
  │                                                                      │
  │  4. セキュリティチェック（必要に応じて）                              │
  │                                                                      │
  │  5. ネイティブ関数の呼び出し                                          │
  │                                                                      │
  │  6. 戻り値の変換                                                      │
  │     ネイティブの値 → Java の型                                        │
  │                                                                      │
  │  7. 保留中の例外のチェック                                            │
  │                                                                      │
  │  8. ローカル参照フレームの解放                                        │
  │                                                                      │
  │  9. スレッドの状態を Java に復帰                                      │
  │     → セーフポイントチェック（GC 要求があればここで停止）             │
  └──────────────────────────────────────────────────────────────────────┘
```

これらすべてのステップを合わせると、通常の Java メソッド呼び出し（数ナノ秒）に比べて **数十〜数百ナノ秒** のオーバーヘッドが発生する。

### 5.2 JIT 最適化の限界 -- インライン化不可の壁

JIT コンパイラの最も強力な最適化手法は **インライン化（Inlining）** である。インライン化とは、メソッド呼び出しを呼び出し先のコードで置き換えることで、呼び出しオーバーヘッドを除去する手法である（詳細は [execution-engine-deep-dive.md](../runtime/execution-engine-deep-dive.md) セクション5.1 参照）。

しかし、**native メソッドは JIT コンパイラによるインライン化ができない**。ネイティブコードは JVM が管理する中間表現（バイトコード / IR）ではないため、JIT コンパイラが解析・変換できないのである。

```
  通常の Java メソッド呼び出し（インライン化可能）:

  // JIT コンパイル前
  int result = helper(x);

  // JIT コンパイル後（インライン化される）
  int result = x * 2 + 1;  // helper() のコードが展開される
  → メソッド呼び出しのオーバーヘッドが消える


  native メソッド呼び出し（インライン化不可）:

  // JIT コンパイル後も呼び出しが残る
  int result = nativeHelper(x);  // ← JNI 境界越えが毎回発生
  → 最適化の限界
```

ただし、HotSpot は一部の **intrinsic（組み込み）** native メソッドを特別扱いしている。例えば `System.arraycopy()` や `Math.sin()` は、JIT コンパイラが直接マシンコードを生成する。これは JNI を経由しないため、通常の native メソッド呼び出しよりもはるかに高速である。

| メソッド | 通常の JNI? | 備考 |
|---------|-----------|------|
| `System.arraycopy()` | **Intrinsic** | JIT が CPU の `memcpy` 命令を直接生成 |
| `Math.sin()` / `Math.cos()` | **Intrinsic** | JIT が CPU の浮動小数点命令を生成 |
| `Object.hashCode()` | **Intrinsic** | JVM がオブジェクトヘッダーから直接取得 |
| `Thread.currentThread()` | **Intrinsic** | TLS から直接読み取り |
| `FileInputStream.read0()` | 通常の JNI | OS の `read()` を呼ぶため最適化不可 |

### 5.3 GC セーフポイントとの関係

JNI のネイティブコード実行中、GC はそのスレッドを**直接停止できない**。ネイティブコードは JVM の管理外であり、任意のタイミングで停止するとネイティブのリソース（メモリ、ファイルハンドル等）がリークする可能性があるためである。

```
  GC セーフポイントと JNI の関係:

  時間 →
  ┌──────────┬───────────────────────┬───────────┐
  │ Java 実行 │  native 実行中        │ Java に復帰│
  │          │  (GC は停止できない)    │           │
  │  ★ GC が │                       │ ★ ここで  │
  │  停止要求 │  → GC は待機中...     │  停止する  │
  └──────────┴───────────────────────┴───────────┘

  ★ セーフポイント = GC がスレッドを安全に停止できるポイント

  native メソッドから Java に戻る瞬間がセーフポイントになる。
  長時間実行する native メソッドがあると、GC の STW (Stop-The-World)
  が遅延する可能性がある。
```

**HotSpot C++ 層のソースパス:**
- `src/hotspot/share/runtime/safepoint.cpp` -- セーフポイント管理

### 5.4 コスト削減のベストプラクティス

| 戦略 | 説明 | 効果 |
|------|------|------|
| バッチ呼び出し | 細かいネイティブ呼び出しを1回にまとめる | 境界越え回数を削減 |
| ダイレクトバッファ | `ByteBuffer.allocateDirect()` を使う | データコピーを回避 |
| Critical Native | `@CriticalNative` アノテーション（内部用） | JNI オーバーヘッドを最小化 |
| Intrinsic 活用 | HotSpot の intrinsic メソッドを優先利用 | JNI 完全回避 |
| FFM API 移行 | JNI の代わりに FFM API を使う | より効率的な境界越え |

---

## 6. JNI の代替技術 -- Foreign Function & Memory API (FFM API)

**対象層:** Java 層

### 6.1 FFM API とは -- Panama プロジェクトの成果

**FFM API (Foreign Function & Memory API)** は、OpenJDK の **Panama プロジェクト** が開発した、JNI の後継となる技術である。**JEP 454** として **Java 22 で正式機能** となった。

FFM API は以下の2つの機能を提供する:

1. **Foreign Function API**: Java からネイティブ関数（C ライブラリ等）を直接呼び出す
2. **Foreign Memory API**: Java からネイティブメモリ（ヒープ外メモリ）を安全に操作する

```
  ┌──────────────────────────────────────────────────────────────────┐
  │  JNI と FFM API の位置づけ                                       │
  │                                                                  │
  │  Java 1.1 (1997)                  Java 22 (2024)                 │
  │     JNI 登場                         FFM API 正式化              │
  │       │                                │                         │
  │       │    27年間の歴史                 │                         │
  │       │    ├── 実績と安定性             │                         │
  │       │    ├── 大量の既存コード          │                         │
  │       │    └── しかし課題も多い          │                         │
  │       │        ├── ボイラープレートが多い │                         │
  │       │        ├── メモリ安全性の問題     │                         │
  │       │        └── C/C++ コードが必要    │                         │
  │       │                                │                         │
  │       └────── 徐々に移行 ────────────▶ │                         │
  │                                        ├── 純粋 Java で記述可能   │
  │                                        ├── メモリ安全             │
  │                                        └── C/C++ コード不要       │
  └──────────────────────────────────────────────────────────────────┘
```

### 6.2 JNI vs FFM API の比較

| 観点 | JNI | FFM API |
|------|-----|---------|
| **導入時期** | JDK 1.1 (1997) | JDK 22 (2024) で正式 |
| **C/C++ コードの記述** | 必要（ヘッダー生成 → C 実装 → コンパイル） | **不要（Java のみで完結）** |
| **型安全性** | 低い（C のポインタ操作はミスしやすい） | **高い（MethodHandle ベース）** |
| **メモリ安全性** | 低い（バッファオーバーフローの危険） | **高い（境界チェック付き）** |
| **パフォーマンス** | オーバーヘッドあり（境界越え） | **JNI と同等以上** |
| **ボイラープレート** | 多い（ヘッダー、型変換、参照管理） | **少ない** |
| **GC との協調** | 手動（グローバル参照の管理） | **自動（Arena ベースの管理）** |
| **エラーハンドリング** | C の仕組みに依存 | **Java の例外機構を使用** |
| **ビルドの複雑さ** | 高い（ネイティブツールチェーンが必要） | **低い（Java のビルドだけ）** |
| **デバッグ** | 困難（C/C++ デバッガが必要） | **容易（Java のデバッガで完結）** |

### 6.3 FFM API のコード例

同じ目的（C の `strlen` 関数を呼ぶ）を JNI と FFM API で比較する。

```
  ┌──────────────────────────────────────────────────────────────────┐
  │  JNI の場合（3ファイル + ネイティブビルドが必要）                  │
  │                                                                  │
  │  // ① Java: native メソッド宣言                                  │
  │  public class StrlenDemo {                                       │
  │      public static native long strlen(String s);                 │
  │      static { System.loadLibrary("strlendemo"); }                │
  │  }                                                               │
  │                                                                  │
  │  // ② C: ヘッダーに基づいて実装                                   │
  │  JNIEXPORT jlong JNICALL                                         │
  │  Java_StrlenDemo_strlen(JNIEnv *env, jclass cls, jstring s) {    │
  │      const char *str = (*env)->GetStringUTFChars(env, s, NULL);  │
  │      jlong len = (jlong)strlen(str);                             │
  │      (*env)->ReleaseStringUTFChars(env, s, str);                 │
  │      return len;                                                 │
  │  }                                                               │
  │                                                                  │
  │  // ③ gcc でコンパイルして .so / .dylib を生成                    │
  │                                                                  │
  ├──────────────────────────────────────────────────────────────────┤
  │  FFM API の場合（Java のみで完結）                                │
  │                                                                  │
  │  // Java だけで C の strlen を呼べる                               │
  │  var linker = Linker.nativeLinker();                              │
  │  var strlen = linker.downcallHandle(                              │
  │      linker.defaultLookup().find("strlen").orElseThrow(),        │
  │      FunctionDescriptor.of(JAVA_LONG, ADDRESS)                   │
  │  );                                                              │
  │  try (var arena = Arena.ofConfined()) {                          │
  │      var cString = arena.allocateFrom("Hello");                  │
  │      long len = (long) strlen.invoke(cString);                   │
  │      // len == 5                                                 │
  │  } // arena がスコープを出ると自動的にメモリ解放                   │
  └──────────────────────────────────────────────────────────────────┘
```

### 6.4 移行の指針

| 状況 | 推奨 |
|------|------|
| 新規開発でネイティブ関数を呼びたい | **FFM API** を使う |
| 既存の JNI コードがある | 安定しているならそのまま維持、問題があれば FFM API に移行 |
| JDK 21 以前をサポートする必要がある | JNI を使う（FFM API は Java 22 から正式） |
| C/C++ のラッパーライブラリを開発 | **jextract** (Panama) でバインディングを自動生成 |
| パフォーマンスが最重要 | FFM API（JIT が最適化しやすく、JNI と同等以上の性能） |

---

## 7. HotSpot C++ 層での JNI 実装

**対象層:** HotSpot C++ 層

### 7.1 jni.cpp -- JNI 関数テーブルの実装

HotSpot VM における JNI の中核は `src/hotspot/share/prims/jni.cpp` である。このファイルには JNI 関数テーブルの全エントリが実装されている。

```
  ┌──────────────────────────────────────────────────────────────────┐
  │  src/hotspot/share/prims/jni.cpp の構造                          │
  │                                                                  │
  │  struct JNINativeInterface_ = {                                  │
  │      NULL,                        // reserved0                   │
  │      NULL,                        // reserved1                   │
  │      NULL,                        // reserved2                   │
  │      NULL,                        // reserved3                   │
  │      jni_GetVersion,              // GetVersion                  │
  │      jni_DefineClass,             // DefineClass                 │
  │      jni_FindClass,               // FindClass                   │
  │      ...                                                         │
  │      jni_NewObject,               // NewObject                   │
  │      jni_GetMethodID,             // GetMethodID                 │
  │      jni_CallObjectMethod,        // CallObjectMethod            │
  │      ...                          // 約 230 エントリ             │
  │  };                                                              │
  │                                                                  │
  │  JNIEnv はこの関数テーブルへのポインタを保持する。                │
  │  C++ から env->FindClass("java/lang/String") を呼ぶと、          │
  │  実際には jni_FindClass() が呼ばれる。                            │
  └──────────────────────────────────────────────────────────────────┘
```

### 7.2 native メソッドの解決プロセス

Java コードが `native` メソッドを呼び出したとき、JVM はどのようにして対応するネイティブ関数を見つけるのか。

```
  ┌──────────────────────────────────────────────────────────────────┐
  │  native メソッドの解決プロセス                                     │
  │                                                                  │
  │  1. Java コードが native メソッドを呼び出す                       │
  │     │                                                            │
  │     ▼                                                            │
  │  2. JVM が Method 構造体を確認                                    │
  │     → native フラグが立っていることを検出                         │
  │     │                                                            │
  │     ▼                                                            │
  │  3. ネイティブ関数ポインタが未解決の場合                          │
  │     │                                                            │
  │     ├──▶ 方法A: 動的リンケージ                                    │
  │     │     JNI 命名規則に従った関数名で                            │
  │     │     ロード済みの共有ライブラリを検索                        │
  │     │     (dlsym / GetProcAddress)                                │
  │     │                                                            │
  │     └──▶ 方法B: RegisterNatives                                   │
  │           JNI_OnLoad 等で事前に登録済みの                         │
  │           関数ポインタテーブルから解決                             │
  │     │                                                            │
  │     ▼                                                            │
  │  4. 解決した関数ポインタを Method 構造体にキャッシュ              │
  │     → 2回目以降は即座に呼び出し                                   │
  │     │                                                            │
  │     ▼                                                            │
  │  5. ネイティブ関数を呼び出す                                      │
  │     (JNI スタブコードが引数を変換して呼び出し)                    │
  └──────────────────────────────────────────────────────────────────┘
```

解決に失敗した場合は `UnsatisfiedLinkError` がスローされる。これは JNI 関連で最もよく見るエラーの一つである。

### 7.3 JNI ハンドルとオブジェクト参照

JNI 境界を越える際、Java オブジェクトの参照は**ハンドル（handle）**に変換される。これは GC によるオブジェクト移動に対応するための仕組みである。

```
  ハンドルの仕組み:

  ネイティブコード        JNI ハンドル           Java ヒープ
  ─────────────────────────────────────────────────────────

  jobject handle ──────▶ ┌──────────┐       ┌──────────────┐
                         │  handle  │──────▶│ Java Object  │
                         │  entry   │       │ (実体)        │
                         └──────────┘       └──────────────┘

  GC がオブジェクトを移動した場合:

  jobject handle ──────▶ ┌──────────┐       ┌──────────────┐
  (変わらない)            │  handle  │──┐    │ (旧: 空き)    │
                         │  entry   │  │    └──────────────┘
                         └──────────┘  │
                                       │    ┌──────────────┐
                                       └───▶│ Java Object  │
                                            │ (移動後)      │
                                            └──────────────┘

  → ハンドルのエントリが更新されるため、
    ネイティブコードは handle を変更せずに正しいオブジェクトにアクセスできる。
```

**HotSpot C++ 層のソースパス:**
- `src/hotspot/share/prims/jni.cpp` -- JNI 関数テーブルの実装
- `src/hotspot/share/runtime/jniHandles.cpp` -- JNI ハンドル管理
- `src/hotspot/share/prims/nativeLookup.cpp` -- native メソッドの解決

---

## 8. Spring Boot と JNI -- 間接的な JNI 利用の実態

**対象層:** Java 層（アプリケーションレベル）

### 8.1 Spring Boot アプリケーションの JNI 依存マップ

Spring Boot アプリケーションは「Java で書かれたフレームワーク」だが、その内部では大量の JNI 呼び出しが行われている。開発者が JNI を意識することはないが、**すべてのリクエスト処理は最終的に JNI を経由して OS に到達する**。

```
  ┌──────────────────────────────────────────────────────────────────┐
  │  Spring Boot アプリケーションの JNI 依存マップ                    │
  │                                                                  │
  │  ┌──────────────────────────────┐                                │
  │  │  Spring Boot アプリケーション  │                                │
  │  │  @RestController              │                                │
  │  │  @Service                     │                                │
  │  │  @Repository                  │                                │
  │  └──────────┬───────────────────┘                                │
  │             │                                                    │
  │  ┌──────────▼───────────────────┐                                │
  │  │  Spring Framework             │                                │
  │  │  DI, AOP, Transaction        │   ← ここまでは純粋 Java         │
  │  └──────────┬───────────────────┘                                │
  │             │                                                    │
  │  ┌──────────▼────────────────────────────────────────────┐       │
  │  │  以下の全てが最終的に JNI を経由                        │       │
  │  │                                                        │       │
  │  │  ① HTTP サーバー (Tomcat)                               │       │
  │  │     └─ ServerSocket.accept() → JNI → OS accept()      │       │
  │  │                                                        │       │
  │  │  ② DB 接続 (HikariCP + JDBC)                           │       │
  │  │     └─ Socket.connect() → JNI → OS connect()          │       │
  │  │     (Type 4 ドライバの場合、ソケット通信で JNI)         │       │
  │  │                                                        │       │
  │  │  ③ ログ出力 (Logback / Log4j2)                         │       │
  │  │     └─ FileOutputStream.write() → JNI → OS write()    │       │
  │  │                                                        │       │
  │  │  ④ ファイル操作 (Multipart upload 等)                   │       │
  │  │     └─ FileInputStream.read() → JNI → OS read()       │       │
  │  │                                                        │       │
  │  │  ⑤ スレッドプール (ThreadPoolTaskExecutor)              │       │
  │  │     └─ Thread.start() → JNI → OS pthread_create()     │       │
  │  │                                                        │       │
  │  │  ⑥ タイマー・スケジューラ                               │       │
  │  │     └─ System.nanoTime() → JNI → OS clock_gettime()   │       │
  │  └────────────────────────────────────────────────────────┘       │
  └──────────────────────────────────────────────────────────────────┘
```

### 8.2 DB 接続 -- HikariCP → JDBC → JNI の流れ

Spring Boot でデフォルトの DB コネクションプール **HikariCP** を使ったデータベース接続の全経路:

```
  Spring Boot での DB アクセスの全経路:

  @Repository
  UserRepository.findById(id)
       │
       ▼
  Spring Data JPA → Hibernate (EntityManager)
       │
       ▼
  HikariCP (コネクションプール)
  → 接続済みコネクションをプールから取り出す
  → なければ新規接続を作成
       │
       ▼
  JDBC (java.sql.Connection / PreparedStatement)
       │
       ▼
  PostgreSQL JDBC ドライバ (Type 4 -- 純粋 Java)
  → SQL を PostgreSQL ワイヤプロトコルにエンコード
       │
       ▼
  java.net.Socket.write()  → OutputStream → native write
       │
       ▼ ★ JNI 境界
  OS の send() / write() syscall
       │
       ▼
  TCP/IP スタック → ネットワーク → PostgreSQL サーバー
```

### 8.3 ログ出力 -- Logback → FileOutputStream → JNI

Spring Boot のデフォルトログフレームワーク **Logback** でファイルにログを書き出す際の経路:

```
  logger.info("User logged in: {}", userId)
       │
       ▼
  Logback (SLF4J 実装)
  → ログレベルフィルタリング
  → メッセージフォーマット
  → Appender (ConsoleAppender / FileAppender)
       │
       ▼
  java.io.OutputStreamWriter → FileOutputStream
       │
       ▼
  write() / writeBytes() [native]
       │
       ▼ ★ JNI 境界
  OS の write() syscall
       │
       ▼
  カーネルのバッファ → ディスク (or コンソール)
```

### 8.4 HTTP サーバー -- Tomcat のソケット処理

Spring Boot 内蔵の **Tomcat** が HTTP リクエストを受け付ける際:

```
  ブラウザ → HTTP リクエスト → TCP コネクション
       │
       ▼
  Tomcat Acceptor スレッド
  ServerSocket.accept()  [内部で native]
       │
       ▼ ★ JNI 境界
  OS の accept() syscall
  → 新しいソケットが返される
       │
       ▼
  Tomcat NIO Connector
  SocketChannel.read()  [内部で native]
       │
       ▼ ★ JNI 境界
  OS の read() / recv() syscall
  → HTTP リクエストデータを読み取り
       │
       ▼
  Tomcat HTTP パーサー (純粋 Java)
  → リクエストライン、ヘッダー解析
       │
       ▼
  DispatcherServlet → @Controller → ビジネスロジック
```

---

## 9. まとめ -- Q&A 形式の早見表

| 質問 | 回答 |
|------|------|
| JNI とは？ | Java コードとネイティブコード (C/C++) の間で相互に呼び出しを行うための標準インタフェース |
| なぜ JNI が必要？ | Java バイトコードだけでは OS の機能（ファイル I/O、ネットワーク、スレッド等）にアクセスできないため |
| `native` キーワードの意味は？ | 「このメソッドの実装は Java ではなく、ネイティブコード (C/C++) にある」という宣言 |
| 普通の Java 開発者も JNI を使っている？ | **はい。** `IO.println("Hello")` でさえ、最終的に JNI 経由で OS の `write()` を呼んでいる |
| JNI は遅い？ | Java → ネイティブの境界越えにはオーバーヘッドがある。ただし Intrinsic メソッドは例外的に高速 |
| JNI メソッドは JIT で最適化される？ | **されない。** JNI メソッドはインライン化不可。ただし Intrinsic は JIT が直接マシンコードを生成 |
| JDBC は JNI を使う？ | Type 2 ドライバは JNI を使う。Type 4 ドライバ（現代の主流）はドライバ自体は純粋 Java（ソケット通信は JNI） |
| Spring Boot は JNI を使う？ | 直接は使わないが、HTTP 受信、DB 接続、ログ出力、スレッド生成で間接的に大量の JNI を使っている |
| JNI の後継技術は？ | FFM API (Foreign Function & Memory API, JEP 454, Java 22 で正式)。C/C++ コード不要、型安全、メモリ安全 |
| FFM API に移行すべき？ | 新規開発では FFM API を推奨。既存の安定した JNI コードは無理に移行する必要はない |
| `UnsatisfiedLinkError` の原因は？ | ネイティブライブラリが見つからない、または native メソッドに対応するネイティブ関数が見つからない |
| `JNIEnv` とは？ | ネイティブコードから Java の世界（クラス、オブジェクト、配列等）にアクセスするための関数テーブルへのポインタ |
| ローカル参照 vs グローバル参照？ | ローカル = native メソッドの呼び出し期間中のみ有効。グローバル = 明示的に削除するまで永続 |
| `System.loadLibrary()` は何をする？ | ネイティブ共有ライブラリ (.so / .dylib / .dll) をプロセスにロードし、native メソッドから呼べるようにする |

---

## 10. 実験用 Java コード

本ドキュメントに関連する実験プログラム:

| ファイル | 概要 |
|---------|------|
| [JniExplorer.java](../../../src/main/java/native/JniExplorer.java) | 標準ライブラリ内の native メソッドをリフレクションで発見・可視化する |

### 10.1 JniExplorer -- native メソッドの発見と統計

`JniExplorer.java` は以下の4つのセクションで構成される:

1. **カテゴリ別 native メソッド統計** -- Object, System, FileInputStream 等の主要クラスに含まれる native メソッドの数と割合をバーグラフで可視化
2. **System クラスの native メソッド詳細** -- `currentTimeMillis()`, `nanoTime()` 等の実際の呼び出しと結果
3. **ファイル I/O の native メソッド** -- FileInputStream / FileOutputStream の native メソッド一覧と処理フロー図
4. **JNI 呼び出しの全体フロー図** -- 通常の Java 呼び出しと native 呼び出しの比較、Thread / Object の native メソッド一覧

```bash
# 実行方法
java src/main/java/native/JniExplorer.java
```

---

## 参考リンク・ソースパス

### 仕様・ドキュメント

| リソース | URL / パス |
|---------|-----------|
| JNI Specification (Oracle) | https://docs.oracle.com/en/java/javase/25/docs/specs/jni/ |
| JEP 454: Foreign Function & Memory API | https://openjdk.org/jeps/454 |
| Panama プロジェクト | https://openjdk.org/projects/panama/ |
| JNI Tips (Android Developers) | https://developer.android.com/training/articles/perf-jni |

### HotSpot ソースパス (OpenJDK mainline)

| ソースファイル | 内容 |
|--------------|------|
| `src/hotspot/share/prims/jni.cpp` | JNI 関数テーブルの実装（約230関数） |
| `src/hotspot/share/prims/nativeLookup.cpp` | native メソッドの解決（動的リンケージ） |
| `src/hotspot/share/runtime/jniHandles.cpp` | JNI ハンドル（ローカル参照/グローバル参照）管理 |
| `src/hotspot/share/runtime/thread.cpp` | スレッド生成 (`Thread.start0()` の実装先) |
| `src/hotspot/share/runtime/safepoint.cpp` | GC セーフポイント管理 |
| `src/hotspot/share/runtime/os.cpp` | OS 抽象化層（`currentTimeMillis()` 等の実装先） |
| `src/hotspot/os/linux/os_linux.cpp` | Linux 固有の OS 操作実装 |
| `src/hotspot/os/bsd/os_bsd.cpp` | macOS (BSD) 固有の OS 操作実装 |

### JDK ネイティブライブラリソースパス

| ソースファイル | 内容 |
|--------------|------|
| `src/java.base/share/native/libjava/io_util.c` | ファイル I/O 共通ユーティリティ |
| `src/java.base/unix/native/libjava/io_util_md.c` | Unix 向けファイル I/O 実装 |
| `src/java.base/unix/native/libjava/FileInputStream.c` | FileInputStream の native 実装 |
| `src/java.base/unix/native/libnet/PlainSocketImpl.c` | Unix ソケット実装 |
| `src/java.base/share/native/libnet/InetAddress.c` | InetAddress の native 実装 |
| `src/java.base/unix/native/libjava/Thread.c` | Thread の native 実装 |
| `src/java.base/share/native/libjava/System.c` | System の native 実装 |

### 関連ドキュメント（本プロジェクト内）

| ドキュメント | 関連セクション |
|------------|--------------|
| [jvm-overview.md](../jvm-overview.md) | セクション6: JNI の概要 |
| [execution-engine-deep-dive.md](../runtime/execution-engine-deep-dive.md) | セクション5.1: インライン化（JNI はインライン化不可） |
| [memory-basics.md](../memory/memory-basics.md) | セクション5.2: ネイティブメソッドスタック |
| [classloader-deep-dive.md](../runtime/classloader-deep-dive.md) | セクション3.3: SPI と JNI ライブラリのロード |
