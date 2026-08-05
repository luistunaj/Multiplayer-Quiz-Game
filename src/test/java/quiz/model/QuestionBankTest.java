package quiz.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestionBankTest {

    private static Question sample(String text) {
        return new Question(text, List.of("a", "b", "c", "d"), 1, 20000);
    }

    @Test
    void bundledQuestionsLoad() throws IOException {
        QuestionBank bank = QuestionBank.loadDefault();

        assertTrue(bank.size() > 0);
        assertNotNull(bank.get(0).text());
    }

    @Test
    void loadsFromAFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("questions.json");
        Files.writeString(file, """
                [
                  {
                    "text": "What is 2+2?",
                    "options": ["3", "4"],
                    "correctIndex": 1,
                    "timeLimitMs": 10000
                  }
                ]
                """);

        QuestionBank bank = QuestionBank.load(file);

        assertEquals(1, bank.size());
        assertEquals("What is 2+2?", bank.get(0).text());
        assertTrue(bank.get(0).isCorrect(1));
        assertFalse(bank.get(0).isCorrect(0));
    }

    @Test
    void rejectsACorrectIndexOutsideTheOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> new Question("Bad", List.of("a", "b"), 5, 10000));
    }

    @Test
    void rejectsAQuestionWithOneOption() {
        assertThrows(IllegalArgumentException.class,
                () -> new Question("Bad", List.of("only"), 0, 10000));
    }

    @Test
    void rejectsABlankQuestion() {
        assertThrows(IllegalArgumentException.class,
                () -> new Question("  ", List.of("a", "b"), 0, 10000));
    }

    @Test
    void rejectsANonPositiveTimeLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new Question("Bad", List.of("a", "b"), 0, 0));
    }

    @Test
    void rejectsAnEmptyBank() {
        assertThrows(IllegalArgumentException.class, () -> new QuestionBank(List.of()));
    }

    @Test
    void reportsWhichFileHeldTheBadQuestion(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, """
                [{"text": "Bad", "options": ["a", "b"], "correctIndex": 9, "timeLimitMs": 1000}]
                """);

        IOException e = assertThrows(IOException.class, () -> QuestionBank.load(file));

        assertTrue(e.getMessage().contains("broken.json"), e.getMessage());
    }

    @Test
    void shufflingKeepsEveryQuestion() {
        QuestionBank bank = new QuestionBank(List.of(sample("one"), sample("two"), sample("three")));

        QuestionBank shuffled = bank.shuffled(new Random(42));

        assertEquals(bank.size(), shuffled.size());
        assertTrue(shuffled.questions().containsAll(bank.questions()));
    }

    @Test
    void optionsCannotBeChangedAfterConstruction() {
        Question question = sample("one");

        assertThrows(UnsupportedOperationException.class, () -> question.options().set(0, "hacked"));
    }
}
