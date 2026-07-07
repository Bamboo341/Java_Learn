package com.example.kakeibo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.TransactionType;
import com.example.kakeibo.repository.CategoryRepository;

/**
 * テスト専用のインメモリ実装(Mapに保存するだけの偽物Repository)。
 *
 * <p>【学習ポイント】Repositoryがinterfaceだからこそ、DBなしのこうした偽物に
 * 差し替えられる。Serviceのテストは「業務ルールが正しいか」だけを高速に検証でき、
 * DBの都合(接続・スキーマ・後始末)から切り離せる。</p>
 */
class InMemoryCategoryRepository implements CategoryRepository {

    /** IDをキーにした保存領域 */
    private final Map<Long, Category> storage = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public List<Category> findAll() {
        List<Category> all = new ArrayList<>(storage.values());
        all.sort(Comparator.comparingInt(Category::getDisplayOrder)
                .thenComparing(Category::getId));
        return all;
    }

    @Override
    public List<Category> findByType(TransactionType type) {
        List<Category> results = new ArrayList<>();
        for (Category category : findAll()) {
            if (category.getType() == type) {
                results.add(category);
            }
        }
        return results;
    }

    @Override
    public Optional<Category> findById(long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public long insert(Category category) {
        long id = nextId++;
        storage.put(id, new Category(id, category.getName(), category.getType(),
                category.getDisplayOrder()));
        return id;
    }

    @Override
    public void update(Category category) {
        storage.put(category.getId(), category);
    }

    @Override
    public void delete(long id) {
        storage.remove(id);
    }

    @Override
    public boolean existsByName(String name) {
        for (Category category : storage.values()) {
            if (category.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
