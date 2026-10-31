package ru.practicum.compilation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.dto.UpdateCompilationRequest;
import ru.practicum.compilation.service.CompilationsService;

import static ru.practicum.common.ControllerConstants.*;

@RestController
@RequestMapping(path = ACCESS_ADMIN + URL_COMPILATIONS)
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class AdminCompilationsController {
    private final CompilationsService compilationsService;

    @PostMapping
    public ResponseEntity<CompilationDto> createCompilation(@RequestBody @Valid NewCompilationDto body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(compilationsService.createCompilation(body));
    }

    @DeleteMapping("/{" + ID_COMPILATION + "}")
    public ResponseEntity<Void> deleteCompilation(@PathVariable(name = ID_COMPILATION) long compId) {
        compilationsService.deleteCompilation(compId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(null);
    }

    @PatchMapping("/{" + ID_COMPILATION + "}")
    public ResponseEntity<CompilationDto> updateCompilation(
            @PathVariable(name = ID_COMPILATION) long compId,
            @RequestBody @Valid UpdateCompilationRequest body
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(compilationsService.updateCompilation(compId, body));
    }
}
