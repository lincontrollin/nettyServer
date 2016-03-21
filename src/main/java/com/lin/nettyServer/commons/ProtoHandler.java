package com.lin.nettyServer.commons;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.RecyclableArrayList;
import io.netty.util.internal.StringUtil;

import java.util.List;

import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

@Sharable
public class ProtoHandler extends ChannelHandlerAdapter{
	
	private static Logger logger = Logger.getLogger(ProtoHandler.class);
	private Dispather dispather;
	
    /**
     * Cumulate {@link ByteBuf}s by merge them into one {@link ByteBuf}'s, using memory copies.
     */
    public static final Cumulator MERGE_CUMULATOR = new Cumulator() {
        @Override
        public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
            ByteBuf buffer;
            if (cumulation.writerIndex() > cumulation.maxCapacity() - in.readableBytes()
                    || cumulation.refCnt() > 1) {
                // Expand cumulation (by replace it) when either there is not more room in the buffer
                // or if the refCnt is greater then 1 which may happen when the user use slice().retain() or
                // duplicate().retain().
                //
                // See:
                // - https://github.com/netty/netty/issues/2327
                // - https://github.com/netty/netty/issues/1764
                buffer = expandCumulation(alloc, cumulation, in.readableBytes());
            } else {
                buffer = cumulation;
            }
            buffer.writeBytes(in);
            in.release();
            return buffer;
        }
    };

    /**
     * Cumulate {@link ByteBuf}s by add them to a {@link CompositeByteBuf} and so do no memory copy whenever possible.
     * Be aware that {@link CompositeByteBuf} use a more complex indexing implementation so depending on your use-case
     * and the decoder implementation this may be slower then just use the {@link #MERGE_CUMULATOR}.
     */
    public static final Cumulator COMPOSITE_CUMULATOR = new Cumulator() {
        @Override
        public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
            ByteBuf buffer;
            if (cumulation.refCnt() > 1) {
                // Expand cumulation (by replace it) when the refCnt is greater then 1 which may happen when the user
                // use slice().retain() or duplicate().retain().
                //
                // See:
                // - https://github.com/netty/netty/issues/2327
                // - https://github.com/netty/netty/issues/1764
                buffer = expandCumulation(alloc, cumulation, in.readableBytes());
                buffer.writeBytes(in);
                in.release();
            } else {
                CompositeByteBuf composite;
                if (cumulation instanceof CompositeByteBuf) {
                    composite = (CompositeByteBuf) cumulation;
                } else {
                    int readable = cumulation.readableBytes();
                    composite = alloc.compositeBuffer();
                    composite.addComponent(cumulation).writerIndex(readable);
                }
                composite.addComponent(in).writerIndex(composite.writerIndex() + in.readableBytes());
                buffer = composite;
            }
            return buffer;
        }
    };

    ByteBuf cumulation;
    private Cumulator cumulator = MERGE_CUMULATOR;
    private boolean singleDecode;
    private boolean first;

    public ProtoHandler(Dispather dispather) {
//    	if (this.isSharable()) {
//            throw new IllegalStateException("@Sharable annotation is not allowed");
//        }
    	this.dispather = dispather;
    }

    /**
     * If set then only one message is decoded on each {@link #channelRead(ChannelHandlerContext, Object)}
     * call. This may be useful if you need to do some protocol upgrade and want to make sure nothing is mixed up.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public void setSingleDecode(boolean singleDecode) {
        this.singleDecode = singleDecode;
    }

    /**
     * If {@code true} then only one message is decoded on each
     * {@link #channelRead(ChannelHandlerContext, Object)} call.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public boolean isSingleDecode() {
        return singleDecode;
    }

    /**
     * Set the {@link Cumulator} to use for cumulate the received {@link ByteBuf}s.
     */
    public void setCumulator(Cumulator cumulator) {
        if (cumulator == null) {
            throw new NullPointerException("cumulator");
        }
        this.cumulator = cumulator;
    }

    /**
     * Returns the actual number of readable bytes in the internal cumulative
     * buffer of this decoder. You usually do not need to rely on this value
     * to write a decoder. Use it only when you must use it at your own risk.
     * This method is a shortcut to {@link #internalBuffer() internalBuffer().readableBytes()}.
     */
    protected int actualReadableBytes() {
        return internalBuffer().readableBytes();
    }

    /**
     * Returns the internal cumulative buffer of this decoder. You usually
     * do not need to access the internal buffer directly to write a decoder.
     * Use it only when you must use it at your own risk.
     */
    protected ByteBuf internalBuffer() {
        if (cumulation != null) {
            return cumulation;
        } else {
            return Unpooled.EMPTY_BUFFER;
        }
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        ByteBuf buf = internalBuffer();
        int readable = buf.readableBytes();
        if (readable > 0) {
            ByteBuf bytes = buf.readBytes(readable);
            buf.release();
            ctx.fireChannelRead(bytes);
            ctx.fireChannelReadComplete();
        } else {
            buf.release();
        }
        cumulation = null;
        handlerRemoved0(ctx);
    }

    /**
     * Gets called after the {@link ByteToMessageDecoder} was removed from the actual context and it doesn't handle
     * events anymore.
     */
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception { }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            RecyclableArrayList out = RecyclableArrayList.newInstance();
            try {
                ByteBuf data = (ByteBuf) msg;
                first = cumulation == null;
                if (first) {
                    cumulation = data;
                } else {
                    cumulation = cumulator.cumulate(ctx.alloc(), cumulation, data);
                }
                callDecode(ctx, cumulation, out);
            } catch (DecoderException e) {
                throw e;
            } catch (Throwable t) {
                throw new DecoderException(t);
            } finally {
                if (cumulation != null && !cumulation.isReadable()) {
                    cumulation.release();
                    cumulation = null;
                }
                int size = out.size();

                for (int i = 0; i < size; i ++) {
                    ctx.fireChannelRead(out.get(i));
                }
                out.recycle();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (cumulation != null && !first && cumulation.refCnt() == 1) {
            // discard some bytes if possible to make more room in the
            // buffer but only if the refCnt == 1  as otherwise the user may have
            // used slice().retain() or duplicate().retain().
            //
            // See:
            // - https://github.com/netty/netty/issues/2327
            // - https://github.com/netty/netty/issues/1764
            cumulation.discardSomeReadBytes();
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        RecyclableArrayList out = RecyclableArrayList.newInstance();
        try {
            if (cumulation != null) {
                callDecode(ctx, cumulation, out);
                decodeLast(ctx, cumulation, out);
            } else {
                decodeLast(ctx, Unpooled.EMPTY_BUFFER, out);
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            try {
                if (cumulation != null) {
                    cumulation.release();
                    cumulation = null;
                }
                int size = out.size();
                for (int i = 0; i < size; i++) {
                    ctx.fireChannelRead(out.get(i));
                }
                if (size > 0) {
                    // Something was read, call fireChannelReadComplete()
                    ctx.fireChannelReadComplete();
                }
                ctx.fireChannelInactive();
            } finally {
                // recycle in all cases
                out.recycle();
            }
        }
    }

    /**
     * Called once data should be decoded from the given {@link ByteBuf}. This method will call
     * {@link #decode(ChannelHandlerContext, ByteBuf, List)} as long as decoding should take place.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link List} to which decoded messages should be added
     */
    protected void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        try {
            while (in.isReadable()) {
                int outSize = out.size();
                int oldInputLength = in.readableBytes();
                decode(ctx, in, out);

                // Check if this handler was removed before continuing the loop.
                // If it was removed, it is not safe to continue to operate on the buffer.
                //
                // See https://github.com/netty/netty/issues/1664
                if (ctx.isRemoved()) {
                    break;
                }

                if (outSize == out.size()) {
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    } else {
                        continue;
                    }
                }

                if (oldInputLength == in.readableBytes()) {
                    throw new DecoderException(
                            StringUtil.simpleClassName(getClass()) +
                            ".decode() did not read anything but decoded a message.");
                }

                if (isSingleDecode()) {
                    break;
                }
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Throwable cause) {
            throw new DecoderException(cause);
        }
    }

    

    /**
     * Is called one last time when the {@link ChannelHandlerContext} goes in-active. Which means the
     * {@link #channelInactive(ChannelHandlerContext)} was triggered.
     *
     * By default this will just call {@link #decode(ChannelHandlerContext, ByteBuf, List)} but sub-classes may
     * override this for some special cleanup operation.
     */
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        decode(ctx, in, out);
    }

    static ByteBuf expandCumulation(ByteBufAllocator alloc, ByteBuf cumulation, int readable) {
        ByteBuf oldCumulation = cumulation;
        cumulation = alloc.buffer(oldCumulation.readableBytes() + readable);
        cumulation.writeBytes(oldCumulation);
        oldCumulation.release();
        return cumulation;
    }

    /**
     * Cumulate {@link ByteBuf}s.
     */
    public interface Cumulator {
        /**
         * Cumulate the given {@link ByteBuf}s and return the {@link ByteBuf} that holds the cumulated bytes.
         * The implementation is responsible to correctly handle the life-cycle of the given {@link ByteBuf}s and so
         * call {@link ByteBuf#release()} if a {@link ByteBuf} is fully consumed.
         */
        ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in);
    }
	

	protected void decode(ChannelHandlerContext arg0, ByteBuf arg1,
			List<Object> arg2) throws Exception {
//		ByteBuf buf = arg1.order(ByteOrder.LITTLE_ENDIAN);
		ByteBuf buf = arg1;
		int size = buf.readableBytes();
		if(size<=0){
			return;
		}
		int length = buf.getInt(0);
		if(length>size){
			logger.info("proto decode length="+length+",readbytes="+size);
			return ;
		}
		int contentSize = length - 4;
		byte[] contentBytes= new byte[contentSize];
		buf.readInt();
		buf.readBytes(contentBytes, 0, contentSize);
		String content = new String(contentBytes, CharsetUtil.UTF_8);
		JSONObject json = JSON.parseObject(content);
		logger.info("proto decode messages="+content);
		dispather.handle(json);
		arg2.add(json);
	}

}
