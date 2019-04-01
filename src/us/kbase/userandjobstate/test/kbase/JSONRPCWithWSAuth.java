package us.kbase.userandjobstate.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.test.auth2.authcontroller.AuthController;
import us.kbase.userandjobstate.CreateJobParams;
import us.kbase.userandjobstate.InitProgress;
import us.kbase.userandjobstate.UserAndJobStateClient;
import us.kbase.userandjobstate.UserAndJobStateServer;
import us.kbase.userandjobstate.authorization.AuthorizationStrategy;
import us.kbase.userandjobstate.test.FakeJob;
import us.kbase.userandjobstate.test.controllers.workspace.WorkspaceController;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;

import com.google.common.collect.ImmutableMap;
import com.mongodb.DB;
import com.mongodb.MongoClient;

public class JSONRPCWithWSAuth extends JSONRPCLayerTestUtils {
	
	public static final Map<String, String> MTMAP = Collections.emptyMap();
	
	public static final String KBWS = "kbaseworkspace";
	
	public static MongoController MONGO;
	public static AuthController AUTHC;
	
	public static WorkspaceController WS;
	public static WorkspaceClient WSC1;
	public static WorkspaceClient WSC2;
	
	public static UserAndJobStateServer UJS;
	public static UserAndJobStateClient UJSC1;
	public static UserAndJobStateClient UJSC2;
	
	public static String USER1 = "user1";
	public static String USER2 = "user2";
	public static String TOKEN1;
	public static String TOKEN2;
	
	public static final String WS_DB_NAME = "ws";
	public static final String JOB_DB_NAME = "job";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		TestCommon.stfuLoggers();
		MONGO = new MongoController(
				TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()),
				TestCommon.useWiredTigerEngine());
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		String mongohost = "localhost:" + MONGO.getServerPort();
		System.out.println("mongo on " + mongohost);
		
		// set up auth
		final String dbname = JSONRPCLayerTest.class.getSimpleName() + "Auth";
		AUTHC = new AuthController(
				TestCommon.getJarsDir(),
				"localhost:" + MONGO.getServerPort(),
				dbname,
				Paths.get(TestCommon.getTempDir()));
		
		final URL authURL = new URL("http://localhost:" + AUTHC.getServerPort() + "/testmode");
		System.out.println("started auth server at " + authURL);
		TestCommon.createAuthUser(authURL, USER1, "display1");
		TOKEN1 = TestCommon.createLoginToken(authURL, USER1);
		TestCommon.createAuthUser(authURL, USER2, "display2");
		TOKEN2 = TestCommon.createLoginToken(authURL, USER2);
		final AuthToken t1 = new AuthToken(TOKEN1, USER1);
		final AuthToken t2 = new AuthToken(TOKEN2, USER2);
		
		WS = new WorkspaceController(
				TestCommon.getJarsDir(),
				mongohost,
				WS_DB_NAME,
				USER2,
				authURL,
				Paths.get(TestCommon.getTempDir()).resolve("tempForWorkspaceForUJSAuthTest"));
		
		final int wsport = WS.getServerPort();
		WSC1 = new WorkspaceClient(new URL("http://localhost:" + wsport), t1);
		WSC2 = new WorkspaceClient(new URL("http://localhost:" + wsport), t2);
		WSC1.setIsInsecureHttpConnectionAllowed(true);
		WSC2.setIsInsecureHttpConnectionAllowed(true);
		
		UJS = startUpUJSServer(
				"localhost:" + MONGO.getServerPort(),
				authURL,
				"http://localhost:" + WS.getServerPort(),
				JOB_DB_NAME);
		int ujsport = UJS.getServerPort();
		System.out.println("Started UJS test server on port " + ujsport);
		System.out.println("Starting tests");
		UJSC1 = new UserAndJobStateClient(new URL("http://localhost:" + ujsport),
				t1);
		UJSC2 = new UserAndJobStateClient(new URL("http://localhost:" + ujsport),
				t2);
		UJSC1.setIsInsecureHttpConnectionAllowed(true);
		UJSC2.setIsInsecureHttpConnectionAllowed(true);
		TOKEN1 = UJSC1.getToken().getToken();
		TOKEN2 = UJSC2.getToken().getToken();
	}
	
	@AfterClass
	public static void afterClass() throws Exception {
		if (MONGO != null) {
			MONGO.destroy(TestCommon.getDeleteTempFiles());
		}
		if (AUTHC != null) {
			AUTHC.destroy(TestCommon.getDeleteTempFiles());
		}
		if (WS != null) {
			WS.destroy(TestCommon.getDeleteTempFiles());
		}
		if (UJS != null) {
			UJS.stopServer();
		}
	}
	
	@Before
	public void before() throws Exception {
		final MongoClient mc = new MongoClient("localhost:" + MONGO.getServerPort());
		DB db = mc.getDB(WS_DB_NAME);
		TestCommon.destroyDB(db);
		db = mc.getDB(JOB_DB_NAME);
		TestCommon.destroyDB(db);
		mc.close();
	}
	
	@Test
	public void testCreateJob() throws Exception {
		WSC1.createWorkspace(new CreateWorkspaceParams().withWorkspace("foo"));
		String id = UJSC1.createJob2(new CreateJobParams().withAuthstrat(KBWS)
				.withAuthparam("1"));
		checkJob(UJSC1, id, USER1, null, "created", null, null, null, null,
				null, null, null, null, null, null, null, KBWS, "1", MTMAP);
		
		failCreateJob(UJSC1, "foo", "1",
				"Invalid authorization strategy: foo");
		failCreateJob(UJSC1, KBWS, "foo",
				"The string foo is not a valid integer workspace ID");
		failCreateJob(UJSC1, KBWS, "2",
				"Error contacting the workspace service to get " +
				"permissions: No workspace with id 2 exists");
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo1"));
		String id2 = UJSC1.createJob2(new CreateJobParams().withAuthstrat(KBWS)
				.withAuthparam("2"));
		checkJob(UJSC1, id2, USER1, null, "created", null, null, null, null,
				null, null, null, null, null, null, null, KBWS, "2", MTMAP);
		
		failCreateJob(UJSC2, KBWS, "1", String.format(
				"User %s cannot write to workspace 1", USER2));
		
		setPermissions(WSC1, 1, "r", USER2);
		failCreateJob(UJSC2, KBWS, "1", String.format(
				"User %s cannot write to workspace 1", USER2));
		
		setPermissions(WSC1, 1, "w", USER2);
		String id3 = UJSC2.createJob2(new CreateJobParams().withAuthstrat(KBWS)
				.withAuthparam("1"));
		checkJob(UJSC1, id3, USER2, null, "created", null, null, null, null,
				null, null, null, null, null, null, null, KBWS, "1", MTMAP);
		setPermissions(WSC1, 1, "a", USER2);
		String id4 = UJSC2.createJob2(new CreateJobParams().withAuthstrat(KBWS)
				.withAuthparam("1"));
		checkJob(UJSC1, id4, USER2, null, "created", null, null, null, null,
				null, null, null, null, null, null, null, KBWS, "1", MTMAP);
	}
	
	@Test
	public void testGetJob() throws Exception {
		InitProgress noprog = new InitProgress().withPtype("none");
		List<String> mtl = new LinkedList<String>();
		
		WSC1.createWorkspace(new CreateWorkspaceParams().withWorkspace("foo"));
		setPermissions(WSC1, 1, "r", USER2);
		
		//test that deleting a workspace keeps the job visible to the owner
		String id =  UJSC1.createJob2(new CreateJobParams()
			.withAuthstrat(KBWS).withAuthparam("1"));
		checkJob(UJSC1, id, USER1, null, "created", null, null, null, null,
				null, null, null, null, null, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC1.getJobOwner(id), is(USER1));
		assertThat("shared list ok", UJSC1.getJobShared(id), is(mtl));
		checkJob(UJSC2, id, USER1, null, "created", null, null, null, null,
				null, null, null, null, null, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC2.getJobOwner(id), is(USER1));
		
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		final String err = String.format(
				"There is no job %s viewable by user %s", id, USER2);
		failGetJob(UJSC2, id, err);
		failGetJobOwner(UJSC2, id, err);
		failGetJobShared(UJSC2, id, err);
		
		UJSC1.startJob(id, TOKEN2, "stat1", "desc1", noprog, null);
		UJSC1.updateJob(id, TOKEN2, "up stat2", null);
		UJSC1.completeJob(id, TOKEN2, "c stat2", null, null);
		checkJob(UJSC1, id, USER1, null, "complete", "c stat2", USER2, "desc1",
				"none", null, null, null, 1L, 0L, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC1.getJobOwner(id), is(USER1));
		assertThat("shared list ok", UJSC1.getJobShared(id), is(mtl));
		
		WSC2.administer(new UObject(ImmutableMap.of("command", "undeleteWorkspace",
				"params", new WorkspaceIdentity().withId(1L))));
		checkJob(UJSC2, id, USER1, null, "complete", "c stat2", USER2, "desc1",
				"none", null, null, null, 1L, 0L, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC2.getJobOwner(id), is(USER1));
		
		setPermissions(WSC1, 1, "n", USER2);
		failGetJob(UJSC2, id, err);
		failGetJobOwner(UJSC2, id, err);
		failGetJobShared(UJSC2, id, err);
		
		setPermissions(WSC1, 1, "w", USER2);
		checkJob(UJSC2, id, USER1, null, "complete", "c stat2", USER2, "desc1",
				"none", null, null, null, 1L, 0L, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC2.getJobOwner(id), is(USER1));
		
		setPermissions(WSC1, 1, "a", USER2);
		checkJob(UJSC2, id, USER1, null, "complete", "c stat2", USER2, "desc1",
				"none", null, null, null, 1L, 0L, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC2.getJobOwner(id), is(USER1));
		
		//test globally readable workspace
		setPermissions(WSC1, 1, "n", USER2);
		failGetJob(UJSC2, id, err);
		failGetJobOwner(UJSC2, id, err);
		failGetJobShared(UJSC2, id, err);
		
		WSC1.setGlobalPermission(new SetGlobalPermissionsParams().withId(1L)
				.withNewPermission("r"));
		checkJob(UJSC2, id, USER1, null, "complete", "c stat2", USER2, "desc1",
				"none", null, null, null, 1L, 0L, null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", UJSC2.getJobOwner(id), is(USER1));
		
	}
	
	@Test
	public void testCancelJob() throws Exception {
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("foo"));
		setPermissions(WSC1, 1, "w", USER2);
		
		//test that deleting a workspace keeps the job visible to the owner
		String id = createStartedJob();
		try {
			UJSC2.cancelJob(id, "canceled1");
		} catch (ServerException se) {
			System.out.println(se.getData());
			throw se;
		}
		checkJob(UJSC1, id, USER1, USER2, "canceled", "canceled1", USER2,
				"desc", "none", null, null, null, 1L, 0L, null, null, KBWS,
				"1", MTMAP);
		id = createStartedJob();
		String id2 = createStartedJob();
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		String err = String.format(
				"There is no job %s that may be canceled by user %s", id,
				USER2);
		failCancelJob(UJSC2, id, "cancel", err);
		checkJob(UJSC1, id, USER1, null, "started", "stat", USER2,
				"desc", "none", null, null, null, 0L, 0L, null, null, KBWS,
				"1", MTMAP);
		UJSC1.cancelJob(id2, "cancel2");
		checkJob(UJSC1, id2, USER1, USER1, "canceled", "cancel2", USER2,
				"desc", "none", null, null, null, 1L, 0L, null, null, KBWS,
				"1", MTMAP);
		WSC2.administer(new UObject(ImmutableMap.of("command", "undeleteWorkspace",
				"params", new WorkspaceIdentity().withId(1L))));
		UJSC2.cancelJob(id, "cancel3");
		checkJob(UJSC1, id, USER1, USER2, "canceled", "cancel3", USER2,
				"desc", "none", null, null, null, 1L, 0L, null, null, KBWS,
				"1", MTMAP);
		
		id = createStartedJob();
		err = String.format(
				"There is no job %s that may be canceled by user %s", id,
				USER2);
		setPermissions(WSC1, 1, "n", USER2);
		failCancelJob(UJSC2, id, "c", err);
		
		setPermissions(WSC1, 1, "r", USER2);
		failCancelJob(UJSC2, id, "c", err);
		
		setPermissions(WSC1, 1, "a", USER2);
		UJSC2.cancelJob(id, "cancel4");
		checkJob(UJSC1, id, USER1, USER2, "canceled", "cancel4", USER2,
				"desc", "none", null, null, null, 1L, 0L, null, null, KBWS,
				"1", MTMAP);
	}

	@Test
	public void testDeleteJob() throws Exception {
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
				.withWorkspace("foo"));
		setPermissions(WSC1, 1, "a", USER2);
		
		//test that deleting a workspace keeps the job visible to the owner
		String id = createCompletedJob();
		deleteJob(UJSC2, id);
		id = createStartedJob();
		deleteJob(UJSC2, id, TOKEN2);
		id = createCompletedJob();
		String id2 = createStartedJob();
		String id3 = createCompletedJob();
		String id4 = createStartedJob();
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		failToDeleteJob(UJSC2, id, String.format(
				"There is no deletable job %s for user %s", id,
				USER2));
		failToDeleteJob(UJSC2, id2, TOKEN2, String.format(
				"There is no deletable job %s for user %s and service %s", id2,
				USER2, USER2));

		deleteJob(UJSC1, id3);
		deleteJob(UJSC1, id4, TOKEN2);
		WSC2.administer(new UObject(ImmutableMap.of("command", "undeleteWorkspace",
				"params", new WorkspaceIdentity().withId(1L))));
		failGetJob(UJSC1, id3, String.format(
				"There is no job %s viewable by user %s",
				id3, USER1));
		failGetJob(UJSC1, id4, String.format(
				"There is no job %s viewable by user %s",
				id4, USER1));
		deleteJob(UJSC2, id);
		deleteJob(UJSC2, id2, TOKEN2);
		
		id = createCompletedJob();
		id2 = createStartedJob();
		String es = "There is no deletable job %s for user %s";
		String err1 = String.format(es, id, USER2);
		String err2 = String.format(es, id2, USER2) + " and service " + USER2;

		setPermissions(WSC1, 1, "n", USER2);
		failToDeleteJob(UJSC2, id, err1);
		failToDeleteJob(UJSC2, id2, TOKEN2, err2);
		
		setPermissions(WSC1, 1, "r", USER2);
		failToDeleteJob(UJSC2, id, err1);
		failToDeleteJob(UJSC2, id2, TOKEN2, err2);
		
		setPermissions(WSC1, 1, "w", USER2);
		failToDeleteJob(UJSC2, id, err1);
		failToDeleteJob(UJSC2, id2, TOKEN2, err2);
		
		setPermissions(WSC1, 1, "a", USER2);
		deleteJob(UJSC2, id);
		deleteJob(UJSC2, id2, TOKEN2);
	}
	
	private String createStartedJob() throws Exception {
		InitProgress noprog = new InitProgress().withPtype("none");
		String id = UJSC1.createJob2(new CreateJobParams().withAuthstrat(KBWS)
				.withAuthparam("1"));
		UJSC1.startJob(id, TOKEN2, "stat", "desc", noprog, null);
		return id;
	}
	
	private String createCompletedJob() throws Exception {
		InitProgress noprog = new InitProgress().withPtype("none");
		String id = UJSC1.createJob2(new CreateJobParams().withAuthstrat(KBWS)
				.withAuthparam("1"));
		UJSC1.startJob(id, TOKEN2, "stat", "desc", noprog, null);
		UJSC1.completeJob(id, TOKEN2, "comp", null, null);
		return id;
	}

	@Test
	public void testListJobs() throws Exception {
		InitProgress noprog = new InitProgress().withPtype("none");
		
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo"));
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo1"));
		WSC1.createWorkspace(new CreateWorkspaceParams()
			.withWorkspace("foo2"));
		setPermissions(WSC1, 1, "r", USER2);
		setPermissions(WSC1, 3, "r", USER2);
		
		String id1 =  UJSC1.createJob2(new CreateJobParams()
			.withAuthstrat(KBWS).withAuthparam("1"));
		UJSC1.startJob(id1, TOKEN2, "stat1", "desc1", noprog, null);
		String id2 =  UJSC1.createJob2(new CreateJobParams()
			.withAuthstrat(KBWS).withAuthparam("2"));
		UJSC1.startJob(id2, TOKEN2, "stat2", "desc2", noprog, null);
		String id3 =  UJSC1.createJob2(new CreateJobParams()
			.withAuthstrat(KBWS).withAuthparam("3"));
		UJSC1.startJob(id3, TOKEN2, "stat3", "desc3", noprog, null);
		// check that this doesn't show up in ws lists
		UJSC1.createAndStartJob(TOKEN2, "defstat", "defdesc", noprog, null);
		
		FakeJob fj1 = new FakeJob(id1, USER1, null, USER2, "started", null,
				"desc1", "none", null, null, "stat1", false, false, null, null,
				new AuthorizationStrategy(KBWS), "1", MTMAP);
		FakeJob fj2 = new FakeJob(id2, USER1, null, USER2, "started", null,
				"desc2", "none", null, null, "stat2", false, false, null, null,
				new AuthorizationStrategy(KBWS), "2", MTMAP);
		FakeJob fj3 = new FakeJob(id3, USER1, null, USER2, "started", null,
				"desc3", "none", null, null, "stat3", false, false, null, null,
				new AuthorizationStrategy(KBWS), "3", MTMAP);
		Set<FakeJob> fjs123 = new HashSet<FakeJob>(
				Arrays.asList(fj1, fj2, fj3));
		Set<FakeJob> fjs13 = new HashSet<FakeJob>(Arrays.asList(fj1, fj3));
		Set<FakeJob> fjs3 = new HashSet<FakeJob>(Arrays.asList(fj3));
		
		
		checkListJobs2(UJSC1, USER2, "", fjs123, KBWS,
				Arrays.asList("1", "2", "3"));
		checkListJobs2(UJSC2, USER2, "", fjs13, KBWS,
				Arrays.asList("1", "3"));
		failListJobs2(UJSC2, USER2, KBWS, Arrays.asList("1", "2", "3"),
				String.format("User %s cannot read workspace 2", USER2));
		
		WSC1.deleteWorkspace(new WorkspaceIdentity().withId(1L));
		failListJobs2(UJSC2, USER2, KBWS, Arrays.asList("1", "3"),
				"Error contacting the workspace service to get permissions: " +
				"Workspace 1 is deleted");
		failListJobs2(UJSC2, USER2, KBWS, Arrays.asList("3", "4"),
				"Error contacting the workspace service to get permissions: " +
				"No workspace with id 4 exists");
		checkListJobs2(UJSC1, USER2, "", fjs3, KBWS, Arrays.asList("3"));
		
		WSC2.administer(new UObject(ImmutableMap.of("command", "undeleteWorkspace",
				"params", new WorkspaceIdentity().withId(1L))));
		checkListJobs2(UJSC2, USER2, "", fjs13, KBWS, Arrays.asList("1", "3"));
		
		setPermissions(WSC1, 3, "n", USER2);

		//check fencepost error
		failListJobs2(UJSC2, USER2, KBWS, Arrays.asList("1", "3"),
				String.format("User %s cannot read workspace 3", USER2));
		
		setPermissions(WSC1, 3, "w", USER2);
		checkListJobs2(UJSC2, USER2, "", fjs13, KBWS, Arrays.asList("1", "3"));
		
		setPermissions(WSC1, 3, "a", USER2);
		checkListJobs2(UJSC2, USER2, "", fjs13, KBWS, Arrays.asList("1", "3"));
		
		//test globally readable workspaces
		setPermissions(WSC1, 3, "n", USER2);
		failListJobs2(UJSC2, USER2, KBWS, Arrays.asList("1", "3"),
				String.format("User %s cannot read workspace 3", USER2));
		WSC1.setGlobalPermission(new SetGlobalPermissionsParams().withId(3L)
				.withNewPermission("r"));
		checkListJobs2(UJSC2, USER2, "", fjs13, KBWS, Arrays.asList("1", "3"));
		
		failListJobs2(UJSC1, "foo", KBWS, new LinkedList<String>(),
				"authParams cannot be null or empty");
		failListJobs2(UJSC1, "foo", KBWS, Arrays.asList("1", "2", "3", "4",
				"5", "6", "7", "8", "9", "10", "11"),
				"No more than 10 workspace IDs may be specified");
		failListJobs2(UJSC1, "foo", KBWS, Arrays.asList("1", "2", "3", "4",
				"5", "6", "7", "8", "9", "foo"),
				"The string foo is not a valid integer workspace ID");
		failListJobs2(UJSC1, "foo", "foo", Arrays.asList("1", "2", "3", "4",
				"5", "6", "7", "8", "9", "10"),
				"Invalid authorization strategy: foo");
		
	}
	
}
