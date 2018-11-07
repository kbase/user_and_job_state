FROM kbase/sdkbase2:latest as build


COPY . /tmp/ujs
COPY deployment /kb/deployment

RUN cd /tmp && \
    git clone https://github.com/kbase/jars && \
    cd /tmp/ujs && \
    make build-libs build-docs && \
	ant buildwar && \
	[ -e deployment/lib ] || mkdir deployment/lib  && \
	[ -e deployment/jettybase/webapps ] || mkdir -p deployment/jettybase/webapps  && \
    [ -e deployment/jettybase/logs ] || mkdir -p deployment/jettybase/logs  && \
	[ -e deployment/jettybase/start.d ] || mkdir -p deployment/jettybase/start.d  && \
	cp dist/UserAndJobStateService.war deployment/jettybase/webapps/root.war 

FROM kbase/kb_jre

# These ARGs values are passed in via the docker build command
ARG BUILD_DATE
ARG VCS_REF
ARG BRANCH=develop

COPY deployment/ /kb/deployment/

ENV KB_DEPLOYMENT_CONFIG /kb/deployment/conf/deployment.cfg

# The BUILD_DATE value seem to bust the docker cache when the timestamp changes, move to
# the end
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.vcs-url="https://github.com/kbase/user_and_job_state.git" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.schema-version="1.0.0-rc1" \
      us.kbase.vcs-branch=$BRANCH \
      maintainer="Steve Chan sychan@lbl.gov"

EXPOSE 7058
ENTRYPOINT [ "/kb/deployment/bin/dockerize" ]
CMD [ "-template", "/kb/deployment/conf/.templates/deployment.cfg.templ:/kb/deployment/conf/deployment.cfg", \
      "-template", "/kb/deployment/conf/.templates/http.ini.templ:/kb/deployment/jettybase/start.d/http.ini", \
      "-template", "/kb/deployment/conf/.templates/server.ini.templ:/kb/deployment/jettybase/start.d/server.ini", \
      "-template", "/kb/deployment/conf/.templates/start_server.sh.templ:/kb/deployment/bin/start_server.sh", \
      "-stdout", "/kb/deployment/jettybase/logs/request.log", \
      "/kb/deployment/bin/start_server.sh" ]

WORKDIR /kb/deployment/jettybase

