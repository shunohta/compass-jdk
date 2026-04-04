# JDK ディストリビューション概要 -- 選定ガイドと比較

- [概要](#概要)
- [1. OpenJDK とは何か](#1-openjdk-とは何か)
  - [1.1 OpenJDK の歴史](#11-openjdk-の歴史)
  - [1.2 ガバナンス構造](#12-ガバナンス構造)
  - [1.3 ソースコードとビルドの関係](#13-ソースコードとビルドの関係)
  - [1.4 「OpenJDK ベース」とはどういう意味か](#14-openjdk-ベースとはどういう意味か)
- [2. JDK ディストリビューションの全体像](#2-jdk-ディストリビューションの全体像)
  - [2.1 なぜ複数のディストリビューションが存在するのか](#21-なぜ複数のディストリビューションが存在するのか)
  - [2.2 TCK と Java SE 準拠](#22-tck-と-java-se-準拠)
  - [2.3 共通する部分と異なる部分](#23-共通する部分と異なる部分)
- [3. 主要ディストリビューション一覧と詳細](#3-主要ディストリビューション一覧と詳細)
  - [3.1 ディストリビューション早見表](#31-ディストリビューション早見表)
  - [3.2 Oracle JDK](#32-oracle-jdk)
  - [3.3 Oracle OpenJDK builds](#33-oracle-openjdk-builds)
  - [3.4 Azul Zulu](#34-azul-zulu)
  - [3.5 Azul Platform Prime (旧 Zing)](#35-azul-platform-prime-旧-zing)
  - [3.6 Eclipse Temurin (旧 AdoptOpenJDK)](#36-eclipse-temurin-旧-adoptopenjdk)
  - [3.7 Amazon Corretto](#37-amazon-corretto)
  - [3.8 Microsoft Build of OpenJDK](#38-microsoft-build-of-openjdk)
  - [3.9 Red Hat Build of OpenJDK](#39-red-hat-build-of-openjdk)
  - [3.10 SAP Machine](#310-sap-machine)
  - [3.11 IBM Semeru Runtime](#311-ibm-semeru-runtime)
  - [3.12 GraalVM](#312-graalvm)
  - [3.13 Liberica JDK](#313-liberica-jdk)
  - [3.14 Alibaba Dragonwell](#314-alibaba-dragonwell)
  - [3.15 Tencent Kona](#315-tencent-kona)
  - [3.16 その他のディストリビューション](#316-その他のディストリビューション)
- [4. ライセンスの比較](#4-ライセンスの比較)
  - [4.1 主要ライセンス形態](#41-主要ライセンス形態)
  - [4.2 ライセンス比較表](#42-ライセンス比較表)
  - [4.3 Oracle ライセンスの変遷](#43-oracle-ライセンスの変遷)
  - [4.4 本番環境で無償で使えるディストリビューション](#44-本番環境で無償で使えるディストリビューション)
- [5. サポート期間の比較](#5-サポート期間の比較)
  - [5.1 LTS の概念](#51-lts-の概念)
  - [5.2 LTS サポート期間比較表](#52-lts-サポート期間比較表)
  - [5.3 Java 25 (LTS) のサポート終了予定](#53-java-25-lts-のサポート終了予定)
- [6. JVM の違い](#6-jvm-の違い)
  - [6.1 JVM 実装の分類](#61-jvm-実装の分類)
  - [6.2 JVM 特性比較表](#62-jvm-特性比較表)
  - [6.3 GC の違い](#63-gc-の違い)
  - [6.4 JIT コンパイラの違い](#64-jit-コンパイラの違い)
- [7. 選定ガイド -- どう選ぶか](#7-選定ガイド----どう選ぶか)
  - [7.1 選定フローチャート](#71-選定フローチャート)
  - [7.2 ユースケース別の推奨](#72-ユースケース別の推奨)
  - [7.3 クラウドプロバイダー別の推奨](#73-クラウドプロバイダー別の推奨)
  - [7.4 コスト要件別](#74-コスト要件別)
  - [7.5 パフォーマンス要件別](#75-パフォーマンス要件別)
- [8. 移行の注意点](#8-移行の注意点)
  - [8.1 移行の基本方針](#81-移行の基本方針)
  - [8.2 注意すべき差分](#82-注意すべき差分)
  - [8.3 バージョンアップ時の互換性](#83-バージョンアップ時の互換性)
- [参考リンク](#参考リンク)

---

## 概要

本ドキュメントは、Java 開発者が直面する「どの JDK ディストリビューションを選ぶべきか」という問いに答えるための包括的ガイドである。OpenJDK の歴史とガバナンスから始め、主要ディストリビューションの特徴・ライセンス・サポート期間を比較し、ユースケース別の選定指針を提供する。

**基準バージョン:** Java 25（2025年9月 GA、LTS）

**対象ランタイム:** Azul Zulu 25 (OpenJDK 25 ベース / HotSpot VM)

**位置づけ:** このドキュメントはディストリビューション選定の「地図」であり、JVM 内部の詳細は以下の既存ドキュメントに委譲する。

| 対象 | 詳細ドキュメント |
|------|-----------------|
| JVM 全体像 | [jvm-overview.md](jvm-overview.md) |
| OpenJDK プロジェクト | [openjdk-projects-overview.md](openjdk-projects-overview.md) |
| GC 全般 | [gc-deep-dive.md](memory/gc-deep-dive.md) |
| G1GC | [g1gc-deep-dive.md](memory/g1gc-deep-dive.md) |
| ZGC | [zgc-deep-dive.md](memory/zgc-deep-dive.md) |

---

## 1. OpenJDK とは何か

### 1.1 OpenJDK の歴史

Java は 1995年に Sun Microsystems が発表したプログラミング言語・プラットフォームである。長らくプロプライエタリなソフトウェアとして開発されていたが、2006年に Sun が Java をオープンソース化する決断を下し、2007年に **OpenJDK** (Open Java Development Kit) として GPL v2 + Classpath Exception ライセンスのもとで公開された。

```
Java の所有権の変遷
====================

1995        2006-2007       2010           現在
  |            |              |              |
  v            v              v              v
Sun         Sun が          Oracle が      Oracle が開発を
Microsystems  OpenJDK を     Sun を買収     リードしつつ
が Java を    公開           Oracle JDK     コミュニティが
発表         (GPLv2+CE)      と OpenJDK     協力して開発
                             の二本立て
```

**主要な出来事:**

| 年 | 出来事 |
|----|--------|
| 1995 | Sun Microsystems が Java 1.0 を発表 |
| 2006 | Sun が Java のオープンソース化を宣言（JavaOne） |
| 2007 | OpenJDK プロジェクト発足。Java SE 7 のリファレンス実装に |
| 2010 | Oracle が Sun Microsystems を買収。Java の管理権を取得 |
| 2017 | Java 9 リリース。6ヶ月リリースサイクルへ移行 |
| 2018 | Oracle JDK の無償利用が制限（Java 11 以降）。多数の代替ディストリビューションが台頭 |
| 2021 | Oracle JDK が NFTC ライセンスに移行（Java 17 以降、無償利用が再び可能に） |
| 2023 | Java 21 LTS リリース。仮想スレッド等が正式化 |
| 2025 | Java 25 LTS リリース。現在の最新 LTS |

### 1.2 ガバナンス構造

Java プラットフォームのガバナンスは、複数の組織・プロセスが関与する多層構造になっている。

```
Java ガバナンスの全体構造
==========================

┌──────────────────────────────────────────────────────┐
│                    JCP (Java Community Process)       │
│  Java SE 仕様の策定プロセス。JSR (Java Specification  │
│  Request) を通じて仕様を標準化する。                   │
│  Executive Committee が最終承認を行う。                │
└────────────────────────┬─────────────────────────────┘
                         │ 仕様を定義
                         v
┌──────────────────────────────────────────────────────┐
│              Java SE Specification                     │
│  言語仕様 (JLS) + VM 仕様 (JVMS) + API 仕様          │
│  TCK でこの仕様への準拠を検証する                      │
└────────────────────────┬─────────────────────────────┘
                         │ リファレンス実装
                         v
┌──────────────────────────────────────────────────────┐
│              OpenJDK コミュニティ                       │
│                                                        │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │ Oracle   │ │ Red Hat  │ │ SAP      │ │ Azul     │ │
│  │ (Lead)   │ │          │ │          │ │          │ │
│  └─────────┘ └──────────┘ └──────────┘ └──────────┘ │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │ Amazon  │ │ Microsoft│ │ Google   │ │ IBM      │ │
│  └─────────┘ └──────────┘ └──────────┘ └──────────┘ │
│         ... 多数の企業・個人コントリビュータ ...       │
│                                                        │
│  JEP (JDK Enhancement Proposal) プロセスで機能を提案   │
│  Mercurial → Git (GitHub) でソース管理                 │
└──────────────────────────────────────────────────────┘
```

**各組織の役割:**

| 組織・プロセス | 役割 |
|---------------|------|
| **JCP** (Java Community Process) | Java SE 仕様の標準化プロセス。JSR を通じて仕様を策定 |
| **OpenJDK コミュニティ** | Java SE のリファレンス実装を開発するオープンソースプロジェクト |
| **Oracle** | OpenJDK の最大のコントリビュータかつプロジェクトリード。JCP の Executive Committee メンバー |
| **JEP** (JDK Enhancement Proposal) | OpenJDK への機能追加を提案・管理するプロセス。JCP の JSR よりも軽量 |
| **TCK** (Technology Compatibility Kit) | Java SE 仕様への準拠を検証するテストスイート。Oracle が管理 |

### 1.3 ソースコードとビルドの関係

OpenJDK のソースコードは単一のリポジトリ（`https://github.com/openjdk/jdk`）で管理されている。各ディストリビューションはこのソースコードを基に、独自のビルドパイプラインでバイナリを生成する。

```
OpenJDK ソースコードからバイナリができるまで
=============================================

                    ┌─────────────────┐
                    │  OpenJDK ソース  │
                    │  (GitHub)       │
                    │  openjdk/jdk    │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              v              v              v
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ Oracle   │  │ Azul     │  │ Eclipse  │  ...
        │ 独自     │  │ 独自     │  │ Adoptium │
        │ パッチ   │  │ パッチ   │  │ パッチ   │
        └────┬─────┘  └────┬─────┘  └────┬─────┘
             │              │              │
             v              v              v
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ ビルド   │  │ ビルド   │  │ ビルド   │
        │ + TCK    │  │ + TCK    │  │ + TCK    │
        │ テスト   │  │ テスト   │  │ テスト   │
        └────┬─────┘  └────┬─────┘  └────┬─────┘
             │              │              │
             v              v              v
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ Oracle   │  │ Azul     │  │ Eclipse  │
        │ JDK      │  │ Zulu     │  │ Temurin  │
        │ バイナリ │  │ バイナリ │  │ バイナリ │
        └──────────┘  └──────────┘  └──────────┘
```

このプロセスは Linux ディストリビューション（Ubuntu, Fedora, Arch が同じ Linux カーネルソースから独自のカーネルをビルドする）と本質的に同じ構造である。

### 1.4 「OpenJDK ベース」とはどういう意味か

「OpenJDK ベース」とは、以下の条件を満たすことを意味する:

1. **ソースコードの出自:** OpenJDK リポジトリのソースコードをベースにしている
2. **仕様準拠:** TCK テストに合格し、Java SE 仕様に準拠している（ことが多い）
3. **互換性:** Java SE API の動作が仕様通りである

ただし「OpenJDK ベース」であっても、以下の点でディストリビューションごとに異なる:

- **独自パッチ:** セキュリティ修正、バグ修正、パフォーマンス改善
- **ビルドオプション:** 暗号プロバイダー、フォントレンダリング、ネイティブライブラリ
- **パッケージング:** インストーラ、ディレクトリ構造、同梱ツール
- **サポート体制:** パッチ提供期間、セキュリティアップデート頻度

---

## 2. JDK ディストリビューションの全体像

### 2.1 なぜ複数のディストリビューションが存在するのか

OpenJDK は「ソースコード」を提供するプロジェクトであり、エンドユーザーが直接使う「バイナリ」を公式に広く配布する体制を持たない。この構造が、複数のディストリビューションが存在する根本的な理由である。

```
ディストリビューションが複数存在する理由
=========================================

┌────────────────────────────────────────────────────┐
│               各社がディストリビューションを出す動機  │
├────────────────────────────────────────────────────┤
│                                                      │
│  Oracle     → Java エコシステムのリーダーシップ維持   │
│  Amazon     → AWS 上の Java ワークロード最適化        │
│  Microsoft  → Azure 上の Java ワークロード最適化      │
│  Azul       → Java ランタイム専業。パフォーマンス差別化│
│  Red Hat    → RHEL エコシステムへの統合               │
│  IBM        → 独自 VM (OpenJ9) による差別化           │
│  SAP        → SAP 製品群との統合                      │
│  Eclipse    → ベンダー中立のコミュニティ主導ビルド     │
│  Alibaba    → 大規模分散システム向け最適化             │
│  Tencent    → 中国国内市場向け最適化                   │
│  BellSoft   → 組み込み・IoT 向けを含む幅広い対応      │
│                                                      │
└────────────────────────────────────────────────────┘
```

### 2.2 TCK と Java SE 準拠

**TCK (Technology Compatibility Kit)** は、ある JDK 実装が Java SE 仕様に準拠しているかを検証するためのテストスイートである。Oracle が管理しており、TCK に合格したディストリビューションは「Java SE 準拠」を名乗ることができる。

**重要なポイント:**

- TCK テストは約15万件以上のテストケースで構成される
- TCK に合格しないと「Java」の商標を使用できない（Oracle との契約が必要）
- OpenJDK コミュニティの一部として開発していれば、TCK へのアクセスが可能
- TCK 合格は「仕様準拠」を保証するが、「品質」や「パフォーマンス」を保証するものではない

```
TCK の位置づけ
===============

  Java SE 仕様 (JLS + JVMS + API)
       │
       v
  ┌──────────┐     合格      ┌──────────────────────┐
  │   TCK    │ ──────────── > │ "Java SE Compatible" │
  │ テスト   │               │  を名乗れる           │
  │ スイート │ ──────────── > │                        │
  └──────────┘     不合格     │  名乗れない            │
                              └──────────────────────┘

  ※ TCK 合格 ≠ 品質保証
  ※ TCK 合格 ≠ パフォーマンス保証
  ※ TCK 合格 = Java SE 仕様への API/動作の準拠
```

### 2.3 共通する部分と異なる部分

| 項目 | 共通 | ディストリビューション固有 |
|------|------|--------------------------|
| Java SE API | 同一仕様 | - |
| バイトコード形式 | 同一仕様 | - |
| JVM 仕様 | 同一仕様 | - |
| JVM 実装 | - | HotSpot / OpenJ9 / Zing 等 |
| GC 実装 | - | 選択肢や独自 GC の有無 |
| JIT コンパイラ | - | C2 / Graal / Falcon 等 |
| セキュリティパッチ | - | 提供タイミング・期間 |
| 暗号プロバイダー | - | デフォルト設定が異なる場合あり |
| インストーラ・パッケージ形式 | - | MSI / deb / rpm / tar.gz 等 |
| サポート期間 | - | 各ベンダーの方針 |
| 追加ツール | - | Mission Control, Flight Recorder 等 |

---

## 3. 主要ディストリビューション一覧と詳細

### 3.1 ディストリビューション早見表

| # | ディストリビューション | 提供元 | JVM | 無償利用 | TCK | LTS サポート | 主な対象 |
|---|----------------------|--------|-----|---------|-----|-------------|---------|
| 1 | Oracle JDK | Oracle | HotSpot | 条件付き | Yes | 長期 | エンタープライズ |
| 2 | Oracle OpenJDK builds | Oracle | HotSpot | Yes | Yes | 短期 | 開発・検証 |
| 3 | Azul Zulu | Azul Systems | HotSpot | CE: Yes | Yes | 長期 | 汎用 |
| 4 | Azul Platform Prime | Azul Systems | Zing | No | Yes | 長期 | 低レイテンシ |
| 5 | Eclipse Temurin | Eclipse Adoptium | HotSpot | Yes | Yes | 長期 | 汎用 |
| 6 | Amazon Corretto | Amazon | HotSpot | Yes | Yes | 長期 | AWS |
| 7 | Microsoft OpenJDK | Microsoft | HotSpot | Yes | Yes | 長期 | Azure |
| 8 | Red Hat OpenJDK | Red Hat | HotSpot | Yes | Yes | 長期 | RHEL |
| 9 | SAP Machine | SAP | HotSpot | Yes | Yes | 長期 | SAP |
| 10 | IBM Semeru | IBM | OpenJ9 | CE: Yes | Yes | 長期 | IBM Cloud |
| 11 | GraalVM | Oracle | HotSpot+Graal | CE: Yes | Yes | 中期 | Native Image |
| 12 | Liberica JDK | BellSoft | HotSpot | Yes | Yes | 長期 | 組み込み・IoT |
| 13 | Alibaba Dragonwell | Alibaba | HotSpot | Yes | Yes | 長期 | 大規模クラウド |
| 14 | Tencent Kona | Tencent | HotSpot | Yes | Yes | 長期 | 中国市場 |

### 3.2 Oracle JDK

**提供元:** Oracle Corporation

**ライセンス:**
- Java 8u202 以前: BCL (Binary Code License) -- 無償利用可
- Java 8u211 〜 Java 16: OTN (Oracle Technology Network License) -- 商用利用は有償
- Java 17 以降: NFTC (No-Fee Terms and Conditions) -- 条件付き無償利用可
- Java 17 以降（商用サポート付き）: Oracle Java SE Subscription

**特徴:**
- OpenJDK のリファレンス実装に最も近い
- Oracle のセキュリティパッチが最速で適用される
- Java Flight Recorder (JFR) / Java Mission Control (JMC) を同梱
- NFTC ライセンスでは「次の LTS リリース後1年間」まで無償利用可能

**Java 25 対応:** GA と同時にリリース

**対象ユーザー:** エンタープライズ環境で Oracle のサポート契約を結べるユーザー。または NFTC の条件内で利用するユーザー。

**注意点:** NFTC ライセンスの条件は複雑であり、LTS の世代が変わると旧バージョンの利用に制限がかかる可能性がある。利用前にライセンス条文を確認すること。

### 3.3 Oracle OpenJDK builds

**提供元:** Oracle Corporation

**ライセンス:** GPL v2 + Classpath Exception（完全に自由なオープンソース）

**特徴:**
- Oracle が OpenJDK ソースからビルドした公式バイナリ
- `jdk.java.net` から入手可能
- サポート期間が**次のフィーチャーリリースまで**（約6ヶ月）と極めて短い
- LTS という概念がない（Oracle はこのビルドに対して LTS を提供しない）
- 独自パッチは基本的になし（upstream そのまま）

**Java 25 対応:** GA と同時にリリースされるが、Java 26 リリース（2026年3月）後はアップデートなし

**対象ユーザー:** 最新バージョンを常に追いかける開発者。CI/CD での検証用。本番環境には推奨しない。

### 3.4 Azul Zulu

**提供元:** Azul Systems

**ライセンス:**
- **Zulu Community Edition:** 無償。ビルド済みバイナリを自由に利用可能
- **Azul Platform Core (旧 Zulu Enterprise):** 商用サポート付き有償版

**特徴:**
- TCK 認証済みの OpenJDK ビルド
- **業界最多のプラットフォーム対応:** x86, ARM (AArch64), Linux, Windows, macOS, Alpine Linux (musl), Solaris 等
- Java 6 からの全バージョンで Zulu ビルドを提供（レガシー対応に強い）
- 独自のセキュリティパッチや安定性修正を含む
- コンテナ環境での動作を考慮したビルドオプション
- **本プロジェクトの参照ランタイム**

**Java 25 対応:** GA と同時にリリース。LTS として長期サポート

**対象ユーザー:** 幅広いプラットフォームでの動作が必要なユーザー。無償で長期サポートを得たいユーザー。

```
Azul の製品体系
================

┌─────────────────────────────────────────────┐
│                 Azul Systems                 │
├──────────────────┬──────────────────────────┤
│                  │                            │
│  Azul Zulu       │  Azul Platform Prime       │
│  (HotSpot ベース) │  (Zing VM ベース)          │
│                  │                            │
│  ┌────────────┐  │  ┌──────────────────────┐ │
│  │ Community  │  │  │ C4 GC (pauseless)    │ │
│  │ Edition    │  │  │ ReadyNow (AOT)       │ │
│  │ (無償)     │  │  │ Falcon JIT           │ │
│  ├────────────┤  │  │ (商用のみ)            │ │
│  │ Platform   │  │  └──────────────────────┘ │
│  │ Core       │  │                            │
│  │ (商用)     │  │                            │
│  └────────────┘  │                            │
└──────────────────┴──────────────────────────┘
```

### 3.5 Azul Platform Prime (旧 Zing)

**提供元:** Azul Systems

**ライセンス:** 商用ライセンスのみ

**特徴:**
- **独自の JVM (Zing VM)** を使用。HotSpot ベースではない
- **C4 GC (Continuously Concurrent Compacting Collector):** 数百 GB のヒープでもポーズ時間が 1ms 以下を実現するガベージコレクタ
- **ReadyNow:** 学習済みプロファイルを使ってウォームアップ時間を大幅に短縮する AOT 的機能
- **Falcon JIT:** LLVM ベースの独自 JIT コンパイラ。C2 よりも高い最適化性能
- 金融取引システム等、超低レイテンシが求められる環境で採用
- GC の詳細は [gc-deep-dive.md](memory/gc-deep-dive.md) も参照

**Java 25 対応:** 対応版がリリースされる見込み（商用契約ベース）

**対象ユーザー:** レイテンシ要件が極めて厳しい金融・リアルタイムシステム。大規模ヒープ（数十〜数百 GB）を使用するアプリケーション。

### 3.6 Eclipse Temurin (旧 AdoptOpenJDK)

**提供元:** Eclipse Adoptium ワーキンググループ（Eclipse Foundation 傘下）

**ライセンス:** GPL v2 + Classpath Exception

**特徴:**
- **ベンダー中立** のコミュニティ主導ディストリビューション
- AdoptOpenJDK プロジェクトが 2021年に Eclipse Foundation に移管されて誕生
- TCK 認証済み（AQAvit テストスイートによる追加品質検証も実施）
- IBM, Microsoft, Red Hat 等の主要企業がワーキンググループに参加
- Docker Hub の公式 Java イメージとして広く利用
- Linux, Windows, macOS, Alpine Linux (musl) 対応

**Java 25 対応:** GA 後に速やかにリリース。LTS として長期サポート

**対象ユーザー:** ベンダーロックインを避けたいユーザー。コンテナ環境での利用。コミュニティ主導の透明性を重視するユーザー。

### 3.7 Amazon Corretto

**提供元:** Amazon Web Services (AWS)

**ライセンス:** GPL v2 + Classpath Exception（完全無償）

**特徴:**
- AWS が内部で使用している OpenJDK ビルドを外部に公開したもの
- AWS 環境（EC2, Lambda, ECS, EKS 等）での動作に最適化
- Amazon Linux 2 / Amazon Linux 2023 でデフォルトの JDK
- 独自のバグ修正やパフォーマンス改善パッチを含む（upstream にも積極的に還元）
- Corretto Crypto Provider (Amazon-LC) による暗号性能の最適化
- 全プラットフォームで無償、商用利用に制限なし

**Java 25 対応:** GA 後に速やかにリリース。LTS として長期サポート

**対象ユーザー:** AWS 上で Java アプリケーションを運用するユーザー。無償で長期サポートを得たいユーザー。

### 3.8 Microsoft Build of OpenJDK

**提供元:** Microsoft

**ライセンス:** GPL v2 + Classpath Exception（完全無償）

**特徴:**
- Azure 環境（App Service, Spring Apps, Azure Functions 等）での動作に最適化
- Azure 上でデフォルトの JDK として提供
- Windows ARM64 への対応が早い
- Microsoft 独自のバグ修正を含む
- LTS バージョンのみ提供（非 LTS バージョンは提供しない）
- macOS (AArch64/x64), Linux (x64/AArch64), Windows (x64/AArch64) 対応

**Java 25 対応:** LTS のため対応版がリリースされる見込み

**対象ユーザー:** Azure 上で Java アプリケーションを運用するユーザー。

### 3.9 Red Hat Build of OpenJDK

**提供元:** Red Hat (IBM 子会社)

**ライセンス:** GPL v2 + Classpath Exception

**特徴:**
- RHEL (Red Hat Enterprise Linux) のパッケージマネージャから直接インストール可能
- Red Hat の OpenShift 環境との統合
- Red Hat は OpenJDK コミュニティで Shenandoah GC 等を主導する主要コントリビュータ
- RHEL サブスクリプションにサポートが含まれる
- Windows 版も提供（RHEL サブスクリプション契約者向け）

**Java 25 対応:** RHEL 向けパッケージとしてリリース

**対象ユーザー:** RHEL / OpenShift 環境を使用するエンタープライズユーザー。

### 3.10 SAP Machine

**提供元:** SAP SE

**ライセンス:** GPL v2 + Classpath Exception

**特徴:**
- SAP の社内で使用している OpenJDK ビルドを外部に公開
- SAP アプリケーションサーバー (SAP NetWeaver 等) との統合テスト済み
- SAP は OpenJDK コミュニティで主にメモリ管理・GC 関連のコントリビュータ
- GitHub で開発プロセスが公開されている

**Java 25 対応:** GA 後にリリース予定

**対象ユーザー:** SAP エコシステム上でアプリケーションを運用するユーザー。

### 3.11 IBM Semeru Runtime

**提供元:** IBM

**ライセンス:**
- **IBM Semeru Runtime Open Edition:** GPL v2 + Classpath Exception + Eclipse Public License v2
- **IBM Semeru Runtime Certified Edition:** IBM 商用ライセンス

**特徴:**
- **HotSpot ではなく Eclipse OpenJ9 VM を使用**
- OpenJ9 は IBM の J9 VM をオープンソース化したもの
- **メモリフットプリントが小さい:** HotSpot と比較して 30-50% 少ないメモリ使用量（公称値）
- **起動時間が速い:** 特に共有クラスキャッシュ (SCC) 使用時
- **JIT コンパイラが異なる:** Eclipse OMR の JIT を使用
- クラウドネイティブ環境（コンテナ）での小フットプリントに強み
- HotSpot と API は同一だが、**JVM 内部の動作が異なる** ため、GC チューニングパラメータ等が全く異なる

**Java 25 対応:** OpenJ9 の対応状況に依存

**対象ユーザー:** メモリ効率を重視するクラウドネイティブアプリケーション。IBM Cloud / WebSphere 環境。

```
HotSpot VM と OpenJ9 VM の構造比較
====================================

HotSpot VM (大半のディストリビューション)     OpenJ9 VM (IBM Semeru)
┌─────────────────────────┐                ┌─────────────────────────┐
│      Java アプリ         │                │      Java アプリ         │
├─────────────────────────┤                ├─────────────────────────┤
│   Java クラスライブラリ   │ ← 同一 API →  │   Java クラスライブラリ   │
├─────────────────────────┤                ├─────────────────────────┤
│  HotSpot Runtime         │                │  OpenJ9 Runtime         │
│  ┌───────┐ ┌───────┐    │                │  ┌───────┐ ┌───────┐   │
│  │  C1   │ │  C2   │    │                │  │  JIT  │ │  AOT  │   │
│  │ (JIT) │ │ (JIT) │    │                │  │(OMR)  │ │(SCC)  │   │
│  └───────┘ └───────┘    │                │  └───────┘ └───────┘   │
│  ┌───────────────────┐   │                │  ┌───────────────────┐  │
│  │ G1GC/ZGC/Shen/... │   │                │  │ gencon/balanced/  │  │
│  └───────────────────┘   │                │  │ optavgpause/...   │  │
└─────────────────────────┘                │  └───────────────────┘  │
                                            └─────────────────────────┘
```

### 3.12 GraalVM

**提供元:** Oracle (GraalVM チーム)

**ライセンス:**
- **GraalVM Community Edition (CE):** GPL v2 + Classpath Exception
- **Oracle GraalVM (旧 GraalVM Enterprise Edition):** GFTC (GraalVM Free Terms and Conditions)

**特徴:**
- **Graal JIT コンパイラ:** Java で書かれた JIT コンパイラ。C2 の後継を目指す
- **Native Image:** Java アプリケーションを AOT コンパイルしてネイティブバイナリを生成。起動時間 10ms 以下、メモリ使用量大幅削減
- **多言語対応 (Polyglot):** JavaScript, Python, Ruby, R, LLVM ベース言語を JVM 上で実行
- Java 25 では HotSpot + Graal JIT として動作するモード、または Native Image として動作するモード
- Quarkus, Micronaut, Spring Native 等のフレームワークが Native Image をサポート
- JVM モードでも Graal JIT により C2 を超える最適化が可能なケースがある

**Java 25 対応:** Java 25 ベースの GraalVM がリリース予定

**対象ユーザー:** マイクロサービスで起動時間・メモリ効率を重視するユーザー。多言語実行環境を必要とするユーザー。

```
GraalVM の動作モード
=====================

┌─────────────────────────────────────────────┐
│                  GraalVM                     │
│                                               │
│  Mode 1: JVM モード                          │
│  ┌─────────────────────────────────────────┐ │
│  │  HotSpot VM + Graal JIT (C2 の代替)     │ │
│  │  → 通常の JVM として動作               │ │
│  │  → Graal JIT で高度な最適化             │ │
│  │  → ウォームアップ後のピーク性能が高い   │ │
│  └─────────────────────────────────────────┘ │
│                                               │
│  Mode 2: Native Image モード                 │
│  ┌─────────────────────────────────────────┐ │
│  │  AOT コンパイル → ネイティブバイナリ     │ │
│  │  → JVM 不要                             │ │
│  │  → 起動時間: 数 ms                      │ │
│  │  → メモリ: 大幅削減                     │ │
│  │  → ピーク性能は JVM モードに劣る場合あり │ │
│  │  → リフレクション等に制約あり           │ │
│  └─────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

### 3.13 Liberica JDK

**提供元:** BellSoft

**ライセンス:**
- **Liberica JDK (Standard):** GPL v2 + Classpath Exception（無償）
- **Liberica JDK with Commercial Support:** 商用サポート付き有償版

**特徴:**
- **組み込み・IoT 向けに強い:** ARM32, ARM64, x86, MIPS 等の幅広いアーキテクチャに対応
- **Liberica NIK (Native Image Kit):** GraalVM Native Image のビルドキットを提供
- **Liberica JDK Lite:** JavaFX を含まない軽量版
- **Liberica JDK Full:** JavaFX を同梱したフルバージョン
- Alpine Linux (musl libc) 対応版あり
- Spring Boot 公式ドキュメントで推奨ディストリビューションの一つとして言及

**Java 25 対応:** GA 後に速やかにリリース

**対象ユーザー:** 組み込み・IoT デバイスで Java を使うユーザー。Spring Boot アプリケーション開発者。JavaFX を同梱した JDK が必要なユーザー。

### 3.14 Alibaba Dragonwell

**提供元:** Alibaba Group

**ライセンス:** GPL v2 + Classpath Exception

**特徴:**
- Alibaba の大規模クラウド基盤（Alibaba Cloud / Taobao / Alipay 等）で使用
- **独自の最適化:**
  - **Wisp2:** コルーチンベースの軽量スレッド実装（Virtual Threads 以前から独自開発）
  - **JWarmup:** ウォームアップの高速化機能
  - **ElasticHeap:** ヒープサイズの動的調整
  - **Multi-tenant JVM:** マルチテナント機能
- これらの独自機能の一部は OpenJDK にアップストリームとして還元されている
- 中国国内での利用が中心だが、GitHub で公開されており誰でも利用可能

**Java 25 対応:** コミュニティベースで対応

**対象ユーザー:** 大規模なクラウドネイティブ環境。中国市場でのデプロイメント。

### 3.15 Tencent Kona

**提供元:** Tencent

**ライセンス:** GPL v2 + Classpath Exception

**特徴:**
- Tencent Cloud やゲーム基盤で使用
- **KonaFiber:** 独自の軽量スレッド実装（Wisp2 と類似のアプローチ）
- **ZGC のバックポート:** 古い Java バージョンへの ZGC バックポート
- 中国市場向けの最適化（タイムゾーン、暗号、ロケール）
- GitHub で公開されている

**Java 25 対応:** コミュニティベースで対応

**対象ユーザー:** Tencent Cloud 環境。中国市場向けアプリケーション。

### 3.16 その他のディストリビューション

| ディストリビューション | 提供元 | 特徴 |
|----------------------|--------|------|
| **Mandrel** | Red Hat | GraalVM CE の Quarkus 向けビルド。Native Image に特化 |
| **ojdkbuild** | コミュニティ | Windows 向けの OpenJDK ビルド（開発終了、Adoptium に移行推奨） |
| **Debian OpenJDK** | Debian Project | Debian/Ubuntu パッケージとして提供。`apt install` で導入可能 |
| **Azul Zulu Prime Builds of OpenJDK** | Azul | HotSpot ベースだが Azul の追加テスト済み。Zulu の上位互換 |
| **Huawei BiSheng JDK** | Huawei | Huawei Cloud / Kunpeng (ARM) 向け最適化 |

---

## 4. ライセンスの比較

### 4.1 主要ライセンス形態

```
JDK ライセンスの分類
=====================

┌────────────────────────────────────────────────────────┐
│                    オープンソース系                       │
│                                                          │
│  GPL v2 + Classpath Exception (GPLv2+CE)                 │
│  ├── ソースコードの改変・再配布: 自由                     │
│  ├── バイナリ単体の配布: 自由（CE により GPL 伝搬なし）    │
│  ├── 商用利用: 無制限                                     │
│  └── 大半のディストリビューションがこのライセンス          │
│                                                          │
├──────────────────────────────────────────────────────────┤
│                    Oracle 固有系                          │
│                                                          │
│  NFTC (No-Fee Terms and Conditions)                      │
│  ├── Java 17 以降の Oracle JDK に適用                     │
│  ├── 商用利用: 無償（ただし条件あり）                     │
│  ├── 条件: 次の LTS リリース後1年以内にアップグレード     │
│  └── 再配布: 制限あり                                     │
│                                                          │
│  OTN (Oracle Technology Network License)                 │
│  ├── Java 8u211 〜 Java 16 の Oracle JDK に適用          │
│  ├── 開発・テスト: 無償                                   │
│  ├── 本番利用: 有償（Java SE Subscription が必要）        │
│  └── 現在は旧バージョンにのみ適用                         │
│                                                          │
│  GFTC (GraalVM Free Terms and Conditions)                │
│  ├── Oracle GraalVM に適用                                │
│  ├── NFTC と類似の条件                                    │
│  └── 商用利用: 無償（条件付き）                           │
│                                                          │
├──────────────────────────────────────────────────────────┤
│                    商用ライセンス系                       │
│                                                          │
│  各社独自の商用ライセンス                                 │
│  ├── Azul Platform Prime: Azul 商用契約                   │
│  ├── IBM Semeru Certified Edition: IBM 商用契約           │
│  ├── Oracle Java SE Subscription: Oracle 商用契約         │
│  └── サポート・パッチ・保証を含む                         │
└────────────────────────────────────────────────────────┘
```

### 4.2 ライセンス比較表

| ディストリビューション | ライセンス | 商用利用 | 再配布 | ソース公開義務 |
|----------------------|-----------|---------|--------|--------------|
| Oracle JDK (17+) | NFTC | 条件付き無償 | 制限あり | - |
| Oracle OpenJDK builds | GPLv2+CE | 無償 | 自由 | 改変時のみ |
| Azul Zulu CE | GPLv2+CE | 無償 | 自由 | 改変時のみ |
| Azul Platform Core | 商用 | 有償 | 契約次第 | - |
| Azul Platform Prime | 商用 | 有償 | 契約次第 | - |
| Eclipse Temurin | GPLv2+CE | 無償 | 自由 | 改変時のみ |
| Amazon Corretto | GPLv2+CE | 無償 | 自由 | 改変時のみ |
| Microsoft OpenJDK | GPLv2+CE | 無償 | 自由 | 改変時のみ |
| Red Hat OpenJDK | GPLv2+CE | 無償 | 自由 | 改変時のみ |
| SAP Machine | GPLv2+CE | 無償 | 自由 | 改変時のみ |
| IBM Semeru Open | GPLv2+CE+EPLv2 | 無償 | 自由 | 改変時のみ |
| GraalVM CE | GPLv2+CE | 無償 | 自由 | 改変時のみ |
| Oracle GraalVM | GFTC | 条件付き無償 | 制限あり | - |
| Liberica JDK | GPLv2+CE | 無償 | 自由 | 改変時のみ |
| Alibaba Dragonwell | GPLv2+CE | 無償 | 自由 | 改変時のみ |
| Tencent Kona | GPLv2+CE | 無償 | 自由 | 改変時のみ |

### 4.3 Oracle ライセンスの変遷

Oracle のライセンス方針は何度も変更されており、Java コミュニティに大きな影響を与えてきた。

```
Oracle JDK ライセンスの変遷（時系列）
======================================

2006          2018           2019          2021          2023
  |             |              |             |             |
  v             v              v             v             v
BCL           OTN に         Java 11      NFTC に       Java 21
(無償)        変更           リリース      変更          LTS
              商用は         商用は        再び条件      リリース
              有償に         有償          付き無償

    ┌─────────┐ ┌───────────┐ ┌───────────┐
    │ BCL 時代 │ │ OTN 時代  │ │ NFTC 時代 │
    │ ~Java 8  │ │ Java 11-16│ │ Java 17+  │
    │ 無償     │ │ 本番有償  │ │ 条件付    │
    │          │ │           │ │ 無償      │
    └─────────┘ └───────────┘ └───────────┘

    ↑ この OTN 時代に多数の代替ディストリビューションが台頭
      (Corretto, Temurin, Zulu 等の利用が急増)
```

**2018年の衝撃:** Oracle が Java 11 以降の Oracle JDK を商用有償にした（OTN ライセンス）ことで、多くの企業が代替ディストリビューションに移行した。これが現在の「多様なディストリビューション」の状況を生み出した最大の契機である。

**2021年の NFTC 回帰:** Oracle は Java 17 以降で NFTC ライセンスに変更し、条件付きで無償利用を再び認めた。しかし一度離れたユーザーの多くは戻らず、Temurin, Corretto, Zulu 等が市場シェアを維持している。

### 4.4 本番環境で無償で使えるディストリビューション

制限なく本番環境で無償利用できるディストリビューション:

- **Eclipse Temurin** -- ベンダー中立、制限なし
- **Amazon Corretto** -- 制限なし
- **Azul Zulu Community Edition** -- 制限なし
- **Liberica JDK** -- 制限なし
- **Microsoft Build of OpenJDK** -- 制限なし
- **Red Hat Build of OpenJDK** -- 制限なし（ただし RHEL サブスクリプション外ではサポートなし）
- **SAP Machine** -- 制限なし
- **Alibaba Dragonwell** -- 制限なし
- **Tencent Kona** -- 制限なし

条件付きで無償利用可能:

- **Oracle JDK (NFTC)** -- 次の LTS + 1年以内のアップグレードが条件
- **Oracle GraalVM (GFTC)** -- NFTC と類似の条件

---

## 5. サポート期間の比較

### 5.1 LTS の概念

Java は 2017年の Java 9 以降、**6ヶ月ごとのフィーチャーリリース**を採用している。このうち特定のバージョンが **LTS (Long-Term Support)** に指定され、長期間のセキュリティアップデートとバグ修正が提供される。

```
Java リリースサイクル (Java 21 〜 Java 29)
============================================

        LTS                              LTS
         |                                |
         v                                v
  21    22    23    24    25    26    27    28    29
  |     |     |     |     |     |     |     |     |
  2023  2024  2024  2025  2025  2026  2026  2027  2027
  Sep   Mar   Sep   Mar   Sep   Mar   Sep   Mar   Sep
                          ^^^
                          LTS (現在の最新)

  ─── LTS: 数年間のサポート
  ─── non-LTS: 次のリリースまで (6ヶ月)
```

**LTS のサイクル:**
- Java 17 (2021年9月) -- LTS
- Java 21 (2023年9月) -- LTS
- Java 25 (2025年9月) -- LTS（**2年間隔に短縮**）
- Java 29 (2027年9月) -- LTS（予定）

Java 25 から LTS の間隔が従来の3年から **2年** に短縮された。これにより、ユーザーはより頻繁に最新の LTS にアクセスできるようになった。

### 5.2 LTS サポート期間比較表

以下は各ディストリビューションの主な LTS バージョンのサポート終了予定（セキュリティアップデート提供の最終年月）である。商用サポートを含む場合と含まない場合で異なるため、無償サポートを基準とする。

**Java 21 LTS のサポート終了予定:**

| ディストリビューション | 無償サポート終了 | 商用サポート終了 |
|----------------------|----------------|----------------|
| Oracle JDK (NFTC) | 2026年9月頃 (Java 25 LTS +1年) | 2031年9月 (Premier) |
| Oracle OpenJDK builds | 2024年3月 (Java 22 GA まで) | - |
| Azul Zulu CE | 2031年9月 | 2031年12月 (Core) |
| Eclipse Temurin | 2029年12月 | - |
| Amazon Corretto | 2030年10月 | - |
| Microsoft OpenJDK | 2028年9月 | - |
| Red Hat OpenJDK | RHEL ライフサイクルに依存 | RHEL に含む |
| Liberica JDK | 2030年3月 | 要問合せ |

**Java 25 LTS のサポート終了予定（予測）:**

| ディストリビューション | 無償サポート終了（予測） | 備考 |
|----------------------|------------------------|------|
| Oracle JDK (NFTC) | 2028年9月頃 | Java 29 LTS +1年 |
| Oracle OpenJDK builds | 2026年3月 | Java 26 GA まで |
| Azul Zulu CE | 2035年9月頃 | 通常10年間 |
| Eclipse Temurin | 2031年頃 | 通常 LTS +6年 |
| Amazon Corretto | 2033年頃 | 通常8年間 |
| Microsoft OpenJDK | 2031年頃 | 通常6年間 |
| Red Hat OpenJDK | RHEL ライフサイクルに依存 | RHEL に含む |
| Liberica JDK | 2033年頃 | 通常8年間 |

> **注意:** サポート終了日は各ベンダーの方針変更により変わる可能性がある。最新情報は各ベンダーの公式サイトで確認すること。

### 5.3 Java 25 (LTS) のサポート終了予定

```
Java 25 LTS サポート期間の比較（視覚化）
==========================================

2025    2027    2029    2031    2033    2035    2037
  |       |       |       |       |       |       |
  ├───┐
  │ORA│ Oracle OpenJDK builds (〜2026年3月)
  ├───┴─────┐
  │ NFTC    │ Oracle JDK NFTC (〜2028年9月頃)
  ├─────────┴─────────┐
  │ Oracle Premier     │ Oracle JDK 商用 (〜2031年)
  ├─────────────────────────┐
  │ Temurin                  │ Eclipse Temurin (〜2031年頃)
  ├───────────────────────────────┐
  │ Corretto                      │ Amazon Corretto (〜2033年頃)
  ├───────────────────────────────┐
  │ Liberica                      │ Liberica JDK (〜2033年頃)
  ├───────────────────────────────────────┐
  │ Azul Zulu CE                          │ Azul Zulu (〜2035年頃)
  |       |       |       |       |       |       |
2025    2027    2029    2031    2033    2035    2037
```

---

## 6. JVM の違い

JDK ディストリビューションの最も根本的な差異は、**どの JVM 実装を使用しているか** である。大半のディストリビューションは HotSpot VM を使用するが、一部は独自の JVM を採用している。

JVM の内部構造については [jvm-overview.md](jvm-overview.md) を参照。

### 6.1 JVM 実装の分類

```
JVM 実装の系統樹
=================

                    ┌──────────────────────┐
                    │   JVM 仕様 (JVMS)    │
                    │  (Java SE Standard)   │
                    └──────────┬───────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              v                v                v
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │  HotSpot VM  │  │  OpenJ9 VM   │  │   Zing VM    │
    │  (OpenJDK)   │  │  (Eclipse)   │  │   (Azul)     │
    └──────┬───────┘  └──────────────┘  └──────────────┘
           │
    ┌──────┴──────────────────┐
    │                         │
    v                         v
┌──────────┐          ┌──────────────┐
│ 標準構成  │          │ GraalVM 構成  │
│ C1+C2 JIT│          │ C1+Graal JIT │
│ G1/ZGC   │          │ + Native     │
│ /Shen    │          │   Image      │
└──────────┘          └──────────────┘
```

| JVM | 主な使用ディストリビューション |
|-----|------------------------------|
| **HotSpot VM (C1+C2)** | Oracle JDK, Azul Zulu, Temurin, Corretto, Microsoft, Red Hat, SAP Machine, Liberica, Dragonwell, Kona |
| **HotSpot VM + Graal JIT** | GraalVM (JVM モード) |
| **Eclipse OpenJ9** | IBM Semeru Runtime |
| **Zing VM** | Azul Platform Prime |

### 6.2 JVM 特性比較表

| 特性 | HotSpot (C1+C2) | HotSpot + Graal | OpenJ9 | Zing |
|------|-----------------|-----------------|--------|------|
| **JIT コンパイラ** | C1 (Client) + C2 (Server) | C1 + Graal | Eclipse OMR JIT | Falcon (LLVM ベース) |
| **デフォルト GC** | G1GC | G1GC | gencon | C4 |
| **低レイテンシ GC** | ZGC, Shenandoah | ZGC, Shenandoah | metronome (RT) | C4 (< 1ms) |
| **起動時間** | 普通 | やや遅い | 速い (SCC) | 普通 |
| **ピーク性能** | 高い | 非常に高い | やや低い | 非常に高い |
| **メモリ効率** | 普通 | やや多い | 良い (30-50%減) | 普通 |
| **大規模ヒープ** | ZGC で対応 | ZGC で対応 | balanced GC | C4 (数百GB可) |
| **AOT 対応** | CDS/AOT Cache | Native Image | SCC | ReadyNow |
| **ライセンス** | OSS | OSS/GFTC | OSS/商用 | 商用のみ |

### 6.3 GC の違い

GC はアプリケーションのレイテンシとスループットに直結する最重要コンポーネントである。

GC の詳細については以下を参照:
- [gc-deep-dive.md](memory/gc-deep-dive.md) -- GC 全般
- [g1gc-deep-dive.md](memory/g1gc-deep-dive.md) -- G1GC の詳細
- [zgc-deep-dive.md](memory/zgc-deep-dive.md) -- ZGC の詳細

**HotSpot VM の GC 選択肢:**

| GC | 特徴 | ポーズ時間目標 | 推奨ヒープサイズ |
|----|------|--------------|----------------|
| **G1GC** (デフォルト) | バランス型。リージョンベース | 200ms (調整可) | 数GB〜数十GB |
| **ZGC** | 超低レイテンシ。カラーポインタ方式 | < 1ms | 数GB〜数TB |
| **Shenandoah** | 低レイテンシ。Red Hat 主導 | < 10ms | 数GB〜数百GB |
| **Parallel GC** | スループット重視 | 制限なし | 数GB |
| **Serial GC** | シングルスレッド。軽量 | 制限なし | 数百MB |

**OpenJ9 の GC 選択肢:**

| GC | 特徴 |
|----|------|
| **gencon** (デフォルト) | 世代別 GC。HotSpot の G1GC に相当 |
| **balanced** | リージョンベース。大規模ヒープ向け |
| **optavgpause** | 平均ポーズ時間の最小化 |
| **optthruput** | スループット最大化 |

**Zing の GC:**

| GC | 特徴 |
|----|------|
| **C4** | Continuously Concurrent Compacting Collector。読み取りバリア方式で、数百 GB のヒープでもサブミリ秒のポーズを実現 |

### 6.4 JIT コンパイラの違い

| JIT | VM | 特徴 |
|-----|-----|------|
| **C1 (Client)** | HotSpot | 高速コンパイル・低最適化。起動時に使用 |
| **C2 (Server)** | HotSpot | 低速コンパイル・高最適化。ピーク性能を担う |
| **Graal JIT** | GraalVM | Java で記述。C2 を超える最適化が可能。部分エスケープ解析等 |
| **Falcon JIT** | Zing | LLVM ベース。C2 を超える最適化性能 |
| **Eclipse OMR JIT** | OpenJ9 | 独自の多段階コンパイル。メモリ効率に優れる |

---

## 7. 選定ガイド -- どう選ぶか

### 7.1 選定フローチャート

```
JDK ディストリビューション選定フローチャート
=============================================

                    ┌──────────────────┐
                    │  JDK が必要だ！   │
                    └────────┬─────────┘
                             │
                    ┌────────v─────────┐
                    │ 商用サポートは    │
                    │ 必要か？          │
                    └───┬──────────┬───┘
                        │          │
                       Yes         No
                        │          │
               ┌────────v────┐  ┌──v──────────────┐
               │ 超低レイテンシ│  │ 特定クラウドに   │
               │ が必要か？   │  │ ロックインOKか？ │
               └──┬───────┬──┘  └──┬───────────┬──┘
                  │       │        │           │
                 Yes      No      Yes          No
                  │       │        │           │
                  v       │   ┌────v────┐      │
            ┌─────────┐   │   │ どの     │      │
            │ Azul    │   │   │ クラウド？│      │
            │Platform │   │   └┬───┬───┬┘      │
            │ Prime   │   │    │   │   │       │
            └─────────┘   │   AWS Azure 他      │
                          │    │   │   │       │
                          │    v   v   │       │
                          │  Cor  MS   │       │
                          │  ret  OJD  │       │
                          │  to   K    │       │
                          │            │       │
                     ┌────v────────────v───┐   │
                     │ Oracle サポート契約  │   │
                     │ を検討できるか？     │   │
                     └──┬──────────────┬──┘   │
                        │              │       │
                       Yes             No      │
                        │              │       │
                        v              │       │
                  ┌──────────┐         │       │
                  │ Oracle   │         │       │
                  │ JDK      │         │       │
                  └──────────┘         │       │
                                       │       │
                              ┌────────v───────v──────┐
                              │ ベンダー中立を         │
                              │ 重視するか？           │
                              └──┬─────────────────┬──┘
                                 │                 │
                                Yes                No
                                 │                 │
                                 v                 v
                           ┌──────────┐     ┌──────────┐
                           │ Eclipse  │     │ Azul     │
                           │ Temurin  │     │ Zulu CE  │
                           └──────────┘     └──────────┘

  ※ Native Image が必要なら → GraalVM
  ※ 組み込み/IoT なら       → Liberica JDK
  ※ IBM/WebSphere 環境なら  → IBM Semeru
  ※ SAP 環境なら            → SAP Machine
  ※ RHEL 環境なら           → Red Hat OpenJDK
```

### 7.2 ユースケース別の推奨

| ユースケース | 推奨ディストリビューション | 理由 |
|-------------|------------------------|------|
| **Web アプリ (Spring Boot 等)** | Temurin, Zulu CE, Corretto | 安定性とサポート期間。どれを選んでも問題ない |
| **バッチ処理** | Temurin, Zulu CE, Corretto | スループット重視なら Parallel GC を検討 |
| **マイクロサービス** | GraalVM (Native Image), Temurin | 起動時間・メモリ効率重視なら GraalVM |
| **組み込み・IoT** | Liberica JDK | ARM32 等の幅広いアーキテクチャ対応 |
| **金融取引 (低レイテンシ)** | Azul Platform Prime | C4 GC によるサブミリ秒ポーズ |
| **クラウドネイティブ (コンテナ)** | Temurin, Corretto, Zulu CE | Alpine Linux (musl) 対応版あり |
| **レガシーシステム (Java 8)** | Azul Zulu CE, Corretto | 長期 LTS サポート |
| **メモリ制約環境** | IBM Semeru (OpenJ9) | メモリフットプリントが小さい |
| **多言語実行環境** | GraalVM | JavaScript, Python 等を JVM 上で実行 |

### 7.3 クラウドプロバイダー別の推奨

| クラウド | 推奨 | 理由 |
|---------|------|------|
| **AWS** | Amazon Corretto | AWS サービスとの統合。Amazon Linux でデフォルト |
| **Azure** | Microsoft Build of OpenJDK | Azure サービスとの統合。Azure Spring Apps 等 |
| **GCP** | Eclipse Temurin or Azul Zulu CE | GCP は特定の JDK を推奨していない。ベンダー中立な選択が無難 |
| **Oracle Cloud** | Oracle JDK | OCI との統合。無償利用可能 |
| **IBM Cloud** | IBM Semeru | IBM 環境との統合 |
| **Alibaba Cloud** | Alibaba Dragonwell | Alibaba Cloud との統合 |

### 7.4 コスト要件別

```
コスト別の選択肢
=================

完全無償（制限なし）
├── Eclipse Temurin
├── Amazon Corretto
├── Azul Zulu CE
├── Microsoft Build of OpenJDK
├── Liberica JDK
├── Red Hat Build of OpenJDK
├── SAP Machine
├── Alibaba Dragonwell
└── Tencent Kona

条件付き無償
├── Oracle JDK (NFTC) -- 次 LTS +1年以内のアップグレードが条件
└── Oracle GraalVM (GFTC) -- 類似の条件

有償（商用サポート付き）
├── Oracle Java SE Subscription -- 包括的サポート
├── Azul Platform Core -- Zulu + 商用サポート
├── Azul Platform Prime -- Zing VM (低レイテンシ)
├── IBM Semeru Certified Edition -- IBM サポート
├── Liberica JDK (商用) -- BellSoft サポート
└── Red Hat (RHEL サブスクリプションに含む)
```

### 7.5 パフォーマンス要件別

| 要件 | 推奨 | 技術的根拠 |
|------|------|-----------|
| **ピーク性能最大化** | Azul Platform Prime (Falcon JIT) or GraalVM (Graal JIT) | LLVM/Graal による高度な最適化 |
| **レイテンシ最小化** | Azul Platform Prime (C4 GC) or HotSpot + ZGC | ポーズレス GC。詳細は [zgc-deep-dive.md](memory/zgc-deep-dive.md) |
| **スループット最大化** | HotSpot + Parallel GC | 全 CPU コアを GC に活用 |
| **起動時間最短** | GraalVM Native Image | JVM なしで直接実行。数 ms で起動 |
| **メモリ最小化** | IBM Semeru (OpenJ9) or GraalVM Native Image | OpenJ9 の共有クラスキャッシュ、Native Image のコンパクトさ |
| **大規模ヒープ** | HotSpot + ZGC or Azul Platform Prime (C4) | TB 級のヒープに対応。詳細は [zgc-deep-dive.md](memory/zgc-deep-dive.md) |

---

## 8. 移行の注意点

### 8.1 移行の基本方針

同じ OpenJDK ベースのディストリビューション間の移行は、基本的に **容易** である。Java SE 仕様に準拠している限り、アプリケーションコードの変更は不要である。

```
ディストリビューション間の移行難易度
======================================

  同一 JVM (HotSpot → HotSpot)
  ┌─────────────────────────────────────┐
  │  Temurin ←→ Zulu ←→ Corretto       │
  │  ←→ Oracle JDK ←→ Liberica         │  ◎ 容易
  │                                       │  JVM オプションもほぼ互換
  └─────────────────────────────────────┘

  異なる JIT (HotSpot → GraalVM JVM モード)
  ┌─────────────────────────────────────┐
  │  HotSpot (C2) → GraalVM (Graal)    │  ○ 比較的容易
  │  JIT 最適化の挙動差に注意            │  パフォーマンス特性が変わる
  └─────────────────────────────────────┘

  異なる JVM (HotSpot → OpenJ9)
  ┌─────────────────────────────────────┐
  │  HotSpot → IBM Semeru (OpenJ9)     │  △ 注意が必要
  │  GC オプションが全く異なる           │  -XX: オプションの非互換
  │  内部 API の差異あり                 │  チューニングのやり直し
  └─────────────────────────────────────┘

  Native Image 化 (JVM → GraalVM Native Image)
  ┌─────────────────────────────────────┐
  │  JVM → Native Image                │  × 大きな変更が必要
  │  リフレクション制約                  │  reachability metadata の作成
  │  動的プロキシ制約                    │  フレームワークの対応が必要
  └─────────────────────────────────────┘
```

### 8.2 注意すべき差分

同じ HotSpot ベースであっても、以下の差分が存在する可能性がある:

| 差分カテゴリ | 内容 | 影響度 |
|-------------|------|--------|
| **独自パッチ** | バグ修正やパフォーマンス改善の適用状況が異なる | 低〜中 |
| **暗号プロバイダー** | デフォルトの暗号プロバイダーやその設定が異なる場合がある（例: Corretto の Amazon-LC） | 中 |
| **CA 証明書** | バンドルされるルート CA 証明書のセットが微妙に異なる場合がある | 低 |
| **フォントレンダリング** | 特に Linux 環境でフォント関連の動作が異なる場合がある | 低 |
| **セキュリティプロパティ** | `java.security` ファイルのデフォルト設定が異なる場合がある | 中 |
| **バージョン文字列** | `java.vendor`, `java.vm.vendor` 等のシステムプロパティが異なる | 低（ただしバージョンチェックロジックに影響する場合あり） |
| **同梱ツール** | JMC, VisualVM 等の同梱有無が異なる | 低 |
| **ディレクトリ構造** | インストールディレクトリの構造が微妙に異なる場合がある | 低 |

### 8.3 バージョンアップ時の互換性

ディストリビューション間の移行よりも、**Java バージョンのアップグレード**（例: Java 21 → Java 25）の方が影響が大きい。バージョンアップ時に注意すべき主な変更:

- **非推奨 API の削除:** 各リリースで deprecated API が削除される可能性がある
- **内部 API のカプセル化:** `sun.misc.*`, `com.sun.*` 等の内部 API が段階的にアクセス不能になる
- **モジュールシステム:** Java 9 以降の JPMS によるアクセス制御
- **GC の変更:** 古い GC (CMS) の削除、新しい GC のデフォルト化
- **セキュリティの強化:** TLS バージョンの制限、アルゴリズムの非推奨化
- **プレビュー機能の扱い:** プレビュー API は次のバージョンで変更・削除される可能性がある

バージョン間の詳細な変更点は [OpenJDK JDK Release Notes](https://openjdk.org/) で確認できる。JEP の一覧と詳細は [openjdk-projects-overview.md](openjdk-projects-overview.md) も参照。

---

## 参考リンク

- [OpenJDK 公式サイト](https://openjdk.org/)
- [Java SE Specifications](https://docs.oracle.com/javase/specs/)
- [Oracle JDK ダウンロード](https://www.oracle.com/java/technologies/downloads/)
- [Eclipse Adoptium (Temurin)](https://adoptium.net/)
- [Azul Zulu ダウンロード](https://www.azul.com/downloads/)
- [Azul Platform Prime](https://www.azul.com/products/prime/)
- [Amazon Corretto](https://aws.amazon.com/corretto/)
- [Microsoft Build of OpenJDK](https://www.microsoft.com/openjdk)
- [Red Hat Build of OpenJDK](https://developers.redhat.com/products/openjdk/overview)
- [SAP Machine](https://sap.github.io/SapMachine/)
- [IBM Semeru Runtime](https://developer.ibm.com/languages/java/semeru-runtimes/)
- [GraalVM](https://www.graalvm.org/)
- [Liberica JDK (BellSoft)](https://bell-sw.com/libericajdk/)
- [Alibaba Dragonwell](https://dragonwell-jdk.io/)
- [Tencent Kona](https://github.com/Tencent/TencentKona-17)
- [Which JDK? -- whichjdk.com](https://whichjdk.com/)

### 関連ドキュメント

| ドキュメント | 内容 |
|-------------|------|
| [jvm-overview.md](jvm-overview.md) | JVM アーキテクチャの全体像 |
| [openjdk-projects-overview.md](openjdk-projects-overview.md) | OpenJDK プロジェクトの概要と JEP 一覧 |
| [gc-deep-dive.md](memory/gc-deep-dive.md) | GC アルゴリズムの詳細解析 |
| [g1gc-deep-dive.md](memory/g1gc-deep-dive.md) | G1GC の内部構造 |
| [zgc-deep-dive.md](memory/zgc-deep-dive.md) | ZGC の内部構造 |
