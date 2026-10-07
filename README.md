# gh-aw-demo — GitHub Agentic Workflows デモ

[GitHub Agentic Workflows](https://github.com/github/gh-aw)（以下 AW）を、運用中のアプリに近い題材で体験するためのデモリポジトリです。

Markdown で書いた自然言語の指示を `gh aw compile` で GitHub Actions ワークフローにコンパイルし、Issue トリアージ・CI 失敗調査・技術的負債の棚卸し・テスト追加 PR・セキュリティレビューを自動化します。

---

## 1. サンプルアプリケーション「bookshelf」

社内図書館の蔵書と貸出を管理する REST API です。AW のデモ用に、**あえて改善余地を残した状態**にしてあります。

### 技術スタック

| 項目 | 内容 |
| --- | --- |
| 言語 | Java 21 |
| フレームワーク | Spring Boot 3.5 (Web / Data JPA / Validation) |
| ビルドツール | Gradle (Wrapper 同梱) |
| データベース | H2 (インメモリ) |
| テスト | JUnit 5 + AssertJ + MockMvc |

### 構成

```
src/main/java/com/example/bookshelf/
├── BookshelfApplication.java     # エントリポイント
├── domain/                       # エンティティ (Book / Loan)
├── repository/                   # Spring Data JPA リポジトリ
├── service/                      # ユースケース (BookshelfService)
└── web/                          # REST コントローラ・DTO・例外ハンドラ
```

### API 一覧

| メソッド | パス | 説明 |
| --- | --- | --- |
| `GET` | `/api/books?keyword=` | 蔵書の一覧・タイトル検索 |
| `GET` | `/api/books/{id}` | 蔵書の取得 |
| `POST` | `/api/books` | 蔵書の登録 |
| `POST` | `/api/loans/books/{bookId}` | 貸出 |
| `POST` | `/api/loans/{loanId}/return` | 返却 |
| `POST` | `/api/loans/{loanId}/renew` | 貸出期限の更新 |
| `GET` | `/api/loans/overdue` | 延滞中の貸出一覧 |

貸出期限の更新では、現在の返却期限から14日延長します。更新は1件の貸出につき最大2回までです。
返却済みまたは延滞中の貸出は更新できません。

### 起動・ビルド

```bash
# ビルドとテスト
./gradlew build

# 起動 (http://localhost:8080)
./gradlew bootRun

# 動作確認
curl http://localhost:8080/api/books
curl -X POST http://localhost:8080/api/loans/books/1 \
  -H 'Content-Type: application/json' -d '{"borrower":"alice"}'
curl -X POST http://localhost:8080/api/loans/1/renew
```

> Java 21 が必要です。Gradle の toolchain 設定を使用しているため、JDK 21 が見つからない場合はビルド時にエラーになります。

### 意図的に残してある「改善余地」

AW に見つけてもらう／直してもらうための題材を、あえて残してあります。
**具体的な内容は答えにあたるため、このリポジトリの外（非公開の Gist）で管理しています。**

エージェントに「知識なしで」調査させるデモを行うため、答えはリポジトリの作業ツリーに置きません。

---

## 2. AW の使い所

このリポジトリには 6 つの Agentic Workflow があります。実体は `.github/workflows/*.md`（人が書く指示）と、そこからコンパイルされた `*.lock.yml`（GitHub Actions ワークフロー）のペアです。

| ワークフロー | トリガー | やること | 出力 (safe-outputs) |
| --- | --- | --- | --- |
| [`issue-triage.md`](.github/workflows/issue-triage.md) | Issue の作成・再オープン | 内容を読んでラベル付与と確認コメント | `add-labels` / `add-comment` |
| [`ci-doctor.md`](.github/workflows/ci-doctor.md) | `CI` ワークフローの失敗 (`workflow_run`) | ログとコードから原因仮説と対処方針を作成 | `create-issue` |
| [`tech-debt-hunter.md`](.github/workflows/tech-debt-hunter.md) | 週次 / 手動 | TODO とリファクタリング候補を棚卸しして優先度付け | `create-issue` |
| [`test-gap-filler.md`](.github/workflows/test-gap-filler.md) | Issue/PR への `/add-tests` コメント | 不足しているテストを追加した PR を作成 | `create-pull-request` / `add-comment` |
| [`security-review.md`](.github/workflows/security-review.md) | 週次 / 手動 | 依存の脆弱性とコード上の懸念を調査 | `create-issue` |
| [`issue-auto-fix.md`](.github/workflows/issue-auto-fix.md) | `Issue トリアージ` 完了後、Issue に `agent-fix` ラベルあり | 内容を読んで最小修正を行いドラフト PR を作成 | `create-pull-request` / `add-comment` |

`tech-debt-hunter` の週次実行では、最後に AI 調査が正常完了した `main` の SHA と今回の SHA を比較し、監視対象に未調査の変更がある場合だけ AI 調査とレポート Issue 作成を行います。監視対象は `src/**`、Gradle のビルド定義・Wrapper、調査ワークフロー、および共有のエージェント／指示／スキル定義です。初回実行や前回失敗後の未調査変更は調査し、手動実行は常に調査します。`main` 以外のブランチで手動実行しても、週次実行の比較基準は更新しません。なお、軽量な変更チェックの GitHub Actions 実行自体は毎週行われます。`security-review` は新しい脆弱性を検知できるよう、変更の有無にかかわらず週次実行します。

### なぜ AW が向いているのか

- **判断が必要で定型化しづらい作業**（トリアージ、失敗原因の切り分け、優先度付け）を任せられる。
- **書き込みが safe-outputs に限定される**ため、エージェントが直接 push することはなく、Issue / PR / コメントという人がレビューできる形でしか結果が出てこない。
- エージェント本体は `permissions: read-all` で動き、**書き込みは別ジョブに分離される**ので、権限を絞ったまま自動化できる。
- 指示が Markdown なので、**プロンプトのレビューと差分管理が Git 上で完結する**。

### 逆に向いていないこと

- 仕様の最終決定や、ビジネス上のトレードオフの判断。
- 決定論的に書ける処理（フォーマッタ、静的解析、依存更新）。これらは通常の Actions や Dependabot の方が速くて安い。

---

## 3. AW 構築手順

このリポジトリを構築するために実行したコマンドと設定です。

### 3.1 CLI のインストール

```bash
# gh CLI の拡張として導入
gh extension install github/gh-aw

# もしくはインストールスクリプト（GitHub トークン不要）
curl -sL https://raw.githubusercontent.com/github/gh-aw/main/install-gh-aw.sh | bash

gh aw version
```

### 3.2 エンジン用シークレットの登録

既定のエンジンは `copilot` です。Copilot Requests: Read 権限を持つ fine-grained PAT を登録します。

```bash
gh secret set COPILOT_GITHUB_TOKEN
```

| エンジン | シークレット名 |
| --- | --- |
| `copilot`（既定） | `COPILOT_GITHUB_TOKEN` |
| `claude` | `ANTHROPIC_API_KEY` |
| `codex` | `OPENAI_API_KEY` |

### 3.3 ワークフローの作成

公開サンプル（[githubnext/agentics](https://github.com/githubnext/agentics)）を取り込む場合:

```bash
# 対話形式（エンジン選択・シークレット設定込み）
gh aw add-wizard githubnext/agentics/issue-triage

# 非対話
gh aw add githubnext/agentics/ci-doctor
```

このリポジトリでは、サンプルを参考にしつつ `.github/workflows/*.md` を自前で記述しています。frontmatter の主な項目は次のとおりです。

```yaml
---
description: |          # ワークフローの説明
on:                     # トリガー（issues / workflow_run / schedule / slash_command など）
  issues:
    types: [opened, reopened]
  reaction: eyes        # 起動時に 👀 リアクションを付ける
permissions: read-all   # エージェント本体は読み取り専用で動かす
network:                # 外部ネットワークの許可リスト
  allowed: [defaults, java]
tools:                  # 使えるツール
  github:
    toolsets: [issues, labels]
  bash: true
  edit:
safe-outputs:           # 書き込みはここで宣言したものだけが許可される
  add-labels:
    allowed: [bug, enhancement]
    max: 3
timeout-minutes: 10
---

# ここから下が自然言語の指示（Markdown）
```

### 3.4 コンパイル

`.md` を編集したら必ずコンパイルし、生成された `.lock.yml` も一緒にコミットします。
PR では CI が `gh-aw v0.86.2` で再コンパイルし、`.lock.yml` に未コミットの差分があれば失敗します。`gh-aw` を更新する場合は、意図的に生成物を更新して同じ PR に含めてください。

```bash
gh aw compile              # すべてコンパイル
gh aw compile issue-triage # 個別にコンパイル
gh aw compile --validate   # 検証のみ
gh aw compile --purge      # 不要になった .lock.yml を削除
```

`.lock.yml` は生成物なので**手で編集しません**。`.gitattributes` で `linguist-generated=true` を指定し、差分表示から除外しています。

### 3.5 実行と調査

```bash
gh aw status                       # 有効／無効と最終実行結果
gh aw run tech-debt-hunter         # 手動実行 (workflow_dispatch)
gh aw logs ci-doctor -c 5          # 直近 5 件のログ取得
gh aw audit <run-id>               # 特定実行のツール使用状況・コストを確認
```

Issue や PR のコメントに `/add-tests` と書くと `test-gap-filler` が起動します。

### 3.6 ラベルの作成

ワークフローが付与・使用するラベルは、あらかじめリポジトリに存在している必要があります。
既定のラベルに加えて、以下を `gh` で作成しています。

```bash
gh label create refactoring --color fbca04 --description "挙動を変えない内部構造の改善"
gh label create security    --color b60205 --description "セキュリティに関する内容"
gh label create ci          --color 1d76db --description "CI / ビルドに関する内容"
gh label create test        --color 0e8a16 --description "テストに関する内容"
gh label create automation  --color 5319e7 --description "Agentic Workflow による自動作成"
gh label create agent-fix   --color 0e8a16 --description "検証可能で明確な小規模修正として自動修正を許可"
```

### 3.7 人間がやるべきタスク（Issue に切り出し済み）

以下は CLI やエージェントだけでは完結せず、人間の権限・判断が必要です。Issue として起票してあります。

| Issue | 内容 | なぜ人間が必要か |
| --- | --- | --- |
| [#2](../../issues/2) | エンジン用シークレット `COPILOT_GITHUB_TOKEN` の登録 | PAT の発行とシークレット登録は権限保有者にしかできない |
| [#3](../../issues/3) | Actions が PR を作成できるようリポジトリ設定を変更 | Settings の変更は管理者権限が必要 |
| [#4](../../issues/4) | `.lock.yml` の再コンパイル漏れを検知する仕組みの整備 | CI コストとの兼ね合いで運用方針の判断が必要 |
| [#5](../../issues/5) | 5 つのワークフローの初回実行と出力・コストの確認 | 出力品質と実行コストの許容判断は人間が行う |

---

## 4. ディレクトリ構成

```
.
├── .github/workflows/
│   ├── ci.yml                    # 通常の CI (Gradle build)
│   ├── issue-triage.md           # ここから 6 つが Agentic Workflow
│   ├── issue-triage.lock.yml     # ← gh aw compile による生成物
│   ├── ci-doctor.md / .lock.yml
│   ├── tech-debt-hunter.md / .lock.yml
│   ├── test-gap-filler.md / .lock.yml
│   ├── security-review.md / .lock.yml
│   └── issue-auto-fix.md / .lock.yml
├── build.gradle
├── settings.gradle
└── src/
    ├── main/java/com/example/bookshelf/
    ├── main/resources/           # application.yml / data.sql
    └── test/java/com/example/bookshelf/
```

## 5. デモの進め方（例）

1. `./gradlew build` でアプリが動くことを確認する。
2. 「延滞者にリマインドしたい」といった Issue を作成し、`issue-triage` がラベルとコメントを付ける様子を見る。
3. `gh aw run tech-debt-hunter` を実行し、TODO 棚卸し Issue が起票されるのを確認する。
4. Issue に `/add-tests` とコメントし、テスト追加 PR が作られるのを確認する。
5. わざとテストを壊して main に push し、`ci-doctor` が原因調査 Issue を起票するのを確認する。
