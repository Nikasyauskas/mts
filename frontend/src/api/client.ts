import type { AuthResponse, LoginRequest, RegisterRequest, RegisterResponse, TransferRequest } from "./types";

const base = () => import.meta.env.VITE_API_BASE ?? "http://localhost:8080";

async function readError(res: Response): Promise<string> {
  const t = await res.text();
  return t.trim() || res.statusText;
}

export async function postRegister(body: RegisterRequest): Promise<RegisterResponse> {
  const res = await fetch(`${base()}/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readError(res));
  return (await res.json()) as RegisterResponse;
}

export async function postToken(body: LoginRequest): Promise<AuthResponse> {
  const res = await fetch(`${base()}/auth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await readError(res));
  return (await res.json()) as AuthResponse;
}

export async function postTransfer(token: string, body: TransferRequest): Promise<string> {
  const res = await fetch(`${base()}/transfer/account-number`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(text.trim() || res.statusText);
  try {
    return JSON.parse(text) as string;
  } catch {
    return text;
  }
}
