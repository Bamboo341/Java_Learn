# 解答 T-101: メモのキーワード検索

> ⚠️ **ネタバレ注意** — このファイルは解答の一例です。受け入れ条件を満たしていれば、違う実装でも正解です。

## 設計方針(なぜその置き場所か)

「メモで絞り込む」は既存の「年月・カテゴリ・区分で絞り込む」の仲間なので、**新しい仕組みは作らず、既存の検索経路(UI→Service→Repository)に条件を1つ増やす**のが素直です。

- **絞り込みはSQLのWHERE句で行う**(Repository)。全件をJavaに読み込んでから`contains`で選別する手もありますが、絞り込みはDBの得意技であり、データが増えても性能が落ちません(BUG-05の教訓)。
- LIKEの特殊文字のエスケープは**SQLを知っているRepositoryの内部事情**なので、Jdbc実装の中に閉じ込めます。UIやServiceは「キーワードそのもの」だけを受け渡します。

## 変更ファイル一覧

| ファイル | 変更内容 |
|---|---|
| `repository/TransactionRepository.java` | `findByConditions` に `memoKeyword` 引数を追加 |
| `repository/JdbcTransactionRepository.java` | LIKE条件とエスケープ処理を追加 |
| `service/TransactionService.java` | `search` に引数を追加して素通し |
| `ui/MainFrame.java` | キーワード入力欄の追加と検索条件への反映 |
| `test/.../InMemoryTransactionRepository.java` | 偽物にも同じ引数を追加(containsで代用) |
| `test/.../JdbcTransactionRepositoryTest.java` ほか | 検索条件のテスト追加、既存テストの引数修正 |

interfaceのシグネチャを変えると、実装クラス・偽物・呼び出し元がすべてコンパイルエラーになります。**コンパイラがTODOリストを作ってくれる**と捉えて、順に直していきましょう。

## 実装例

### Repository(Jdbc実装)

```java
// findByConditions のWHERE句組み立てに追加
if (memoKeyword != null && !memoKeyword.isBlank()) {
    // ESCAPE '\' を宣言したうえで、キーワード中の特殊文字をエスケープする
    conditions.add("memo LIKE ? ESCAPE '\\'");
    params.add("%" + escapeLike(memoKeyword.trim()) + "%");
}
```

```java
/**
 * LIKE検索の特殊文字を無効化(エスケープ)する。
 * %は「任意の文字列」、_は「任意の1文字」という意味を持つため、
 * そのまま渡すとユーザーの意図と違う検索になってしまう。
 */
private String escapeLike(String keyword) {
    return keyword.replace("\\", "\\\\")   // エスケープ文字自身を最初に
                  .replace("%", "\\%")
                  .replace("_", "\\_");
}
```

### MainFrame(抜粋)

```java
private final JTextField keywordField = new JTextField(10);

// buildFilterPanel() に追加
panel.add(new JLabel("メモ:"));
keywordField.getDocument().addDocumentListener(new DocumentListener() {
    @Override public void insertUpdate(DocumentEvent e) { onFilterChanged(); }
    @Override public void removeUpdate(DocumentEvent e) { onFilterChanged(); }
    @Override public void changedUpdate(DocumentEvent e) { onFilterChanged(); }
});
panel.add(keywordField);

// reloadTransactions() の検索呼び出し
List<Transaction> transactions = transactionService.search(
        getSelectedMonth(), getSelectedCategoryId(), getSelectedType(),
        keywordField.getText());
```

空欄のときの「絞り込みなし」は、Repository側の `isBlank()` 判定が受け持ちます。

### テスト例

```java
@Test
@DisplayName("findByConditions: メモのキーワードで部分一致検索できる")
void findByConditionsFiltersByMemoKeyword() {
    insertTransaction("2026-06-06", 10000, TransactionType.EXPENSE, foodCategoryId, "通勤定期(1ヶ月)");
    insertTransaction("2026-06-10", 3000, TransactionType.EXPENSE, foodCategoryId, "スーパー");

    List<Transaction> results = repository.findByConditions(null, null, null, "定期");

    assertEquals(1, results.size());
    assertEquals("通勤定期(1ヶ月)", results.get(0).getMemo());
}

@Test
@DisplayName("findByConditions: %や_はワイルドカードではなく文字として検索される")
void findByConditionsEscapesLikeWildcards() {
    insertTransaction("2026-06-10", 1000, TransactionType.EXPENSE, foodCategoryId, "達成率100%の記念");
    insertTransaction("2026-06-11", 2000, TransactionType.EXPENSE, foodCategoryId, "100点の買い物");

    // エスケープしないと「100」+任意文字列 に両方マッチしてしまう
    List<Transaction> results = repository.findByConditions(null, null, null, "100%");

    assertEquals(1, results.size());
}
```

## ハマりどころ

- **LIKEエスケープの見落とし。**受け入れ条件の「%や_」はここを確かめるためのものです。`ESCAPE '\'` の宣言と、キーワード側の置換(`\`→`\\`を最初に!順番を逆にすると二重エスケープになります)をセットで。
- **interface変更の波及。**偽物(InMemoryTransactionRepository)の直し忘れはテストのコンパイルエラーで気づけます。
- DocumentListenerは1文字ごとに再検索します。この規模では問題ありませんが、「Enterで確定」方式(`keywordField.addActionListener`)にする判断もアリです。どちらでも受け入れ条件は満たせます。
