package us.kbase.userandjobstate.authorization;

import java.util.List;

import us.kbase.common.exceptions.UnimplementedException;
import us.kbase.userandjobstate.authorization.exceptions.UJSAuthorizationException;
import us.kbase.userandjobstate.jobstate.Job;

/** Throws an exception for anything that isn't the default authorization type.
 * @author gaprice@lbl.gov
 *
 */
public class DefaultUJSAuthorizer extends UJSAuthorizer {

	@Override
	protected void externallyAuthorizeCreate(
			final AuthorizationStrategy strat,
			final String authParam)
			throws UJSAuthorizationException {
		throw new UnimplementedException();
	}

	@Override
	protected void externallyAuthorizeRead(final String user, final Job j)
			throws UJSAuthorizationException {
		throw new UnimplementedException();
	}

	@Override
	protected void externallyAuthorizeRead(
			final AuthorizationStrategy strat,
			final String user,
			final List<String> authParams)
			throws UJSAuthorizationException {
		throw new UnimplementedException();
	}

	@Override
	protected void externallyAuthorizeCancel(final String user, final Job j)
			throws UJSAuthorizationException {
		throw new UnimplementedException();
	}

	@Override
	protected void externallyAuthorizeDelete(final String user, final Job j)
			throws UJSAuthorizationException {
		throw new UnimplementedException();
	}
}
