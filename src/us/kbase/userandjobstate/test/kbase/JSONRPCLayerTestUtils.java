package us.kbase.userandjobstate.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.slf4j.LoggerFactory;

import com.mongodb.DB;

import us.kbase.auth.AuthToken;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.mongo.exceptions.InvalidHostException;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple13;
import us.kbase.common.service.Tuple14;
import us.kbase.common.service.Tuple2;
import us.kbase.common.service.Tuple3;
import us.kbase.common.service.Tuple5;
import us.kbase.common.service.Tuple7;
import us.kbase.common.test.TestCommon;
import us.kbase.userandjobstate.CreateJobParams;
import us.kbase.userandjobstate.ListJobsParams;
import us.kbase.userandjobstate.Result;
import us.kbase.userandjobstate.Results;
import us.kbase.userandjobstate.UserAndJobStateClient;
import us.kbase.userandjobstate.UserAndJobStateServer;
import us.kbase.userandjobstate.test.FakeJob;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;
import us.kbase.workspace.test.WorkspaceTestCommon;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

public class JSONRPCLayerTestUtils {

	protected static String CHAR101 = "";
	protected static String CHAR1001 = "";
	static {
		String hundred = "";
		for (int i = 0; i < 10; i++) {
			hundred += "0123456789";
		}
		CHAR101 = hundred + "a";
		String thousand = "";
		for (int i = 0; i < 10; i++) {
			thousand += hundred;
		}
		CHAR1001 = thousand + "a";
	}
	
	static {
		//stfu Jetty
		((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
				.setLevel(Level.OFF);
	}
	
	public static UserAndJobStateServer startUpUJSServer(String mongohost,
			String wsurl, String dbname, AuthToken t, String pwd)
			throws Exception {
		//write the server config file:
		File iniFile = File.createTempFile("test", ".cfg",
				new File(TestCommon.getTempDir()));
		iniFile.deleteOnExit();
		System.out.println("Created temporary config file: " +
				iniFile.getAbsolutePath());
		Ini ini = new Ini();
		Section ws = ini.add("UserAndJobState");
		ws.add("mongodb-host", mongohost);
		ws.add("mongodb-database", dbname);
		ws.add("mongodb-user", "foo");
		ws.add("mongodb-pwd", "foo");
		ws.add("auth-service-url", TestCommon.getAuthUrl());
		ws.add("globus-url", TestCommon.getGlobusUrl());
		if (pwd == null) {
			ws.add("kbase-admin-token", t.getToken());
		} else {
			ws.add("kbase-admin-user", t.getUserName());
			ws.add("kbase-admin-pwd", pwd);
		}
		if (wsurl != null) {
			ws.add("workspace-url", wsurl);
		}
		ini.store(iniFile);
		
		//set up env
		Map<String, String> env = TestCommon.getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "UserAndJobState");

		UserAndJobStateServer serv = new UserAndJobStateServer();
		new UJSServerThread(serv).start();
		System.out.println("Main thread waiting for UJS server to start up");
		while(serv.getServerPort() == null) {
			Thread.sleep(1000);
		}
		return serv;
	}
	
	protected static class UJSServerThread extends Thread {

		private final UserAndJobStateServer server;
		
		public UJSServerThread(UserAndJobStateServer server) {
			this.server = server;
		}
		
		public void run() {
			try {
				server.startupServer();
			} catch (Exception e) {
				System.err.println("Can't start server:");
				e.printStackTrace();
			}
		}
	}
	
	//TODO ZZLATER make the JSONRPCLayerTester method public & use
	public static WorkspaceServer startupWorkspaceServer(String mongohost,
			String dbname, String typedb, AuthToken t1, String pwd1,
			String user2)
			throws InvalidHostException, UnknownHostException, IOException,
			NoSuchFieldException, IllegalAccessException, Exception,
			InterruptedException {
		DB db = GetMongoDB.getDB(mongohost, dbname);
		WorkspaceTestCommon.initializeGridFSWorkspaceDB(db, typedb);
		
		//write the server config file:
		File iniFile = File.createTempFile("test", ".cfg",
				new File(TestCommon.getTempDir()));
		if (iniFile.exists()) {
			iniFile.delete();
		}
		System.out.println("Created temporary config file: " +
		iniFile.getAbsolutePath());
		Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		ws.add("mongodb-host", mongohost);
		ws.add("mongodb-database", db.getName());
		ws.add("backend-secret", "foo");
		ws.add("auth-service-url", TestCommon.getAuthUrl());
		ws.add("globus-url", TestCommon.getGlobusUrl());
		ws.add("ws-admin", user2);
		if (pwd1 == null) {
			ws.add("kbase-admin-token", t1.getToken());
		} else {
			ws.add("kbase-admin-user", t1.getUserName());
			ws.add("kbase-admin-pwd", pwd1);
		}
		ws.add("temp-dir", Paths.get(TestCommon.getTempDir())
				.resolve("tempForWorkspaceForUJSAuthTest"));
		ws.add("ignore-handle-service", "true");
		ini.store(iniFile);
		iniFile.deleteOnExit();
		
		//set up env
		Map<String, String> env = TestCommon.getenv();
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.getAbsolutePath());
		env.put("KB_SERVICE_NAME", "Workspace");

		WorkspaceServer.clearConfigForTests();
		WorkspaceServer server = new WorkspaceServer();
		new WSServerThread(server).start();
		System.out.println(
				"Main thread waiting for Workspace server to start up");
		while (server.getServerPort() == null) {
			Thread.sleep(1000);
		}
		return server;
	}
	
	protected static class WSServerThread extends Thread {
		private WorkspaceServer server;
		
		protected WSServerThread(WorkspaceServer server) {
			this.server = server;
		}
		
		public void run() {
			try {
				server.startupServer();
			} catch (Exception e) {
				System.err.println("Can't start server:");
				e.printStackTrace();
			}
		}
	}
	
	private SimpleDateFormat getDateFormat() {
		SimpleDateFormat dateform =
				new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		dateform.setLenient(false);
		return dateform;
	}
	
	protected void checkJob(UserAndJobStateClient cli, String id, String owner,
			String canceledby, String stage, String status, String service,
			String desc, String progtype, Long prog, Long maxprog,
			String estCompl, Long complete, Long error, String errormsg,
			Results results, String authStrat, String authParam,
			Map<String, String> meta)
			throws Exception {
		
		Tuple13<String, Tuple2<String, String>, String, String, String,
			Tuple3<String, String, String>, Tuple3<Long, Long, String>, Long,
			Long, Tuple2<String, String>, Map<String, String>, String,
			Results> ret = cli.getJobInfo2(id);
		String s = " for job " + id;
		SimpleDateFormat dateform = getDateFormat();
		
		assertThat("job id ok" + s, ret.getE1(), is(id));
		
		Tuple2<String, String> users = ret.getE2();
		assertThat("job owner ok", users.getE1(), is(owner));
		assertThat("job canceled by ok", users.getE2(), is(canceledby));
		
		assertThat("job service ok" + s, ret.getE3(), is(service));
		assertThat("job stage ok" + s, ret.getE4(), is(stage));
		assertThat("job status ok" + s, ret.getE5(), is(status));
		
		Tuple3<String, String, String> dates = ret.getE6();
		if (dates.getE1() != null) {
			dateform.parse(dates.getE1()); // should throw error if bad format
		}
		dateform.parse(dates.getE2()); // should throw error if bad format
		assertThat("job est comp ok" + s, dates.getE3(), is(estCompl));
		
		Tuple3<Long, Long, String> progt = ret.getE7();
		assertThat("job prog ok" + s, progt.getE1(), is(prog));
		assertThat("job maxprog ok" + s, progt.getE2(), is(maxprog));
		assertThat("job prog type ok" + s, progt.getE3(), is(progtype));
		
		assertThat("job complete ok" + s, ret.getE8(), is(complete));
		assertThat("job error ok" + s, ret.getE9(), is(error));
		
		Tuple2<String, String> auth = ret.getE10();
		assertThat("job authstrat ok" + s, auth.getE1(), is(authStrat));
		assertThat("job authparam ok" + s, auth.getE2(), is(authParam));
		
		assertThat("job meta ok" + s, ret.getE11(), is(meta));
		assertThat("job desc ok" + s, ret.getE12(), is(desc));
		checkResults(ret.getE13(), results);
		
		checkJob(cli, id, stage, status, service, desc, progtype, prog,
				maxprog, estCompl, complete, error, errormsg, results);
	}
	
	private void checkJob(UserAndJobStateClient cli, String id, String stage,
			String status, String service, String desc, String progtype,
			Long prog, Long maxprog, String estCompl, Long complete, 
			Long error, String errormsg, Results results)
			throws Exception {
		SimpleDateFormat dateform = getDateFormat();
		@SuppressWarnings("deprecation")
		Tuple14<String, String, String, String, String, String,
				Long, Long, String, String, Long, Long, String,
				Results> ret = cli.getJobInfo(id);
		String s = " for job " + id;
		assertThat("job id ok" + s, ret.getE1(), is(id));
		assertThat("job service ok" + s, ret.getE2(), is(service));
		assertThat("job stage ok" + s, ret.getE3(), is(stage));
		if (ret.getE4() != null) {
			dateform.parse(ret.getE4()); //should throw error if bad format
		}
		assertThat("job status ok" + s, ret.getE5(), is(status));
		dateform.parse(ret.getE6()); //should throw error if bad format
		assertThat("job prog ok" + s, ret.getE7(), is(prog));
		assertThat("job maxprog ok" + s, ret.getE8(), is(maxprog));
		assertThat("job progtype ok" + s, ret.getE9(), is(progtype));
		assertThat("job est compl ok" + s, ret.getE10(), is(estCompl));
		assertThat("job complete ok" + s, ret.getE11(), is(complete));
		assertThat("job error ok" + s, ret.getE12(), is(error));
		assertThat("job desc ok" + s, ret.getE13(), is(desc));
		checkResults(ret.getE14(), results);
		
		Tuple5<String, String, Long, String, String> jobdesc =
				cli.getJobDescription(id);
		assertThat("job service ok" + s, jobdesc.getE1(), is(service));
		assertThat("job progtype ok" + s, jobdesc.getE2(), is(progtype));
		assertThat("job maxprog ok" + s, jobdesc.getE3(), is(maxprog));
		assertThat("job desc ok" + s, jobdesc.getE4(), is(desc));
		if (jobdesc.getE5() != null) {
			dateform.parse(jobdesc.getE5()); //should throw error if bad format
		}
		
		Tuple7<String, String, String, Long, String, Long, Long> 
				jobstat = cli.getJobStatus(id);
		dateform.parse(jobstat.getE1()); //should throw error if bad format
		assertThat("job stage ok" + s, jobstat.getE2(), is(stage));
		assertThat("job status ok" + s, jobstat.getE3(), is(status));
		assertThat("job progress ok" + s, jobstat.getE4(), is(prog));
		assertThat("job est compl ok" + s, jobstat.getE5(), is(estCompl));
		assertThat("job complete ok" + s, jobstat.getE6(), is(complete));
		assertThat("job error ok" + s, jobstat.getE7(), is(error));
		
		checkResults(cli.getResults(id), results);
		
		assertThat("job error msg ok" + s, cli.getDetailedError(id),
				is(errormsg));
	}
	
	private void checkResults(Results got, Results expected) throws Exception {
		if (got == null & expected == null) {
			return;
		}
		if (got == null ^ expected == null) {
			fail("got null for results when expected real results or vice versa: " 
					+ got + " " + expected);
		}
		assertThat("shock ids same", got.getShocknodes(), is(expected.getShocknodes()));
		assertThat("shock url same", got.getShockurl(), is(expected.getShockurl()));
		assertThat("ws ids same", got.getWorkspaceids(), is(expected.getWorkspaceids()));
		assertThat("ws url same", got.getWorkspaceurl(), is(expected.getWorkspaceurl()));
		if (got.getResults() == null ^ expected.getResults() == null) {
			fail("got null for results.getResults() when expected real results or vice versa: " 
					+ got + " " + expected);
		}
		if (got.getResults() == null) {return;}
		if (got.getResults().size() != expected.getResults().size()) {
			fail("results lists not same size");
		}
		Iterator<Result> gr = got.getResults().iterator();
		Iterator<Result> er = expected.getResults().iterator();
		while (gr.hasNext()) {
			Result gres = gr.next();
			Result eres = er.next();
			assertThat("server type same", gres.getServerType(), is(eres.getServerType()));
			assertThat("url same", gres.getUrl(), is(eres.getUrl()));
			assertThat("id same", gres.getId(), is(eres.getId()));
			assertThat("description same", gres.getDescription(), is(eres.getDescription()));
		}
	}

	protected static void failGetJob(UserAndJobStateClient cli, String jobid,
			String exception)
			throws Exception {
		try {
			@SuppressWarnings({ "deprecation", "unused" })
			Tuple14<String, String, String, String, String, String, Long, Long,
				String, String, Long, Long, String, Results> foo =
				cli.getJobInfo(jobid);
			fail("got job with bad id");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			cli.getJobInfo2(jobid);
			fail("got job with bad id");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			cli.getJobDescription(jobid);
			fail("got job with bad id");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			cli.getJobStatus(jobid);
			fail("got job with bad id");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			cli.getResults(jobid);
			fail("got job with bad id");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
		try {
			cli.getDetailedError(jobid);
			fail("got job with bad id");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	protected void failShareUnshareJob(UserAndJobStateClient cli, String id,
			List<String> users, String exception)
			throws Exception {
		failShareJob(cli, id, users, exception);
		failUnshareJob(cli, id, users, exception);
	}
	
	protected void failShareJob(UserAndJobStateClient cli, String id,
			List<String> users, String exception)
			throws Exception {
		try {
			cli.shareJob(id, users);
			fail("shared job w/ bad args");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}
	
	protected void failUnshareJob(UserAndJobStateClient cli, String id,
			List<String> users, String exception)
			throws Exception {
		try {
			cli.unshareJob(id, users);
			fail("unshared job w/ bad args");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}

	@SuppressWarnings("deprecation")
	protected void checkListJobs(UserAndJobStateClient cli, String service, String filter,
			Set<FakeJob> expected) throws Exception {
		for (String authStrat: Arrays.asList(null, "", "DEFAULT")) {
			checkListJobs2(cli, service, filter, expected, authStrat,
					Arrays.asList("1"));
		}
		
		Set<FakeJob> got = new HashSet<FakeJob>();
		for (Tuple14<String, String, String, String, String, String, Long,
				Long, String, String, Long, Long, String, Results> ji: 
					cli.listJobs(Arrays.asList(service), filter)) {
			got.add(new FakeJob(ji));
		}
		Set<FakeJob> v1 = new HashSet<FakeJob>();
		for (FakeJob fj: expected) {
			v1.add(fj.asV1DB());
		}
		assertThat("got the correct jobs", got, is(v1));
	}

	protected void checkListJobs2(UserAndJobStateClient cli, String service,
			String filter, Set<FakeJob> expected, String authStrat,
			List<String> authparams)
			throws IOException, JsonClientException {
		Set<FakeJob> got = new HashSet<FakeJob>();
		//ew.
		for (Tuple13<String, Tuple2<String, String>, String, String, String,
				Tuple3<String, String, String>, Tuple3<Long, Long, String>,
				Long, Long, Tuple2<String, String>, Map<String, String>,
				String, Results> j: cli.listJobs2(new ListJobsParams()
					.withAuthstrat(authStrat)
					.withAuthparams(authparams)
					.withServices(Arrays.asList(service))
					.withFilter(filter))) {
			got.add(new FakeJob(j));
		}
		assertThat("got the correct jobs", got, is(expected));
	}

	@SuppressWarnings("deprecation")
	protected void failListJobs(UserAndJobStateClient cli, String service,
			String exception) throws Exception {
		try {
			cli.listJobs(Arrays.asList(service), "RCE");
			fail("list jobs worked w/ bad service");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
		failListJobs2(cli, service, "DEFAULT", Arrays.asList("1"), exception);
	}

	protected void failListJobs2(UserAndJobStateClient cli,
			String service, String authstrat, List<String> authparams,
			String exception) throws IOException,
			JsonClientException {
		try {
			cli.listJobs2(new ListJobsParams()
					.withServices(Arrays.asList(service)).withFilter("RCE")
					.withAuthstrat(authstrat).withAuthparams(authparams));
			fail("list jobs worked w/ bad service");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}

	protected static void failGetJobShared(UserAndJobStateClient cli, String id,
			String exception) throws Exception {
		try {
			cli.getJobShared(id);
			fail("got job shared list w/ bad args");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}

	protected static void failGetJobOwner(UserAndJobStateClient cli, String id,
			String exception) throws Exception {
		try {
			cli.getJobOwner(id);
			fail("got job owner w/ bad args");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}

	public static void setPermissions(WorkspaceClient wsc, long id, String perm,
			String user) throws Exception {
		wsc.setPermissions(new SetPermissionsParams().withId(id)
				.withNewPermission(perm)
				.withUsers(Arrays.asList(user)));
	}
	
	
	protected void failCancelJob(UserAndJobStateClient cli, String jobid,
			String status, String exception)
			throws Exception {
		try {
			cli.cancelJob(jobid, status);
			fail("canceled with bad args");
		} catch (ServerException se) {
			assertThat("correct exception", se.getMessage(), is(exception));
		}
		
	}
	
	protected static void deleteJob(
			final UserAndJobStateClient cli,
			final String id)
			throws Exception {
		try {
			cli.deleteJob(id);
		} catch (ServerException se) {
			System.out.println(se.getData());
			throw se;
		}
		failGetJob(cli, id, String.format(
				"There is no job %s viewable by user %s",
				id, cli.getToken().getUserName()));
	}
	
	protected static void deleteJob(
			final UserAndJobStateClient cli,
			final String id,
			final String service)
			throws Exception {
		try {
			cli.forceDeleteJob(service, id);
		} catch (ServerException se) {
			System.out.println(se.getData());
			throw se;
		}
		failGetJob(cli, id, String.format(
				"There is no job %s viewable by user %s",
				id, cli.getToken().getUserName()));
	}
	
	protected void failToDeleteJob(
			final UserAndJobStateClient cli,
			final String jobid,
			final String exception)
			throws Exception {
		failToDeleteJob(cli, jobid, null, exception, false);
	}
	
	protected void failToDeleteJob(
			final UserAndJobStateClient cli,
			final String jobid,
			final String token,
			final String exception)
			throws Exception {
		failToDeleteJob(cli, jobid, token, exception, false);
	}
	
	protected void failToDeleteJob(
			final UserAndJobStateClient cli, 
			final String jobid,
			final String token,
			final String exception,
			boolean usenulltoken)
			throws Exception {
		try {
			if (!usenulltoken && token == null) {
				cli.deleteJob(jobid);
			} else {
				cli.forceDeleteJob(token, jobid);
			}
			fail("deleted job with bad args");
		} catch (ServerException se) {
			assertThat("correct exception", se.getLocalizedMessage(),
					is(exception));
		}
	}

	protected static void failCreateJob(UserAndJobStateClient cli,
			String authstrat, String param, String exp)
			throws Exception {
		try {
			cli.createJob2(new CreateJobParams().withAuthstrat(authstrat)
					.withAuthparam(param));
			fail("created job with bad authstrat");
		} catch (ServerException e) {
			assertThat("incorrect exception message. Server trace: " +
					e.getData(), e.getLocalizedMessage(), is(exp));
		}
	}
}
