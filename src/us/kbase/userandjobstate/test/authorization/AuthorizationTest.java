package us.kbase.userandjobstate.test.authorization;

import static org.junit.Assert.fail;

import static us.kbase.common.test.TestCommon.assertExceptionCorrect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;
import com.mongodb.DBCollection;

import us.kbase.common.exceptions.UnimplementedException;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.schemamanager.SchemaManager;
import us.kbase.common.test.TestCommon;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.userandjobstate.authorization.AuthorizationStrategy;
import us.kbase.userandjobstate.authorization.DefaultUJSAuthorizer;
import us.kbase.userandjobstate.authorization.UJSAuthorizer;
import us.kbase.userandjobstate.authorization.exceptions.UJSAuthorizationException;
import us.kbase.userandjobstate.jobstate.Job;
import us.kbase.userandjobstate.jobstate.JobState;

public class AuthorizationTest {

	private static final String DB_NAME = "AuthTests";

	private static MongoController mongo;
	
	private static JobState js;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		mongo = new MongoController(
				TestCommon.getMongoExe(),
				Paths.get(TestCommon.getTempDir()));
		System.out.println("Using Mongo temp dir " + mongo.getTempDir());
		
		final DB db = GetMongoDB.getDB(
				"localhost:" + mongo.getServerPort(), DB_NAME, 0, 0);
		DBCollection jobcol = db.getCollection("jobstate");
		DBCollection schemacol = db.getCollection("schema");
		js = new JobState(jobcol, new SchemaManager(schemacol));
				
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		if (mongo != null) {
			mongo.destroy(TestCommon.getDeleteTempFiles());
		}
	}
	
	@Test
	public void testStrat() throws Exception {
		new AuthorizationStrategy("foo"); //should work
		
		failMakeAuthStrat(null, "strategy cannot be null or empty");
		failMakeAuthStrat("", "strategy cannot be null or empty");
	}
	
	private void failMakeAuthStrat(String strat, String exp) {
		try {
			new AuthorizationStrategy(strat);
			fail("bad auth strat");
		} catch (IllegalArgumentException got) {
			assertExceptionCorrect(got, new IllegalArgumentException(exp));
		}
	}
	
	private static class LenientAuth extends UJSAuthorizer {

		@Override
		protected void externallyAuthorizeCreate(AuthorizationStrategy strat,
				String authParam) throws UJSAuthorizationException {
			if (strat.getStrat().equals("create fail")) {
				throw new UJSAuthorizationException("strat fail");
			}
			if (authParam.equals("create fail")) {
				throw new UJSAuthorizationException("param fail");
			}
		}

		@Override
		protected void externallyAuthorizeRead(String user, Job j)
				throws UJSAuthorizationException {
			if (j.getAuthorizationStrategy().getStrat().equals("fail")) {
				throw new UJSAuthorizationException("strat fail");
			}
			if (j.getAuthorizationParameter().equals("fail")) {
				throw new UJSAuthorizationException("param fail");
			}
		}

		@Override
		protected void externallyAuthorizeRead(AuthorizationStrategy strat,
				String user, List<String> authParams)
				throws UJSAuthorizationException {
			if (strat.getStrat().equals("fail")) {
				throw new UJSAuthorizationException("strat fail");
			}
			if (authParams.contains("fail")) {
				throw new UJSAuthorizationException("param fail");
			}
		}

		@Override
		protected void externallyAuthorizeCancel(String user, Job j)
				throws UJSAuthorizationException {
			if (j.getAuthorizationStrategy().getStrat().equals("fail")) {
				throw new UJSAuthorizationException("strat fail");
			}
			if (j.getAuthorizationParameter().equals("fail")) {
				throw new UJSAuthorizationException("param fail");
			}
		}
		
		@Override
		protected void externallyAuthorizeDelete(String user, Job j)
				throws UJSAuthorizationException {
			if (j.getAuthorizationStrategy().getStrat().equals("fail")) {
				throw new UJSAuthorizationException("strat fail");
			}
			if (j.getAuthorizationParameter().equals("fail")) {
				throw new UJSAuthorizationException("param fail");
			}
		}
	}

	@Test
	public void testCreate() throws Exception {
		AuthorizationStrategy def = new AuthorizationStrategy("DEFAULT");
		
		//should work
		new DefaultUJSAuthorizer().authorizeCreate(def, "foo");
		
		failCreate(new AuthorizationStrategy("foo"), "n",
				new UnimplementedException());
		failCreate(null, "n",
				new NullPointerException());
		
		failCreate(def, null, new IllegalArgumentException(
				"authParam cannot be null or empty"));
		failCreate(def, "", new IllegalArgumentException(
				"authParam cannot be null or empty"));
		
		LenientAuth la = new LenientAuth();
		//should work:
		la.authorizeCreate(new AuthorizationStrategy("foo"), "bar");
		
		failCreate(la, new AuthorizationStrategy("create fail"), "bar",
				new UJSAuthorizationException("strat fail"));
		failCreate(la, new AuthorizationStrategy("whoo"), "create fail",
				new UJSAuthorizationException("param fail"));
	}
	
	private void failCreate(AuthorizationStrategy as, String authParam,
			Exception exp)
			throws Exception {
		failCreate(new DefaultUJSAuthorizer(), as, authParam, exp);
	}
	
	private void failCreate(UJSAuthorizer auth, AuthorizationStrategy as,
			String authParam, Exception exp)
			throws Exception {
		try {
			auth.authorizeCreate(as, authParam);
			fail("incorrectly authorized create");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
	}
	
	@Test
	public void testSingleRead() throws Exception {
		String user1 = "foo";
		String user2 = "bar";
		Job j = js.getJob(user1, js.createJob(user1));
		
		DefaultUJSAuthorizer dua = new DefaultUJSAuthorizer();
		//should work
		dua.authorizeRead(user1, j);
		
		failSingleRead(user2, j, new UJSAuthorizationException(String.format(
				"Job %s is not viewable by user %s", j.getID(), user2)));
		
		js.shareJob(user1, j.getID(), Arrays.asList(user2));
		j = js.getJob(user1, j.getID());
		dua.authorizeRead(user2, j);
		
		failSingleRead(user2, null,
				new NullPointerException("job cannot be null"));
		
		failSingleRead(null, j, new IllegalArgumentException(
				"user cannot be null or empty"));
		failSingleRead("", j, new IllegalArgumentException(
				"user cannot be null or empty"));
		
		LenientAuth la = new LenientAuth();
		Job j2 = createJob(user1, new AuthorizationStrategy("foo"), "bar");
		//should work:
		la.authorizeRead(user1, j2);
		failSingleRead(dua, user1, j2, new UnimplementedException());
		
		Job j3 = createJob(user1, new AuthorizationStrategy("fail"), "bar");
		failSingleRead(la, user1, j3,
				new UJSAuthorizationException("strat fail"));
		
		Job j4 = createJob(user1, new AuthorizationStrategy("whoo"), "fail");
		failSingleRead(la, user1, j4,
				new UJSAuthorizationException("param fail"));
	}
	
	@Test
	public void testCancel() throws Exception {
		String user1 = "foo";
		String user2 = "bar";
		Job j = js.getJob(user1, js.createJob(user1));
		
		DefaultUJSAuthorizer dua = new DefaultUJSAuthorizer();
		//should work
		dua.authorizeCancel(user1, j);
		
		failCancel(user2, j, new UJSAuthorizationException(String.format(
				"User %s may not cancel job %s", user2, j.getID())));
		
		// sharing jobs should not effect cancellation
		js.shareJob(user1, j.getID(), Arrays.asList(user2));
		j = js.getJob(user1, j.getID());
		failCancel(user2, j, new UJSAuthorizationException(String.format(
				"User %s may not cancel job %s", user2, j.getID())));
		
		failCancel(user2, null,
				new NullPointerException("job cannot be null"));
		
		failCancel(null, j, new IllegalArgumentException(
				"user cannot be null or empty"));
		failCancel("", j, new IllegalArgumentException(
				"user cannot be null or empty"));
		
		LenientAuth la = new LenientAuth();
		Job j2 = createJob(user1, new AuthorizationStrategy("foo"), "bar");
		//should work:
		la.authorizeCancel(user1, j2);
		failCancel(dua, user1, j2, new UnimplementedException());
		
		Job j3 = createJob(user1, new AuthorizationStrategy("fail"), "bar");
		failCancel(la, user1, j3, new UJSAuthorizationException("strat fail"));
		
		Job j4 = createJob(user1, new AuthorizationStrategy("whoo"), "fail");
		failCancel(la, user1, j4, new UJSAuthorizationException("param fail"));
	}
	
	@Test
	public void testDelete() throws Exception {
		String user1 = "foo";
		String user2 = "bar";
		Job j = js.getJob(user1, js.createJob(user1));
		
		DefaultUJSAuthorizer dua = new DefaultUJSAuthorizer();
		//should work
		dua.authorizeDelete(user1, j);
		
		failDelete(user2, j, new UJSAuthorizationException(String.format(
				"User %s may not delete job %s", user2, j.getID())));
		
		// sharing jobs should not effect deletion
		js.shareJob(user1, j.getID(), Arrays.asList(user2));
		j = js.getJob(user1, j.getID());
		failDelete(user2, j, new UJSAuthorizationException(String.format(
				"User %s may not delete job %s", user2, j.getID())));
		
		failDelete(user2, null,
				new NullPointerException("job cannot be null"));
		
		failDelete(null, j, new IllegalArgumentException(
				"user cannot be null or empty"));
		failDelete("", j, new IllegalArgumentException(
				"user cannot be null or empty"));
		
		LenientAuth la = new LenientAuth();
		Job j2 = createJob(user1, new AuthorizationStrategy("foo"), "bar");
		//should work:
		la.authorizeDelete(user1, j2);
		failDelete(dua, user1, j2, new UnimplementedException());
		
		Job j3 = createJob(user1, new AuthorizationStrategy("fail"), "bar");
		failDelete(la, user1, j3, new UJSAuthorizationException("strat fail"));
		
		Job j4 = createJob(user1, new AuthorizationStrategy("whoo"), "fail");
		failDelete(la, user1, j4, new UJSAuthorizationException("param fail"));
	}
	
	private Job createJob(
			final String user,
			final AuthorizationStrategy strat,
			final String authParam)
			throws Exception {
		Constructor<Job> jc = Job.class.getDeclaredConstructor();
		jc.setAccessible(true);
		Job j = jc.newInstance();
		
		Field id = j.getClass().getDeclaredField("_id");
		id.setAccessible(true);
		id.set(j, new ObjectId());
		
		Field u = j.getClass().getDeclaredField("user");
		u.setAccessible(true);
		u.set(j, user);
		
		Field s = j.getClass().getDeclaredField("authstrat");
		s.setAccessible(true);
		s.set(j, strat.getStrat());
		
		Field p = j.getClass().getDeclaredField("authparam");
		p.setAccessible(true);
		p.set(j, authParam);
		
		final Date d = new Date();
		Field up = j.getClass().getDeclaredField("updated");
		up.setAccessible(true);
		up.set(j, d);
		
		Field m = j.getClass().getDeclaredField("meta");
		m.setAccessible(true);
		m.set(j, new ArrayList<Map<String, String>>());
		
		return j;
	}

	private void failSingleRead(String user, Job j, Exception exp) {
		failSingleRead(new DefaultUJSAuthorizer(), user, j, exp);
	}
	
	private void failSingleRead(UJSAuthorizer auth, String user, Job j,
			Exception exp) {
		try {
			auth.authorizeRead(user, j);
			fail("authorized bad read");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
	}
	
	private void failCancel(String user, Job j, Exception exp) {
		failCancel(new DefaultUJSAuthorizer(), user, j, exp);
	}
	
	private void failCancel(UJSAuthorizer auth, String user, Job j,
			Exception exp) {
		try {
			auth.authorizeCancel(user, j);
			fail("authorized bad cancel");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
	}
	
	private void failDelete(String user, Job j, Exception exp) {
		failDelete(new DefaultUJSAuthorizer(), user, j, exp);
	}
	
	private void failDelete(UJSAuthorizer auth, String user, Job j,
			Exception exp) {
		try {
			auth.authorizeDelete(user, j);
			fail("authorized bad delete");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
	}
	
	@Test
	public void testMultipleRead() throws Exception {
		AuthorizationStrategy def = new AuthorizationStrategy("DEFAULT");
		String user1 = "foo";
		
		//should work
		new DefaultUJSAuthorizer().authorizeRead(def, user1,
				Arrays.asList("bar"));
		
		failMultipleRead(null, user1, Arrays.asList("bar"),
				new NullPointerException());
		failMultipleRead(new AuthorizationStrategy("foo"), user1,
				Arrays.asList("bar"), new UnimplementedException());
		
		failMultipleRead(def, null, Arrays.asList("bar"),
				new IllegalArgumentException("user cannot be null or empty"));
		failMultipleRead(def, "", Arrays.asList("bar"),
				new IllegalArgumentException("user cannot be null or empty"));

		failMultipleRead(def, user1, null,
				new IllegalArgumentException(
						"authParams cannot be null or empty"));
		failMultipleRead(def, user1, new LinkedList<String>(),
				new IllegalArgumentException(
						"authParams cannot be null or empty"));
		
		failMultipleRead(def, user1, Arrays.asList((String) null),
				new IllegalArgumentException(
						"authParam cannot be null or empty"));
		failMultipleRead(def, user1, Arrays.asList(""),
				new IllegalArgumentException(
						"authParam cannot be null or empty"));
		
		LenientAuth la = new LenientAuth();
		//should work:
		la.authorizeRead(new AuthorizationStrategy("foo"), user1,
				Arrays.asList("bar"));
		
		failMultipleRead(la, new AuthorizationStrategy("fail"), user1,
				Arrays.asList("bar"),
				new UJSAuthorizationException("strat fail"));
		failMultipleRead(la, new AuthorizationStrategy("whoo"), user1,
				Arrays.asList("fail"),
				new UJSAuthorizationException("param fail"));
	}

	private void failMultipleRead(AuthorizationStrategy as, String user,
			List<String> authParams, Exception exp) {
		failMultipleRead(new DefaultUJSAuthorizer(), as, user, authParams,
				exp);
	}
	
	private void failMultipleRead(UJSAuthorizer auth, AuthorizationStrategy as,
			String user, List<String> authParams,
			Exception exp) {
		try {
			auth.authorizeRead(as, user, authParams);
			fail("authorized bad read");
		} catch (Exception got) {
			assertExceptionCorrect(got, exp);
		}
		
	}
}
