import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { postToken } from "@/api/client";
import { parseJwtUserId } from "@/lib/jwt";

const STORAGE_KEY = "mts_token";
const ACCOUNTS_KEY = "mts_accounts";

function loadStoredAccounts(): string[] {
  try {
    const raw = localStorage.getItem(ACCOUNTS_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((x): x is string => typeof x === "string");
  } catch {
    return [];
  }
}

export const useAuthStore = defineStore("auth", () => {
  const token = ref<string | null>(localStorage.getItem(STORAGE_KEY));
  const accountNumbers = ref<string[]>(token.value ? loadStoredAccounts() : []);

  const userId = computed(() => (token.value ? parseJwtUserId(token.value) : null));
  const isAuthenticated = computed(() => Boolean(token.value && userId.value));

  function persistAccounts(accounts: string[]) {
    accountNumbers.value = accounts;
    if (accounts.length) localStorage.setItem(ACCOUNTS_KEY, JSON.stringify(accounts));
    else localStorage.removeItem(ACCOUNTS_KEY);
  }

  function persist(next: string | null) {
    token.value = next;
    if (next) localStorage.setItem(STORAGE_KEY, next);
    else {
      localStorage.removeItem(STORAGE_KEY);
      persistAccounts([]);
    }
  }

  async function login(email: string, password: string) {
    const auth = await postToken({ email, password });
    persist(auth.token);
    persistAccounts(auth.accountNumbers ?? []);
  }

  function logout() {
    persist(null);
  }

  return {
    token,
    accountNumbers,
    userId,
    isAuthenticated,
    login,
    logout,
  };
});
