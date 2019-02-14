package us.kbase.userandjobstate.test.kbase;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;

import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.userandjobstate.CreateJobParams;
import us.kbase.userandjobstate.InitProgress;
import us.kbase.userandjobstate.UserAndJobStateClient;
import us.kbase.userandjobstate.UserAndJobStateServer;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceServer;


/* Tests associating a job with a workspace id, and the restarting the UJS
 * without associating it with the WSS.
 */
public class PullWSJobWithoutWSTest extends JSONRPCLayerTestUtils {
	
	public static final Map<String, String> MTMAP = new HashMap<>();
	
	public static final String KBWS = "kbaseworkspace";
	
	public static MongoController MONGO;
	
	public static WorkspaceServer WS;
	public static WorkspaceClient WSC;
	
	public static AuthUser USER;
	
	public static final String WS_DB_NAME = "ws";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		final ConfigurableAuthService auth = new ConfigurableAuthService(
				new AuthConfig().withKBaseAuthServerURL(
						TestCommon.getAuthUrl()));
		final AuthToken t1 = TestCommon.getToken(1, auth);
		USER = auth.getUserFromToken(t1);

		MONGO = new MongoController(
				TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " + MONGO.getTempDir());
		String mongohost = "localhost:" + MONGO.getServerPort();
		System.out.println("mongo on " + mongohost);
		
		WS = startupWorkspaceServer(mongohost, WS_DB_NAME, "ws_types",
				t1, t1.getUserName());
		final int wsport = WS.getServerPort();
		WSC = new WorkspaceClient(new URL("http://localhost:" + wsport), t1);
		WSC.setIsInsecureHttpConnectionAllowed(true);
	}
	
	@AfterClass
	public static void afterClass() throws Exception {
		if (MONGO != null) {
			MONGO.destroy(TestCommon.getDeleteTempFiles());
		}
		if (WS != null) {
			WS.stopServer();
		}
	}
	
	@Before
	public void before() throws Exception {
		DB db = GetMongoDB.getDB("localhost:" + MONGO.getServerPort(),
				WS_DB_NAME);
		TestCommon.destroyDB(db);
	}

	@Test
	public void testGetWSJobWithoutWs() throws Exception {
		List<String> mtl = new LinkedList<String>();
		//setup ujs and client
		UserAndJobStateServer ujs = startUpUJSServer(
				"localhost:" + MONGO.getServerPort(),
				"http://localhost:" + WS.getServerPort(),
				"ujs", USER.getToken());
		UserAndJobStateClient cli = new UserAndJobStateClient(
				new URL("http://localhost:" + ujs.getServerPort()),
				USER.getToken());
		cli.setIsInsecureHttpConnectionAllowed(true);
		
		//create a job associated with a ws
		WSC.createWorkspace(new CreateWorkspaceParams().withWorkspace("foo"));
		String id = createJobForTest(cli);
		String cid1 = createJobForTest(cli);
		String cid2 = createJobForTest(cli);
		String did1 = createJobForTest(cli, true);
		String did2 = createJobForTest(cli);
		String did3 = createJobForTest(cli, true);
		String did4 = createJobForTest(cli);
		
		//check job is accessible
		checkJob(cli, id, USER.getUserId(), null, "started", "stat",
				USER.getUserId(), "desc", "none", null, null, null, 0L, 0L,
				null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", cli.getJobOwner(id), is(USER.getUserId()));
		assertThat("shared list ok", cli.getJobShared(id), is(mtl));
		cli.cancelJob(cid1, "can1");
		deleteJob(cli, did1);
		deleteJob(cli, did2, cli.getToken().getToken());
		
		//start up ujs without a ws link
		ujs.stopServer();
		UserAndJobStateServer.clearConfigForTests();
		ujs = startUpUJSServer("localhost:" + MONGO.getServerPort(),
				null, "ujs", USER.getToken());
		cli = new UserAndJobStateClient(
				new URL("http://localhost:" + ujs.getServerPort()),
				USER.getToken());
		cli.setIsInsecureHttpConnectionAllowed(true);
		
		//fail to get / cancel / delete jobs
		failGetJob(cli, id, String.format(
				"There is no job %s viewable by user %s",
				id, USER.getUserId()));
		failGetJobOwner(cli, id, String.format(
				"There is no job %s viewable by user %s",
				id, USER.getUserId()));
		failGetJobShared(cli, id, String.format(
				"There is no job %s viewable by user %s",
				id, USER.getUserId()));
		failCancelJob(cli, cid2, "can2", String.format(
				"There is no job %s that may be canceled by user %s",
				cid2, USER.getUserId()));
		failToDeleteJob(cli, did3, String.format(
				"There is no deletable job %s for user %s",
				did3, USER.getUserId()));
		failToDeleteJob(cli, did4, cli.getToken().getToken(), String.format(
				"There is no deletable job %s for user %s and service %s",
				did4, USER.getUserId(), USER.getUserId()));
		
		//set up UJS with a WS link again
		ujs.stopServer();
		UserAndJobStateServer.clearConfigForTests();
		ujs = startUpUJSServer(
				"localhost:" + MONGO.getServerPort(),
				"http://localhost:" + WS.getServerPort(),
				"ujs", USER.getToken());
		cli = new UserAndJobStateClient(
				new URL("http://localhost:" + ujs.getServerPort()),
				USER.getToken());
		cli.setIsInsecureHttpConnectionAllowed(true);
		
		//check jobs are accessible again
		checkJob(cli, id, USER.getUserId(), null, "started", "stat",
				USER.getUserId(), "desc", "none", null, null, null, 0L, 0L,
				null, null, KBWS, "1", MTMAP);
		assertThat("owner ok", cli.getJobOwner(id), is(USER.getUserId()));
		assertThat("shared list ok", cli.getJobShared(id), is(mtl));
		cli.cancelJob(cid2, "stat");
		deleteJob(cli, did3);
		deleteJob(cli, did4, cli.getToken().getToken());
		
		//stop UJS
		ujs.stopServer();
		
	}

	private String createJobForTest(final UserAndJobStateClient cli)
			throws Exception {
		return createJobForTest(cli, false);
	}
	
	private String createJobForTest(
			final UserAndJobStateClient cli,
			final boolean complete)
			throws Exception {
		String id = cli.createJob2(new CreateJobParams().withAuthstrat(KBWS)
				.withAuthparam("1"));
		cli.startJob(id, USER.getTokenString(), "stat", "desc",
				new InitProgress().withPtype("none"), null);
		if (complete) {
			cli.completeJob(id, USER.getTokenString(), "stat", null, null);
		}
		return id;
	}
}