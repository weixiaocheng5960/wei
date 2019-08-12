package com.hhkj.lkjdata.zzgd.action;

import java.io.IOException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import net.sf.json.JSONArray;

import com.dx.jwfm.framework.core.RequestContext;
import com.dx.jwfm.framework.core.dao.DbHelper;
import com.dx.jwfm.framework.core.dao.po.FastPo;
import com.dx.jwfm.framework.core.model.FastModel;
import com.dx.jwfm.framework.util.FastUtil;
import com.dx.jwfm.framework.web.action.FastBaseAction;
import com.dx.jwfm.framework.web.exception.DatagridBuilderNotFound;
import com.dx.jwfm.framework.web.exception.ValidateException;
import com.hhkj.framework.datamaintain.logic.ISQLMapper;
import com.hhkj.framework.exception.AppException;
import com.hhkj.framework.util.DateUtil;
import com.hhkj.framework.util.LogUtil;
import com.hhkj.framework.util.StringUtil;
import com.hhkj.framework.util.node.JsonTreeNode;
import com.hhkj.framework.util.node.TreeNodeProperty;
import com.hhkj.sys.login.model.User;
import com.hhkj.sys.org.dao.po.SysOrg;
import com.hhkj.sys.org.dao.po.SysVOrg;
import com.hhkj.util.DbUtil;
import com.hhkj.util.OrgUtil;
import com.hhkj.util.TreeNode;



/**
 * @author: shixy 
 * @Description:
 * @date:2018-10-16 上午10:41:14
 */
public class ZzgdTPhtzdAction extends FastBaseAction {


	@Override
	public String execute() throws ClassNotFoundException,DatagridBuilderNotFound {
		String DT_ADD=DateUtil.format(new Date(), "yyyy-MM");
//		if(StringUtil.isBlank(search.getString("DT_ADD")))
//			search.setPropt("DT_ADD", DT_ADD);
		String flagLeaf = User.getUser().getSysOrg().getVcFlagLeaf();
		if(flagLeaf == null){
			flagLeaf = "0";
		}
		if(StringUtil.isBlank(search.getString("VC_DEP_ID")))
			search.setPropt("VC_DEP_ID", User.getUserOrgId());
		if(!"0".equals(search.getString("N_FLAG_R$S")))
			search.setPropt("N_STATE_C", "0");
		
		return super.execute();
	}


	protected String openAddPage() {
		String res=super.openAddPage();
		return res;
	}

	protected String addItem() throws ValidateException {
		String msg = validateData();
		if(FastUtil.isBlank(msg)){
			FastPo newp = po.clone();
			newp.initDefaults();//克隆一个新对象，先初始化默认值，再将提交对象的值写入新对象，最后再使用新对象进行保存操作，这样不影响默认值
			for(String key:po.keySet()){
				newp.put(key, po.get(key));
			}
			po = newp;
			po.initIdDelValue();
			String orgid = DbUtil.getFirstStringSqlQuery("SELECT T.VC_FLAG$LEAF FROM SYS_T_ORG T WHERE T.VC_ID='"+User.getUserOrgId()+"'");
			if("1".equals(orgid)){
				po.setPropt("N_FLAG", 1);
			}else{
				po.setPropt("N_FLAG", 0);
			}
			po.setPropt("DT_ADD", new Date());
			po.setPropt("VC_ADD", RequestContext.getBeanValue("curUserName"));
	       
	        items = new ArrayList<FastPo>();
	        new FastPo();
	        FastPo p_f = FastPo.getPo("ZZGD_T_PHTZD_ITEM");//发送
	        p_f.setPropt("VC_MID", po.get("VC_ID"));
	        p_f.setPropt("VC_DEP_ID", User.getUser().getVcOrgId());
	        p_f.setPropt("N_FLAG_R$S", 0);
	        
	        FastPo p_s = FastPo.getPo("ZZGD_T_PHTZD_ITEM");//接收
	        p_s.setPropt("VC_MID", po.get("VC_ID"));
	        p_s.setPropt("VC_DEP_ID", po.getString("VC_PHDW_ID"));
	        p_s.setPropt("N_FLAG_R$S", 1);
	        
	        items.add(p_f);
	        items.add(p_s);
	        
	        String vcEqu=StringUtil.fromArrayToStr(getParameterValues("po.VC_EQU"), false);
			po.setPropt("VC_EQU", vcEqu);
			
			try {
				logic.addPo(po,items);
			} catch (SQLException e) {
				logger.error(e.getMessage(),e);
				throw new RuntimeException("数据保存时出错。原因："+e.getMessage(),e);
			}
			return "success";
		}else{
			throw new ValidateException(msg);
		}
	}


	protected String openModifyPage() {
		return super.openModifyPage();
	}

	protected String modifyItem() throws ValidateException {
		String sql="SELECT VC_ID, VC_MID, VC_DEP_ID, N_FLAG_R$S FROM ZZGD_T_PHTZD_ITEM WHERE VC_MID=? AND N_FLAG_R$S=1";
		String vcEqu=StringUtil.fromArrayToStr(getParameterValues("po.VC_EQU"), false);
		po.setPropt("VC_EQU", vcEqu);
		try {
			items=getDb().executeSqlQuery(sql, new Object[]{po.getString("VC_ID")});
			for(FastPo ipo:items){
				ipo.setTableModelName("ZZGD_T_PHTZD_ITEM");
				if(FastUtil.isNotBlank(po.getString("VC_PHDW_ID")))
					ipo.setPropt("VC_DEP_ID", po.getString("VC_PHDW_ID"));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} 
		return super.modifyItem();
	}

	
	/**
	 * 下发
	 * @author: shixy 
	 * @Description:
	 * @return
	 * @date:2018-10-16 上午10:41:21
	 */
	public String downItem(){
		String[] ids = getParameterValues("chkSelf");
		if(ids==null){
			ids = getParameterValues("chkSelf[]");
		}
		if(ids!=null && ids.length>0){
			String sql = "UPDATE ZZGD_T_PHTZD T SET T.N_STATE=1,VC_NOTICE_USER=?,\r\n" + 
					"DT_NOTICE=? WHERE T.VC_ID IN ('"+FastUtil.join(ids,"','")+"')";
			DbHelper db = new DbHelper();
			try {
				int cnt=db.executeSqlUpdate(sql, new Object[]{User.getUser().getVcName(),new Date()});
				return ajaxResult(true, "共下发成功 "+cnt+"条记录 ！");
			} catch (SQLException e) {
				logger.error(e.getMessage(),e);
				return ajaxResult(false, "下发时发生错误："+e.getMessage()+"\n"+e.getClass().getName());
			}
		}
		return ajaxResult(false, "您没有指定任何要下发的数据！");
	}
	
	
	
	/**
	 * 签认
	 * @author: shixy 
	 * @Description:
	 * @return
	 * @date:2018-10-16 上午10:41:37
	 */
	public String submitItem(){
		String[] ids = getParameterValues("chkSelf");
		String dtDo=getParameter("dtDo");
		if(ids==null){
			ids = getParameterValues("chkSelf[]");
		}
		Date date=new Date();
		if(dtDo!=null){
			Date d=FastUtil.parseDate(dtDo);
			if(d!=null) date=d;
		}
		if(ids!=null && ids.length>0){
			String sql = "UPDATE ZZGD_T_PHTZD T SET T.N_STATE=2,T.VC_PHDW_USER=?,T.DT_PHDW_SIGN=SYSDATE,DT_DO_START=? " +
					"WHERE T.VC_ID IN ('"+FastUtil.join(ids,"','")+"')";
			DbHelper db = new DbHelper();
			try {
				int cnt = db.executeSqlUpdate(sql,new Object[]{User.getUserName(),date});
				return ajaxResult(true, "共签认成功 "+cnt+"条记录 ！");
			} catch (SQLException e) {
				logger.error(e.getMessage(),e);
				return ajaxResult(false, "签认时发生错误："+e.getMessage()+"\n"+e.getClass().getName());
			}
		}
		return ajaxResult(false, "您没有指定任何要签认的数据！");
	}
	
	
	/**
	 * 销号
	 * @author: shixy 
	 * @Description:
	 * @return
	 * @date:2018-10-30 上午9:13:13
	 */
	public String cancel(){
		String[] ids = getParameterValues("chkSelf");
		if(ids==null){
			ids = getParameterValues("chkSelf[]");
		}
		if(ids!=null && ids.length>0){
			String sql = "UPDATE ZZGD_T_PHTZD T SET T.N_STATE=5 WHERE T.VC_ID IN ('"+FastUtil.join(ids,"','")+"')";
			DbHelper db = new DbHelper();
			try {
				int cnt = db.executeSqlUpdate(sql);
				return ajaxResult(true, "共销号成功 "+cnt+"条记录 ！");
			} catch (SQLException e) {
				logger.error(e.getMessage(),e);
				return ajaxResult(false, "销号时发生错误："+e.getMessage()+"\n"+e.getClass().getName());
			}
		}
		return ajaxResult(false, "您没有指定任何要销号的数据！");
	}
	
	/**
	 * 关闭
	 * @author: shixy 
	 * @Description:
	 * @return
	 * @date:2018-10-16 下午2:36:35
	 */
	public String closeItem(){
		String[] ids = getParameterValues("chkSelf");
		if(ids==null){
			ids = getParameterValues("chkSelf[]");
		}
		if(ids!=null && ids.length>0){
			String sql = "UPDATE ZZGD_T_PHTZD T SET T.N_STATE=4 WHERE T.VC_ID IN ('"+FastUtil.join(ids,"','")+"')";
			DbHelper db = new DbHelper();
			try {
				int cnt = db.executeSqlUpdate(sql);
				return ajaxResult(true, "共关不成功 "+cnt+"条记录 ！");
			} catch (SQLException e) {
				logger.error(e.getMessage(),e);
				return ajaxResult(false, "关闭时发生错误："+e.getMessage()+"\n"+e.getClass().getName());
			}
		}
		return ajaxResult(false, "您没有指定任何要关闭的数据！");
	}
	
	/**
	 * 打开申请关闭页面
	 * @author: shixy 
	 * @Description:
	 * @return
	 * @date:2018-10-16 下午2:57:57
	 */
	public String applyItem(){
		FastModel fmodel = RequestContext.getFastModel();
		if(fmodel==null){
			throw new RuntimeException("can't find FastModel in this URL");
		}
		if("save".equals(op)){
			po.setPropt("VC_CLOSE_USER", po.get("VC_CLOSE_USER"));
			po.setPropt("DT_CLOSE", po.get("DT_CLOSE"));
			po.setPropt("VC_CLOSE_REASON", po.get("VC_CLOSE_REASON"));
			po.setPropt("N_STATE", po.get("N_STATE"));
			po.setPropt("VC_ID", po.get("VC_ID"));
			return modifyItemAjax();
		}else{
			super.openModifyPage();
			return "/zzgd/zzgdTPhtzdApplyItem.jsp";
		}
	}
	
	/**
	 * 根据车站获取道岔
	 * @author: shixy 
	 * @Description:
	 * @return
	 * @date:2018-10-30 下午1:52:23
	 */
	public String getDaoChaBystation(){
		JSONArray json = new JSONArray();
		String station = getParameter("station");
		String sql = "SELECT DISTINCT T.VC_NAME key,T.VC_NAME value FROM GD_T_ARCHIVES T WHERE T.N_DEL=0";
		if(StringUtil.isNotBlank(station)){
			sql += " AND T.VC_STATION_ID='"+station+"'";	
		}
		List<JsonTreeNode> cjList = DbUtil.executeSqlQuery(sql, JsonTreeNode.mapper);
		json.add(cjList);		
		return super.writeHTML(json.toString().substring(1,json.toString().length()-1));
	}
	
	/**
	 * 获取设备管理单位负责人
	 * @author: shixy 
	 * @Description:
	 * @param orgId
	 * @date:2018-10-18 上午9:40:55
	 */
	public void getGqPerson(){
//		String orgId = User.getUser().getVcOrgId();
		String orgId = getParameter("orgId");
		if(FastUtil.isBlank(orgId))
			orgId=User.getUserOrgId();
		List<TreeNode> personList=getPersonNoPublic(orgId);
		 //将树以JSON格式的字符串进行输出
		JSONArray json=new JSONArray(); 
		json.addAll(personList);
		writeHTML(json.toString());
		
	}
	
	/**
	 * 过滤掉机构中的公用账号，因为发现公用账号的名字并不规则，所以使用角色名字来进行判断。
	 * @param vcOrgId //需要过滤的机构id
	 * @return //返回用户id和用户name的list
	 */
	private List<TreeNode> getPersonNoPublic(String vcOrgId){
		String sql=
				"select vc_name,vc_name from sys_t_person\n" +
						"where VC_ORG$ID = '"+vcOrgId+"'\n" + 
						"and vc_del$flag=0\n" + 
						"and vc_id not in(select p.vc_id\n" + 
						"  from sys_t_person p,sys_t_account$role a,sys_t_role r\n" + 
						" where p.vc_id=a.vc_user$id\n" + 
						"       and r.vc_id=a.vc_role$id\n" + 
						"       and r.vc_del$flag=0\n" + 
						"       and p.vc_del$flag = 0\n" + 
						"       and r.vc_name like '%公用账号%'\n ) and vc_name not like '%公共%'" + 
						"       order by N_SEQ";
		List<TreeNode> list=(List<TreeNode>) DbUtil.executeSqlQuery(sql,  TreeNode.mapper2);
		return list;
	}
	
	/**
	 * 
	 * @author: shixy 
	 * @Description:
	 * @throws AppException
	 * @date:2018-10-18 上午10:34:35
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void searchOrgJsonTree()throws  AppException{
		SysVOrg org = User.getUser().getSysOrg();
    	JSONArray jsonTree = new JSONArray();
		
    	StringBuffer sql  = new StringBuffer("select DISTINCT t.VC_ORG$ID,t.VC_ORG$NAME, t.VC_ORG$TYPE,t.VC_ORG$FLAG$LEAF, t.VC_ORG$TYPE$FLAG,t.VC_PAR$ORGID \r\n" + 
    			"from PUB_V_ORG$STATION t WHERE vc_id IN(select t.vc_id from PUB_V_ORG$STATION t WHERE VC_ORG$ID=?) \r\n" + 
    			"AND vc_org$type IN ('6') and t.VC_ORG$FLAG$LEAF<>? ORDER BY VC_ORG$TYPE");
    	
		//将通过SQL语句查询的结果集组合成SysVOrg对象的数组
    	List<?> orgList = DbUtil.executeSqlQuery(sql.toString(),new ISQLMapper(){
			public SysVOrg fromSQLQuery(ResultSet rs, int row) {
				SysVOrg view = new SysVOrg();
				try {
					view.setVcId(rs.getString("VC_ORG$ID"));
					view.setVcName(rs.getString("VC_ORG$NAME"));
					view.setVcParOrgid(rs.getString("VC_PAR$ORGID"));
					view.setVcType(rs.getString("VC_ORG$TYPE"));
				} catch (SQLException e) {
					e.printStackTrace();
				}
				return view;
			}
		},new String[]{org.getVcId(),org.getVcFlagLeaf()});

    	if(!orgList.isEmpty()){
    		List<JsonTreeNode> childList =this.getChildNodeListForOrg(null,orgList);  //获取根节点下的全部节点信息
    		jsonTree.addAll(childList);  //写入树节点信息
    	}
    	
    	//以字符串为文件流进行输出
		writeHTML(jsonTree.toString());  //将树以JSON格式的字符串进行输出
    }
    	
  
    /**
     * @author: shixy 
     * 根据父节点的id获取子节点的机构树List，其中List中的对象为JsonTreeNode
     * @Description:
     * @param pid
     * @param orgList
     * @return
     * @date:2018-10-18 上午10:34:04
     */
    protected List<JsonTreeNode> getChildNodeListForOrg(String pid,List<?> orgList){
    	List<JsonTreeNode> rootList = new ArrayList<JsonTreeNode>();
    	//循环遍历所有机构
    	for(int i=0;i<orgList.size();i++){
    		SysVOrg org = (SysVOrg)orgList.get(i);
    		//判断机构信息，当机构的上级ID和参数pid相同时，才将该机构纳入到子节点中
    		if((pid==null&&"6".equals(org.getVcType()))||(pid!=null&&pid.equals(org.getVcParOrgid()))){
        		JsonTreeNode child = new JsonTreeNode();
            	child.setId(org.getVcId());
            	child.setText(org.getVcName());
            	TreeNodeProperty attributes = new TreeNodeProperty();
            	attributes.setOrgType(org.getVcManProType());
            	child.setAttributes(attributes);
            	List<JsonTreeNode> childList = this.getChildNodeListForOrg(org.getVcId(),orgList);  //递归获取的该节点的下级节点
            	child.setChildren(childList);
            	if(childList!=null&&childList.size()>0)  //当有子节点时默认树的状态为不展开
            	    child.setState("closed");
            	rootList.add(child); //将节点放入List数组中
    		}
    	}
    	return rootList;
    }
    
    
    /**
     * 打印配合通知单
     * @author: shixy 
     * @Description:
     * @return
     * @throws AppException
     * @date:2018-10-22 上午11:35:59
     */
    public String openPrintPage() throws AppException{
    	String id = getParameter("printId");
    	try{
    		po = getDb().loadFastPo(po, id);
		}catch(Exception e){
		    LogUtil.logError("打开打印配合通知单页面失败", e);
			throw new AppException("打开打印配合通知单页面失败，可能原因如下："+e.getMessage());
		}
    	if(po==null){
			return writeHTML("can't find record by id:["+id+"]");
		}
    	return "/zzgd/zzgdTPhtzdPrint.jsp";
    }
    
    
    /**
	 * 机构树的类型：dep:行政部门树  org：生产机构树 默认为org
	 */
    private String treeType = "";
    
    public String getTreeType() {
		return treeType;
	}

	public void setTreeType(String treeType) {
		this.treeType = treeType;
	}
  
}
