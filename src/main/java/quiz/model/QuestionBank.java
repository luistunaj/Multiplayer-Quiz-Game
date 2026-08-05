package quiz.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The questions for one game, in the order they will be asked. */
public final class QuestionBank {

    private static final String BUNDLED = "/questions.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<Question> questions;

    public QuestionBank(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("A quiz needs at least one question");
        }
        this.questions = List.copyOf(questions);
    }

    /** Loads the question set bundled in the jar. */
    public static QuestionBank loadDefault() throws IOException {
        try (InputStream in = QuestionBank.class.getResourceAsStream(BUNDLED)) {
            if (in == null) {
                throw new IOException("Missing bundled resource " + BUNDLED);
            }
            return read(in, BUNDLED);
        }
    }

    /** Loads a question set from a file, so a host can supply their own. */
    public static QuestionBank load(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return read(in, path.toString());
        }
    }

    private static QuestionBank read(InputStream in, String source) throws IOException {
        try {
            return new QuestionBank(MAPPER.readValue(in, new TypeReference<List<Question>>() {
            }));
        } catch (JsonProcessingException e) {
            // Covers both malformed JSON and a question the record rejects:
            // Jackson wraps the constructor's IllegalArgumentException rather
            // than letting it through. Either way, name the file that caused it.
            throw new IOException("Invalid questions in " + source + ": " + e.getOriginalMessage(), e);
        } catch (IllegalArgumentException e) {
            // An empty file parses cleanly but is still not a usable quiz.
            throw new IOException("Invalid questions in " + source + ": " + e.getMessage(), e);
        }
    }

    public int size() {
        return questions.size();
    }

    public Question get(int index) {
        return questions.get(index);
    }

    public List<Question> questions() {
        return questions;
    }

    /** A copy in random order, so repeated games are not identical. */
    public QuestionBank shuffled(Random random) {
        List<Question> copy = new ArrayList<>(questions);
        Collections.shuffle(copy, random);
        return new QuestionBank(copy);
    }
}
