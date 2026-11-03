package ru.practicum.user.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.practicum.user.entity.User;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);

    List<User> findAllByIdIn(Collection<Long> ids, Pageable pageable);

    @Query(value = "SELECT u.id FROM User u WHERE u.id IN :ids")
    Set<Long> findUsersIds(List<Long> ids);
}
