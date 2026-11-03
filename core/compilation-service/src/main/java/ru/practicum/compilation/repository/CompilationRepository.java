package ru.practicum.compilation.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.compilation.entity.Compilation;

import java.util.List;
import java.util.Optional;

public interface CompilationRepository extends JpaRepository<Compilation, Long> {

    @Query(value = """
            SELECT *
            FROM compilations
            WHERE (:pinned IS NULL OR pinned = :pinned)
            ORDER BY id
            OFFSET :from
            ROWS FETCH NEXT :size ROWS ONLY
            """,
            nativeQuery = true)
    List<Compilation> findWithOffset(@Param("pinned") Boolean pinned, @Param("from") int from, @Param("size") int size);

    @EntityGraph(attributePaths = {"events"}, type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT c FROM Compilation c WHERE c.id = :id")
    Optional<Compilation> findWithEventsById(@Param("id") Long id);
}
