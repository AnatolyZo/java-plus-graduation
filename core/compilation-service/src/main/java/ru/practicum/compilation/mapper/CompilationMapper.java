package ru.practicum.compilation.mapper;


import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.entity.Compilation;

public class CompilationMapper {
    public static Compilation toCompilation(NewCompilationDto body) {
        return Compilation.builder()
                .pinned(body.isPinned())
                .title(body.getTitle())
                .build();
    }

    public static CompilationDto toCompilationDto(Compilation compilation) {
        return CompilationDto.builder()
                .id(compilation.getId())
                .pinned(compilation.isPinned())
                .title(compilation.getTitle())
                .build();
    }
}
