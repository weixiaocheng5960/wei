package com.hhkj.main.action;

import java.sql.SQLException;
import java.util.List;

import javax.servlet.http.Cookie;

import com.dx.jwfm.framework.core.dao.po.FastPo;
import com.dx.jwfm.framework.web.action.FastBaseAction;
import com.dx.jwfm.framework.web.filter.CheckLoginFilter;
import com.hhkj.framework.constants.WebConstants;
import com.hhkj.framework.exception.AppException;
import com.hhkj.framework.util.LogUtil;
import com.hhkj.framework.util.MD5;
import com.hhkj.framework.util.StringUtil;
import com.hhkj.framework.util.Uuid;
import com.hhkj.sys.login.model.User;
import com.hhkj.sys.person.dao.po.SysPerson;
import com.hhkj.util.DbUtil;
import com.hhkj.util.OrgUtil;
import com.hhkj.util.RegeditUtil;
import com.hhkj.util.SessionUtil;

public class IndexAction extends FastBaseAction {
	
	public String execute(){
		if("logout".equals(op)){
			return logout();
		}
		User user = User.getUser();
		if(user==null){
			return "/login.jsp";
		}
		else{
			return "/main.jsp";
		}
	}
	
	private String logout() {
		CheckLoginFilter.logOutAndClearSessionInfo();
		return "/login.jsp";
	}

	public String login(){
		String username = getParameter("username");
		String password = getParameter("password");
		if(username==null || password==null){
	        return ajaxResult(false, "userError");
		}
		User user = null;
		//先验证超级管理员
		if (User.checkSuperUser(username.trim(), username.trim(), password.trim())) {
		    // 超管用户验证成功
		    try {
				user = User.setSuperUserInfo(OrgUtil.getRootOrgId());
			} catch (AppException e) {
				LogUtil.logError(e);
			}
		}
		if(user==null){//再验证是否有系统用户
			List<?> userList = null;
			try {
				userList = DbUtil.executeHql("from SysPerson po where po.vcLogName='"+StringUtil.filterSqlContent(username.trim())+
						"' and po.vcStatus >0 and po.vcDelFlag = 0");
			} catch (Exception e) {
				LogUtil.logError(e);
			}
			if (userList == null || userList.isEmpty()) {
			    return ajaxResult(false, "用户名不存在");
			}
			if (userList.size()>1) {
				return ajaxResult(false, "存在重名用户 请联系管理员");
			}
		    // 系统登陆用户验证成功
		    SysPerson person = (SysPerson) userList.get(0);
	    	if(StringUtil.isNotBlank(person.getVcPwd())){
	    		if(person.getVcPwd().length()==32){//如果密码是加密后的MD5码，则进行转码后匹配
	    			if(person.getVcPwd().equals(MD5.toMd5String(password))){
	    				user = new User(person);
	    			}
	    		}
	    		else{//否则使用明文匹配
	    			//将旧系统中使用明文保存的密码加密
	    			try {
						List<FastPo> list = getDb().executeSqlQuery("select vc_id,vc_pwd from sys_t_person where vc_pwd is not null and length(vc_pwd)<32");
						String sql = "update sys_t_person set vc_pwd=? where vc_id=?";
						for(FastPo node:list){
							getDb().executeSqlUpdate(sql, new Object[]{MD5.toMd5String(node.getString("VC_PWD")), node.getVcId()});
						}
					} catch (SQLException e) {
						LogUtil.logError(e);
					}
	    			if(person.getVcPwd().equals(password)){
	    				user = new User(person);
	    			}
	    		}
	    	}
		}
    	SessionUtil.setAttribute(User.USER_SESSION_ID, user);
		if(user!=null){
			//将每页显示行数的信息写入SESSION�?
			getRequest().getSession().setAttribute(WebConstants.SEARCH_PAGE_SIZE_FLDNAME, user.getRegeditValue(WebConstants.ROWS_PER_PAGE));
		    //生成SSO单点登录ID，写入数据库，同时写入COOKIE
		    String ssoid = Uuid.getUuid();
		    String sql = "insert into SYS_T_SESSION$INF(SESSION_ID,SESSION_VALUE,add_date) values(?,?,sysdate)";
			try {
				getDb().executeSqlUpdate(sql ,new Object[]{ssoid,user.getVcId()});
			} catch (SQLException e) {
				LogUtil.logError(e);
			}
			Cookie ck = new Cookie("SSO_ID",ssoid);
			ck.setPath("/");
			getResponse().addCookie(ck);
		    SessionUtil.setAttribute("SSO_ID",ssoid);
	    	String toUrl = (String) getRequest().getSession().getAttribute("loginToPage");
	    	getRequest().getSession().removeAttribute("loginToPage");
	    	if(toUrl!=null&&toUrl.indexOf("goto=true")<0){
	    		toUrl = null;
	    	}
	    	if(StringUtil.isBlank(toUrl)){
	    		toUrl = RegeditUtil.getRegeditStringValue("SYSTEM_INDEX_PATH");
	    	}
		    return ajaxResult(true, toUrl);
		}
		else{
		    return ajaxResult(false, "密码不正�?");
		}
	}

}
