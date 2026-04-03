package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitmart;

import com.solfini.common.CustomLogger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import java.util.zip.Inflater;

public class BitmartStringCompress {
    private static final CustomLogger LOGGER = CustomLogger.getLogger(BitmartStringCompress.class);
    private static String uncompress(final ByteBuf buf) {
        try {
            LOGGER.debug("Starting decompression of buffer with readable bytes: " + buf.readableBytes());
            final byte[] temp = new byte[buf.readableBytes()];
            final ByteBufInputStream bis = new ByteBufInputStream(buf);
            bis.read(temp);
            bis.close();
            final Inflater decompresser = new Inflater(true);
            decompresser.setInput(temp, 0, temp.length);
            final StringBuilder sb = new StringBuilder();
            final byte[] result = new byte[1024];
            while (!decompresser.finished()) {
                final int resultLength = decompresser.inflate(result);
                sb.append(new String(result, 0, resultLength, "UTF-8"));
            }
            decompresser.end();
            LOGGER.debug("Decompression successful, result length: " + sb.length());
            return sb.toString();
        } catch (final Exception e) {
            LOGGER.error("uncompress exception", e);
        }
        return "";
    }

    public static String decode(final ByteBuf content){
        LOGGER.debug("Decoding ByteBuf content with readable bytes: " + content.readableBytes());
        final byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);
        final ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
        final String str = uncompress(byteBuf);
        LOGGER.debug("Decoded string length: " + str.length());
        return str;
    }

}