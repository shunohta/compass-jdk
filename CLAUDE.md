# CLAUDE.md (jdk-core)

## 1. プロジェクト概要
* **名称:** jdk-core
* **目的:** OpenJDK (HotSpot/Core-libs) の内部解析および次世代仕様への寄稿。
* **ターゲット:** `jdk` repository (mainline / Java 25+).
* **参照ランタイム:** Azul Zulu 25 (OpenJDK 25) の挙動を正装（Standard）とする。

---

## 2. ファイル構成とプロジェクト構造

| ディレクトリ | 内容 |
|---|---|
| `src/main/java/` | 学習・実験用 Java ファイル。カテゴリ別サブディレクトリに整理。各ファイルは**独立した単一ファイルソースプログラム**として `java ファイル名.java` で実行する。 |
| `src/main/java/memory/` | メモリ関連（ヒープ、スタック、Metaspace、CodeCache） |
| `src/main/java/gc/` | GC 関連（ライフサイクル、GC ルート、OOM、ログ解析） |
| `src/main/java/classloader/` | クラスローダー関連（ローダー階層、ライフサイクル、アイデンティティ） |
| `src/main/java/engine/` | 実行エンジン関連（バイトコード、JIT） |
| `src/main/java/native/` | ネイティブ連携関連（JNI） |
| `docs/research/` | 日本語 Markdown 形式の調査ドキュメント。ファイル名は英語のケバブケース (例: `memory-basics.md`)。 |

* 各 Java ファイルは独立して完結する。共有ライブラリやユーティリティクラスへの依存は持たない。
* ヘルパーメソッド（`formatBytes`, `sleep` 等）はファイルごとに定義する。
* 各 Java ファイルは必ず対応する `docs/research/` のドキュメントからリンクされる。

### 実行方法
```bash
java src/main/java/カテゴリ/ファイル名.java

# JVM オプション付きの例
java -Xmx64m -verbose:gc src/main/java/memory/HeapGcObserver.java
```
* `--enable-preview` は不要（`void main()`, `IO.println` は Java 25 で標準機能）。
* `java.base` モジュールの暗黙的 import が有効になる。

---

## 3. 言語運用ルール

| 対象 | 言語 |
|---|---|
| AI との対話・解説・思考プロセス | 日本語 |
| Javadoc（クラスレベル・メソッドレベルとも） | 日本語 |
| コンソール出力（`IO.println` 等） | 日本語 |
| ソースコード内のインラインコメント | 日本語 |
| ドキュメント (`docs/research/`) | 日本語 Markdown |
| コミットメッセージ・ブランチ名・識別子 | 英語 |

### Javadoc 解析（英語原文の読み解き）
* 英語の Javadoc や JEP 仕様書は、そのまま翻訳するのではなく**「技術的な意図」を汲み取った上で、明確な日本語で要約・解説**すること。
* 専門的な英語の技術用語を使用する場合は、その背景知識や概念を日本語で補足し、理解を助けること。

---

## 4. コーディングスタイル

### 4.1 全般
* **Modern Java Standard:** JEP 477/495 等に基づき、最新 API を優先使用。
* **簡潔性:** 冗長な修飾（`this.` 等）やボイラープレートは一切排除し、極めてスキャナブルなコードを維持する。
* **学習目的の可読性:** 本プロジェクトのコードは教育・実験目的であるため、「動けばいい」ではなく「読んで学べる」コードを書く。解説コメントを積極的に添え、初学者が追いかけられるようにする。

### 4.2 暗黙的クラス (JEP 477)
* **エントリポイント:** トップレベルの `void main()` で記述する。`class` 宣言は書かない。
* **アクセス修飾子:** 暗黙クラスのメソッド・フィールドにはアクセス修飾子を付けない（`public`, `private` 不要）。
* **トップレベル宣言:** フィールド、レコード、メソッドはすべてトップレベルに記述する。
* **`static` フィールド:** 複数スレッドから参照されるカウンター等には `static` + `Atomic*` 型を使用する。

### 4.3 暗黙的 import (JEP 477/495)
* 単一ファイルソースプログラムでは `java.base` モジュール全体が自動インポートされる。
* `import java.util.*;` `import java.io.*;` 等は**不要**。
* `java.management`, `com.sun.management`, `javax.management` 等の `java.base` 以外のモジュールは**引き続き明示的な import が必要**。

### 4.4 var の使用 (LVTI Style Guidelines 準拠)
* `var` は右辺から型が明確に推論できる場合のみ使用する。
* OK: `var list = new ArrayList<String>();` / `var runtime = Runtime.getRuntime();`
* OK: `var usage = pool.getUsage();` -- 文脈から MemoryUsage であることが明白な場合
* NG: `for (var x : collection)` -- 要素型が不明な場合は明示的な型を記述する。
* NG: `var result = someMethod();` -- メソッドの戻り値型が文脈から不明な場合は明示的な型を記述する。

### 4.5 for 文 vs Stream の使い分け
* **Stream を使うべき場面:** map/filter/reduce 等の純粋関数的変換、collect での集約、副作用のない処理。
* **for 文を使うべき場面:** `IO.println` による出力を含むループ、外部変数の変更、複雑な制御フロー (break, continue, 例外処理)。

### 4.6 パターンマッチングの積極使用
* `switch` 式 + ガード付きパターン (`case String s when s.contains(...)`) を積極活用する。
* `instanceof` パターンマッチング (`if (obj instanceof Type t)`) を使用する。
* レコードパターン (`case Record(var a, var b)`) を使用する。
* ローカルレコード + `switch` 式の組み合わせで、複数条件の判定をスキャナブルに記述する。
* **プリミティブ・パターン** (`case int p when p < 30`) は Java 25 でまだプレビューのため使用しない。

### 4.7 レコード (record) の活用
* **データモデル:** 不変データの表現にはレコードを積極的に使う（従来の getter/setter POJO を排除）。
* **ローカルレコード:** メソッド内で一時的に複数の値をグルーピングする場合、ローカルレコードを定義してパターンマッチングと組み合わせる。
* トップレベルのレコードは暗黙クラス内にそのまま宣言できる。

### 4.8 sealed interface
* 取りうる型が限定される代数的データ型を表現する場合に使用する。
* `switch` 式の網羅性チェック（exhaustiveness）と組み合わせることで、新しいバリアントの追加漏れをコンパイル時に検出できる。

### 4.9 文字列の扱い
* **`+` 演算子:** 短い文字列結合や `IO.println` 内での簡易結合に使用する。
* **`String.format`:** 桁揃え・フォーマット指定が必要な場合に使用する。
* **テキストブロック (`"""`):** 複数行の定型テキスト（ASCII アート、ヘルプメッセージ等）に使用を検討する。

### 4.10 出力 API
* **`IO.println`:** 標準的な出力に使用する。`System.out.println` は使わない。
* **`IO.print`:** 改行なし出力（プロンプト表示等）に使用する。

### 4.11 命名規則
* **メソッド名:** キャメルケース (`formatBytes`, `renderBar`)。
* **変数名:** キャメルケース (`heapUsed`, `totalGcCount`)。
* **定数 (`static final`):** UPPER_SNAKE_CASE (`BAR_WIDTH`, `MB`)。
* **ファイル名:** パスカルケース (`MemoryExplorer.java`)。

---

## 5. Javadoc の書き方

### 5.1 クラスレベル Javadoc（ファイル先頭）
ファイルの先頭に `/** ... */` でプログラム全体の Javadoc を記述する。以下のセクションを含める:

```java
/**
 * プログラムの概要（1行目は簡潔な要約文）。
 *
 * <p>詳細な説明（背景知識や技術的意図を含む）。
 *
 * <h2>学べること</h2>
 * <ul>
 *   <li>学習ポイント1</li>
 *   <li>学習ポイント2</li>
 * </ul>
 *
 * <h2>対象層</h2>                      ← 該当する場合
 * <ul>
 *   <li><strong>Java 層:</strong> ...</li>
 *   <li><strong>HotSpot C++ 層:</strong> ...</li>
 * </ul>
 *
 * <h2>実行方法</h2>
 * <pre>{@code
 * java src/main/java/カテゴリ/ファイル名.java
 * }</pre>
 *
 * @author jdk-core
 * @see 関連クラスへの参照
 */
```

### 5.2 メソッドレベル Javadoc
* エントリポイント (`void main()`) とすべてのヘルパーメソッドに Javadoc を記述する。
* `@param`, `@return`, `@see` を日本語で記述する。
* 短いヘルパー（1-2行のメソッド）には1行 Javadoc (`/** ... */`) も許容する。

### 5.3 `@author` タグ
* すべてのファイルで `@author jdk-core` を統一使用する。

---

## 6. 例外処理

* **`Error` の catch:** 学習・デモ目的でのみ `StackOverflowError` や `OutOfMemoryError` を catch してよい。その際、学習目的であることをコメントで明記する。本番コードでは `Error` を catch してはならない。
* **`InterruptedException`:** `Thread.currentThread().interrupt()` でフラグを復元してから処理を続行する。以下のパターンを標準とする:
  ```java
  void sleep(long ms) {
      try {
          Thread.sleep(ms);
      } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
      }
  }
  ```
* **checked vs unchecked:** 回復可能な異常は checked 例外、プログラムのバグは unchecked 例外 (`IllegalArgumentException` 等) を使用する。

---

## 7. OpenJDK 特有のルール

### 層の意識
* 常に「Java 層」「JNI/ネイティブ層」「HotSpot (C++) 層」のどこを扱っているか明示する。
* 解説時には層をまたぐ処理フローを可視化する。
* Javadoc の `<h2>対象層</h2>` セクションで明示するか、インラインコメントで層を注記する。

### 検証
* `jtreg` によるテストを重視し、Zulu とのランタイム特性（GC/JIT）の差分を確認する。

---

## 8. ワークフロー

1. **Insight:** 英語のソースコメントや Javadoc を読み解き、日本語の技術メモ（`docs/research/`）に整理する。
2. **Trace:** 最新機能が JVM 内部（C++ 層）でどう処理されるか、AI と共に日本語で議論しながら追跡する。
3. **Build:** `bash configure` および `make images` を実行し、実際の挙動を日本語でレポートする。
