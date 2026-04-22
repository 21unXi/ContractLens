import { defineStore } from 'pinia';
import http, { setAuthToken, syncAuthToken } from '../api/http';

export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: syncAuthToken(),
    }),
    getters: {
        isAuthenticated: (state) => !!state.token,
    },
    actions: {
        async login(user) {
            const response = await http.post('/auth/login', user);
            this.token = response.data.token;
            localStorage.setItem('token', this.token);
            setAuthToken(this.token);
        },
        async register(user) {
            await http.post('/auth/register', user);
        },
        logout() {
            this.token = null;
            localStorage.removeItem('token');
            setAuthToken(null);
        },
    },
});
