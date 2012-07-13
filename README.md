GLASSFISH-18861
===============

[Bug Description]
After setting context root of two wars which deployed on the server target with the same value,both of the two wars accessed failed.

[Operations]
1Deploy two wars called test_sample1.war and test_sample2.war to the server target and ensure that the two wars can be accessed.
(asadmin deploy test_sample1.war)
(asadmin deploy test_sample2.war)

2.From admin gui to edit the configuration of test_sample2.war and change the context root's value of test_sample2.war into "/test_sample1",and then the context root's value of test_sample2.war is the same as test_sample1.war successfully.

Note: when looking server.log, the following error messages appeared:

[#|2012-07-03T13:19:16.984+0800|WARNING|44.0|javax.enterprise.system.container.web.com.sun.enterprise.web|_ThreadID=73;_ThreadName=Thread-2;|java.lang.Exception: WEB0113: Virtual server [server] already has a web module [test_sample1] loaded at [/test_sample1]; therefore web module [test_sample2] cannot be loaded at this context path on this virtual server. 
java.lang.Exception: WEB0113: Virtual server [server] already has a web module [test_sample1] loaded at [/test_sample1]; therefore web module [test_sample2] cannot be loaded at this context path on this virtual server. 

3.When you accessed "http://localhost:8080/test_sample1",the error message of "HTTP Status 404 - Not Found" happened.

4.The more serious problem is that,from admin gui,the modified war(test_sample2) can't be undeployed and there is no any useful message to tell user why the war can't be undeployed.

Note: when looking server.log again, the following stacktrace appeared:
[#|2012-07-03T13:22:40.968+0800|SEVERE|44.0|javax.enterprise.system.tools.admin.org.glassfish.deployment.admin|_ThreadID=69;_ThreadName=Thread-2;|Application not registered|#]

[#|2012-07-03T13:22:40.968+0800|INFO|44.0|javax.enterprise.system.tools.admin.org.glassfish.deployment.admin|_ThreadID=69;_ThreadName=Thread-2;|PostUndeployCommand starting|#]

[#|2012-07-03T13:22:40.968+0800|INFO|44.0|javax.enterprise.system.tools.admin.org.glassfish.deployment.admin|_ThreadID=69;_ThreadName=Thread-2;|PostUndeployCommand done successfully|#]

[Bug Level Analyse]
There is the following use case that shows the problem will not be acceptable by user.

UseCase: if user has deployed an large economic application on GF Server, this economic application can work effectively until another 
application is deployed and some administrator changed the latter application's context root's value with the economic application's context root's value. 
Then the economic application can't work any longer!

[Affected versions ]
1 4.0_b41
2 gf's trunk until 2012/06/22


 

