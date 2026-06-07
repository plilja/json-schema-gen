package se.plilja.jsonschemagen.internal.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import se.plilja.jsonschemagen.internal.model.Schema;

final class TestParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestParser() {
    }

    @SneakyThrows
    static <T extends Schema> T parse(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }
}
