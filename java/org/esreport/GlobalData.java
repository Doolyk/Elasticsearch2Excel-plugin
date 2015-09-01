package org.esreport;

public class GlobalData {
	
	public static final String engineName = "JavaScript";

	//JSON parameters
	public static final String doubleStr = "double";
	public static final String longStr = "long";
	public static final String stringStr = "string";
	
	public static final String decimalStr = "double_precision";
	public static final String columnSizeStr = "columnSize";
	
	public static final String valueMappingStr = "valueMapping";
	public static final String routingStr = "routing";
	public static final String batchSizeStr = "batchSize";
	public static final String nullValueStr = "nullValue";
	public static final String defaultStr = "default";
	public static final String reportAccessStr = "reportAccess";
	public static final String formatStr = "format";
	
	public static final String startIndexStr = "startIndex";
	public static final String endIndexStr = "endIndex";
	
	public static final String reportFileConfigStr = "ftp";
	public static final String filePathStr = "filePath";
	
	public static final String indexStr = "index";
	public static final String typeStr = "type";
	public static final String configStr = "config";
	public static final String statementStr = "statement";
	public static final String reportTitleStr = "reportTitle";
	
	//Mail cfg
	public static final String ftpServerName = "myhost.dom";
	public static final String emailStr = "email";
	public static final String deliverToStr = "deliverTo";
	public static final String subjectStr = "subject";
	public static final String descriptionStr = "description";
	
	//Default values for parameters
	public static int columnSize = -1; //-1: autoSize
	public static int doublePrecision = 2;
	public static int batchsize = 5000;
	public static final String nullValue = "NULL";
	
}
