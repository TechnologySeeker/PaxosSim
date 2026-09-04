# PaxosSim

## What I'm building

A small, deterministic simulation of the Paxos consensus algorithm driving a
replicated key-value state machine across a 3-node cluster (Node A, Node B,
Node C). Each node plays all three Paxos roles — proposer, acceptor, and
learner. Nodes talk over a simulated, in-process network rather than real
sockets, so tests can control exactly when (and whether) messages are
delivered.

Client commands (`SET x 10`, `DELETE x`, ...) are agreed on one replicated
log slot at a time via single-decree Paxos instances (the beginning of
Multi-Paxos). Once a slot is chosen, every node applies it to its local
key-value store in slot order, so all healthy nodes converge on the same
state.

## Why Paxos

Paxos is the reference algorithm for achieving consensus on a single value
among unreliable nodes that can drop messages or go offline, without
tolerating malicious (Byzantine) behavior. Building it from scratch —
ballots, quorums, the "adopt the highest previously-accepted value" rule —
is the clearest way to demonstrate and test the safety guarantees that
distributed systems built on consensus rely on.

## Scope

This is a **simulation**, not a production consensus service:

- No real networking — nodes exchange messages through an in-process
  `SimulatedNetwork` that can deliver, drop, delay, reorder, partition, and
  reconnect messages on command.
- No threads, sockets, or timers — message delivery is driven explicitly by
  tests (`network.deliverNext()`, `network.deliverAll()`, ...), so every
  scenario (including failures and race conditions) is fully reproducible.
- The replicated state machine is a minimal key-value store (`SET`,
  `DELETE`) — not a database.

## What I'm NOT building

This is an educational implementation intended to demonstrate consensus,
replicated logs, and failure handling. It deliberately does **not**
implement:

- durable disk persistence
- real networking (sockets, RPC, serialization)
- Byzantine fault tolerance
- production leader leases / stable leader election
- log compaction or snapshots
- dynamic cluster membership (reconfiguration)
- optimized Multi-Paxos leader reuse (every slot runs its own Phase 1/2)

These omissions are intentional scope cuts, not oversights, and are meant to
keep the project small enough to build and test thoroughly rather than
"complete" and untested.

## High-level architecture

```
                    Client
                       │
                       │ SET x=10
                       ▼
                 ┌───────────┐
                 │ Proposer A│
                 └─────┬─────┘
                       │
                  Prepare(1,A)
              ┌────────┼────────┐
              ▼        ▼        ▼
           Node A    Node B    Node C
          Acceptor  Acceptor  Acceptor
              │        │        │
              └────Promise──────┘
                       │
                       ▼
                 Proposer A
                       │
                Accept(x=10)
              ┌────────┼────────┐
              ▼        ▼        ▼
           Node A    Node B    Node C
              │        │
              └─ quorum┘
                   │
                   ▼
             VALUE CHOSEN
                   │
                   ▼
            replicated log
                   │
                   ▼
             state machine
```

Nodes A, B, and C each connect only through a `SimulatedNetwork`:

```
Node A ─┐
Node B ─┼── SimulatedNetwork
Node C ─┘
```

The network is the single seam controlling message delivery, which is what
makes failure injection (dropped messages, partitions, delayed/reordered
delivery, disconnect/reconnect) possible without real concurrency.

## Project structure

```
src/
├── main/java/paxossim/
│   ├── core/                  # ballots, commands, replicated log, state machine, acceptor state
│   │   ├── Ballot.java
│   │   ├── Command.java
│   │   ├── LogEntry.java
│   │   ├── Log.java
│   │   ├── StateMachine.java
│   │   └── AcceptorState.java
│   ├── message/                 # Paxos protocol message types
│   │   ├── Message.java
│   │   ├── Prepare.java
│   │   ├── Promise.java
│   │   ├── AcceptRequest.java
│   │   ├── Accepted.java
│   │   └── Nack.java
│   ├── network/                 # deterministic in-process message delivery
│   │   ├── Envelope.java
│   │   └── SimulatedNetwork.java
│   ├── node/                    # cluster member combining all Paxos roles
│   │   └── Node.java
│   └── role/                    # proposer/acceptor/learner roles
│       ├── Acceptor.java
│       ├── Proposer.java
│       └── Learner.java
└── test/java/paxossim/
    ├── RunAllTests.java
    ├── core/
    │   ├── BallotTest.java
    │   ├── LogTest.java
    │   └── StateMachineTest.java
    ├── integration/              # end-to-end, multi-role Paxos scenarios
    │   ├── SingleDecreePaxosTest.java
    │   ├── MultiSlotPaxosTest.java
    │   ├── ReplicationConvergenceTest.java
    │   ├── FailureScenarioTest.java
    │   └── SafetyInvariantsTest.java
    ├── network/
    │   └── SimulatedNetworkTest.java
    ├── node/
    │   └── NodeTest.java
    ├── role/
    │   ├── AcceptorTest.java
    │   ├── ProposerTest.java
    │   └── LearnerTest.java
    └── testing/                 # dependency-free assertion + test runner, safety invariant checks
        ├── Assertions.java
        ├── PaxosInvariants.java
        ├── PaxosInvariantsTest.java
        └── TestRunner.java
```

## Status

Scaffold only — modules are currently empty. See the build order and test
plan tracked alongside this project for the implementation sequence
(acceptor → simulated network → proposer phases → single-decree Paxos tests
→ replicated log → failure injection → integration tests).
