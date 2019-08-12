package com.hhkj.lkjdata.gd.action;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import com.dx.jwfm.framework.core.RequestContext;
import com.dx.jwfm.framework.core.dao.po.FastPo;
import com.dx.jwfm.framework.core.model.FastModel;
import com.dx.jwfm.framework.core.model.FastModelStructure;
import com.dx.jwfm.framework.core.model.search.SearchResultColumn;
import com.dx.jwfm.framework.util.FastUtil;
import com.dx.jwfm.framework.web.action.FastBaseAction;
import com.dx.jwfm.framework.web.exception.ValidateException;

public class GdTTurnoutStandardAction extends FastBaseAction {


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
			int N_SEQ=0;
			StringBuffer err=new StringBuffer();
			try {
				wb = new HSSFWorkbook(new FileInputStream(getImpExcelFile()));
				HSSFSheet sheet = wb.getSheetAt(0);
				int rowCnt = sheet.getLastRowNum();
				String vcType=getParameter("vcType");
				//获取最大序列号
				if(N_SEQ==0){
					String sql="select t.N_SEQ from GD_T_TURNOUT_STANDARD t WHERE  t.n_del=0 AND t.n_seq IS NOT NULL " +
							"AND vc_type='"+vcType+"' ORDER BY t.N_SEQ DESC";
					N_SEQ=getDb().getFirstIntSqlQuery(sql);
				}
				for(int i=2;i<=rowCnt;i++){
					HSSFRow row = sheet.getRow(i);
					FastPo p = getPoFromRow(row, vcType,model);
					if(p!=null){
						p.setPropt("N_SEQ", ++N_SEQ);
						list.add(p);
					}else{
						String message="第"+(i-1)+"条数据为空，未添加。";
						err.append(message+"<br>");
						logger.error(message);
					}
				}
				getDb().addPo(list);
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
			if(FastUtil.isBlank(err.toString())){
				return ajaxResult(true, "导入成功，共" + list.size() + "条");
			}else{
				return ajaxResult(false, "导入成功，共" + list.size() + "条。"+err.insert(0, "<br>其中").toString());
			}
		} else {
			return "/gd/gdTTurnoutStandardImport.jsp";
		}
	
		
	}

	private String category=null;
	private String vcItem=null;
	/**
	 * 将表里的数据写到po中
	 * @param row
	 * @param vcType
	 * @param model
	 * @return
	 */
	protected FastPo getPoFromRow(HSSFRow row, String vcType,FastModelStructure model) {
		if(model==null || model.getSearch()==null || model.getSearch().getSearchResultColumns()==null){
			return null;
		}
		short colIdx = 0;
		String[] cols=new String[]{"VC_CATEGORY", "VC_ITEM", "VC_STANDARD","N_FLAG"};
		FastPo po = FastPo.getPo(model.getMainTableName());
		po.put("VC_TYPE", vcType);
		for (String c : cols) {
			HSSFCell cell = getCell(row, colIdx);
			Object val = null;
			if(cell!=null){
				try {
					val = cell.getStringCellValue();
				} catch (Exception e) {
					val = cell.getNumericCellValue();
				}
			}
			if(c.equals("VC_CATEGORY")&&(val==null||"".equals(val))){
				val=category;
			}
			if(c.equals("VC_ITEM")&&(val==null||"".equals(val))){
				val=vcItem;
			}
			
			if(val==null||"".equals(val)){
				return null;
			}
			po.put(c, val);
			colIdx++;
		}
		if("工务".equals(po.get("N_FLAG"))){
			po.setPropt("N_FLAG", 1);
		}else if("电务".equals(po.get("N_FLAG"))){
			po.setPropt("N_FLAG", 0);
		}
		po.setPropt("DT_ADD", new Date());
		po.setPropt("VC_ADD", RequestContext.getBeanValue("curUserName"));
		category=(String) po.get("VC_CATEGORY");
		vcItem=(String) po.get("VC_ITEM");
		return po;
	}
	@Override
	public String look() {
		return "openViewPage";
	}
	@Override
	public String expExcel() {
		FastModel fmodel = RequestContext.getFastModel();
		FastModelStructure Structure = fmodel.getModelStructure();
		for (SearchResultColumn c : Structure.getSearch().getSearchResultColumns()) {
			String format = c.getVcFormat();
			if(format.contains("function")) c.setVcFormat(null);
			
		}
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


	private void poView(FastPo fastPo){
		int flag=fastPo.getInteger("N_FLAG");
		if(flag==0){
			fastPo.setPropt("N_FLAG", "电务");
		}else if(flag==1){
			fastPo.setPropt("N_FLAG", "工务");
		}else{
			fastPo.setPropt("N_FLAG", "");
		}
	}
	
}
