<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

const email = ref("");
const password = ref("");
const loading = ref(false);
const error = ref<string | null>(null);

async function submit() {
  error.value = null;
  loading.value = true;
  try {
    await auth.login(email.value.trim(), password.value);
    const next = typeof route.query.next === "string" ? route.query.next : "/app";
    await router.push(next);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Login failed";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="card">
    <h1>Sign in</h1>
    <p class="sub">Use the same credentials as the Scala API.</p>
    <p v-if="error" class="err">{{ error }}</p>
    <form @submit.prevent="submit">
      <div class="field">
        <label for="email">Email</label>
        <input id="email" v-model="email" type="email" autocomplete="username" required />
      </div>
      <div class="field">
        <label for="password">Password</label>
        <input id="password" v-model="password" type="password" autocomplete="current-password" required />
      </div>
      <button class="btn" type="submit" :disabled="loading">{{ loading ? "…" : "Sign in" }}</button>
    </form>
    <p class="muted">No account? <RouterLink to="/register">Register</RouterLink></p>
  </div>
</template>
