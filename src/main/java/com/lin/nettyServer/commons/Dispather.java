package com.lin.nettyServer.commons;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSONObject;
import com.lin.nettyServer.annotations.ActionAnnotation;
import com.lin.nettyServer.exceptions.NotDirectoryException;

public class Dispather {
	
	static Logger logger = Logger.getLogger(Dispather.class);
	
	Map<String, Action> actionMaps = new HashMap<String, Action>(16);
	
	ForkJoinPool pool = new ForkJoinPool();
	
	public void init(String packageName)throws Exception{
		List<String> classNames = findAllClasses(packageName);
		if(CollectionUtil.isNotEmpty(classNames)){
			for(String fileName : classNames){
				registerMethod(fileName);
			}
		}
	}
	
	/**
	 * 注册所有的action
	 * @param className
	 * @throws Exception
	 */
	@SuppressWarnings("rawtypes")
	private void registerMethod(String className)throws Exception{
		Class claz = Class.forName(className);
		Method[] allMethod=claz.getDeclaredMethods();
		Object instance = claz.newInstance();
		for(Method method : allMethod){
			ActionAnnotation annotation = (ActionAnnotation)method.getAnnotation(ActionAnnotation.class);
			if(annotation!=null){
				String path = annotation.path();
				actionMaps.put(path, new Action(method, path, instance));
			}
		}
	}
	
	/**
	 * 找到所有的classes
	 * @param path
	 * @return
	 * @throws Exception
	 */
	public List<String> findAllClasses(String packageName)throws Exception{
		String strFile = packageName.replaceAll("\\.", "/");  
        Enumeration<URL> urls = Dispather.class.getClassLoader().getResources(strFile);
        List<String>  classes= new ArrayList<String>();
        while (urls.hasMoreElements()) {  
            URL url = urls.nextElement();  
            if (url != null) {  
                String protocol = url.getProtocol();  
                String pkgPath = url.getPath();  
                logger.info("protocol:" + protocol +" path:" + pkgPath);  
                if ("file".equals(protocol)){  
                    // 本地自己可见的代码
                    classes.addAll(findClassName(pkgPath,packageName));  
                }
            }  
        }
        return classes;
	}
	
	private List<String> findClassName(String pkgPath,String packageName)throws Exception{
		File []files = new File(pkgPath).listFiles();
		List<String> classNames = new ArrayList<String>(files.length);
		for(File file:files){
			String fileName = file.getName();  
            if (file.isFile()) {
            	classNames.add(getClassName(packageName, fileName));
            }
		}
        return classNames;
	}

	/**
	 * 根据文件名和报名，获取class名
	 * @param pkgName
	 * @param fileName
	 * @return
	 */
	private static String getClassName(String pkgName, String fileName) {  
		int endIndex = fileName.lastIndexOf(".");  
		String clazz = null;  
		if (endIndex >= 0) {  
			clazz = fileName.substring(0, endIndex);  
		}  
		String clazzName = null;  
		if (clazz != null) {  
			clazzName = pkgName + "." + clazz;  
		}  
		return clazzName;  
	}  
	
	/**
	 * 处理请求
	 * @param json
	 * @throws Exception
	 */
	public void handle(final JSONObject json)throws Exception{
		pool.execute(new Runnable() {
			public void run() {
				try{
					Action action = actionMaps.get(json.getString("path"));
					action.method.invoke(action.instance,json.get("param"));
				}catch(Exception e){
					
				}
			}
		});
	}
	
	public static void main(String[] args) {
		
	}

}
