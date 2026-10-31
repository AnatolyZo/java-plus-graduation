package ru.practicum.compilation.test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.dto.UpdateCompilationRequest;

import java.util.List;
import java.util.function.Consumer;

@ExtendWith(MockitoExtension.class)
public class TestBase {
    protected <M> void assertMethodCall(M mock, Consumer<M> methodCall) {
        methodCall.accept(Mockito.verify(
                mock,
                Mockito.times(1))
        );
    }

    protected NewCompilationDto buildNewCompilationDto() {
        return NewCompilationDto.builder()
                .pinned(false)
                .title("Compilation Title")
                .build();
    }

    protected NewCompilationDto buildNewCompilationDto(List<Long> eventIds) {
        return NewCompilationDto.builder()
                .events(eventIds)
                .pinned(false)
                .title("Compilation Title")
                .build();
    }

    protected UpdateCompilationRequest buildUpdateCompilationRequest(List<Long> eventIds) {
        return UpdateCompilationRequest.builder()
                .events(eventIds)
                .pinned(true)
                .title("Compilation Title Update")
                .build();
    }
}
