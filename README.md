# Multiplayer Quiz Game

A LAN multiplayer quiz over raw Java sockets, with no networking framework. One machine runs the server and prints its address; everyone else connects from another machine and answers multiple-choice questions against a clock. Answering
correctly and quickly scores more.

Two clients ship with it, a console one and a Swing one and both are built on the same networking class.

## Features

- Any number of players over a LAN, each on its own virtual thread
- Timed questions, scored on speed as well as correctness
- Live scoreboard, lobby, host controls, host handover if the host leaves
- JSON message protocol that any TCP client can speak
- Console and desktop clients sharing one networking layer

## Building

```bash
mvn package
```

This runs the tests and produces `target/multithread-quiz-0.1.0-SNAPSHOT.jar`,
which bundles its one dependency so nothing else has to be installed to run it.

## Running

Start the server on the host machine:

```bash
java -jar target/multithread-quiz-0.1.0-SNAPSHOT.jar
```

It prints the addresses players should connect to, so the host does not have to
look them up:

```
Quiz Server
Loaded 10 questions
Join with: <lan-address> port <port>
Listening on port: <port>
```

Each player then runs a client with that address. The console client:

```bash
java -cp target/multithread-quiz-0.1.0-SNAPSHOT.jar quiz.client.ConsoleClient <lan-address> <port>
```

```
join name         join the lobby
start             start the game (host only)
2                 answer with option 2
quit              leave
```

Or the desktop client, which asks for the address in its first screen:

```bash
java -cp target/multithread-quiz-0.1.0-SNAPSHOT.jar quiz.client.SwingClient
```

Messages are JSON, one object per line, so any TCP client works too:

```bash
nc <lan-address> <port>
{"type":"JOIN","name":"username"}
```

## Architecture

```
                   ┌─────────────────────────────────────┐
                   │             GameSession             │
                   │                                     │
                   │   one thread, no locks              │
                   │   owns players, scores, phase       │
                   └─────────────────────────────────────┘
                        ▲                        │
             GameEvent  │                        │  Msg
              (queue)   │                        ▼
             ┌──────────┴───────┐      ┌───────────────────┐
             │  reader threads  │      │  writer threads   │
             │  decode incoming │      │  bounded queue    │
             └──────────────────┘      └───────────────────┘
                        ▲                        │
                        │                        ▼
        ════════════════ player sockets ══════════════════

        ScheduledExecutorService ──── GameEvent ────▶ GameSession
        (question deadlines)
```

Every connection gets two threads. One reads lines off the socket and turns them
into events. One writes queued messages back out.

All the game state sits in `GameSession`, on a single thread. Nothing else
touches it. Connection threads and the question timer just put events on a queue,
and the session thread handles them one at a time in the order they arrived.

That means there are no locks in the game logic. No synchronized blocks, no lock
ordering to get right, nothing that can deadlock.

The writer thread exists so the game loop never waits on a socket. `send` drops a
line on a queue and returns immediately. If a client falls 256 messages behind it
gets dropped, since old quiz frames are useless anyway.

All of these are virtual threads, so one thread per connection stays cheap.

## Wire protocol

Newline-delimited JSON over TCP. Full list in [PROTOCOL.md](PROTOCOL.md).

```
C->S   {"type":"JOIN","name":"alice"}
S->C   {"type":"WELCOME","playerId":"1","host":true}
S->C   {"type":"QUESTION","index":0,"total":10,"text":"...","options":["a","b"],"limitMs":20000}
C->S   {"type":"ANSWER","questionIndex":0,"optionIndex":1}
S->C   {"type":"REVEAL","questionIndex":0,"correctIndex":1,"points":950,"total":950}
```

Decoding gives back a typed record, not an array of strings. A mistyped field is
a compile error instead of something the server trips over at runtime.

## Scoring

```
points = 1000 * (1 - 0.5 * elapsed / limit)     correct answers
points = 0                                      wrong or unanswered
```

Answer instantly and you get 1000. Answer right on the buzzer and you get 500. A
slow correct answer still beats a fast wrong one.

The server does the timing. It stamps the arrival time as the message comes off
the socket, so clients never get to report how fast they were. It also means a
player is not punished for their answer sitting in a queue behind everyone
else's.

## Testing

```bash
mvn test
```

58 tests. Most of them never touch the network.

- `JsonWireTest` — encoding, decoding, malformed input
- `ScoringTest` — the scoring curve at its edges
- `QuestionBankTest` — loading and validating questions
- `GameSessionTest` — the whole state machine against a fake connection. Double
  answers, stale question indexes, late timers, mid-question disconnects
- `IntegrationTest` — a real server on an OS-assigned port, three real clients,
  a full game

`GameSession` talks to a `Connection` interface instead of the socket class, so
the state machine tests run in milliseconds with a fake.

## Layout

```
quiz/protocol   messages and JSON encoding
quiz/model      questions
quiz/server     sockets, events, game state, scoring
quiz/client     shared networking core, console UI, desktop UI
```

`server` and `client` both use `protocol`. Neither knows about the other.
