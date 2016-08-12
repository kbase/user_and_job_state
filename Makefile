#now set in deploy.cfg
SERVICE_PORT =
SERVICE = userandjobstate
SERVICE_CAPS = UserAndJobState
CLIENT_JAR = UserAndJobStateClient.jar
WAR = UserAndJobStateService.war
URL = https://kbase.us/services/userandjobstate/

#End of user defined variables

GITCOMMIT := $(shell git rev-parse --short HEAD)
TAGS := $(shell git tag --contains $(GITCOMMIT))

TOP_DIR = $(shell python -c "import os.path as p; print p.abspath('../..')")

TOP_DIR_NAME = $(shell basename $(TOP_DIR))

ifeq ($(TOP_DIR_NAME), dev_container)
include $(TOP_DIR)/tools/Makefile.common
endif

DEPLOY_RUNTIME ?= /kb/runtime
JAVA_HOME ?= $(DEPLOY_RUNTIME)/java
TARGET ?= /kb/deployment
SERVICE_DIR ?= $(TARGET)/services/$(SERVICE)
GLASSFISH_HOME ?= $(DEPLOY_RUNTIME)/glassfish3
SERVICE_USER ?= kbase

ASADMIN = $(GLASSFISH_HOME)/glassfish/bin/asadmin

ANT = ant

# make sure our make test works
.PHONY : test

default: build-libs build-docs

# fake deploy-cfg target for if this is run outside the dev_container
deploy-cfg:

ifeq ($(TOP_DIR_NAME), dev_container)
include $(TOP_DIR)/tools/Makefile.common.rules
endif

build-libs:
	@#TODO at some point make dependent on compile - checked in for now.
	$(ANT) compile
	
build-docs: build-libs
	-rm -r docs 
	$(ANT) javadoc
	pod2html --infile=lib/Bio/KBase/$(SERVICE)/Client.pm --outfile=docs/$(SERVICE)_perl.html
	rm -f pod2htm?.tmp
	cp $(SERVICE).spec docs/.
	cp docshtml/* docs/.

compile: compile-typespec compile-typespec-java compile-html

compile-html:
	kb-sdk compile --html --out docshtml $(SERVICE).spec

compile-typespec-java:
	kb-sdk compile  --java --javasrc src --javasrv --out . \
		--url $(URL) $(SERVICE).spec

compile-typespec:
	kb-sdk compile \
		--out lib \
		--jsclname javascript/$(SERVICE)/Client \
		--plclname Bio::KBase::$(SERVICE)::Client \
		--pyclname biokbase.$(SERVICE).client \
		--url $(URL) \
		$(SERVICE).spec

test: test-client test-service test-scripts
	
test-client: test-service
	$(ANT) test_client_import

test-service:
	test/cfg_to_runner.py $(TESTCFG)
	test/run_tests.sh

test-scripts:
	@echo "no scripts to test"

	
deploy: deploy-client deploy-service

deploy-client: deploy-client-libs deploy-docs deploy-scripts

deploy-client-libs:
	mkdir -p $(TARGET)/lib/
	cp dist/client/$(CLIENT_JAR) $(TARGET)/lib/
	cp -rv lib/* $(TARGET)/lib/
	echo $(GITCOMMIT) > $(TARGET)/lib/$(SERVICE).clientdist
	echo $(TAGS) >> $(TARGET)/lib/$(SERVICE).clientdist

deploy-docs:
	mkdir -p $(SERVICE_DIR)/webroot
	cp  -r docs/* $(SERVICE_DIR)/webroot/.

deploy-scripts:
	@echo no scripts to deploy

deploy-service: deploy-service-libs deploy-service-scripts deploy-cfg

deploy-service-libs:
	$(ANT) buildwar
	mkdir -p $(SERVICE_DIR)
	cp dist/$(WAR) $(SERVICE_DIR)
	echo $(GITCOMMIT) > $(SERVICE_DIR)/$(SERVICE).serverdist
	echo $(TAGS) >> $(SERVICE_DIR)/$(SERVICE).serverdist
	
deploy-service-scripts:
	cp server_scripts/glassfish_administer_service.py $(SERVICE_DIR)
	server_scripts/build_server_control_scripts.py $(SERVICE_DIR) $(WAR)\
		$(TARGET) $(JAVA_HOME) deploy.cfg $(ASADMIN) $(SERVICE_CAPS)\
		$(SERVICE_PORT)

deploy-upstart:
	echo "# $(SERVICE) service" > /etc/init/$(SERVICE).conf
	echo "# NOTE: stop $(SERVICE) does not work" >> /etc/init/$(SERVICE).conf
	echo "# Use the standard stop_service script as the $(SERVICE_USER) user" >> /etc/init/$(SERVICE).conf
	echo "#" >> /etc/init/$(SERVICE).conf
	echo "# Make sure to set up the $(SERVICE_USER) user account" >> /etc/init/$(SERVICE).conf
	echo "# shell> groupadd $(SERVICE_USER)" >> /etc/init/$(SERVICE).conf
	echo "# shell> useradd -r -g $(SERVICE_USER) $(SERVICE_USER)" >> /etc/init/$(SERVICE).conf
	echo "#" >> /etc/init/$(SERVICE).conf
	echo "start on runlevel [23] and started mongod" >> /etc/init/$(SERVICE).conf 
	echo "stop on runlevel [!23]" >> /etc/init/$(SERVICE).conf 
	echo "pre-start exec chown -R $(SERVICE_USER) $(TARGET)/services/$(SERVICE)" >> /etc/init/$(SERVICE).conf 
	echo "exec su $(SERVICE_USER) -c '$(TARGET)/services/$(SERVICE)/start_service'" >> /etc/init/$(SERVICE).conf 

undeploy:
	-rm -rf $(SERVICE_DIR)
	-rm -rfv $(TARGET)/lib/Bio/KBase/$(SERVICE)
	-rm -rfv $(TARGET)/lib/biokbase/$(SERVICE)
	-rm -rfv $(TARGET)/lib/javascript/$(SERVICE) 
	-rm -rfv $(TARGET)/lib/$(CLIENT_JAR)

create-db-update-script:
	$(ANT) updatescript

clean:
	-rm -rf docs
	-rm -rf dist
	@#TODO remove lib once files are generated on the fly
