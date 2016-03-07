package com.lin.nettyServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import com.lin.nettyServer.commons.Dispather;
import com.lin.nettyServer.commons.ProtoHandler;


/**
 * Hello world!
 *
 */
public class App 
{
	String path = "com.lin.nettyServer.actions";
	String host = "127.0.0.1";
	int port = 8089;
	Dispather dispather;
	EventLoopGroup bossGroup ;
	EventLoopGroup workGroup ;
	public void init() throws Exception{
		dispather = new Dispather();
		dispather.init(path);
		 
		ServerBootstrap bootstrap = new ServerBootstrap();
		bossGroup = new NioEventLoopGroup(1);
		workGroup = new NioEventLoopGroup();
		try{
			bootstrap.group(bossGroup, workGroup)
			.channel(NioServerSocketChannel.class)
			.childHandler(new ProtoHandler(dispather));
			// Start the server.
			ChannelFuture f = bootstrap.bind(port).sync();
			// Wait until the server socket is closed.
			f.channel().closeFuture().sync();
		} finally {
			// Shut down all event loops to terminate all threads.
			bossGroup.shutdownGracefully();
			workGroup.shutdownGracefully();
		}
	}
    public static void main( String[] args )throws Exception
    {
    	App app = new App();
    	app.init();
    }
}
