User and Job State service
==========================

Service to maintain global user state and job status

RUNTIME REQUIREMENTS
--------------------

mongo 2.4.3+ required.

SETUP
-----

1. make
2. if you want to run tests:  
    1. MongoDB must be installed, but not necessarily running.  
    2. Copy ./test.cfg.example to ./test.cfg  
    2. fill in ./test.cfg  
    3. make test  
3. A mongodb instance must be up and running.
5. fill in deploy.cfg
6. make deploy
7. optionally, set KB_DEPLOYMENT_CONFIG appropriately
8. /kb/deployment/services/user_and_job_state/start_service

If the server doesn't start up correctly, check /var/log/syslog and
/kb/deployment/services/user_and_job_state/glassfish_domain/UserAndJobState/
  logs/server.log for debugging information, assuming the deploy is in the
default location.
