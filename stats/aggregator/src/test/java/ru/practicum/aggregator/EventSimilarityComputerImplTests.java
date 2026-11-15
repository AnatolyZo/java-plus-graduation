package ru.practicum.aggregator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.practicum.aggregator.service.EventSimilarityComputerImpl;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class EventSimilarityComputerImplTests {

    private EventSimilarityComputerImpl computer;

    @BeforeEach
    void setUp() {
        computer = new EventSimilarityComputerImpl();
    }

    private UserActionAvro action(long userId, long eventId, ActionTypeAvro type) {
        return UserActionAvro.newBuilder()
                .setUserId(userId)
                .setEventId(eventId)
                .setActionType(type)
                .setTimestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("null-тип действия — пустой список")
    void nullActionTypeReturnsEmpty() {
        UserActionAvro invalidAction = action(1, 1, ActionTypeAvro.VIEW);
        invalidAction.put(2, null);

        assertThat(computer.compute(invalidAction)).isEmpty();
    }

    @Test
    @DisplayName("первое событие пользователя — пустой список")
    void firstInteractionReturnsEmpty() {
        assertThat(computer.compute(action(1, 1, ActionTypeAvro.LIKE))).isEmpty();
    }

    @Test
    @DisplayName("повторное действие с тем же весом (delta = 0) — пустой список")
    void repeatedSameWeightActionReturnsEmpty() {
        computer.compute(action(1, 1, ActionTypeAvro.LIKE));
        assertThat(computer.compute(action(1, 1, ActionTypeAvro.LIKE))).isEmpty();
    }

    @Test
    @DisplayName("уменьшение веса (VIEW после REGISTER) — пустой список")
    void lowerWeightActionReturnsEmpty() {
        computer.compute(action(1, 1, ActionTypeAvro.REGISTER));
        assertThat(computer.compute(action(1, 1, ActionTypeAvro.VIEW))).isEmpty();
    }

    @Test
    @DisplayName("увеличение веса на одиночном событии не даёт сходства, хоть и меняет вес")
    void increasedWeightOnSingleEventStillEmpty() {
        computer.compute(action(1, 1, ActionTypeAvro.VIEW));
        assertThat(computer.compute(action(1, 1, ActionTypeAvro.REGISTER))).isEmpty();
    }

    @Test
    @DisplayName("два лайка одного пользователя — score = 1.0, пара нормализована (eventA < eventB)")
    void identicalWeightsGiveScoreOne() {
        computer.compute(action(1, 2, ActionTypeAvro.LIKE));

        List<EventSimilarityAvro> result = computer.compute(action(1, 1, ActionTypeAvro.LIKE));

        assertThat(result).hasSize(1);
        EventSimilarityAvro sim = result.get(0);
        assertThat(sim.getEventA()).isEqualTo(1L);
        assertThat(sim.getEventB()).isEqualTo(2L);
        assertThat(sim.getScore()).isEqualTo(1.0, offset(1e-9));
    }

    @Test
    @DisplayName("score считается через min и нормы: 0.4 / sqrt(0.4 * 1.0)")
    void scoreUsesMinAndNorms() {
        computer.compute(action(1, 1, ActionTypeAvro.LIKE));      // w(1,1) = 1.0

        List<EventSimilarityAvro> result = computer.compute(action(1, 2, ActionTypeAvro.VIEW)); // w(2,2) = 0.4

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScore())
                .isEqualTo(0.4 / Math.sqrt(0.4 * 1.0), offset(1e-9)); // ≈ 0.632456
    }

    @Test
    @DisplayName("вес события — максимум, а не сумма: VIEW затем REGISTER на одном событии")
    void weightIsMaxNotSum() {
        computer.compute(action(1, 1, ActionTypeAvro.VIEW));      // 0.4
        computer.compute(action(1, 1, ActionTypeAvro.REGISTER)); // 0.8, вывода нет

        List<EventSimilarityAvro> result = computer.compute(action(1, 2, ActionTypeAvro.REGISTER));

        // если бы вес суммировался (1.2), нормы были бы другими и score не равнялся 1.0
        assertThat(result.get(0).getScore()).isEqualTo(1.0, offset(1e-9));
    }

    @Test
    @DisplayName("уменьшение веса не перезаписывает больший: VIEW после REGISTER")
    void lowerActionDoesNotOverwriteHigherWeight() {
        computer.compute(action(1, 1, ActionTypeAvro.REGISTER)); // 0.8
        computer.compute(action(1, 1, ActionTypeAvro.VIEW));     // delta = 0, без вывода

        List<EventSimilarityAvro> result = computer.compute(action(1, 2, ActionTypeAvro.VIEW));

        // min(0.4, 0.8) = 0.4; нормы 0.8 и 0.4
        assertThat(result.get(0).getScore())
                .isEqualTo(0.4 / Math.sqrt(0.4 * 0.8), offset(1e-9)); // ≈ 0.707107
    }

    @Test
    @DisplayName("несколько пользователей: min-суммы и нормы накапливаются корректно")
    void multipleUsersAccumulate() {
        // пользователь 1: LIKE 1, LIKE 2 → пара (1,2), sum = 1.0
        computer.compute(action(1, 1, ActionTypeAvro.LIKE));
        computer.compute(action(1, 2, ActionTypeAvro.LIKE));

        // пользователь 2: VIEW 1, REGISTER 2 → min-сумма +0.4, нормы 1.4 и 1.8
        computer.compute(action(2, 1, ActionTypeAvro.VIEW));

        List<EventSimilarityAvro> result = computer.compute(action(2, 2, ActionTypeAvro.REGISTER));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScore())
                .isEqualTo(1.4 / Math.sqrt(1.4 * 1.8), offset(1e-9)); // ≈ 0.881917
    }

    @Test
    @DisplayName("после действия на событие возвращаются только пары этого события")
    void returnsOnlyPairsOfCurrentEvent() {
        computer.compute(action(1, 1, ActionTypeAvro.LIKE));
        computer.compute(action(2, 2, ActionTypeAvro.LIKE));
        computer.compute(action(1, 2, ActionTypeAvro.LIKE));

        List<EventSimilarityAvro> result = computer.compute(action(1, 3, ActionTypeAvro.REGISTER));

        assertThat(result)
                .isNotEmpty()
                .allSatisfy(sim -> {
                    boolean containsEvent3 = sim.getEventA() == 3L || sim.getEventB() == 3L;
                    assertThat(containsEvent3)
                            .as("пара должна содержать событие 3: eventA=%d, eventB=%d",
                                    sim.getEventA(), sim.getEventB())
                            .isTrue();
                });
    }

    @Test
    @DisplayName("поля avro заполнены, timestamp выставлен")
    void avroFieldsAreFilled() {
        computer.compute(action(1, 1, ActionTypeAvro.LIKE));

        List<EventSimilarityAvro> result = computer.compute(action(1, 2, ActionTypeAvro.LIKE));

        EventSimilarityAvro sim = result.get(0);
        assertThat(sim.getEventA()).isEqualTo(1L);
        assertThat(sim.getEventB()).isEqualTo(2L);
        assertThat(sim.getTimestamp()).isNotNull();
    }
}