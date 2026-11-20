package ru.practicum.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.event.entity.Category;
import ru.practicum.event.feign.RequestExistsClientAdapter;
import ru.practicum.event.repository.CategoryRepository;
import ru.practicum.feign.adapters.EventExistsClientAdapter;
import ru.practicum.feign.adapters.UserExistsClientAdapter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CategoryRepositoryTest {

    @MockitoBean
    private UserExistsClientAdapter userExistsClientAdapter;

    @MockitoBean
    private EventExistsClientAdapter eventExistsClientAdapter;

    @MockitoBean
    private RequestExistsClientAdapter requestExistsClientAdapter;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Category persistCategory(String name) {
        return entityManager.persist(Category.builder().name(name).build());
    }

    @Test
    @DisplayName("existsByName: true для существующего имени, false для отсутствующего")
    void existsByName_checksExistingAndMissing() {
        persistCategory("Concerts");
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.existsByName("Concerts")).isTrue();
        assertThat(categoryRepository.existsByName("Exhibitions")).isFalse();
    }

    @Test
    @DisplayName("findWithOffset: возвращает категории в порядке id с учётом OFFSET/FETCH")
    void findWithOffset_paginatesInIdOrder() {
        for (int i = 1; i <= 5; i++) {
            persistCategory("category-" + i);
        }
        entityManager.flush();
        entityManager.clear();

        List<Category> page1 = categoryRepository.findWithOffset(0, 2);
        List<Category> page2 = categoryRepository.findWithOffset(2, 2);
        List<Category> tail = categoryRepository.findWithOffset(4, 2);

        assertThat(page1).extracting(Category::getName).containsExactly("category-1", "category-2");
        assertThat(page2).extracting(Category::getName).containsExactly("category-3", "category-4");
        assertThat(tail).extracting(Category::getName).containsExactly("category-5");

        assertThat(page1).extracting(Category::getId)
                .isSorted()
                .doesNotContainAnyElementsOf(page2.stream().map(Category::getId).toList());
    }

    @Test
    @DisplayName("findWithOffset: offset за пределами выборки — пустой список")
    void findWithOffset_offsetBeyondData_returnsEmpty() {
        persistCategory("single");
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryRepository.findWithOffset(1, 10)).isEmpty();
    }

    @Test
    @DisplayName("findWithOffset: пустая таблица — пустой список")
    void findWithOffset_emptyTable_returnsEmpty() {
        assertThat(categoryRepository.findWithOffset(0, 10)).isEmpty();
    }
}