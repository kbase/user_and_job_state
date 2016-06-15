package us.kbase.userandjobstate.updater;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

public class UjsUpdater {

	public static void main(String[] args) {
		
		final UpdateArgs ua = new UpdateArgs();
		final JCommander jc = new JCommander(ua);
		jc.setProgramName("db_update");
		try {
			jc.parse(args);
		} catch (ParameterException e) {
			System.out.println("Error: " + e.getLocalizedMessage());
			jc.usage();
			System.exit(1);
		}
	}

	@Parameters(commandDescription =
			"Update the UJS database to a version matching this codebase.")
	private static class UpdateArgs {
		@Parameter(names={"-d","--deploy"}, required = true,
				description="Path to the deploy.cfg file")
		String deploy;
		
		@Parameter(names={"-c","--commit"}, description="Make changes to " +
				"the database. If this parameter is not set, a dry run will " +
				"be performed.")
		boolean commit = false;
		
		@Parameter(names={"-v","--verbose"},
				description="Print error stacktraces.")
		boolean verbose = false;
	}
}
