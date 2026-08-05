package quiz.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import quiz.protocol.Msg;

/** Collects what the session sent, so game logic can be tested without sockets. */
final class FakeConnection implements Connection {

    private final String name;
    private final List<Msg> sent = new ArrayList<>();

    private boolean closed;

    FakeConnection(String name) {
        this.name = name;
    }

    @Override
    public void send(Msg msg) {
        sent.add(msg);
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public String remote() {
        return name;
    }

    boolean isClosed() {
        return closed;
    }

    List<Msg> sent() {
        return sent;
    }

    /** The most recent message of this type, if any. */
    <T extends Msg> Optional<T> last(Class<T> type) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (type.isInstance(sent.get(i))) {
                return Optional.of(type.cast(sent.get(i)));
            }
        }
        return Optional.empty();
    }

    <T extends Msg> T require(Class<T> type) {
        return last(type).orElseThrow(
                () -> new AssertionError(name + " never received a " + type.getSimpleName()
                        + ". Got: " + sent));
    }

    <T extends Msg> long count(Class<T> type) {
        return sent.stream().filter(type::isInstance).count();
    }

    void clear() {
        sent.clear();
    }
}
