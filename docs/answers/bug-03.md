# 解答 BUG-03: 月末日の支出が月次集計に含まれない

> ⚠️ **ネタバレ注意** — このファイルは解答です。自分の仮説を立て、検証してから読んでください。

## 症状の整理

- 5月31日の支出が、5月の**月次集計には**含まれない
- 一覧画面で5月にフィルタすると、5月31日の取引は**表示される**
- `./gradlew test` は全部グリーン

## 調査の思考過程

1. **「一覧は正しく、集計だけ間違う」**という対比が最大の手がかり。両者の実装を見比べる方針を立てる。
   - 一覧の絞り込み: `JdbcTransactionRepository.findByConditions()`
   - 月次集計: `JdbcTransactionRepository.summarizeMonth()`
2. `findByConditions` の年月条件は「月初以上・**月末以下**」で絞っている。
3. 一方 `summarizeMonth` の月の判定を見ると、「月初以上・**月末未満**」になっている。`未満` なので、ちょうど月末日(5月31日)のデータだけが弾かれる。
4. 数字でも裏を取る。一覧フィルタの5月支出合計と、集計画面の支出合計の差額が、ちょうど5月31日の取引の合計(サンプルデータでは 1,200円+3,000円=4,200円)と一致する。仮説と証拠が揃った。

## 真因

`summarizeMonth()` の日付範囲の**境界条件の誤り**。「月末以下(<=)」とすべきところが「月末未満(<)」になっており、月末日ちょうどの取引が集計から漏れる。いわゆる**境界値バグ**(off-by-one)である。

## 修正内容

`src/main/java/com/example/kakeibo/repository/JdbcTransactionRepository.java` の `summarizeMonth()` 内、対象月の判定:

```diff
-            // 対象月(月初以上・月末未満)の取引だけを集計する
-            if (transaction.getDate().isBefore(firstDay) || !transaction.getDate().isBefore(lastDay)) {
+            // 対象月(月初以上・月末以下)の取引だけを集計する
+            if (transaction.getDate().isBefore(firstDay) || transaction.getDate().isAfter(lastDay)) {
                 continue;
             }
```

`!isBefore(lastDay)` は「月末日**以降**を除外」= 月末日そのものまで捨ててしまいます。正しくは `isAfter(lastDay)`(「月末日**より後**を除外」)です。

> 補足: もしBUG-05を先に修正してSQL集計に戻している場合は、SQLの範囲条件が `transaction_date <= ?`(以下)になっていることを確認してください。`<`(未満)だと同じバグになります。日付範囲の「以上・以下」は、書き方がJavaでもSQLでも常に境界値バグの発生地点です。

## なぜテストをすり抜けたのか(このバグの核心)

`JdbcTransactionRepositoryTest` の集計テストを見てください。使われている日付は **10日・15日・20日・25日……すべて月の真ん中**です。月初(1日)や月末(30日・31日)のデータがひとつもないため、境界がどちらに倒れていてもテストは通ってしまいます。

**テストが緑であることは「テストが確認した範囲では正しい」という意味でしかありません。** 範囲の端(境界値)は、バグが最も出やすいのにテストが最も漏れやすい場所です。

## 再発防止テスト

境界値をピンポイントで固定するテストを追加します(`JdbcTransactionRepositoryTest` へ)。

```java
@Test
@DisplayName("summarizeMonth: 月初日と月末日の取引も集計に含まれる(境界値)")
void summarizeMonthIncludesFirstAndLastDayOfMonth() {
    insertTransaction("2026-06-01", 1000, TransactionType.EXPENSE, foodCategoryId, "月初");
    insertTransaction("2026-06-30", 2000, TransactionType.EXPENSE, foodCategoryId, "月末");
    insertTransaction("2026-07-01", 9999, TransactionType.EXPENSE, foodCategoryId, "翌月(対象外)");

    MonthlySummary summary = repository.summarizeMonth(YearMonth.of(2026, 6));

    assertEquals(3000, summary.getExpenseTotal());  // 月初+月末の両方が入る
}
```

このテストはバグ入りの実装では赤(2000円が漏れて1000円になる)、修正後は緑になります。

## この型のバグから得る教訓

- **境界値(範囲の端・0件・最大値)は、テスト設計で最優先に狙う場所。**「真ん中」のテストをいくつ増やしても境界は守れない。
- 「AとBで同じはずの数字が合わない」ときは、**AとBの実装を並べて見比べる**のが早い。
- 範囲条件を書くとき・読むときは「以上?より大きい?以下?未満?」を声に出して確認する習慣を。
