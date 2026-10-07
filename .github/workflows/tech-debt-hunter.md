---
description: |
  リポジトリ内の TODO コメントや技術的負債を毎週棚卸しし、
  優先度付きのレポート Issue を作成するワークフロー。

on:
  schedule: weekly
  workflow_dispatch:

permissions:
  actions: read
  copilot-requests: write
  contents: read
  issues: read
  pull-requests: read

network: defaults

jobs:
  change_gate:
    runs-on: ubuntu-latest
    permissions:
      actions: read
      contents: read
    outputs:
      run_agent: ${{ steps.check.outputs.run_agent }}
    steps:
      - uses: actions/checkout@v7.0.1
        with:
          ref: ${{ github.sha }}
          fetch-depth: 0
      - name: 監視対象の変更を確認
        id: check
        env:
          GH_TOKEN: ${{ github.token }}
          CURRENT_SHA: ${{ github.sha }}
          EVENT_NAME: ${{ github.event_name }}
          REF: ${{ github.ref }}
        run: |
          set -euo pipefail

          write_result() {
            echo "run_agent=$1" >> "$GITHUB_OUTPUT"
            echo "$2" >> "$GITHUB_STEP_SUMMARY"
          }

          echo "調査対象 SHA: $CURRENT_SHA" >> "$GITHUB_STEP_SUMMARY"

          if [ "$EVENT_NAME" = "workflow_dispatch" ]; then
            echo "手動実行のため調査を実行します。" >> "$GITHUB_STEP_SUMMARY"
            write_result true "実行理由: 手動実行"
            exit 0
          fi

          if [ "$EVENT_NAME" != "schedule" ] || [ "$REF" != "refs/heads/main" ]; then
            echo "::error::予期しないイベントまたはブランチです: $EVENT_NAME ($REF)"
            exit 1
          fi

          if ! runs=$(gh api --paginate \
            --jq '.workflow_runs[] | select(.status == "completed" and .head_branch == "main" and (.event == "schedule" or .event == "workflow_dispatch")) | [.id, .head_sha] | @tsv' \
            "repos/$GITHUB_REPOSITORY/actions/workflows/tech-debt-hunter.lock.yml/runs?branch=main&status=completed&per_page=100"); then
            echo "::error::過去の調査実行履歴を取得できませんでした。"
            exit 1
          fi

          baseline_sha=""
          baseline_run_id=""
          while IFS=$'\t' read -r run_id run_sha; do
            [ -n "$run_id" ] || continue
            if ! agent_conclusion=$(gh api --paginate \
              --jq '.jobs[] | select(.name == "agent") | .conclusion' \
              "repos/$GITHUB_REPOSITORY/actions/runs/$run_id/jobs?per_page=100"); then
              echo "::error::実行 $run_id の調査ジョブ結果を取得できませんでした。"
              exit 1
            fi
            if [ "$agent_conclusion" = "success" ]; then
              baseline_sha=$run_sha
              baseline_run_id=$run_id
              break
            fi
          done <<< "$runs"

          if [ -z "$baseline_sha" ]; then
            echo "最後に正常完了した調査はありません。初回調査を実行します。" >> "$GITHUB_STEP_SUMMARY"
            write_result true "実行理由: 初回調査"
            exit 0
          fi

          echo "最後に正常完了した調査 SHA: $baseline_sha (run $baseline_run_id)" >> "$GITHUB_STEP_SUMMARY"
          set +e
          git diff --quiet "$baseline_sha" "$CURRENT_SHA" -- \
            src build.gradle settings.gradle gradle gradlew gradlew.bat \
            .github/workflows/tech-debt-hunter.md \
            AGENTS.md .github/agents .github/instructions .github/skills
          diff_status=$?
          set -e

          case "$diff_status" in
            0)
              echo "監視対象に未調査の変更はありません。AI とレポート作成をスキップします。" >> "$GITHUB_STEP_SUMMARY"
              write_result false "スキップ理由: 監視対象に変更なし"
              ;;
            1)
              echo "監視対象に未調査の変更があります。調査を実行します。" >> "$GITHUB_STEP_SUMMARY"
              write_result true "実行理由: 監視対象に変更あり"
              ;;
            *)
              echo "::error::調査対象コミットの比較に失敗しました (git diff exit $diff_status)。"
              exit "$diff_status"
              ;;
          esac

  agent:
    needs: [change_gate]
    if: needs.change_gate.outputs.run_agent == 'true'

tools:
  github:
    toolsets: [repos, issues, pull_requests]
  bash: true

safe-outputs:
  create-issue:
    title-prefix: "[tech-debt] "
    labels: [refactoring, automation]
    max: 1
    close-older-issues: true

timeout-minutes: 15
---

# 技術的負債の棚卸し

Java / Spring Boot 製の蔵書管理アプリ「bookshelf」の技術的負債を棚卸しし、レポート Issue を作成してください。

## 手順

1. `grep -rn "TODO" src` などでソースコード中の TODO / FIXME コメントを収集する。
2. 収集した各項目について、実際のコードを読んで内容と影響範囲を把握する。
3. 併せて以下の観点でリファクタリング候補を洗い出す。
   - 1 クラス・1 メソッドに責務が集中していないか（例: `BookshelfService`）
   - マジックナンバーやビジネスルールがハードコードされていないか
   - N+1 クエリやページングの欠如などパフォーマンス上の懸念がないか
   - テストが不足しているビジネスルールがないか
4. 既に対応中の Issue や PR がないかを検索し、重複を避ける。

## Issue に含める内容

- **サマリ**: 検出した項目数と全体所見
- **一覧表**: `| 優先度 | 対象ファイル:行 | 内容 | 提案する対応 |` の Markdown テーブル
- **優先度の根拠**: 影響範囲・実装コスト・リスクの観点で説明する
- **今週着手するなら**: おすすめの 1〜3 件と、その理由

## 注意事項

- 実在するファイルパスと行番号のみを記載する（推測で書かない）。
- コードの修正や PR 作成は行わない。レポートのみを作成する。
- 出力はすべて日本語で記述する。
