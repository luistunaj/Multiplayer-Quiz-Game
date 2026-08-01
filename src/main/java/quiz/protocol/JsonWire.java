package quiz.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonWire {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonWire() {
    }

    /** Builds a frame line. The trailing newline is added by the socket writer. */
    public static String encode(Msg message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
          
            throw new IllegalStateException("Cannot encode " + message, e);
        }
    }

    /** Parses a frame line into a typed message. */
    public static Msg decode(String line) throws ProtocolException {
        try {
            return MAPPER.readValue(line, Msg.class);
        } catch (JsonProcessingException e) {
            throw new ProtocolException("Bad frame: " + e.getOriginalMessage());
        }
    }
}
