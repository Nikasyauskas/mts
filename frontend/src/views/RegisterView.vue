<script setup lang="ts">
import { ref } from "vue";
import { postRegister } from "@/api/client";

const email = ref("");
const password = ref("");
const userName = ref("");
const phone = ref("");
const loading = ref(false);
const error = ref<string | null>(null);
const done = ref<string | null>(null);

async function submit() {
  error.value = null;
  done.value = null;
  loading.value = true;
  try {
    const res = await postRegister({
      email: email.value.trim(),
      password: password.value,
      userName: userName.value.trim(),
      phone: phone.value.trim() || undefined,
    });
    done.value = `User ${res.userId}, account ${res.accountNumber}.`;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "Registration failed";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="card">
    <h1>Register</h1>
    <p class="sub">Password must be at least 8 characters.</p>
    <p v-if="error" class="err">{{ error }}</p>
    <p v-if="done" class="ok">{{ done }} <RouterLink to="/login">Sign in</RouterLink>.</p>
    <form @submit.prevent="submit">
      <div class="field">
        <label for="userName">Display name</label>
        <input id="userName" v-model="userName" type="text" autocomplete="name" required />
      </div>
      <div class="field">
        <label for="email">Email</label>
        <input id="email" v-model="email" type="email" autocomplete="username" required />
      </div>
      <div class="field">
        <label for="password">Password</label>
        <input id="password" v-model="password" type="password" autocomplete="new-password" required minlength="8" />
      </div>
      <div class="field">
        <label for="phone">Phone (optional)</label>
        <input id="phone" v-model="phone" type="text" autocomplete="tel" />
      </div>
      <button class="btn" type="submit" :disabled="loading">{{ loading ? "…" : "Create account" }}</button>
    </form>
    <p class="muted"><RouterLink to="/login">Back to sign in</RouterLink></p>
  </div>
</template>
