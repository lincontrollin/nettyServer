package com.lin.nettyServer;

import io.netty.buffer.ByteBuf;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

import com.alibaba.fastjson.JSONObject;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class AppTest 
    extends TestCase
{
	private int port = 8089;
	private String host = "127.0.0.1";
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp()throws Exception
    {
        Socket socket = new Socket(host, port);
        JSONObject json = new JSONObject();
        json.put("path", "/test/test.action");
        JSONObject jsonParams = new JSONObject();
        jsonParams.put("hello", "firsttest");
        json.put("params", jsonParams);
        OutputStream os = socket.getOutputStream();
        byte [] content = json.toString().getBytes();
        int size = content.length+4;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.putInt(size);
        buffer.put(content);
        buffer.flip();
        os.write(buffer.array());
        os.flush();
        os.close();
        socket.close();
    }
}
