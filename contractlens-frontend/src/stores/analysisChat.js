import { defineStore } from 'pinia';
import { contractService } from '../api/contract';

const DEFAULT_INITIAL_PROMPT = '请对这份租房合同做一次完整风险分析。';

const timers = new Map();

export const useAnalysisChatStore = defineStore('analysisChat', {
    state: () => ({
        sessions: {},
    }),
    actions: {
        startTimer(contractId) {
            const key = String(contractId);
            const session = this.ensureSession(contractId);
            const existing = timers.get(key);
            if (existing) clearInterval(existing);

            session.startedAtMs = Date.now();
            session.elapsedMs = 0;

            const id = setInterval(() => {
                const current = this.sessions?.[key];
                if (!current) return;
                if (!current.streaming || current.startedAtMs == null) return;
                current.elapsedMs = Date.now() - current.startedAtMs;
            }, 1000);

            timers.set(key, id);
        },
        stopTimer(contractId) {
            const key = String(contractId);
            const id = timers.get(key);
            if (id) clearInterval(id);
            timers.delete(key);

            const session = this.sessions?.[key];
            if (session?.startedAtMs != null) {
                session.elapsedMs = Date.now() - session.startedAtMs;
            }
        },
        ensureSession(contractId) {
            const key = String(contractId);
            if (!this.sessions[key]) {
                this.sessions[key] = {
                    contractId,
                    messages: [],
                    status: null,
                    streaming: false,
                    error: null,
                    analysisResult: null,
                    lastAssistantMessageIndex: null,
                    startedAtMs: null,
                    elapsedMs: null,
                };
            }
            return this.sessions[key];
        },
        resetSession(contractId) {
            const key = String(contractId);
            this.stopTimer(contractId);
            this.sessions[key] = {
                contractId,
                messages: [],
                status: null,
                streaming: false,
                error: null,
                analysisResult: null,
                lastAssistantMessageIndex: null,
                startedAtMs: null,
                elapsedMs: null,
            };
            return this.sessions[key];
        },
        appendUser(contractId, content) {
            const session = this.ensureSession(contractId);
            session.messages.push({
                role: 'user',
                content,
                ts: Date.now(),
            });
        },
        beginAssistant(contractId) {
            const session = this.ensureSession(contractId);
            session.messages.push({
                role: 'assistant',
                content: '',
                ts: Date.now(),
            });
            session.lastAssistantMessageIndex = session.messages.length - 1;
        },
        appendAssistantChunk(contractId, chunk, isLast) {
            const session = this.ensureSession(contractId);
            if (session.lastAssistantMessageIndex == null) {
                this.beginAssistant(contractId);
            }
            const msg = session.messages[session.lastAssistantMessageIndex];
            const text = typeof chunk === 'string' ? chunk : '';
            msg.content += msg.content ? `\n\n${text}` : text;
            if (isLast) {
                session.lastAssistantMessageIndex = null;
            }
        },
        appendAssistantDelta(contractId, delta, isLast) {
            const session = this.ensureSession(contractId);
            if (session.lastAssistantMessageIndex == null) {
                this.beginAssistant(contractId);
            }
            const msg = session.messages[session.lastAssistantMessageIndex];
            const text = typeof delta === 'string' ? delta : '';
            msg.content += text;
            if (isLast) {
                session.lastAssistantMessageIndex = null;
            }
        },
        setStatus(contractId, status) {
            const session = this.ensureSession(contractId);
            session.status = status;
        },
        setError(contractId, error) {
            const session = this.ensureSession(contractId);
            session.error = error;
        },
        setStreaming(contractId, streaming) {
            const session = this.ensureSession(contractId);
            session.streaming = streaming;
            if (!streaming) {
                this.stopTimer(contractId);
            }
        },
        setAnalysisResult(contractId, analysisResult) {
            const session = this.ensureSession(contractId);
            session.analysisResult = analysisResult;
        },
        async loadHistory(contractId) {
            const session = this.ensureSession(contractId);
            session.error = null;
            session.lastAssistantMessageIndex = null;
            try {
                const response = await contractService.getChatHistory(contractId);
                const items = Array.isArray(response?.data) ? response.data : [];
                session.messages = items.map((item) => ({
                    role: item?.role || 'assistant',
                    content: item?.content || '',
                    ts: item?.createdAt ? new Date(item.createdAt).getTime() : Date.now(),
                }));
            } catch (err) {
                session.messages = session.messages || [];
            }
        },
        async loadAnalysisResult(contractId) {
            const session = this.ensureSession(contractId);
            try {
                const response = await contractService.getAnalysisResult(contractId);
                session.analysisResult = response?.data || null;
            } catch (err) {
                session.analysisResult = session.analysisResult || null;
            }
        },
        async startInitial(contractId, { signal } = {}) {
            const session = this.ensureSession(contractId);
            session.error = null;
            session.streaming = true;
            session.status = null;
            session.lastAssistantMessageIndex = null;
            this.startTimer(contractId);
            if (session.messages.length === 0) {
                this.appendUser(contractId, DEFAULT_INITIAL_PROMPT);
            }

            await contractService.streamAnalyzeContract(contractId, null, {
                signal,
                onStatus: (payload) => {
                    this.setStatus(contractId, payload);
                },
                onAnswer: (payload) => {
                    if (payload?.delta != null) {
                        this.appendAssistantDelta(contractId, payload?.delta, payload?.isLast);
                    } else {
                        this.appendAssistantChunk(contractId, payload?.chunk, payload?.isLast);
                    }
                },
                onDone: (payload) => {
                    this.setAnalysisResult(contractId, payload?.analysisResult || null);
                    this.setStreaming(contractId, false);
                },
                onError: (payload) => {
                    this.setError(contractId, payload);
                    this.setStreaming(contractId, false);
                },
            });

            this.setStreaming(contractId, false);
        },
        async sendFollowUp(contractId, message, { signal } = {}) {
            const text = String(message || '').trim();
            if (!text) return;

            const session = this.ensureSession(contractId);
            session.error = null;
            session.streaming = true;
            session.status = null;
            session.lastAssistantMessageIndex = null;
            this.startTimer(contractId);

            this.appendUser(contractId, text);

            await contractService.streamAnalyzeContract(contractId, text, {
                signal,
                onStatus: (payload) => {
                    this.setStatus(contractId, payload);
                },
                onAnswer: (payload) => {
                    if (payload?.delta != null) {
                        this.appendAssistantDelta(contractId, payload?.delta, payload?.isLast);
                    } else {
                        this.appendAssistantChunk(contractId, payload?.chunk, payload?.isLast);
                    }
                },
                onDone: (payload) => {
                    if (payload?.analysisResult) {
                        this.setAnalysisResult(contractId, payload.analysisResult);
                    }
                    this.setStreaming(contractId, false);
                },
                onError: (payload) => {
                    this.setError(contractId, payload);
                    this.setStreaming(contractId, false);
                },
            });

            this.setStreaming(contractId, false);
        },
    },
});
