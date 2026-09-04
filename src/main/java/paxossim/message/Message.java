package paxossim.message;

/** The Paxos protocol messages exchanged between proposers and acceptors. */
public sealed interface Message permits Prepare, Promise, AcceptRequest, Accepted, Nack {
}
