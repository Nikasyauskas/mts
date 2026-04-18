import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", redirect: "/app" },
    {
      path: "/login",
      component: () => import("@/views/LoginView.vue"),
      meta: { guest: true },
    },
    {
      path: "/register",
      component: () => import("@/views/RegisterView.vue"),
      meta: { guest: true },
    },
    {
      path: "/app",
      component: () => import("@/views/TransferView.vue"),
      meta: { requiresAuth: true },
    },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (auth.token && !auth.userId) auth.logout();
  if (to.meta.requiresAuth && !auth.isAuthenticated) return { path: "/login", query: { next: to.fullPath } };
  if (to.meta.guest && auth.isAuthenticated) return { path: "/app" };
  return true;
});

export default router;
