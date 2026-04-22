import http from './http';

export const knowledgeService = {
    getStatus() {
        return http.get('/knowledge/status');
    },

    listDocs({ page = 0, size = 20 } = {}) {
        return http.get('/knowledge/docs', { params: { page, size } });
    },

    rebuild() {
        return http.post('/knowledge/rebuild');
    },
};

