package us.kbase.userandjobstate.test.authorization;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.DB;
import com.mongodb.DBCollection;

import us.kbase.common.exceptions.UnimplementedException;
import us.kbase.common.mongo.GetMongoDB;
import us.kbase.common.schemamanager.SchemaManager;
import us.kbase.common.test.controllers.mongo.MongoController;
import us.kbase.userandjobstate.authorization.AuthorizationStrategy;
import us.kbase.userandjobstate.authorization.DefaultUJSAuthorizer;
import us.kbase.userandjobstate.authorization.UJSAuthorizer;
import us.kbase.userandjobstate.authorization.exceptions.UJSAuthorizationException;
import us.kbase.userandjobstate.jobstate.Job;
import us.kbase.userandjobstate.jobstate.JobState;
import us.kbase.userandjobstate.test.UserJobStateTestCommon;

public class AuthorizationTest {

	private static final String DB_NAME = "AuthTests";

	private static MongoController mongo;
	
	private static JobState js;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		mongo = new MongoController(
				UserJobStateTestCommon.getMongoExe(),
				Paths.get(UserJobStateTestCommon.getTempDir()));
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
			mongo.destroy(UserJobStateTestCommon.getDeleteTempFiles());
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
			if (strat.getStrat().equals("fail")) {
				throw new UJSAuthorizationException("strat fail");
			}
			if (authParam.equals("fail")) {
				throw new UJSAuthorizationException("param fail");
			}
		}

		@Override
		protected void externallyAuthorizeRead(AuthorizationStrategy strat,
				String user, String authParam, Job j)
				throws UJSAuthorizationException {
			if (strat.getStrat().equals("fail")) {
				throw new UJSAuthorizationException("strat fail");
			}
			if (authParam.equals("fail")) {
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
		
		failCreate(la, new AuthorizationStrategy("fail"), "bar",
				new UJSAuthorizationException("strat fail"));
		failCreate(la, new AuthorizationStrategy("whoo"), "fail",
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
		
		AuthorizationStrategy def = new AuthorizationStrategy("DEFAULT");
		
		DefaultUJSAuthorizer dua = new DefaultUJSAuthorizer();
		//should work
		dua.authorizeRead(def, user1, "foo", j);
		
		failSingleRead(def, user2, "foo", j,
				new UJSAuthorizationException(String.format(
						"Job %s is not viewable by user %s",
						j.getID(), user2)));
		
		js.shareJob(user1, j.getID(), Arrays.asList(user2));
		j = js.getJob(user1, j.getID());
		dua.authorizeRead(def, user2, "foo", j);
		
		failSingleRead(new AuthorizationStrategy("bar"), "user1", "foo", j,
				new UnimplementedException());
		
		failSingleRead(null, "user1", "foo", j,
				new NullPointerException());
		
		failSingleRead(def, user2, "foo", null,
				new NullPointerException("job cannot be null"));
		
		failSingleRead(def, user2, null, j, new IllegalArgumentException(
				"authParam cannot be null or empty"));
		failSingleRead(def, user2, "", j, new IllegalArgumentException(
				"authParam cannot be null or empty"));
		
		failSingleRead(def, null, "boo", j, new IllegalArgumentException(
				"user cannot be null or empty"));
		failSingleRead(def, "", "boo", j, new IllegalArgumentException(
				"user cannot be null or empty"));
		
		LenientAuth la = new LenientAuth();
		//should work:
		la.authorizeRead(new AuthorizationStrategy("foo"), user1, "bar", j);
		
		failSingleRead(la, new AuthorizationStrategy("fail"), user1, "bar", j,
				new UJSAuthorizationException("strat fail"));
		failSingleRead(la, new AuthorizationStrategy("whoo"), user1, "fail", j,
				new UJSAuthorizationException("param fail"));
	}
	
	private void failSingleRead(AuthorizationStrategy as, String user,
			String authParam, Job j, Exception exp) {
		failSingleRead(new DefaultUJSAuthorizer(), as, user, authParam, j, exp);
	}
	
	private void failSingleRead(UJSAuthorizer auth, AuthorizationStrategy as,
			String user, String authParam, Job j,
			Exception exp) {
		try {
			auth.authorizeRead(as, user, authParam, j);
			fail("authorized bad read");
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

	private static void assertExceptionCorrect(
			Exception got, Exception expected) {
		assertThat("incorrect exception. trace:\n" +
				ExceptionUtils.getStackTrace(got),
				got.getLocalizedMessage(),
				is(expected.getLocalizedMessage()));
		assertThat("incorrect exception type", got, is(expected.getClass()));
	}
	
	
}
