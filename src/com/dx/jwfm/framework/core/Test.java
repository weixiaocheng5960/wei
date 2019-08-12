package com.dx.jwfm.framework.core;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import com.dx.jwfm.framework.core.parser.IMacroValueGenerator;
import com.dx.jwfm.framework.core.parser.MacroValueNode;

public class Test {
	static Logger logger = Logger.getLogger(Test.class);
	public static Object getMacroValue(String name) {
		MacroValueNode n = FastFilter.filter.macroValueMap.get(name);
		logger.info("=========================");
		logger.info("MacroValueNode="+n);
		logger.info("filter="+FastFilter.filter);
		logger.info("FastFilter.filter.macroValueMap="+FastFilter.filter.macroValueMap);
		
		HashMap<String, MacroValueNode> map = FastFilter.filter.macroValueMap;
		if(map!=null){
			Set<Entry<String, MacroValueNode>> set = map.entrySet();
			for (Entry<String, MacroValueNode> entry : set) {
				logger.info(entry.getKey()+"=="+entry.getValue());
			}
		}
		if(n!=null){
			IMacroValueGenerator handle = n.getValueHandel();
			logger.info("handle="+handle);
			logger.info("value="+n.getValueHandel().getValue(name));
			return n.getValueHandel().getValue(name);
		}
		return null;
	}
}
