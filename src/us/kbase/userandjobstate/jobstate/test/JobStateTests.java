package us.kbase.userandjobstate.jobstate.test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Date;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import us.kbase.common.test.RegexMatcher;
import us.kbase.userandjobstate.jobstate.Job;
import us.kbase.userandjobstate.jobstate.JobState;
import us.kbase.userandjobstate.jobstate.exceptions.NoSuchJobException;
import us.kbase.userandjobstate.test.UserJobStateTestCommon;

public class JobStateTests {
	
	private static JobState js;
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		UserJobStateTestCommon.destroyAndSetupDB();
		
		String host = UserJobStateTestCommon.getHost();
		String mUser = UserJobStateTestCommon.getMongoUser();
		String mPwd = UserJobStateTestCommon.getMongoPwd();
		String db = UserJobStateTestCommon.getDB();
		
		if (mUser != null) {
			js = new JobState(host, db, "jobstate", mUser, mPwd);
		} else {
			js = new JobState(host, db, "jobstate");
		}
	}
	
	//TODO JSONRPC layer tests
	
	private static final RegexMatcher OBJ_ID_MATCH = new RegexMatcher("[\\da-f]{24}");
	
	@Test
	public void createJob() throws Exception {
		try {
			js.createJob(null);
			fail("created job with invalid user");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(), 
					is("user cannot be null or the empty string"));
		}
		try {
			js.createJob("");
			fail("created job with invalid user");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(), 
					is("user cannot be null or the empty string"));
		}
		String jobid = js.createJob("foo");
		assertThat("get job id", jobid, OBJ_ID_MATCH);
		Job j = js.getJob("foo", jobid);
		checkJob(j, jobid, "created", "foo", null, null, null, null, null, null, null,
				null, null);
		try {
			js.getJob("foo1", jobid);
			fail("Got a non-existant job");
		} catch (NoSuchJobException nsje) {
			assertThat("correct exception", nsje.getLocalizedMessage(),
					is(String.format("There is no job %s for user foo1", jobid)));
		}
		try {
			js.getJob("foo", "a" + jobid.substring(1));
			fail("Got a non-existant job");
		} catch (NoSuchJobException nsje) {
			assertThat("correct exception", nsje.getLocalizedMessage(),
					is(String.format("There is no job %s for user foo",
					"a" + jobid.substring(1))));
		}
		try {
			js.getJob("foo", "a" + jobid);
			fail("Got a job with a bad id");
		} catch (IllegalArgumentException iae) {
			assertThat("correct exception", iae.getLocalizedMessage(),
					is(String.format("Job ID %s is not a legal ID",
					"a" + jobid)));
		}
	}
	
	@Test
	public void startJob() throws Exception {
		String jobid = js.createJob("foo");
		js.startJob("foo", jobid, "serv1", "started job", "job desc");
		Job j = js.getJob("foo", jobid);
		checkJob(j, jobid, "started", "foo", "started job", "serv1", "job desc", "none",
				null, null, false, false, null);
		testStartJobBadArgs("foo", jobid, "serv2", "started job", "job desc",
				new NoSuchJobException(String.format(
						"There is no unstarted job %s for user foo", jobid)));
		testStartJobBadArgs("foo1", jobid, "serv2", "started job", "job desc",
				new NoSuchJobException(String.format(
						"There is no unstarted job %s for user foo1", jobid)));
		testStartJobBadArgs("foo", "a" + jobid.substring(1), "serv2", "started job", "job desc",
				new NoSuchJobException(String.format(
						"There is no unstarted job %s for user foo", "a" + jobid.substring(1))));
		testStartJobBadArgs(null, jobid, "serv2", "started job", "job desc",
				new IllegalArgumentException("user cannot be null or the empty string"));
		testStartJobBadArgs("", jobid, "serv2", "started job", "job desc",
				new IllegalArgumentException("user cannot be null or the empty string"));
		//TODO more start job tests, all prog types, bad args
	}
	
	private void testStartJobBadArgs(String user, String jobid, String service,
			String status, String desc, Exception exception) throws Exception {
		try {
			js.startJob(user, jobid, service, status, desc);
			fail("Started job with bad args");
		} catch (Exception e) {
			assertThat("correct exception type", e,
					is(exception.getClass()));
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
		try {
			js.startJobWithPercentProg(user, jobid, service, status, desc);
			fail("Started job with bad args");
		} catch (Exception e) {
			assertThat("correct exception type", e,
					is(exception.getClass()));
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
		try {
			js.startJob(user, jobid, service, status, desc, 6);
			fail("Started job with bad args");
		} catch (Exception e) {
			assertThat("correct exception type", e,
					is(exception.getClass()));
			assertThat("correct exception msg", e.getLocalizedMessage(),
					is(exception.getLocalizedMessage()));
		}
		
	}
	
	private void checkJob(Job j, String id, String stage, String user,
			String status, String service, String desc, String progtype,
			Integer prog, Integer maxproj, Boolean complete, Boolean error,
			Map<String, Object> results) {
		assertThat("job id ok", j.getID(), is(id));
		assertThat("job stage ok", j.getStage(), is(stage));
		assertThat("job user ok", j.getUser(), is(user));
		assertThat("job service ok", j.getService(), is(service));
		assertThat("job desc ok", j.getDescription(), is(desc));
		assertThat("job progtype ok", j.getProgType(), is(progtype));
		assertThat("job prog ok", j.getProgress(), is(prog));
		assertThat("job maxprog ok", j.getMaxProgress(), is(maxproj));
		assertThat("job status ok", j.getStatus(), is(status));
		assertThat("job updated ok", j.getLastUpdated(), is(Date.class));
		assertThat("job complete ok", j.isComplete(), is(complete));
		assertThat("job error ok", j.hasError(), is(error));
	}
}
