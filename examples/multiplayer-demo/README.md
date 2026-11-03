# Multiplayer Demo

Minimal shared-board example. Each authenticated player controls a marker.
The authoritative room state lives server-side. Moving a marker increments a
room version and broadcasts the new state to all connected clients.

This proves identity, matchmaking/room membership, realtime delivery,
server authority, persistence and reconnect synchronization without game-specific complexity.
