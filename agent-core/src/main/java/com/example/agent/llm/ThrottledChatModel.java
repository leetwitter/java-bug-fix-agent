package com.example.agent.llm;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.Set;

/**
 * Wraps a {@link ChatModel} and enforces a minimum interval between calls, a
 * client-side rate limiter for endpoints that throttle by requests-per-minute.
 *
 * <p>The self-critique configurations roughly double the call volume per task,
 * which is exactly what trips an RPM cap; spacing every {@code chat()} (not just
 * every task) is what keeps a burst under the limit. The convenience
 * {@code chat(String)}/{@code chat(ChatMessage...)} defaults route through
 * {@link #chat(ChatRequest)}, so they inherit the throttle automatically.
 */
public final class ThrottledChatModel implements ChatModel {

    private final ChatModel delegate;
    private final long intervalNanos;
    private final Object lock = new Object();
    private long nextAllowedNanos = 0L;

    /**
     * @param delegate         the real model to call
     * @param minIntervalMillis minimum wall-clock gap between successive calls;
     *                          must be positive
     */
    public ThrottledChatModel(ChatModel delegate, long minIntervalMillis) {
        if (minIntervalMillis <= 0) {
            throw new IllegalArgumentException("minIntervalMillis must be > 0, was " + minIntervalMillis);
        }
        this.delegate = delegate;
        this.intervalNanos = minIntervalMillis * 1_000_000L;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        awaitSlot();
        return delegate.chat(chatRequest);
    }

    /** Blocks until at least {@code intervalNanos} has elapsed since the previous call started. */
    private void awaitSlot() {
        synchronized (lock) {
            long now = System.nanoTime();
            if (now < nextAllowedNanos) {
                long sleepMillis = (nextAllowedNanos - now) / 1_000_000L;
                if (sleepMillis > 0) {
                    try {
                        Thread.sleep(sleepMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while throttling chat calls", e);
                    }
                }
            }
            nextAllowedNanos = System.nanoTime() + intervalNanos;
        }
    }

    // --- pure delegation: capability/metadata queries carry no rate cost ---

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
