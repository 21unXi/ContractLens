import { createRouter, createWebHistory } from 'vue-router';
import Login from '../views/Login.vue';
import Register from '../views/Register.vue';
import Dashboard from '../views/Dashboard.vue';
import History from '../views/History.vue';
import Settings from '../views/Settings.vue';
import Knowledge from '../views/Knowledge.vue';
import { useAuthStore } from '../stores/auth';

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: Login,
    },
    {
        path: '/register',
        name: 'Register',
        component: Register,
    },
    {
        path: '/',
        name: 'Dashboard',
        component: Dashboard,
        meta: { requiresAuth: true },
    },
    {
        path: '/history',
        name: 'History',
        component: History,
        meta: { requiresAuth: true },
    },
    {
        path: '/knowledge',
        name: 'Knowledge',
        component: Knowledge,
        meta: { requiresAuth: true },
    },
    {
        path: '/settings',
        name: 'Settings',
        component: Settings,
        meta: { requiresAuth: true },
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

router.beforeEach((to, from, next) => {
    const authStore = useAuthStore();
    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
        next('/login');
    } else {
        next();
    }
});

export default router;
