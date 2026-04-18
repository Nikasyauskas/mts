/** Decode `userId` from API JWT (matches server JwtClaims / jwt-scala layout). */
export function parseJwtUserId(token: string): string | null {
  try {
    const parts = token.split(".");
    if (parts.length < 2) return null;
    let payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const pad = "=".repeat((4 - (payload.length % 4)) % 4);
    payload += pad;
    const raw = JSON.parse(atob(payload)) as Record<string, unknown>;
    const top = raw.userId;
    if (typeof top === "string" && top.trim()) return top.trim();
    const content = raw.content;
    if (typeof content === "string") {
      const inner = JSON.parse(content) as Record<string, unknown>;
      const u = inner.userId;
      if (typeof u === "string" && u.trim()) return u.trim();
    }
    return null;
  } catch {
    return null;
  }
}
