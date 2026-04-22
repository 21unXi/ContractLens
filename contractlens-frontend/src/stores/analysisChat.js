import { defineStore } from 'pinia';
import { contractService } from '../api/contract';

const DEFAULT_INITIAL_PROMPT = '请对这份租房合同做一次完整风险分析。';

export const useAnalysisChatStore = defineStore('analysisChat', {
    state: () => ({
        sessions: {},
    }),
    actions: {
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
                };
            }
            return this.sessions[key];
        },
        resetSession(contractId) {
            const key = String(contractId);
            this.sessions[key] = {
                contractId,
                messages: [],
                status: null,
                streaming: false,
                error: null,
                analysisResult: null,
                lastAssistantMessageIndex: null,
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
        },
        setAnalysisResult(contractId, analysisResult) {
            const session = this.ensureSession(contractId);
            session.analysisResult = analysisResult;
        },
        async startInitial(contractId, { signal } = {}) {
            const session = this.ensureSession(contractId);
            session.error = null;
            session.streaming = true;
            session.status = null;
            session.lastAssistantMessageIndex = null;
            if (session.messages.length === 0) {
                this.appendUser(contractId, DEFAULT_INITIAL_PROMPT);
            }

            await contractService.streamAnalyzeContract(contractId, null, {
                signal,
                onStatus: (payload) => {
                    this.setStatus(contractId, payload);
                },
                onAnswer: (payload) => {
                    this.appendAssistantChunk(contractId, payload?.chunk, payload?.isLast);
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

            this.appendUser(contractId, text);

            await contractService.streamAnalyzeContract(contractId, text, {
                signal,
                onStatus: (payload) => {
                    this.setStatus(contractId, payload);
                },
                onAnswer: (payload) => {
                    this.appendAssistantChunk(contractId, payload?.chunk, payload?.isLast);
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
