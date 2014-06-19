[Smap fieldTask](http://www.smap.com.au) 
======

fieldTask is an Android client for Smap Server that extends [odkCollect](http://opendatakit.org/use/collect/) with Task Management functionality. It depends on a modified
version of odkCollect referenced as a library.

Follow the latest news about Smap on our [blog](http://blog.smap.com.au) and on twitter [@dgmsot](https://twitter.com/dgmsot).

Frequently Asked Questions
---------------------------
##### How to install and run
* Import as GIT project into Eclipse
* Import smap version of ODK library as a GIT project
* Import smap version of playservices as a GIT project
* Open the properties of ODK1.4_lib and select Java Build Path then the Order and Export Tab
* Uncheck "Android Private Libraries"
* Clean the ODK1.4_lib project
* Select fieldTask and run as an Android application

Instructions on installing a Smap server can be found in the operations manual [here](http://www.smap.com.au/downloads.shtml)

Task Management 
---------------

A user of fieldTask can be assigned tasks to complete as per this [video](http://www.smap.com.au/taskManagement.shtml). 

Future Directions
-----------------

Smap Server supports completing surveys using web forms as well as on Android devices.  This isn't as mature as as the Android client however it does already support an updated version of the Task Management API. 

##### Get existing survey data as an XForm instance XML file
https://hostname/instanceXML/{survey id}/0?datakey={key name}&datakeyvalue={value of key}

##### Update existing results
https://{hostname}/submission/{instanceid}

Note the instance id of the existing data is included in the instanceXML.  It should be replaced with a new instance id before the results are submitted. However the instance id of the data to be replaced needs to be included in teh submission URL.

This API allows you to maintain data using surveys. In the following video the data is published on a map, however it could also be published in a table as a patient registry or list of assets. fieldTask needs to be customised to access these links using the data keys in a similar way to web forms.

[![ScreenShot](http://img.youtube.com/vi/FUNPOmMnt1I/0.jpg)](https://www.youtube.com/watch?v=FUNPOmMnt1I)

Development
-----------
* Code contributions are very welcome. 
* [Issue Tracker](https://github.com/smap-consulting/fieldTask/issues)

