import axios from 'axios';

const API_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
const AUTH_HEADER = 'Authorization';

export function isLikelyJwt(token) {
    if (typeof token !== 'string') return false;
    const trimmed = token.trim();
    if (!trimmed) return false;
    if (trimmed === 'null' || trimmed === 'undefined') return false;
    return trimmed.split('.').length === 3;
}

const http = axios.create({
    baseURL: API_URL,
});

export function setAuthToken(token) {
    if (isLikelyJwt(token)) {
        http.defaults.headers.common[AUTH_HEADER] = `Bearer ${token.trim()}`;
        return;
    }

    delete http.defaults.headers.common[AUTH_HEADER];
}

export function syncAuthToken() {
    const token = localStorage.getItem('token');
    if (!isLikelyJwt(token)) {
        localStorage.removeItem('token');
        setAuthToken(null);
        return null;
    }
    setAuthToken(token);
    return token.trim();
}

async function handleUnauthorized() {
    localStorage.removeItem('token');
    setAuthToken(null);

    if (window.location.pathname !== '/login') {
        window.location.assign('/login');
    }
}

http.interceptors.response.use(
    (response) => response,
    async (error) => {
        const status = error?.response?.status;
        const token = localStorage.getItem('token');

        if (status === 401 && token) {
            await handleUnauthorized();
        }

        return Promise.reject(error);
    },
);

syncAuthToken();

export default http;
