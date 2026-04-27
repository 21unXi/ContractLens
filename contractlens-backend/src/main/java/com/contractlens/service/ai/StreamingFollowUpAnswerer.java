package com.contractlens.service.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class StreamingFollowUpAnswerer {

    private static final String SYSTEM_PROMPT = """
            你是一位专业的租房合同法律顾问。请结合合同原文、参考资料和已有对话，回答用户的追问。
            
            【回答要求】
            1. 回答必须聚焦租房合同风险，不要泛化到其他合同场景
            2. 充分结合合同原文、参考资料和已有对话，不要脱离上下文
            3. 结论尽量明确，必要时分点说明
            4. 如果涉及法律依据，优先引用中国现行法律法规
            5. 不要输出 JSON，直接输出适合前端流式展示的自然语言内容
            6. 在回答追问后，如发现与追问主题强相关的“缺失条款风险/未提及但应当约定的风险”，必须追加简短的“补充提醒”，并给出可落地的补充建议
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            【合同内容】
            %s
            
            【参考资料】
            %s
            
            【图谱上下文】
            %s
            
            【已有对话】
            %s
            
            【用户追问】
            %s
            """;

    private final StreamingChatLanguageModel streamingChatLanguageModel;

    public StreamingFollowUpAnswerer(StreamingChatLanguageModel streamingChatLanguageModel) {
        this.streamingChatLanguageModel = streamingChatLanguageModel;
    }

    public String streamAnswer(
            String contractContent,
            String retrievedContext,
            String graphContext,
            String conversationHistory,
            String question,
            Consumer<String> onDelta,
            Supplier<Boolean> isCancelled
    ) {
        StringBuilder full = new StringBuilder();

        List<ChatMessage> messages = List.of(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(String.format(
                        USER_PROMPT_TEMPLATE,
                        safe(contractContent),
                        safe(retrievedContext),
                        safe(graphContext),
                        safe(conversationHistory),
                        safe(question)
                ))
        );

        Object lock = new Object();
        Throwable[] error = new Throwable[1];
        boolean[] done = new boolean[]{false};

        streamingChatLanguageModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                if (token == null || token.isEmpty()) return;
                if (Boolean.TRUE.equals(isCancelled.get())) return;
                full.append(token);
                onDelta.accept(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                synchronized (lock) {
                    done[0] = true;
                    lock.notifyAll();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                synchronized (lock) {
                    error[0] = throwable;
                    done[0] = true;
                    lock.notifyAll();
                }
            }
        });

        synchronized (lock) {
            while (!done[0]) {
                try {
                    lock.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while streaming", e);
                }
                if (Boolean.TRUE.equals(isCancelled.get())) {
                    throw new IllegalStateException("Cancelled");
                }
            }
        }

        if (error[0] != null) {
            if (error[0] instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(error[0].getMessage(), error[0]);
        }

        return full.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
