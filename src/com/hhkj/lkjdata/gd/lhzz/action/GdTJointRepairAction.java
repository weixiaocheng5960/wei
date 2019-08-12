package com.hhkj.lkjdata.gd.lhzz.action;

import java.io.File;
import java.io.FileInputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;


import net.sf.json.JSONArray;

import com.dx.jwfm.framework.core.RequestContext;
import com.dx.jwfm.framework.core.dao.po.FastPo;
import com.dx.jwfm.framework.core.model.FastModel;
import com.dx.jwfm.framework.util.FastUtil;
import com.dx.jwfm.framework.util.POIUtil;
import com.dx.jwfm.framework.web.action.FastBaseAction;
import com.dx.jwfm.framework.web.exception.DatagridBuilderNotFound;
import com.dx.jwfm.framework.web.exception.ValidateException;
import com.hhkj.framework.context.FrameworkContext;
import com.hhkj.framework.util.DateUtil;
import com.hhkj.framework.util.LogUtil;
import com.hhkj.framework.util.StringUtil;
import com.hhkj.sys.login.model.User;
import com.hhkj.sys.org.dao.po.SysVOrg;
import com.hhkj.util.OrgUtil;

public class GdTJointRepairAction extends FastBaseAction {

	@Override
	public String execute() throws ClassNotFoundException,DatagridBuilderNotFound {
		String type=getParameter("type");
		if(type!=null&&type.equals("all")){
			return super.execute();
		}
		if(StringUtil.isBlank(search.getString("VC_ORG")))
			search.setPropt("VC_ORG", User.getUser().getOrgManProId());
		String DT_JH=DateUtil.format(new Date(), "yyyy-MM");
		if(StringUtil.isBlank(search.getString("DT_JH")))
			search.setPropt("DT_JH", DT_JH);
		return super.execute();
	}


	@Override
	protected String buildResultJson(List<FastPo> list)
			throws ClassNotFoundException, DatagridBuilderNotFound {
		for (FastPo fastPo : list) {
			poView(fastPo);
		}
		return super.buildResultJson(list);
	}

	private void poView(FastPo fastPo){
		String dw=fastPo.getString("VC_DWD_NAME")+"/"+
				fastPo.getString("VC_DWCJ_NAME");
		
		String gw=fastPo.getString("VC_GWD_NAME")+"/"+
				fastPo.getString("VC_GWCJ_NAME");
		
		fastPo.setPropt("dw", dw);
		fastPo.setPropt("gw", gw);
	}
	

	@Override
	public String expExcel(){
		FastModel fmodel = RequestContext.getFastModel();
		pager.setPage(1);
		pager.setRows(99999999);
		List<FastPo> list = logic.searchData(fmodel.getModelStructure().getSearch(),fmodel.getModelStructure().getMainTable(),search,pager);
		for(FastPo fastPo : list) {
			poView(fastPo);
		}

		HSSFWorkbook wb = getWorkbook(list,fmodel.getModelStructure());
		try {
			getResponse().setCharacterEncoding("UTF-8");
			getResponse().setContentType("application/zip"); // MIME type for pdf doc
			getResponse().setHeader("Content-disposition", "attachment; filename=\""
					+ new String((fmodel.getVcName()+".xls").getBytes("GBK"),"ISO8859-1") + "\"");
			wb.write(getResponse().getOutputStream());
		} catch (Exception e) {
			logger.error(e.getMessage(),e);
			return writeJavaScript("alert('导出文件时发生错误！');");
		}
		return null;
	}



	protected String openAddPage() {
		User curUser=User.getUser();
		String curOrgId=curUser.getVcOrgId();
		//计划月份
		String jh=getParameter("jh");
		setAttribute("jh", jh);
		if(curUser.isCjUser()||curUser.isGqUser()){
			//查询管辖车站
			String sql="SELECT VC_ID            STATION_ID,\r\n" + 
					"       VC_NAME          STATION_NAME,\r\n" + 
					"       VC_ORG$ID,\r\n" + 
					"       VC_PAR$ORGID,\r\n" + 
					"       VC_ORG$NAME,\r\n" + 
					"       VC_ORG$FLAG$LEAF,\r\n" + 
					"       VC_LINE$ID,\r\n" + 
					"       VC_LINE$NAME " +
					"FROM pub_v_org$station v WHERE vc_org$id=? ";
			List<FastPo> czList = null;
			try {
				czList = getDb().executeSqlQuery(sql, new String []{curOrgId});
			} catch (SQLException e) {
				logger.error(e.getMessage(),e);
			}
			//查询站内设备
			sql="SELECT T.VC_ID, T.VC_NAME\r\n" + 
					"  FROM GD_T_ARCHIVES T\r\n" + 
					" WHERE T.N_DEL = 0 and T.VC_STATION_ID = ?";
			for (FastPo fastPo : czList) {
				Object stationId=fastPo.get("STATION_ID");
				try {
					List<FastPo> arList = getDb().executeSqlQuery(sql, new Object []{stationId});
					fastPo.put("arList", arList);
				} catch (SQLException e) {
					logger.error(e.getMessage(),e);
				}
			}
			setAttribute("czList", czList);
			
			return super.openAddPage();
		}
		return writeHTML("请使用工区或车间用户添加计划");
	}

	protected String addItem() throws ValidateException {
//		User curUser=User.getUser();
//		SysVOrg curOrg = curUser.getSysOrg();
//		int proFlag=curUser.getOrgManProFlagInt();
		String msg = validateData();
		List<String> arids = searchArids();
		List<FastPo> addItems=new ArrayList<FastPo>();
		if(FastUtil.isBlank(msg)){
			//获取工区的Id
			String sql="SELECT *  FROM PUB_V_ORG$STATION V WHERE VC_ID = ? AND VC_ORG$FLAG$LEAF IS NOT NULL\r\n" + 
					" ORDER BY VC_ORG$FLAG$LEAF, VC_ORG$TYPE";
			
			if(items!=null){//给items赋上初始的MID
				int i=0;
				for(FastPo ipo:items){
					String[] turnoutId = getParameterValues("items["+i+"].turnoutId");
					i++;
					if(turnoutId==null) continue;
					
					String tableName=ipo.getString("tableModelName");
					if(ipo.getTblModel()==null && FastUtil.isNotBlank(tableName)){
						ipo.setTableModelName(tableName);
					}
					ipo.setPropt("DT_ADD", new Date());
					ipo.setPropt("VC_ADD", User.getUserName());
					ipo.setPropt("DT_MODIFY", new Date());
					ipo.setPropt("VC_MODIFY", User.getUserName());
					ipo.setPropt("N_STATE", 0);
					
					List<FastPo> czList = null;
					try {
						czList = getDb().executeSqlQuery(sql, new String []{ipo.getString("VC_STATION_ID")});
						setOrg(czList, ipo);
						
					} catch (SQLException e) {
						e.printStackTrace();
						logger.error(e.getMessage(),e);
					}
					
					
					
					for (String id : turnoutId) {
						if(arids.contains(id)){
							continue;
						}
						FastPo vr = getFastpoById("GD_T_ARCHIVES",id);
						FastPo addItem=FastPo.getPo(ipo.getTblModel().getName());
						FastUtil.copyBeanPropts(addItem, ipo);
						addItem.setPropt("VC_TURNOUT_ID", id);
						addItem.setPropt("VC_TURNOUT_NAME", vr.get("VC_NAME"));
						addItems.add(addItem);
					}
					
				}
			}
			try {
				logic.addPo(null,addItems);
			} catch (SQLException e) {
				logger.error(e.getMessage(),e);
				throw new RuntimeException("数据保存时出错。原因："+e.getMessage(),e);
			}
			return "addSuccess";
		}else{
			throw new ValidateException(msg);
		}
	}

	/**
	 * 检测是否有道岔标准
	 * @return
	 */
	public String checkStandard(){
		FastModel fmodel = RequestContext.getFastModel();
		if(fmodel==null){
			throw new RuntimeException("can't find FastModel in this URL");
		}

		super.openModifyPage();
		int res=0;
		try {
			res = getStandard();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		if(res==1) 
			return writeResult("false", "该道岔尚未设置道岔种类或设置错误，请到道岔台账中设置道岔种类。");
		
		return writeResult("true", "");
	
	}
	
	@Override
	public String look() {
		String res=super.look();
		try {
			getStandard();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return res;
	}

	/**
	 * 录入调查数据
	 * @return
	 */
	public String investigate(){
		FastModel fmodel = RequestContext.getFastModel();
		if(fmodel==null){
			throw new RuntimeException("can't find FastModel in this URL");
		}
		User curUser=User.getUser();
		SysVOrg curOrg = curUser.getSysOrg();
		String flagLeaf=curOrg.getVcFlagLeaf();
		if("save".equals(op)){
			if(StringUtil.isNotBlank(po.getString("loginName"))){
				if("0".equals(flagLeaf)){
					po.setPropt("VC_DW_INVESTIGATION", po.getString("loginName"));
				}else if("1".equals(flagLeaf)){
					po.setPropt("VC_GW_INVESTIGATION", po.getString("loginName"));
				}
			}
			return modifyItemAjax();
		}
		else{
			super.openModifyPage();
			if("0".equals(flagLeaf)){
				po.setPropt("DT_DW_INVESTIGATION", new Date());
				po.setPropt("VC_DW_INVESTIGATION", User.getUserName());
			}else if("1".equals(flagLeaf)){
				po.setPropt("DT_GW_INVESTIGATION", new Date());
				po.setPropt("VC_GW_INVESTIGATION", User.getUserName());
			}
			po.setPropt("loginName", User.getUserName());
			int res=0;
			try {
				res = getStandard();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			if(res==1) return writeResult("false", "该道岔尚未设置道岔种类或设置错误，请到道岔台账中设置道岔种类。");
			setAttribute("orgFlag", flagLeaf);
			
			return "openInvestigate";
		}
	}
	
	
	
	
	
	/**
	 * 录入整治数据
	 * @return
	 */
	public String repair(){
		FastModel fmodel = RequestContext.getFastModel();
		if(fmodel==null){
			throw new RuntimeException("can't find FastModel in this URL");
		}
		User curUser=User.getUser();
		SysVOrg curOrg = curUser.getSysOrg();
		String flagLeaf=curOrg.getVcFlagLeaf();
		if("save".equals(op)){
			po.setPropt("DT_SJ", new Date());
			if(StringUtil.isNotBlank(po.getString("loginName"))){
				if("0".equals(flagLeaf)){
					po.setPropt("VC_DW_REPAIR", po.getString("loginName"));
				}else if("1".equals(flagLeaf)){
					po.setPropt("VC_GW_REPAIR", po.getString("loginName"));
				}
			}
			return modifyItemAjax();
		}
		else{
			super.openModifyPage();
			if("0".equals(flagLeaf)){
				po.setPropt("DT_DW_REPAIR", new Date());
				po.setPropt("VC_DW_REPAIR", User.getUserName());
				
			}else if("1".equals(flagLeaf)){
				po.setPropt("DT_GW_REPAIR", new Date());
				po.setPropt("VC_GW_REPAIR", User.getUserName());
			}
			po.setPropt("loginName", User.getUserName());
			List<FastPo> list = (List<FastPo>) getAttribute("pro");
			if(list.isEmpty()){
				return writeHTML("未匹配到相应的调查数据");
			}
			
			for (FastPo fastPo : list) {
				if(FastUtil.isBlank(fastPo.getString("VC_REPAIR_DATA"))) 
					fastPo.setPropt("VC_REPAIR_DATA", fastPo.getString("VC_INVESTIGATION_DATA"));
			}
			setAttribute("orgFlag", curOrg.getVcFlagLeaf());
			
			return "openInvestigate";
		}
	}
	/**
	 * 录入验收数据
	 * @return
	 */
	public String check(){
		FastModel fmodel = RequestContext.getFastModel();
		if(fmodel==null){
			throw new RuntimeException("can't find FastModel in this URL");
		}
		User curUser=User.getUser();
		SysVOrg curOrg = curUser.getSysOrg();
		String flagLeaf=curOrg.getVcFlagLeaf();
		if("save".equals(op)){
			if(StringUtil.isNotBlank(po.getString("loginName"))){
				if("0".equals(flagLeaf)){
					po.setPropt("VC_DW_CHECK", po.getString("loginName"));
				}else if("1".equals(flagLeaf)){
					po.setPropt("VC_GW_CHECK", po.getString("loginName"));
				}
			}
			return modifyItemAjax();
		}
		else{
			super.openModifyPage();
			if("0".equals(flagLeaf)){
				po.setPropt("DT_DW_CHECK", new Date());
				po.setPropt("VC_DW_CHECK", User.getUserName());
			}else if("1".equals(flagLeaf)){
				po.setPropt("DT_GW_CHECK", new Date());
				po.setPropt("VC_GW_CHECK", User.getUserName());
			}
			po.setPropt("loginName", User.getUserName());
			List<FastPo> list = (List<FastPo>) getAttribute("pro");
			if(list.isEmpty()){
				return writeHTML("未匹配到相应的整治数据");
			}
			for (FastPo fastPo : list) {
				if(FastUtil.isBlank(fastPo.getString("VC_CHECK_DATA"))) 
					fastPo.setPropt("VC_CHECK_DATA", fastPo.getString("VC_REPAIR_DATA"));
			}
			setAttribute("orgFlag", curOrg.getVcFlagLeaf());
			
			return "openInvestigate";
		}
	}

	/**
	 * 获取道岔整治标准
	 * @throws SQLException 
	 */
	private int getStandard() throws SQLException {
		List<FastPo> list = (List<FastPo>) getAttribute("pro");
		if(!list.isEmpty()){
			return 0;
		}
		
		//道岔种类
		String vcTurnoutId =po.getString("VC_TURNOUT_ID");
		FastPo arPo=FastPo.getPo("gd_t_archives");
		arPo = getDb().loadFastPo(arPo, vcTurnoutId);
		String sql = "select vc_category, vc_standard, vc_item, vc_note, n_flag, n_seq" +
				" from gd_t_turnout_standard where vc_type=${VC_TYPE} and n_del=0 order by n_flag,N_SEQ,VC_CATEGORY,vc_item";
		list= getDb().executeSqlQuery(sql, arPo);
		
		if(list.isEmpty()){
			return 1;
		}
		
		setAttribute("pro", list);
		return 0;
	}

	@Override
	protected String modifyItem() throws ValidateException {
		User curUser=User.getUser();
		SysVOrg curOrg = curUser.getSysOrg();
		String flagLeaf=curOrg.getVcFlagLeaf();
		
		String keyCode = po.getTblModel().keyColCode();
		FastPo p=null;
		if(keyCode!=null){
			try {
				p = getDb().loadFastPo(po, po.getString(keyCode ));
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			if(p!=null && p.getString(keyCode)!=null){
				for(String key:po.keySet()){
					p.put(key, po.get(key));
				}
			}
		}
		
		String dwInvestige = p.getString("VC_DW_INVESTIGATION");
		String dwRepair = p.getString("VC_DW_REPAIR");
		String dwCheck = p.getString("VC_DW_CHECK");
		
		String gwInvestige = p.getString("VC_GW_INVESTIGATION");
		String gwRepair = p.getString("VC_GW_REPAIR");
		String gwCheck = p.getString("VC_GW_CHECK");
		
		//更改状态
		if (StringUtil.isNotBlank(dwInvestige)&&StringUtil.isNotBlank(gwInvestige)) {
			po.put("N_STATE", 1);
		}	
		if (StringUtil.isNotBlank(dwRepair)||StringUtil.isNotBlank(gwRepair)) {
			po.put("N_STATE", 2);
		}	
		if (StringUtil.isNotBlank(dwCheck)&&StringUtil.isNotBlank(gwCheck)) {
			po.put("N_STATE", 3);
		}	
		//更新部门
		int proFlag=curUser.getOrgManProFlagInt();
		if(proFlag==7){
			SysVOrg cj = OrgUtil.getOrg(curOrg.getVcParOrgid());
			SysVOrg duan = OrgUtil.getOrg(cj.getVcParOrgid());
			if("0".equals(flagLeaf))
				setDwOrg(po,curOrg.getVcId(), curOrg.getVcName(), cj.getVcId(), cj.getVcName(), duan.getVcId(), duan.getVcName());
			if("1".equals(flagLeaf))
				setGwOrg(po,curOrg.getVcId(), curOrg.getVcName(), cj.getVcId(), cj.getVcName(), duan.getVcId(), duan.getVcName());
			
		}else if(proFlag==6){
			SysVOrg duan = OrgUtil.getOrg(curOrg.getVcParOrgid());
			if("0".equals(flagLeaf))
				setDwOrg(po,null,null,curOrg.getVcId(), curOrg.getVcName(), duan.getVcId(), duan.getVcName());
			if("1".equals(flagLeaf))
				setGwOrg(po,null,null,curOrg.getVcId(), curOrg.getVcName(), duan.getVcId(), duan.getVcName());
		}else if(proFlag==5){
			/*if("0".equals(flagLeaf))
				setDwOrg(po,null,null,null,null,curOrg.getVcId(), curOrg.getVcName());
			if("1".equals(flagLeaf))
				setGwOrg(po,null,null,null,null,curOrg.getVcId(), curOrg.getVcName());*/
		}
		
		String res =super.modifyItem();
		System.out.println(po.get("DT_DW_INVESTIGATION"));
		return res;
	
	}
	

	
	/**
	 * 设置工务段和电务段的三级单位
	 * @param czList 工区的机构
	 * @param ipo
	 */
	private void setOrg(List<FastPo> czList,FastPo ipo){
		SysVOrg dwDduan=new SysVOrg();
		SysVOrg dwCj=new SysVOrg();
		SysVOrg dwGq=new SysVOrg();
		
		SysVOrg gwDduan=new SysVOrg();
		SysVOrg gwCj=new SysVOrg();
		SysVOrg gwGq=new SysVOrg();
		if(!czList.isEmpty()){
			for (FastPo czView : czList) {
				Integer orgType = czView.getInteger("VC_ORG$TYPE");
				if("0".equals(czView.getString("VC_ORG$FLAG$LEAF"))){
					if(orgType==5){
						dwDduan.setVcId(czView.getString("VC_ORG$ID"));
						dwDduan.setVcName(czView.getString("VC_ORG$NAME"));
					}else if(orgType==6){
						dwCj.setVcId(czView.getString("VC_ORG$ID"));
						dwCj.setVcName(czView.getString("VC_ORG$NAME"));
						
					}else if(orgType==7){
						dwGq.setVcId(czView.getString("VC_ORG$ID"));
						dwGq.setVcName(czView.getString("VC_ORG$NAME"));
					}
					
				}else if("1".equals(czView.getString("VC_ORG$FLAG$LEAF"))){
					if(orgType==5){
						gwDduan.setVcId(czView.getString("VC_ORG$ID"));
						gwDduan.setVcName(czView.getString("VC_ORG$NAME"));
					}else if(orgType==6){
						gwCj.setVcId(czView.getString("VC_ORG$ID"));
						gwCj.setVcName(czView.getString("VC_ORG$NAME"));
						
					}else if(orgType==7){
						gwGq.setVcId(czView.getString("VC_ORG$ID"));
						gwGq.setVcName(czView.getString("VC_ORG$NAME"));
					}
					
				}
			}
			setDwOrg(ipo,dwGq.getVcId(), dwGq.getVcName(), dwCj.getVcId(), dwCj.getVcName(), 
					dwDduan.getVcId(), dwDduan.getVcName());
			setGwOrg(ipo,gwGq.getVcId(), gwGq.getVcName(), gwCj.getVcId(), gwCj.getVcName(), 
					gwDduan.getVcId(), gwDduan.getVcName());
		}
		
		//工区没有填写
		if(ipo.get("VC_DWGQ_ID")==null){
			try {
				String sql="SELECT * FROM pub_v_org$station v WHERE vc_id=? AND vc_org$type=6 and vc_org$flag$leaf='0'";
				czList = getDb().executeSqlQuery(sql, new String []{ipo.getString("VC_STATION_ID")});
				if(!czList.isEmpty()){
					FastPo czView=czList.get(0);
					SysVOrg duan = OrgUtil.getOrg(czView.getString("VC_PAR$ORGID"));
					setDwOrg(ipo,czView.getString("VC_ORG$ID"), null, czView.getString("VC_ORG$ID"), czView.getString("VC_ORG$NAME"),
							duan.getVcId(), duan.getVcName());
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		if(ipo.get("VC_GWGQ_ID")==null){
			try {
				String sql="SELECT * FROM pub_v_org$station v WHERE vc_id=? AND vc_org$type=6 and vc_org$flag$leaf='1'";
				czList = getDb().executeSqlQuery(sql, new String []{ipo.getString("VC_STATION_ID")});
				if(!czList.isEmpty()){
					FastPo czView=czList.get(0);
					SysVOrg duan = OrgUtil.getOrg(czView.getString("VC_PAR$ORGID"));
					setGwOrg(ipo,czView.getString("VC_ORG$ID"), null, czView.getString("VC_ORG$ID"), czView.getString("VC_ORG$NAME"),
							duan.getVcId(), duan.getVcName());
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * 设置电务部门
	 * @param jointPo
	 * @param gqId 工区
	 * @param gqName
	 * @param cjId 车间
	 * @param cjName
	 * @param did 段级单位
	 * @param dname
	 */
	private void setDwOrg(FastPo jointPo,String gqId,String gqName,String cjId,String cjName,String did,String dname){
		if (gqId != null)
			jointPo.setPropt("VC_DWGQ_ID", gqId);
		if (gqName != null)
			jointPo.setPropt("VC_DWGQ_NAME", gqName);
		if (cjId != null)
			jointPo.setPropt("VC_DWCJ_ID", cjId);
		if (cjName != null)
			jointPo.setPropt("VC_DWCJ_NAME", cjName);
		if (did != null)
			jointPo.setPropt("VC_DWD_ID", did);
		if (dname != null)
			jointPo.setPropt("VC_DWD_NAME", dname);
	}
	
	/**
	 * 设置工务部门
	 * @param jointPo
	 * @param gqId 工区
	 * @param gqName
	 * @param cjId 车间
	 * @param cjName
	 * @param did 段级单位
	 * @param dname
	 */
	private void setGwOrg(FastPo jointPo,String gqId,String gqName,String cjId,String cjName,String did,String dname){
		if (gqId != null)
			jointPo.setPropt("VC_GWGQ_ID", gqId);
		if (gqName != null)
			jointPo.setPropt("VC_GWGQ_NAME", gqName);
		if (cjId != null)
			jointPo.setPropt("VC_GWCJ_ID", cjId);
		if (cjName != null)
			jointPo.setPropt("VC_GWCJ_NAME", cjName);
		if (did != null)
			jointPo.setPropt("VC_GWD_ID", did);
		if (dname != null)
			jointPo.setPropt("VC_GWD_NAME", dname);
	}
	
	public String searchAridsAjax() throws ClassNotFoundException, DatagridBuilderNotFound{
		List<String> arids=searchArids();
		JSONArray ary = new JSONArray();
		ary.addAll(arids);
		return writeHTML(ary.toString());
	}
	/**
	 * 获取已计划道岔的ids
	 * @return
	 */
	public List<String> searchArids(){
		String jh=getParameter("jh");
		List<String> arids = null;
		String sql="SELECT T.VC_TURNOUT_ID FROM GD_T_JOINT_REPAIR T\r\n" + 
				" WHERE T.N_DEL = 0 and to_char(t.DT_JH,'yyyy-mm')=?";
		try {
			arids = getDb().executeStringSqlQuery(sql, new String []{jh});
		} catch (SQLException e) {
			logger.error(e.getMessage(),e);
		}
		return arids;
		
	}
	
	private FastPo getFastpoById(String tableName,String id){
		FastPo fastPo=FastPo.getPo("GD_T_ARCHIVES");
		try {
			fastPo = getDb().loadFastPo(fastPo, id);
		} catch (SQLException e) {
			logger.error(e.getMessage(),e);
		}
		return fastPo;
	}
	
	/**
	 * 查看导出视图Excel
	 * @return
	 */
	public String expViewExcel(){
		look();
		List<FastPo> list = (List<FastPo>) getAttribute("pro");
		
		HSSFWorkbook wb = getViewbook(list);
		try {
			getResponse().setCharacterEncoding("UTF-8");
			getResponse().setContentType("application/zip"); // MIME type for pdf doc
			getResponse().setHeader("Content-disposition", "attachment; filename=\""
					+ new String(("联合整治详情.xls").getBytes("GBK"),"ISO8859-1") + "\"");
			wb.write(getResponse().getOutputStream());
		} catch (Exception e) {
			logger.error(e.getMessage(),e);
			return writeJavaScript("alert('导出文件时发生错误！');");
		}
		
		return null;
	}
	
	public HSSFWorkbook getViewbook(List<FastPo> data){
		String uri = getRequest().getRequestURI();
		uri = uri.substring(FrameworkContext.getPath().length(),uri.lastIndexOf("/"));
		String excelSrc = uri+"/viewExcel.xls";
		File f = new File(FrameworkContext.getAppPath()+excelSrc);
		HSSFWorkbook wb=null;
		
		try {
			wb = new HSSFWorkbook(new FileInputStream(f));
			HSSFSheet sheet = wb.getSheetAt(0);
			HSSFRow row = sheet.getRow(0);
			HSSFCellStyle style = sheet.getRow(1).getCell((short) 0).getCellStyle();
			HSSFCellStyle leftstyle = sheet.getRow(1).getCell((short) 1).getCellStyle();
			int fontPoints = wb.getFontAt(style.getFontIndex()).getFontHeightInPoints();
			
			sheet.shiftRows(3, sheet.getLastRowNum(), data.size(),true,true);
			
			
			short colIdx = 0;
			float rowHeight = 0;
		
			int rowIdx = 1;
			/*表头列*/
			String [] columns=new String[]{"VC_CATEGORY","VC_ITEM",
					"VC_STANDARD","VC_INVESTIGATION_DATA","VC_REPAIR_DATA","VC_CHECK_DATA","VC_NOTE","flagName"};
			
			for(FastPo rowdata:data){
				String flag=rowdata.getString("N_FLAG");
				if("0".equals(flag)){
					rowdata.setPropt("flagName", "电务录入");
				}else{
					rowdata.setPropt("flagName", "工务录入");
				}
				
				row = sheet.createRow(rowIdx);
				if(row==null){
					row = sheet.createRow(0);
				}
				colIdx = 0;
				rowHeight = 0;
				
				for (String col : columns) {
					HSSFCell cell = getCell(row,colIdx);
					cell.setCellStyle(style);
					String val = rowdata.getString(col);
					cell.setCellValue(val);
					cell.setCellStyle(leftstyle);
					int lines = POIUtil.getCellStrLines(sheet,row.getRowNum(),cell,fontPoints);
					rowHeight = Math.max(rowHeight, lines*fontPoints*1.2f+4);
					colIdx++;
				}
				row.setHeightInPoints(fontPoints*0.4f + Math.max(rowHeight,fontPoints*1.2f+8));//设置行高
				rowIdx++;
			}
			
			short[] mergeCol=new short[]{0,1,7};
			for (short s : mergeCol) {
				int start=1;
				for(int i=2;i<=data.size()+1;i++){
					HSSFRow r = sheet.getRow(i);
					HSSFRow br = sheet.getRow(i-1);
					
					HSSFCell cell = getCell(r, s);
					HSSFCell bcell = getCell(br, s);
					
					String val = (cell==null||cell.getCellType()!=HSSFCell.CELL_TYPE_STRING)?"":cell.getStringCellValue();
					String bval =(bcell==null||bcell.getCellType()!=HSSFCell.CELL_TYPE_STRING)?"":bcell.getStringCellValue();
					
					if(!val.equals(bval)){
						if(i-start>1)
							sheet.addMergedRegion(new CellRangeAddress(start, i-1, s, s));
						start=i;
					}
				}
			}
			
			

			String [] fmtStrings=new String[]{"DT_DW_INVESTIGATION" ,"DT_GW_INVESTIGATION",
								"DT_DW_REPAIR" ,"DT_GW_REPAIR" ,"DT_DW_CHECK" ,"DT_GW_CHECK" };

			for (String fmt : fmtStrings) {
				po.setPropt(fmt, po.getDate(fmt)==null?"":FastUtil.format(po.getDate(fmt), "yyyy-MM-dd　HH:mm"));
			}
			
			Pattern pat3 = Pattern.compile("%\\{([^\\}]+)\\}");//结束后表尾区
			for(int i=0;i<=sheet.getLastRowNum();i++){
				row = sheet.getRow(i);
				if(row==null){
					continue;
				}
				for(int j=0;j<=row.getLastCellNum();j++){
					HSSFCell cell = row.getCell((short) j);
					String str = null;
					try {
						str = (cell==null||cell.getCellType()!=HSSFCell.CELL_TYPE_STRING)?null:cell.getStringCellValue();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					if(StringUtil.isNotBlank(str)){
						Matcher mat = pat3.matcher(str);
						while(mat.find()){
							String name = mat.group(1);
							String val = "";
							try {
								val = po.getString(name);
							} catch (Exception e) {
								LogUtil.logError(e);
							}
							
							str = str.replace("%{"+name+"}", val);
						}
						cell.setCellValue(str);
					}
				}
			}
			
		} catch (Exception e) {
			logger.error(e.getMessage(),e);
		}
		return wb;
	}
	
}
