package us.kbase.userandjobstate.test.jobstate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import us.kbase.userandjobstate.jobstate.JobResult;
import us.kbase.userandjobstate.jobstate.JobResults;

public class JobResultsTest {
	
	private static String s1001 = "";
	static {
		String s = "0123456789";
		for (int i = 0; i < 100; i++) {
			s1001 += s;
		}
		s1001 += "a";
	}
	
	@Test
	public void failCreateJobResult() {
		failCreateJobResult("s", "u", null, null);
		failCreateJobResult("s", null, "i", null);
		failCreateJobResult(null, "u", "i", null);
	}
	
	private void failCreateJobResult(String servtype, String url, String id,
			String desc) {
		try {
			new JobResult(servtype, url, id, desc);
			fail("created a jobresult with bad input");
		} catch (NullPointerException npe) {
			assertThat("got correct exception", npe.getMessage(),
					is("The servtype, url, and id fields are required for a job result"));
		}
	}
	
	@Test
	public void createAndCheckJobResult() {
		JobResult jr = new JobResult("s", "u", "i", null);
		assertThat("servtype ok", jr.getServtype(), is("s"));
		assertThat("url ok", jr.getUrl(), is("u"));
		assertThat("id ok", jr.getId(), is("i"));
		assertNull("desc ok", jr.getDesc());
		
		jr = new JobResult("s", "u", "i", "d");
		assertThat("desc ok", jr.getDesc(), is("d"));
	}
	
	@Test
	public void createAndCheckJobResults() {
		List<JobResult> jrl = new LinkedList<JobResult>();
		jrl.add(new JobResult("s", "u", "i", "foO"));
		
		List<JobResult> jrlexp = new LinkedList<JobResult>();
		jrlexp.add(new JobResult("s", "u", "i", "foO"));
		
		JobResults jrs = new JobResults(jrl, "ws url",
				Arrays.asList("ws1", "ws2"), "shock url",
				Arrays.asList("sh1", "sh2"));
		
		assertThat("results ok", jrs.getResults(), is(jrlexp));
		assertThat("ws url ok", jrs.getWorkspaceurl(), is("ws url"));
		assertThat("ws ids ok", jrs.getWorkspaceids(), is(Arrays.asList("ws1", "ws2")));
		assertThat("shock url ok", jrs.getShockurl(), is("shock url"));
		assertThat("shock ids ok", jrs.getShocknodes(), is(Arrays.asList("sh1", "sh2")));
		
		
		jrs = new JobResults(null, null, null, null, null);
		assertNull("results null", jrs.getResults());
		assertNull("ws url null", jrs.getWorkspaceurl());
		assertNull("ws ids null", jrs.getWorkspaceids());
		assertNull("shock url null", jrs.getShockurl());
		assertNull("shock ids null", jrs.getShocknodes());
		
		jrs = new JobResults(new LinkedList<JobResult>(), "ws url",
				new LinkedList<String>(), "shock url",
				new LinkedList<String>());
		
		assertThat("results ok", jrs.getResults(), is(
				(List<JobResult>) new LinkedList<JobResult>()));
		assertThat("ws url ok", jrs.getWorkspaceurl(), is("ws url"));
		assertThat("ws ids ok", jrs.getWorkspaceids(), is(
				(List<String>) new LinkedList<String>()));
		assertThat("shock url ok", jrs.getShockurl(), is("shock url"));
		assertThat("shock ids ok", jrs.getShocknodes(), is(
				(List<String>) new LinkedList<String>()));
	}
	
	@Test
	public void failCreateJobResults() {
		List<String> nullarray = new LinkedList<String>();
		nullarray.add(null);
		List<String> mtarray = Arrays.asList("");
		List<String> lngarray = Arrays.asList(s1001);
		
		failCreateJobResults("", null, null, null, "workspaceurl cannot be the empty string");
		failCreateJobResults(null, null, "", null, "shockurl cannot be the empty string");
		failCreateJobResults(s1001, null, null, null, "workspaceurl exceeds the maximum length of 1000");
		failCreateJobResults(null, null, s1001, null, "shockurl exceeds the maximum length of 1000");
		failCreateJobResults(null, nullarray, null, null, "workspaceid cannot be null or the empty string");
		failCreateJobResults(null, null, null, nullarray, "shocknode cannot be null or the empty string");
		failCreateJobResults(null, mtarray, null, null, "workspaceid cannot be null or the empty string");
		failCreateJobResults(null, null, null, mtarray, "shocknode cannot be null or the empty string");
		failCreateJobResults(null, lngarray, null, null, "workspaceid exceeds the maximum length of 1000");
		failCreateJobResults(null, null, null, lngarray, "shocknode exceeds the maximum length of 1000");
		
	}
	
	private void failCreateJobResults(String wsurl, List<String> wsids,
			String shockurl, List<String> shockids, String exp) {
		try {
			new JobResults(null, wsurl, wsids, shockurl, shockids);
			fail("created bad job results");
		} catch (IllegalArgumentException iae) {
			assertThat("got correct exception", iae.getMessage(),
					is(exp));
		}
	}
	
	@Test
	public void failMutate() {
		List<JobResult> jrl = new LinkedList<JobResult>();
		jrl.add(new JobResult("s", "u", "i", "foO"));
		
		JobResults jrs = new JobResults(jrl, "ws url",
				Arrays.asList("ws1", "ws2"), "shock url",
				Arrays.asList("sh1", "sh2"));
		
		try {
			jrs.getResults().add(new JobResult("s", "u", "i", null));
			fail("able to mutate jobresults contents");
		} catch (UnsupportedOperationException e) {
		}
		try {
			jrs.getWorkspaceids().add("foo");
			fail("able to mutate jobresults contents");
		} catch (UnsupportedOperationException e) {
		}
		try {
			jrs.getShocknodes().add("foo");
			fail("able to mutate jobresults contents");
		} catch (UnsupportedOperationException e) {
		}
	}

}
