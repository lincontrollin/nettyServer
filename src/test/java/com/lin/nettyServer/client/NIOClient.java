package com.lin.nettyServer.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class NIOClient {
	int port = 8088;
	
	String host = "localhost";
	
	NioEventLoopGroup workgroup = new NioEventLoopGroup();
	
	public void init()throws Exception{
		try{
			Bootstrap b = new Bootstrap();
			b.group(workgroup).channel(NioSocketChannel.class)
				.handler(new ClientHandler()).option(ChannelOption.SO_KEEPALIVE, true);
			
			ChannelFuture future = b.bind(host, port).sync();
			future.channel().closeFuture().sync();
		}finally{
			workgroup.shutdownGracefully();
		}
	}
	
	public static void main(String[] args) throws Exception{
		new NIOClient().init();
	}
	
	

}
