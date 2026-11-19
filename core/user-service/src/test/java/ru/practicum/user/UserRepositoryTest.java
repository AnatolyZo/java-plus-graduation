package ru.practicum.user;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.practicum.user.entity.User;
import ru.practicum.user.repository.UserRepository;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String name, String email) {
        return entityManager.persist(User.builder()
                .name(name)
                .email(email)
                .build());
    }

    @Test
    @DisplayName("existsByEmail: true для существующего email, false для отсутствующего")
    void existsByEmail_checksExistingAndMissing() {
        persistUser("Alice", "alice@example.com");
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("bob@example.com")).isFalse();
    }

    @Test
    @DisplayName("уникальность email: повторная вставка того же email запрещена")
    void duplicateEmail_isRejected() {
        persistUser("Alice", "alice@example.com");
        entityManager.flush();

        User duplicate = User.builder()
                .name("Another Alice")
                .email("alice@example.com")
                .build();

        assertThatThrownBy(() -> entityManager.persist(duplicate))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("findAllByIdIn: только запрошенные пользователи, с сортировкой")
    void findAllByIdIn_returnsRequestedUsersWithSort() {
        User u1 = persistUser("first", "first@example.com");
        User u2 = persistUser("second", "second@example.com");
        persistUser("third", "third@example.com");
        entityManager.flush();
        entityManager.clear();

        List<User> result = userRepository.findAllByIdIn(
                List.of(u2.getId(), u1.getId(), 999L),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id")));

        assertThat(result).extracting(User::getId)
                .containsExactly(u1.getId(), u2.getId());
        assertThat(result).extracting(User::getEmail)
                .doesNotContain("third@example.com");
    }

    @Test
    @DisplayName("findAllByIdIn: пагинация режет выборку по сортировке")
    void findAllByIdIn_appliesPageable() {
        User u1 = persistUser("first", "first@example.com");
        User u2 = persistUser("second", "second@example.com");
        User u3 = persistUser("third", "third@example.com");
        entityManager.flush();
        entityManager.clear();

        List<User> page = userRepository.findAllByIdIn(
                List.of(u1.getId(), u2.getId(), u3.getId()),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "id")));

        assertThat(page).extracting(User::getId)
                .containsExactly(u3.getId());
    }

    @Test
    @DisplayName("findAllByIdIn: ни одного существующего id — пустой список")
    void findAllByIdIn_unknownIds_returnsEmpty() {
        assertThat(userRepository.findAllByIdIn(
                List.of(999L, 888L), PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("findUsersIds: возвращает id только существующих пользователей")
    void findUsersIds_returnsIdsOfExistingUsers() {
        User u1 = persistUser("first", "first@example.com");
        User u2 = persistUser("second", "second@example.com");
        entityManager.flush();
        entityManager.clear();

        Set<Long> result = userRepository.findUsersIds(
                List.of(u1.getId(), u2.getId(), 999L));

        assertThat(result).containsExactlyInAnyOrder(u1.getId(), u2.getId());
        assertThat(result).doesNotContain(999L);
    }

    @Test
    @DisplayName("findUsersIds: пустой список — пустое множество")
    void findUsersIds_emptyInput_returnsEmptySet() {
        assertThat(userRepository.findUsersIds(List.of())).isEmpty();
    }
}