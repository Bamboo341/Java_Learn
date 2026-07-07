package com.example.kakeibo.repository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        // 全取引を読み込み、Java側で対象月を集計する
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();
        List<Transaction> all = findByConditions(null, null, null);

        int incomeTotal = 0;
        int expenseTotal = 0;
        Map<String, Integer> expenseByCategory = new LinkedHashMap<>();
        for (Transaction transaction : all) {
            String categoryName = findCategoryName(transaction.getCategoryId());
            // 対象月(月初以上・月末未満)の取引だけを集計する
            if (transaction.getDate().isBefore(firstDay) || !transaction.getDate().isBefore(lastDay)) {
                continue;
            }
            if (transaction.getType() == TransactionType.INCOME) {
                incomeTotal += transaction.getAmount();
            } else {
                expenseTotal += transaction.getAmount();
                expenseByCategory.merge(categoryName, transaction.getAmount(), Integer::sum);
            }
        }

        List<CategoryExpense> categoryExpenses = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : expenseByCategory.entrySet()) {
            categoryExpenses.add(new CategoryExpense(entry.getKey(), entry.getValue()));
        }
        categoryExpenses.sort((a, b) -> b.getAmount() - a.getAmount());  // 支出の大きい順
        return new MonthlySummary(yearMonth, incomeTotal, expenseTotal, categoryExpenses);
    }

    /** カテゴリ名を1件取得する。 */
    private String findCategoryName(long categoryId) {
        String sql = "SELECT name FROM categories WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("name");
            }
        } catch (SQLException e) {
            throw new DataAccessException("カテゴリ名の取得に失敗しました", e);
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
