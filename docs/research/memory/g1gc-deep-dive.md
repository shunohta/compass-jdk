# G1GC ディープダイブ -- Garbage-First GC の全貌を解き明かす

> 本ドキュメントは [gc-deep-dive.md](gc-deep-dive.md) 第7章「G1GC の詳細な動作」を独立ファイルとして分離・大幅拡充したものである。
> **Java 25 / Azul Zulu 25 (OpenJDK 25)** を基準とする。

---

## 目次

- [第1章: G1GC の歴史的背景](#第1章-g1gc-の歴史的背景)
  - [1.1 名前の由来 -- なぜ "Garbage-First" なのか](#11-名前の由来----なぜ-garbage-first-なのか)
  - [1.2 G1GC の進化年表](#12-g1gc-の進化年表)
  - [1.3 Java 25 時点での G1GC の位置づけ](#13-java-25-時点での-g1gc-の位置づけ)
- [第2章: リージョンベースのヒープ管理](#第2章-リージョンベースのヒープ管理)
  - [2.1 従来の GC との根本的な違い](#21-従来の-gc-との根本的な違い)
  - [2.2 リージョンサイズの決定](#22-リージョンサイズの決定)
  - [2.3 リージョンベースの利点](#23-リージョンベースの利点)
- [第3章: リージョンの種類](#第3章-リージョンの種類)
  - [3.1 Eden リージョン](#31-eden-リージョン)
  - [3.2 Survivor リージョン](#32-survivor-リージョン)
  - [3.3 Old リージョン](#33-old-リージョン)
  - [3.4 Humongous リージョン](#34-humongous-リージョン)
  - [3.5 Free リージョン](#35-free-リージョン)
  - [3.6 リージョン種別の比較表](#36-リージョン種別の比較表)
- [第4章: TLAB と G1GC のオブジェクト割り当て](#第4章-tlab-と-g1gc-のオブジェクト割り当て)
  - [4.1 TLAB の基本原理](#41-tlab-の基本原理)
  - [4.2 G1GC における TLAB の特殊性](#42-g1gc-における-tlab-の特殊性)
  - [4.3 TLAB のサイズ調整](#43-tlab-のサイズ調整)
  - [4.4 Slow-path allocation](#44-slow-path-allocation)
- [第5章: Remembered Set, Card Table, Write Barrier](#第5章-remembered-set-card-table-write-barrier)
  - [5.1 世代間参照の問題](#51-世代間参照の問題)
  - [5.2 Card Table -- ヒープのダーティトラッキング](#52-card-table----ヒープのダーティトラッキング)
  - [5.3 Remembered Set (RSet)](#53-remembered-set-rset)
  - [5.4 Write Barrier の詳細](#54-write-barrier-の詳細)
  - [5.5 Refinement スレッド](#55-refinement-スレッド)
  - [5.6 RSet のメモリオーバーヘッド](#56-rset-のメモリオーバーヘッド)
- [第6章: G1GC のフェーズ -- 4つの動作モード](#第6章-g1gc-のフェーズ----4つの動作モード)
  - [6.1 全体サイクルの概観](#61-全体サイクルの概観)
  - [6.2 Young-only フェーズ（Young GC の繰り返し）](#62-young-only-フェーズyoung-gc-の繰り返し)
  - [6.3 Concurrent Marking（並行マーキング）](#63-concurrent-marking並行マーキング)
  - [6.4 Mixed GC フェーズ](#64-mixed-gc-フェーズ)
  - [6.5 Full GC（最終手段）](#65-full-gc最終手段)
  - [6.6 SATB (Snapshot-At-The-Beginning)](#66-satb-snapshot-at-the-beginning)
- [第7章: Evacuation（退避）の仕組み](#第7章-evacuation退避の仕組み)
  - [7.1 Evacuation とは何か](#71-evacuation-とは何か)
  - [7.2 Evacuation の処理手順](#72-evacuation-の処理手順)
  - [7.3 Collection Set (CSet)](#73-collection-set-cset)
  - [7.4 Evacuation Failure（退避失敗）](#74-evacuation-failure退避失敗)
- [第8章: Humongous Object の詳細](#第8章-humongous-object-の詳細)
  - [8.1 Humongous Object の割り当て](#81-humongous-object-の割り当て)
  - [8.2 Eager Reclaim（即時回収）](#82-eager-reclaim即時回収)
  - [8.3 Humongous Object のパフォーマンス影響](#83-humongous-object-のパフォーマンス影響)
- [第9章: Adaptive IHOP](#第9章-adaptive-ihop)
  - [9.1 IHOP の役割](#91-ihop-の役割)
  - [9.2 Adaptive IHOP のアルゴリズム](#92-adaptive-ihop-のアルゴリズム)
  - [9.3 手動設定と自動調整の使い分け](#93-手動設定と自動調整の使い分け)
- [第10章: 停止時間目標とリージョン選択](#第10章-停止時間目標とリージョン選択)
  - [10.1 MaxGCPauseMillis の仕組み](#101-maxgcpausemillis-の仕組み)
  - [10.2 停止時間予測モデル](#102-停止時間予測モデル)
  - [10.3 動的調整の対象](#103-動的調整の対象)
- [第11章: String Deduplication](#第11章-string-deduplication)
  - [11.1 文字列の重複問題](#111-文字列の重複問題)
  - [11.2 String Deduplication の仕組み](#112-string-deduplication-の仕組み)
  - [11.3 有効化と監視](#113-有効化と監視)
- [第12章: リージョンピンニング (JEP 423)](#第12章-リージョンピンニング-jep-423)
  - [12.1 JNI Critical Region の問題](#121-jni-critical-region-の問題)
  - [12.2 リージョンピンニングによる解決](#122-リージョンピンニングによる解決)
  - [12.3 Java 25 での状態](#123-java-25-での状態)
- [第13章: G1GC のメモリオーバーヘッド](#第13章-g1gc-のメモリオーバーヘッド)
  - [13.1 オーバーヘッドの内訳](#131-オーバーヘッドの内訳)
  - [13.2 オーバーヘッドを減らすテクニック](#132-オーバーヘッドを減らすテクニック)
- [第14章: G1GC チューニングパラメータ一覧](#第14章-g1gc-チューニングパラメータ一覧)
  - [14.1 基本パラメータ](#141-基本パラメータ)
  - [14.2 Concurrent Marking 関連](#142-concurrent-marking-関連)
  - [14.3 Mixed GC 関連](#143-mixed-gc-関連)
  - [14.4 Humongous 関連](#144-humongous-関連)
  - [14.5 その他の重要パラメータ](#145-その他の重要パラメータ)
- [第15章: G1GC のパフォーマンス特性](#第15章-g1gc-のパフォーマンス特性)
  - [15.1 G1GC が向いているワークロード](#151-g1gc-が向いているワークロード)
  - [15.2 G1GC が苦手なワークロード](#152-g1gc-が苦手なワークロード)
  - [15.3 他の GC との比較](#153-他の-gc-との比較)
- [第16章: リージョン状態遷移図](#第16章-リージョン状態遷移図)
- [実験用 Java コード](#実験用-java-コード)
- [HotSpot C++ ソースパス一覧](#hotspot-c-ソースパス一覧)
- [参考リンク](#参考リンク)

---

## 第1章: G1GC の歴史的背景

### 1.1 名前の由来 -- なぜ "Garbage-First" なのか

G1GC の正式名称は **Garbage-First Garbage Collector** である。この名前は「**ゴミ（到達不能オブジェクト）が最も多いリージョンから優先的に回収する**」という設計思想に由来する。従来の GC が世代全体を一括で回収するのに対し、G1GC はリージョン単位で「費用対効果の高い場所」を選んで回収する。これが "Garbage-First"（ゴミ優先）の意味である。

この設計により、限られた停止時間の中で最大のメモリ回収量を得ることができる。

### 1.2 G1GC の進化年表

```
2004年    G1GC の研究論文発表（Sun Microsystems）
  |       "Garbage-First Garbage Collection" -- Detlefs, Flood, Heller, Printezis
  |
2009年    Java 6 Update 14 で実験的導入（-XX:+UnlockExperimentalVMOptions が必要）
  |
2012年    Java 7 Update 4 で正式サポート（ただしデフォルトは Parallel GC のまま）
  |
2017年    Java 9 -- JEP 248: G1GC がデフォルト GC に昇格
  |       ┌──────────────────────────────────────────────────────┐
  |       │ これは Java の GC 史における最大の転換点の一つ。       │
  |       │ ParallelGC のスループット重視から、G1GC のレイテンシ   │
  |       │ 重視へとデフォルトの哲学が変わった。                   │
  |       └──────────────────────────────────────────────────────┘
  |
2018年    Java 10 -- JEP 307: Full GC の並列化
  |       Full GC がシングルスレッドからマルチスレッドになり、最悪ケースが大幅改善
  |
2019年    Java 12 -- JEP 346: Abortable Mixed Collections
  |       停止時間目標を超えそうな場合に Mixed GC を途中で打ち切り
  |
2019年    Java 12 -- JEP 344: Promptly Return Unused Memory
  |       アイドル時に未使用ヒープメモリを OS に返却
  |
2020年    Java 14 -- JEP 345: NUMA-Aware Memory Allocation for G1
  |       NUMA アーキテクチャでのメモリ割り当て最適化
  |
2024年    Java 22 -- JEP 423: Region Pinning for G1
  |       JNI Critical Region でも GC をブロックしなくなった
  |
2025年    Java 25 -- 継続的な改善
          リージョンピンニングの成熟、各種内部最適化
```

### 1.3 Java 25 時点での G1GC の位置づけ

Java 25 (2025年9月 GA 予定) において、G1GC は以下の位置づけにある。

| 観点 | 状態 |
|---|---|
| デフォルト GC | Java 9 以降不変。**G1GC がデフォルト** |
| 成熟度 | 10 年以上の本番実績。最も広く使われている GC |
| 代替 GC | ZGC (JEP 377)、Shenandoah が本番利用可能 |
| Full GC | 並列化済み (JEP 307)、Abortable Mixed Collections (JEP 346) で発生頻度も低減 |
| JNI 互換性 | Region Pinning (JEP 423) で JNI Critical Region 問題を解決済み |
| 推奨ヒープサイズ | 数 GB 〜 数十 GB（汎用的に使える） |

**G1GC を使うべきか迷ったら:** 特別な理由がない限り、G1GC をそのまま使うのが正解。チューニングなしでも「そこそこ良い」パフォーマンスを出せるのが G1GC の最大の美点である。

---

## 第2章: リージョンベースのヒープ管理

### 2.1 従来の GC との根本的な違い

従来の GC（Serial, Parallel）はヒープを **連続した Young / Old の 2 領域** に分割する。これに対し、G1GC はヒープを **等サイズのリージョン (Region)** に分割し、各リージョンに役割を動的に割り当てる。

```
従来の GC のヒープレイアウト:
┌════════════════════════┬══════════════════════════════════════┐
│     Young Generation   │          Old Generation              │
│ [Eden   ][S0 ][S1 ]   │                                      │
└════════════════════════┴══════════════════════════════════════┘
  ← 固定サイズ →            ← 残り全部 →
  起動時に決定。動的に変更できない。

G1GC のヒープレイアウト:
┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
│  E  │  E  │  S  │  O  │  O  │  E  │  O  │  H  │  H  │  F  │
├─────┼─────┼─────┼─────┼─────┼─────┼─────┼─────┼─────┼─────┤
│  O  │  F  │  E  │  O  │  F  │  F  │  O  │  E  │  S  │  O  │
└─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┘
  E = Eden    S = Survivor    O = Old    H = Humongous    F = Free
  各リージョンの役割は GC サイクルごとに変化する。
```

ここが革新的なのは、**物理的に連続した領域を確保する必要がない**ということである。Eden リージョンは飛び飛びに配置されていてもよい。この柔軟性が、停止時間の予測・制御を可能にする基盤になっている。

### 2.2 リージョンサイズの決定

リージョンサイズはヒープサイズに応じて JVM が自動決定する。

```
リージョンサイズの決定ロジック（疑似コード）:

  region_size = heap_size / 2048
  region_size = 2のべき乗に切り上げ
  region_size = clamp(region_size, 1MB, 32MB)

ヒープサイズとリージョンサイズの対応:
┌─────────────────┬───────────────────┬──────────────┐
│ ヒープサイズ      │ リージョンサイズ    │ リージョン数  │
├─────────────────┼───────────────────┼──────────────┤
│ < 4 GB          │ 1 MB              │ 〜 4096      │
│ 4 GB 〜 8 GB    │ 2 MB              │ 2048 〜 4096 │
│ 8 GB 〜 16 GB   │ 4 MB              │ 2048 〜 4096 │
│ 16 GB 〜 32 GB  │ 8 MB              │ 2048 〜 4096 │
│ 32 GB 〜 64 GB  │ 16 MB             │ 2048 〜 4096 │
│ > 64 GB         │ 32 MB             │ 2048+        │
└─────────────────┴───────────────────┴──────────────┘
```

**明示指定:** `-XX:G1HeapRegionSize=<size>` で 1MB 〜 32MB（2 のべき乗）を指定可能。通常は自動決定に任せるのが推奨。

**HotSpot C++ 層での実装:**
- `src/hotspot/share/gc/g1/heapRegion.cpp` -- リージョンの基本管理
- `src/hotspot/share/gc/g1/g1HeapRegionSize.cpp` -- リージョンサイズの計算

### 2.3 リージョンベースの利点

1. **動的な世代比率:** Young / Old の比率を GC サイクルごとに調整できる。Eden リージョン数を増やせばスループットが上がり、減らせば停止時間が短くなる。
2. **選択的回収:** "Garbage-First" の名の通り、ゴミの多いリージョンだけを選んで回収できる。
3. **停止時間の制御:** 回収するリージョン数を調整することで、STW 時間を目標内に収められる。
4. **メモリの効率的な再利用:** 完全に空になったリージョンは即座に Free リストに戻る。

---

## 第3章: リージョンの種類

### 3.1 Eden リージョン

新しいオブジェクトが最初に配置される場所。アプリケーションスレッドが `new` でオブジェクトを生成すると、そのスレッドの TLAB（後述）を通じて Eden リージョンに配置される。

```
Eden リージョン内部:
┌────────────────────────────────────────────────┐
│ [Thread1 TLAB][Thread2 TLAB]  [Thread3 TLAB]  │
│ ┌──────────┐ ┌──────────┐   ┌──────────┐     │
│ │obj obj   │ │obj obj   │   │obj       │     │
│ │obj   obj │ │  obj obj │   │obj obj   │     │
│ └──────────┘ └──────────┘   └──────────┘     │
│                        [未割り当て領域]          │
└────────────────────────────────────────────────┘
```

- Young GC で毎回全 Eden リージョンが回収対象になる
- Eden リージョン数は G1GC が動的に決定する（`-XX:G1NewSizePercent` 〜 `-XX:G1MaxNewSizePercent` の範囲内）
- デフォルト: ヒープの 5% 〜 60%

### 3.2 Survivor リージョン

Young GC で生き残ったオブジェクトが移動する先。オブジェクトが Survivor に移動するたびに **age（年齢）** が 1 増加する。age が閾値に達すると Old リージョンに昇格する。

```
age の進行:
  [Eden] ──Young GC──→ [Survivor (age=1)]
                         │
                         ├──Young GC──→ [Survivor (age=2)]
                         │                │
                         │                ├──Young GC──→ [Survivor (age=3)]
                         │                │                ...
                         │                │
                         │                └──age >= 閾値──→ [Old]
                         │
                         └──age >= 閾値──→ [Old]
```

- 昇格閾値: `-XX:MaxTenuringThreshold=15`（デフォルト 15、最大 15）
- 実際の閾値は G1GC が **Survivor 空間の目標充填率** に基づいて動的に調整する
- HotSpot C++ 層: `src/hotspot/share/gc/shared/ageTable.cpp`

### 3.3 Old リージョン

Survivor で十分な回数を生き延びたオブジェクトが昇格する先。Old リージョンは Mixed GC で選択的に回収される。

- Old リージョンの回収にはまず Concurrent Marking でライブオブジェクトを特定する必要がある
- Concurrent Marking なしに Old リージョンを安全に回収する方法はない

### 3.4 Humongous リージョン

リージョンサイズの **50% 以上**を占める巨大オブジェクトは Humongous Object として扱われる。詳細は[第8章](#第8章-humongous-object-の詳細)で解説する。

```
Humongous Object の配置例（リージョンサイズ = 2MB の場合）:

1.2MB のオブジェクト → 1 リージョン（StartsHumongous）
┌──────────────────────────────────┐
│ SH: [====== 1.2MB object ======]│  ← 残り 0.8MB は使えない
└──────────────────────────────────┘

3.5MB のオブジェクト → 2 リージョン（StartsHumongous + ContinuesHumongous）
┌──────────────────────────────────┬──────────────────────────────────┐
│ SH: [========= 3.5MB object ====│====]  CH: [残り未使用]            │
└──────────────────────────────────┴──────────────────────────────────┘
  SH = StartsHumongous               CH = ContinuesHumongous
```

### 3.5 Free リージョン

現在使用されていない空きリージョン。新しい Eden、Survivor、Humongous の割り当てに再利用される。GC 後に回収されたリージョンは Free に戻る。

### 3.6 リージョン種別の比較表

| 種類 | 説明 | GC での扱い | 特記事項 |
|---|---|---|---|
| **Eden** | 新規オブジェクト配置先 | Young GC で毎回回収 | TLAB 経由で割り当て |
| **Survivor** | Young GC 生存者 | Young GC で毎回処理 | age 管理付き |
| **Old** | 長寿命オブジェクト | Mixed GC で選択的回収 | Concurrent Marking 必須 |
| **Humongous** | リージョンの 50% 以上 | 特別扱い（Eager Reclaim 等） | 連続リージョンに配置 |
| **Free** | 空きリージョン | 次の割り当てに使用 | Free リストで管理 |

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/g1/heapRegion.cpp` -- リージョンの基本管理
- `src/hotspot/share/gc/g1/heapRegionType.hpp` -- リージョン種別の定義（enum で Eden/Survivor/Old/Humongous 等を区別）

---

## 第4章: TLAB と G1GC のオブジェクト割り当て

### 4.1 TLAB の基本原理

**TLAB (Thread-Local Allocation Buffer)** は、各スレッドが専有するヒープ上の小さなバッファである。オブジェクト生成のたびにヒープ全体のロックを取得するのは極めて高コストであるため、各スレッドに事前割り当てされた領域からロックなしで高速にオブジェクトを確保する。

```
TLAB の仕組み:

スレッド A の TLAB:
┌──────────────────────────────────────────┐
│ [obj1][obj2][obj3]  ↑ [   空き領域   ]   │
│                     top              end  │
└──────────────────────────────────────────┘
  start               ↑
                ポインタを進めるだけで割り当て完了（atomic ではない）

new Object() の処理:
  1. top + object_size <= end ?
     → YES: top の位置にオブジェクトを配置、top += object_size（ロック不要）
     → NO:  TLAB を使い切った → 新しい TLAB を取得（ロック必要）

このポインタ・バンプ (pointer bump) 方式により、
オブジェクト割り当ては単なる「足し算と比較」に帰着する。
```

### 4.2 G1GC における TLAB の特殊性

G1GC の TLAB は **Eden リージョン内** に配置される。一つの Eden リージョンに複数スレッドの TLAB が共存する場合もあるし、大きな TLAB は一つのリージョンにまたがらず単独で使うこともある。

```
G1GC のヒープ上での TLAB 配置:

Eden リージョン 1:        Eden リージョン 2:
┌──────────────────┐    ┌──────────────────┐
│ [TLAB-A][TLAB-B] │    │ [TLAB-C]         │
│ [TLAB-D]         │    │ [TLAB-E][TLAB-F] │
└──────────────────┘    └──────────────────┘

各スレッドは自分の TLAB 内でロックなしにオブジェクトを割り当てる。
TLAB が使い切られると、同じ Eden リージョン内に新しい TLAB を切り出すか、
別の Eden リージョンから TLAB を取得する。
```

**従来の GC との違い:** 従来の GC では TLAB は単一の連続した Eden 空間から切り出されるが、G1GC では複数の Eden リージョンにまたがって TLAB が配置される。この違いはアプリケーションからは見えないが、GC のリージョン管理と密に連携している。

### 4.3 TLAB のサイズ調整

TLAB のサイズは JVM が各スレッドの割り当て速度に基づいて動的に調整する。

```
TLAB サイズの動的調整:

  高速割り当てスレッド（例: Web リクエスト処理） → 大きい TLAB
  低速割り当てスレッド（例: タイマースレッド）   → 小さい TLAB

  利点:
  - 割り当てが多いスレッドは TLAB を頻繁にリフィルしなくて済む
  - 割り当てが少ないスレッドは無駄な予約を最小限にできる
```

関連 JVM フラグ:
- `-XX:+UseTLAB` -- TLAB を有効化（デフォルト ON、通常は変更不要）
- `-XX:TLABSize=<size>` -- 初期 TLAB サイズ（通常は自動調整に任せる）
- `-XX:-ResizeTLAB` -- TLAB サイズの動的調整を無効化（デバッグ用途）

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp` -- TLAB の管理・リサイズ
- `src/hotspot/share/gc/shared/threadLocalAllocBuffer.hpp` -- TLAB のデータ構造

### 4.4 Slow-path allocation

TLAB に収まらないオブジェクト（TLAB サイズより大きいが Humongous 未満）は、**slow-path** で直接 Eden リージョンに割り当てられる。この場合はリージョンレベルのロック（CAS 操作）が必要になるため、TLAB 経由より遅い。

```
オブジェクト割り当ての判定フロー:

  new Object(size)
    │
    ├── size <= TLAB の残り → TLAB 内でポインタバンプ（最速）
    │
    ├── size > TLAB の残り だが TLAB リフィルが有利
    │   → 新しい TLAB を取得して割り当て
    │
    ├── size > TLAB サイズ だが < リージョンの 50%
    │   → Eden リージョンに直接割り当て（slow-path）
    │
    └── size >= リージョンの 50%
        → Humongous Object として専用リージョンに割り当て
```

---

## 第5章: Remembered Set, Card Table, Write Barrier

### 5.1 世代間参照の問題

Young GC は Young Generation（Eden + Survivor）だけを回収対象とする。しかし、Old Generation のオブジェクトが Young Generation のオブジェクトを参照している場合、その Young オブジェクトは生存していると見なさなければならない。

```
問題のシナリオ:

Old リージョン:          Young リージョン:
┌─────────────┐         ┌─────────────┐
│ [old_obj]───┼────────→│ [young_obj] │
└─────────────┘         └─────────────┘
       ↑                       ↑
  この参照を知らないと、   young_obj を誤って回収してしまう

素朴な解決策: Young GC のたびに Old Generation 全体をスキャンする
  → これでは Young GC の意味がない（Old を含めた全体スキャンになる）

G1GC の解決策: Remembered Set + Card Table + Write Barrier
  → Old → Young の参照を「差分」で追跡する
```

### 5.2 Card Table -- ヒープのダーティトラッキング

ヒープ全体を **512 バイト** 単位の小さな **カード** に分割する。カードテーブルは巨大な byte 配列で、ヒープの各 512 バイト領域に対応する 1 バイトのエントリを持つ。

```
Card Table の構造:

ヒープ (例: 1GB):
┌────┬────┬────┬────┬────┬────┬────┬────┬── ... ──┐
│512B│512B│512B│512B│512B│512B│512B│512B│         │
│ #0 │ #1 │ #2 │ #3 │ #4 │ #5 │ #6 │ #7 │         │
└────┴────┴────┴────┴────┴────┴────┴────┴── ... ──┘

Card Table (1GB / 512B = 約2Mエントリ):
[ 0 ][ 0 ][ 1 ][ 0 ][ 0 ][ 1 ][ 0 ][ 0 ] ...
              ↑               ↑
           dirty           dirty
  → この 2 カード（1024 バイト分）だけスキャンすれば OK
```

**dirty の意味:** そのカードに含まれるオブジェクトの参照フィールドが変更されたことを示す。Card Table は世代を問わずヒープ全体をカバーする。

### 5.3 Remembered Set (RSet)

G1GC では、各リージョンが自分を参照しているリージョン・カードの **逆引きインデックス** を持つ。これが **Remembered Set (RSet)** である。

```
RSet のイメージ:

         ┌────────────┐        ┌────────────┐
         │ Region R2  │        │ Region R5  │
         │ [objA]─────┼───────→│ [objX]     │
         └────────────┘        │ [objY]     │
                               └────────────┘
         ┌────────────┐              ↑
         │ Region R8  │              │
         │ [objB]─────┼──────────────┘
         └────────────┘

Region R5 の RSet:
  ┌─────────────────────────────────────────┐
  │ "自分を参照しているのは:"                  │
  │   - Region R2, Card #3                   │
  │   - Region R8, Card #7                   │
  └─────────────────────────────────────────┘

Young GC 時:
  R5 が Young リージョンなら、R5 の RSet を参照
  → R2 の Card #3 と R8 の Card #7 だけスキャンすれば
    Old → Young の参照を漏れなく発見できる
```

**RSet の粒度（Granularity）:**

RSet は参照の密度に応じて 3 段階のデータ構造を使い分ける。

```
RSet の 3 段階:

1. Sparse (スパース): 少数の参照
   → PRT (Per-Region Table) でカード番号をリスト管理
   → メモリ効率が良い

2. Fine (ファイン): 中程度の参照
   → ビットマップでカード番号を管理
   → 1リージョンあたり 1 ビットマップ

3. Coarse (コース): 大量の参照
   → リージョン番号だけを記録（カード番号は諦める）
   → スキャン範囲が広がるが、メモリ使用量が一定
```

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/g1/g1RemSet.cpp` -- Remembered Set の管理
- `src/hotspot/share/gc/g1/g1CardTable.cpp` -- Card Table の実装

### 5.4 Write Barrier の詳細

**Write Barrier（書き込みバリア）** は、参照フィールドが更新されるたびに JVM が挿入する追加コードである。G1GC は **2 種類** の Write Barrier を使用する。

#### Pre-write Barrier（SATB バリア）

参照が**上書きされる前**に、**上書きされる側（古い参照先）** を記録する。これは Concurrent Marking 中の SATB（Snapshot-At-The-Beginning）を維持するために必要。

```java
// Java コード
obj.field = newRef;

// JVM が生成する疑似コード:
// --- Pre-write Barrier (SATB) ---
old_ref = obj.field;                    // 上書き前の参照を読む
if (concurrent_marking_active) {
    satb_queue.enqueue(old_ref);         // SATB キューに記録
}
// --- 実際の書き込み ---
obj.field = newRef;
```

**なぜ Pre-write が必要か:** Concurrent Marking 中にアプリケーションが参照を切り替えると、マーキングの開始時点では到達可能だったオブジェクトが見逃される可能性がある。Pre-write Barrier で「切り離される側」を記録しておけば、Remark フェーズで回収漏れを防げる。

#### Post-write Barrier（RSet 更新バリア）

参照が**書き込まれた後**に、Card Table と RSet の更新をキューに入れる。

```java
// Java コード
obj.field = newRef;

// JVM が生成する疑似コード:
// --- 実際の書き込み ---
obj.field = newRef;
// --- Post-write Barrier ---
card = card_table[address_of(obj) >> 9];  // 512 = 2^9
if (card != DIRTY) {
    card_table[address_of(obj) >> 9] = DIRTY;
    dirty_card_queue.enqueue(card_address);  // Refinement キューに追加
}
```

```
2 種類の Write Barrier の全体像:

  obj.field = newRef; の実行時:

  ┌───────────────────────────────────┐
  │ 1. Pre-write Barrier (SATB)       │
  │    old_ref を SATB キューに記録     │
  │    （Concurrent Marking 中のみ）    │
  ├───────────────────────────────────┤
  │ 2. 実際の参照書き込み               │
  │    obj.field = newRef              │
  ├───────────────────────────────────┤
  │ 3. Post-write Barrier             │
  │    Card Table を dirty に          │
  │    Dirty Card Queue にエンキュー    │
  └───────────────────────────────────┘
         │
         ↓ 非同期で処理
  ┌───────────────────────────────────┐
  │ Refinement スレッド                │
  │  Dirty Card → RSet 更新            │
  └───────────────────────────────────┘
```

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/g1/g1BarrierSet.cpp` -- G1 固有の Write Barrier
- `src/hotspot/share/gc/g1/g1SATBMarkQueueSet.cpp` -- SATB キュー

### 5.5 Refinement スレッド

Post-write Barrier がキューに入れた dirty カード情報を **非同期** で処理し、RSet を更新するのが **Refinement スレッド** の役割である。

```
Refinement スレッドの動作:

アプリケーションスレッド:
  obj.field = ref → dirty card queue に追加 → 次の処理へ（ブロックしない）

Refinement スレッド（バックグラウンド）:
  dirty card queue からカードを取り出し
  → カード内のオブジェクトをスキャン
  → 他リージョンへの参照を発見
  → 参照先リージョンの RSet を更新

スレッド数の自動調整:
  ┌─────────────────────────────────────────────────┐
  │ キュー長  │ 動作                                  │
  │ 短い      │ Refinement スレッドはスリープ           │
  │ 中程度    │ 1〜数スレッドが処理                     │
  │ 長い      │ 全 Refinement スレッドが稼働            │
  │ 非常に長い │ アプリケーションスレッドも手伝う          │
  │           │ （最悪ケース、性能に影響）               │
  └─────────────────────────────────────────────────┘
```

- `-XX:G1ConcRefinementThreads=<n>` -- Refinement スレッド数（デフォルトは自動）
- キューが溢れるとアプリケーションスレッドが直接 RSet 更新を行うため、一時的にスローダウンする

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/g1/g1ConcurrentRefine.cpp` -- Refinement スレッドの管理
- `src/hotspot/share/gc/g1/g1DirtyCardQueue.cpp` -- Dirty Card キュー

### 5.6 RSet のメモリオーバーヘッド

RSet は G1GC の最大のメモリオーバーヘッド源である。一般的にヒープの **1% 〜 20%** を消費する。

```
RSet のメモリ消費が大きくなるケース:

1. Old → Old の参照が非常に多い場合
   （複雑なオブジェクトグラフ）

2. 多くのリージョンにまたがる参照パターン
   （キャッシュの局所性が低いデータ構造）

3. 参照の書き込みが非常に頻繁な場合
   （高頻度の参照更新ワークロード）

目安:
  通常のアプリケーション: ヒープの 1〜5%
  複雑なグラフ構造:       ヒープの 5〜10%
  最悪ケース:             ヒープの 〜20%
```

RSet の使用量は GC ログで確認できる:
```bash
java -Xlog:gc+remset*=trace -jar app.jar
```

---

## 第6章: G1GC のフェーズ -- 4つの動作モード

### 6.1 全体サイクルの概観

G1GC は以下の 4 つのフェーズを状態マシンのように遷移する。

```
G1GC のフェーズサイクル:

  ┌─────────────────────────────────────────────────────────────────┐
  │                                                                 │
  │  ┌──────────────────┐    Old 使用率が IHOP に到達                │
  │  │  Young-only      │─────────────────────────┐                 │
  │  │  フェーズ          │                         ↓                 │
  │  │                  │                  ┌──────────────────┐     │
  │  │  (Young GC を     │                  │ Concurrent       │     │
  │  │   繰り返す)       │                  │ Marking          │     │
  │  │                  │                  │ フェーズ           │     │
  │  └──────────────────┘                  │                  │     │
  │           ↑                            │ (アプリと並行して  │     │
  │           │                            │  Old の生死を判定) │     │
  │           │                            └────────┬─────────┘     │
  │           │                                      │               │
  │           │  Old 使用率が十分低下                   ↓               │
  │           │                            ┌──────────────────┐     │
  │           └────────────────────────────│ Mixed GC         │     │
  │                                        │ フェーズ           │     │
  │                                        │                  │     │
  │                                        │ (Young + Old の   │     │
  │                                        │  混合回収)        │     │
  │                                        └──────────────────┘     │
  │                                                                 │
  │  ※ いずれのフェーズでも空きリージョン枯渇時は Full GC が発生         │
  └─────────────────────────────────────────────────────────────────┘
```

### 6.2 Young-only フェーズ（Young GC の繰り返し）

Eden リージョンが満杯になるたびに **Young GC** が実行される。Young GC は Eden + Survivor の全リージョンを対象とする STW (Stop-the-World) 停止である。

```
Young GC の前後:

  Before:
  ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
  │  E  │  E  │  E  │  S  │  O  │  O  │  O  │  F  │
  │full │full │full │     │     │     │     │     │
  └─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┘
              ↓ Eden 満杯 → Young GC (STW)

  After:
  ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
  │  F  │  F  │  F  │  F  │  O  │  O  │  O  │  S  │
  │     │     │     │     │     │     │     │ new │
  └─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┘
    ↑旧 Eden は Free に    ↑旧 Survivor も Free に  ↑新 Survivor
    ※ 一部オブジェクトは Old に昇格（図には表れていない既存 Old に追加）
```

**Young GC の処理内容:**
1. GC Roots（スタック変数、static フィールド等）から到達可能な Young オブジェクトを特定
2. RSet を参照して、Old → Young の参照も追跡
3. 生存オブジェクトを新しい Survivor リージョンにコピー（Evacuation）
4. age が閾値に達したオブジェクトは Old リージョンにコピー
5. 旧 Eden / 旧 Survivor リージョンを Free に戻す

このフェーズは、Old Generation の使用率が **IHOP (Initiating Heap Occupancy Percent)** に達するまで続く。

### 6.3 Concurrent Marking（並行マーキング）

Old Generation の使用率が IHOP に達すると、**アプリケーションと並行して** マーキングが開始される。全 5 ステップから構成される。

```
Concurrent Marking の 5 ステップ:

  時間軸 →
  ────────────────────────────────────────────────────────────→

  STW           Concurrent        Concurrent    STW        STW+Conc
  ┌────┐      ┌──────────────┐  ┌──────────┐  ┌────┐     ┌──────┐
  │ 1  │      │      2       │  │    3     │  │ 4  │     │  5   │
  │Init│      │ Root Region  │  │Concurrent│  │ Re │     │Clean │
  │Mark│      │   Scan       │  │  Mark    │  │mark│     │ up   │
  └────┘      └──────────────┘  └──────────┘  └────┘     └──────┘
   数ms          数ms             数百ms〜秒     数ms       数ms+α

  ← アプリケーション停止 →  ← アプリケーション並行実行 →
```

#### ステップ 1: Initial Mark (STW)

GC Roots から直接参照されるオブジェクトをマークする。通常は **Young GC に便乗 (piggybacking)** して実行されるため、追加の STW はほぼ発生しない。

#### ステップ 2: Root Region Scan (Concurrent)

Survivor リージョン内のオブジェクトが参照する Old オブジェクトをスキャンする。次の Young GC が始まるまでに完了する必要がある。

#### ステップ 3: Concurrent Mark (Concurrent)

ヒープ全体のオブジェクトグラフを走査し、到達可能なオブジェクトをマークする。アプリケーションと **完全に並行** で実行される。

```
Concurrent Mark の動作イメージ:

  アプリケーションスレッド:   |===処理===|===処理===|===処理===|
  GC マーキングスレッド:      |~~mark~~~~|~~mark~~~~|~~mark~~~~|
                              （同時進行、アプリを止めない）

  SATB バリアにより、マーキング中の参照変更も追跡される。
```

#### ステップ 4: Remark (STW)

Concurrent Mark 中にアプリケーションが変更した参照を再処理する。SATB キューに溜まったエントリを処理し、マーキングを完了させる。

#### ステップ 5: Cleanup (STW + Concurrent)

- **STW 部分:** リージョンごとの生存率を計算し、完全に空のリージョンを即座に Free リストに戻す。Mixed GC の対象リージョンをソートする。
- **Concurrent 部分:** 空リージョンの内部データ構造（RSet 等）を並行でクリーンアップする。

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/g1/g1ConcurrentMark.cpp` -- Concurrent Marking のメインロジック
- `src/hotspot/share/gc/g1/g1ConcurrentMarkThread.cpp` -- マーキングスレッドの管理

### 6.4 Mixed GC フェーズ

Concurrent Marking で特定された「ゴミの多い Old リージョン」を、Young GC と一緒に回収する。"Mixed" という名前は、Young リージョンと Old リージョンを**混合して**回収することに由来する。

```
Mixed GC のリージョン選択:

Concurrent Marking 後の各 Old リージョンの生存率:
┌──────────┬──────────┬──────────┐
│ Old-1    │ Old-2    │ Old-3    │
│ 生存 20% │ 生存 80% │ 生存 30% │
│ ゴミ 80% │ ゴミ 20% │ ゴミ 70% │
│ ★効率 高 │   効率 低│ ★効率 高 │
└──────────┴──────────┴──────────┘
┌──────────┬──────────┐
│ Old-4    │ Old-5    │
│ 生存 90% │ 生存 10% │
│ ゴミ 10% │ ゴミ 90% │
│   効率 低│ ★効率 高 │
└──────────┴──────────┘

回収効率でソート: Old-5(90%) > Old-1(80%) > Old-3(70%) > ...

Mixed GC 1回目: Eden + Survivor + Old-5 + Old-1
  → 停止時間目標内に収まるリージョン数を選択
Mixed GC 2回目: Eden + Survivor + Old-3
  → まだゴミが残っていれば続行
...繰り返し...

終了条件:
  - ゴミの割合が G1HeapWastePercent (5%) 以下になった
  - または G1MixedGCCountTarget (8) 回に達した
```

Mixed GC の関連パラメータ:
- `-XX:G1MixedGCCountTarget=8` -- Mixed GC の目標回数（デフォルト 8）
- `-XX:G1HeapWastePercent=5` -- この割合以下のゴミしかなくなったら Mixed GC 終了
- `-XX:G1MixedGCLiveThresholdPercent=85` -- 生存率がこれ以上のリージョンは Mixed GC 対象外

### 6.5 Full GC（最終手段）

Concurrent Marking や Mixed GC が間に合わず、空きリージョンが完全に枯渇した場合に発生する **最後の砦** である。

```
Full GC の発生条件:

  ┌───────────────────────────────────────────────────────┐
  │                                                       │
  │  Evacuation 先の Free リージョンが確保できない          │
  │  → Evacuation Failure 発生                             │
  │  → Full GC にフォールバック                              │
  │                                                       │
  │  または:                                               │
  │  Humongous Object 用の連続 Free リージョンがない        │
  │  → Full GC で Compaction してから割り当て               │
  │                                                       │
  └───────────────────────────────────────────────────────┘
```

- **ヒープ全体**を対象に Mark-Sweep-Compact を実行
- **Java 10 以降 (JEP 307):** Full GC は並列化されている（`-XX:ParallelGCThreads` スレッドを使用）
- それでも非常に重い操作であり、本番環境で頻発させてはならない
- Full GC が頻発する場合の対策: ヒープサイズの増加、IHOP の調整、オブジェクト割り当てパターンの見直し

### 6.6 SATB (Snapshot-At-The-Beginning)

Concurrent Marking 中にアプリケーションが参照を変更すると、マーキング結果が不正確になる可能性がある。G1GC は **SATB (Snapshot-At-The-Beginning)** と呼ばれる手法でこの問題を解決する。

```
SATB の問題設定:

  Concurrent Mark 開始時:
    A ──→ B ──→ C    （A→B→C は到達可能）

  マーキング中にアプリが参照を変更:
    1. マーカーが A をマーク済み
    2. アプリが B.next = null にする（B→C の参照を切断）
    3. アプリが A.other = C にする（A→C の直接参照を追加）
    4. マーカーが A の子を調べる → B を発見（マーク済みなのでスキップ）
       → C は B から辿れなくなっている → C がマークされない！

  SATB の解決:
    手順 2 で B.next が変更される「前」に、Pre-write Barrier が
    旧参照先 (C) を SATB キューに記録する。
    → Remark フェーズで C を再マークする
    → C は「生存」として安全に保護される

  トレードオフ:
    実際には C が不要になっていても、このサイクルでは回収されない（保守的）。
    次のマーキングサイクルで正しく回収される。これを "floating garbage" と呼ぶ。
```

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/g1/g1SATBMarkQueueSet.cpp` -- SATB キューの管理

---

## 第7章: Evacuation（退避）の仕組み

### 7.1 Evacuation とは何か

G1GC におけるオブジェクト回収は、**ゴミを消す**のではなく、**生存オブジェクトを別の場所にコピー（退避）する**方式を採る。これを **Evacuation（退避）** と呼ぶ。Evacuation 後、元のリージョンは丸ごと Free に戻る。

```
Evacuation の基本:

  Before:                         After:
  ┌─────────────────┐            ┌─────────────────┐
  │ [live] [dead]   │            │      Free       │  ← 丸ごと解放
  │ [dead] [live]   │   copy →   └─────────────────┘
  │ [live] [dead]   │
  └─────────────────┘            ┌─────────────────┐
    回収対象リージョン              │ [live][live]    │  ← コピー先
                                 │ [live]          │     (コンパクト化される)
                                 └─────────────────┘
                                   退避先リージョン
```

**Evacuation のメリット:**
1. **コンパクション効果:** コピー時に生存オブジェクトが詰めて配置されるため、メモリの断片化が解消される
2. **高速な解放:** リージョン単位で丸ごと Free に戻せるため、個別のオブジェクト解放が不要
3. **世代間移動の自然な実装:** Evacuation 先を Survivor にすれば若い世代内移動、Old にすれば昇格になる

### 7.2 Evacuation の処理手順

```
Evacuation の詳細手順:

  1. Collection Set (CSet) の決定
     ┌─────────────────────────────────────────┐
     │ Young GC:  全 Eden + 全 Survivor         │
     │ Mixed GC:  全 Eden + 全 Survivor + 選択 Old │
     └─────────────────────────────────────────┘

  2. GC Roots のスキャン (STW)
     ├── スタック変数
     ├── static フィールド
     ├── JNI グローバル参照
     └── その他の Root

  3. RSet のスキャン
     CSet 内リージョンの RSet を参照して、
     CSet 外 → CSet 内の参照を発見

  4. オブジェクトのコピー（並列処理）
     ┌──────────────────────────────────────────────────┐
     │ 各 GC ワーカースレッドが並列にコピーを実行:         │
     │                                                  │
     │  生存オブジェクトを発見                              │
     │    → age < 閾値 → 新 Survivor リージョンにコピー    │
     │    → age >= 閾値 → Old リージョンにコピー            │
     │  元のオブジェクトに forwarding pointer を設置       │
     │  参照を forwarding pointer 経由で新アドレスに更新   │
     └──────────────────────────────────────────────────┘

  5. 参照の更新
     コピー先を指すように全参照を更新

  6. CSet リージョンを Free に戻す
```

### 7.3 Collection Set (CSet)

**Collection Set (CSet)** は、GC で回収対象とするリージョンの集合である。CSet の選択が G1GC の性能を大きく左右する。

```
CSet の構成:

  Young GC の CSet:
  ┌──────────────────────────────────────┐
  │  全 Eden リージョン                    │  ← 必ず含まれる
  │  + 全 Survivor リージョン              │  ← 必ず含まれる
  └──────────────────────────────────────┘

  Mixed GC の CSet:
  ┌──────────────────────────────────────┐
  │  全 Eden リージョン                    │  ← 必ず含まれる
  │  + 全 Survivor リージョン              │  ← 必ず含まれる
  │  + 選択された Old リージョン            │  ← 回収効率で選択
  └──────────────────────────────────────┘
```

**Old リージョンの選択アルゴリズム:**

1. Concurrent Marking で各 Old リージョンの **生存バイト数** を算出
2. **回収効率** = (リージョンサイズ - 生存バイト数) / 推定 Evacuation コスト
3. 回収効率の高い順にソート
4. 停止時間予測モデルに基づき、目標時間内に収まるリージョン数を決定
5. `G1MixedGCLiveThresholdPercent` (85%) 以上の生存率のリージョンは除外

```
CSet 選択の判定フロー:

  Old リージョン一覧（回収効率順）:
    #1: 効率=高  → CSet に追加  累計停止時間: 20ms ✓
    #2: 効率=高  → CSet に追加  累計停止時間: 35ms ✓
    #3: 効率=中  → CSet に追加  累計停止時間: 55ms ✓
    #4: 効率=中  → ここで 200ms 目標を超えそう → 打ち切り（JEP 346）
    #5: 効率=低  → スキップ
    ...

  ※ JEP 346 (Abortable Mixed Collections, Java 12) により、
    GC 中に停止時間が超過しそうな場合は途中で打ち切れるようになった
```

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/g1/g1CollectionSet.cpp` -- CSet の管理と選択ロジック
- `src/hotspot/share/gc/g1/g1Policy.cpp` -- GC ポリシー（停止時間予測）

### 7.4 Evacuation Failure（退避失敗）

Evacuation 先の Free リージョンが不足すると、オブジェクトをコピーできなくなる。これが **Evacuation Failure** である。

```
Evacuation Failure のシナリオ:

  CSet 内の生存オブジェクトをコピー中...
  ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
  │  E  │  E  │  E  │  O  │  O  │  O  │  O  │  O  │
  │CSet │CSet │CSet │     │     │     │     │     │
  └─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┘
    ↑ Free リージョンが 0 → コピー先がない！

  対処:
  1. コピーできなかったオブジェクトは元の場所に留置（in-place）
  2. そのリージョンは Old リージョンに変更
  3. 最悪の場合、Full GC にフォールバック

  ※ Evacuation Failure は GC ログに明示的に記録される
```

Evacuation Failure が発生した場合のログ例:
```
[gc] GC(42) Pause Young (Normal) (Evacuation Failure)
```

---

## 第8章: Humongous Object の詳細

### 8.1 Humongous Object の割り当て

リージョンサイズの **50% 以上** を占めるオブジェクトは **Humongous Object** として特別に扱われる。Eden リージョンではなく、Old Generation に直接割り当てられる。

```
Humongous Object の割り当て:

  リージョンサイズ = 4MB の場合:

  2.5MB のオブジェクト → 1 リージョン
  ┌────────────────────────────────────────┐
  │ SH: [======= 2.5MB =======][1.5MB空き]│
  └────────────────────────────────────────┘
    StartsHumongous    ↑ この空き領域は他のオブジェクトに使えない

  7MB のオブジェクト → 2 リージョン
  ┌────────────────────────────────────────┬────────────────────────────────────────┐
  │ SH: [============= 7MB ===============│=============]  CH: [1MB 空き]          │
  └────────────────────────────────────────┴────────────────────────────────────────┘
    StartsHumongous                          ContinuesHumongous

  17MB のオブジェクト → 5 リージョン
  ┌──────┬──────┬──────┬──────┬──────┐
  │  SH  │  CH  │  CH  │  CH  │  CH  │  ← 5 つの連続リージョンが必要
  │ 4MB  │ 4MB  │ 4MB  │ 4MB  │ 1MB  │     残り 3MB は空き
  └──────┴──────┴──────┴──────┴──────┘
```

**重要: 連続リージョンの確保**

Humongous Object は**連続した**リージョンに配置される必要がある。ヒープが断片化していると、十分な空きリージョンがあっても連続していなければ割り当てが失敗する。この場合 Full GC が発生して Compaction が行われる。

### 8.2 Eager Reclaim（即時回収）

Java 8u60 以降、G1GC は Concurrent Marking の **Cleanup ステップ**で、到達不能な Humongous Object を即座に回収する機能を持つ。これを **Eager Reclaim** と呼ぶ。

```
Eager Reclaim の条件:

  1. Humongous Object が到達不能（GC Roots から参照されていない）
  2. RSet の参照がない（他のリージョンからの参照がない）
  3. SATB キューに関連エントリがない

  条件を満たす場合:
  ┌──────────────────┐
  │ SH: [dead hobj]  │  → Cleanup で即座に Free に回収
  └──────────────────┘     Mixed GC を待たなくてよい

  ※ Young GC 時にも Eager Reclaim が発生することがある
```

Eager Reclaim は Humongous Object のライフサイクルが短い場合に非常に有効である。例えば、大きなバイト配列を一時的に生成して処理するワークロードでは、Eager Reclaim により混合 GC を待たずに回収できる。

### 8.3 Humongous Object のパフォーマンス影響

```
Humongous Object のコスト一覧:

  ┌───────────────────────────────────────────────────────────────┐
  │ 問題                    │ 影響                                 │
  ├───────────────────────────────────────────────────────────────┤
  │ 内部断片化               │ 最終リージョンの空き領域が無駄になる    │
  │ 外部断片化               │ 連続 Free リージョンの確保が困難に     │
  │ TLAB バイパス            │ slow-path 割り当てで遅い              │
  │ Evacuation 非対象       │ コピーされないため Compact されない     │
  │ Full GC の誘発          │ 連続リージョン不足で Full GC           │
  └───────────────────────────────────────────────────────────────┘
```

**対策:**
1. 不要に大きな配列を作らない（特に `byte[]`、`char[]`、`Object[]`）
2. リージョンサイズを大きくする（`-XX:G1HeapRegionSize`）→ Humongous 閾値が上がる
3. Humongous の割り当て頻度を GC ログで監視する

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/g1/g1CollectedHeap.cpp` -- `humongous_obj_allocate()` メソッド
- `src/hotspot/share/gc/g1/g1ConcurrentMark.cpp` -- Eager Reclaim のロジック

---

## 第9章: Adaptive IHOP

### 9.1 IHOP の役割

**IHOP (Initiating Heap Occupancy Percent)** は、Concurrent Marking を開始する Old Generation 使用率の閾値である。IHOP が適切でないと、以下の問題が発生する。

```
IHOP が高すぎる場合:
  → Concurrent Marking の開始が遅れる
  → Mixed GC が間に合わない
  → Evacuation Failure → Full GC

IHOP が低すぎる場合:
  → Concurrent Marking が不必要に頻繁に実行される
  → CPU リソースの無駄遣い
  → アプリケーションのスループット低下

         IHOP
          ↓
  ├───────┼─────────────────────────────────┤
  0%      45%                              100%
          ← 低すぎ = 無駄な Marking    高すぎ = Full GC リスク →
```

### 9.2 Adaptive IHOP のアルゴリズム

Java 9 以降、G1GC は **Adaptive IHOP** により IHOP を自動調整する。

```
Adaptive IHOP の計算（概念）:

  必要な時間: Concurrent Marking にかかる推定時間
  Old の増加速度: 単位時間あたりの Old 使用量の増加率
  安全マージン: Concurrent Marking 中に消費される Old 領域

  adaptive_ihop = current_old_capacity
                  - (marking_time × old_allocation_rate)
                  - safety_margin

  言い換えると:
  「Concurrent Marking が完了するまでに Old が満杯にならないよう、
    十分な余裕を持って開始する閾値」を動的に計算する。

  ┌───────────────────────────────────────────────────┐
  │                                                   │
  │  Old 使用量   ╱╱╱╱╱╱╱╱╱╱╱╱╱╱                      │
  │  の推移      ╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱                   │
  │             ╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱                │
  │     ────────────────────────────────── Old 容量    │
  │            ↑                    ↑                  │
  │     IHOP で marking 開始    marking 完了            │
  │            ├── この間に Old が溢れなければ OK ──┤    │
  │                                                   │
  └───────────────────────────────────────────────────┘
```

### 9.3 手動設定と自動調整の使い分け

| 方法 | フラグ | 推奨場面 |
|---|---|---|
| Adaptive IHOP（デフォルト） | `-XX:+G1UseAdaptiveIHOP` | 通常はこれで十分 |
| 固定 IHOP | `-XX:-G1UseAdaptiveIHOP -XX:InitiatingHeapOccupancyPercent=45` | 予測可能なワークロード |

**Adaptive IHOP を無効化すべきケース:**
- ワークロードが極めて均一で、固定値の方が安定する場合
- Adaptive IHOP の学習期間中に Full GC が発生する場合（初期値を `-XX:InitiatingHeapOccupancyPercent` で設定）

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/g1/g1Policy.cpp` -- IHOP の計算と GC ポリシー
- `src/hotspot/share/gc/g1/g1IHOPControl.cpp` -- Adaptive IHOP のアルゴリズム

---

## 第10章: 停止時間目標とリージョン選択

### 10.1 MaxGCPauseMillis の仕組み

G1GC の最大の特徴は、**停止時間目標 (Pause Time Target)** を指定できることである。

```bash
# デフォルト: 200ms
java -XX:MaxGCPauseMillis=200 -jar app.jar

# より厳しい目標: 50ms
java -XX:MaxGCPauseMillis=50 -jar app.jar

# より緩い目標: 500ms（スループット重視）
java -XX:MaxGCPauseMillis=500 -jar app.jar
```

**重要:** `MaxGCPauseMillis` は **「目標」** であり **「保証」** ではない。実際の停止時間はこれを超える場合がある。

### 10.2 停止時間予測モデル

G1GC は、過去の GC 統計データに基づく **減衰平均 (decaying average)** モデルで停止時間を予測する。

```
予測モデルの入力:

  過去の GC データ:
    ┌─────────┬──────────┬─────────┬──────────┐
    │ GC #N-3 │ GC #N-2  │ GC #N-1 │ GC #N    │
    │  45ms   │  52ms    │  48ms   │  51ms    │
    └─────────┴──────────┴─────────┴──────────┘
          ↑ 古いデータほど重みが小さい（減衰）

  予測に使われる要素:
    - リージョンあたりの Evacuation 時間
    - RSet スキャン時間
    - オブジェクトコピー時間
    - 参照更新時間
    - GC Root スキャン時間

  予測結果 → 次の GC で処理するリージョン数を決定
```

### 10.3 動的調整の対象

G1GC は停止時間目標を満たすために以下を動的に調整する。

| 調整対象 | 方法 | トレードオフ |
|---|---|---|
| Eden リージョン数 | 少なくすれば Young GC が軽くなる | GC 頻度が増える |
| Mixed GC で回収する Old リージョン数 | 少なくすれば停止時間が短くなる | 回収量が減る |
| Survivor → Old の昇格閾値 | 動的に調整 | 早期昇格は Old 増加を招く |
| Young GC の頻度 | Eden を小さくすれば頻度が上がる | スループット低下 |

```
MaxGCPauseMillis のトレードオフ:

  小さい値 (例: 50ms):
    + 停止時間が短い（レイテンシ重視）
    - GC 頻度が増える
    - 1回の回収量が少ない
    - Full GC のリスクが上がる可能性

  大きい値 (例: 500ms):
    + 1回の回収量が多い（スループット重視）
    + GC 頻度が減る
    - 停止時間が長い

  ┌──────────────────────────────────────────────────┐
  │              MaxGCPauseMillis                     │
  │  ←── 小さい ──────────────────── 大きい ──→      │
  │  レイテンシ重視                スループット重視    │
  │  GC 頻度: 高                   GC 頻度: 低       │
  │  1回の回収量: 少               1回の回収量: 多    │
  └──────────────────────────────────────────────────┘
```

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/g1/g1Policy.cpp` -- 停止時間予測と Eden サイズの決定
- `src/hotspot/share/gc/g1/g1Analytics.cpp` -- GC 統計の減衰平均計算

---

## 第11章: String Deduplication

### 11.1 文字列の重複問題

多くの Java アプリケーションでは、同じ内容の `String` オブジェクトがヒープに大量に存在する。例えば、Web アプリケーションの HTTP ヘッダ名（"Content-Type", "Accept" 等）は無数のリクエストで繰り返し生成される。

```
典型的なヒープ内の文字列重複:

  String #1: value → char[] {'H','e','l','l','o'}  ←┐
  String #2: value → char[] {'H','e','l','l','o'}  ←┤ 同じ内容！
  String #3: value → char[] {'H','e','l','l','o'}  ←┘
  String #4: value → char[] {'W','o','r','l','d'}
  String #5: value → char[] {'W','o','r','l','d'}  ← 同じ内容！

  ※ String.intern() はプログラマーが明示的に呼ぶ必要があるが、
    String Deduplication は GC が自動的に行う。
```

### 11.2 String Deduplication の仕組み

G1GC の String Deduplication は、**String オブジェクトの内部 `byte[]`（Java 9+ では Compact Strings）** を共有化する。String オブジェクト自体は残る（identity は変わらない）が、内部のバッキング配列が共有される。

```
Deduplication の前後:

  Before:
  String #1 ──→ byte[] {'H','e','l','l','o'}   40 bytes
  String #2 ──→ byte[] {'H','e','l','l','o'}   40 bytes
  String #3 ──→ byte[] {'H','e','l','l','o'}   40 bytes
                                          合計: 120 bytes

  After (Deduplication):
  String #1 ──→ byte[] {'H','e','l','l','o'}   40 bytes
  String #2 ──┘                                 ↑ 共有
  String #3 ──┘                                 ↑ 共有
                                          合計: 40 bytes（67% 削減）

  ※ String オブジェクト自体はそのまま（3つとも存在）
  ※ equals() や == の挙動は一切変わらない
  ※ String.intern() との違い: intern は String オブジェクト自体を共有化する
```

**処理タイミング:** Young GC で Survivor に移動したタイミング（つまり少なくとも 1 回の GC を生き延びた文字列）で Deduplication キューにエントリが追加され、バックグラウンドスレッドが非同期で重複を解消する。

### 11.3 有効化と監視

```bash
# String Deduplication を有効化
java -XX:+UseStringDeduplication -jar app.jar

# 統計情報を表示
java -XX:+UseStringDeduplication -Xlog:stringdedup*=debug -jar app.jar

# Deduplication 対象の最小 age を指定（デフォルト: 3）
java -XX:+UseStringDeduplication -XX:StringDeduplicationAgeThreshold=3 -jar app.jar
```

**有効な場面:**
- 大量の重複文字列があるアプリケーション（Web アプリ、XML/JSON パーサー等）
- ヒープ使用量を削減したい場合

**注意点:**
- CPU オーバーヘッドは小さいがゼロではない
- Young GC の停止時間がわずかに増加する可能性がある
- Java 18 以降、ZGC と Shenandoah でも String Deduplication が利用可能

---

## 第12章: リージョンピンニング (JEP 423)

### 12.1 JNI Critical Region の問題

JNI の `GetPrimitiveArrayCritical` / `GetStringCritical` は、Java 配列/文字列の内部バッファへの直接ポインタを返す。GC がオブジェクトを移動するとこのポインタが無効になるため、従来の G1GC は **JNI Critical Region 中は GC 全体をブロック** していた。

```
従来の問題 (JEP 423 以前):

  スレッド A: GetPrimitiveArrayCritical(arr)
    → JNI Critical Region に突入
    → この間、G1GC は GC を開始できない（全スレッドがブロック）

  スレッド B, C, D: 通常のアプリケーション処理
    → Eden が満杯になっても GC できない
    → 結果: Allocation Stall（割り当て待ち）

  Critical Region が長時間続くと:
    ┌─────────────────────────────────────────┐
    │ スレッド A: ───[Critical Region]──────   │
    │ GC:       待機...待機...待機...          │
    │ スレッド B: ───[割り当て待ち]──────       │
    │ スレッド C: ───[割り当て待ち]──────       │
    └─────────────────────────────────────────┘
```

### 12.2 リージョンピンニングによる解決

JEP 423 (Java 22) では、JNI Critical Region で参照されているオブジェクトが存在する**リージョンだけ**を「ピン留め（pin）」し、そのリージョンは Evacuation 対象から除外する。他のリージョンは通常通り GC が進行する。

```
Region Pinning (JEP 423):

  スレッド A: GetPrimitiveArrayCritical(arr)
    → arr が存在するリージョン R3 をピン留め

  GC 発生時:
  ┌─────┬─────┬─────┬─────┬─────┬─────┐
  │  E  │  E  │  E  │  O  │  O  │  F  │
  │CSet │CSet │ 📌  │     │     │     │
  │     │     │pin! │     │     │     │
  └─────┴─────┴─────┴─────┴─────┴─────┘
    回収 ✓  回収 ✓  スキップ  ← R3 はピン留めされているので
                              Evacuation 対象外。他はすべて正常に回収。

  結果:
  - GC は R3 以外を通常通り回収できる
  - JNI Critical Region が GC 全体をブロックしなくなった
  - レイテンシの大幅な改善
```

### 12.3 Java 25 での状態

JEP 423 は Java 22 で正式導入され、Java 25 では安定した機能として動作している。特別な有効化フラグは不要（G1GC を使用すれば自動的に有効）。

**影響を受けるユースケース:**
- JNI を多用するアプリケーション（ネイティブライブラリ連携）
- `GetPrimitiveArrayCritical` を使う I/O ライブラリ
- FFM API (Foreign Function & Memory API) との連携

HotSpot C++ 層での実装:
- `src/hotspot/share/gc/g1/g1CollectedHeap.cpp` -- ピン留めリージョンの管理
- `src/hotspot/share/gc/g1/g1EvacInfo.hpp` -- Evacuation 時のピン判定

---

## 第13章: G1GC のメモリオーバーヘッド

### 13.1 オーバーヘッドの内訳

G1GC はリージョン単位の管理と正確な参照追跡のために、純粋なヒープ使用量以外にメモリを消費する。

```
G1GC のメモリオーバーヘッド:

  ┌──────────────────────────────────────────────────────┐
  │ コンポーネント      │ 概算サイズ        │ 説明          │
  ├──────────────────────────────────────────────────────┤
  │ Remembered Set     │ ヒープの 1〜20%    │ 最大のコスト   │
  │ Card Table         │ ヒープの 0.2%     │ 512Bに1バイト  │
  │ Bitmap (marking)   │ ヒープの 1.5%     │ 2ビット/オブジェクト│
  │ リージョン管理構造  │ 固定（小さい）     │ 数KB/リージョン │
  │ SATB バッファ      │ スレッド数に比例   │ 各スレッドに1バッファ│
  │ Dirty Card Queue   │ スレッド数に比例   │ 各スレッドに1キュー│
  └──────────────────────────────────────────────────────┘

  例: 8GB ヒープの場合
  ┌────────────────────────────────────────────────────┐
  │ Card Table:        8GB / 512B × 1B ≈ 16MB         │
  │ Marking Bitmap:    8GB × 1.5%       ≈ 120MB       │
  │ RSet (典型):       8GB × 5%         ≈ 400MB       │
  │ その他:                              ≈ 数十MB      │
  │ ─────────────────────────────────                  │
  │ 合計オーバーヘッド:                  ≈ 500〜600MB   │
  │                                     (ヒープの約7%)  │
  └────────────────────────────────────────────────────┘
```

### 13.2 オーバーヘッドを減らすテクニック

1. **参照の局所性を高める:** オブジェクトの参照が同一リージョン内に収まれば、リージョン間参照が減り RSet が小さくなる
2. **リージョンサイズを大きくする:** リージョン数が減るため管理構造が小さくなる（ただしリージョン内の無駄が増える可能性）
3. **Humongous Object を減らす:** 不必要に大きな配列を避ける
4. **不要な参照を早めに切る:** `null` 代入で参照を明示的に切ることで、RSet エントリが不要になる

---

## 第14章: G1GC チューニングパラメータ一覧

### 14.1 基本パラメータ

| パラメータ | デフォルト | 説明 |
|---|---|---|
| `-XX:+UseG1GC` | Java 9+ で ON | G1GC を有効化 |
| `-XX:MaxGCPauseMillis=<ms>` | 200 | 停止時間目標（ミリ秒） |
| `-XX:G1HeapRegionSize=<size>` | 自動 | リージョンサイズ（1MB〜32MB、2のべき乗） |
| `-Xms` / `-Xmx` | OS 依存 | 最小/最大ヒープサイズ |
| `-XX:ParallelGCThreads=<n>` | CPU コア数 | STW GC のワーカースレッド数 |
| `-XX:ConcGCThreads=<n>` | ParallelGCThreads/4 | Concurrent Marking のスレッド数 |

### 14.2 Concurrent Marking 関連

| パラメータ | デフォルト | 説明 |
|---|---|---|
| `-XX:InitiatingHeapOccupancyPercent=<n>` | 45 | Concurrent Marking 開始閾値（Adaptive IHOP の初期値） |
| `-XX:+G1UseAdaptiveIHOP` | ON | Adaptive IHOP を有効化 |
| `-XX:G1ReservePercent=<n>` | 10 | Evacuation 用の予約ヒープ割合（%） |

### 14.3 Mixed GC 関連

| パラメータ | デフォルト | 説明 |
|---|---|---|
| `-XX:G1MixedGCCountTarget=<n>` | 8 | Mixed GC の目標実行回数 |
| `-XX:G1HeapWastePercent=<n>` | 5 | この割合以下のゴミで Mixed GC 終了（%） |
| `-XX:G1MixedGCLiveThresholdPercent=<n>` | 85 | この生存率以上のリージョンは Mixed GC 対象外（%） |
| `-XX:G1OldCSetRegionThresholdPercent=<n>` | 10 | 1回の Mixed GC で回収する Old リージョンの上限（ヒープの%） |

### 14.4 Humongous 関連

| パラメータ | デフォルト | 説明 |
|---|---|---|
| `-XX:G1HeapRegionSize=<size>` | 自動 | リージョンサイズを大きくすると Humongous 閾値が上がる |

Humongous 閾値は常にリージョンサイズの 50% であり、直接変更はできない。リージョンサイズを変更することで間接的に制御する。

### 14.5 その他の重要パラメータ

| パラメータ | デフォルト | 説明 |
|---|---|---|
| `-XX:G1NewSizePercent=<n>` | 5 | Young Generation の最小サイズ（ヒープの%） |
| `-XX:G1MaxNewSizePercent=<n>` | 60 | Young Generation の最大サイズ（ヒープの%） |
| `-XX:MaxTenuringThreshold=<n>` | 15 | Survivor → Old 昇格までの最大 age |
| `-XX:+UseStringDeduplication` | OFF | String Deduplication の有効化 |
| `-XX:StringDeduplicationAgeThreshold=<n>` | 3 | Deduplication 対象の最小 age |
| `-XX:G1ConcRefinementThreads=<n>` | 自動 | Refinement スレッド数 |
| `-XX:+G1EagerReclaimHumongousObjects` | ON | Humongous Eager Reclaim の有効化 |

**チューニングの原則:**

```
G1GC チューニングの優先順位:

  1. まずは MaxGCPauseMillis だけを調整する（最も効果的）
  2. ヒープサイズ (-Xmx) を適切に設定する
  3. GC ログを分析して問題を特定する
  4. 問題が特定されたら、該当するパラメータだけを調整する
  5. 一度に変更するのは 1 パラメータだけ（効果を測定するため）

  ※ パラメータを大量に指定するのは逆効果。
    G1GC の自動調整機能を信頼すること。
```

---

## 第15章: G1GC のパフォーマンス特性

### 15.1 G1GC が向いているワークロード

```
G1GC の得意分野:

  ✓ 汎用的な Web アプリケーション（Spring Boot 等）
    → 200ms 以内の停止時間で十分な場合が多い

  ✓ ヒープサイズ 4GB 〜 32GB の中規模アプリケーション
    → G1GC の設計が最も活きるレンジ

  ✓ レイテンシとスループットのバランスが求められる場合
    → MaxGCPauseMillis で柔軟に調整可能

  ✓ オブジェクトのライフサイクルが多様な場合
    → 短命・長寿命が混在するワークロードに適応

  ✓ 「とりあえずチューニングなしで使いたい」場合
    → デフォルト設定で広範なワークロードに対応
```

### 15.2 G1GC が苦手なワークロード

```
G1GC の苦手分野:

  △ サブミリ秒のレイテンシが必要な場合
    → ZGC (JEP 377) を推奨
    → ZGC は 10ms 以下の停止時間を実現

  △ 非常に小さなヒープ（< 1GB）
    → Serial GC の方がオーバーヘッドが少ない
    → G1GC のリージョン管理が相対的に高コスト

  △ 最大スループットだけが重要で停止時間を気にしない場合
    → Parallel GC の方がスループットが高い可能性

  △ 100GB+ の超大規模ヒープ
    → ZGC の方が設計上適している
    → G1GC の Full GC は超大規模ヒープでは非常に重い

  △ Humongous Object が大量に発生するワークロード
    → リージョンサイズの調整が必要
    → それでも問題が解決しない場合は ZGC を検討
```

### 15.3 他の GC との比較

```
Java 25 で利用可能な GC の比較:

  ┌──────────────┬────────────┬────────────┬──────────────┐
  │ GC           │ 停止時間    │ スループット │ 推奨ヒープ    │
  ├──────────────┼────────────┼────────────┼──────────────┤
  │ Serial GC    │ 長い       │ 低い        │ < 1GB        │
  │              │            │ (シングルCPU │ (組み込み向け)│
  │              │            │  では最速)   │              │
  ├──────────────┼────────────┼────────────┼──────────────┤
  │ Parallel GC  │ 中程度     │ 最高        │ 1〜32GB      │
  │              │ (予測困難)  │             │ (バッチ処理)  │
  ├──────────────┼────────────┼────────────┼──────────────┤
  │ G1GC         │ 制御可能   │ 高い        │ 4〜32GB      │
  │ (デフォルト)  │ (200ms目標)│             │ (汎用)       │
  ├──────────────┼────────────┼────────────┼──────────────┤
  │ ZGC          │ 極短       │ やや低い     │ 8GB〜16TB   │
  │              │ (< 10ms)  │ (G1比)      │ (大規模)     │
  ├──────────────┼────────────┼────────────┼──────────────┤
  │ Shenandoah   │ 極短       │ やや低い     │ 数GB〜数十GB │
  │              │ (< 10ms)  │             │ (中〜大規模) │
  ├──────────────┼────────────┼────────────┼──────────────┤
  │ Epsilon      │ なし       │ 最高        │ 任意         │
  │ (No-Op GC)   │ (GCしない) │ (GCなし)    │ (テスト用)   │
  └──────────────┴────────────┴────────────┴──────────────┘

  GC 選定フローチャート:
    ┌─ 停止時間 < 10ms 必須?
    │   ├─ YES → ZGC / Shenandoah
    │   └─ NO
    │       ├─ ヒープ < 1GB?
    │       │   ├─ YES → Serial GC
    │       │   └─ NO
    │       │       ├─ スループット最優先?
    │       │       │   ├─ YES → Parallel GC
    │       │       │   └─ NO → G1GC (デフォルト)
    │       │       └─────────────────────
    └─────────────────────────────────
```

---

## 第16章: リージョン状態遷移図

```
G1GC リージョン状態遷移図（完全版）:

  ┌─────────┐
  │  Free   │ ← 空きリージョン
  └────┬────┘
       │
       ├──────────────────────── TLAB 割り当て
       │                          ↓
       │                    ┌─────────┐
       │                    │  Eden   │
       │                    └────┬────┘
       │                         │
       │          Young GC で生存者をコピー
       │                         │
       │                    ┌────↓────┐
       │                    │Survivor │
       │                    └────┬────┘
       │                         │
       │              age >= 閾値 │ age < 閾値
       │              ┌──────────┤
       │              ↓          ↓
       │         ┌─────────┐   Survivor に再コピー（age+1）
       │         │   Old   │
       │         └────┬────┘
       │              │
       │     Mixed GC で選択的回収
       │              │
       ├──────────────┘
       │
       ├──────────────────────── Humongous 割り当て（>= 50% region）
       │                          ↓
       │                    ┌───────────┐
       │                    │ Humongous │
       │                    │ (SH + CH) │
       │                    └─────┬─────┘
       │                          │
       │            Eager Reclaim / Concurrent Marking 後
       │                          │
       ├──────────────────────────┘
       │
       ↓
  ┌─────────┐
  │  Free   │ ← 回収後、再び Free に戻る
  └─────────┘

  ※ いずれのフェーズでも Free 枯渇時は Full GC が発生
     Full GC: ヒープ全体を Mark-Sweep-Compact → 可能な限り Free を回復
```

---

## 実験用 Java コード

以下の実験用 Java コードで、G1GC の動作を観察できる。

### GcLifecycleDemo -- GC ライフサイクルの観察

[src/main/java/gc/GcLifecycleDemo.java](../../../src/main/java/gc/GcLifecycleDemo.java)

GC の世代管理（Eden → Survivor → Old）の流れを段階的に観察するデモ。オブジェクトの生成・保持・破棄のパターンを変えることで、Young GC と Old GC の動作の違いを体感できる。

```bash
java -Xmx128m -verbose:gc src/main/java/gc/GcLifecycleDemo.java
```

### GcRootsDemo -- GC Roots の可視化

[src/main/java/gc/GcRootsDemo.java](../../../src/main/java/gc/GcRootsDemo.java)

ローカル変数、static フィールド、スレッド参照が GC Root としてオブジェクトを保護する仕組みを実験で確認する。

### GcLogAnalyzer -- GC イベントのリアルタイム解析

[src/main/java/gc/GcLogAnalyzer.java](../../../src/main/java/gc/GcLogAnalyzer.java)

`GarbageCollectorMXBean` と `NotificationEmitter` を使って GC 通知を購読し、GC の種類・停止時間・メモリ使用量をリアルタイム表示する。G1GC の各フェーズ（Young GC, Mixed GC）の発生パターンを観察するのに最適。

```bash
java -Xmx128m src/main/java/gc/GcLogAnalyzer.java
```

### MemoryLiveReporter -- メモリ消費リアルタイム実況

[src/main/java/gc/MemoryLiveReporter.java](../../../src/main/java/gc/MemoryLiveReporter.java)

ヒープ使用量と GC イベントをバーグラフ付きでリアルタイムに実況中継するプログラム。Eden / Survivor / Old Gen の使用率変化を視覚的に追跡できる。

```bash
java -Xmx256m src/main/java/gc/MemoryLiveReporter.java
```

### HeapGcObserver -- ヒープと GC の統合観察

[src/main/java/memory/HeapGcObserver.java](../../../src/main/java/memory/HeapGcObserver.java)

ヒープの使用状況と GC の動作を統合的に観察するプログラム。メモリプールごとの使用量変化を追跡する。

```bash
java -Xmx64m -verbose:gc src/main/java/memory/HeapGcObserver.java
```

### G1GC 固有のログを詳しく見る

```bash
# G1GC の全ログを最大レベルで出力
java -Xlog:gc*=debug -Xmx256m src/main/java/gc/GcLifecycleDemo.java

# RSet の詳細
java -Xlog:gc+remset*=trace -Xmx256m src/main/java/gc/GcLifecycleDemo.java

# Humongous 割り当ての追跡
java -Xlog:gc+humongous=debug -Xmx256m src/main/java/gc/OomScenarios.java

# Concurrent Marking の詳細
java -Xlog:gc+marking=debug -Xmx256m src/main/java/gc/GcLifecycleDemo.java

# Evacuation の詳細
java -Xlog:gc+ergo+cset=debug -Xmx256m src/main/java/gc/GcLifecycleDemo.java
```

---

## HotSpot C++ ソースパス一覧

| 機能 | ソースパス |
|---|---|
| G1GC メイン | `src/hotspot/share/gc/g1/g1CollectedHeap.cpp` |
| Concurrent Marking | `src/hotspot/share/gc/g1/g1ConcurrentMark.cpp` |
| Concurrent Marking スレッド | `src/hotspot/share/gc/g1/g1ConcurrentMarkThread.cpp` |
| Remembered Set | `src/hotspot/share/gc/g1/g1RemSet.cpp` |
| Card Table | `src/hotspot/share/gc/g1/g1CardTable.cpp` |
| Write Barrier | `src/hotspot/share/gc/g1/g1BarrierSet.cpp` |
| SATB キュー | `src/hotspot/share/gc/g1/g1SATBMarkQueueSet.cpp` |
| Concurrent Refinement | `src/hotspot/share/gc/g1/g1ConcurrentRefine.cpp` |
| Dirty Card キュー | `src/hotspot/share/gc/g1/g1DirtyCardQueue.cpp` |
| Collection Set | `src/hotspot/share/gc/g1/g1CollectionSet.cpp` |
| GC ポリシー | `src/hotspot/share/gc/g1/g1Policy.cpp` |
| GC 統計 (Analytics) | `src/hotspot/share/gc/g1/g1Analytics.cpp` |
| Adaptive IHOP | `src/hotspot/share/gc/g1/g1IHOPControl.cpp` |
| リージョン管理 | `src/hotspot/share/gc/g1/heapRegion.cpp` |
| リージョン種別定義 | `src/hotspot/share/gc/g1/heapRegionType.hpp` |
| リージョンサイズ計算 | `src/hotspot/share/gc/g1/g1HeapRegionSize.cpp` |
| TLAB | `src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp` |
| 年齢テーブル | `src/hotspot/share/gc/shared/ageTable.cpp` |
| Mark Word（オブジェクトヘッダ） | `src/hotspot/share/oops/markWord.hpp` |

---

## 参考リンク

### JEP

| JEP | 内容 | Java バージョン |
|---|---|---|
| [JEP 248](https://openjdk.org/jeps/248) | G1GC をデフォルト化 | Java 9 |
| [JEP 307](https://openjdk.org/jeps/307) | Full GC の並列化 | Java 10 |
| [JEP 344](https://openjdk.org/jeps/344) | 未使用メモリの OS 返却 | Java 12 |
| [JEP 345](https://openjdk.org/jeps/345) | NUMA-Aware メモリ割り当て | Java 14 |
| [JEP 346](https://openjdk.org/jeps/346) | Abortable Mixed Collections | Java 12 |
| [JEP 423](https://openjdk.org/jeps/423) | Region Pinning | Java 22 |

### 書籍・ドキュメント

- [Oracle - G1GC documentation](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html) -- G1GC 公式ドキュメント
- [Oracle - Garbage Collection Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/) -- 公式チューニングガイド
- [HotSpot GC Wiki](https://wiki.openjdk.org/display/HotSpot/GC) -- OpenJDK GC Wiki
- Detlefs et al., "Garbage-First Garbage Collection" (2004) -- G1GC の原論文

### Zulu との差分

Azul Zulu (OSS 版) は OpenJDK mainline のビルドであり、G1GC の実装は HotSpot そのものである。本ドキュメントの内容はすべてそのまま適用される。

ただし、Azul の商用製品 **Azul Platform Prime（旧 Zing）** では、独自の **C4 GC (Continuously Concurrent Compacting Collector)** を搭載しており、以下の点で G1GC と大きく異なる。

| 比較項目 | G1GC | Azul C4 GC |
|---|---|---|
| コンパクション | STW で実行 | **完全に並行** |
| Full GC | 発生し得る（STW） | **発生しない**（設計上排除） |
| 最大停止時間 | 数百 ms 〜数秒 | **常に 1ms 以下** |
| 対応ヒープ | 数 GB 〜数十 GB | **数百 GB 〜数 TB** |
| ライセンス | OSS (GPLv2) | 商用（有償） |
