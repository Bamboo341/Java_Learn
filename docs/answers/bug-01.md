# 解答 BUG-01: 使用中カテゴリの削除でクラッシュ

> ⚠️ **ネタバレ注意** — このファイルは解答です。自分の仮説を立て、検証してから読んでください。

## 症状の整理

- 取引で使用中のカテゴリを削除しようとすると、アプリがエラーを起こす(コンソールに長いスタックトレースが出て、操作が正常に完了しない)
- 期待は「使用中のため削除できません」という案内

## 調査の思考過程

1. **ヒントどおり `./gradlew test` を実行する。** すると `CategoryServiceTest > deleteCategory: 取引で使用中のカテゴリは削除できない` が**赤**になっている。実はこの時点で「Service層の削除まわりに問題がある」とほぼ特定できてしまう。テストは最強の手がかりである。
2. 赤いテストを開くと、「使用中カテゴリを消そうとしたら `ValidationException` が飛ぶこと」を期待している。つまり本来はServiceが止めるはずの操作だ。
3. 実際にアプリで再現し、コンソールのスタックトレースを読む。`Caused by:` をたどると `JdbcSQLIntegrityConstraintViolationException`(参照整合性制約違反)が出ている。`transactions` テーブルが `categories` を**外部キー**で参照しているため、使用中の親行は消せない——DBが最後の砦として拒否したのだ。
4. `CategoryService.deleteCategory()` を開くと、使用中チェックがなく、いきなりRepositoryの `delete` を呼んでいる。

## 真因

`CategoryService.deleteCategory()` から**使用中チェックが消えている**こと。チェックがないままDBに削除を投げると、外部キー制約違反の `SQLException`(をラップした `DataAccessException`)が発生する。呼び出し元の `CategoryDialog.onDelete()` は `ValidationException` しか捕捉していないため、この例外は誰にも処理されず、イベントスレッドまで貫通してアプリがエラー状態になる。

## 修正内容

`src/main/java/com/example/kakeibo/service/CategoryService.java`

```diff
     public void deleteCategory(long id) throws ValidationException {
+        // 取引から使われているカテゴリを消すと、外部キー制約違反(DBエラー)になるうえ、
+        // 過去の取引の分類が失われてしまう。削除前に必ず使用中チェックを行う。
+        int usageCount = transactionRepository.countByCategoryId(id);
+        if (usageCount > 0) {
+            throw new ValidationException(
+                    "このカテゴリは" + usageCount + "件の取引で使用中のため削除できません");
+        }
         categoryRepository.delete(id);
     }
```

修正後、`./gradlew test` が全件グリーンになり、アプリでも「〜件の取引で使用中のため削除できません」と警告ダイアログが出ることを確認します。

## 再発防止テスト

このバグは既存テストが検知しました(だから5つのバグの中で唯一、テスト実行だけで場所の当たりが付きます)。該当テストを読み直しておきましょう。

```java
@Test
@DisplayName("deleteCategory: 取引で使用中のカテゴリは削除できない")
void deleteCategoryRejectsCategoryInUse() {
    long id = categoryRepository.insert(new Category(null, "食費", TransactionType.EXPENSE, 1));
    transactionRepository.insert(new Transaction(LocalDate.of(2026, 6, 15), 3000,
            TransactionType.EXPENSE, id, "スーパー"));

    ValidationException e = assertThrows(ValidationException.class,
            () -> service.deleteCategory(id));
    assertTrue(e.getMessage().contains("使用中"));
    assertTrue(categoryRepository.findById(id).isPresent());  // 消えていないことも確認
}
```

## この型のバグから得る教訓

- **バグ調査は「まずテストを回す」から始める。** 赤いテストは、症状よりもはるかに正確に場所を教えてくれる。
- **業務ルール(使用中は消せない)はServiceの責務。** DBの外部キー制約は最後の砦であって、ユーザー案内の手段ではない。
- 例外には「想定内(ValidationException)」と「想定外(DataAccessException)」がある。想定外の例外が貫通したときに何が起きるか、今回の症状で体感できたはず。
