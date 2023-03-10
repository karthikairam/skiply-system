package com.skiply.system.common.api.jackson.converter.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.skiply.system.common.domain.model.valueobject.StudentId;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class StudentIdJsonConverter implements ValueObjectJsonConverter<StudentId> {

    @Override
    public JsonDeserializer<StudentId> getJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public StudentId deserialize(
                    JsonParser jsonParser,
                    DeserializationContext deserializationContext) throws IOException {

                final var value = jsonParser.getValueAsString();
                if (value == null) {
                    return null;
                }

                return new StudentId(value);
            }
        };
    }

    @Override
    public JsonSerializer<StudentId> getJsonSerializer() {
        return new JsonSerializer<>() {
            @Override
            public void serialize(
                    StudentId studentId,
                    JsonGenerator jsonGenerator,
                    SerializerProvider serializerProvider) throws IOException {
                if (studentId == null) {
                    jsonGenerator.writeNull();
                } else {
                    jsonGenerator.writeString(studentId.value());
                }
            }
        };
    }

    @Override
    public Class<StudentId> getType() {
        return StudentId.class;
    }
}
