package ru.practicum.event.test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.event.dto.category.NewCategoryDto;
import ru.practicum.event.dto.category.UpdateCategoryDto;

import java.util.function.Consumer;

@ExtendWith(MockitoExtension.class)
public class TestBase {
    protected <M> void assertMethodCall(M mock, Consumer<M> methodCall) {
        methodCall.accept(Mockito.verify(
                mock,
                Mockito.times(1))
        );
    }

    protected <M> void assertMethodNotCall(M mock, Consumer<M> methodCall) {
        methodCall.accept(Mockito.verify(
                mock,
                Mockito.never())
        );
    }

    protected NewCategoryDto buildNewCategoryDto() {
        return NewCategoryDto.builder()
                .name("Category Name")
                .build();
    }

    protected UpdateCategoryDto buildUpdateCategoryDto() {
        return UpdateCategoryDto.builder()
                .name("Category Name Update")
                .build();
    }
}
