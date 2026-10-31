package ru.practicum.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.ResultActions;
import ru.practicum.event.controller.category.AdminCategoryController;
import ru.practicum.event.dto.category.NewCategoryDto;
import ru.practicum.event.dto.category.UpdateCategoryDto;
import ru.practicum.event.exception.handler.ErrorHandler;
import ru.practicum.event.service.CategoryService;
import ru.practicum.event.test.ControllerTest;

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static ru.practicum.common.ControllerConstants.ACCESS_ADMIN;
import static ru.practicum.common.ControllerConstants.URL_CATEGORIES;

public class AdminCategoryControllerTest extends ControllerTest {
    private static final String URL_BASE = ACCESS_ADMIN + URL_CATEGORIES;

    @InjectMocks
    private AdminCategoryController adminCategoryController;
    @Mock
    private CategoryService categoryService;

    @BeforeEach
    public void setUp() {
        mockMvc = standaloneSetup(adminCategoryController)
                .setControllerAdvice(new ErrorHandler())
                .build();
    }

    @Test
    public void createCategory_StatusCreated() {
        // Arrange
        NewCategoryDto body = buildNewCategoryDto();

        // Act
        ResultActions result = performPost(URL_BASE, body);

        // Assert
        expectStatusCreated(result);
        assertMethodCall(categoryService, service ->
                service.createCategory(Mockito.any(NewCategoryDto.class)));
    }

    @Test
    public void deleteCategory_StatusNoContent() {
        // Arrange
        long catId = 1L;

        // Act
        ResultActions result = performDelete(createIdUrl(URL_BASE, catId));

        // Assert
        expectStatusNoContent(result);
        assertMethodCall(categoryService, service ->
                service.deleteCategory(Mockito.eq(catId)));
    }

    @Test
    public void updateCategory_StatusOk() {
        // Arrange
        long catId = 1L;
        NewCategoryDto body = buildNewCategoryDto();

        // Act
        ResultActions result = performPatch(createIdUrl(URL_BASE, catId), body);

        // Assert
        expectStatusOk(result);
        assertMethodCall(categoryService, service -> service.updateCategory(
                Mockito.eq(catId),
                Mockito.any(UpdateCategoryDto.class)
        ));
    }
}
