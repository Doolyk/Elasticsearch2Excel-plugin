# Elasticsearch2Excel Plugin (Enhanced version)
##Elasticsearch2Excel Plugin to Generate Excel Reports

This plugin was originally developed by Raghavendar (https://github.com/raghavendar-ts/). This enhanced version of the software provides the following new features: 
<ol>
<li>Report generation for Excel’s version >=2007. Creating xlsx files brings the advantage that files can contain more than 65K rows;</li>

<li>Possibility to configure the column’s width;</li>

<li>Bug fixing for the Mapping functionality (i.e., sometimes the mapping fails due to additional space characters in the field values);</li>

<li>Possibility to configure the data format in the Excel (see example below);</li>

<li>Possibility to notify the report generation to a set of recipients via email (see example below).</li>

</ol>

___
####Installing and Removing the Plugin : 
Go to ES_HOME/bin

__Command to Install :__
<pre>
 plugin --install esreport --url https://github.com/Doolyk/Elasticsearch2Excel-plugin/blob/master/target/releases/es-report-plugin-2.0-SNAPSHOT.zip?raw=true
</pre>
__Command to Remove :__
<pre>
plugin --remove esreport
</pre>


####Operations: 

> **Note:** 
For the original documentation of available functionalities, please refer to https://github.com/raghavendar-ts/Elasticsearch-Report-Plugin. 

####The plugin URL
The plugin is available at the following URL:
<pre>
http://server_ip:port/_report
e.g. http://localhost:9200/_report
</pre>

####The columnSize parameter

It is possible to define column's width by adding the <b>columnSize</b> parameter to the JSON configuration file.
> **Note:**

> - Setting columnSize=-1 will enable the columnAutoSize feature of the Excel report generator. Unfortunately, this functionality is very slow for reports containing big amounts of data.
> - This is an optional parameter (default: -1)

####Defining Excel Data Formats
It is possible to specify data formats in the Excel report by adding the <b>type</b> parameter to each report's field. <b>This parameter is *MANDATORY*.</b> Here is the list of the Data Types:

|Data Type | Notes|
|---|-----------|
|string| Standard String type. Note: this type is used also for datetime values.|
|long| Standard long type|
|double| This type allows to specify the number of decimal digits for a double field. The <b>double_precision</b> configuration parameter* is optional (default: 2) |

* In the current version <i>all</i> the double fields stored in the same report share the same precision value. 


####Detailed Example with Sample Data :

__Sample HTTP Request to the Plugin :__ 
<pre>
POST http://localhost:9200/_report
{
  "reportTitle": "Report One",
  "reportName": "MyReport",
  "reportAccess": {
    "fileName": "Report_1",
    "ftp": {
      "filePath": "/path/to/file"
    },
            "email": {
            "subject": "Email subject",
            "description": "Description",
            "deliverTo": [
                "email_address_1",
                "email_address_2"
            ]
        }
  },
  "batchSize": 1000,
  "columnSize": 10,
  "index": "sample_index",
  "type": "sample_type",
  "double_precision": 3,
  "statement": {
    "query": {
      "bool": {
        "must": [
          {
            "match_all": {}
          }
        ]
      }
    },
    "fields": [
      "text",
      "created_at",
      "count",
      "value"
    ]
  },
  "config": [
    {
      "title": "text",
      "type": "string",
      "format": "[0,text]"
    },
    {
      "title": "created_at",
      "type": "string",
      "format": "[0,created_at]"
    },
    {
      "title": "count",
      "type": "long",
      "format": "[0,count]"
    },
    {
      "title": "value",
      "type": "double",
      "format": "[0,value]"
    }
  ]
}
