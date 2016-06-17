/* 
User and Job State service (UJS)

Service for storing arbitrary key/object pairs on a per user per service basis
and storing job status so that a) long JSON RPC calls can report status and
UI elements can receive updates, and b) there's a centralized location for 
job status reporting.

There are two modes of operation for setting key values for a user: 
1) no service authentication - an authorization token for a service is not 
	required, and any service with the user token can write to any other
	service's unauthed values for that user.
2) service authentication required - the service must pass a Globus Online
	token that identifies the service in the argument list. Values can only be
	set by services with possession of a valid token. The service name 
	will be set to the username of the token.
The sets of key/value pairs for the two types of method calls are entirely
separate - for example, the workspace service could have a key called 'default'
that is writable by all other services (no auth) and the same key that was 
set with auth to which only the workspace service can write (or any other
service that has access to a workspace service account token, so keep your
service credentials safe).

Setting objects are limited to 640Kb.

All job writes require service authentication. No reads, either for key/value
pairs or jobs, require service authentication.

The service assumes other services are capable of simple math and does not
throw errors if a progress bar overflows.

Potential job process flows:

Asysnc:
UI calls service function which returns with job id
service call [spawns thread/subprocess to run job that] periodically updates
	the job status of the job id on the job status server
meanwhile, the UI periodically polls the job status server to get progress
	updates
service call finishes, completes job
UI pulls pointers to results from the job status server

Sync:
UI creates job, gets job id
UI starts thread that calls service, providing job id
service call runs, periodically updating the job status of the job id on the
	job status server
meanwhile, the UI periodically polls the job status server to get progress
	updates
service call finishes, completes job, returns results
UI thread joins

Authorization:
Currently two modes of authorization are supported:

DEFAULT:
DEFAULT authorization uses the UJS access control lists (ACLs) stored in the
UJS database. All methods work normally for this authorization strategy. To
use the default authorization strategy, simply do not specify an authorization
strategy when creating a job.

kbaseworkspace:
kbaseworkspace authorization (kbwsa) associates each job with an integer
Workspace Service (WSS) workspace ID (the authorization parameter). In order to
create a job with kbwsa, a user must have write access to the workspace in
question. That user can then read and update (but not necessarily list) the job
for the remainder of the job lifetime, regardless of the workspace permission.

Other users must have read permissions to the workspace in order to view the
job.

Share and unshare commands do not work with kbwsa.

*/

module UserAndJobState {

	/*
		Returns the version of the userandjobstate service.
	*/
	funcdef ver() returns(string ver);

	/* All other calls require authentication. */
	authentication required;
	
	/* A boolean. 0 = false, other = true. */
	typedef int boolean;
	
	/* Login name of a KBase user account. */
	typedef string username;
	
	/* A service name. Alphanumerics and the underscore are allowed. */
	typedef string service_name;
	
	/* A globus ID token that validates that the service really is said
	service. */
	typedef string service_token;
	
	/* Specifies whether results returned should be from key/value pairs
		set with service authentication (true) or without (false).
	*/
	typedef boolean authed;
	
	/* Set the state of a key for a service without service authentication. */
	funcdef set_state(service_name service, string key,
		UnspecifiedObject value) returns();
		
	/* Set the state of a key for a service with service authentication. */
	funcdef set_state_auth(service_token token, string key,
		UnspecifiedObject value) returns();
		
	/* Get the state of a key for a service. */
	funcdef get_state(service_name service, string key, authed auth)
		returns(UnspecifiedObject value);
		
	/* Determine if a key exists for a service. */
	funcdef has_state(service_name service, string key, authed auth)
		returns(boolean has_key);
		
	/* Get the state of a key for a service, and do not throw an error if the
		key doesn't exist. If the key doesn't exist, has_key will be false
		and the key value will be null. */
	funcdef get_has_state(service_name service, string key, authed auth)
		returns(boolean has_key, UnspecifiedObject value);
	
	/* Remove a key value pair without service authentication. */
	funcdef remove_state(service_name service, string key) returns ();
	
	/* Remove a key value pair with service authentication. */
	funcdef remove_state_auth(service_token token, string key) returns ();
		
	/* List all keys. */
	funcdef list_state(service_name service, authed auth) returns(
		list<string> keys);
		
	/* List all state services. */
	funcdef list_state_services(authed auth) returns(list<service_name> services);

	/* 
		A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference
		in time to UTC in the format +/-HHMM, eg:
			2012-12-17T23:24:06-0500 (EST time)
			2013-04-03T08:56:32+0000 (UTC time)
	*/
	typedef string timestamp;
		
	/* A job id. */
	typedef string job_id;
	
	/* A string that describes the stage of processing of the job.
		One of 'created', 'started', 'completed', or 'error'.
	*/
	typedef string job_stage;
	
	/* A job status string supplied by the reporting service. No more than
		200 characters. 
	*/
	typedef string job_status;
	
	/* A job description string supplied by the reporting service. No more than
		1000 characters. 
	*/
	typedef string job_description;
	
	/* The amount of progress the job has made since the last update. This will
		be summed to the total progress so far. */
	typedef int progress;
	
	/* Detailed information about a job error, such as a stacktrace, that will
		not fit in the job_status. No more than 100K characters.
	*/
	typedef string detailed_err;
	
	/* The total progress of a job. */
	typedef int total_progress;
	
	/* The maximum possible progress of a job. */
	typedef int max_progress;
	
	/* The type of progress that is being tracked. One of:
		'none' - no numerical progress tracking
		'task' - Task based tracking, e.g. 3/24
		'percent' - percentage based tracking, e.g. 5/100%
	*/ 
	typedef string progress_type;
	
	/* Initialization information for progress tracking. Currently 3 choices:
		
		progress_type ptype - one of 'none', 'percent', or 'task'
		max_progress max- required only for task based tracking. The 
			total number of tasks until the job is complete.
	*/
	typedef structure {
		progress_type ptype;
		max_progress max;
	} InitProgress;
	
	/* A place where the results of a job may be found.
		All fields except description are required.
		
		string server_type - the type of server storing the results. Typically
			either "Shock" or "Workspace". No more than 100 characters.
		string url - the url of the server. No more than 1000 characters.
		string id - the id of the result in the server. Typically either a
			workspace id or a shock node. No more than 1000 characters.
		string description - a free text description of the result.
			 No more than 1000 characters.
	*/
	typedef structure {
		string server_type;
		string url;
		string id;
		string description;
	} Result;
	
	/* A pointer to job results. All arguments are optional. Applications
		should use the default shock and workspace urls if omitted.
		list<string> shocknodes - the shocknode(s) where the results can be
			found. No more than 1000 characters.
		string shockurl - the url of the shock service where the data was
			saved.  No more than 1000 characters.
		list<string> workspaceids - the workspace ids where the results can be
			found. No more than 1000 characters.
		string workspaceurl - the url of the workspace service where the data
			was saved.  No more than 1000 characters.
		list<Result> - a set of job results. This format allows for specifying
			results at multiple server locations and providing a free text
			description of the result.
	*/
	typedef structure {
		list<string> shocknodes;
		string shockurl;
		list<string> workspaceids;
		string workspaceurl;
		list<Result> results;
	} Results;
	
	/*
		An authorization strategy to use for jobs. Other than the
		DEFAULT strategy (ACLs local to the UJS and managed by the UJS
		sharing functions), currently the only other strategy is the
		'kbaseworkspace' strategy, which consults the workspace service for
		authorization information.
	*/
	typedef string auth_strategy;
	
	/*
		An authorization parameter. The contents of this parameter differ by
		auth_strategy, but for the workspace strategy it is the workspace id
		(an integer) as a string.
	*/
	typedef string auth_param;
	
	/*
		User provided metadata about a job.
		Arbitrary key-value pairs provided by the user.
	*/
	typedef mapping<string, string> usermeta;
	
	/*
		Parameters for the create_job2 method.
		
		Optional parameters:
		auth_strategy authstrat - the authorization strategy to use for the
			job. Omit to use the standard UJS authorization. If an
			authorization strategy is supplied, in most cases an authparam must
			be supplied as well.
		auth_param - a parameter for the authorization strategy.
		usermeta meta - metadata for the job.
	*/
	typedef structure {
		auth_strategy authstrat;
		auth_param authparam;
		usermeta meta;
	} CreateJobParams;
	
	/* Create a new job status report. */
	funcdef create_job2(CreateJobParams params) returns (job_id job);
	
	/* Create a new job status report.
		@deprecated create_job2
	 */
	funcdef create_job() returns(job_id job);
	
	/* Start a job and specify the job parameters. */
	funcdef start_job(job_id job, service_token token, job_status status, 
		job_description desc, InitProgress progress, timestamp est_complete)
		returns();
	
	/* Create and start a job. */
	funcdef create_and_start_job(service_token token, job_status status, 
		job_description desc, InitProgress progress, timestamp est_complete)
		returns(job_id job);
	
	/* Update the status and progress for a job. */
	funcdef update_job_progress(job_id job, service_token token,
		job_status status, progress prog, timestamp est_complete) returns();
		
	/* Update the status for a job. */
	funcdef update_job(job_id job, service_token token, job_status status,
		timestamp est_complete) returns();
	
	/* Get the description of a job. */
	funcdef get_job_description(job_id job) returns(service_name service,
		progress_type ptype, max_progress max, job_description desc,
		timestamp started);
	
	/* Get the status of a job. */
	funcdef get_job_status(job_id job) returns(timestamp last_update, 
		job_stage stage, job_status status, total_progress progress,
		timestamp est_complete, boolean complete, boolean error);
	
	/* Complete the job. After the job is completed, total_progress always
		equals max_progress. If detailed_err is anything other than null,
		the job is considered to have errored out.
	*/
	funcdef complete_job(job_id job, service_token token, job_status status,
		detailed_err error, Results res) returns();
		
	/* Get the job results. */
	funcdef get_results(job_id job) returns(Results res);
	
	/* Get the detailed error message, if any */
	funcdef get_detailed_error(job_id job) returns(detailed_err error);
	
	/* Job timing information. */
	typedef tuple<timestamp started, timestamp last_update,
		timestamp est_complete> time_info;
		
	/* Job authorization strategy information. */
	typedef tuple<auth_strategy strat, auth_param param> auth_info;
	
	/* Job progress information. */
	typedef tuple<total_progress prog, max_progress max, progress_type ptype>
		progress_info;
		
	/* Information about a job. */
	typedef tuple<job_id job, service_name service, job_stage stage,
		job_status status, time_info times, progress_info progress,
		boolean complete, boolean error, auth_info auth, usermeta meta,
		job_description desc, Results res> job_info2;
	
	/* Information about a job.
		@deprecated job_info2
	 */
	typedef tuple<job_id job, service_name service, job_stage stage,
		timestamp started, job_status status, timestamp last_update,
		total_progress prog, max_progress max, progress_type ptype,
		timestamp est_complete, boolean complete, boolean error,
		job_description desc, Results res> job_info;
	
	/* Get information about a job. */
	funcdef get_job_info2(job_id job) returns(job_info2 info);
	
	/* Get information about a job.
		@deprecated get_job_info2
	 */
	funcdef get_job_info(job_id job) returns(job_info info);

	/* A string-based filter for listing jobs.
	
		If the string contains:
			'R' - running jobs are returned.
			'C' - completed jobs are returned.
			'E' - jobs that errored out are returned.
			'S' - shared jobs are returned.
		The string can contain any combination of these codes in any order.
		If the string contains none of the codes or is null, all self-owned 
		jobs are returned. If only the S filter is present, all jobs are
		returned. The S filter is ignored for jobs not using the default
		authorization strategy.
	*/
	typedef string job_filter;
	
	/*
		Input parameters for the list_jobs2 method.
		
		Optional parameters:
		list<service_name> services - the services from which to list jobs.
			Omit to list jobs from all services.
		job_filter filter - the filter to apply to the set of jobs.
		auth_strategy authstrat - return jobs with the specified
			authorization strategy. If this parameter is omitted, jobs
			with the default strategy will be returned.
		list<auth_params> authparams - only return jobs with one of the
			specified authorization parameters. An authorization strategy must
			be provided if authparams is specified. In most cases, at least one
			authorization parameter must be supplied and there is an upper
			limit to the number of paramters allowed. In the case of the
			kbaseworkspace strategy, these limits are 1 and 10, respectively.
	*/
	typedef structure {
		list<service_name> services;
		job_filter filter;
		auth_strategy authstrat;
		list<auth_param> authparams;
	} ListJobsParams;
	
	/* List jobs. */
	funcdef list_jobs2(ListJobsParams params) returns(list<job_info2> jobs);
	
	/* List jobs. Leave 'services' empty or null to list jobs from all
		services.
		
		@deprecated list_jobs2
	*/
	funcdef list_jobs(list<service_name> services, job_filter filter)
		returns(list<job_info> jobs);
	
	/* List all job services. */
	funcdef list_job_services() returns(list<service_name> services);
	
	/* Share a job. Sharing a job to the same user twice or with the job owner
		has no effect. Attempting to share a job not using the default auth
		strategy will fail.
	*/
	funcdef share_job(job_id job, list<username> users) returns();
	
	/* Stop sharing a job. Removing sharing from a user that the job is not
		shared with or the job owner has no effect. Attemping to unshare a job
		not using the default auth strategy will fail.
	*/
	funcdef unshare_job(job_id job, list<username> users) returns();
	
	/* Get the owner of a job. */
	funcdef get_job_owner(job_id job) returns(username owner);
	
	/* Get the list of users with which a job is shared. Only the job owner
		may access this method. Returns an empty list for jobs not using the
		default auth strategy.
	*/
	funcdef get_job_shared(job_id job) returns(list<username> users);
	
	/* Delete a job. Will fail if the job is not complete.
	*/
	funcdef delete_job(job_id job) returns();
	
	/* Force delete a job - will succeed unless the job has not been started.
		In that case, the service must start the job and then delete it, since
		a job is not "owned" by any service until it is started.
	*/
	funcdef force_delete_job(service_token token, job_id job) returns();
};