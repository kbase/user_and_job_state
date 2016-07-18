package us.kbase.userandjobstate.kbase;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.userandjobstate.authorization.AuthorizationStrategy;
import us.kbase.userandjobstate.authorization.UJSAuthorizer;
import us.kbase.userandjobstate.authorization.exceptions.UJSAuthorizationException;
import us.kbase.userandjobstate.jobstate.Job;
import us.kbase.workspace.GetPermissionsMassParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;

/** A factory for a UJS authorizer that uses the workspace as an authorization
 * source.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceAuthorizationFactory {
	
	//TODO ZZLATER add static cache
	//TODO ZZLATER should add retries to the auth client

	public static final AuthorizationStrategy WS_AUTH =
			new AuthorizationStrategy("kbaseworkspace");
	
	private final URL wsURL;
	private final boolean insecure;
	
	/** Construct the factory.
	 * @param workspaceURL the url of the workspace to contact.
	 * @throws JsonClientException if a workspace client exception occurs.
	 * @throws IOException if an IO exception occurs.
	 */
	public WorkspaceAuthorizationFactory(final URL workspaceURL)
			throws IOException, JsonClientException {
		if (workspaceURL == null) {
			throw new NullPointerException("workspaceURL");
		}
		final Logger l = LoggerFactory.getLogger(getClass());
		
		wsURL = workspaceURL;
		if (!wsURL.getProtocol().equals("https")) {
			insecure = true;
			LoggerFactory.getLogger(getClass()).warn("The workspace url " +
				wsURL + " is not secure. Use an https:// url if possible.");
		} else {
			insecure = false;
		}
		 //test the url is ok and log
		l.info("Connected to Workspace Service v" +
				new WorkspaceClient(wsURL).ver() + " at " + wsURL);
	}
	
	/** Get an authorizer using the given token.
	 * @param token the token for which to build and authorizer.
	 * @return a workspace authorizer.
	 * @throws UnauthorizedException if the token is invalid.
	 * @throws IOException if an IO exception occurs.
	 */
	public UJSAuthorizer buildAuthorizer(final AuthToken token)
			throws UnauthorizedException, IOException {
		if (token == null) {
			throw new NullPointerException("token");
		}
		return new WorkspaceAuthorizer(wsURL, token, insecure);
	}
	
	private static void checkStrat(final AuthorizationStrategy strat)
			throws UJSAuthorizationException {
		if (!WS_AUTH.equals(strat)) {
			throw new UJSAuthorizationException(
					"Invalid authorization strategy: " + strat.getStrat());
		}
	}
	
	private static class WorkspaceAuthorizer extends UJSAuthorizer {

		private static final List<String> CAN_READ =
				Collections.unmodifiableList(Arrays.asList("r", "w", "a"));
		private static final List<String> CAN_WRITE =
				Collections.unmodifiableList(Arrays.asList("w", "a"));
		private static final int MAX_WS_COUNT = 10;
		
		private WorkspaceClient client;
		private String username;
		
		private WorkspaceAuthorizer(
				final URL wsURL,
				final AuthToken token,
				final boolean insecure)
				throws UnauthorizedException, IOException {
			username = token.getUserName();
			client = new WorkspaceClient(wsURL, token);
			if (insecure) {
				client.setIsInsecureHttpConnectionAllowed(true);
			}
		}
		
		private void checkWSUser(final String user) {
			if (!user.equals(username)) {
				throw new IllegalStateException(
						"A programming error occured: the token username " +
						"and the supplied username do not match");
			}
		}
		
		private long parseWsid(final String wsid)
				throws UJSAuthorizationException {
			try {
				return Long.parseLong(wsid);
			} catch (NumberFormatException e) {
				throw new UJSAuthorizationException(String.format(
						"The string %s is not a valid integer workspace ID",
						wsid));
			}
		}
		
		private List<Long> parseWsid(final List<String> wsids)
				throws UJSAuthorizationException {
			final List<Long> ret = new ArrayList<Long>();
			for (final String id: wsids) {
				ret.add(parseWsid(id));
			}
			return ret;
		}
		
		private List<Map<String, String>> getPerms(final String wsid)
				throws UJSAuthorizationException {
			return getPerms(Arrays.asList(wsid));
		}

		private List<Map<String, String>> getPerms(final List<String> wsids)
				throws UJSAuthorizationException {
			final List<WorkspaceIdentity> wsis =
					new LinkedList<WorkspaceIdentity>();
			for (final Long id: parseWsid(wsids)) {
				wsis.add(new WorkspaceIdentity().withId(id.longValue()));
			}
			try {
				return client.getPermissionsMass(new GetPermissionsMassParams()
					.withWorkspaces(wsis)).getPerms();
			} catch (IOException | JsonClientException e) {
				throw new UJSAuthorizationException(
						"Error contacting the workspace service to get permissions: " +
								e.getLocalizedMessage(), e);
			}
		}

		@Override
		protected void externallyAuthorizeCreate(
				final AuthorizationStrategy strat,
				final String authParam)
				throws UJSAuthorizationException {
			checkStrat(strat);
			final String p = getPerms(authParam).get(0).get(username);
			if (!CAN_WRITE.contains(p)) {
				throw new UJSAuthorizationException(String.format(
						"User %s cannot write to workspace %s",
						username, authParam));
			}
		}

		@Override
		protected void externallyAuthorizeRead(
				final String user,
				final Job j)
				throws UJSAuthorizationException {
			checkWSUser(user);
			checkStrat(j.getAuthorizationStrategy());
			if (user.equals(j.getUser())) {
				return; // owner can always read job
			}
			final String p = getPerms(j.getAuthorizationParameter())
					.get(0).get(username);
			if (!CAN_READ.contains(p)) {
				throw new UJSAuthorizationException(String.format(
						"User %s cannot read workspace %s",
						username, j.getAuthorizationParameter()));
			}
		}

		@Override
		protected void externallyAuthorizeRead(
				final AuthorizationStrategy strat,
				final String user,
				final List<String> authParams)
				throws UJSAuthorizationException {
			checkWSUser(user);
			checkStrat(strat);
			if (authParams.size() > MAX_WS_COUNT) {
				throw new UJSAuthorizationException(String.format(
						"No more than %s workspace IDs may be specified",
						MAX_WS_COUNT));
			}
			final List<Map<String, String>> perms = getPerms(authParams);
			for (int i = 0; i < authParams.size(); i ++) {
				final String p = perms.get(i).get(username);
				if (!CAN_READ.contains(p)) {
					throw new UJSAuthorizationException(String.format(
							"User %s cannot read workspace %s",
							username, authParams.get(i)));
				}
			}
		}
		
		@Override
		protected void externallyAuthorizeCancel(
				final String user,
				final Job j)
				throws UJSAuthorizationException {
			//TODO NOW
		}
		
	}

}
