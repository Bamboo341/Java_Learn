package com.example.kakeibo.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.h2.tools.RunScript;

import com.example.kakeibo.exception.DataAccessException;

/**
 * データベース接続の管理とスキーマ初期化を担当するクラス。
 *
 * <p>H2 Database をファイルモード(./data/kakeibo)で使用します。
 * 初回起動時には schema.sql と data.sql を自動実行して、
 * テーブルの作成と初期データの投入を行います。</p>
 */
public class DatabaseManager {

    private static final Logger logger = Logger.getLogger(DatabaseManager.class.getName());

    /**
     * デフォルトの接続先(ファイルモード。./data/kakeibo.mv.db が生成される)。
     * DB_CLOSE_DELAY=-1 は「全接続が閉じてもDBを開いたままにする」という設定で、
     * 接続のたびにDBファイルを開き直すコストを避けるために付けている。
     */
    private static final String DEFAULT_JDBC_URL = "jdbc:h2:./data/kakeibo;DB_CLOSE_DELAY=-1";

    /** 接続先のJDBC URL */
    private final String jdbcUrl;

    /** 本番用(ファイルモード)の接続設定で生成する。 */
    public DatabaseManager() {
        this(DEFAULT_JDBC_URL);
    }

    /**
     * 接続先URLを指定して生成する。
     * テストでは in-memory モードのURLを渡すことで、
     * 本体のDBファイルに影響を与えずに検証できる。
     *
     * @param jdbcUrl H2のJDBC URL
     */
    public DatabaseManager(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    /**
     * 新しいデータベース接続を取得する。
     * 呼び出し側は try-with-resources で確実にクローズすること。
     */
    public Connection getConnection() {
        try {
            return DriverManager.getConnection(jdbcUrl);
        } catch (SQLException e) {
            throw new DataAccessException("データベースへの接続に失敗しました", e);
        }
    }

    /**
     * テーブルが未作成であれば schema.sql と data.sql を実行して初期化する。
     * すでに初期化済みの場合は何もしないため、起動のたびに呼んでも安全(冪等)。
     */
    public void initialize() {
        try (Connection conn = getConnection()) {
            if (isInitialized(conn)) {
                logger.info("データベースは初期化済みのため、スキーマ初期化をスキップします");
                return;
            }
            logger.info("データベースを初期化します(テーブル作成+初期データ投入)");
            runScript(conn, "/schema.sql");
            runScript(conn, "/data.sql");
            logger.info("データベースの初期化が完了しました");
        } catch (SQLException e) {
            throw new DataAccessException("データベースの初期化に失敗しました", e);
        }
    }

    /** transactions テーブルの存在有無で、初期化済みかどうかを判定する。 */
    private boolean isInitialized(Connection conn) throws SQLException {
        // H2 は引用符なしの識別子(テーブル名など)を大文字で管理するため、大文字で問い合わせる
        try (ResultSet rs = conn.getMetaData()
                .getTables(null, null, "TRANSACTIONS", new String[] {"TABLE"})) {
            return rs.next();
        }
    }

    /** クラスパス上のSQLファイルを読み込んで実行する。 */
    private void runScript(Connection conn, String resourcePath) throws SQLException {
        InputStream in = DatabaseManager.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new DataAccessException("初期化用SQLファイルが見つかりません: " + resourcePath);
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            // H2付属のツールにスクリプト全体の実行を任せる(文の区切りの解釈などを自前で書かずに済む)
            RunScript.execute(conn, reader);
        } catch (IOException e) {
            throw new DataAccessException("初期化用SQLファイルの読み込みに失敗しました: " + resourcePath, e);
        }
    }
}
