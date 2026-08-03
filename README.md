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
mvn package
```

This runs the tests and produces `target/multithread-quiz-0.1.0-SNAPSHOT.jar`, which bundles the one dependency so nothing else has to be installed to run it.

## Running

Start the server on the host machine:

```bash
java -jar target/multithread-quiz-0.1.0-SNAPSHOT.jar
```

On startup it prints the LAN addresses that players should connect to, so the
host does not have to look them up:

```
Quiz Server
Join with: <lan-address> port <port>
Listening on port: <port>
```

Each player then runs a client, passing the address the server printed:

```bash
java -cp target/multithread-quiz-0.1.0-SNAPSHOT.jar quiz.client.ConsoleClient <lan-address> <port>
```

With no arguments it connects to `localhost` on the default port. Anything
typed is sent to the server; lines coming back from the server are prefixed
with `<`. Type `quit` to disconnect.

Messages are JSON, one object per line, so any TCP client works too:

```bash
nc <lan-address> <port>
```

Several clients can be connected at the same time. Each one is handled on its
own virtual thread.

## Scoring

- Correct answers earn points.
- Faster responses receive more points.
- Incorrect or unanswered questions receive no points.
- The player with the highest total score wins.
