package com.returnright.core.util;

import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Thin wrapper over the Boot-managed Jackson 3 mapper (jsonb columns are stored as JSON text). */
@Component
public class Json {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private final JsonMapper mapper;

    public Json(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public Map<String, Object> readMap(String json) {
        return json == null ? Map.of() : mapper.readValue(json, MAP);
    }
}
