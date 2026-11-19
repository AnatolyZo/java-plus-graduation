package ru.practicum.compilation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import ru.practicum.compilation.entity.Compilation;
import ru.practicum.compilation.entity.CompilationEvent;
import ru.practicum.compilation.repository.CompilationRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CompilationRepositoryTest {

    @Autowired
    private CompilationRepository compilationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Compilation persistCompilation(boolean pinned, String title) {
        return entityManager.persist(Compilation.builder()
                .pinned(pinned)
                .title(title)
                .build());
    }

    private void attachEvent(Compilation compilation, long eventId) {
        entityManager.persist(CompilationEvent.builder()
                .compilation(compilation)
                .eventId(eventId)
                .build());
    }

    @Test
    @DisplayName("findWithOffset(pinned=true): только закреплённые подборки")
    void findWithOffset_returnsOnlyPinned() {
        persistCompilation(true, "pinned-1");
        persistCompilation(true, "pinned-2");
        persistCompilation(false, "unpinned");
        entityManager.flush();
        entityManager.clear();

        List<Compilation> result = compilationRepository.findWithOffset(true, 0, 10);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(Compilation::isPinned);
    }

    @Test
    @DisplayName("findWithOffset(pinned=false): только незакреплённые")
    void findWithOffset_returnsOnlyUnpinned() {
        persistCompilation(true, "pinned");
        persistCompilation(false, "unpinned-1");
        persistCompilation(false, "unpinned-2");
        entityManager.flush();
        entityManager.clear();

        List<Compilation> result = compilationRepository.findWithOffset(false, 0, 10);

        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(Compilation::isPinned);
    }

    @Test
    @DisplayName("findWithOffset(pinned=null): все подборки, порядок по id")
    void findWithOffset_nullPinned_returnsAllInIdOrder() {
        Compilation first = persistCompilation(true, "first");
        persistCompilation(false, "second");
        persistCompilation(true, "third");
        entityManager.flush();
        entityManager.clear();

        List<Compilation> result = compilationRepository.findWithOffset(null, 0, 10);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Compilation::getId)
                .containsExactly(first.getId(), first.getId() + 1, first.getId() + 2);
    }

    @Test
    @DisplayName("findWithOffset: нарезка страниц без пересечений, хвост короче size")
    void findWithOffset_paginates() {
        for (int i = 1; i <= 5; i++) {
            persistCompilation(false, "c" + i);
        }
        entityManager.flush();
        entityManager.clear();

        List<Compilation> page1 = compilationRepository.findWithOffset(null, 0, 2);
        List<Compilation> page2 = compilationRepository.findWithOffset(null, 2, 2);
        List<Compilation> tail = compilationRepository.findWithOffset(null, 4, 2);

        assertThat(page1).extracting(Compilation::getTitle).containsExactly("c1", "c2");
        assertThat(page2).extracting(Compilation::getTitle).containsExactly("c3", "c4");
        assertThat(tail).extracting(Compilation::getTitle).containsExactly("c5");

        assertThat(page1).extracting(Compilation::getId)
                .isSorted()
                .doesNotContainAnyElementsOf(page2.stream().map(Compilation::getId).toList());
    }

    @Test
    @DisplayName("findWithEventsById: events подгружены запросом — доступны после detach")
    void findWithEventsById_fetchedWithoutLazyLoading() {
        Compilation compilation = persistCompilation(false, "with events");
        attachEvent(compilation, 100L);
        attachEvent(compilation, 200L);
        entityManager.flush();
        entityManager.clear();

        Compilation loaded = compilationRepository.findWithEventsById(compilation.getId()).orElseThrow();

        entityManager.detach(loaded);
        assertThat(loaded.getEvents()).hasSize(2);
        assertThat(loaded.getEvents()).extracting(CompilationEvent::getEventId)
                .containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    @DisplayName("findWithEventsById: несуществующий id — пустой Optional")
    void findWithEventsById_unknownId_returnsEmpty() {
        assertThat(compilationRepository.findWithEventsById(999L)).isEmpty();
    }
}