package quiz.protocol;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class JsonWireTest {

    @Test
    void roundTripsAQuestion() throws Exception {
        Msg.Question sent
                = new Msg.Question(0, 2, "What is 2+2?", List.of("3", "4", "5", "6"), 20000);

        assertEquals(sent, JsonWire.decode(JsonWire.encode(sent)));
    }

    @Test
    void decodesToTheRightRecordType() throws Exception {
        Msg decoded = JsonWire.decode(JsonWire.encode(new Msg.Join("alice")));

        assertInstanceOf(Msg.Join.class, decoded);
        assertEquals("alice", ((Msg.Join) decoded).name());
    }

    @Test
    void separatorInANameIsHarmless() throws Exception {
        // What would have been an injection attempt against a delimited format.
        Msg.Join sent = new Msg.Join("bob|9999");

        assertEquals(sent, JsonWire.decode(JsonWire.encode(sent)));
    }

    @Test
    void quotesAndBackslashesSurvive() throws Exception {
        Msg.Join sent = new Msg.Join("bob\"\\name");

        assertEquals(sent, JsonWire.decode(JsonWire.encode(sent)));
    }

    @Test
    void encodedFrameNeverContainsARawNewline() {
        String encoded = JsonWire.encode(new Msg.Join("bob\nCHEAT"));

        // This is what keeps one message on one line over the socket.
        assertEquals(-1, encoded.indexOf('\n'));
    }

    @Test
    void newlineInANameSurvives() throws Exception {
        Msg.Join sent = new Msg.Join("bob\nCHEAT");

        assertEquals(sent, JsonWire.decode(JsonWire.encode(sent)));
    }

    @Test
    void roundTripsAScoreboard() throws Exception {
        Msg.Scores sent = new Msg.Scores(
                List.of(new Msg.Scores.Row(1, "alice", 950), new Msg.Scores.Row(2, "bob", 0)));

        assertEquals(sent, JsonWire.decode(JsonWire.encode(sent)));
    }

    @Test
    void roundTripsAMessageWithNoFields() throws Exception {
        assertEquals(new Msg.GameOver(), JsonWire.decode(JsonWire.encode(new Msg.GameOver())));
    }

    @Test
    void roundTripsAnAnswer() throws Exception {
        Msg.Answer sent = new Msg.Answer(3, 2);

        assertEquals(sent, JsonWire.decode(JsonWire.encode(sent)));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(ProtocolException.class, () -> JsonWire.decode("{not json"));
    }

    @Test
    void rejectsAnUnknownMessageType() {
        ProtocolException e = assertThrows(ProtocolException.class,
                () -> JsonWire.decode("{\"type\":\"NOPE\"}"));

        // Pins down why it threw, rather than accepting any parse failure.
        assertTrue(e.getMessage().contains("NOPE"), e.getMessage());
    }

    @Test
    void rejectsAMessageWithNoType() {
        assertThrows(ProtocolException.class, () -> JsonWire.decode("{\"name\":\"alice\"}"));
    }

    @Test
    void rejectsAnEmptyLine() {
        assertThrows(ProtocolException.class, () -> JsonWire.decode(""));
    }
}
