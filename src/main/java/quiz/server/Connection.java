package quiz.server;

import quiz.protocol.Msg;

/** What a game needs from a player's connection. Keeps GameSession off sockets. */
public interface Connection {

    void send(Msg msg);

    void close();

    String remote();
}
