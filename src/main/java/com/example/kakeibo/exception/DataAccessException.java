package com.example.kakeibo.exception;

/**
 * データベースアクセス中の障害を表す例外。
 *
 * <p>JDBC の {@link java.sql.SQLException} をラップする非検査例外です。
 * DB障害はアプリのその場のコードでは回復できないため、検査例外にして
 * あらゆる呼び出し元に try-catch を書かせるのではなく、非検査例外として
 * UIの最上位でまとめて捕捉し、ユーザー向けメッセージに変換します
 * (使い分けの理由は {@link ValidationException} のコメントを参照)。</p>
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
