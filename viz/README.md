# PaxosSim Visualizer

A minimal, static, local UI for stepping through a Paxos run — SEND/RECEIVE/
DROPPED messages, PROMISE/ACCEPTED/NACK replies, CHOSEN slots, and
STATE_CHANGE applications — one event at a time. No backend, no build step:
open `index.html` in a browser.

## Quick start (zero setup)

Open `index.html` directly (double-click it, or `file://.../viz/index.html`),
pick one of the four bundled samples from the dropdown, and press **Step**
or **Play**. The samples are embedded directly in the page, so this works
completely offline — no server, no `fetch()`.

## Generating your own run

`ScenarioRunner` drives the real Paxos classes (`Proposer`, `Node`,
`Learner`, `Log`, `StateMachine`) through a named scenario and writes an
events JSON file:

```
javac -d out $(find src/main/java -name '*.java')
java -cp out paxossim.viz.ScenarioRunner <scenario> <output-file>
```

Scenarios: `normal`, `competing_proposers`, `node_failure`, `partition`.

Then load the file you generated via the **file picker** on the page (not
the dropdown, which only knows about the bundled samples) — this reads the
file directly in the browser via `FileReader`, so it also needs no server.

The four `events-*.json` files already in this directory are exactly what
produced the bundled samples; regenerate them any time the scenarios change
by re-running the command above for each of the four scenario names.

## What the UI shows

- **Nodes** — one circle per acceptor, with its current promised ballot,
  accepted value, and applied key-value state; a colored ring flashes for
  whichever kind of event just happened (promise / accept / nack / state
  change). Proposer ids discovered in the event stream are shown above as
  small tags.
- **Message arrows** — for the current SEND/RECEIVE/DROPPED event, a line is
  drawn between the two nodes involved: gray dashed while in flight, green
  once received, red with a "✕" if it was dropped or blocked by a partition.
- **Replicated log** and **state machines** — update live as CHOSEN and
  STATE_CHANGE events are replayed.
- **Event timeline** — every event in the run, with the current one
  highlighted; click Step/Play/Reset or drag the scrubber to move through
  it.

## Notes

- Replaying is a full re-apply from event 0 up to the current position every
  time you step, play, or scrub — simple and always correct, at the cost of
  redoing a little work on every step. Fine at this project's scale (tens to
  low hundreds of events).
- This page has no automated test coverage of its own (there's no JS runtime
  in this project's toolchain); `ScenarioRunner`'s output is what's tested —
  see `ScenarioRunnerTest` and the `EventLog`/`Node`/`Learner`/`StateMachine`
  event-emission tests under `src/test/java`.
