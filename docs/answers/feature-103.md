# 解答 T-103: カテゴリ別の月次予算

> ⚠️ **ネタバレ注意** — このファイルは解答の一例です。受け入れ条件を満たしていれば、違う実装でも正解です。

## 設計方針(なぜその置き場所か)

- **予算はカテゴリの属性**なので、`categories` テーブルに `budget` 列(NULL可)を追加します。「予算」専用テーブルを作る手もありますが、「カテゴリ1つに月次予算1つ」というこの仕様では列追加が最小です(月ごとに違う予算は対象外、と明記されているのがヒント)。
- **この課題の核心はスキーマ移行(マイグレーション)です。**`schema.sql` は初回起動時にしか実行されないため、既存ユーザーのDBには**起動のたびに冪等に適用できる移行処理**を用意します。置き場所はスキーマ初期化と同じ責務を持つ `DatabaseManager` が適任です。
- 予算の入力チェック(1円以上など)はServiceに置きます(このアプリの一貫した方針)。
- 「予算・実績・残額」は月次集計の一部なので、集計SQL(カテゴリ別支出)にJOINで `budget` を同乗させ、`CategoryExpense` DTOに載せて画面へ運びます。

## 変更ファイル一覧

| ファイル | 変更内容 |
|---|---|
| `resources/schema.sql` | `budget INT` 列を追加(新規インストール用) |
| `infra/DatabaseManager.java` | 既存DB向けの冪等なマイグレーション処理を追加 |
| `model/Category.java` | `budget` フィールド(Integer・null許容)追加 |
| `model/CategoryExpense.java` | `budget` フィールド追加(集計表示用) |
| `repository/CategoryRepository.java`+Jdbc実装 | budgetの読み書き、`updateBudget` 追加 |
| `repository/JdbcTransactionRepository.java` | 集計SQLに `c.budget` を追加 |
| `service/CategoryService.java` | `setBudget`(バリデーション付き)追加 |
| `ui/CategoryDialog.java` | 「予算設定」ボタン追加 |
| `ui/SummaryDialog.java` | 予算・実績・残額列と超過の赤表示 |
| 各テスト | budget対応・予算ルールのテスト追加 |

## 実装例(要点のみ)

### マイグレーション(この課題の山場)

```java
// DatabaseManager
public void initialize() {
    try (Connection conn = getConnection()) {
        if (!isInitialized(conn)) {
            runScript(conn, "/schema.sql");
            runScript(conn, "/data.sql");
        }
        migrate(conn);  // ← 初期化済みかどうかに関わらず、毎回実行する
    } catch (SQLException e) {
        throw new DataAccessException("データベースの初期化に失敗しました", e);
    }
}

/**
 * 既存のDBファイルを最新のスキーマへ追いつかせる。
 * IF NOT EXISTS付きなので、何度実行しても安全(冪等)。
 */
private void migrate(Connection conn) throws SQLException {
    try (Statement st = conn.createStatement()) {
        // v1.1: カテゴリ別の月次予算
        st.execute("ALTER TABLE categories ADD COLUMN IF NOT EXISTS budget INT");
    }
}
```

`schema.sql` 側にも `budget INT`(コメント付き)を追加します。「新規はschema.sql、既存はmigrate」の**二重管理になる**点は覚えておいてください(実務ではFlywayなどの移行ツールでこの二重管理を解消しますが、仕組みを一度手作りしておくと道具のありがたみが分かります)。

### NULL許容カラムの読み取り(ハマりどころ先取り)

```java
// JdbcCategoryRepository.mapRow
(Integer) rs.getObject("budget")   // rs.getInt だとNULLが0円になってしまう!
```

### Service

```java
/** カテゴリに月次予算を設定する。nullで解除。 */
public void setBudget(long categoryId, Integer budget) throws ValidationException {
    Category current = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new ValidationException("対象のカテゴリが見つかりません"));
    if (current.getType() != TransactionType.EXPENSE) {
        throw new ValidationException("予算は支出カテゴリにのみ設定できます");
    }
    if (budget != null && budget <= 0) {
        throw new ValidationException("予算は1円以上で入力してください(解除する場合は空欄)");
    }
    categoryRepository.updateBudget(categoryId, budget);
}
```

### 集計SQLと画面

```sql
SELECT c.name, c.budget, SUM(t.amount) AS total
FROM transactions t JOIN categories c ON t.category_id = c.id
WHERE t.type = ? AND t.transaction_date >= ? AND t.transaction_date <= ?
GROUP BY c.name, c.budget ORDER BY total DESC
```

SummaryDialogのテーブルを「カテゴリ / 予算 / 実績 / 残額 / 構成比」にし、行の描画で残額がマイナスなら赤にします。

```java
breakdownTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(
                table, value, isSelected, hasFocus, row, column);
        // モデルに保持しておいた「超過フラグ」を見て行の色を決める
        c.setForeground(isOverBudget(row) ? Color.RED : Color.BLACK);
        return c;
    }
});
```

## ハマりどころ

- **「schema.sqlを直したのに反映されない」**——初期化は初回しか走らない、というこの課題の核心。migrate方式のほか「DBを消してもらう」という運用逃げもありますが、ユーザーのデータが消えるため受け入れ条件を満たしません。
- **`rs.getInt` のNULL問題。**NULLは0になって返るため、「予算0円」と「予算未設定」の区別が消えます。`getObject` を使うこと。書き込み側も `setObject(i, budget)`(nullをそのまま渡せる)が楽です。
- **支出が0円の月は、そのカテゴリが集計に出てきません**(JOINが取引起点のため)。「予算があるのに使っていないカテゴリも表示したい」ならLEFT JOIN起点をカテゴリ側にする改造が要りますが、受け入れ条件外なので「表示されない」仕様でOKです(答え合わせのとき自分がどちらの仕様にしたか意識しておくこと)。
- 集計SQLの`GROUP BY`に`c.budget`を足し忘れると、H2が構文エラーにします(集計キー以外の裸の列は選べない)。
