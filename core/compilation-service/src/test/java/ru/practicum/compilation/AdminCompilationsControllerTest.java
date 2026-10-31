package ru.practicum.compilation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.ResultActions;
import ru.practicum.compilation.controller.AdminCompilationsController;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.dto.UpdateCompilationRequest;
import ru.practicum.compilation.exception.ErrorHandler;
import ru.practicum.compilation.service.CompilationsService;
import ru.practicum.compilation.test.ControllerTest;

import java.util.List;

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static ru.practicum.common.ControllerConstants.ACCESS_ADMIN;
import static ru.practicum.common.ControllerConstants.URL_COMPILATIONS;

public class AdminCompilationsControllerTest extends ControllerTest {
    private static final String URL_BASE = ACCESS_ADMIN + URL_COMPILATIONS;
    @InjectMocks
    private AdminCompilationsController adminCompilationsController;
    @Mock
    private CompilationsService compilationsService;

    @BeforeEach
    public void setUp() {
        mockMvc = standaloneSetup(adminCompilationsController)
                .setControllerAdvice(new ErrorHandler())
                .build();
    }

    @Test
    public void createCompilation_StatusCreated() {
        // Arrange
        NewCompilationDto body = buildNewCompilationDto(List.of());

        // Act
        ResultActions result = performPost(URL_BASE, body);

        // Assert
        expectStatusCreated(result);
        assertMethodCall(compilationsService, service ->
                service.createCompilation(Mockito.any(NewCompilationDto.class)));
    }

    @Test
    public void deleteCompilation_StatusNoContent() {
        // Arrange
        long compId = 1L;

        // Act
        ResultActions result = performDelete(createIdUrl(URL_BASE, compId));

        // Assert
        expectStatusNoContent(result);
        assertMethodCall(compilationsService, service ->
                service.deleteCompilation(Mockito.eq(compId)));
    }

    @Test
    public void updateCompilation_StatusOk() {
        // Arrange
        long compId = 1L;
        UpdateCompilationRequest body = buildUpdateCompilationRequest(List.of());

        // Act
        ResultActions result = performPatch(createIdUrl(URL_BASE, compId), body);

        // Assert
        expectStatusOk(result);
        assertMethodCall(compilationsService, service ->
                service.updateCompilation(Mockito.eq(compId), Mockito.any(UpdateCompilationRequest.class)));
    }
}
