import axios from 'axios';

const API_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
const AUTH_HEADER = 'Authorization';

const http = axios.create({
    baseURL: API_URL,
});

export function setAuthToken(token) {
    if (token) {
        http.defaults.headers.common[AUTH_HEADER] = `Bearer ${token}`;
        return;
    }

    delete http.defaults.headers.common[AUTH_HEADER];
}

export function syncAuthToken() {
    const token = localStorage.getItem('token');
    setAuthToken(token);
    return token;
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
