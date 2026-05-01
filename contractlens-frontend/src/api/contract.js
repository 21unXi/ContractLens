import http from './http';
import { isLikelyJwt } from './http';

const API_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

function safeJsonParse(value, fallback) {
    if (value == null) return fallback;
    if (typeof value !== 'string') return value;
    const trimmed = value.trim();
    if (!trimmed) return fallback;
    try {
        return JSON.parse(trimmed);
    } catch {
        return fallback;
    }
}

function normalizeAnalysisResult(raw) {
    if (!raw) return null;

    const partyTenantRisks = raw.party_tenant_risks ?? safeJsonParse(raw.partyTenantRisks, []);
    const partyLessorRisks = raw.party_lessor_risks ?? safeJsonParse(raw.partyLessorRisks, []);
    const suggestions = raw.suggestions ?? safeJsonParse(raw.suggestions, []);
    const contractTags = raw.contract_tags ?? safeJsonParse(raw.contractTags, []);

    return {
        ...raw,
        risk_level: raw.risk_level ?? raw.riskLevel,
        risk_score: raw.risk_score ?? raw.riskScore,
        party_tenant_risks: Array.isArray(partyTenantRisks) ? partyTenantRisks : [],
        party_lessor_risks: Array.isArray(partyLessorRisks) ? partyLessorRisks : [],
        suggestions: Array.isArray(suggestions) ? suggestions : [],
        contract_tags: Array.isArray(contractTags) ? contractTags : [],
    };
}

async function parseSseResponse(response, { onEvent, signal }) {
    if (!response.ok) {
        if (response.status === 401 && localStorage.getItem('token')) {
            localStorage.removeItem('token');
            if (window.location.pathname !== '/login') {
                window.location.assign('/login');
            }
        }
        const text = await response.text().catch(() => '');
        const err = new Error(text || `HTTP ${response.status}`);
        err.status = response.status;
        throw err;
    }

    const reader = response.body?.getReader?.();
    if (!reader) {
        throw new Error('Streaming not supported');
    }

    const decoder = new TextDecoder();
    let buffer = '';
    let shouldStop = false;

    const dispatch = (block) => {
        const lines = block.split(/\r?\n/);
        let eventName = null;
        const dataLines = [];
        for (const line of lines) {
            const trimmed = line.replace(/\r$/, '');
            if (!trimmed) continue;
            if (trimmed.startsWith('event:')) {
                eventName = trimmed.slice(6).trim();
                continue;
            }
            if (trimmed.startsWith('data:')) {
                dataLines.push(trimmed.slice(5).trimStart());
            }
        }

        if (!eventName || dataLines.length === 0) return;
        const dataText = dataLines.join('\n');
        let data;
        try {
            data = JSON.parse(dataText);
        } catch {
            data = dataText;
        }
        const result = onEvent?.(eventName, data);
        if (result === true) {
            shouldStop = true;
        }
    };

    while (true) {
        if (signal?.aborted) {
            try {
                await reader.cancel();
            } catch {}
            return;
        }

        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const parts = buffer.split(/\r?\n\r?\n/);
        buffer = parts.pop() || '';
        for (const part of parts) {
            dispatch(part);
            if (shouldStop) {
                try {
                    await reader.cancel();
                } catch {}
                return;
            }
        }
    }

    if (buffer.trim()) {
        dispatch(buffer);
    }
}

export const contractService = {
    uploadContract(file) {
        const formData = new FormData();
        formData.append('file', file);
        return http.post('/contracts/upload', formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
    },

    getUserContracts() {
        return http.get('/contracts');
    },

    deleteContract(contractId) {
        return http.delete(`/contracts/${contractId}`);
    },

    getChatHistory(contractId) {
        return http.get(`/analysis/contracts/${contractId}/chat/history`);
    },

    async getAnalysisResult(contractId) {
        const response = await http.get(`/analysis/contracts/${contractId}/result`);
        return { ...response, data: normalizeAnalysisResult(response?.data) };
    },

    async streamAnalyzeContract(contractId, message, { onStatus, onAnswer, onDone, onError, signal } = {}) {
        const token = localStorage.getItem('token');
        const headers = {
            Accept: 'text/event-stream',
        };
        if (isLikelyJwt(token)) {
            headers.Authorization = `Bearer ${token.trim()}`;
        } else if (token) {
            localStorage.removeItem('token');
        }

        let body;
        if (message != null && String(message).trim()) {
            headers['Content-Type'] = 'application/json';
            body = JSON.stringify({ message });
        }

        const response = await fetch(`${API_URL}/analysis/contracts/${contractId}/stream`, {
            method: 'POST',
            headers,
            body,
            signal,
        });

        await parseSseResponse(response, {
            signal,
            onEvent: (eventName, payload) => {
                if (eventName === 'status') onStatus?.(payload);
                else if (eventName === 'answer') onAnswer?.(payload);
                else if (eventName === 'done') {
                    const normalized = normalizeAnalysisResult(payload?.analysisResult);
                    onDone?.({ ...payload, analysisResult: normalized });
                    return true;
                } else if (eventName === 'error') onError?.(payload);
                if (eventName === 'error') return true;
            },
        });
    },
};
