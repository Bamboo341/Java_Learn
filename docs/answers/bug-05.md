# 解答 BUG-05: 月次集計を開くと画面が固まる(大量データ時)

> ⚠️ **ネタバレ注意** — このファイルは解答です。自分の仮説を立て、検証してから読んでください。

## 症状の整理

- 5万件投入後に月次集計を開くと、画面全体が数秒以上固まる(ウィンドウ移動もできない)
- データが少ないうちは気にならなかった
- チケットのヒントいわく「なぜ遅いのか」と「なぜ固まるのか」は別問題

## 調査の思考過程

1. **まず計測する。**推測より計測。`summarizeMonth` の呼び出し前後に時計を仕込む。

   ```java
   long start = System.currentTimeMillis();
   MonthlySummary summary = transactionService.summarizeMonth(month);
   System.out.println("集計: " + (System.currentTimeMillis() - start) + "ms");
   ```

   5万件の環境で数千ms(参考実測: 約3,200ms)。少量データでは数ms。**データ量に比例して遅くなる処理**だと分かる。
2. `JdbcTransactionRepository.summarizeMonth()` を読む。すると:
   - `findByConditions(null, null, null)` で**全取引をメモリにロード**している(対象月以外も全部!)
   - ループの中で1件ごとに `findCategoryName()` を呼び、**取引1件につきSQLを1回**発行している
   
   後者は俗に **N+1問題** と呼ばれる典型的なアンチパターンだ(1回のクエリで済むはずが、N件分の追加クエリが走る)。5万件なら5万回のSELECTになる。
3. 次に「なぜ固まるのか」。遅い処理があっても、画面が完全に凍る必然はない。`SummaryDialog.loadSummary()` を見ると、この重い集計を**そのまま(=EDT上で)呼んでいる**。Swingではイベントディスパッチスレッド(EDT)という1本のスレッドが描画とボタン処理をすべて担うため、EDTを数秒占有すれば、その間アプリは再描画も応答も一切できない。
4. まとめると、**遅さの原因(全件ロード+N+1)**と**固まる原因(EDTで実行)**の2つが重なっている。

## 真因

1. `JdbcTransactionRepository.summarizeMonth()` が、SQLに任せるべき集計を「全件ロード+1件ずつカテゴリ名を都度SELECT(N+1)」のJavaループで行っている
2. `SummaryDialog.loadSummary()` がその重い処理をEDT上で直接実行している

## 修正内容

### 修正1: 集計をSQL(GROUP BY)に戻す

DBは集計が得意です。合計はSQLに計算させ、カテゴリ名もJOINで一緒に取れば、クエリは合計2回で済みます。

`src/main/java/com/example/kakeibo/repository/JdbcTransactionRepository.java` — Javaループ実装(`summarizeMonth` と補助メソッド `findCategoryName`)を丸ごと次に置き換えます。

```java
@Override
public MonthlySummary summarizeMonth(YearMonth yearMonth) {
    // 集計はJavaのループではなくSQL(GROUP BY)で行う。
    // 全件をメモリに読み込む必要がなく、データが増えても性能が落ちにくい。
    Date firstDay = Date.valueOf(yearMonth.atDay(1));
    Date lastDay = Date.valueOf(yearMonth.atEndOfMonth());

    String totalsSql = "SELECT type, SUM(amount) AS total FROM transactions"
            + " WHERE transaction_date >= ? AND transaction_date <= ?"
            + " GROUP BY type";
    String categorySql = "SELECT c.name, SUM(t.amount) AS total"
            + " FROM transactions t JOIN categories c ON t.category_id = c.id"
            + " WHERE t.type = ? AND t.transaction_date >= ? AND t.transaction_date <= ?"
            + " GROUP BY c.name ORDER BY total DESC";  // 支出の大きいカテゴリが上に来るように

    try (Connection conn = databaseManager.getConnection()) {
        int incomeTotal = 0;
        int expenseTotal = 0;
        try (PreparedStatement ps = conn.prepareStatement(totalsSql)) {
            ps.setDate(1, firstDay);
            ps.setDate(2, lastDay);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TransactionType type = TransactionType.fromDbValue(rs.getString("type"));
                    if (type == TransactionType.INCOME) {
                        incomeTotal = rs.getInt("total");
                    } else {
                        expenseTotal = rs.getInt("total");
                    }
                }
            }
        }

        List<CategoryExpense> categoryExpenses = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(categorySql)) {
            ps.setString(1, TransactionType.EXPENSE.toDbValue());
            ps.setDate(2, firstDay);
            ps.setDate(3, lastDay);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    categoryExpenses.add(new CategoryExpense(rs.getString("name"), rs.getInt("total")));
                }
            }
        }

        return new MonthlySummary(yearMonth, incomeTotal, expenseTotal, categoryExpenses);
    } catch (SQLException e) {
        throw new DataAccessException("月次集計に失敗しました (" + yearMonth + ")", e);
    }
}
```

> 注意: 日付の範囲条件は「以上(>=)・以下(<=)」です。BUG-03と同じ境界値バグをSQLで再生産しないこと。

### 修正2: 集計をSwingWorkerでバックグラウンド実行する

`src/main/java/com/example/kakeibo/ui/SummaryDialog.java` — `loadSummary` を次のようにします。

```java
private void loadSummary(YearMonth month) {
    // 読み込み中であることが分かるように表示を切り替えておく
    incomeLabel.setText("集計中...");
    expenseLabel.setText("集計中...");
    balanceLabel.setText("集計中...");
    balanceLabel.setForeground(Color.BLACK);
    breakdownModel.setRowCount(0);

    // [設計意図] 集計はSwingWorkerでバックグラウンド実行する。Swingの描画や
    // ボタン操作はEDT(イベントディスパッチスレッド)という1本のスレッドが
    // 処理しているため、EDT上で時間のかかる処理をすると画面全体が固まる。
    // SwingWorkerを使うと、doInBackgroundは別スレッドで、done(画面更新)は
    // EDTで実行され、「重い処理は裏で・画面更新は表で」を安全に実現できる。
    SwingWorker<MonthlySummary, Void> worker = new SwingWorker<>() {
        @Override
        protected MonthlySummary doInBackground() {
            return transactionService.summarizeMonth(month);
        }

        @Override
        protected void done() {
            try {
                showSummary(get());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "月次集計の取得に失敗", e);
                JOptionPane.showMessageDialog(SummaryDialog.this,
                        "集計中にエラーが発生しました。\n" + e.getMessage(),
                        "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    };
    worker.execute();
}
```

**2つの修正はどちらも必要**です。SQL化だけだと、将来また重い処理が入ったとき同じ事故が起きます。SwingWorker化だけだと、固まりはしないものの5万件で数秒待たされ、N+1という根本の無駄も残ります。

## なぜテストをすり抜けたのか

Javaループ版の集計も**結果自体は正しい**ため、正しさを見るテストはすべて通ります。遅さはデータ量が閾値を超えて初めて顕在化する性質で、少量データの単体テストでは観測できません。性能問題は「単体テストの緑」とは別の軸(計測・負荷試験)で守る必要があります。

## 再発防止(手動確認手順で代替)

```
【集計の性能・応答確認】(docs/04で5万件を投入した状態で)
1. 集計前後の時間をログ出力する等で計測し、5万件で1秒未満であることを確認
2. 月次集計を開いた直後に、ウィンドウのドラッグ移動と「閉じる」ボタンが効くことを確認
   (集計中でも画面が応答する=EDTが空いている証拠)
3. 年月を素早く切り替えても固まらないことを確認
```

## この型のバグから得る教訓

- **性能の議論は計測から。**「遅い気がする」ではなく、どこで何ミリ秒かを数字で押さえる。
- **N+1問題**はDBアクセスの頻出アンチパターン。「ループの中でSQLを呼んでいたら疑え」。集計・結合はDBの得意技なので、SQLに寄せるのが定石。
- **EDT(UIスレッド)で重い処理をしない。**これはSwingに限らず、Android・JavaFX・ブラウザ(メインスレッド)まで共通する、UIプログラミングの大原則。
- データ量が少ない開発環境では性能バグは眠っている。**本番相当のデータ量で触ってみる**ことの価値を体感してほしい。
