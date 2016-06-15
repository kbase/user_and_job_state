package us.kbase.userandjobstate.authorization;

import java.util.List;

import us.kbase.userandjobstate.authorization.exceptions.UJSAuthorizationException;
import us.kbase.userandjobstate.jobstate.Job;

public abstract class UJSAuthorizer {
	
	//TODO NOW tests
	
	public static final AuthorizationStrategy DEFAULT_AUTHSTRAT = 
			new AuthorizationStrategy("DEFAULT");
	
	// unused so really doesn't matter
	public static final String DEFAULT_AUTH_PARAM = "DEFAULT";

	private void checkAuthParam(final String authParam) {
		if (authParam == null || authParam.isEmpty()) {
			throw new IllegalArgumentException(
					"authParam cannot be null or empty");
		}
	}
	
	private void checkUser(final String user) {
		if (user == null || user.isEmpty()) {
			throw new IllegalArgumentException(
					"user cannot be null or empty");
		}
	}
	
	public void authorizeCreate(
			final AuthorizationStrategy strat,
			final String authParam)
			throws UJSAuthorizationException {
		checkAuthParam(authParam);
		if (strat.equals(DEFAULT_AUTHSTRAT)) {
			// authorized, since anyone can create a job. Do nothing.
		} else {
			externallyAuthorizeCreate(strat, authParam);
		}
	}
	
	public abstract void externallyAuthorizeCreate(
			final AuthorizationStrategy strat,
			final String authParam)
			throws UJSAuthorizationException;

	public void authorizeRead(
			final AuthorizationStrategy strat,
			final String user,
			final String authParam,
			final Job j)
			throws UJSAuthorizationException {
		checkAuthParam(authParam);
		checkUser(user);
		if (j == null) {
			throw new NullPointerException("job cannot be null");
		}
		if (strat.equals(DEFAULT_AUTHSTRAT)) {
			if (!user.equals(j.getUser()) && !j.getShared().contains(user)) {
				throw new UJSAuthorizationException(String.format(
						"Job %s is not viewable by user %s", j.getID(), user));
			}
		} else {
			externallyAuthorizeRead(strat, user, authParam, j);
		}
	}
	
	public abstract void externallyAuthorizeRead(
			final AuthorizationStrategy strat,
			final String user,
			final String authParam,
			final Job j)
			throws UJSAuthorizationException;

	public void authorizeRead(
			final AuthorizationStrategy strat,
			final String user,
			final List<String> authParams)
			throws UJSAuthorizationException {
		if (authParams == null || authParams.isEmpty()) {
			throw new IllegalArgumentException(
					"authParams cannot be null or emtpy");
		}
		for (final String p: authParams) {
			checkAuthParam(p);
		}
		if (strat.equals(DEFAULT_AUTHSTRAT)) {
			//do nothing. A user can read ujs jobs shared with him/her.
			//however, the job fetching logic needs to pull back the right
			//jobs.
		} else {
			externallyAuthorizeRead(strat, user, authParams);
		}
	}
	
	public abstract void externallyAuthorizeRead(
			final AuthorizationStrategy strat,
			final String user,
			final List<String> authParams)
			throws UJSAuthorizationException;

}