package us.kbase.userandjobstate.test.jobstate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;
import com.mongodb.DBCollection;

import us.kbase.common.exceptions.UnimplementedException;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.schemamanager.SchemaManager;
import us.kbase.common.schemamanager.exceptions.IncompatibleSchemaException;
import us.kbase.common.test.RegexMatcher;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.userandjobstate.authorization.AuthorizationStrategy;
import us.kbase.userandjobstate.authorization.DefaultUJSAuthorizer;
import us.kbase.userandjobstate.authorization.UJSAuthorizer;
import us.kbase.userandjobstate.authorization.exceptions.UJSAuthorizationException;
import us.kbase.userandjobstate.jobstate.Job;
import us.kbase.userandjobstate.jobstate.JobResult;
import us.kbase.userandjobstate.jobstate.JobResults;
import us.kbase.userandjobstate.jobstate.JobState;
import us.kbase.userandjobstate.jobstate.exceptions.NoSuchJobException;
import us.kbase.userandjobstate.test.FakeJob;
import us.kbase.userandjobstate.test.UserJobStateTestCommon;
import us.kbase.workspace.database.WorkspaceUserMetadata;

public class JobStateTests {
	
	private static final String DB_NAME = "JobStateTests";

	private static MongoController mongo;
	
	private static DBCollection jobcol;
	private static DBCollection schemacol;
	private static JobState js;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		mongo = new MongoController(
				UserJobStateTestCommon.getMongoExe(),
				Paths.get(UserJobStateTestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " + mongo.getTempDir());
		
		final DB db = GetMongoDB.getDB(
				"localhost:" + mongo.getServerPort(), DB_NAME, 0, 0);
		jobcol = db.getCollection("jobstate");
		schemacol = db.getCollection("schema");
		js = new JobState(jobcol, new SchemaManager(schemacol));
				
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (mongo != null) {
			mongo.destroy(UserJobStateTestCommon.getDeleteTempFiles());
		}
	}
	
	@Before
	public void clearDB() throws Exception {
		DB db = GetMongoDB.getDB("localhost:" + mongo.getServerPort(),
				DB_NAME);
		UserJobStateTestCommon.destroyDB(db);
	}
	
	private static final RegexMatcher OBJ_ID_MATCH = new RegexMatcher("[\\da-f]{24}");
	
	private static final Date MAX_DATE = new Date(Long.MAX_VALUE);
	
	private final static String LONG101;
	private final static String LONG201;
	private final static String LONG1001;
	private final static String LONG100001;

	static {
		String l101 = "";
		String l201 = "";
		String l1001 = "";
		String l100001 = "";
		for (int i = 0; i < 5; i++) {
			l101 += "aaaaaaaaaabbbbbbbbbb";
		}
		l201 = l101 + l101;
		for (int i = 0; i < 5; i++) {
			l1001 += l201;
		}
		for (int i = 0; i < 100; i++) {
			l100001 += l1001;
		}
		LONG101 = l101 + "f";
		LONG201 = l201 + "f";
		LONG1001 = l1001 + "f";
		LONG100001 = l100001 + "f";
	}
	
	@Test
	public void checkSchema() throws Exception {
		/* this just tests that the schema manager is doing something.
		 * The schema manager tests should handle everything else.
		 */
		new SchemaManager(schemacol).setRecord("jobstate", 1, false);
		try {
			new JobState(jobcol, new SchemaManager(schemacol));
			fail("created job state with bad schema");
		} catch (IncompatibleSchemaException e) {
			assertThat("incorrect exception message", e.getLocalizedMessage(),
					is("Incompatible database schema for schema type " +
							"jobstate. DB is v1, codebase is v2"));
		}
	}
	
	@Test
	public void createJob() throws Exception {
		failCreateJob(null, "user cannot be null or the empty string");
		failCreateJob("", "user cannot be null or the empty string");
		String jobid = js.createJob("foo");
		assertThat("get job id", jobid, OBJ_ID_MATCH);
		Job j = js.getJob("foo", jobid);
		checkJob(j, jobid, "created", null, "foo", null, null, null, null,
				null, null, null, null, null, null);
		
		failGetJob("foo1", jobid, new NoSuchJobException(String.format(
							"There is no job %s viewable by user foo1",
							jobid)));
		failGetJob("foo", "a" + jobid.substring(1), new NoSuchJobException(
				String.format("There is no job %s viewable by user foo",
						"a" + jobid.substring(1))));
		failGetJob("foo", "a" + jobid, new IllegalArgumentException(
				String.format("Job ID %s is not a legal ID", "a" + jobid)));
		
		jobid = js.createJob("foo", new DefaultUJSAuthorizer(),
				UJSAuthorizer.DEFAULT_AUTH_STRAT,
				UJSAuthorizer.DEFAULT_AUTH_PARAM,
				new WorkspaceUserMetadata());
		assertThat("get job id", jobid, OBJ_ID_MATCH);
		j = js.getJob("foo", jobid);
		checkJob(j, jobid, "created", null, "foo", null, null, null, null,
				null, null, null, null, null, null);
		
		failGetJob("foo1", jobid, new NoSuchJobException(String.format(
							"There is no job %s viewable by user foo1",
							jobid)));
		failGetJob("foo", "a" + jobid.substring(1), new NoSuchJobException(
				String.format("There is no job %s viewable by user foo",
						"a" + jobid.substring(1))));
		failGetJob("foo", "a" + jobid, new IllegalArgumentException(
				String.format("Job ID %s is not a legal ID", "a" + jobid)));
	}
	
	@Test
	public void createJobAuth() throws Exception {
		WorkspaceUserMetadata mt = new WorkspaceUserMetadata();
		String user = "foo";
		
		failCreateJob(user, null, UJSAuthorizer.DEFAULT_AUTH_STRAT,
				UJSAuthorizer.DEFAULT_AUTH_PARAM, mt,
				new NullPointerException());
		
		failCreateJob(user, new DefaultUJSAuthorizer(),
				new AuthorizationStrategy("foo"),
				UJSAuthorizer.DEFAULT_AUTH_PARAM, mt,
				new UnimplementedException());
		
		failCreateJob(user, new DefaultUJSAuthorizer(),
				null,
				UJSAuthorizer.DEFAULT_AUTH_PARAM, mt,
				new NullPointerException());
		
		failCreateJob(user, new DefaultUJSAuthorizer(),
				UJSAuthorizer.DEFAULT_AUTH_STRAT,
				null, mt,
				new IllegalArgumentException(
						"authParam cannot be null or empty"));
		
		failCreateJob(user, new DefaultUJSAuthorizer(),
				UJSAuthorizer.DEFAULT_AUTH_STRAT,
				"", mt,
				new IllegalArgumentException(
						"authParam cannot be null or empty"));
		
		String id = js.createJob(user, new DefaultUJSAuthorizer(),
				new AuthorizationStrategy("DEFAULT"), "whoo", mt);
		
		FakeJob fj = new FakeJob(id, user, null, "created", null, null, null,
				null, null, null, null, null, null, null,
				new AuthorizationStrategy("DEFAULT"), "whoo",
				new HashMap<String, String>());
		checkJob(fj);
	}
	
	@Test
	public void alternateAuth() throws Exception {
		UJSAuthorizer lenientauth = new UJSAuthorizer() {
			
			@Override
			protected void externallyAuthorizeRead(AuthorizationStrategy strat,
					String user, List<String> authParams)
					throws UJSAuthorizationException {
				throw new UnimplementedException(); // will probably need to implement
			}
			
			@Override
			protected void externallyAuthorizeRead(AuthorizationStrategy strat,
					String user, String authParam, Job j)
					throws UJSAuthorizationException {
				if ("fail single".equals(authParam)) {
					throw new UJSAuthorizationException("fail single req");
				}
			}
			
			@Override
			protected void externallyAuthorizeCreate(
					AuthorizationStrategy strat, String authParam)
					throws UJSAuthorizationException {
				if ("fail create".equals(authParam)) {
					throw new UJSAuthorizationException("fail create req");
				}
			}
		};
		String user = "foo";
		WorkspaceUserMetadata mt = new WorkspaceUserMetadata();
		failCreateJob(user, lenientauth, new AuthorizationStrategy("foo"),
				"fail create", mt,
				new UJSAuthorizationException("fail create req"));
		failCreateJob(user, new DefaultUJSAuthorizer(),
				new AuthorizationStrategy("foo"),
				"fail create", mt, new UnimplementedException());
		
		String id = js.createJob(user, lenientauth,
				new AuthorizationStrategy("strat1"), "whee", mt);
		FakeJob fj = new FakeJob(id, user, null, "created", null, null, null,
				null, null, null, null, null, null, null,
				new AuthorizationStrategy("strat1"), "whee",
				new HashMap<String, String>());
		checkJob(lenientauth, fj);
		failShareJob(user, id, Arrays.asList("bar"),
				new NoSuchJobException(String.format(
						"There is no job %s with default authorization " +
						"owned by user %s", id, user)));
		failUnshareJob(user, id, Arrays.asList("foo"),
				new NoSuchJobException(String.format(
						"There is no job %s with default authorization " +
						"visible to user %s", id, user)));
		checkJob(lenientauth, fj, new LinkedList<String>());
		
		id = js.createJob(user, lenientauth,
				new AuthorizationStrategy("strat1"), "fail single", mt);
		failGetJob(user, id, new UnimplementedException());
		failGetJob(user, id, lenientauth,
				new NoSuchJobException(String.format(
						"There is no job %s viewable by user %s", id, user)));
	}
	
	private static void failCreateJob(String user, String exp)
			throws Exception {
		try {
			js.createJob(user);
			fail("created job with invalid params");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(), 
					is(exp));
		}
		try {
			js.createJob(user, new DefaultUJSAuthorizer(),
					UJSAuthorizer.DEFAULT_AUTH_STRAT,
					UJSAuthorizer.DEFAULT_AUTH_PARAM,
					new WorkspaceUserMetadata());
			fail("created job with invalid params");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(), 
					is(exp));
		}
	}
	
	private static void failCreateJob(String user, UJSAuthorizer auth,
			AuthorizationStrategy strat, String authParam,
			WorkspaceUserMetadata meta, Exception exp)
			throws Exception {
		try {
			js.createJob(user, auth, strat, authParam, meta);
			fail("created job with bad params");
		} catch (Exception e) {
			assertExceptionCorrect(e, exp);
		}
	}
	
	private static void assertExceptionCorrect(
			Exception got, Exception expected) {
		assertThat("incorrect exception. trace:\n" +
				ExceptionUtils.getStackTrace(got),
				got.getLocalizedMessage(),
				is(expected.getLocalizedMessage()));
		assertThat("incorrect exception type", got, is(expected.getClass()));
	}
	
	@Test
	public void metadata() throws Exception {
		String user = "foo";
		AuthorizationStrategy as = UJSAuthorizer.DEFAULT_AUTH_STRAT;
		String ap = UJSAuthorizer.DEFAULT_AUTH_PARAM;
		Map<String, String> m = new HashMap<String, String>();
		m.put("bar", "baz");
		m.put("whoo", "wee");
		String id = js.createJob(user, new DefaultUJSAuthorizer(),
				UJSAuthorizer.DEFAULT_AUTH_STRAT,
				UJSAuthorizer.DEFAULT_AUTH_PARAM,
				new WorkspaceUserMetadata(m));
		FakeJob fj = new FakeJob(id, user, null, "created", null, null, null,
				null, null, null, null, null, null, null, as, ap, m);
		checkJob(fj);
		js.startJob(user, id, "serv", "stat", "desc", null);
		fj = new FakeJob(id, user, "serv", "started", null, "desc", "none",
				null, null, "stat", false, false, null, null, as, ap, m);
		checkJob(fj);
		js.updateJob(user, id, "serv", "stat1", null, null);
		fj = new FakeJob(id, user, "serv", "started", null, "desc", "none",
				null, null, "stat1", false, false, null, null, as, ap, m);
		checkJob(fj);
		js.completeJob(user, id, "serv", "stat2", null, null);
		fj = new FakeJob(id, user, "serv", "complete", null, "desc", "none",
				null, null, "stat2", true, false, null, null, as, ap, m);
		checkJob(fj);
		
		failCreateJob(user, new DefaultUJSAuthorizer(), as, ap, null,
				new NullPointerException("meta"));
	}
	
	@Test
	public void startJob() throws Exception {
		Date nearfuture = new Date(new Date().getTime() + 10000);
		Date nearpast = new Date(new Date().getTime() - 10);
		String jobid = js.createJob("foo");
		js.startJob("foo", jobid, "serv1", "started job", "job desc", null);
		Job j = js.getJob("foo", jobid);
		checkJob(j, jobid, "started", null, "foo", "started job", "serv1",
				"job desc", "none", null, null, false, false, null, null);
		testStartJobBadArgs("foo", jobid, "serv2", "started job", "job desc", null,
				new NoSuchJobException(String.format(
						"There is no unstarted job %s for user foo", jobid)));
		testStartJobBadArgs("foo1", jobid, "serv2", "started job", "job desc", null,
				new NoSuchJobException(String.format(
						"There is no unstarted job %s for user foo1", jobid)));
		testStartJobBadArgs("foo", "a" + jobid.substring(1), "serv2", "started job", "job desc", null,
				new NoSuchJobException(String.format(
						"There is no unstarted job %s for user foo", "a" + jobid.substring(1))));
		testStartJobBadArgs(null, jobid, "serv2", "started job", "job desc", null,
				new IllegalArgumentException("user cannot be null or the empty string"));
		testStartJobBadArgs("", jobid, "serv2", "started job", "job desc", null,
				new IllegalArgumentException("user cannot be null or the empty string"));
		testStartJobBadArgs(LONG101, jobid, "serv2", "started job", "job desc", null,
				new IllegalArgumentException("user exceeds the maximum length of 100"));
		testStartJobBadArgs("foo",  null, "serv2", "started job", "job desc", null,
				new IllegalArgumentException("id cannot be null or the empty string"));
		testStartJobBadArgs("foo", "", "serv2", "started job", "job desc", null,
				new IllegalArgumentException("id cannot be null or the empty string"));
		testStartJobBadArgs("foo", "afeaefafaefaefafeaf", "serv2", "started job", "job desc", null,
				new IllegalArgumentException("Job ID afeaefafaefaefafeaf is not a legal ID"));
		testStartJobBadArgs("foo", jobid, null, "started job", "job desc", null,
				new IllegalArgumentException("service cannot be null or the empty string"));
		testStartJobBadArgs("foo", jobid, "", "started job", "job desc", null,
				new IllegalArgumentException("service cannot be null or the empty string"));
		testStartJobBadArgs("foo", jobid, LONG101, "started job", "job desc", null,
				new IllegalArgumentException("service exceeds the maximum length of 100"));
		testStartJobBadArgs("foo", jobid, "serv2", LONG201, "job desc", null,
				new IllegalArgumentException("status exceeds the maximum length of 200"));
		testStartJobBadArgs("foo", jobid, "serv2", "started job", LONG1001, null,
				new IllegalArgumentException("description exceeds the maximum length of 1000"));
		testStartJobBadArgs("foo", jobid, "serv2", "started job", "job desc", nearpast,
				new IllegalArgumentException("The estimated completion date must be in the future"));
		try {
			js.startJob("foo", jobid, "serv2", "started job", "job desc", 0,
					null);
			fail("Started job with 0 for num tasks");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is("The maximum progress for the job must be > 0"));
		}
		int[] char1 = {11614};
		String uni = new String(char1, 0, 1);
		jobid = js.createJob("unicode");
		js.startJob("unicode", jobid, "serv3", uni, "desc3", 200,
				nearfuture);
		j = js.getJob("unicode", jobid);
		checkJob(j, jobid, "started", nearfuture, "unicode", uni,
				"serv3", "desc3", "task", 0, 200, false, false, null, null);
		
		jobid = js.createJob("foo3");
		js.startJob("foo3", jobid, "serv3", "start3", "desc3", 200, nearfuture);
		j = js.getJob("foo3", jobid);
		checkJob(j, jobid, "started", nearfuture, "foo3", "start3", "serv3",
				"desc3", "task", 0, 200, false, false, null, null);
		jobid = js.createJob("foo4");
		js.startJobWithPercentProg("foo4", jobid, "serv4", "start4", "desc4",
				nearfuture);
		j = js.getJob("foo4", jobid);
		checkJob(j, jobid, "started", nearfuture, "foo4", "start4", "serv4",
				"desc4", "percent", 0, 100, false, false, null, null);
		
		jobid = js.createAndStartJob("fooc1", "servc1", "startc1", "desc_c1",
				nearfuture);
		j = js.getJob("fooc1", jobid);
		checkJob(j, jobid, "started", nearfuture, "fooc1", "startc1",
				"servc1", "desc_c1", "none", null, null, false, false, null,
				null);
		
		jobid = js.createAndStartJob("fooc2", "servc2", "startc2", "desc_c2",
				50, nearfuture);
		j = js.getJob("fooc2", jobid);
		checkJob(j, jobid, "started", nearfuture, "fooc2", "startc2", "servc2",
				"desc_c2", "task", 0, 50, false, false, null, null);
		
		jobid = js.createAndStartJobWithPercentProg("fooc3", "servc3",
				"startc3", "desc_c3", nearfuture);
		j = js.getJob("fooc3", jobid);
		checkJob(j, jobid, "started", nearfuture, "fooc3", "startc3", "servc3",
				"desc_c3", "percent", 0, 100, false, false, null, null);
		
	}
	
	private void testStartJobBadArgs(String user, String jobid, String service,
			String status, String desc, Date estCompl, Exception exception)
			throws Exception {
		try {
			js.startJob(user, jobid, service, status, desc, estCompl);
			fail("Started job with bad args");
		} catch (Exception e) {
			assertThat("correct exception type", e,
					is(exception.getClass()));
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
		try {
			js.startJobWithPercentProg(user, jobid, service, status, desc,
					estCompl);
			fail("Started job with bad args");
		} catch (Exception e) {
			assertThat("correct exception type", e,
					is(exception.getClass()));
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
		try {
			js.startJob(user, jobid, service, status, desc, 6, estCompl);
			fail("Started job with bad args");
		} catch (Exception e) {
			assertThat("correct exception type", e,
					is(exception.getClass()));
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
		if (!goodID(jobid) || exception instanceof NoSuchJobException) {
			return;
		}
		try {
			js.createAndStartJob(user, service, status, desc, estCompl);
			fail("Started job with bad args");
		} catch (Exception e) {
			assertThat("correct exception type", e,
					is(exception.getClass()));
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
		try {
			js.createAndStartJobWithPercentProg(user, service, status, desc,
					estCompl);
			fail("Started job with bad args");
		} catch (Exception e) {
			assertThat("correct exception type", e,
					is(exception.getClass()));
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
		try {
			js.createAndStartJob(user, service, status, desc, 6, estCompl);
			fail("Started job with bad args");
		} catch (Exception e) {
			assertThat("correct exception type", e,
					is(exception.getClass()));
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
	}
	
	private static boolean goodID(String jobid) {
		try {
			new ObjectId(jobid);
			return true;
		} catch (IllegalArgumentException iae) {
			return false;
		}
	}
	
	private void checkJob(Job j, String id, String stage, Date estComplete, 
			String user, String status, String service, String desc,
			String progtype, Integer prog, Integer maxproj, Boolean complete,
			Boolean error, String errmsg, JobResults results) {
		checkJob(j, id, stage, estComplete, user, status, service, desc,
				progtype, prog, maxproj, complete, error, errmsg, results,
				null, UJSAuthorizer.DEFAULT_AUTH_STRAT,
				UJSAuthorizer.DEFAULT_AUTH_PARAM,
				new HashMap<String, String>());
	}
	
	private void checkJob(String id, String stage, Date estComplete, 
			String user, String status, String service, String desc,
			String progtype, Integer prog, Integer maxproj, Boolean complete,
			Boolean error, String errmsg, JobResults results,
			List<String> shared) throws Exception {
		checkJob(js.getJob(user, id), id, stage, estComplete, user, status,
				service, desc, progtype, prog, maxproj, complete, error, errmsg,
				results, shared,
				UJSAuthorizer.DEFAULT_AUTH_STRAT,
				UJSAuthorizer.DEFAULT_AUTH_PARAM,
				new HashMap<String, String>());
	}
	
	private void checkJob(FakeJob fj) throws Exception {
		checkJob(new DefaultUJSAuthorizer(), fj);
	}
	
	private void checkJob(UJSAuthorizer auth, FakeJob fj) throws Exception {
		checkJob(auth, fj, null);
	}
	
	private void checkJob(UJSAuthorizer auth, FakeJob fj, List<String> shared)
			throws Exception {
		checkJob(js.getJob(fj.user, fj.id, auth), fj.id, fj.stage, fj.estcompl,
				fj.user, fj.status, fj.service, fj.desc, fj.progtype, fj.prog,
				fj.maxprog, fj.complete, fj.error, fj.errormsg, fj.results,
				shared, fj.authstrat, fj.authparam, fj.metadata);
	}
	
	private void checkJob(Job j, String id, String stage, Date estComplete, 
			String user, String status, String service, String desc,
			String progtype, Integer prog, Integer maxproj, Boolean complete,
			Boolean error, String errmsg, JobResults results,
			List<String> shared, AuthorizationStrategy strat,
			String authParam, Map<String, String> meta) {
		assertThat("job id ok", j.getID(), is(id));
		assertThat("job stage ok", j.getStage(), is(stage));
		assertThat("job user ok", j.getUser(), is(user));
		assertThat("job service ok", j.getService(), is(service));
		assertThat("job desc ok", j.getDescription(), is(desc));
		assertThat("job progtype ok", j.getProgType(), is(progtype));
		assertThat("job prog ok", j.getProgress(), is(prog));
		assertThat("job maxprog ok", j.getMaxProgress(), is(maxproj));
		assertThat("job status ok", j.getStatus(), is(status));
		assertTrue("job started is ok", j.getStarted() == null ||
				j.getStarted() instanceof Date);
		assertThat("job est complete ok", j.getEstimatedCompletion(),
				is(estComplete));
		assertThat("job updated ok", j.getLastUpdated(), is(Date.class));
		assertThat("job complete ok", j.isComplete(), is(complete));
		assertThat("job error ok", j.hasError(), is(error));
		assertThat("job results ok", j.getResults(), is(results));
		assertThat("job error ok", j.getErrorMsg(), is(errmsg));
		if (shared != null) {
			assertThat("shared list ok", j.getShared(), is(shared));
		}
		assertThat("incorrect auth strat", j.getAuthorizationStrategy(),
				is(strat));
		assertThat("incorrect auth param", j.getAuthorizationParameter(),
				is(authParam));
		assertThat("incorrect metadata", j.getMetadata(),
				is(meta));
	}
	
	@Test
	public void getJob() throws Exception {
		String user = "foo";
		String user2 = "foo1";
		String id = js.createJob(user);
		//should work
		js.getJob(user, id);
		js.getJob(user, id, new DefaultUJSAuthorizer());
		js.shareJob(user, id, Arrays.asList(LONG101.substring(1)));
		//should work
		js.getJob(LONG101.substring(1), id);
		js.getJob(LONG101.substring(1), id, new DefaultUJSAuthorizer());
		
		failGetJob(LONG101, id, new IllegalArgumentException(
				"user exceeds the maximum length of 100"));
		
		failGetJob(null, id, new IllegalArgumentException(
				"user cannot be null or the empty string"));
		failGetJob("", id, new IllegalArgumentException(
				"user cannot be null or the empty string"));
	
		failGetJob(user, null, new IllegalArgumentException(
				"id cannot be null or the empty string"));
		failGetJob(user, "", new IllegalArgumentException(
				"id cannot be null or the empty string"));
		
		failGetJob(user, id.substring(1), new IllegalArgumentException(
				String.format("Job ID %s is not a legal ID",
						id.substring(1))));
		failGetJob(user, "g" + id.substring(1), new IllegalArgumentException(
				String.format("Job ID %s is not a legal ID",
						"g" + id.substring(1))));
		
		failGetJob(user2, id, new NoSuchJobException(String.format(
				"There is no job %s viewable by user %s", id, user2)));
		
		js.startJob(user, id, "foo", "stat", "desc", null);
		js.deleteJob(user, id, "foo");
		failGetJob(user, id, new NoSuchJobException(String.format(
				"There is no job %s viewable by user %s", id, user)));
	}
	
	@Test
	public void updateJob() throws Exception {
		Date nearfuture = new Date(new Date().getTime() + 10000);
		Date nearpast = new Date(new Date().getTime() - 10);
		//task based progress
		String jobid = js.createAndStartJob("bar", "service1", "st", "de", 33,
				null);
		Job j = js.getJob("bar", jobid);
		checkJob(j, jobid, "started", null, "bar", "st", "service1", "de",
				"task", 0, 33, false, false, null, null);
		
		js.updateJob("bar", jobid, "service1", "new st", 4, null);
		j = js.getJob("bar", jobid);
		checkJob(j, jobid, "started", null, "bar", "new st",
				"service1", "de", "task", 4, 33, false, false, null, null);
		
		js.updateJob("bar", jobid, "service1", "new st2", 16, nearfuture);
		j = js.getJob("bar", jobid);
		checkJob(j, jobid, "started", nearfuture, "bar", "new st2",
				"service1", "de", "task", 20, 33, false, false, null, null);
		
		js.updateJob("bar", jobid, "service1", "this really should be done",
				16, null);
		j = js.getJob("bar", jobid);
		checkJob(j, jobid, "started", nearfuture, "bar",
				"this really should be done", "service1", "de", "task", 33, 33,
				false, false, null, null);
		
		//no progress tracking
		jobid = js.createAndStartJob("bar2", "service2", "st2", "de2", null);
		j = js.getJob("bar2", jobid);
		checkJob(j, jobid, "started", null, "bar2", "st2", "service2", "de2",
				"none", null, null, false, false, null, null);
		
		js.updateJob("bar2", jobid, "service2", "st2-2", null, nearfuture);
		j = js.getJob("bar2", jobid);
		checkJob(j, jobid, "started", nearfuture, "bar2", "st2-2", "service2",
				"de2", "none", null, null, false, false, null, null);
		
		js.updateJob("bar2", jobid, "service2", "st2-3", 6, null);
		j = js.getJob("bar2", jobid);
		checkJob(j, jobid, "started", nearfuture, "bar2", "st2-3", "service2",
				"de2", "none", null, null, false, false, null, null);
		
		//percentage based tracking
		jobid = js.createAndStartJobWithPercentProg("bar3", "service3", "st3",
				"de3", null);
		j = js.getJob("bar3", jobid);
		checkJob(j, jobid, "started", null, "bar3", "st3", "service3", "de3",
				"percent", 0, 100, false, false, null, null);
		
		js.updateJob("bar3", jobid, "service3", "st3-2", 30, null);
		j = js.getJob("bar3", jobid);
		checkJob(j, jobid, "started", null, "bar3", "st3-2", "service3", "de3",
				"percent", 30, 100, false, false, null, null);
		
		js.updateJob("bar3", jobid, "service3", "st3-3", 2, nearfuture);
		j = js.getJob("bar3", jobid);
		checkJob(j, jobid, "started", nearfuture, "bar3", "st3-3",
				"service3", "de3", "percent", 32, 100, false, false, null,
				null);
		
		js.updateJob("bar3", jobid, "service3", "st3-4", 80, null);
		j = js.getJob("bar3", jobid);
		checkJob(j, jobid, "started", nearfuture, "bar3", "st3-4",
				"service3", "de3", "percent", 100, 100, false, false, null,
				null);
		
		testUpdateJobBadArgs("bar3", jobid, "service2", "stat", null, null,
				new NoSuchJobException(String.format(
						"There is no uncompleted job %s for user bar3 started by service service2",
						jobid)));
		testUpdateJobBadArgs("bar2", jobid, "service3", "stat", null, null,
				new NoSuchJobException(String.format(
						"There is no uncompleted job %s for user bar2 started by service service3",
						jobid)));
		testUpdateJobBadArgs("bar2", "a" + jobid.substring(1), "service2", "stat", null, null,
				new NoSuchJobException(String.format(
						"There is no uncompleted job %s for user bar2 started by service service2",
						"a" + jobid.substring(1))));
		testUpdateJobBadArgs(null, jobid, "serv2", "started job", 1, null,
				new IllegalArgumentException("user cannot be null or the empty string"));
		testUpdateJobBadArgs("", jobid, "serv2", "started job", 1, null,
				new IllegalArgumentException("user cannot be null or the empty string"));
		testUpdateJobBadArgs(LONG101, jobid, "serv2", "started job", 1, null,
				new IllegalArgumentException("user exceeds the maximum length of 100"));
		testUpdateJobBadArgs("foo",  null, "serv2", "started job", 1, null,
				new IllegalArgumentException("id cannot be null or the empty string"));
		testUpdateJobBadArgs("foo", "", "serv2", "started job", 1, null,
				new IllegalArgumentException("id cannot be null or the empty string"));
		testUpdateJobBadArgs("foo", "afeaefafaefaefafeaf", "serv2", "started job", 1, null,
				new IllegalArgumentException("Job ID afeaefafaefaefafeaf is not a legal ID"));
		testUpdateJobBadArgs("foo", jobid, null, "started job", 1, null,
				new IllegalArgumentException("service cannot be null or the empty string"));
		testUpdateJobBadArgs("foo", jobid, "", "started job", 1, null,
				new IllegalArgumentException("service cannot be null or the empty string"));
		testUpdateJobBadArgs("foo", jobid, LONG101, "started job", 1, null,
				new IllegalArgumentException("service exceeds the maximum length of 100"));
		testUpdateJobBadArgs("foo", jobid, "serv2", LONG201, 1, null,
				new IllegalArgumentException("status exceeds the maximum length of 200"));
		testUpdateJobBadArgs("foo", jobid, "serv2", "started job", -1, null,
				new IllegalArgumentException("progress cannot be negative"));
		testUpdateJobBadArgs("foo", jobid, "serv2", "started job", -1, nearpast,
				new IllegalArgumentException("The estimated completion date must be in the future"));
		
		//fail on updating a completed or unstarted job
		jobid = js.createJob("foobar");
		testUpdateJobBadArgs("foobar", jobid, "serv2", "stat", 1, null,
				new NoSuchJobException(String.format(
				"There is no uncompleted job %s for user foobar started by service serv2",
				jobid)));
		jobid = js.createAndStartJob("foobar", "serv2", "stat", "desc", null);
		js.completeJob("foobar", jobid, "serv2", "stat", null, null);
		testUpdateJobBadArgs("foobar", jobid, "serv2", "stat", 1, null,
				new NoSuchJobException(String.format(
				"There is no uncompleted job %s for user foobar started by service serv2",
				jobid)));
	}
	
	private void testUpdateJobBadArgs(String user, String jobid, String service,
			String status, Integer progress, Date estCompl, Exception exception)
			throws Exception {
		try {
			js.updateJob(user, jobid, service, status, progress, estCompl);
			fail("updated job with bad args");
		} catch (Exception e) {
			assertThat("correct exception type", e,
					is(exception.getClass()));
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
	}
	
	@Test
	public void completeJob() throws Exception {
		String jobid = js.createAndStartJob("comp", "cserv1", "cstat1",
				"cdesc1", 5, null);
		js.updateJob("comp", jobid, "cserv1", "cstat1-2", 2, null);
		js.updateJob("comp", jobid, "cserv1", "cstat1-2", 6, null);
		JobResults res1 = new JobResults(null, null, null, null,
				Arrays.asList("node1", "node2"));
		js.completeJob("comp", jobid, "cserv1", "cstat1-3", "thing", res1);
		Job j = js.getJob("comp", jobid);
		checkJob(j, jobid, "error", null, "comp", "cstat1-3", "cserv1", "cdesc1",
				"task", 5, 5, true, true, "thing", res1);
		try {
			js.completeJob("comp", jobid, "cserv1", "cstat1-4", null, res1);
			fail("completed a completed job");
		} catch (NoSuchJobException nsje) {
			assertThat("correct exception msg", nsje.getLocalizedMessage(),
					is(String.format(
					"There is no uncompleted job %s for user comp started by service cserv1",
					jobid)));
		}
		
		List<JobResult> ljr = new LinkedList<JobResult>();
		ljr.add(new JobResult("s1", "a url", "an Id", "some desc"));
		ljr.add(new JobResult("s2", "a url2", "an Id2", "some desc2"));
		
		JobResults res2 = new JobResults(ljr, "ws url",
				Arrays.asList("ws id 1", "ws id 2"),
				"shock url", Arrays.asList("shock id 1", "shock id 2"));
		
		jobid = js.createAndStartJobWithPercentProg("comp", "cserv2", "cstat2",
				"cdesc2", null);
		js.updateJob("comp", jobid, "cserv2", "cstat2-2", 25, null);
		js.updateJob("comp", jobid, "cserv2", "cstat2-3", 50, null);
		js.completeJob("comp", jobid, "cserv2", "cstat2-3", null, res2);
		j = js.getJob("comp", jobid);
		checkJob(j, jobid, "complete", null, "comp", "cstat2-3", "cserv2", "cdesc2",
				"percent", 100, 100, true, false, null, res2);
		
		ljr = new LinkedList<JobResult>();
		
		JobResults res3 = new JobResults(ljr, "ws url 2",
				Arrays.asList("ws id 3", "ws id 4"),
				"shock url 2", Arrays.asList("shock id 3", "shock id 4"));
		
		jobid = js.createAndStartJobWithPercentProg("comp", "cserv3", "cstat3",
				"cdesc3", null);
		js.completeJob("comp", jobid, "cserv3", "cstat3-3", null, res3);
		j = js.getJob("comp", jobid);
		checkJob(j, jobid, "complete", null, "comp", "cstat3-3", "cserv3", "cdesc3",
				"percent", 100, 100, true, false, null, res3);
		
		jobid = js.createJob("comp");
		try {
			js.completeJob("comp", jobid, "cserv2", "badstat", null, res2);
			fail("completed an unstarted job");
		} catch (NoSuchJobException nsje) {
			assertThat("correct exception msg", nsje.getLocalizedMessage(),
					is(String.format(
					"There is no uncompleted job %s for user comp started by service cserv2",
					jobid)));
		}
		
		jobid = js.createAndStartJob("comp", "service2", "stat", "desc", null);
		
		testCompleteJobBadArgs("comp1", jobid, "service2", "stat", null,
				new NoSuchJobException(String.format(
						"There is no uncompleted job %s for user comp1 started by service service2",
						jobid)));
		testCompleteJobBadArgs("comp", jobid, "service3", "stat", null,
				new NoSuchJobException(String.format(
						"There is no uncompleted job %s for user comp started by service service3",
						jobid)));
		testCompleteJobBadArgs("comp", "a" + jobid.substring(1), "service2", "stat", null,
				new NoSuchJobException(String.format(
						"There is no uncompleted job %s for user comp started by service service2",
						"a" + jobid.substring(1))));
		testCompleteJobBadArgs(null, jobid, "service2", "started job", null,
				new IllegalArgumentException("user cannot be null or the empty string"));
		testCompleteJobBadArgs("", jobid, "service2", "started job", null,
				new IllegalArgumentException("user cannot be null or the empty string"));
		testCompleteJobBadArgs(LONG101, jobid, "service2", "started job", null,
				new IllegalArgumentException("user exceeds the maximum length of 100"));
		testCompleteJobBadArgs("comp",  null, "service2", "started job", null,
				new IllegalArgumentException("id cannot be null or the empty string"));
		testCompleteJobBadArgs("comp", "", "service2", "started job", null,
				new IllegalArgumentException("id cannot be null or the empty string"));
		testCompleteJobBadArgs("comp", "afeaefafaefaefafeaf", "service2", "started job", null,
				new IllegalArgumentException("Job ID afeaefafaefaefafeaf is not a legal ID"));
		testCompleteJobBadArgs("comp", jobid, null, "started job", null,
				new IllegalArgumentException("service cannot be null or the empty string"));
		testCompleteJobBadArgs("comp", jobid, "", "started job", null,
				new IllegalArgumentException("service cannot be null or the empty string"));
		testCompleteJobBadArgs("comp", jobid, LONG101, "started job", null,
				new IllegalArgumentException("service exceeds the maximum length of 100"));
		testCompleteJobBadArgs("comp", jobid, "service2", LONG201, null,
				new IllegalArgumentException("status exceeds the maximum length of 200"));
		testCompleteJobBadArgs("comp", jobid, "service2", "started job", LONG100001,
				new IllegalArgumentException("error exceeds the maximum length of 100000"));
	}
	
	private void testCompleteJobBadArgs(String user, String jobid, String service,
			String status, String errormsg, Exception exception) throws Exception {
		try {
			js.completeJob(user, jobid, service, status, errormsg, null);
			fail("completed job with bad args");
		} catch (Exception e) {
			assertThat("correct exception type", e,
					is(exception.getClass()));
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
	}
	
	@Test
	public void checkDateUpdates() throws Exception {
		String jobid = js.createJob("date");
		Job j = js.getJob("date", jobid);
		Date create = j.getLastUpdated();
		js.startJob("date", jobid, "serv1", "stat", "desc", null);
		j = js.getJob("date", jobid);
		Date start = j.getLastUpdated();
		js.updateJob("date", jobid, "serv1", "stat", null, null);
		j = js.getJob("date", jobid);
		Date update = j.getLastUpdated();
		js.updateJob("date", jobid, "serv1", "stat", null, null);
		j = js.getJob("date", jobid);
		Date update2 = j.getLastUpdated();
		js.completeJob("date", jobid, "serv1", "stat", null, null);
		j = js.getJob("date", jobid);
		Date complete = j.getLastUpdated();
		
		assertTrue("date created < started", create.compareTo(start) == -1);
		assertTrue("date started < updated", start.compareTo(update) == -1);
		assertTrue("date updated < updated2", update.compareTo(update2) == -1);
		assertTrue("date updated2 < complete", update2.compareTo(complete) == -1);
	}
	
	@Test
	public void deleteJob() throws Exception {
		String jobid = js.createAndStartJob("delete", "serv1", "st", "dsc",
				null);
		js.completeJob("delete", jobid, "serv1", "st", null, null);
		Job j = js.getJob("delete", jobid); //should work
		checkJob(j, jobid, "complete", null, "delete", "st", "serv1", "dsc",
				"none", null, null, true, false, null, null);
		succeedAtDeletingJob("delete", jobid);
		failToDeleteJob("delete", jobid, null);
		
		jobid = js.createJob("delete");
		failToDeleteJob("delete", jobid, null);
		js.startJob("delete", jobid, "s", "s", "d", null);
		failToDeleteJob("delete", jobid, null);
		js.updateJob("delete", jobid, "s", "s", 1, null);
		failToDeleteJob("delete", jobid, null);
		failToDeleteJob("delete1", jobid, null);
		failToDeleteJob("delete", "a" + jobid.substring(1), null);
		
		failToDeleteJob("delete1", jobid, "serv1");
		failToDeleteJob("delete", "a" + jobid.substring(1), "serv1");
		
		jobid = js.createJob("delete");
//		succeedAtDeletingJob("delete", jobid, "serv1");
		failToDeleteJob("delete", jobid, "serv1");
		jobid = js.createAndStartJob("delete", "serv1", "st", "dsc", null);
		succeedAtDeletingJob("delete", jobid, "serv1");
		failToDeleteJob("delete", jobid, "serv1");
		jobid = js.createAndStartJob("delete", "serv1", "st", "dsc", null);
		js.updateJob("delete", jobid, "serv1", "st", null, null);
		succeedAtDeletingJob("delete", jobid, "serv1");
		failToDeleteJob("delete", jobid, "serv1");
		jobid = js.createAndStartJob("delete", "serv1", "st", "dsc", null);
		js.completeJob("delete", jobid, "serv1", "st", null, null);
		succeedAtDeletingJob("delete", jobid, "serv1");
		failToDeleteJob("delete", jobid, "serv1");
		
	}
	
	private void succeedAtDeletingJob(String user, String jobid, String service)
			throws Exception {
		js.deleteJob(user, jobid, service);
		try {
			js.getJob(user, jobid);
			fail("got deleted job");
		} catch (NoSuchJobException nsje) {
			assertThat("correct exception msg", nsje.getLocalizedMessage(),
					is(String.format(
					"There is no job %s viewable by user %s", jobid, user)));
		}
		try {
			js.getJob(user, jobid, new DefaultUJSAuthorizer());
			fail("got deleted job");
		} catch (NoSuchJobException nsje) {
			assertThat("correct exception msg", nsje.getLocalizedMessage(),
					is(String.format(
					"There is no job %s viewable by user %s", jobid, user)));
		}
	}
	
	private void succeedAtDeletingJob(String user, String jobid)
			throws Exception {
		js.deleteJob(user, jobid);
		try {
			js.getJob(user, jobid);
			fail("got deleted job");
		} catch (NoSuchJobException nsje) {
			assertThat("correct exception msg", nsje.getLocalizedMessage(),
					is(String.format(
					"There is no job %s viewable by user %s", jobid, user)));
		}
		try {
			js.getJob(user, jobid, new DefaultUJSAuthorizer());
			fail("got deleted job");
		} catch (NoSuchJobException nsje) {
			assertThat("correct exception msg", nsje.getLocalizedMessage(),
					is(String.format(
					"There is no job %s viewable by user %s", jobid, user)));
		}
	}
	
	private void failToDeleteJob(String user, String jobid, String service)
			throws Exception {
		try {
			js.deleteJob(user, jobid, service);
			fail("deleted job when should've failed");
		} catch (NoSuchJobException nsje) {
			assertThat("correct exception msg", nsje.getLocalizedMessage(),
					is(String.format(
					"There is no %sjob %s for user %s",
					service == null ? "completed " : "", jobid, user +
					(service == null ? "" : " and service " + service))));
		}
		if (service != null) {
			return;
		}
		try {
			js.deleteJob(user, jobid);
			fail("deleted job when should've failed");
		} catch (NoSuchJobException nsje) {
			assertThat("correct exception msg", nsje.getLocalizedMessage(),
					is(String.format(
					"There is no completed job %s for user %s",
					jobid, user)));
		}
	}
	
	@Test
	public void listServices() throws Exception {
		checkListServ("listserv", new ArrayList<String>());
		String jobid = js.createJob("listserv");
		checkListServ("listserv", new ArrayList<String>());
		js.startJob("listserv", jobid, "serv1", null, null, null);
		checkListServ("listserv", Arrays.asList("serv1"));
		checkListServ("listserv2", new ArrayList<String>());
		js.createAndStartJob("listserv", "serv2", null, null, null);
		checkListServ("listserv", Arrays.asList("serv1", "serv2"));
		jobid = js.createAndStartJob("listserv2", "serv3", null, null, null);
		checkListServ("listserv", Arrays.asList("serv1", "serv2"));
		checkListServ("listserv2", Arrays.asList("serv3"));
		js.shareJob("listserv2", jobid, Arrays.asList("listserv"));
		checkListServ("listserv", Arrays.asList("serv1", "serv2", "serv3"));
		js.unshareJob("listserv2", jobid, Arrays.asList("listserv"));
		checkListServ("listserv", Arrays.asList("serv1", "serv2"));
	}
	
	private void checkListServ(String user, List<String> expected) throws Exception {
		Set<String> expc = new HashSet<String>(expected);
		assertThat("get correct services", js.listServices(user),
				is(expc));
	}
	
	@Test
	public void listJobs() throws Exception {
		String lj = "listjobs";
		List<FakeJob> empty = new ArrayList<FakeJob>();
		checkListJobs(empty, lj, Arrays.asList("serv1"), true, true, true, false);
		String jobid = js.createJob(lj);
		checkListJobs(empty, lj, Arrays.asList("serv1"), true, true, true, false);
		
		jobid = js.createAndStartJob(lj, "serv1", "lst", "ldsc", 42, MAX_DATE);
		FakeJob started = new FakeJob(jobid, lj, "serv1", "started",
				MAX_DATE,"ldsc", "task", 0, 42, "lst", false, false, null, null);
		checkListJobs(Arrays.asList(started), lj, Arrays.asList("serv1"), true, true, true, false);
		checkListJobs(empty, lj, Arrays.asList("serv2"), true, true, true, false);
		checkListJobs(Arrays.asList(started), lj, Arrays.asList("serv1"), false, false, false, false);
		
		jobid = js.createAndStartJob(lj, "serv1", "comp-st", "comp-dsc",
				MAX_DATE);
		js.completeJob(lj, jobid, "serv1", "comp-st1", null, null);
		FakeJob complete = new FakeJob(jobid, lj, "serv1", "complete",
				MAX_DATE, "comp-dsc", "none", null, null, "comp-st1",
				true, false, null, null);
		
		jobid = js.createAndStartJobWithPercentProg(lj, "serv1", "err-st",
				"err-dsc", MAX_DATE);
		js.completeJob(lj, jobid, "serv1", "err-st1", "some error", null);
		FakeJob error = new FakeJob(jobid, lj, "serv1", "error",
				MAX_DATE, "err-dsc", "percent", 100, 100, "err-st1", true,
				true, "some error", null);
		
		//all 3
		List<FakeJob> all = Arrays.asList(started, complete, error);
		checkListJobs(all, lj, Arrays.asList("serv1"), true, true, true, false);
		checkListJobs(all, lj, Arrays.asList("serv1"), false, false, false, false);
		
		//1 of 3
		checkListJobs(Arrays.asList(started),
				lj, Arrays.asList("serv1"), true, false, false, false);
		checkListJobs(Arrays.asList(complete),
				lj, Arrays.asList("serv1"), false, true, false, false);
		checkListJobs(Arrays.asList(error),
				lj, Arrays.asList("serv1"), false, false, true, false);
		
		//2 of 3
		checkListJobs(Arrays.asList(started, complete),
				lj, Arrays.asList("serv1"), true, true, false, false);
		checkListJobs(Arrays.asList(complete, error),
				lj, Arrays.asList("serv1"), false, true, true, false);
		checkListJobs(Arrays.asList(started, error),
				lj, Arrays.asList("serv1"), true, false, true, false);
		
		//check on jobs from multiple services
		jobid = js.createAndStartJob(lj, "serv2", "mst", "mdsc", 42, MAX_DATE);
		FakeJob multi = new FakeJob(jobid, lj, "serv2", "started",
				MAX_DATE, "mdsc", "task", 0, 42, "mst", false, false, null, null);
		checkListJobs(Arrays.asList(started, complete, error, multi),
				lj, new ArrayList<String>(), true, true, true, false);
		checkListJobs(Arrays.asList(started, complete, error, multi),
				lj, null, true, true, true, false);
		checkListJobs(Arrays.asList(started, complete, error, multi),
				lj, Arrays.asList("serv1", "serv2"), true, true, true, false);
		checkListJobs(Arrays.asList(started, complete),
				lj, Arrays.asList("serv1"), true, true, false, false);
		checkListJobs(Arrays.asList(multi),
				lj, Arrays.asList("serv2"), true, true, true, false);
		
		//check on shared jobs
		jobid = js.createAndStartJob("listJobsShare", "shareserv", "sst", "sdsc", null);
		FakeJob shared = new FakeJob(jobid, "listJobsShare", "shareserv", "started",
				null, "sdsc", "none", null, null, "sst", false, false, null, null);
		checkListJobs(Arrays.asList(started),
				lj, Arrays.asList("serv1", "shareserv"), true, false, false, true);
		js.shareJob("listJobsShare", jobid, Arrays.asList(lj));
		checkListJobs(Arrays.asList(started),
				lj, Arrays.asList("serv1", "shareserv"), true, false, false, false);
		checkListJobs(Arrays.asList(started, shared),
				lj, Arrays.asList("serv1", "shareserv"), true, false, false, true);
		js.unshareJob("listJobsShare", jobid, Arrays.asList(lj));
		checkListJobs(Arrays.asList(started),
				lj, Arrays.asList("serv1", "shareserv"), true, false, false, true);
		
		// fail on user
		failListJobs(null, Arrays.asList("serv"), new IllegalArgumentException(
				"user cannot be null or the empty string"));
		failListJobs("", Arrays.asList("serv"), new IllegalArgumentException(
				"user cannot be null or the empty string"));
		
		// fail on service
		failListJobs(lj, Arrays.asList((String) null),
				new IllegalArgumentException(
				"service cannot be null or the empty string"));
		failListJobs(lj, Arrays.asList(""), new IllegalArgumentException(
				"service cannot be null or the empty string"));
		//these shouldn't except
		js.listJobs(lj, Arrays.asList(LONG101.substring(1)),
				false, false, false, false);
		js.listJobs(lj, Arrays.asList(LONG101.substring(1)),
				false, false, false, false, new DefaultUJSAuthorizer(),
				UJSAuthorizer.DEFAULT_AUTH_STRAT,
				Arrays.asList(UJSAuthorizer.DEFAULT_AUTH_PARAM));
		failListJobs(lj, Arrays.asList(LONG101), new IllegalArgumentException(
				"service exceeds the maximum length of 100"));
		
		// fail on auth 
		failListJobs(lj, null, null, new AuthorizationStrategy("foo"),
				Arrays.asList("foo"), new NullPointerException());
		failListJobs(lj, null, new DefaultUJSAuthorizer(),
				null, Arrays.asList("foo"), new NullPointerException());
		failListJobs(lj, null, new DefaultUJSAuthorizer(),
				new AuthorizationStrategy("foo"), Arrays.asList("foo"),
				new UnimplementedException());
		
		// fail on auth params
		failListJobs(lj, null, new DefaultUJSAuthorizer(),
				new AuthorizationStrategy("DEFAULT"), null,
				new IllegalArgumentException(
						"authParams cannot be null or empty"));
		failListJobs(lj, null, new DefaultUJSAuthorizer(),
				new AuthorizationStrategy("DEFAULT"), new ArrayList<String>(),
				new IllegalArgumentException(
						"authParams cannot be null or empty"));
		failListJobs(lj, null, new DefaultUJSAuthorizer(),
				new AuthorizationStrategy("DEFAULT"),
				Arrays.asList((String) null), new IllegalArgumentException(
						"authParam cannot be null or empty"));
		failListJobs(lj, null, new DefaultUJSAuthorizer(),
				new AuthorizationStrategy("DEFAULT"),
				Arrays.asList(""), new IllegalArgumentException(
						"authParam cannot be null or empty"));
	}
	
	private void failListJobs(String user, List<String> services,
			Exception exp) {
		try {
			js.listJobs(user, services, false, false, false, false);
			fail("listed jobs w/ bad args");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
		failListJobs(user, services, new DefaultUJSAuthorizer(),
				UJSAuthorizer.DEFAULT_AUTH_STRAT,
				Arrays.asList(UJSAuthorizer.DEFAULT_AUTH_PARAM), exp);
	}

	private void failListJobs(String user, List<String> services,
			DefaultUJSAuthorizer auth, AuthorizationStrategy strat,
			List<String> params, Exception exp) {
		try {
			js.listJobs(user, services, false, false, false, false, auth,
					strat, params);
			fail("listed jobs w/ bad args");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
		
	}

	private void checkListJobs(List<FakeJob> expected, String user,
			List<String> services, boolean running, boolean complete,
			boolean error, boolean shared) throws Exception {
		checkListJobs(expected, js.listJobs(user, services, running, complete,
				error, shared));
		checkListJobs(expected, js.listJobs(user, services, running, complete,
				error, shared, new DefaultUJSAuthorizer(),
				UJSAuthorizer.DEFAULT_AUTH_STRAT,
				Arrays.asList(UJSAuthorizer.DEFAULT_AUTH_PARAM)));
	}

	private void checkListJobs(List<FakeJob> expected, List<Job> result)
		throws Exception {
		HashSet<FakeJob> res = new HashSet<FakeJob>();
		for (Job j: result) {
			res.add(new FakeJob(j));
		}
		assertThat("got expected jobs back", res, is(new HashSet<FakeJob>(expected)));
	}
	
	@Test
	public void sharing() throws Exception {
		String sh = "share";
		String jobid = js.createAndStartJob(sh, "shareserv", "st", "dsc",
				null);
		failGetJob("foo", jobid, new NoSuchJobException(String.format(
				"There is no job %s viewable by user %s", jobid, "foo")));
		js.shareJob(sh, jobid, Arrays.asList("foo", "bar"));
		checkJob(jobid, "started", null, sh, "st", "shareserv", "dsc", "none",
				null, null, false, false, null, null, Arrays.asList("foo", "bar"));
		js.getJob("share", jobid); //should work
		js.getJob("foo", jobid); //should work
		js.getJob("bar", jobid); //should work
		failGetJob("baz", jobid, new NoSuchJobException(String.format(
				"There is no job %s viewable by user %s", jobid, "baz")));
		
		js.shareJob(sh, jobid, Arrays.asList("foo", "bar", "baz"));
		checkJob(jobid, "started", null, sh, "st", "shareserv", "dsc", "none",
				null, null, false, false, null, null, Arrays.asList("foo", "bar", "baz"));
		js.shareJob(sh, jobid, Arrays.asList(sh)); //noop
		checkJob(jobid, "started", null, sh, "st", "shareserv", "dsc", "none",
				null, null, false, false, null, null, Arrays.asList("foo", "bar", "baz"));
		
		failShareJob("foo", jobid, Arrays.asList("bag"), new NoSuchJobException(
				String.format("There is no job %s with default authorization " +
						"owned by user %s", jobid, "foo")));
		failShareJob(null, jobid, Arrays.asList("bag"), new IllegalArgumentException(
				String.format("owner cannot be null or the empty string")));
		failShareJob("", jobid, Arrays.asList("bag"), new IllegalArgumentException(
				String.format("owner cannot be null or the empty string")));
		failShareUnshareJob("foo", jobid, null, new IllegalArgumentException(
				String.format("The users list cannot be null")));
		failShareUnshareJob("foo", jobid, new ArrayList<String>(), new IllegalArgumentException(
				String.format("The users list is empty")));
		failShareUnshareJob("foo", jobid, Arrays.asList("bag", null), new IllegalArgumentException(
				String.format("user cannot be null or the empty string")));
		failShareUnshareJob("foo", jobid, Arrays.asList("bag", ""), new IllegalArgumentException(
				String.format("user cannot be null or the empty string")));
		failShareUnshareJob(sh, null, Arrays.asList("bag"), new IllegalArgumentException(
				String.format("id cannot be null or the empty string")));
		failShareUnshareJob(sh, "", Arrays.asList("bag"), new IllegalArgumentException(
				String.format("id cannot be null or the empty string")));
		failShareUnshareJob(sh, "bleargh", Arrays.asList("bag"), new IllegalArgumentException(
				String.format("Job ID bleargh is not a legal ID")));
		failGetJob("bag", jobid, new NoSuchJobException(String.format(
				"There is no job %s viewable by user %s", jobid, "bag")));
		
		String nojob = new ObjectId().toString();
		failUnshareJob(sh, nojob, Arrays.asList("bag"), new NoSuchJobException(
				String.format("There is no job %s with default " +
						"authorization visible to user %s", nojob, sh)));
		failUnshareJob("bag", jobid, Arrays.asList("bag"), new NoSuchJobException(
				String.format("There is no job %s with default " +
						"authorization visible to user %s", jobid, "bag")));
		failUnshareJob("foo", jobid, Arrays.asList("bag"), new IllegalArgumentException(
				String.format("User %s may only stop sharing job %s for themselves", "foo", jobid)));
		failUnshareJob(null, jobid, Arrays.asList("bag"), new IllegalArgumentException(
				String.format("user cannot be null or the empty string")));
		failUnshareJob("", jobid, Arrays.asList("bag"), new IllegalArgumentException(
				String.format("user cannot be null or the empty string")));
		
		js.unshareJob(sh, jobid, Arrays.asList("bar", "baz"));
		checkJob(jobid, "started", null, sh, "st", "shareserv", "dsc", "none",
				null, null, false, false, null, null, Arrays.asList("foo"));
		failGetJob("bar", jobid, new NoSuchJobException(String.format(
				"There is no job %s viewable by user %s", jobid, "bar")));
		failGetJob("baz", jobid, new NoSuchJobException(String.format(
				"There is no job %s viewable by user %s", jobid, "baz")));
		js.getJob("foo", jobid); //should work
		js.unshareJob("foo", jobid, Arrays.asList("foo"));
		checkJob(jobid, "started", null, sh, "st", "shareserv", "dsc", "none",
				null, null, false, false, null, null, new ArrayList<String>());
		failGetJob("foo", jobid, new NoSuchJobException(String.format(
				"There is no job %s viewable by user %s", jobid, "foo")));
		js.unshareJob(sh, jobid, Arrays.asList(sh, "bar", "baz")); //noop
		checkJob(jobid, "started", null, sh, "st", "shareserv", "dsc", "none",
				null, null, false, false, null, null, new ArrayList<String>());
		js.getJob(sh, jobid); //should work
	}
	
	private void failGetJob(String user, String jobid, Exception e) {
		try {
			js.getJob(user, jobid);
			fail("got job sucessfully but expected fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
		failGetJob(user, jobid, new DefaultUJSAuthorizer(), e);
	}
	
	private void failGetJob(String user, String jobid, UJSAuthorizer auth,
			Exception e) {
		try {
			js.getJob(user, jobid, auth);
			fail("got job successfully but expected fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	private void failShareJob(String user, String jobid, List<String> users,
			Exception e) {
		try {
			js.shareJob(user, jobid, users);
			fail("got job sucessfully but expected fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
	
	private void failShareUnshareJob(String user, String jobid, List<String> users,
			Exception e) {
		failShareJob(user, jobid, users, e);
		failUnshareJob(user, jobid, users, e);
	}

	private void failUnshareJob(String user, String jobid, List<String> users,
			Exception e) {
		try {
			js.unshareJob(user, jobid, users);
			fail("unshared job sucessfully but expected fail");
		} catch (Exception exp) {
			assertThat("correct exception", exp.getLocalizedMessage(),
					is(e.getLocalizedMessage()));
			assertThat("correct exception type", exp, is(e.getClass()));
		}
	}
}
