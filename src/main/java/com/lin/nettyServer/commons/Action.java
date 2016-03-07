package com.lin.nettyServer.commons;

import java.lang.reflect.Method;

public class Action {
	public Action(Method method, String path, Object instance) {
		super();
		this.method = method;
		this.path = path;
		this.instance = instance;
	}
	public Method method;
	public String path;
	public Object instance;

}
