/* Published under the Mozilla Public License 2.0. */
package com.mirth.connect.donkey.server.channel;

import java.util.Objects;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Status;
import org.apache.logging.log4j.LogManager;

/**
 * Optional in-process observer. Providers own telemetry; callbacks must not block or mutate
 * message behavior. An observation belongs to its starting thread. A provider that fails during
 * start/activation must restore any context it already attached before throwing.
 */
public final class MessageTelemetry {
    public enum Stage { SOURCE, TRANSFORM, SEND, RESPONSE }
    public interface Observation extends AutoCloseable {
        default void status(Status status) { }
        default void failed(Throwable failure) { }
        @Override void close();
    }
    public interface Provider {
        Observation start(Stage stage, ConnectorMessage message);
        /**
         * Before the source map becomes read-only and is first persisted. May add only private,
         * content-free propagation data to sourceMap; preserve all application entries. This
         * callback must open no span/scope or other resource requiring later cleanup.
         */
        default void beforeStore(ConnectorMessage message, Map<String, Object> sourceMap) { }
        /**
         * Capture immutable context now, without opening a scope or allocating live resources.
         * The supplier attaches it only if the task runs; its observation restores worker state.
         * Null means no transfer is needed. Never capture a thread-bound open scope.
         */
        default Supplier<Observation> capture() { return null; }
    }
    private static final Observation NONE = () -> { };
    private static final AtomicReference<Registration> CURRENT = new AtomicReference<>();
    private MessageTelemetry() { }

    /**
     * Exactly one provider; the idempotent token detaches only this installation and never blocks
     * traffic. Existing observations and captured context activations retain the old provider,
     * which must allow them to finish safely after detach. New stage observations (including those
     * inside captured tasks) select the current installation. This token does not shut down resources.
     */
    public static AutoCloseable install(Provider provider) {
        Registration registration = new Registration(Objects.requireNonNull(provider));
        if (!CURRENT.compareAndSet(null, registration)) throw new IllegalStateException("Message telemetry already installed");
        return () -> CURRENT.compareAndSet(registration, null);
    }
    public static Observation start(Stage stage, ConnectorMessage message) {
        Registration registration = CURRENT.get();
        if (registration == null) return NONE;
        return observe(registration, () -> registration.provider.start(stage, message));
    }
    public static void beforeStore(ConnectorMessage message, Map<String, Object> sourceMap) {
        Registration registration = CURRENT.get();
        if (registration == null) return;
        try { registration.provider.beforeStore(message, sourceMap); }
        catch (Throwable failure) { registration.failed(failure); }
    }
    private static Observation observe(Registration registration, Supplier<Observation> start) {
        try {
            Observation observation = start.get();
            return observation == null ? NONE : new Observation() {
                public void status(Status status) { try { observation.status(status); } catch (Throwable failure) { registration.failed(failure); } }
                public void failed(Throwable cause) {
                    try { observation.failed(cause); }
                    catch (Throwable failure) {
                        if (fatal(cause) && fatal(failure)) {
                            if (cause != failure) {
                                try { cause.addSuppressed(failure); }
                                catch (Throwable ignored) { /* Preserve the original fatal even if suppression cannot allocate. */ }
                            }
                        } else registration.failed(failure);
                    }
                }
                public void close() { try { observation.close(); } catch (Throwable failure) { registration.failed(failure); } }
            };
        } catch (Throwable failure) { registration.failed(failure); return NONE; }
    }
    private static boolean fatal(Throwable failure) {
        return failure instanceof VirtualMachineError || failure instanceof ThreadDeath;
    }
    public static <T> Callable<T> wrap(Callable<T> task) {
        Objects.requireNonNull(task);
        Registration registration = CURRENT.get();
        if (registration == null) return task;
        try {
            Supplier<Observation> captured = registration.provider.capture();
            if (captured == null) return task;
            return () -> {
                try (Observation scope = observe(registration, captured)) {
                    return task.call();
                }
            };
        }
        catch (Throwable failure) { registration.failed(failure); return task; }
    }
    private static final class Registration {
        final Provider provider;
        final AtomicBoolean warned = new AtomicBoolean();
        Registration(Provider provider) { this.provider = provider; }
        void failed(Throwable failure) {
            if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
            if (failure instanceof ThreadDeath) throw (ThreadDeath) failure;
            if (warned.compareAndSet(false, true)) {
                try {
                    LogManager.getLogger(MessageTelemetry.class)
                            .warn("Message telemetry callback failed; further warnings for this installation are suppressed");
                } catch (VirtualMachineError | ThreadDeath fatal) { throw fatal; }
                catch (Throwable loggingFailure) { /* A broken appender must not break message processing. */ }
            }
        }
    }
}
