package us.kbase.userandjobstate.authorization;

import java.util.List;

import us.kbase.common.exceptions.UnimplementedException;
import us.kbase.userandjobstate.authorization.exceptions.UJSAuthorizationException;
import us.kbase.userandjobstate.jobstate.Job;

public class DefaultUJSAuthorizer extends UJSAuthorizer {

	@Override
	public void externallyAuthorizeCreate(
			final AuthorizationStrategy strat,
			final String authParam)
			throws UJSAuthorizationException {
		throw new UnimplementedException();
	}

	@Override
	public void externallyAuthorizeRead(
			final AuthorizationStrategy strat,
			final String user,
			final String authParam,
			final Job j)
			throws UJSAuthorizationException {
		throw new UnimplementedException();
	}

	@Override
	public void externallyAuthorizeRead(
			final AuthorizationStrategy strat,
			final String user,
			final List<String> authParams)
			throws UJSAuthorizationException {
		throw new UnimplementedException();
	}
}
