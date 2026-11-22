package org.traffichunter.titan.dispatch;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.traffichunter.titan.bootstrap.Settings;
import org.traffichunter.titan.core.resilience.flowcontrol.FlowControlConfiguration;
import org.traffichunter.titan.core.resilience.flowcontrol.MemoryPressureDamper;
import org.traffichunter.titan.core.spi.*;
import org.traffichunter.titan.core.util.OptionKey;

/**
 * SPI launcher that installs fanout behavior into a managed STOMP server.
 *
 * <p>The bootstrap/core SPI discovers this class as a {@link FanoutLauncher}.
 * When the managed server is STOMP, the launcher creates a fanout gateway,
 * connects it to the server-side STOMP connection registry through
 * {@link StompServerFanoutLauncher}, and registers {@link StompSendToFanoutHandler}
 * as the SEND command ingress.</p>
 *
 * <pre>{@code
 * Titan bootstrap
 *      |
 *      v
 * Managed STOMP server
 *      |
 *      v
 * StompServerFanoutLauncher.apply(...)
 *      |
 *      v
 * SEND handler -> Gateway -> StompDispatchExporter
 * }</pre>
 */
@SuppressWarnings("unused")
public final class StompServerFanoutLauncher implements FanoutLauncher {

    private static final Logger log = LoggerFactory.getLogger(StompServerFanoutLauncher.class);

    private static final OptionKey<String> FANOUT_MODE = OptionKey.stringKey("fanout-mode");
    private static final String FANOUT_MODE_VIRTUAL = "virtual";

    private static final OptionKey<Integer> MAX_MESSAGE_BLOCK_SIZE = OptionKey.intKey("max-message-block-size");
    private static final OptionKey<Integer> MAX_MESSAGE_BLOCK_BYTES = OptionKey.intKey("max-message-block-bytes");
    private static final int MESSAGE_BLOCK_SIZE_LIMIT = 64;
    // A connection's write buffer resumes below 32KiB, and one block must fit under that.
    private static final int MESSAGE_BLOCK_BYTES_LIMIT = 32 * 1024;

    @Override
    public List<OptionKey<?>> optionKeys() {
        return List.of(FANOUT_MODE, MAX_MESSAGE_BLOCK_SIZE, MAX_MESSAGE_BLOCK_BYTES);
    }

    @Override
    public boolean supports(
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        return "stomp".equalsIgnoreCase(protocol) && findAdapter(protocol, transport, protocolOptions, managedServer) != null;
    }

    @Override
    public void apply(
            final Settings settings,
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        checkFanoutMode(protocolOptions);
        int maxMessageBlockSize = MAX_MESSAGE_BLOCK_SIZE.get(
                protocolOptions, FanoutDispatcher.DEFAULT_MAX_MESSAGE_BLOCK_SIZE);
        if (maxMessageBlockSize < 1 || maxMessageBlockSize > MESSAGE_BLOCK_SIZE_LIMIT) {
            throw new IllegalStateException(MAX_MESSAGE_BLOCK_SIZE.name() + " must be between 1 and "
                    + MESSAGE_BLOCK_SIZE_LIMIT + ": " + maxMessageBlockSize);
        }
        int maxMessageBlockBytes = MAX_MESSAGE_BLOCK_BYTES.get(
                protocolOptions, FanoutDispatcher.DEFAULT_MAX_MESSAGE_BLOCK_BYTES);
        if (maxMessageBlockBytes < 1 || maxMessageBlockBytes > MESSAGE_BLOCK_BYTES_LIMIT) {
            throw new IllegalStateException(MAX_MESSAGE_BLOCK_BYTES.name() + " must be between 1 and "
                    + MESSAGE_BLOCK_BYTES_LIMIT + ": " + maxMessageBlockBytes);
        }

        ManagedServerFanoutAdapter adapter = findAdapter(protocol, transport, protocolOptions, managedServer);
        if (adapter == null) {
            throw new IllegalStateException("No fanout adapter for protocol=" + protocol + ", transport=" + transport);
        }

        adapter.apply(
                protocol,
                transport,
                protocolOptions,
                managedServer,
                dispatchExporter -> {
                    Settings.FlowControlSettings flowControl = settings.flowControl();
                    Settings.QueueFlowControlSettings queue = flowControl.queue();
                    long maxPendingBytes = flowControl.enabled() && queue.enabled()
                            ? queue.maxPendingBytes()
                            : DispatcherQueue.DEFAULT_MAX_PENDING_BYTES;
                    long resumePendingBytes = flowControl.enabled() && queue.enabled()
                            ? queue.resumePendingBytes()
                            : DestinationQueueMetadata.defaultResumePendingBytes(maxPendingBytes);
                    PathFinder pathFinder = PathFinder.getDefault(maxPendingBytes, resumePendingBytes);
                    Gateway gateway = Gateway.of(
                            dispatchExporter,
                            pathFinder,
                            maxMessageBlockSize,
                            maxMessageBlockBytes
                    );
                    DispatcherQueueManagers.register(managedServer.name(), DispatcherQueueManager.of(pathFinder));
                    Settings.HeapFlowControlSettings heap = flowControl.heap();
                    if (flowControl.enabled() && heap.enabled()) {
                        MemoryPressureDamper damper = new MemoryPressureDamper(
                                new FlowControlConfiguration(
                                        heap.highWatermark(),
                                        heap.lowWatermark()
                                )
                        );
                        gateway.chainHandler(chain -> chain.addFirst(
                                new FlowControlDispatchChainHandler(damper)
                        ));
                    }
                    return gateway;
                }
        );
        log.info("Fanout launcher started fanout server = {}", managedServer.name());
    }

    @Override
    public void apply(
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        apply(new Settings(null, null, null), protocol, transport, protocolOptions, managedServer);
    }

    private static @Nullable ManagedServerFanoutAdapter findAdapter(
            final String protocol,
            final String transport,
            final Map<String, String> protocolOptions,
            final ManagedServer managedServer
    ) {
        for (ManagedServerFanoutAdapter adapter : ManagedServerFanoutAdapter.load()) {
            if (adapter.supports(protocol, transport, protocolOptions, managedServer)) {
                return adapter;
            }
        }

        return null;
    }

    /** Dispatch runs on virtual threads only; the option remains so existing configurations still load. */
    private static void checkFanoutMode(final Map<String, String> protocolOptions) {
        String raw = FANOUT_MODE.get(protocolOptions, FANOUT_MODE_VIRTUAL);
        if (!FANOUT_MODE_VIRTUAL.equals(raw.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "Unsupported " + FANOUT_MODE.name() + "=" + raw + ". Only \"" + FANOUT_MODE_VIRTUAL + "\" is supported");
        }
    }
}
