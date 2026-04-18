<script setup lang="ts">
import { RouterLink, RouterView } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const auth = useAuthStore();
</script>

<template>
  <div class="layout">
    <header class="header">
      <RouterLink to="/" class="brand">MTS</RouterLink>
      <nav class="nav">
        <template v-if="auth.isAuthenticated">
          <RouterLink to="/app">Transfer</RouterLink>
          <button type="button" class="linkish" @click="auth.logout()">Sign out</button>
        </template>
        <template v-else>
          <RouterLink to="/login">Sign in</RouterLink>
          <RouterLink to="/register">Register</RouterLink>
        </template>
      </nav>
    </header>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>

<style>
:root {
  --bg: #0f172a;
  --panel: #1e293b;
  --text: #f1f5f9;
  --muted: #94a3b8;
  --accent: #38bdf8;
  --accent-dim: #0ea5e9;
  --danger: #f87171;
  --radius: 10px;
  --font: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  min-height: 100vh;
  font-family: var(--font);
  background: radial-gradient(1200px 600px at 20% 0%, #1e3a5f 0%, var(--bg) 55%);
  color: var(--text);
}

a {
  color: var(--accent);
  text-decoration: none;
}
a:hover {
  text-decoration: underline;
}

.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #334155;
  background: rgba(15, 23, 42, 0.85);
  backdrop-filter: blur(8px);
}

.brand {
  font-weight: 700;
  font-size: 1.25rem;
  letter-spacing: 0.04em;
  color: var(--text);
}
.brand:hover {
  color: var(--accent);
  text-decoration: none;
}

.nav {
  display: flex;
  gap: 1.25rem;
  align-items: center;
}

.linkish {
  background: none;
  border: none;
  color: var(--accent);
  font: inherit;
  cursor: pointer;
  padding: 0;
}
.linkish:hover {
  text-decoration: underline;
}

.main {
  flex: 1;
  padding: 2rem 1.25rem 3rem;
  max-width: 520px;
  width: 100%;
  margin: 0 auto;
}

.card {
  background: var(--panel);
  border-radius: var(--radius);
  padding: 1.75rem;
  border: 1px solid #334155;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}

h1 {
  margin: 0 0 0.35rem;
  font-size: 1.35rem;
  font-weight: 600;
}

.sub {
  margin: 0 0 1.5rem;
  color: var(--muted);
  font-size: 0.9rem;
}

.field {
  margin-bottom: 1rem;
}
.field label {
  display: block;
  font-size: 0.8rem;
  color: var(--muted);
  margin-bottom: 0.35rem;
}
.field input,
.field select {
  width: 100%;
  padding: 0.55rem 0.65rem;
  border-radius: 6px;
  border: 1px solid #475569;
  background: #0f172a;
  color: var(--text);
  font: inherit;
}
.field input:focus,
.field select:focus {
  outline: 2px solid var(--accent-dim);
  border-color: transparent;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  margin-top: 0.5rem;
  padding: 0.65rem 1rem;
  border: none;
  border-radius: 6px;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  background: linear-gradient(160deg, var(--accent), var(--accent-dim));
  color: #0f172a;
}
.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.err {
  margin: 0 0 1rem;
  padding: 0.65rem 0.75rem;
  border-radius: 6px;
  background: rgba(248, 113, 113, 0.12);
  color: var(--danger);
  font-size: 0.875rem;
}

.ok {
  margin: 0 0 1rem;
  padding: 0.65rem 0.75rem;
  border-radius: 6px;
  background: rgba(52, 211, 153, 0.12);
  color: #6ee7b7;
  font-size: 0.875rem;
}

.muted {
  color: var(--muted);
  font-size: 0.875rem;
  margin-top: 1.25rem;
}
</style>
