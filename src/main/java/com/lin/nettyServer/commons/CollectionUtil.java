package com.lin.nettyServer.commons;

import java.util.Collection;

public class CollectionUtil {
	
	/**
	 * 集合是否为空
	 * @param collection
	 * @return
	 */
	@SuppressWarnings("rawtypes") 
	public static  boolean isNotEmpty(Collection collection){
		return collection!=null && collection.size()>0;
	}

}
