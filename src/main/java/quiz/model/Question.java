package quiz.model;

import java.util.List;

public record Question(String text, List<String> options, int correctIndex, int timeLimitMs) {

    public Question {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Question text is required");
        }
        if (options == null || options.size() < 2) {
            throw new IllegalArgumentException("A question needs at least two options: " + text);
        }
        // Defensive copy, so a caller cannot change the options after the fact.
        options = List.copyOf(options);
        if (correctIndex < 0 || correctIndex >= options.size()) {
            throw new IllegalArgumentException(
                    "correctIndex " + correctIndex + " is outside the options of: " + text);
        }
        if (timeLimitMs <= 0) {
            throw new IllegalArgumentException("timeLimitMs must be positive: " + text);
        }
    }

    public boolean isCorrect(int optionIndex) {
        return optionIndex == correctIndex;
    }
}
