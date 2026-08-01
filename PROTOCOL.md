# Wire Protocol

The client and server talk over TCP. Every message is a single JSON object on
one line, UTF-8 encoded, terminated by `\n`.

```
{"type":"JOIN","name":"alice"}
```

TCP is a byte stream and does not preserve message boundaries, so the newline is
what marks the end of a message. Anything before the first `\n` is one message,
however the bytes happened to arrive.

The `type` field selects the message. Its fields are defined by the matching
record in [`Msg.java`](src/main/java/quiz/protocol/Msg.java), which is the
authority on names and types; they are not repeated here.

## Messages

| Direction | Type | Purpose |
|-----------|------|---------|
| C→S | `JOIN` | Join the lobby under a name. |
| C→S | `START` | Start the game. Host only. |
| C→S | `ANSWER` | Answer the current question. |
| S→C | `WELCOME` | Join accepted. Carries the player id and whether they are host. |
| S→C | `LOBBY` | Current players, in join order. Sent whenever someone joins or leaves. |
| S→C | `QUESTION` | A new question, with its options and time limit. |
| S→C | `ANSWER_ACK` | The answer was accepted. |
| S→C | `REVEAL` | The question is over. Sent per player, since the points differ. |
| S→C | `SCORES` | Scoreboard, highest first. |
| S→C | `GAME_OVER` | No questions left. |
| S→C | `ERROR` | The last message was rejected. |

Indexes are zero-based. Times are in milliseconds.

## Example game

```
C→S   {"type":"JOIN","name":"alice"}
S→C   {"type":"WELCOME","playerId":"1","host":true}
S→C   {"type":"LOBBY","players":["alice"]}
S→C   {"type":"LOBBY","players":["alice","bob"]}
C→S   {"type":"START"}
S→C   {"type":"QUESTION","index":0,"total":2,"text":"What is 2+2?","options":["3","4","5","6"],"limitMs":20000}
C→S   {"type":"ANSWER","questionIndex":0,"optionIndex":1}
S→C   {"type":"ANSWER_ACK","questionIndex":0}
S→C   {"type":"REVEAL","questionIndex":0,"correctIndex":1,"points":950,"total":950}
S→C   {"type":"SCORES","rows":[{"rank":1,"name":"alice","score":950}]}
S→C   {"type":"GAME_OVER"}
```

## Errors

| Code | Meaning |
|------|---------|
| `BAD_FRAME` | The message could not be parsed. |
| `BAD_NAME` | The name was empty, too long, or already taken. |
| `NOT_HOST` | Only the host can start the game. |
| `WRONG_PHASE` | The message does not apply right now. |
| `ALREADY_ANSWERED` | Only the first answer to a question counts. |

A rejected message does not close the connection. The server replies with
`ERROR` and carries on reading.

## Timing

The server decides how long an answer took, using the moment the message was
read off the socket. Clients never send timestamps, since a client could report
whatever time suited it.
