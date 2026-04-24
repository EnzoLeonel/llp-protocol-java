package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPLayerParser;
import com.flamingo.comm.llp.spi.LayerData;
import com.flamingo.comm.llp.spi.LayerParseResult;
import com.flamingo.comm.llp.spi.ParseErrorReason;
import com.flamingo.comm.llp.util.LayerIds;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

final class SimpleFrameParser implements LLPFrameParser {

    private static final int EXTENDED_METADATA_FLAG = 255;

    private final LayerParserProvider provider;

    SimpleFrameParser(LayerParserProvider provider) {
        this.provider = provider;
    }

    @Override
    public LLPFrame parse(LLPRawFrame rawFrame) {
        if (rawFrame == null) {
            throw new IllegalArgumentException("rawFrame cannot be null");
        }

        ByteBuffer buffer = rawFrame.payload().asReadOnlyBuffer();
        buffer.order(ByteOrder.BIG_ENDIAN);

        NodeChain.Builder chainBuilder = new NodeChain.Builder();

        loop:while (buffer.hasRemaining()) {

            int layerId = Byte.toUnsignedInt(buffer.get());

            // Final layer (ID = 0)
            if (LayerIds.isFinal(layerId)) {
                chainBuilder.add(FinalNode.of(buffer.slice()));
                break loop;
            }

            // --- METADATA LENGTH ---
            if (!buffer.hasRemaining()) {
                chainBuilder.add(new FailureNode(
                        layerId,
                        CoreParseErrorReason.LAYER_TOO_SHORT
                ));
                break loop;
            }

            int metaLen = Byte.toUnsignedInt(buffer.get());

            if (metaLen == EXTENDED_METADATA_FLAG) {
                if (buffer.remaining() < 2) {
                    chainBuilder.add(new FailureNode(
                            layerId,
                            CoreParseErrorReason.LAYER_TOO_SHORT
                    ));
                    break loop;
                }
                metaLen = buffer.getShort() & 0xFFFF;
            }

            // --- METADATA ---
            if (buffer.remaining() < metaLen) {
                chainBuilder.add(new FailureNode(
                        layerId,
                        CoreParseErrorReason.METADATA_TRUNCATED
                ));
                break loop;
            }

            ByteBuffer metadata = buffer.slice();
            metadata.limit(metaLen);
            buffer.position(buffer.position() + metaLen);

            // --- PAYLOAD ---
            ByteBuffer layerPayload = buffer.slice();

            Optional<LLPLayerParser> parserOpt = provider.get(layerId);

            // --- UNKNOWN LAYER ---
            if (parserOpt.isEmpty()) {

                if (LayerIds.isNonSkippable(layerId)) {
                    chainBuilder.add(new FailureNode(
                            layerId,
                            CoreParseErrorReason.UNKNOWN_CRITICAL_LAYER
                    ));
                    break loop;
                }

                // skippable → we're still using the same payload
                chainBuilder.add(new UnknownNode(layerId, toArray(metadata)));
                buffer = layerPayload;
                continue loop;
            }

            // --- PARSE LAYER ---
            try {
                LLPLayerParser parser = parserOpt.get();

                LayerParseResult result = parser.parse(
                        new DefaultLayerData(
                                metadata.asReadOnlyBuffer(),
                                layerPayload.asReadOnlyBuffer()
                        )
                );

                switch (result) {
                    case LayerParseResult.Success success -> {
                        chainBuilder.add(success.node());

                        ByteBuffer next = success.payload();
                        if (!next.hasRemaining()) {
                            break loop;
                        }

                        buffer = next.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
                    }

                    case LayerParseResult.Failure failure -> {

                        ParseErrorReason reason = failure.errorReason();

                        chainBuilder.add(new FailureNode(layerId, toArray(metadata), reason));

                        if (LayerIds.isNonSkippable(layerId)) {
                            break loop;
                        }

                        // skippable → we'll stick with the original payload
                        buffer = layerPayload;
                    }
                }

            } catch (Exception e) {
                // protection against faulty plugins
                chainBuilder.add(new FailureNode(
                        layerId,
                        toArray(metadata),
                        CoreParseErrorReason.PLUGIN_EXCEPTION,
                        e
                ));

                if (LayerIds.isNonSkippable(layerId)) {
                    break loop;
                }

                buffer = layerPayload;
            }
        }

        return new LLPFrame(
                chainBuilder.build(),
                rawFrame.crc(),
                rawFrame.timestamp()
        );
    }

    /**
     * Internal LayerData implementation.
     */
    private record DefaultLayerData(
            ByteBuffer metadata,
            ByteBuffer payload
    ) implements LayerData {
    }

    /**
     * Utility to convert metadata to byte[] only when needed.
     * (Used for UnknownNode which is byte[] based)
     */
    private static byte[] toArray(ByteBuffer buffer) {
        byte[] arr = new byte[buffer.remaining()];
        buffer.duplicate().get(arr);
        return arr;
    }
}