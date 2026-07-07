package com.example.kakeibo.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.kakeibo.exception.DataAccessException;
import com.example.kakeibo.infra.DatabaseManager;
import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.TransactionType;

/**
 * {@link CategoryRepository} のJDBC実装。
 *
 * <p>素のJDBC(PreparedStatement)でcategoriesテーブルを読み書きします。
 * SQLExceptionはすべて {@link DataAccessException} にラップして投げ直します。</p>
 */
public class JdbcCategoryRepository implements CategoryRepository {

    /** SELECTで取得する列の並び(mapRowと対応させる) */
    private static final String SELECT_COLUMNS =
            "SELECT id, name, type, display_order FROM categories";

    private final DatabaseManager databaseManager;

    public JdbcCategoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public List<Category> findAll() {
        String sql = SELECT_COLUMNS + " ORDER BY display_order, id";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Category> results = new ArrayList<>();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;
        } catch (SQLException e) {
            throw new DataAccessException("カテゴリ一覧の取得に失敗しました", e);
        }
    }

    @Override
    public List<Category> findByType(TransactionType type) {
        String sql = SELECT_COLUMNS + " WHERE type = ? ORDER BY display_order, id";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type.toDbValue());
            try (ResultSet rs = ps.executeQuery()) {
                List<Category> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new DataAccessException("カテゴリの検索に失敗しました", e);
        }
    }

    @Override
    public Optional<Category> findById(long id) {
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
            throw new DataAccessException("カテゴリの取得に失敗しました (id=" + id + ")", e);
        }
    }

    @Override
    public long insert(Category category) {
        String sql = "INSERT INTO categories (name, type, display_order) VALUES (?, ?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getType().toDbValue());
            ps.setInt(3, category.getDisplayOrder());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("カテゴリの登録に失敗しました", e);
        }
    }

    @Override
    public void update(Category category) {
        if (category.getId() == null) {
            throw new IllegalArgumentException("更新にはIDが必要です");
        }
        String sql = "UPDATE categories SET name = ?, type = ?, display_order = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getType().toDbValue());
            ps.setInt(3, category.getDisplayOrder());
            ps.setLong(4, category.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("カテゴリの更新に失敗しました (id=" + category.getId() + ")", e);
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM categories WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            // 取引から参照されているカテゴリを消そうとすると、外部キー制約違反でここに来る。
            // その状態を事前に防ぐのはServiceの責務(使用中チェック)。
            throw new DataAccessException("カテゴリの削除に失敗しました (id=" + id + ")", e);
        }
    }

    @Override
    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM categories WHERE name = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("カテゴリ名の重複チェックに失敗しました", e);
        }
    }

    /** ResultSetの現在行をCategoryに変換する。 */
    private Category mapRow(ResultSet rs) throws SQLException {
        return new Category(
                rs.getLong("id"),
                rs.getString("name"),
                TransactionType.fromDbValue(rs.getString("type")),
                rs.getInt("display_order"));
    }
}
