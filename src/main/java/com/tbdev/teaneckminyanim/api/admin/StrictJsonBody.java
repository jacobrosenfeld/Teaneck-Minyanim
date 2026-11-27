package com.tbdev.teaneckminyanim.api.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.tbdev.teaneckminyanim.api.ApiException;

import java.util.stream.Collectors;

/**
 * Reads a PATCH body strictly: an unknown field (for example a typo such as {@code "enable"}) or a value of the
 * wrong type is a 400 VALIDATION_ERROR instead of being ignored, so a client never believes a change was applied
 * when it was not.
 */
final class StrictJsonBody {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private StrictJsonBody() {
    }

    static <T> T read(JsonNode body, Class<T> type) {
        if (body == null || !body.isObject()) {
            throw ApiException.validation("The request body must be a JSON object.");
        }
        try {
            return MAPPER.treeToValue(body, type);
        } catch (UnrecognizedPropertyException e) {
            throw ApiException.validation("Unknown field '" + path(e) + "'.");
        } catch (MismatchedInputException e) {
            throw ApiException.validation("Invalid value for '" + path(e) + "'.");
        } catch (JsonProcessingException e) {
            throw ApiException.validation("The request body is not valid.");
        }
    }

    private static String path(com.fasterxml.jackson.databind.JsonMappingException e) {
        String path = e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : String.valueOf(ref.getIndex()))
                .collect(Collectors.joining("."));
        return path.isEmpty() ? "body" : path;
    }
}
