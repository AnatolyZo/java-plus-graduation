package ru.practicum.event.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.common.Entities;
import ru.practicum.event.exception.NotFoundException;

@Slf4j
public class ServiceBase {
    protected <E> E findEntityIn(JpaRepository<E, Long> repository, long id, Entities entity) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException(entity, id));
    }
}
