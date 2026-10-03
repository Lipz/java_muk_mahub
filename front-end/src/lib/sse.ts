export type SseEvent = { event: string; data: string };

/**
 * Split a text/event-stream body into events. Comments (the hub's pings) are
 * not events, but any bytes at all count as `onActivity`.
 */
export async function* readSse(body: ReadableStream<Uint8Array>, onActivity: () => void): AsyncGenerator<SseEvent> {
  const reader = body.getReader();
  // stream: true keeps a multi-byte character split across chunks intact
  const decoder = new TextDecoder();
  let buf = "";
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) return;
      onActivity();
      buf += decoder.decode(value, { stream: true });
      // A chunk can end between the \r and \n of one line break: hold that \r back
      const heldCr = buf.endsWith("\r");
      buf = (heldCr ? buf.slice(0, -1) : buf).replace(/\r\n?/g, "\n");
      let cut: number;
      while ((cut = buf.indexOf("\n\n")) >= 0) {
        const block = buf.slice(0, cut);
        buf = buf.slice(cut + 2);
        let event = "message";
        const data: string[] = [];
        for (const line of block.split("\n")) {
          if (line.startsWith(":")) continue;
          const colon = line.indexOf(":");
          const field = colon < 0 ? line : line.slice(0, colon);
          const val = colon < 0 ? "" : line.slice(colon + 1).replace(/^ /, "");
          if (field === "event") event = val;
          else if (field === "data") data.push(val);
        }
        if (data.length) yield { event, data: data.join("\n") };
      }
      if (heldCr) buf += "\r";
    }
  } finally {
    reader.releaseLock();
  }
}
