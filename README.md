[Smap fieldTask](http://www.smap.com.au) 
======

This repository has been deprecated.  fieldTask has been migrated from Eclipse to Android Studio and is being maintained in a new repository [https://github.com/nap2000/fieldTask2](https://github.com/nap2000/fieldTask2)

fieldTask is an Android client for Smap Server that extends [odkCollect](http://opendatakit.org/use/collect/) with Task Management functionality. It depends on a modified
version of odkCollect referenced as a library.

Follow the latest news about Smap on our [blog](http://blog.smap.com.au) and on twitter [@dgmsot](https://twitter.com/dgmsot).

##### How to install and run
* Import as GIT project into Eclipse
* Import smap version of ODK library as a GIT project
* Import smap version of playservices as a GIT project
* Open the properties of ODK1.4_lib and select Java Build Path then the Order and Export Tab
* Uncheck "Android Private Libraries"
* Clean the ODK1.4_lib project
* Select fieldTask and run as an Android application

Instructions on installing a Smap server can be found in the operations manual [here](http://www.smap.com.au/downloads.shtml)


