package ru.practicum.event.mapper;


import ru.practicum.event.dto.event.Location;
import ru.practicum.event.entity.LocationEmbeddable;

public class LocationMapper {
    public static Location toLocation(LocationEmbeddable locationEmbeddable) {
        return new Location(locationEmbeddable.getLat(), locationEmbeddable.getLon());
    }

    public static LocationEmbeddable toLocationEmbeddable(Location location) {
        return new LocationEmbeddable(location.lat(), location.lon());
    }
}
