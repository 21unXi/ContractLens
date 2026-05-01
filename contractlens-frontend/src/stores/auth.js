import { defineStore } from 'pinia';
import http, { isLikelyJwt, setAuthToken, syncAuthToken } from '../api/http';

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
            const token = response?.data?.token;
            if (!isLikelyJwt(token)) {
                this.token = null;
                localStorage.removeItem('token');
                setAuthToken(null);
                throw new Error('登录失败：服务端未返回有效 token');
            }
            this.token = token.trim();
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
