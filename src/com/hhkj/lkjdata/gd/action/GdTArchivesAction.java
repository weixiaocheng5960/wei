package com.hhkj.lkjdata.gd.action;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.expr.NewArray;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.jasper.tagplugins.jstl.core.If;
import org.apache.jsp.jwfm.main.main_jsp;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.jsoup.helper.DataUtil;

import com.dx.jwfm.framework.core.ContextAssist;
import com.dx.jwfm.framework.core.RequestContext;
import com.dx.jwfm.framework.core.contants.RequestContants;
import com.dx.jwfm.framework.core.dao.DbHelper;
import com.dx.jwfm.framework.core.dao.dialect.DatabaseDialect;
import com.dx.jwfm.framework.core.dao.model.FastTable;
import com.dx.jwfm.framework.core.dao.po.FastPo;
import com.dx.jwfm.framework.core.model.FastModel;
import com.dx.jwfm.framework.core.model.FastModelStructure;
import com.dx.jwfm.framework.core.model.search.SearchResultColumn;
import com.dx.jwfm.framework.core.model.view.DictNode;
import com.dx.jwfm.framework.util.FastUtil;
import com.dx.jwfm.framework.util.POIUtil;
import com.dx.jwfm.framework.web.action.FastBaseAction;
import com.dx.jwfm.framework.web.exception.DatagridBuilderNotFound;
import com.dx.jwfm.framework.web.logic.IPermiss;
import com.hhkj.framework.context.FrameworkContext;
import com.hhkj.framework.datamaintain.logic.ISQLMapper;
import com.hhkj.framework.util.DateUtil;
import com.hhkj.framework.util.LogUtil;
import com.hhkj.framework.util.StringUtil;
import com.hhkj.sys.login.model.User;
import com.hhkj.util.DbUtil;
import com.hhkj.util.RegeditUtil;

public class GdTArchivesAction extends FastBaseAction {

	private String[] cols=null;
	
	
	
	@Override
	public String execute() throws ClassNotFoundException,DatagridBuilderNotFound {
		FastPo p=getDic();
		if(p!=null)
			setAttribute("archDic", JSONObject.fromObject(p).get("VC_NOTE").toString());
		else
			setAttribute("archDic", "{}");

		if(StringUtil.isBlank(search.getString("orgId")))
			search.setPropt("orgId", User.getUser().getOrgManProId());
		return super.execute();
	}
	
	/**
	 * excel导出
	 */
	@Override
	public HSSFWorkbook getWorkbook(List<FastPo> data, FastModelStructure model) {
		String uri = getRequest().getRequestURI();
		uri = uri.substring(FrameworkContext.getPath().length(),uri.lastIndexOf("/"));
		String excelSrc = uri+"/道岔档案.xls";
		
		File f = new File(FrameworkContext.getAppPath()+excelSrc);
		HSSFWorkbook wb = null;
		try {
			InputStream in = new FileInputStream(f);
			wb = new HSSFWorkbook(in);
			HSSFSheet sheet = wb.getSheetAt(0);
			setHead(sheet.getRow(1));
			
			HSSFRow row = sheet.getRow(0);
			if(row==null){
				row = sheet.createRow(0);
			}
			HSSFCellStyle style = sheet.getRow(2).getCell((short) 0).getCellStyle();
			int fontPoints = wb.getFontAt(style.getFontIndex()).getFontHeightInPoints();
			short colIdx = 0;
			float rowHeight = 0;
			HSSFCell cell = null;
			sheet.setColumnWidth(0, 1000);
			int headRows = 2;//表头行
			int rowIdx = 2;
			HSSFRow dataRow = sheet.getRow(2);
			for(FastPo rowdata:data){
				row = sheet.createRow(rowIdx);
				if(row==null){
					row = sheet.createRow(0);
				}
				colIdx = 0;
				rowHeight = 0;
				cell = getCell(row,colIdx++);
				cell.setCellValue(rowIdx-headRows+1);
				cell.setCellStyle(style);
				for (String col : cols) {
					cell = getCell(row,colIdx);
					if(dataRow.getCell(colIdx)!=null)
						style=dataRow.getCell(colIdx).getCellStyle();
					cell.setCellStyle(style);
					if(rowdata.get(col) instanceof Date){
						cell.setCellValue(DateUtil.format(rowdata.getDate(col)));
					}else{
						cell.setCellValue(rowdata.getString(col));
					}
					int lines = POIUtil.getCellStrLines(sheet,row.getRowNum(),cell,fontPoints);
					rowHeight = Math.max(rowHeight, lines*fontPoints*1.2f+4);
					colIdx++;
				}
				row.setHeightInPoints(fontPoints*0.4f + Math.max(rowHeight,fontPoints*1.2f+8));//设置行高
				rowIdx++;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(),e);
		}
		return wb;
	
	}




	/**
	 * excel表导入
	 */
	@Override
	public String importItem() {
		if ("save".equals(op)) {
			if (getImpExcelFile() == null) {
				return ajaxResult(false, "未找到上传的文件！");
			}
			FastModel fmodel = RequestContext.getFastModel();
			if(fmodel==null){
				return ajaxResult(false, "未找到匹配的导出模板！");
			}
			FastModelStructure model = fmodel.getModelStructure();
			HSSFWorkbook wb = null;
			List<FastPo> list = new ArrayList<FastPo>();
			int rowCnt = 0;
			try {
				wb = new HSSFWorkbook(new FileInputStream(getImpExcelFile()));
				HSSFSheet sheet = wb.getSheetAt(0);
				rowCnt = sheet.getLastRowNum();
				//设置表头字段
				setHead(sheet.getRow(1));
				for(int i=2;i<=rowCnt;i++){
					HSSFRow row = sheet.getRow(i);
					int[] arr = {2,4,5};
					for (int x : arr) {
						if(row.getCell(x).toString() == ""){
							x++;
							return ajaxResult(false, "第" + i + "行,第"+x+"列不能为空,请修改后重新导入");
						}
					}
					logger.info("解析第"+i+"行数据");
					FastPo p = getPoFromRow(row,model);
					if(p!=null){
						list.add(p);
					}
				}
				getDb().insertOrUpdatePo(list);
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
				return ajaxResult(false, "Excel文件读取失败！");
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
				return ajaxResult(false, "导入失败，具体原因："+FastUtil.getExceptionInfo(e));
			}finally{
				if(wb!=null){
					try {
						wb.close();
					} catch (IOException e) {
						logger.error(e.getMessage(),e);
					}
				}
			}
			return ajaxResult(true, "导入成功，共"+(rowCnt-1)+"条数据,导入成功" + list.size() + "条数据");
		} else {
			return "/gd/gdTArchivesImport.jsp";
		}
	
		
	}
	

	private void setHead(HSSFRow row) {
		List<String> list=new ArrayList<String>();
		for (short i = 1; i < row.getLastCellNum(); i++) {
			HSSFCell cell = getCell(row, i);
			String val = cell.getStringCellValue().trim();
			val=parseString(val);
			if(val!=null)
				list.add(val);
			else
				break;
			
		}
		cols=new String[list.size()];
		cols=list.toArray(cols);
	}


	/**
	 * @param row
	 * @param model
	 * @return
	 */
	protected FastPo getPoFromRow(HSSFRow row,FastModelStructure model){
		if(model==null || model.getSearch()==null || model.getSearch().getSearchResultColumns()==null){
			return null;
		}
		short colIdx = 1;
		
		FastPo po = FastPo.getPo(model.getMainTableName());
		po.setPropt("DT_ADD", new Date());
		po.setPropt("VC_ADD", RequestContext.getBeanValue("curUserName"));
		
		HSSFCell idCell = getCell(row, row.getLastCellNum());
		String id = idCell.getStringCellValue().trim();
		if(FastUtil.isNotBlank(id)){
			try {
				po = getDb().loadFastPo(po, id);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		String sql="SELECT T.VC_ID, T.VC_NAME, T.VC_LINEID, L.VC_NAME LINE_NAME  FROM PUB_T_STATION T, PUB_T_LINE L\r\n" + 
				"WHERE t.vc_lineid=l.vc_id AND (t.vc_name IN(?,?,?,?,?) OR t.vc_station$number IN(?,?,?,?,?)) and t.vc_del$flag=0 and l.vc_del$flag=0";
		for (String c : cols) {
			HSSFCell cell = getCell(row, colIdx);
			Object val = null;
			if(cell!=null){
				if("DT_DATE".equals(c)){
//					val = cell.getDateCellValue();
					val = cell.getStringCellValue().trim();
					try {
						val=DateUtil.parse((String) val);
					} catch (ParseException e) {
						logger.info("第DT_DATE列:日期类型错误");
						e.printStackTrace();
					}
					
					
				}else if("VC_STATION".equals(c)){
					val = cell.getStringCellValue().trim();
					try {
						List<FastPo> stations = getDb().executeSqlQuery(sql, new Object[]{val,"郑州北"+val,val+"站",val+"线路所",val+"区",val,"郑州北"+val,val+"站",val+"线路所",val+"区"});
						if(!stations.isEmpty()){
							FastPo staFastPo=stations.get(0);
							po.put("VC_LINE_ID", staFastPo.get("VC_LINEID"));
							po.put("VC_STATION_ID", staFastPo.get("VC_ID"));
							po.put("VC_LINE", staFastPo.get("LINE_NAME"));
							po.put("VC_STATION", staFastPo.get("VC_NAME"));
						}
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}else{
					try {
						val = cell.getStringCellValue().trim();
					} catch (Exception e) {
						val = cell.getNumericCellValue();
					}
				}
			}
			po.put(c, val);
			logger.info("第"+colIdx+"列:"+val);
			colIdx++;
		}
		
		
		return po;
	}

	private String parseString(String name) {
		Pattern pat = Pattern.compile("\\$\\{([^\\}]+)\\}");
		Matcher mat = pat.matcher(name);
		while(mat.find()){
			return mat.group(1);
		}
		return null;
	}
	public static void main(String[] args) {
		try {
			System.out.println(DateUtil.parse("2017/1/1"));
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	@Override
	protected String validateData() {
		String keyCode = po.getTblModel().keyColCode();
		String method = (String)getRequest().getAttribute(RequestContants.REQUEST_URI_METHOD);
		if("add".equals(method)){//天加新数据
			String station=po.getString("VC_STATION_ID");
			String name=po.getString("VC_NAME");
			String sql="SELECT count(1)  FROM GD_T_ARCHIVES t WHERE t.n_del=0 AND t.vc_name=? AND t.vc_station_id=?";
			try {
				int cnt=getDb().getFirstIntSqlQuery(sql,new String[]{name,station});
				if(cnt>0)
				return "该道岔已存在,请更改其他名称";
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	/**
	 * 查询道岔档案显示列表
	 * @return
	 */
	private FastPo getDic(){
		//获取字典项
    	String falgLeaf = User.getUser().getSysOrg().getVcFlagLeaf()==null?"":User.getUser().getSysOrg().getVcFlagLeaf();
    	List<FastPo> list = FastUtil.getDicts("道岔档案显示列表");
    	FastPo p=null;
		if(list!=null&&!list.isEmpty()){
			for (FastPo fastPo : list) {
				if(falgLeaf.equals(fastPo.getString("VC_CODE")))
					p=fastPo;
			}
		}
		return p;
	}
	
	private List<SearchResultColumn> resultCols;
	/**
	 * 开发人：魏晓成
	 * 开发日期: 2018年11月13日 上午11:44:08
	 * 功能描述: 模块的设置功能
	 * 方法的参数和返回值: 
	 */
	public String moudleConf(){
		String url = getParameter("url");//在toolbar标签中指定的menuUrl
		String baseUrl = getParameter("baseUrl");//当前页面的URL
    	IPermiss permiss = FastUtil.getPermiss();
    	//获取字典项
    	String falgLeaf = User.getUser().getSysOrg().getVcFlagLeaf()==null?"":User.getUser().getSysOrg().getVcFlagLeaf();
    	FastPo p=getDic();
		
    	if(permiss!=null && !(url!=null && permiss.hasPermiss(url, "setCols")) && !(baseUrl!=null && permiss.hasPermiss(baseUrl, "setCols"))){
			return writeHTML("您没有权限执行此操作！");
		}else if("saveResults".equals(op)){
			JSONObject map =new JSONObject();
			for (SearchResultColumn col : resultCols) {
				map.put(col.getVcCode(), col.isHidden());
			}
			
			if(p==null){
				p=FastPo.getPo("FAST_T_DICT");
				p.setPropt("VC_USER_ID", "sys");
				p.setPropt("VC_GROUP", "道岔档案显示列表");
				p.setPropt("VC_CODE", falgLeaf);
			}
			p.setPropt("VC_NOTE", map.toString());
			try {
				getDb().insertOrUpdatePo(p);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			return ajaxResult(true, "保存成功");
		}
		else{
			FastModel model = ContextAssist.getFastModel(url);
			FastModel baseModel = ContextAssist.getFastModel(baseUrl);
			setAttribute("url", url);
			JSONArray ary = new JSONArray();
			ary = new JSONArray();
			if(baseModel!=null){
				resultCols = baseModel.getModelStructure().getSearch().getSearchResultColumns();
			}
			else if(model!=null){
				resultCols=model.getModelStructure().getSearch().getSearchResultColumns();
			}
			
			for (int i = 0; i < resultCols.size(); i++) {
				SearchResultColumn resultCol = resultCols.get(i);
				if(!resultCol.isHidden()){
					ary.add(resultCol);
				}
			}
			if(p!=null){
				String note=p.getString("VC_NOTE");
				JSONObject json=JSONObject.fromObject(note);
				for (Object object : ary) {
					JSONObject resultCol = (JSONObject) object;
					resultCol.put("hidden",json.get(resultCol.get("vcCode")));
				}
			}
			
			setAttribute("resultModel", ary.toString());//表格结果格式优先使用当前页面的URL
			setAttribute("url", url);
			setAttribute("baseUrl", baseUrl);
			return "/gd/archives/colsPage.jsp";
		}
	}
	
	public String synData(){
		FastModel fmodel = RequestContext.getFastModel();
		String sql = search.getSearchSql();
		DbHelper db = new DbHelper();
		try {
			//getDb().executeSqlQuery();
			List<FastPo> list = db.executeSqlQuery(sql,FastPo.getPo(fmodel.getModelStructure().getMainTableName()),search);
			String vcStation,vcStationId,vcName;
			String equDetailSQl="select * from em_v_equ$detail_dc where (vc_pos=? or vcstationname=?) and vc_equ$use$name=?";
			//sczh的数据
			FastPo equDetail;
			//要同步的字段
			String[]synNames=new String[]{"VC_CHAH","VC_ZXDC","VC_DWTH","VC_KAIX","VC_DWJDBH","VC_QLFS","VC_DZSX"};
			String synValue;
			//同步总数
			int cnt =0;
			//VC_STATION//VC_NAME
			for (FastPo fastPo : list) {
				vcStationId=fastPo.getString("VC_STATION_ID");
				vcStation=fastPo.getString("VC_STATION");
				vcName=fastPo.getString("VC_NAME");
				if(StringUtil.isNotBlank(vcStation)&&StringUtil.isNotBlank(vcName)){
					List<FastPo> equDetailList = getDb().executeSqlQuery(equDetailSQl, new Object[]{vcStationId,vcStation,vcName});
					if(!equDetailList.isEmpty()){
						equDetail=equDetailList.get(0);
						fastPo.setPropt("VC_SYN_ID", equDetail.getString("VC_ID"));
						for (String synName : synNames) {
							synValue=equDetail.getString(synName);
							if(StringUtil.isNotBlank(synValue)){
								fastPo.setPropt(synName, synValue);
							}
						}
						int i=db.updatePo(fastPo)?1:0;
						cnt+=i;
					}
				}
			}
			return ajaxResult(true, "查询数据"+list.size()+"条，更新成功 "+cnt+"条记录 ！");
		} catch (SQLException e) {
			logger.error(e.getMessage(),e);
			return ajaxResult(false, "更新时发生错误："+e.getMessage()+"\n"+e.getClass().getName());
		}
		
	}
	
	public String getAlarmInfo() {
		// 报警信息
		String bjsql = "select * from "
				+ "(select * from alarm_v_info t where vc_alarm_name in('道岔缺口报警','道岔断表示报警') and t.vc_station_id=? and t.vc_equ=? order by t.dt_time desc) "
				+ "where rownum<=50";
		try {
			List<FastPo> alarmList = getDb().executeSqlQuery(bjsql,new Object[] { search.getString("VC_STATION_ID"),search.getString("VC_NAME") });
			return buildResultJson(alarmList);
		} catch (Exception e) {
			logger.error(e.getMessage(),e);
			e.printStackTrace();
		}
		return null;
		
	}
	public String getAffectInfo() {
		// 故障信息
		String sql = "select * from GT_SAFE_AFFECT_INFO_V \r\n" + 
				"where VC_STATION=? order by DT_happen desc,VC_ID";
		try {
			List<FastPo> affectList = getDb().executeSqlQuery(sql,new Object[] {search.getString("VC_STATION_ID")});
			return buildResultJson(affectList);
		} catch (Exception e) {
			logger.error(e.getMessage(),e);
			e.printStackTrace();
		}
		return null;
		
	}

	public List<SearchResultColumn> getResultCols() {
		return resultCols;
	}

	public void setResultCols(List<SearchResultColumn> resultCols) {
		this.resultCols = resultCols;
	}
	
	
}
