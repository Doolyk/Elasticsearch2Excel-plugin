package org.esreport;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
import org.json.JSONArray;
import org.json.JSONObject;

public class ESReport {

	final ESLogger logger = Loggers.getLogger(ESReport.class);

	Vector<String> typeMapping = new Vector<String>();

	XSSFWorkbook wb = new XSSFWorkbook();
	XSSFSheet sheet;
	XSSFRow row;
	XSSFCell cell;
	XSSFFont font = wb.createFont();
	XSSFCellStyle data_style;
	XSSFCellStyle double_data_style;
	XSSFCellStyle date_data_style;
	XSSFCellStyle title_style;
	XSSFCellStyle header_style;
	ScriptEngineManager mgr = new ScriptEngineManager();
	ScriptEngine engine = mgr.getEngineByName(GlobalData.engineName);

	// INPUT PARAMETERS
	String index;
	String type;
	String config;
	String statement;
	String reportTitle;
	String routing = "";
	String nullValue = GlobalData.nullValue;

	JSONArray configObj;
	JSONObject queryObj;
	JSONObject reportAccessType;
	JSONObject valueMapping;
	Settings settings;
	Client esclient;

	int k = 0;
	long hitscount = 0;
	int rows_fetched = 0;
	int y = 0;
	int doublePrecision = GlobalData.doublePrecision; // default 2
	int columnSize = GlobalData.columnSize; // default -1 (i.e., autoSize)
	int batchsize = GlobalData.batchsize; // default 5000
	int i = 0;
	int rownumber = 0;

	ESReport() {
		logger.info("Initializing Constructor");
		setStyles();
	}

	public ESReport(Client client) {
		logger.info("Initializing Constructor");
		setStyles();
		this.esclient = client;
	}

	private void setStyles() {
		setDataStyle();
		setTitleStyle();
		setHeaderStyle();
	}

	private void setHeaderStyle() {
		header_style = wb.createCellStyle();
		font.setFontHeightInPoints((short) 11);
		font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
		font.setColor(HSSFColor.WHITE.index);
		header_style.setFont(font);
		header_style.setFillForegroundColor(HSSFColor.LIGHT_BLUE.index);
		header_style.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
	}

	private void setTitleStyle() {
		title_style = wb.createCellStyle();
		font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
		font.setColor(HSSFColor.WHITE.index);
		font.setFontHeightInPoints((short) 14);
		title_style.setFont(font);
		title_style.setFillForegroundColor(HSSFColor.LIGHT_BLUE.index);
		title_style.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
	}

	private void setDataStyle() {
		data_style = wb.createCellStyle();
		data_style.setBorderBottom(HSSFCellStyle.BORDER_THIN);
		data_style.setBorderTop(HSSFCellStyle.BORDER_THIN);
		data_style.setBorderRight(HSSFCellStyle.BORDER_THIN);
		data_style.setBorderLeft(HSSFCellStyle.BORDER_THIN);
	}

	private void setDoubleDataStyle() {
		double_data_style = wb.createCellStyle();
		String doubleFormatString = doubleStringFormatGenerator(doublePrecision);
		double_data_style.setDataFormat(wb.createDataFormat().getFormat(doubleFormatString));
	}

	public void process(String inputStr) {
		i = 0;
		logger.info("Process Started");

		JSONObject input = new JSONObject(inputStr);
		initializeParameters(input);

		sheet = wb.createSheet(reportTitle);

		setTitle();
		setHeaders();

		logger.info("Building Excel Report");
		do {
			queryObj.put("from", batchsize * k);
			SearchResponse response = null;
			logger.debug("Query: " + queryObj.toString());
			if (routing.equals("")) {
				response = esclient.prepareSearch(index).setTypes(type).setSource(queryObj.toString()).execute()
						.actionGet();
			} else {
				response = esclient.prepareSearch(index).setTypes(type).setRouting(routing)
						.setSource(queryObj.toString()).execute().actionGet();
			}
			SearchHits hits = response.getHits();
			hitscount = hits.totalHits();
			buildDataLayout(hits);
			logger.debug("Processing: " + response.toString());
			k++;
			rows_fetched = batchsize * k;
		} while (rows_fetched < hitscount);
		logger.info("Finished processing data");
		formatExcelSheet();

		reportAccess(wb, reportAccessType);
		esclient.close();
	}

	private void initializeParameters(JSONObject input) {
		logger.info("Initializing Input Parameters");

		index = input.getString(GlobalData.indexStr);
		type = input.getString(GlobalData.typeStr);
		config = input.get(GlobalData.configStr).toString();
		statement = input.get(GlobalData.statementStr).toString();
		reportTitle = input.getString(GlobalData.reportTitleStr);

		if (input.has(GlobalData.valueMappingStr)) {
			valueMapping = input.getJSONObject(GlobalData.valueMappingStr);
		}

		if (input.has(GlobalData.routingStr)) {
			routing = input.getString(GlobalData.routingStr);
		}
		if (input.has(GlobalData.batchSizeStr)) {
			batchsize = input.getInt(GlobalData.batchSizeStr);
		}
		if (input.has(GlobalData.nullValueStr)) {
			nullValue = input.getString(GlobalData.nullValueStr);
		}

		configObj = new JSONArray(config);
		queryObj = new JSONObject(statement);

		queryObj.put("size", batchsize);
		reportAccessType = input.getJSONObject(GlobalData.reportAccessStr);

		if (input.has(GlobalData.columnSizeStr))
			columnSize = input.getInt(GlobalData.columnSizeStr);


		if (input.has(GlobalData.decimalStr))
			doublePrecision = input.getInt(GlobalData.decimalStr);

		setDoubleDataStyle();

		// Read data format from JSON file
		readTypeConfig();

		k = 0;
		hitscount = 0;
		rows_fetched = 0;
	}

	private void setTitle() {
		logger.info("Setting Title and Headers");

		row = sheet.createRow(rownumber);
		rownumber++;

		cell = row.createCell(0);
		cell.setCellValue(reportTitle);
		cell.setCellStyle(title_style);

		for (int i = 1; i < configObj.length(); i++) {
			cell = row.createCell(i);
			cell.setCellStyle(title_style);
		}
	}

	private void setHeaders() {
		for (int i = 1; i < configObj.length(); i++) {
			cell = row.createCell(i);
			cell.setCellStyle(title_style);
		}

		row = sheet.createRow(rownumber);
		for (int i = 0; i < configObj.length(); i++) {
			cell = row.createCell(i);
			JSONObject headerJSON = (JSONObject) configObj.get(i);
			cell.setCellValue(headerJSON.getString("title"));
			cell.setCellStyle(header_style);
		}
		rownumber++;
	}

	private void buildDataLayout(SearchHits hits) {
		logger.info("buildDataLayout");
		// For each row
		for (int i = 0; i < hits.getHits().length; i++) {
			// Row n
			Map<String, SearchHitField> responseFields = hits.getAt(i).getFields();
			row = sheet.createRow(rownumber);
			for (int j = 0; j < configObj.length(); j++) {
				cell = row.createCell(j);
				JSONObject headerJSON = (JSONObject) configObj.get(j);
				String content = null;
				content = headerJSON.getString(GlobalData.formatStr).trim();
				content = getExprValue(responseFields, content);
				writeToCell(j, content, cell);
			}
			rownumber++;
		}
	}

	// 0 getValue
	// 1 getDerivedValue
	// 2 Length
	// 3 Format Number Length
	// 4 Sub String
	// 5 Character at index
	// 6 Calculate
	// 7 Range
	// 8 Array indexOf(int value)
	// 9 Array indexOf(String value)
	// 10 Array valueAt(index)
	private String getExprValue(Map<String, SearchHitField> responseFields, String format) {
		String exprTemp = format;
		int exprIndexSize = 0;

		int startIndexCount = StringUtils.countMatches(exprTemp, "[");
		int endIndexCount = StringUtils.countMatches(exprTemp, "]");

		if (startIndexCount == endIndexCount) {
			exprIndexSize = startIndexCount;
		}

		for (int i = 0; i < exprIndexSize; i++) {
			JSONObject exprIndex = getExprIndex(exprTemp);
			String elementeryExpr = exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr) + 1,
					exprIndex.getInt(GlobalData.endIndexStr));

			String[] elementeryExprArray = elementeryExpr.split(",");

			if (elementeryExprArray[0].equals("0")) {
				String t = getValue(responseFields, elementeryExprArray[1]);
				t = getExprValue(responseFields, t);
				exprTemp = exprTemp
						.replaceFirst(Pattern.quote(exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr),
								exprIndex.getInt(GlobalData.endIndexStr) + 1)), t);
			}

			if (elementeryExprArray[0].equals("1")) {
				String t = getDerivedValue(responseFields, elementeryExprArray[1], elementeryExprArray[2]);
				t = getExprValue(responseFields, t);
				exprTemp = exprTemp
						.replaceFirst(Pattern.quote(exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr),
								exprIndex.getInt(GlobalData.endIndexStr) + 1)), t);
			}

			if (elementeryExprArray[0].equals("2")) {
				String t = getStringLength(elementeryExprArray[1]);
				exprTemp = exprTemp
						.replaceFirst(Pattern.quote(exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr),
								exprIndex.getInt(GlobalData.endIndexStr) + 1)), t);
			}

			if (elementeryExprArray[0].equals("3")) {
				String t = getFormatNumberLength(elementeryExprArray[1], Integer.valueOf(elementeryExprArray[2]));
				exprTemp = exprTemp
						.replaceFirst(Pattern.quote(exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr),
								exprIndex.getInt(GlobalData.endIndexStr) + 1)), t);
			}

			if (elementeryExprArray[0].equals("4")) {
				String t = getSubString(elementeryExprArray[1], Integer.valueOf(elementeryExprArray[2]),
						Integer.valueOf(elementeryExprArray[3]));
				exprTemp = exprTemp
						.replaceFirst(Pattern.quote(exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr),
								exprIndex.getInt(GlobalData.endIndexStr) + 1)), t);
			}

			if (elementeryExprArray[0].equals("5")) {
				String t = getCharacter(elementeryExprArray[1], Integer.valueOf(elementeryExprArray[2]));
				exprTemp = exprTemp
						.replaceFirst(Pattern.quote(exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr),
								exprIndex.getInt(GlobalData.endIndexStr) + 1)), t);
			}

			if (elementeryExprArray[0].equals("6")) {
				String t = getComputedString(elementeryExprArray[1]);
				exprTemp = exprTemp
						.replaceFirst(Pattern.quote(exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr),
								exprIndex.getInt(GlobalData.endIndexStr) + 1)), t);
			}

			if (elementeryExprArray[0].equals("7")) {
				String t = getRange(elementeryExprArray[1], elementeryExprArray[2]);
				t = getExprValue(responseFields, t);
				exprTemp = exprTemp
						.replaceFirst(Pattern.quote(exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr),
								exprIndex.getInt(GlobalData.endIndexStr) + 1)), t);
			}

			if (elementeryExprArray[0].equals("8")) {
				String t = getArrayIndexOf(responseFields, elementeryExprArray[1],
						Integer.valueOf(elementeryExprArray[2]));
				exprTemp = exprTemp
						.replaceFirst(Pattern.quote(exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr),
								exprIndex.getInt(GlobalData.endIndexStr) + 1)), String.valueOf(t));
			}

			if (elementeryExprArray[0].equals("9")) {
				String t = getArrayIndexOf(responseFields, elementeryExprArray[1], elementeryExprArray[2]);
				exprTemp = exprTemp
						.replaceFirst(Pattern.quote(exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr),
								exprIndex.getInt(GlobalData.endIndexStr) + 1)), String.valueOf(t));
			}

			if (elementeryExprArray[0].equals("10")) {
				String t = getArrayValueAt(responseFields, elementeryExprArray[1],
						Integer.valueOf(elementeryExprArray[2]));
				exprTemp = exprTemp
						.replaceFirst(Pattern.quote(exprTemp.substring(exprIndex.getInt(GlobalData.startIndexStr),
								exprIndex.getInt(GlobalData.endIndexStr) + 1)), t);
			}
		}

		return exprTemp;
	}

	// ProcessType: 0
	private String getValue(Map<String, SearchHitField> responseFields, String fieldName) {
		if (responseFields.containsKey(fieldName)) {
			SearchHitField fieldValueObj = responseFields.get(fieldName);
			return fieldValueObj.getValue().toString();
		} else {
			return nullValue;
		}
	}

	// ProcessType: 1
	private String getDerivedValue(Map<String, SearchHitField> responseFields, String valueMappingKey, String value) {
		String trimValue = value.trim();
		JSONObject tempMapping = valueMapping.getJSONObject(valueMappingKey);
		if (tempMapping.has(trimValue)) {
			return tempMapping.getString(trimValue);
		} else if (tempMapping.has(GlobalData.defaultStr)) {
			return tempMapping.getString(GlobalData.defaultStr);
		} else {
			return nullValue;
		}
	}

	// ProcessType: 2
	private String getStringLength(String fieldValue) {
		if (!fieldValue.equals(nullValue)) {
			return String.valueOf(fieldValue.length());
		} else {
			return nullValue;
		}
	}

	// ProcessType: 3
	private String getFormatNumberLength(String fieldValue, Integer formatNumberLength) {
		String format = StringUtils.repeat("0", formatNumberLength);
		DecimalFormat mFormat = new DecimalFormat(format);
		if (StringUtils.isNumeric(fieldValue)) {
			return mFormat.format(Integer.valueOf(fieldValue));
		} else {
			return nullValue;
		}
	}

	// ProcessType: 4
	private String getSubString(String fieldValue, int from, int end) {
		if (!fieldValue.equals("-")) {
			return fieldValue.substring(from, end);
		} else {
			return fieldValue;
		}
	}

	// ProcessType: 5
	private String getCharacter(String fieldValue, int index) {
		if (index < fieldValue.length() && !fieldValue.equals(nullValue)) {
			return String.valueOf(fieldValue.charAt(index));
		} else {
			return nullValue;
		}
	}

	// ProcessType: 6
	private String getComputedString(String fieldValue) {
		if (!fieldValue.equals(nullValue) && !fieldValue.equals("")) {
			try {
				return String.valueOf(engine.eval(fieldValue));
			} catch (ScriptException e) {
				return nullValue;
			}
		}
		return nullValue;
	}

	// ProcessType: 7
	@SuppressWarnings("unchecked")
	private String getRange(String valueMappingKey, String fieldValue) {
		JSONObject tempMapping = valueMapping.getJSONObject(valueMappingKey);

		if (!fieldValue.equals(nullValue) && !fieldValue.equals("")) {
			Iterator<String> keys = tempMapping.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				String keyTemp = key;
				key = key.replace("x", fieldValue);
				try {
					if ((Boolean) engine.eval(key)) {
						return tempMapping.getString(keyTemp);
					}
				} catch (ScriptException e) {
					return nullValue;
				}
			}
		}
		if (tempMapping.has(GlobalData.defaultStr)) {
			return tempMapping.getString(GlobalData.defaultStr);
		}
		return nullValue;
	}

	// ProcessType: 8
	private String getArrayIndexOf(Map<String, SearchHitField> responseFields, String fieldName, int value) {
		try {
			return String.valueOf(responseFields.get(fieldName).getValues().indexOf(value));
		} catch (Exception e) {
			return nullValue;
		}
	}

	// ProcessType: 9
	private String getArrayIndexOf(Map<String, SearchHitField> responseFields, String fieldName, String value) {
		try {
			return String.valueOf(responseFields.get(fieldName).getValues().indexOf(value));
		} catch (Exception e) {
			return nullValue;
		}
	}

	// ProcessType 10
	private String getArrayValueAt(Map<String, SearchHitField> responseFields, String fieldName, int arrayIndex) {
		try {
			return String.valueOf(responseFields.get(fieldName).getValues().get(arrayIndex));
		} catch (Exception e) {
			return nullValue;
		}
	}

	private JSONObject getExprIndex(String exprTemp) {
		int startIndex = 0;
		int endIndex = 0;

		for (int i = 0; i < exprTemp.length(); i++) {
			if (exprTemp.substring(i, i + 1).equals("[")) {
				startIndex = i;
				continue;
			}
			if (exprTemp.substring(i, i + 1).equals("]")) {
				endIndex = i;
				break;
			}
		}

		JSONObject exprIndex = new JSONObject();
		exprIndex.put(GlobalData.startIndexStr, startIndex);
		exprIndex.put(GlobalData.endIndexStr, endIndex);

		return exprIndex;
	}

	public void reportAccess(XSSFWorkbook wb2, JSONObject reportAccess) {
		DecimalFormat mFormat = new DecimalFormat("00");
		Calendar date = new GregorianCalendar();
		String fileName = reportAccess.getString("fileName");
		fileName += "_" + date.get(Calendar.YEAR) + mFormat.format(Integer.valueOf(date.get(Calendar.MONTH) + 1))
				+ date.get(Calendar.DAY_OF_MONTH) + "_" + mFormat.format(date.get(Calendar.HOUR_OF_DAY))
				+ mFormat.format(date.get(Calendar.MINUTE));
		JSONObject reportAccessType = null;

		if (reportAccess.has(GlobalData.reportFileConfigStr)) {
			logger.info("Saving file for FTP access");
			reportAccessType = reportAccess.getJSONObject(GlobalData.reportFileConfigStr);
			reportAccessTypeFile(wb, reportAccessType, fileName);
			logger.info("Saving file for FTP access done");
		} else
			logger.info("Save path null!");

		if (reportAccess.has(GlobalData.emailStr)) {
			logger.info("Sending E-Mail...");
			reportAccessType = reportAccess.getJSONObject(GlobalData.emailStr);
			reportAccessTypeEMail(wb, reportAccessType, fileName);
		} else
			logger.info("No email will be sent!!");
	}

	public void reportAccessTypeEMail(XSSFWorkbook localwb, JSONObject reportAccessTypeEMail, String fileName) {
		String ftpUrl = "ftp://user:psw@" + GlobalData.ftpServerName + "/" + fileName + ".xlsx";
		JSONArray eMailList = reportAccessTypeEMail.getJSONArray(GlobalData.deliverToStr);
		MailAPI mailAPI = new MailAPI();
		if (reportAccessTypeEMail.has(GlobalData.subjectStr)) {
			mailAPI.setSubject(reportAccessTypeEMail.getString(GlobalData.subjectStr));
		}
		if (reportAccessTypeEMail.has(GlobalData.descriptionStr)) {
			mailAPI.setText(reportAccessTypeEMail.getString(GlobalData.descriptionStr));
		}
		mailAPI.addRecipients(eMailList);
		mailAPI.setText(ftpUrl);
		mailAPI.send();
		logger.info("E-Mail Sent");
	}

	public void reportAccessTypeFile(XSSFWorkbook localWB, JSONObject reportAccessTypeFile, String fileName) {
		FileOutputStream out;
		try {
			out = new FileOutputStream(
					reportAccessTypeFile.getString(GlobalData.filePathStr) + "//" + fileName + ".xlsx");
			localWB.write(out);
			out.close();
		} catch (FileNotFoundException e) {
			logger.info("Save path write error!!");
			e.printStackTrace();
		} catch (IOException e) {
			logger.info("Save path write error!!");
			e.printStackTrace();
		}
	}

	private void formatExcelSheet() {
		if (columnSize != -1) {
			for (int i = 0; i < configObj.length(); i++) {
				try {
					// Char size
					sheet.setColumnWidth(i, columnSize * 256);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			for (int i = 0; i < configObj.length(); i++) {
				try {
					sheet.autoSizeColumn(i);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void readTypeConfig() {
		for (int i = 0; i < configObj.length(); i++) {
			JSONObject headerJSON = (JSONObject) configObj.get(i);
			String type = headerJSON.getString(GlobalData.typeStr);
			if (type.equals(GlobalData.doubleStr)) {
				typeMapping.addElement(GlobalData.doubleStr);
			} else if (type.equals(GlobalData.longStr)) {
				typeMapping.addElement(GlobalData.longStr);
			} else if (type.equals(GlobalData.stringStr)) {
				typeMapping.addElement(GlobalData.stringStr);
			} else
				logger.error("Error parsing config data type!");
		}
	}

	private void writeToCell(int colIndex, String contentValue, XSSFCell cell) {
		try {
			String dataType = typeMapping.get(colIndex);
			switch (dataType) {
			case GlobalData.doubleStr: {
				if (contentValue == null || contentValue.equals(nullValue.toLowerCase())
						|| contentValue.equals(nullValue.toUpperCase()) || contentValue.equals(""))
					contentValue = "0";
				double tempValue = Double.parseDouble(contentValue);
				cell.setCellValue(tempValue);
				cell.setCellType(XSSFCell.CELL_TYPE_NUMERIC);
				cell.setCellStyle(double_data_style);
				cell = null;
				break;
			}
			case GlobalData.longStr: {
				if (contentValue == null || contentValue.equals(nullValue.toLowerCase())
						|| contentValue.equals(nullValue.toUpperCase()) || contentValue.equals(""))
					contentValue = "0";
				long tempValue = Long.parseLong(contentValue);
				cell.setCellValue(tempValue);
				cell.setCellType(XSSFCell.CELL_TYPE_NUMERIC);
				cell = null;
				break;
			}
			case GlobalData.stringStr: {
				if (contentValue == null)
					contentValue = "";
				cell.setCellValue(contentValue);
				cell = null;
				break;
			}
			default:
				logger.error("Error parsing data type!");
				cell = null;
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		}
	}

	private String doubleStringFormatGenerator(int precision) {
		String ret = "0.";
		for (int i = 0; i < precision; i++)
			ret += "0";
		return ret;
	}

}
