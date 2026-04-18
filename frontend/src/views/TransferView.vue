<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useAuthStore } from "@/stores/auth";
import { postTransfer } from "@/api/client";

const auth = useAuthStore();

const fromAccount = ref("");
const toAccount = ref("");
const amount = ref<number | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
const success = ref<string | null>(null);

const accounts = computed(() => auth.accountNumbers);

watch(
  accounts,
  (list) => {
    if (list.length && !fromAccount.value) fromAccount.value = list[0] ?? "";
  },
  { immediate: true },
);

async function submit() {
  error.value = null;
  success.value = null;
  const uid = auth.userId;
  if (!uid || !auth.token) {
    error.value = "Not signed in";
    return;
  }
  if (!fromAccount.value || !toAccount.value.trim()) {
    error.value = "Pick source account and enter destination.";
    return;
  }
  const amt = amount.value;
  if (amt == null || amt <= 0 || Number.isNaN(amt)) {
    error.value = "Amount must be a positive number.";
    return;
  }
  loading.value = true;
  try {
    const msg = await postTransfer(auth.token, {
      userId: uid,
      fromAccount: fromAccount.value,
      toAccount: toAccount.value.trim(),
      amount: amt,
    });
    success.value = msg || "Transfer completed.";
    toAccount.value = "";
    amount.value = null;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Transfer failed";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="card">
    <h1>Transfer</h1>
    <p class="sub">
      Signed in as <code>{{ auth.userId }}</code>
    </p>
    <p v-if="!accounts.length" class="err">
      No accounts in session. Sign out and sign in again to load account numbers, or register a new user.
    </p>
    <p v-if="error" class="err">{{ error }}</p>
    <p v-if="success" class="ok">{{ success }}</p>
    <form @submit.prevent="submit">
      <div class="field">
        <label for="from">From account</label>
        <select id="from" v-model="fromAccount" :disabled="!accounts.length">
          <option v-for="a in accounts" :key="a" :value="a">{{ a }}</option>
        </select>
      </div>
      <div class="field">
        <label for="to">To account number</label>
        <input id="to" v-model="toAccount" type="text" autocomplete="off" placeholder="e.g. 8901201002" />
      </div>
      <div class="field">
        <label for="amt">Amount</label>
        <input id="amt" v-model.number="amount" type="number" step="0.01" min="0.01" placeholder="0.00" />
      </div>
      <button class="btn" type="submit" :disabled="loading || !accounts.length">{{ loading ? "…" : "Send" }}</button>
    </form>
  </div>
</template>

<style scoped>
code {
  font-size: 0.8rem;
  color: var(--muted, #94a3b8);
  word-break: break-all;
}
</style>
