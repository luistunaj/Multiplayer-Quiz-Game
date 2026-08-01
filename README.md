# Multiplayer Quiz Game

A LAN multiplayer quiz game built with **Java sockets**. One machine hosts the server, while multiple players connect over the local network to compete in a timed multiple-choice quiz. Players earn more points for answering correctly and quickly. Each client connection is handled using **Java Virtual Threads**.

## Features

- LAN multiplayer gameplay
- Java Socket-based client/server architecture
- Multiple players can join simultaneously
- Timed multiple-choice questions
- Faster correct answers earn higher scores
- Live scoreboard
- One virtual thread per client connection
- Simple console-based interface

## Building

```bash
./scripts/build.sh
```

This compiles every source file under `src/main/java` into `out/`. The script
resolves the repository root from its own location, so it can be run from any
directory.

Run the server:

```bash
java -cp out quiz.server.QuizServer
```

On startup the server prints the LAN addresses players should connect to.

## Running

Start the server on the host machine:

```bash
java -cp out quiz.server.QuizServer
```

On startup it prints the LAN addresses that players should connect to, so the
host does not have to look them up:

```
Quiz Server
Join with: <lan-address> port <port>
Listening on port: <port>
```

```bash
nc <lan-address> <port>
```

Lines sent by the client are logged by the server and echoed back. Nothing is
delivered until a newline is sent.

Several clients can be connected at the same time. Each one is handled on its
own virtual thread.

## Scoring

- Correct answers earn points.
- Faster responses receive more points.
- Incorrect or unanswered questions receive no points.
- The player with the highest total score wins.
