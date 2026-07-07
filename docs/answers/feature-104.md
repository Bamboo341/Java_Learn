# 解答 T-104: 定期取引の自動計上

> ⚠️ **ネタバレ注意** — このファイルは解答の一例です。受け入れ条件を満たしていれば、違う実装でも正解です。

## 設計方針(なぜその置き場所か)

- 定期取引のマスタは新テーブル `recurring_transactions` に置き、既存の3層(model / repository / service / ui)と同じ形で一式を増やします。**既存アーキテクチャの「もう1周」を自分で作る**のがこの課題の実質的なテーマです。
- **二重計上の防止(核心)**は、マスタ自身に「どの月まで計上済みか」を記録する `last_posted_month` 列を持たせる方式を採ります。
  - 対案「取引テーブルを検索して当月分があるか調べる」は、ユーザーが計上済み取引を編集・削除しただけで判定が壊れます(消したらまた勝手に復活する!)。**判定用の状態は、ユーザーが自由に触るデータと分離する**のが定石です。
- 「起動時に計上する」処理はServiceのメソッド(`postDueTransactions`)にし、`Main` がUI表示前に1回呼びます。日付判定のロジックがServiceにあれば、日付を引数にしてテストできます。

## 変更ファイル一覧

| ファイル | 変更内容 |
|---|---|
| `infra/DatabaseManager.java` | migrateに新テーブルのCREATE(IF NOT EXISTS)追加 |
| `model/RecurringTransaction.java`(新規) | マスタのモデル |
| `repository/RecurringTransactionRepository.java`+Jdbc実装(新規) | マスタのCRUDと計上済み月の更新 |
| `service/RecurringTransactionService.java`(新規) | 計上判定・実行・二重計上防止 |
| `Main.java` | 起動時に `postDueTransactions(LocalDate.now())` を呼ぶ |
| `ui/RecurringDialog.java`(新規)+`MainFrame.java` | 最低限の管理画面とメニュー項目 |
| 各テスト | 計上判定のテスト(インメモリ偽物を追加して) |

## 実装例(要点のみ)

### テーブル

```sql
CREATE TABLE IF NOT EXISTS recurring_transactions (
    id                IDENTITY PRIMARY KEY,
    name              VARCHAR(50) NOT NULL,       -- 例: 家賃
    amount            INT NOT NULL,
    type              VARCHAR(10) NOT NULL,
    category_id       BIGINT NOT NULL REFERENCES categories(id),
    day_of_month      INT NOT NULL,               -- 毎月の計上日(1〜31)
    last_posted_month VARCHAR(7)                  -- 計上済みの月 '2026-07'。未計上ならNULL
);
```

### 計上ロジック(Service)

```java
/**
 * 計上日を過ぎていて未計上の定期取引を、当月の取引として登録する。
 * アプリ起動時に一度呼ばれる。todayを引数にしてあるのはテストのため。
 */
public void postDueTransactions(LocalDate today) {
    YearMonth currentMonth = YearMonth.from(today);
    for (RecurringTransaction recurring : recurringRepository.findAll()) {
        // 31日設定で2月のような短い月 → その月の末日に丸める
        int day = Math.min(recurring.getDayOfMonth(), currentMonth.lengthOfMonth());
        LocalDate postingDate = currentMonth.atDay(day);

        if (today.isBefore(postingDate)) {
            continue;  // まだ計上日が来ていない
        }
        if (currentMonth.equals(recurring.getLastPostedMonth())) {
            continue;  // 当月分は計上済み(二重計上の防止)
        }
        transactionRepository.insert(new Transaction(postingDate, recurring.getAmount(),
                recurring.getType(), recurring.getCategoryId(),
                recurring.getName() + "(自動計上)"));
        recurringRepository.updateLastPostedMonth(recurring.getId(), currentMonth);
    }
}
```

### テスト例(インメモリ偽物を使う)

```java
@Test
@DisplayName("同じ月に2回起動しても二重計上されない")
void postDueTransactionsDoesNotPostTwiceInSameMonth() {
    long id = recurringRepository.insert(rent(82000, 1));  // 毎月1日の家賃

    service.postDueTransactions(LocalDate.of(2026, 7, 5));
    service.postDueTransactions(LocalDate.of(2026, 7, 20));  // 同月2回目

    assertEquals(1, transactionRepository.findByConditions(
            YearMonth.of(2026, 7), null, null).size());
}

@Test
@DisplayName("月が変われば新しい月の分が計上される")
void postDueTransactionsPostsForNewMonth() {
    long id = recurringRepository.insert(rent(82000, 1));

    service.postDueTransactions(LocalDate.of(2026, 7, 5));
    service.postDueTransactions(LocalDate.of(2026, 8, 2));

    assertEquals(2, transactionRepository.findByConditions(null, null, null).size());
}

@Test
@DisplayName("計上日がまだ来ていなければ計上されない")
void postDueTransactionsSkipsBeforePostingDay() {
    long id = recurringRepository.insert(rent(82000, 25));  // 毎月25日

    service.postDueTransactions(LocalDate.of(2026, 7, 10));

    assertTrue(transactionRepository.findByConditions(null, null, null).isEmpty());
}
```

## ハマりどころ

- **判定を「取引の存在」でやってしまう。**自動計上した取引をユーザーが削除すると、次回起動でまた計上されます(ゾンビ復活)。マスタ側に状態(last_posted_month)を持たせれば、ユーザー操作の影響を受けません。
- **存在しない日(31日など)。**`YearMonth.atDay(31)` は2月で例外になります。`lengthOfMonth()` で丸めるのを忘れずに。テストに2月のケースを足すと安心です。
- **「挿入」と「計上済みマーク」は2つの別クエリ**なので、間でアプリが落ちると片方だけ実行された状態になり得ます。厳密にはトランザクション(コミット/ロールバック)で束ねるべきところです。この教材の規模では許容しますが、「ここが甘い」と認識しておくことが大切です。
- 起動時処理は `Main` の**UI表示前**(EDTの外)で呼びます。処理は軽いのでSwingWorkerまでは不要ですが、定期取引が大量にあると起動が遅くなる構造ではあります。
