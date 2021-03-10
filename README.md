# Reference Checker Tool
This tool checks the reference integrity of Liferay database and display the existing inconsistencies.
It also allows viewing the table relations of Liferay data model. 

**Disclaimer:** This tool is not officially supported by Liferay Inc. or its affiliates. Use it under your responsibility: false positives can be returned.

## Download

Download it from https://github.com/jorgediaz-lr/reference-checker/releases page

There are two versions available: command line and portlet version
  - Command line tool: `references-checker-cmd-n.n.n.zip`
  - Portlet version for 6.1 and 6.2: `references-checker-portlet-6x-n.n.n.war`
  - Portlet version for 7.x: `references-checker-portlet-7x-n.n.n.war`

Command line tool version is the recommended one!!

 ## How does it work?

 - It has an internal file configuration with the rules about Liferay model
 - There are general rules (for example: columns must map with the values of the table with the primary key with same name)
 - ...and specific ones (for example: JournalArticle articleId maps with JournalArticleResource)
 - Using the defined rules and the existing database, a relation list is created.
 
 ## Checking the reference integrity of Liferay database:

Execution:
 - **Command line:** Execute script `./references-checker -m` (Linux / Unix) or `references-checker.bat -m` (Windows)
 - **Portlet version:** Open portlet in "Control panel => Apps" and click "Execute"

For each column, a SQL that checks invalid values is executed: select column1 from table1 where column1 not in (select column2 from table2)
Wrong values will be written to the output

 ## Viewing all Liferay data model relations:

Execution:
 - **Command line:** Command line: Execute script `./references-checker -r` (Linux / Unix) or references-checker.bat -r (Windows)
 - **Command line:** Portlet version: Open portlet in "Control panel => Apps" and click "Get relations list"
 
All defined relations for each table  will be written to the output


