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

### How the pieces fit together

- **`Message`** (`Prepare`, `Promise`, `AcceptRequest`, `Accepted`, `Nack`) —
  a sealed hierarchy of immutable records. Every one of them carries a
  `slot`, so the whole protocol is multi-decree from the message format up,
  even though early commits only ever drove one slot at a time.
- **`SimulatedNetwork`** — a FIFO `Envelope` queue plus a `nodeId ->
  MessageHandler` registry. `send` enqueues; `deliverNext`/`deliverAll`
  dispatch to whichever handler is registered for the recipient, or silently
  no-op if nobody is (a dead node, or a node that was never created). Loss,
  reordering, and partitions (`dropWhere`, `reorderPending`,
  `partition`/`healPartition`) all act on this same queue, so a test can
  compose several failure modes and still know exactly what will happen.
- **`Acceptor`** — one `AcceptorState` (promised/accepted ballot + value) per
  slot, holding the Phase 1/2 promise and accept rules. Framework-free: it
  never touches the network directly.
- **`Node`** — the network-facing wrapper around an `Acceptor`. It registers
  itself under an id, replies to whoever sent a message, and — the one
  broadcast in the system — also forwards every `Accepted` it produces to a
  list of learner ids, mirroring how real acceptors notify learners
  directly rather than only the proposer that asked.
- **`Proposer`** — its own standalone role (not bundled into `Node`, since
  a cluster member's proposer role is really "whoever is trying to get a
  value chosen right now," not a fixed per-node responsibility). Tracks
  Promise/Accepted replies against a majority of the full acceptor list —
  not just of whoever has replied — which is what lets it keep working with
  a minority down. `chooseValue` is where the one safety-critical rule
  lives: adopt the highest already-accepted value from the promises
  collected, never propose over it.
- **`Learner`** — a passive observer, never a sender. It buckets `Accepted`
  votes by `(slot, ballot)` — never by slot alone — so a stale ballot's lone
  vote can never combine with a newer ballot's votes to falsely reach
  quorum, then marks a slot chosen in a `Log` once a real majority agrees.
- **`Log`** — one `LogEntry` per slot. `recordChosen` is the one place the
  "only one value chosen per slot" invariant is enforced in production
  code: re-recording the *same* value is a no-op, recording a *different*
  one throws, because that would mean the protocol itself was broken.
- **`StateMachine`** — applies a log's chosen commands strictly in slot
  order, holding at the first gap and catching up once it's filled, so
  replaying never depends on the order slots happened to be learned in.

Nothing here calls into real threads: a "concurrent" scenario (competing
proposers, a crash mid-round) is built by controlling the exact order
`SimulatedNetwork` delivers envelopes in, which is what makes every race in
this project reproducible on demand instead of only occasionally
reproducible under load.

## Trade-offs

Every deliberate scope cut below was chosen to keep the system small enough
to reason about completely, in exchange for something a production system
would need:

- **Simulation vs. a real network.** Gained: every scenario — including
  message loss, reordering, and partitions — is deterministic and
  replayable; a "flaky" test here is always a real bug, never timing noise,
  and a whole failure scenario is a handful of synchronous method calls
  instead of a multi-process test harness with sleeps and retries. Given
  up: none of this exercises real I/O — serialization bugs, partial reads,
  connection resets, actual clock skew, and TCP-level reordering behavior
  are all outside what these tests can catch. Porting `SimulatedNetwork`'s
  callers onto real sockets/RPC would need a retry/timeout layer this
  project never had to build, since here "message never arrives" just
  means "nobody calls `deliverNext` for it."
- **No persistence.** Gained: every test starts from a clean, fully
  in-memory state, so there's no on-disk format to design, version, or
  clean up between runs, and a "crashed" node in a test is just an object
  we stop calling methods on. Given up: a real crash in this simulation is
  indistinguishable from "the process is fine but nobody's driving it" —
  there's no analogue of a node restarting and needing to recover
  `AcceptorState`/`Log` from disk. A production port would need every
  acceptor to fsync its promised/accepted ballot before replying (the one
  piece of state that must survive a restart for safety), and every log
  entry to be durable before it's applied to the state machine.
- **In-memory key-value store, not a database.** `SET`/`DELETE` on a
  `Map<String, String>` is enough to prove commands replicate and apply in
  order; it deliberately doesn't exercise anything a real storage engine
  would need (compaction, range queries, transactions).

## What you would do next

Roughly in the order it would matter for turning this from a teaching
simulation into something closer to a real system:

1. **Real networking.** Replace `SimulatedNetwork.send`/`deliverNext` with
   an actual transport (even loopback TCP or gRPC), adding the
   timeout/retry logic that `SimulatedNetwork` never needed because tests
   deliver messages explicitly.
2. **Durability.** Persist `AcceptorState` (promised/accepted ballot and
   value) before every reply, and the `Log` before applying to the
   `StateMachine`, so a real restart can recover instead of just
   forgetting everything, per the no-persistence trade-off above.
3. **Log compaction / snapshots.** The `Log` grows one entry per slot
   forever; a long-running cluster needs periodic snapshots of the
   `StateMachine` plus truncation of the entries behind them.
4. **Stable leader / leases.** Every slot currently pays for its own
   Phase 1 (explicitly out of scope, see "What I'm NOT building" above); a real Multi-Paxos system
   elects a stable leader that skips Phase 1 for every slot after the
   first, which is most of the throughput win over plain Paxos.
5. **Dynamic membership.** The acceptor list is fixed at construction
   (`List<String> acceptorIds`); reconfiguring a live cluster needs its own
   Paxos-agreed slot type carrying membership changes.
6. **Richer failure injection.** `SimulatedNetwork` covers loss, reorder,
   and partition; it doesn't model variable delay distributions, message
   corruption, or (deliberately, per Scope) Byzantine behavior.
7. **A client-facing API.** Right now a "client" is just test code calling
   `Proposer` methods directly; a real system needs a request/response
   surface in front of it, including how a client finds the current
   leader and retries against a new one after a failed round.

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

Feature-complete for the scope above: single- and multi-decree Paxos
(Phase 1/2, the highest-accepted-value safety rule, quorum tracking),
learners and a replicated log with a per-slot single-chosen-value
invariant, a state machine that applies in strict slot order, and
network failure injection (drop, reorder, partition) exercised end to
end by dedicated failure-scenario and cross-node safety-invariant tests.
89 tests pass via `RunAllTests`. See "What you would do next" above for
what's deliberately left for a production port.
