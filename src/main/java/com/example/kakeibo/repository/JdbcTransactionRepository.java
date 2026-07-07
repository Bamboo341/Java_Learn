package com.example.kakeibo.repository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.kakeibo.exception.DataAccessException;
import com.example.kakeibo.infra.DatabaseManager;
import com.example.kakeibo.model.CategoryExpense;
import com.example.kakeibo.model.MonthlySummary;
import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;

/**
 * {@link TransactionRepository} のJDBC実装。
 *
 * <p>素のJDBC(PreparedStatement)でtransactionsテーブルを読み書きします。
 * SQLExceptionはすべて {@link DataAccessException} にラップして投げ直します。</p>
 */
public class JdbcTransactionRepository implements TransactionRepository {

    /** SELECTで取得する列の並び(mapRowと対応させる) */
    private static final String SELECT_COLUMNS =
            "SELECT id, transaction_date, amount, type, category_id, memo, created_at FROM transactions";

    private final DatabaseManager databaseManager;

    public JdbcTransactionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public List<Transaction> findByConditions(YearMonth yearMonth, Long categoryId, TransactionType type) {
        // 指定された条件だけをWHERE句に加えていく(nullの条件は絞り込みなし)
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (yearMonth != null) {
            // 月初日〜月末日の範囲で絞り込む
            conditions.add("transaction_date >= ? AND transaction_date <= ?");
            params.add(Date.valueOf(yearMonth.atDay(1)));
            params.add(Date.valueOf(yearMonth.atEndOfMonth()));
        }
        if (categoryId != null) {
            conditions.add("category_id = ?");
            params.add(categoryId);
        }
        if (type != null) {
            conditions.add("type = ?");
            params.add(type.toDbValue());
        }

        // [設計意図] 条件値は文字列連結ではなくPreparedStatementのプレースホルダ(?)で渡す。
        // 文字列連結だと、入力値がSQL文の一部として解釈される「SQLインジェクション」攻撃を
        // 許してしまう。プレースホルダなら値はあくまで値として扱われ、日付や文字列の
        // 変換・エスケープもJDBCドライバに任せられる。
        String sql = SELECT_COLUMNS
                + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions))
                + " ORDER BY transaction_date DESC, id DESC";  // 新しい取引が上に来るように

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Transaction> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new DataAccessException("取引の検索に失敗しました", e);
        }
    }

    @Override
    public Optional<Transaction> findById(long id) {
        String sql = SELECT_COLUMNS + " WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("取引の取得に失敗しました (id=" + id + ")", e);
        }
    }

    @Override
    public long insert(Transaction transaction) {
        String sql = "INSERT INTO transactions (transaction_date, amount, type, category_id, memo)"
                + " VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             // RETURN_GENERATED_KEYS: データベースが採番したIDを後で取得できるようにする
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setDate(1, Date.valueOf(transaction.getDate()));
            ps.setInt(2, transaction.getAmount());
            ps.setString(3, transaction.getType().toDbValue());
            ps.setLong(4, transaction.getCategoryId());
            ps.setString(5, transaction.getMemo());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("取引の登録に失敗しました", e);
        }
    }

    @Override
    public void update(Transaction transaction) {
        if (transaction.getId() == null) {
            throw new IllegalArgumentException("更新にはIDが必要です");
        }
        String sql = "UPDATE transactions SET transaction_date = ?, amount = ?, type = ?,"
                + " category_id = ?, memo = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(transaction.getDate()));
            ps.setInt(2, transaction.getAmount());
            ps.setString(3, transaction.getType().toDbValue());
            ps.setLong(4, transaction.getCategoryId());
            ps.setString(5, transaction.getMemo());
            ps.setLong(6, transaction.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("取引の更新に失敗しました (id=" + transaction.getId() + ")", e);
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM transactions WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("取引の削除に失敗しました (id=" + id + ")", e);
        }
    }

    @Override
    public int countByCategoryId(long categoryId) {
        String sql = "SELECT COUNT(*) FROM transactions WHERE category_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("カテゴリ使用件数の取得に失敗しました", e);
        }
    }

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

    @Override
    public List<YearMonth> findDistinctMonths() {
        String sql = "SELECT DISTINCT YEAR(transaction_date) AS y, MONTH(transaction_date) AS m"
                + " FROM transactions ORDER BY y DESC, m DESC";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<YearMonth> months = new ArrayList<>();
            while (rs.next()) {
                months.add(YearMonth.of(rs.getInt("y"), rs.getInt("m")));
            }
            return months;
        } catch (SQLException e) {
            throw new DataAccessException("年月一覧の取得に失敗しました", e);
        }
    }

    /** ResultSetの現在行をTransactionに変換する。 */
    private Transaction mapRow(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getLong("id"),
                rs.getDate("transaction_date").toLocalDate(),
                rs.getInt("amount"),
                TransactionType.fromDbValue(rs.getString("type")),
                rs.getLong("category_id"),
                rs.getString("memo"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
