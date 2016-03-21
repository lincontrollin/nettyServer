package com.lin.nettyServer.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;

import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;


public class ClientHandler extends ByteToMessageDecoder{
	
	private static Logger logger = Logger.getLogger(ClientHandler.class);
	
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in,
			List<Object> out) throws Exception {
		ByteBuf buf = in;
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
		
	}

}
