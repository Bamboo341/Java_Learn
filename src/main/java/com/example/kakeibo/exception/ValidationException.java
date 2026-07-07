package com.example.kakeibo.exception;

/**
 * 入力値や業務ルールの違反を表す例外。
 *
 * <p>メッセージにはユーザーにそのまま表示できる日本語の説明を設定します。</p>
 */
// [設計意図] 入力ミスや業務ルール違反は「必ず起こり得る想定内の事態」なので、
// 検査例外(Exception のサブクラス)にして呼び出し側にエラー処理を強制する。
// 一方、DB障害のような「その場では回復できない異常」は非検査例外
// (DataAccessException)にして、UIの最上位でまとめて捕捉する。
public class ValidationException extends Exception {

    public ValidationException(String message) {
        super(message);
    }
}
