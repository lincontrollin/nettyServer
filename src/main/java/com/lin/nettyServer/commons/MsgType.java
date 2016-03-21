package com.lin.nettyServer.commons;

public enum MsgType {
	
	PING(0),
	request(1),
	response(2);
	public int type;
	private MsgType(int type) {
		this.type = type;
	}
}
