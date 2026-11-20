package until.the.eternity.das.hero;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Visibility changes require an actual JSON boolean, never a coerced string or number. */
public class HeroBooleanDeserializer extends JsonDeserializer<Boolean> {
    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_TRUE) && !parser.hasToken(JsonToken.VALUE_FALSE)) {
            return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
        }
        return parser.getBooleanValue();
    }
}
