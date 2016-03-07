package com.lin.nettyServer.actions;

import java.util.Date;

import com.alibaba.fastjson.JSONObject;
import com.lin.nettyServer.annotations.ActionAnnotation;

public class TestAction extends BaseAction{
	
	
	
	@ActionAnnotation(path="/test/test.action")
	public JSONObject test(JSONObject object)throws Exception{
		logger.info("url=test,params="+object);
		object.put("time", new Date());
		return object;
	}

}
