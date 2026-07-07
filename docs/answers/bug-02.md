# 解答 BUG-02: カンマ付き金額の入力でクラッシュ

> ⚠️ **ネタバレ注意** — このファイルは解答です。自分の仮説を立て、検証してから読んでください。

## 症状の整理

- 金額欄に「1,000」とカンマ付きで入力して保存すると、エラーで落ちる
- 期待は 1000円として登録されること

## 調査の思考過程

1. **再現してコンソールを見る。** 保存ボタンを押した瞬間、次のようなスタックトレースが出る。

   ```
   Exception in thread "AWT-EventQueue-0" java.lang.NumberFormatException: For input string: "1,000"
       at java.base/java.lang.Integer.parseInt(Integer.java:...)
       at com.example.kakeibo.ui.TransactionDialog.parseAmount(TransactionDialog.java:...)
       at com.example.kakeibo.ui.TransactionDialog.buildTransactionFromInput(TransactionDialog.java:...)
       at com.example.kakeibo.ui.TransactionDialog.onSave(TransactionDialog.java:...)
       ...
   ```

2. 読み方(02_バグ修正演習.md 3-1)のとおりに読む。1行目: `NumberFormatException` =「数値に変換できない文字列 "1,000" を変換しようとした」。`at` の並びから、**自分のコードで一番上**は `TransactionDialog.parseAmount`。行番号までわかる。
3. `parseAmount` を開くと、入力文字列をそのまま `Integer.parseInt` に渡している。`parseInt` はカンマを受け付けないので、"1,000" で必ず例外になる。
4. この `NumberFormatException` は**非検査例外**で、どこにもcatchがないため、イベントスレッド(スレッド名 `AWT-EventQueue-0` がその証拠)まで貫通した。

## 真因

`TransactionDialog.parseAmount()` に**入力の正規化(カンマ除去)と、数字以外を弾く防御**がなく、未捕捉の `NumberFormatException` が発生すること。

## 修正内容

`src/main/java/com/example/kakeibo/ui/TransactionDialog.java`

```diff
     private int parseAmount(String text) throws ValidationException {
         if (text == null || text.trim().isEmpty()) {
             throw new ValidationException("金額を入力してください");
         }
-        return Integer.parseInt(text.trim());
+        // 「1,000」のような桁区切りカンマ付きの入力も受け付けられるよう、先に取り除く
+        String normalized = text.trim().replace(",", "");
+        // 数字だけになったことを確かめてから変換する(想定外の文字での例外を防ぐ)
+        if (!normalized.matches("\\d+")) {
+            throw new ValidationException("金額は半角数字で入力してください");
+        }
+        return Integer.parseInt(normalized);
     }
```

ポイントは2段構えであること。①カンマは「ユーザーの善意の入力」なので受け入れて除去する。②それでも数字でないもの(例: "abc")は、例外を**起こす前に**チェックして、ユーザー向けメッセージ(ValidationException)に変換する。

## なぜテストをすり抜けたのか(重要)

`./gradlew test` は今回まったく反応しませんでした。理由は単純で、**`parseAmount` はダイアログ内のprivateメソッドで、どのテストからも呼ばれていない**からです。このアプリのUIテストは「TableModelなどのロジック部分だけ」を対象にしており、ダイアログの中に埋まったロジックはテストの射程外でした。

### 再発防止テスト+リファクタリング提案

「テストがない場所にバグは棲む」——であれば、**テストできる場所に切り出す**のが本質的な対策です。例えば入力解析を小さなクラスに移します。

```java
// ui/AmountParser.java(新規)
public class AmountParser {
    /** 金額欄の文字列をintに変換する。カンマ付き入力も受け付ける。 */
    public static int parse(String text) throws ValidationException {
        // (中身はparseAmountと同じ)
    }
}
```

ダイアログからは `AmountParser.parse(amountField.getText())` を呼ぶだけにすれば、次のようなテストが書けます。

```java
class AmountParserTest {
    @Test
    void カンマ付きの金額を解釈できる() throws Exception {
        assertEquals(1000, AmountParser.parse("1,000"));
    }

    @Test
    void 数字以外は入力エラーになる() {
        assertThrows(ValidationException.class, () -> AmountParser.parse("abc"));
    }
}
```

## この型のバグから得る教訓

- スタックトレースの**スレッド名・例外名・自分のコードの最上行**の3点で、実行時例外はほぼ即座に特定できる。
- ユーザー入力は「壊れているのが普通」。**受け入れて直せるものは直し、直せないものは分かりやすく断る**のが防御的な入力処理。
- privateメソッドに埋まったロジックはテストできない。「テストしにくいな」と感じたら、それは設計改善のサインである。
