package ru.practicum.user;

import org.junit.jupiter.api.Test;
import ru.practicum.user.dto.NewUserRequest;
import ru.practicum.user.dto.UserDto;
import ru.practicum.user.dto.UserShortDto;
import ru.practicum.user.entity.User;
import ru.practicum.user.mapper.UserMapper;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {
    @Test
    void toUser_shouldMapNewUserRequestToUser() {
        NewUserRequest request = NewUserRequest.builder()
                .name("Test User")
                .email("test-user@mail.com")
                .build();

        User result = UserMapper.toUser(request);

        assertAll(
                () -> assertNull(result.getId()),
                () -> assertEquals(request.getName(), result.getName()),
                () -> assertEquals(request.getEmail(), result.getEmail())
        );
    }

    @Test
    void toUserDto_shouldMapUserToUserDto() {
        User user = User.builder()
                .id(1L)
                .name("Test User")
                .email("test-user@mail.com")
                .build();

        UserDto result = UserMapper.toUserDto(user);

        assertAll(
                () -> assertEquals(user.getId(), result.getId()),
                () -> assertEquals(user.getName(), result.getName()),
                () -> assertEquals(user.getEmail(), result.getEmail())
        );
    }

    @Test
    void toUserShortDto_shouldMapUserToUserShortDto() {
        User user = User.builder()
                .id(1L)
                .name("Test User")
                .email("test-user@mail.com")
                .build();

        UserShortDto result = UserMapper.toUserShortDto(user);

        assertAll(
                () -> assertEquals(user.getId(), result.getId()),
                () -> assertEquals(user.getName(), result.getName())
        );
    }
}
