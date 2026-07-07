package com.example.kakeibo.model;

/**
 * 収支の区分(収入 / 支出)を表す列挙型。
 */
// [設計意図] 収入・支出を金額の符号(プラス/マイナス)ではなく「区分」で表す。
// 符号方式は「-1円の収入」のような不正データを許してしまううえ、集計のたびに
// 絶対値への変換が必要になりミスの温床になる。区分を enum という「型」で分けておけば、
// 収入と支出の取り違えにコンパイル時や画面表示の段階で気付きやすくなる。
public enum TransactionType {

    /** 収入 */
    INCOME("収入"),

    /** 支出 */
    EXPENSE("支出");

    /** 画面表示用の日本語ラベル */
    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    /** 画面表示用の日本語ラベルを返す。 */
    public String getLabel() {
        return label;
    }

    /** データベースに保存する文字列("INCOME" / "EXPENSE")を返す。 */
    public String toDbValue() {
        return name();
    }

    /**
     * データベースに保存された文字列から列挙値に変換する。
     *
     * @param value "INCOME" または "EXPENSE"
     * @throws IllegalArgumentException 未知の値の場合(DBのデータ破損などプログラムの想定外)
     */
    public static TransactionType fromDbValue(String value) {
        for (TransactionType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不正な収支区分です: " + value);
    }
}
