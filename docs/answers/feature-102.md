# 解答 T-102: フィルタ結果のCSVエクスポート

> ⚠️ **ネタバレ注意** — このファイルは解答の一例です。受け入れ条件を満たしていれば、違う実装でも正解です。

## 設計方針(なぜその置き場所か)

- **CSVの整形ロジックは、画面から独立した小さなクラス(`ui/CsvExporter`)に置きます。**理由はBUG-02の教訓そのもの——ダイアログやフレームのprivateメソッドに埋めるとテストできないからです。受け入れ条件に「整形ロジックにテスト」とあるのは、この設計を促すためです。
- 置き場所は`ui`パッケージとしました。「画面表示と同じ日本語ラベル(収入/支出)で出す」という**表示の都合**を担うため、Service層(業務ルール)より表示側が適切です。
- 「何をエクスポートするか」は**画面に表示中の内容**なので、データ源はMainFrameが持つ検索結果(TableModel経由)を使います。DBを再検索する方式だと、画面とファイルの内容がズレる可能性があります。

## 変更ファイル一覧

| ファイル | 変更内容 |
|---|---|
| `ui/CsvExporter.java`(新規) | CSV整形とファイル書き出し |
| `ui/MainFrame.java` | メニュー項目「CSVエクスポート」とJFileChooser処理 |
| `ui/TransactionTableModel.java` | 表示中の全取引を返すgetter(なければ)追加 |
| `test/.../CsvExporterTest.java`(新規) | 整形ロジックのテスト |

## 実装例

### CsvExporter(新規)

```java
package com.example.kakeibo.ui;

/**
 * 取引一覧をCSVファイルに書き出すクラス。
 * 整形ロジックを画面から分離してあるため、単体テストできる。
 */
public class CsvExporter {

    /** ExcelにUTF-8だと知らせるためのBOM(ファイル先頭に置く目印バイト) */
    private static final String BOM = "\uFEFF";
    private static final String HEADER = "日付,区分,カテゴリ,金額,メモ";

    /** 取引一覧をCSVファイルへ書き出す。 */
    public void export(Path path, List<Transaction> transactions,
                       Map<Long, String> categoryNames) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(BOM);
            writer.write(HEADER);
            writer.newLine();
            for (Transaction transaction : transactions) {
                writer.write(toCsvLine(transaction, categoryNames));
                writer.newLine();
            }
        }
    }

    /** 取引1件をCSVの1行に整形する(テストしやすいようpackage-privateにしてある)。 */
    String toCsvLine(Transaction t, Map<Long, String> categoryNames) {
        return String.join(",",
                escapeField(t.getDate().toString()),
                escapeField(t.getType().getLabel()),
                escapeField(categoryNames.getOrDefault(t.getCategoryId(), "(不明)")),
                escapeField(String.valueOf(t.getAmount())),
                escapeField(t.getMemo() == null ? "" : t.getMemo()));
    }

    /**
     * CSVのフィールドを必要に応じてダブルクォートで囲む(RFC 4180の規則)。
     * カンマ・引用符・改行を含む場合に囲み、引用符自身は2つ重ねる。
     */
    static String escapeField(String field) {
        if (field.contains(",") || field.contains("\"")
                || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
```

### MainFrame(抜粋)

```java
// メニューに追加
JMenuItem exportItem = new JMenuItem("CSVエクスポート");
exportItem.addActionListener(e -> onExportCsv());
menu.add(exportItem);
```

```java
private void onExportCsv() {
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new java.io.File("kakeibo.csv"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
        return;
    }
    try {
        // 画面に表示中の内容(現在のフィルタ結果)をそのまま出力する
        new CsvExporter().export(chooser.getSelectedFile().toPath(),
                tableModel.getTransactions(), currentCategoryNames());
        JOptionPane.showMessageDialog(this, "エクスポートが完了しました");
    } catch (IOException e) {
        logger.log(Level.SEVERE, "CSVエクスポートに失敗", e);
        JOptionPane.showMessageDialog(this, "ファイルの書き込みに失敗しました。\n" + e.getMessage(),
                "エラー", JOptionPane.ERROR_MESSAGE);
    }
}
```

(`tableModel.getTransactions()` は表示中の取引リストを返すgetterとして追加します。カテゴリ名の対応表は `reloadTransactions()` で作ったものをフィールドに保持しておくと使い回せます)

### テスト例

```java
@Test
@DisplayName("カンマや引用符を含むメモでも列が崩れない")
void escapeFieldQuotesSpecialCharacters() {
    assertEquals("そのまま", CsvExporter.escapeField("そのまま"));
    assertEquals("\"a,b\"", CsvExporter.escapeField("a,b"));
    assertEquals("\"彼は\"\"OK\"\"と言った\"", CsvExporter.escapeField("彼は\"OK\"と言った"));
}
```

## ハマりどころ

- **BOMの付け忘れ。**プログラム上は正しいUTF-8でも、ExcelでダブルクリックするとBOMがないと文字化けします。テキストエディタで開くと正常に見えるため、「Excelで開いて確認」という受け入れ条件を必ず実施すること。
- **クォート処理の自作ミス。**「カンマを含むときだけ囲む」だけだと、引用符入り・改行入りで壊れます。escapeFieldのような1箇所に規則を集約し、そこをテストで固めるのが安全です。
- **JFileChooserは上書き確認をしてくれません。**`File.exists()` を見て確認ダイアログを挟むと丁寧です(受け入れ条件外なのでお好みで)。
- 拡張子`.csv`の自動補完も同様に「あると親切」レベル。やりすぎて本筋(正しいCSV)を後回しにしないように。
