export interface Env { ROOMS: DurableObjectNamespace }

export class RealtimeRoom {
  constructor(private state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket")
      return new Response("Expected WebSocket", { status: 426 })

    const pair = new WebSocketPair()
    const client = pair[0], server = pair[1]
    this.state.acceptWebSocket(server)
    server.send(JSON.stringify({ type:"sync", room:this.state.id.toString() }))
    return new Response(null, { status:101, webSocket:client })
  }

  webSocketMessage(ws: WebSocket, message: string | ArrayBuffer) {
    const text = typeof message === "string" ? message : new TextDecoder().decode(message)
    for (const peer of this.state.getWebSockets()) if (peer !== ws) peer.send(text)
  }

  webSocketClose(_ws: WebSocket) {}
  webSocketError(_ws: WebSocket) {}
}
