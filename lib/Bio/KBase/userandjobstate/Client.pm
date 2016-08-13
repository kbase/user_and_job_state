package Bio::KBase::userandjobstate::Client;

use JSON::RPC::Client;
use POSIX;
use strict;
use Data::Dumper;
use URI;
use Bio::KBase::Exceptions;
my $get_time = sub { time, 0 };
eval {
    require Time::HiRes;
    $get_time = sub { Time::HiRes::gettimeofday() };
};

use Bio::KBase::AuthToken;

# Client version should match Impl version
# This is a Semantic Version number,
# http://semver.org
our $VERSION = "0.1.0";

=head1 NAME

Bio::KBase::userandjobstate::Client

=head1 DESCRIPTION


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


=cut

sub new
{
    my($class, $url, @args) = @_;
    
    if (!defined($url))
    {
	$url = 'https://kbase.us/services/userandjobstate/';
    }

    my $self = {
	client => Bio::KBase::userandjobstate::Client::RpcClient->new,
	url => $url,
	headers => [],
    };

    chomp($self->{hostname} = `hostname`);
    $self->{hostname} ||= 'unknown-host';

    #
    # Set up for propagating KBRPC_TAG and KBRPC_METADATA environment variables through
    # to invoked services. If these values are not set, we create a new tag
    # and a metadata field with basic information about the invoking script.
    #
    if ($ENV{KBRPC_TAG})
    {
	$self->{kbrpc_tag} = $ENV{KBRPC_TAG};
    }
    else
    {
	my ($t, $us) = &$get_time();
	$us = sprintf("%06d", $us);
	my $ts = strftime("%Y-%m-%dT%H:%M:%S.${us}Z", gmtime $t);
	$self->{kbrpc_tag} = "C:$0:$self->{hostname}:$$:$ts";
    }
    push(@{$self->{headers}}, 'Kbrpc-Tag', $self->{kbrpc_tag});

    if ($ENV{KBRPC_METADATA})
    {
	$self->{kbrpc_metadata} = $ENV{KBRPC_METADATA};
	push(@{$self->{headers}}, 'Kbrpc-Metadata', $self->{kbrpc_metadata});
    }

    if ($ENV{KBRPC_ERROR_DEST})
    {
	$self->{kbrpc_error_dest} = $ENV{KBRPC_ERROR_DEST};
	push(@{$self->{headers}}, 'Kbrpc-Errordest', $self->{kbrpc_error_dest});
    }

    #
    # This module requires authentication.
    #
    # We create an auth token, passing through the arguments that we were (hopefully) given.

    {
	my $token = Bio::KBase::AuthToken->new(@args);
	
	if (!$token->error_message)
	{
	    $self->{token} = $token->token;
	    $self->{client}->{token} = $token->token;
	}
    }

    my $ua = $self->{client}->ua;	 
    my $timeout = $ENV{CDMI_TIMEOUT} || (30 * 60);	 
    $ua->timeout($timeout);
    bless $self, $class;
    #    $self->_validate_version();
    return $self;
}




=head2 ver

  $ver = $obj->ver()

=over 4

=item Parameter and return types

=begin html

<pre>
$ver is a string

</pre>

=end html

=begin text

$ver is a string


=end text

=item Description

Returns the version of the userandjobstate service.

=back

=cut

 sub ver
{
    my($self, @args) = @_;

# Authentication: none

    if ((my $n = @args) != 0)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function ver (received $n, expecting 0)");
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.ver",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'ver',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method ver",
					    status_line => $self->{client}->status_line,
					    method_name => 'ver',
				       );
    }
}
 


=head2 set_state

  $obj->set_state($service, $key, $value)

=over 4

=item Parameter and return types

=begin html

<pre>
$service is an UserAndJobState.service_name
$key is a string
$value is an UnspecifiedObject, which can hold any non-null object
service_name is a string

</pre>

=end html

=begin text

$service is an UserAndJobState.service_name
$key is a string
$value is an UnspecifiedObject, which can hold any non-null object
service_name is a string


=end text

=item Description

Set the state of a key for a service without service authentication.

=back

=cut

 sub set_state
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 3)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function set_state (received $n, expecting 3)");
    }
    {
	my($service, $key, $value) = @args;

	my @_bad_arguments;
        (!ref($service)) or push(@_bad_arguments, "Invalid type for argument 1 \"service\" (value was \"$service\")");
        (!ref($key)) or push(@_bad_arguments, "Invalid type for argument 2 \"key\" (value was \"$key\")");
        (defined $value) or push(@_bad_arguments, "Invalid type for argument 3 \"value\" (value was \"$value\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to set_state:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'set_state');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.set_state",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'set_state',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method set_state",
					    status_line => $self->{client}->status_line,
					    method_name => 'set_state',
				       );
    }
}
 


=head2 set_state_auth

  $obj->set_state_auth($token, $key, $value)

=over 4

=item Parameter and return types

=begin html

<pre>
$token is an UserAndJobState.service_token
$key is a string
$value is an UnspecifiedObject, which can hold any non-null object
service_token is a string

</pre>

=end html

=begin text

$token is an UserAndJobState.service_token
$key is a string
$value is an UnspecifiedObject, which can hold any non-null object
service_token is a string


=end text

=item Description

Set the state of a key for a service with service authentication.

=back

=cut

 sub set_state_auth
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 3)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function set_state_auth (received $n, expecting 3)");
    }
    {
	my($token, $key, $value) = @args;

	my @_bad_arguments;
        (!ref($token)) or push(@_bad_arguments, "Invalid type for argument 1 \"token\" (value was \"$token\")");
        (!ref($key)) or push(@_bad_arguments, "Invalid type for argument 2 \"key\" (value was \"$key\")");
        (defined $value) or push(@_bad_arguments, "Invalid type for argument 3 \"value\" (value was \"$value\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to set_state_auth:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'set_state_auth');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.set_state_auth",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'set_state_auth',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method set_state_auth",
					    status_line => $self->{client}->status_line,
					    method_name => 'set_state_auth',
				       );
    }
}
 


=head2 get_state

  $value = $obj->get_state($service, $key, $auth)

=over 4

=item Parameter and return types

=begin html

<pre>
$service is an UserAndJobState.service_name
$key is a string
$auth is an UserAndJobState.authed
$value is an UnspecifiedObject, which can hold any non-null object
service_name is a string
authed is an UserAndJobState.boolean
boolean is an int

</pre>

=end html

=begin text

$service is an UserAndJobState.service_name
$key is a string
$auth is an UserAndJobState.authed
$value is an UnspecifiedObject, which can hold any non-null object
service_name is a string
authed is an UserAndJobState.boolean
boolean is an int


=end text

=item Description

Get the state of a key for a service.

=back

=cut

 sub get_state
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 3)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_state (received $n, expecting 3)");
    }
    {
	my($service, $key, $auth) = @args;

	my @_bad_arguments;
        (!ref($service)) or push(@_bad_arguments, "Invalid type for argument 1 \"service\" (value was \"$service\")");
        (!ref($key)) or push(@_bad_arguments, "Invalid type for argument 2 \"key\" (value was \"$key\")");
        (!ref($auth)) or push(@_bad_arguments, "Invalid type for argument 3 \"auth\" (value was \"$auth\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_state:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_state');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.get_state",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_state',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_state",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_state',
				       );
    }
}
 


=head2 has_state

  $has_key = $obj->has_state($service, $key, $auth)

=over 4

=item Parameter and return types

=begin html

<pre>
$service is an UserAndJobState.service_name
$key is a string
$auth is an UserAndJobState.authed
$has_key is an UserAndJobState.boolean
service_name is a string
authed is an UserAndJobState.boolean
boolean is an int

</pre>

=end html

=begin text

$service is an UserAndJobState.service_name
$key is a string
$auth is an UserAndJobState.authed
$has_key is an UserAndJobState.boolean
service_name is a string
authed is an UserAndJobState.boolean
boolean is an int


=end text

=item Description

Determine if a key exists for a service.

=back

=cut

 sub has_state
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 3)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function has_state (received $n, expecting 3)");
    }
    {
	my($service, $key, $auth) = @args;

	my @_bad_arguments;
        (!ref($service)) or push(@_bad_arguments, "Invalid type for argument 1 \"service\" (value was \"$service\")");
        (!ref($key)) or push(@_bad_arguments, "Invalid type for argument 2 \"key\" (value was \"$key\")");
        (!ref($auth)) or push(@_bad_arguments, "Invalid type for argument 3 \"auth\" (value was \"$auth\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to has_state:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'has_state');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.has_state",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'has_state',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method has_state",
					    status_line => $self->{client}->status_line,
					    method_name => 'has_state',
				       );
    }
}
 


=head2 get_has_state

  $has_key, $value = $obj->get_has_state($service, $key, $auth)

=over 4

=item Parameter and return types

=begin html

<pre>
$service is an UserAndJobState.service_name
$key is a string
$auth is an UserAndJobState.authed
$has_key is an UserAndJobState.boolean
$value is an UnspecifiedObject, which can hold any non-null object
service_name is a string
authed is an UserAndJobState.boolean
boolean is an int

</pre>

=end html

=begin text

$service is an UserAndJobState.service_name
$key is a string
$auth is an UserAndJobState.authed
$has_key is an UserAndJobState.boolean
$value is an UnspecifiedObject, which can hold any non-null object
service_name is a string
authed is an UserAndJobState.boolean
boolean is an int


=end text

=item Description

Get the state of a key for a service, and do not throw an error if the
key doesn't exist. If the key doesn't exist, has_key will be false
and the key value will be null.

=back

=cut

 sub get_has_state
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 3)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_has_state (received $n, expecting 3)");
    }
    {
	my($service, $key, $auth) = @args;

	my @_bad_arguments;
        (!ref($service)) or push(@_bad_arguments, "Invalid type for argument 1 \"service\" (value was \"$service\")");
        (!ref($key)) or push(@_bad_arguments, "Invalid type for argument 2 \"key\" (value was \"$key\")");
        (!ref($auth)) or push(@_bad_arguments, "Invalid type for argument 3 \"auth\" (value was \"$auth\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_has_state:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_has_state');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.get_has_state",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_has_state',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_has_state",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_has_state',
				       );
    }
}
 


=head2 remove_state

  $obj->remove_state($service, $key)

=over 4

=item Parameter and return types

=begin html

<pre>
$service is an UserAndJobState.service_name
$key is a string
service_name is a string

</pre>

=end html

=begin text

$service is an UserAndJobState.service_name
$key is a string
service_name is a string


=end text

=item Description

Remove a key value pair without service authentication.

=back

=cut

 sub remove_state
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 2)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function remove_state (received $n, expecting 2)");
    }
    {
	my($service, $key) = @args;

	my @_bad_arguments;
        (!ref($service)) or push(@_bad_arguments, "Invalid type for argument 1 \"service\" (value was \"$service\")");
        (!ref($key)) or push(@_bad_arguments, "Invalid type for argument 2 \"key\" (value was \"$key\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to remove_state:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'remove_state');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.remove_state",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'remove_state',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method remove_state",
					    status_line => $self->{client}->status_line,
					    method_name => 'remove_state',
				       );
    }
}
 


=head2 remove_state_auth

  $obj->remove_state_auth($token, $key)

=over 4

=item Parameter and return types

=begin html

<pre>
$token is an UserAndJobState.service_token
$key is a string
service_token is a string

</pre>

=end html

=begin text

$token is an UserAndJobState.service_token
$key is a string
service_token is a string


=end text

=item Description

Remove a key value pair with service authentication.

=back

=cut

 sub remove_state_auth
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 2)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function remove_state_auth (received $n, expecting 2)");
    }
    {
	my($token, $key) = @args;

	my @_bad_arguments;
        (!ref($token)) or push(@_bad_arguments, "Invalid type for argument 1 \"token\" (value was \"$token\")");
        (!ref($key)) or push(@_bad_arguments, "Invalid type for argument 2 \"key\" (value was \"$key\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to remove_state_auth:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'remove_state_auth');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.remove_state_auth",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'remove_state_auth',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method remove_state_auth",
					    status_line => $self->{client}->status_line,
					    method_name => 'remove_state_auth',
				       );
    }
}
 


=head2 list_state

  $keys = $obj->list_state($service, $auth)

=over 4

=item Parameter and return types

=begin html

<pre>
$service is an UserAndJobState.service_name
$auth is an UserAndJobState.authed
$keys is a reference to a list where each element is a string
service_name is a string
authed is an UserAndJobState.boolean
boolean is an int

</pre>

=end html

=begin text

$service is an UserAndJobState.service_name
$auth is an UserAndJobState.authed
$keys is a reference to a list where each element is a string
service_name is a string
authed is an UserAndJobState.boolean
boolean is an int


=end text

=item Description

List all keys.

=back

=cut

 sub list_state
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 2)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function list_state (received $n, expecting 2)");
    }
    {
	my($service, $auth) = @args;

	my @_bad_arguments;
        (!ref($service)) or push(@_bad_arguments, "Invalid type for argument 1 \"service\" (value was \"$service\")");
        (!ref($auth)) or push(@_bad_arguments, "Invalid type for argument 2 \"auth\" (value was \"$auth\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to list_state:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'list_state');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.list_state",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'list_state',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method list_state",
					    status_line => $self->{client}->status_line,
					    method_name => 'list_state',
				       );
    }
}
 


=head2 list_state_services

  $services = $obj->list_state_services($auth)

=over 4

=item Parameter and return types

=begin html

<pre>
$auth is an UserAndJobState.authed
$services is a reference to a list where each element is an UserAndJobState.service_name
authed is an UserAndJobState.boolean
boolean is an int
service_name is a string

</pre>

=end html

=begin text

$auth is an UserAndJobState.authed
$services is a reference to a list where each element is an UserAndJobState.service_name
authed is an UserAndJobState.boolean
boolean is an int
service_name is a string


=end text

=item Description

List all state services.

=back

=cut

 sub list_state_services
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function list_state_services (received $n, expecting 1)");
    }
    {
	my($auth) = @args;

	my @_bad_arguments;
        (!ref($auth)) or push(@_bad_arguments, "Invalid type for argument 1 \"auth\" (value was \"$auth\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to list_state_services:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'list_state_services');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.list_state_services",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'list_state_services',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method list_state_services",
					    status_line => $self->{client}->status_line,
					    method_name => 'list_state_services',
				       );
    }
}
 


=head2 create_job2

  $job = $obj->create_job2($params)

=over 4

=item Parameter and return types

=begin html

<pre>
$params is an UserAndJobState.CreateJobParams
$job is an UserAndJobState.job_id
CreateJobParams is a reference to a hash where the following keys are defined:
	authstrat has a value which is an UserAndJobState.auth_strategy
	authparam has a value which is an UserAndJobState.auth_param
	meta has a value which is an UserAndJobState.usermeta
auth_strategy is a string
auth_param is a string
usermeta is a reference to a hash where the key is a string and the value is a string
job_id is a string

</pre>

=end html

=begin text

$params is an UserAndJobState.CreateJobParams
$job is an UserAndJobState.job_id
CreateJobParams is a reference to a hash where the following keys are defined:
	authstrat has a value which is an UserAndJobState.auth_strategy
	authparam has a value which is an UserAndJobState.auth_param
	meta has a value which is an UserAndJobState.usermeta
auth_strategy is a string
auth_param is a string
usermeta is a reference to a hash where the key is a string and the value is a string
job_id is a string


=end text

=item Description

Create a new job status report.

=back

=cut

 sub create_job2
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function create_job2 (received $n, expecting 1)");
    }
    {
	my($params) = @args;

	my @_bad_arguments;
        (ref($params) eq 'HASH') or push(@_bad_arguments, "Invalid type for argument 1 \"params\" (value was \"$params\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to create_job2:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'create_job2');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.create_job2",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'create_job2',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method create_job2",
					    status_line => $self->{client}->status_line,
					    method_name => 'create_job2',
				       );
    }
}
 


=head2 create_job

  $job = $obj->create_job()

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
job_id is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
job_id is a string


=end text

=item Description

Create a new job status report.
@deprecated create_job2

=back

=cut

 sub create_job
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 0)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function create_job (received $n, expecting 0)");
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.create_job",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'create_job',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method create_job",
					    status_line => $self->{client}->status_line,
					    method_name => 'create_job',
				       );
    }
}
 


=head2 start_job

  $obj->start_job($job, $token, $status, $desc, $progress, $est_complete)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$token is an UserAndJobState.service_token
$status is an UserAndJobState.job_status
$desc is an UserAndJobState.job_description
$progress is an UserAndJobState.InitProgress
$est_complete is an UserAndJobState.timestamp
job_id is a string
service_token is a string
job_status is a string
job_description is a string
InitProgress is a reference to a hash where the following keys are defined:
	ptype has a value which is an UserAndJobState.progress_type
	max has a value which is an UserAndJobState.max_progress
progress_type is a string
max_progress is an int
timestamp is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$token is an UserAndJobState.service_token
$status is an UserAndJobState.job_status
$desc is an UserAndJobState.job_description
$progress is an UserAndJobState.InitProgress
$est_complete is an UserAndJobState.timestamp
job_id is a string
service_token is a string
job_status is a string
job_description is a string
InitProgress is a reference to a hash where the following keys are defined:
	ptype has a value which is an UserAndJobState.progress_type
	max has a value which is an UserAndJobState.max_progress
progress_type is a string
max_progress is an int
timestamp is a string


=end text

=item Description

Start a job and specify the job parameters.

=back

=cut

 sub start_job
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 6)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function start_job (received $n, expecting 6)");
    }
    {
	my($job, $token, $status, $desc, $progress, $est_complete) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        (!ref($token)) or push(@_bad_arguments, "Invalid type for argument 2 \"token\" (value was \"$token\")");
        (!ref($status)) or push(@_bad_arguments, "Invalid type for argument 3 \"status\" (value was \"$status\")");
        (!ref($desc)) or push(@_bad_arguments, "Invalid type for argument 4 \"desc\" (value was \"$desc\")");
        (ref($progress) eq 'HASH') or push(@_bad_arguments, "Invalid type for argument 5 \"progress\" (value was \"$progress\")");
        (!ref($est_complete)) or push(@_bad_arguments, "Invalid type for argument 6 \"est_complete\" (value was \"$est_complete\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to start_job:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'start_job');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.start_job",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'start_job',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method start_job",
					    status_line => $self->{client}->status_line,
					    method_name => 'start_job',
				       );
    }
}
 


=head2 create_and_start_job

  $job = $obj->create_and_start_job($token, $status, $desc, $progress, $est_complete)

=over 4

=item Parameter and return types

=begin html

<pre>
$token is an UserAndJobState.service_token
$status is an UserAndJobState.job_status
$desc is an UserAndJobState.job_description
$progress is an UserAndJobState.InitProgress
$est_complete is an UserAndJobState.timestamp
$job is an UserAndJobState.job_id
service_token is a string
job_status is a string
job_description is a string
InitProgress is a reference to a hash where the following keys are defined:
	ptype has a value which is an UserAndJobState.progress_type
	max has a value which is an UserAndJobState.max_progress
progress_type is a string
max_progress is an int
timestamp is a string
job_id is a string

</pre>

=end html

=begin text

$token is an UserAndJobState.service_token
$status is an UserAndJobState.job_status
$desc is an UserAndJobState.job_description
$progress is an UserAndJobState.InitProgress
$est_complete is an UserAndJobState.timestamp
$job is an UserAndJobState.job_id
service_token is a string
job_status is a string
job_description is a string
InitProgress is a reference to a hash where the following keys are defined:
	ptype has a value which is an UserAndJobState.progress_type
	max has a value which is an UserAndJobState.max_progress
progress_type is a string
max_progress is an int
timestamp is a string
job_id is a string


=end text

=item Description

Create and start a job.

=back

=cut

 sub create_and_start_job
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 5)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function create_and_start_job (received $n, expecting 5)");
    }
    {
	my($token, $status, $desc, $progress, $est_complete) = @args;

	my @_bad_arguments;
        (!ref($token)) or push(@_bad_arguments, "Invalid type for argument 1 \"token\" (value was \"$token\")");
        (!ref($status)) or push(@_bad_arguments, "Invalid type for argument 2 \"status\" (value was \"$status\")");
        (!ref($desc)) or push(@_bad_arguments, "Invalid type for argument 3 \"desc\" (value was \"$desc\")");
        (ref($progress) eq 'HASH') or push(@_bad_arguments, "Invalid type for argument 4 \"progress\" (value was \"$progress\")");
        (!ref($est_complete)) or push(@_bad_arguments, "Invalid type for argument 5 \"est_complete\" (value was \"$est_complete\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to create_and_start_job:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'create_and_start_job');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.create_and_start_job",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'create_and_start_job',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method create_and_start_job",
					    status_line => $self->{client}->status_line,
					    method_name => 'create_and_start_job',
				       );
    }
}
 


=head2 update_job_progress

  $obj->update_job_progress($job, $token, $status, $prog, $est_complete)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$token is an UserAndJobState.service_token
$status is an UserAndJobState.job_status
$prog is an UserAndJobState.progress
$est_complete is an UserAndJobState.timestamp
job_id is a string
service_token is a string
job_status is a string
progress is an int
timestamp is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$token is an UserAndJobState.service_token
$status is an UserAndJobState.job_status
$prog is an UserAndJobState.progress
$est_complete is an UserAndJobState.timestamp
job_id is a string
service_token is a string
job_status is a string
progress is an int
timestamp is a string


=end text

=item Description

Update the status and progress for a job.

=back

=cut

 sub update_job_progress
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 5)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function update_job_progress (received $n, expecting 5)");
    }
    {
	my($job, $token, $status, $prog, $est_complete) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        (!ref($token)) or push(@_bad_arguments, "Invalid type for argument 2 \"token\" (value was \"$token\")");
        (!ref($status)) or push(@_bad_arguments, "Invalid type for argument 3 \"status\" (value was \"$status\")");
        (!ref($prog)) or push(@_bad_arguments, "Invalid type for argument 4 \"prog\" (value was \"$prog\")");
        (!ref($est_complete)) or push(@_bad_arguments, "Invalid type for argument 5 \"est_complete\" (value was \"$est_complete\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to update_job_progress:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'update_job_progress');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.update_job_progress",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'update_job_progress',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method update_job_progress",
					    status_line => $self->{client}->status_line,
					    method_name => 'update_job_progress',
				       );
    }
}
 


=head2 update_job

  $obj->update_job($job, $token, $status, $est_complete)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$token is an UserAndJobState.service_token
$status is an UserAndJobState.job_status
$est_complete is an UserAndJobState.timestamp
job_id is a string
service_token is a string
job_status is a string
timestamp is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$token is an UserAndJobState.service_token
$status is an UserAndJobState.job_status
$est_complete is an UserAndJobState.timestamp
job_id is a string
service_token is a string
job_status is a string
timestamp is a string


=end text

=item Description

Update the status for a job.

=back

=cut

 sub update_job
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 4)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function update_job (received $n, expecting 4)");
    }
    {
	my($job, $token, $status, $est_complete) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        (!ref($token)) or push(@_bad_arguments, "Invalid type for argument 2 \"token\" (value was \"$token\")");
        (!ref($status)) or push(@_bad_arguments, "Invalid type for argument 3 \"status\" (value was \"$status\")");
        (!ref($est_complete)) or push(@_bad_arguments, "Invalid type for argument 4 \"est_complete\" (value was \"$est_complete\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to update_job:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'update_job');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.update_job",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'update_job',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method update_job",
					    status_line => $self->{client}->status_line,
					    method_name => 'update_job',
				       );
    }
}
 


=head2 get_job_description

  $service, $ptype, $max, $desc, $started = $obj->get_job_description($job)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$service is an UserAndJobState.service_name
$ptype is an UserAndJobState.progress_type
$max is an UserAndJobState.max_progress
$desc is an UserAndJobState.job_description
$started is an UserAndJobState.timestamp
job_id is a string
service_name is a string
progress_type is a string
max_progress is an int
job_description is a string
timestamp is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$service is an UserAndJobState.service_name
$ptype is an UserAndJobState.progress_type
$max is an UserAndJobState.max_progress
$desc is an UserAndJobState.job_description
$started is an UserAndJobState.timestamp
job_id is a string
service_name is a string
progress_type is a string
max_progress is an int
job_description is a string
timestamp is a string


=end text

=item Description

Get the description of a job.

=back

=cut

 sub get_job_description
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_job_description (received $n, expecting 1)");
    }
    {
	my($job) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_job_description:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_job_description');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.get_job_description",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_job_description',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_job_description",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_job_description',
				       );
    }
}
 


=head2 get_job_status

  $last_update, $stage, $status, $progress, $est_complete, $complete, $error = $obj->get_job_status($job)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$last_update is an UserAndJobState.timestamp
$stage is an UserAndJobState.job_stage
$status is an UserAndJobState.job_status
$progress is an UserAndJobState.total_progress
$est_complete is an UserAndJobState.timestamp
$complete is an UserAndJobState.boolean
$error is an UserAndJobState.boolean
job_id is a string
timestamp is a string
job_stage is a string
job_status is a string
total_progress is an int
boolean is an int

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$last_update is an UserAndJobState.timestamp
$stage is an UserAndJobState.job_stage
$status is an UserAndJobState.job_status
$progress is an UserAndJobState.total_progress
$est_complete is an UserAndJobState.timestamp
$complete is an UserAndJobState.boolean
$error is an UserAndJobState.boolean
job_id is a string
timestamp is a string
job_stage is a string
job_status is a string
total_progress is an int
boolean is an int


=end text

=item Description

Get the status of a job.

=back

=cut

 sub get_job_status
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_job_status (received $n, expecting 1)");
    }
    {
	my($job) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_job_status:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_job_status');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.get_job_status",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_job_status',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_job_status",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_job_status',
				       );
    }
}
 


=head2 complete_job

  $obj->complete_job($job, $token, $status, $error, $res)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$token is an UserAndJobState.service_token
$status is an UserAndJobState.job_status
$error is an UserAndJobState.detailed_err
$res is an UserAndJobState.Results
job_id is a string
service_token is a string
job_status is a string
detailed_err is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$token is an UserAndJobState.service_token
$status is an UserAndJobState.job_status
$error is an UserAndJobState.detailed_err
$res is an UserAndJobState.Results
job_id is a string
service_token is a string
job_status is a string
detailed_err is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string


=end text

=item Description

Complete the job. After the job is completed, total_progress always
equals max_progress. If detailed_err is anything other than null,
the job is considered to have errored out.

=back

=cut

 sub complete_job
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 5)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function complete_job (received $n, expecting 5)");
    }
    {
	my($job, $token, $status, $error, $res) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        (!ref($token)) or push(@_bad_arguments, "Invalid type for argument 2 \"token\" (value was \"$token\")");
        (!ref($status)) or push(@_bad_arguments, "Invalid type for argument 3 \"status\" (value was \"$status\")");
        (!ref($error)) or push(@_bad_arguments, "Invalid type for argument 4 \"error\" (value was \"$error\")");
        (ref($res) eq 'HASH') or push(@_bad_arguments, "Invalid type for argument 5 \"res\" (value was \"$res\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to complete_job:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'complete_job');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.complete_job",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'complete_job',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method complete_job",
					    status_line => $self->{client}->status_line,
					    method_name => 'complete_job',
				       );
    }
}
 


=head2 cancel_job

  $obj->cancel_job($job, $status)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$status is an UserAndJobState.job_status
job_id is a string
job_status is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$status is an UserAndJobState.job_status
job_id is a string
job_status is a string


=end text

=item Description

Cancel a job.

=back

=cut

 sub cancel_job
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 2)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function cancel_job (received $n, expecting 2)");
    }
    {
	my($job, $status) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        (!ref($status)) or push(@_bad_arguments, "Invalid type for argument 2 \"status\" (value was \"$status\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to cancel_job:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'cancel_job');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.cancel_job",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'cancel_job',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method cancel_job",
					    status_line => $self->{client}->status_line,
					    method_name => 'cancel_job',
				       );
    }
}
 


=head2 get_results

  $res = $obj->get_results($job)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$res is an UserAndJobState.Results
job_id is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$res is an UserAndJobState.Results
job_id is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string


=end text

=item Description

Get the job results.

=back

=cut

 sub get_results
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_results (received $n, expecting 1)");
    }
    {
	my($job) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_results:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_results');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.get_results",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_results',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_results",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_results',
				       );
    }
}
 


=head2 get_detailed_error

  $error = $obj->get_detailed_error($job)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$error is an UserAndJobState.detailed_err
job_id is a string
detailed_err is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$error is an UserAndJobState.detailed_err
job_id is a string
detailed_err is a string


=end text

=item Description

Get the detailed error message, if any

=back

=cut

 sub get_detailed_error
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_detailed_error (received $n, expecting 1)");
    }
    {
	my($job) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_detailed_error:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_detailed_error');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.get_detailed_error",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_detailed_error',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_detailed_error",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_detailed_error',
				       );
    }
}
 


=head2 get_job_info2

  $info = $obj->get_job_info2($job)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$info is an UserAndJobState.job_info2
job_id is a string
job_info2 is a reference to a list containing 13 items:
	0: (job) an UserAndJobState.job_id
	1: (users) an UserAndJobState.user_info
	2: (service) an UserAndJobState.service_name
	3: (stage) an UserAndJobState.job_stage
	4: (status) an UserAndJobState.job_status
	5: (times) an UserAndJobState.time_info
	6: (progress) an UserAndJobState.progress_info
	7: (complete) an UserAndJobState.boolean
	8: (error) an UserAndJobState.boolean
	9: (auth) an UserAndJobState.auth_info
	10: (meta) an UserAndJobState.usermeta
	11: (desc) an UserAndJobState.job_description
	12: (res) an UserAndJobState.Results
user_info is a reference to a list containing 2 items:
	0: (owner) an UserAndJobState.username
	1: (canceledby) an UserAndJobState.username
username is a string
service_name is a string
job_stage is a string
job_status is a string
time_info is a reference to a list containing 3 items:
	0: (started) an UserAndJobState.timestamp
	1: (last_update) an UserAndJobState.timestamp
	2: (est_complete) an UserAndJobState.timestamp
timestamp is a string
progress_info is a reference to a list containing 3 items:
	0: (prog) an UserAndJobState.total_progress
	1: (max) an UserAndJobState.max_progress
	2: (ptype) an UserAndJobState.progress_type
total_progress is an int
max_progress is an int
progress_type is a string
boolean is an int
auth_info is a reference to a list containing 2 items:
	0: (strat) an UserAndJobState.auth_strategy
	1: (param) an UserAndJobState.auth_param
auth_strategy is a string
auth_param is a string
usermeta is a reference to a hash where the key is a string and the value is a string
job_description is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$info is an UserAndJobState.job_info2
job_id is a string
job_info2 is a reference to a list containing 13 items:
	0: (job) an UserAndJobState.job_id
	1: (users) an UserAndJobState.user_info
	2: (service) an UserAndJobState.service_name
	3: (stage) an UserAndJobState.job_stage
	4: (status) an UserAndJobState.job_status
	5: (times) an UserAndJobState.time_info
	6: (progress) an UserAndJobState.progress_info
	7: (complete) an UserAndJobState.boolean
	8: (error) an UserAndJobState.boolean
	9: (auth) an UserAndJobState.auth_info
	10: (meta) an UserAndJobState.usermeta
	11: (desc) an UserAndJobState.job_description
	12: (res) an UserAndJobState.Results
user_info is a reference to a list containing 2 items:
	0: (owner) an UserAndJobState.username
	1: (canceledby) an UserAndJobState.username
username is a string
service_name is a string
job_stage is a string
job_status is a string
time_info is a reference to a list containing 3 items:
	0: (started) an UserAndJobState.timestamp
	1: (last_update) an UserAndJobState.timestamp
	2: (est_complete) an UserAndJobState.timestamp
timestamp is a string
progress_info is a reference to a list containing 3 items:
	0: (prog) an UserAndJobState.total_progress
	1: (max) an UserAndJobState.max_progress
	2: (ptype) an UserAndJobState.progress_type
total_progress is an int
max_progress is an int
progress_type is a string
boolean is an int
auth_info is a reference to a list containing 2 items:
	0: (strat) an UserAndJobState.auth_strategy
	1: (param) an UserAndJobState.auth_param
auth_strategy is a string
auth_param is a string
usermeta is a reference to a hash where the key is a string and the value is a string
job_description is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string


=end text

=item Description

Get information about a job.

=back

=cut

 sub get_job_info2
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_job_info2 (received $n, expecting 1)");
    }
    {
	my($job) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_job_info2:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_job_info2');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.get_job_info2",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_job_info2',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_job_info2",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_job_info2',
				       );
    }
}
 


=head2 get_job_info

  $info = $obj->get_job_info($job)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$info is an UserAndJobState.job_info
job_id is a string
job_info is a reference to a list containing 14 items:
	0: (job) an UserAndJobState.job_id
	1: (service) an UserAndJobState.service_name
	2: (stage) an UserAndJobState.job_stage
	3: (started) an UserAndJobState.timestamp
	4: (status) an UserAndJobState.job_status
	5: (last_update) an UserAndJobState.timestamp
	6: (prog) an UserAndJobState.total_progress
	7: (max) an UserAndJobState.max_progress
	8: (ptype) an UserAndJobState.progress_type
	9: (est_complete) an UserAndJobState.timestamp
	10: (complete) an UserAndJobState.boolean
	11: (error) an UserAndJobState.boolean
	12: (desc) an UserAndJobState.job_description
	13: (res) an UserAndJobState.Results
service_name is a string
job_stage is a string
timestamp is a string
job_status is a string
total_progress is an int
max_progress is an int
progress_type is a string
boolean is an int
job_description is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$info is an UserAndJobState.job_info
job_id is a string
job_info is a reference to a list containing 14 items:
	0: (job) an UserAndJobState.job_id
	1: (service) an UserAndJobState.service_name
	2: (stage) an UserAndJobState.job_stage
	3: (started) an UserAndJobState.timestamp
	4: (status) an UserAndJobState.job_status
	5: (last_update) an UserAndJobState.timestamp
	6: (prog) an UserAndJobState.total_progress
	7: (max) an UserAndJobState.max_progress
	8: (ptype) an UserAndJobState.progress_type
	9: (est_complete) an UserAndJobState.timestamp
	10: (complete) an UserAndJobState.boolean
	11: (error) an UserAndJobState.boolean
	12: (desc) an UserAndJobState.job_description
	13: (res) an UserAndJobState.Results
service_name is a string
job_stage is a string
timestamp is a string
job_status is a string
total_progress is an int
max_progress is an int
progress_type is a string
boolean is an int
job_description is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string


=end text

=item Description

Get information about a job.
@deprecated get_job_info2

=back

=cut

 sub get_job_info
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_job_info (received $n, expecting 1)");
    }
    {
	my($job) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_job_info:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_job_info');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.get_job_info",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_job_info',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_job_info",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_job_info',
				       );
    }
}
 


=head2 list_jobs2

  $jobs = $obj->list_jobs2($params)

=over 4

=item Parameter and return types

=begin html

<pre>
$params is an UserAndJobState.ListJobsParams
$jobs is a reference to a list where each element is an UserAndJobState.job_info2
ListJobsParams is a reference to a hash where the following keys are defined:
	services has a value which is a reference to a list where each element is an UserAndJobState.service_name
	filter has a value which is an UserAndJobState.job_filter
	authstrat has a value which is an UserAndJobState.auth_strategy
	authparams has a value which is a reference to a list where each element is an UserAndJobState.auth_param
service_name is a string
job_filter is a string
auth_strategy is a string
auth_param is a string
job_info2 is a reference to a list containing 13 items:
	0: (job) an UserAndJobState.job_id
	1: (users) an UserAndJobState.user_info
	2: (service) an UserAndJobState.service_name
	3: (stage) an UserAndJobState.job_stage
	4: (status) an UserAndJobState.job_status
	5: (times) an UserAndJobState.time_info
	6: (progress) an UserAndJobState.progress_info
	7: (complete) an UserAndJobState.boolean
	8: (error) an UserAndJobState.boolean
	9: (auth) an UserAndJobState.auth_info
	10: (meta) an UserAndJobState.usermeta
	11: (desc) an UserAndJobState.job_description
	12: (res) an UserAndJobState.Results
job_id is a string
user_info is a reference to a list containing 2 items:
	0: (owner) an UserAndJobState.username
	1: (canceledby) an UserAndJobState.username
username is a string
job_stage is a string
job_status is a string
time_info is a reference to a list containing 3 items:
	0: (started) an UserAndJobState.timestamp
	1: (last_update) an UserAndJobState.timestamp
	2: (est_complete) an UserAndJobState.timestamp
timestamp is a string
progress_info is a reference to a list containing 3 items:
	0: (prog) an UserAndJobState.total_progress
	1: (max) an UserAndJobState.max_progress
	2: (ptype) an UserAndJobState.progress_type
total_progress is an int
max_progress is an int
progress_type is a string
boolean is an int
auth_info is a reference to a list containing 2 items:
	0: (strat) an UserAndJobState.auth_strategy
	1: (param) an UserAndJobState.auth_param
usermeta is a reference to a hash where the key is a string and the value is a string
job_description is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string

</pre>

=end html

=begin text

$params is an UserAndJobState.ListJobsParams
$jobs is a reference to a list where each element is an UserAndJobState.job_info2
ListJobsParams is a reference to a hash where the following keys are defined:
	services has a value which is a reference to a list where each element is an UserAndJobState.service_name
	filter has a value which is an UserAndJobState.job_filter
	authstrat has a value which is an UserAndJobState.auth_strategy
	authparams has a value which is a reference to a list where each element is an UserAndJobState.auth_param
service_name is a string
job_filter is a string
auth_strategy is a string
auth_param is a string
job_info2 is a reference to a list containing 13 items:
	0: (job) an UserAndJobState.job_id
	1: (users) an UserAndJobState.user_info
	2: (service) an UserAndJobState.service_name
	3: (stage) an UserAndJobState.job_stage
	4: (status) an UserAndJobState.job_status
	5: (times) an UserAndJobState.time_info
	6: (progress) an UserAndJobState.progress_info
	7: (complete) an UserAndJobState.boolean
	8: (error) an UserAndJobState.boolean
	9: (auth) an UserAndJobState.auth_info
	10: (meta) an UserAndJobState.usermeta
	11: (desc) an UserAndJobState.job_description
	12: (res) an UserAndJobState.Results
job_id is a string
user_info is a reference to a list containing 2 items:
	0: (owner) an UserAndJobState.username
	1: (canceledby) an UserAndJobState.username
username is a string
job_stage is a string
job_status is a string
time_info is a reference to a list containing 3 items:
	0: (started) an UserAndJobState.timestamp
	1: (last_update) an UserAndJobState.timestamp
	2: (est_complete) an UserAndJobState.timestamp
timestamp is a string
progress_info is a reference to a list containing 3 items:
	0: (prog) an UserAndJobState.total_progress
	1: (max) an UserAndJobState.max_progress
	2: (ptype) an UserAndJobState.progress_type
total_progress is an int
max_progress is an int
progress_type is a string
boolean is an int
auth_info is a reference to a list containing 2 items:
	0: (strat) an UserAndJobState.auth_strategy
	1: (param) an UserAndJobState.auth_param
usermeta is a reference to a hash where the key is a string and the value is a string
job_description is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string


=end text

=item Description

List jobs.

=back

=cut

 sub list_jobs2
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function list_jobs2 (received $n, expecting 1)");
    }
    {
	my($params) = @args;

	my @_bad_arguments;
        (ref($params) eq 'HASH') or push(@_bad_arguments, "Invalid type for argument 1 \"params\" (value was \"$params\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to list_jobs2:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'list_jobs2');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.list_jobs2",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'list_jobs2',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method list_jobs2",
					    status_line => $self->{client}->status_line,
					    method_name => 'list_jobs2',
				       );
    }
}
 


=head2 list_jobs

  $jobs = $obj->list_jobs($services, $filter)

=over 4

=item Parameter and return types

=begin html

<pre>
$services is a reference to a list where each element is an UserAndJobState.service_name
$filter is an UserAndJobState.job_filter
$jobs is a reference to a list where each element is an UserAndJobState.job_info
service_name is a string
job_filter is a string
job_info is a reference to a list containing 14 items:
	0: (job) an UserAndJobState.job_id
	1: (service) an UserAndJobState.service_name
	2: (stage) an UserAndJobState.job_stage
	3: (started) an UserAndJobState.timestamp
	4: (status) an UserAndJobState.job_status
	5: (last_update) an UserAndJobState.timestamp
	6: (prog) an UserAndJobState.total_progress
	7: (max) an UserAndJobState.max_progress
	8: (ptype) an UserAndJobState.progress_type
	9: (est_complete) an UserAndJobState.timestamp
	10: (complete) an UserAndJobState.boolean
	11: (error) an UserAndJobState.boolean
	12: (desc) an UserAndJobState.job_description
	13: (res) an UserAndJobState.Results
job_id is a string
job_stage is a string
timestamp is a string
job_status is a string
total_progress is an int
max_progress is an int
progress_type is a string
boolean is an int
job_description is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string

</pre>

=end html

=begin text

$services is a reference to a list where each element is an UserAndJobState.service_name
$filter is an UserAndJobState.job_filter
$jobs is a reference to a list where each element is an UserAndJobState.job_info
service_name is a string
job_filter is a string
job_info is a reference to a list containing 14 items:
	0: (job) an UserAndJobState.job_id
	1: (service) an UserAndJobState.service_name
	2: (stage) an UserAndJobState.job_stage
	3: (started) an UserAndJobState.timestamp
	4: (status) an UserAndJobState.job_status
	5: (last_update) an UserAndJobState.timestamp
	6: (prog) an UserAndJobState.total_progress
	7: (max) an UserAndJobState.max_progress
	8: (ptype) an UserAndJobState.progress_type
	9: (est_complete) an UserAndJobState.timestamp
	10: (complete) an UserAndJobState.boolean
	11: (error) an UserAndJobState.boolean
	12: (desc) an UserAndJobState.job_description
	13: (res) an UserAndJobState.Results
job_id is a string
job_stage is a string
timestamp is a string
job_status is a string
total_progress is an int
max_progress is an int
progress_type is a string
boolean is an int
job_description is a string
Results is a reference to a hash where the following keys are defined:
	shocknodes has a value which is a reference to a list where each element is a string
	shockurl has a value which is a string
	workspaceids has a value which is a reference to a list where each element is a string
	workspaceurl has a value which is a string
	results has a value which is a reference to a list where each element is an UserAndJobState.Result
Result is a reference to a hash where the following keys are defined:
	server_type has a value which is a string
	url has a value which is a string
	id has a value which is a string
	description has a value which is a string


=end text

=item Description

List jobs. Leave 'services' empty or null to list jobs from all
services.

@deprecated list_jobs2

=back

=cut

 sub list_jobs
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 2)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function list_jobs (received $n, expecting 2)");
    }
    {
	my($services, $filter) = @args;

	my @_bad_arguments;
        (ref($services) eq 'ARRAY') or push(@_bad_arguments, "Invalid type for argument 1 \"services\" (value was \"$services\")");
        (!ref($filter)) or push(@_bad_arguments, "Invalid type for argument 2 \"filter\" (value was \"$filter\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to list_jobs:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'list_jobs');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.list_jobs",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'list_jobs',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method list_jobs",
					    status_line => $self->{client}->status_line,
					    method_name => 'list_jobs',
				       );
    }
}
 


=head2 list_job_services

  $services = $obj->list_job_services()

=over 4

=item Parameter and return types

=begin html

<pre>
$services is a reference to a list where each element is an UserAndJobState.service_name
service_name is a string

</pre>

=end html

=begin text

$services is a reference to a list where each element is an UserAndJobState.service_name
service_name is a string


=end text

=item Description

List all job services. Note that only services with jobs owned by the
user or shared with the user via the default auth strategy will be
listed.

=back

=cut

 sub list_job_services
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 0)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function list_job_services (received $n, expecting 0)");
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.list_job_services",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'list_job_services',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method list_job_services",
					    status_line => $self->{client}->status_line,
					    method_name => 'list_job_services',
				       );
    }
}
 


=head2 share_job

  $obj->share_job($job, $users)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$users is a reference to a list where each element is an UserAndJobState.username
job_id is a string
username is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$users is a reference to a list where each element is an UserAndJobState.username
job_id is a string
username is a string


=end text

=item Description

Share a job. Sharing a job to the same user twice or with the job owner
has no effect. Attempting to share a job not using the default auth
strategy will fail.

=back

=cut

 sub share_job
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 2)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function share_job (received $n, expecting 2)");
    }
    {
	my($job, $users) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        (ref($users) eq 'ARRAY') or push(@_bad_arguments, "Invalid type for argument 2 \"users\" (value was \"$users\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to share_job:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'share_job');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.share_job",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'share_job',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method share_job",
					    status_line => $self->{client}->status_line,
					    method_name => 'share_job',
				       );
    }
}
 


=head2 unshare_job

  $obj->unshare_job($job, $users)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$users is a reference to a list where each element is an UserAndJobState.username
job_id is a string
username is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$users is a reference to a list where each element is an UserAndJobState.username
job_id is a string
username is a string


=end text

=item Description

Stop sharing a job. Removing sharing from a user that the job is not
shared with or the job owner has no effect. Attemping to unshare a job
not using the default auth strategy will fail.

=back

=cut

 sub unshare_job
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 2)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function unshare_job (received $n, expecting 2)");
    }
    {
	my($job, $users) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        (ref($users) eq 'ARRAY') or push(@_bad_arguments, "Invalid type for argument 2 \"users\" (value was \"$users\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to unshare_job:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'unshare_job');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.unshare_job",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'unshare_job',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method unshare_job",
					    status_line => $self->{client}->status_line,
					    method_name => 'unshare_job',
				       );
    }
}
 


=head2 get_job_owner

  $owner = $obj->get_job_owner($job)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$owner is an UserAndJobState.username
job_id is a string
username is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$owner is an UserAndJobState.username
job_id is a string
username is a string


=end text

=item Description

Get the owner of a job.

=back

=cut

 sub get_job_owner
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_job_owner (received $n, expecting 1)");
    }
    {
	my($job) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_job_owner:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_job_owner');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.get_job_owner",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_job_owner',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_job_owner",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_job_owner',
				       );
    }
}
 


=head2 get_job_shared

  $users = $obj->get_job_shared($job)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
$users is a reference to a list where each element is an UserAndJobState.username
job_id is a string
username is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
$users is a reference to a list where each element is an UserAndJobState.username
job_id is a string
username is a string


=end text

=item Description

Get the list of users with which a job is shared. Only the job owner
may access this method. Returns an empty list for jobs not using the
default auth strategy.

=back

=cut

 sub get_job_shared
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function get_job_shared (received $n, expecting 1)");
    }
    {
	my($job) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to get_job_shared:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'get_job_shared');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.get_job_shared",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'get_job_shared',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return wantarray ? @{$result->result} : $result->result->[0];
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method get_job_shared",
					    status_line => $self->{client}->status_line,
					    method_name => 'get_job_shared',
				       );
    }
}
 


=head2 delete_job

  $obj->delete_job($job)

=over 4

=item Parameter and return types

=begin html

<pre>
$job is an UserAndJobState.job_id
job_id is a string

</pre>

=end html

=begin text

$job is an UserAndJobState.job_id
job_id is a string


=end text

=item Description

Delete a job. Will fail if the job is not complete. Only the job owner
can delete a job.

=back

=cut

 sub delete_job
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 1)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function delete_job (received $n, expecting 1)");
    }
    {
	my($job) = @args;

	my @_bad_arguments;
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 1 \"job\" (value was \"$job\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to delete_job:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'delete_job');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.delete_job",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'delete_job',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method delete_job",
					    status_line => $self->{client}->status_line,
					    method_name => 'delete_job',
				       );
    }
}
 


=head2 force_delete_job

  $obj->force_delete_job($token, $job)

=over 4

=item Parameter and return types

=begin html

<pre>
$token is an UserAndJobState.service_token
$job is an UserAndJobState.job_id
service_token is a string
job_id is a string

</pre>

=end html

=begin text

$token is an UserAndJobState.service_token
$job is an UserAndJobState.job_id
service_token is a string
job_id is a string


=end text

=item Description

Force delete a job - will succeed unless the job has not been started.
In that case, the service must start the job and then delete it, since
a job is not "owned" by any service until it is started. Only the job
owner can delete a job.

=back

=cut

 sub force_delete_job
{
    my($self, @args) = @_;

# Authentication: required

    if ((my $n = @args) != 2)
    {
	Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
							       "Invalid argument count for function force_delete_job (received $n, expecting 2)");
    }
    {
	my($token, $job) = @args;

	my @_bad_arguments;
        (!ref($token)) or push(@_bad_arguments, "Invalid type for argument 1 \"token\" (value was \"$token\")");
        (!ref($job)) or push(@_bad_arguments, "Invalid type for argument 2 \"job\" (value was \"$job\")");
        if (@_bad_arguments) {
	    my $msg = "Invalid arguments passed to force_delete_job:\n" . join("", map { "\t$_\n" } @_bad_arguments);
	    Bio::KBase::Exceptions::ArgumentValidationError->throw(error => $msg,
								   method_name => 'force_delete_job');
	}
    }

    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
	    method => "UserAndJobState.force_delete_job",
	    params => \@args,
    });
    if ($result) {
	if ($result->is_error) {
	    Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
					       code => $result->content->{error}->{code},
					       method_name => 'force_delete_job',
					       data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
					      );
	} else {
	    return;
	}
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method force_delete_job",
					    status_line => $self->{client}->status_line,
					    method_name => 'force_delete_job',
				       );
    }
}
 
  
sub status
{
    my($self, @args) = @_;
    if ((my $n = @args) != 0) {
        Bio::KBase::Exceptions::ArgumentValidationError->throw(error =>
                                   "Invalid argument count for function status (received $n, expecting 0)");
    }
    my $url = $self->{url};
    my $result = $self->{client}->call($url, $self->{headers}, {
        method => "UserAndJobState.status",
        params => \@args,
    });
    if ($result) {
        if ($result->is_error) {
            Bio::KBase::Exceptions::JSONRPC->throw(error => $result->error_message,
                           code => $result->content->{error}->{code},
                           method_name => 'status',
                           data => $result->content->{error}->{error} # JSON::RPC::ReturnObject only supports JSONRPC 1.1 or 1.O
                          );
        } else {
            return wantarray ? @{$result->result} : $result->result->[0];
        }
    } else {
        Bio::KBase::Exceptions::HTTP->throw(error => "Error invoking method status",
                        status_line => $self->{client}->status_line,
                        method_name => 'status',
                       );
    }
}
   

sub version {
    my ($self) = @_;
    my $result = $self->{client}->call($self->{url}, $self->{headers}, {
        method => "UserAndJobState.version",
        params => [],
    });
    if ($result) {
        if ($result->is_error) {
            Bio::KBase::Exceptions::JSONRPC->throw(
                error => $result->error_message,
                code => $result->content->{code},
                method_name => 'force_delete_job',
            );
        } else {
            return wantarray ? @{$result->result} : $result->result->[0];
        }
    } else {
        Bio::KBase::Exceptions::HTTP->throw(
            error => "Error invoking method force_delete_job",
            status_line => $self->{client}->status_line,
            method_name => 'force_delete_job',
        );
    }
}

sub _validate_version {
    my ($self) = @_;
    my $svr_version = $self->version();
    my $client_version = $VERSION;
    my ($cMajor, $cMinor) = split(/\./, $client_version);
    my ($sMajor, $sMinor) = split(/\./, $svr_version);
    if ($sMajor != $cMajor) {
        Bio::KBase::Exceptions::ClientServerIncompatible->throw(
            error => "Major version numbers differ.",
            server_version => $svr_version,
            client_version => $client_version
        );
    }
    if ($sMinor < $cMinor) {
        Bio::KBase::Exceptions::ClientServerIncompatible->throw(
            error => "Client minor version greater than Server minor version.",
            server_version => $svr_version,
            client_version => $client_version
        );
    }
    if ($sMinor > $cMinor) {
        warn "New client version available for Bio::KBase::userandjobstate::Client\n";
    }
    if ($sMajor == 0) {
        warn "Bio::KBase::userandjobstate::Client version is $svr_version. API subject to change.\n";
    }
}

=head1 TYPES



=head2 boolean

=over 4



=item Description

A boolean. 0 = false, other = true.


=item Definition

=begin html

<pre>
an int
</pre>

=end html

=begin text

an int

=end text

=back



=head2 username

=over 4



=item Description

Login name of a KBase user account.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 service_name

=over 4



=item Description

A service name. Alphanumerics and the underscore are allowed.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 service_token

=over 4



=item Description

A globus ID token that validates that the service really is said
service.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 authed

=over 4



=item Description

Specifies whether results returned should be from key/value pairs
set with service authentication (true) or without (false).


=item Definition

=begin html

<pre>
an UserAndJobState.boolean
</pre>

=end html

=begin text

an UserAndJobState.boolean

=end text

=back



=head2 timestamp

=over 4



=item Description

A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference
in time to UTC in the format +/-HHMM, eg:
        2012-12-17T23:24:06-0500 (EST time)
        2013-04-03T08:56:32+0000 (UTC time)


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 job_id

=over 4



=item Description

A job id.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 job_stage

=over 4



=item Description

A string that describes the stage of processing of the job.
One of 'created', 'started', 'completed', 'canceled' or 'error'.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 job_status

=over 4



=item Description

A job status string supplied by the reporting service. No more than
200 characters.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 job_description

=over 4



=item Description

A job description string supplied by the reporting service. No more than
1000 characters.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 progress

=over 4



=item Description

The amount of progress the job has made since the last update. This will
be summed to the total progress so far.


=item Definition

=begin html

<pre>
an int
</pre>

=end html

=begin text

an int

=end text

=back



=head2 detailed_err

=over 4



=item Description

Detailed information about a job error, such as a stacktrace, that will
not fit in the job_status. No more than 100K characters.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 total_progress

=over 4



=item Description

The total progress of a job.


=item Definition

=begin html

<pre>
an int
</pre>

=end html

=begin text

an int

=end text

=back



=head2 max_progress

=over 4



=item Description

The maximum possible progress of a job.


=item Definition

=begin html

<pre>
an int
</pre>

=end html

=begin text

an int

=end text

=back



=head2 progress_type

=over 4



=item Description

The type of progress that is being tracked. One of:
'none' - no numerical progress tracking
'task' - Task based tracking, e.g. 3/24
'percent' - percentage based tracking, e.g. 5/100%


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 InitProgress

=over 4



=item Description

Initialization information for progress tracking. Currently 3 choices:

progress_type ptype - one of 'none', 'percent', or 'task'
max_progress max- required only for task based tracking. The 
        total number of tasks until the job is complete.


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
ptype has a value which is an UserAndJobState.progress_type
max has a value which is an UserAndJobState.max_progress

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
ptype has a value which is an UserAndJobState.progress_type
max has a value which is an UserAndJobState.max_progress


=end text

=back



=head2 Result

=over 4



=item Description

A place where the results of a job may be found.
All fields except description are required.

string server_type - the type of server storing the results. Typically
        either "Shock" or "Workspace". No more than 100 characters.
string url - the url of the server. No more than 1000 characters.
string id - the id of the result in the server. Typically either a
        workspace id or a shock node. No more than 1000 characters.
string description - a free text description of the result.
         No more than 1000 characters.


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
server_type has a value which is a string
url has a value which is a string
id has a value which is a string
description has a value which is a string

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
server_type has a value which is a string
url has a value which is a string
id has a value which is a string
description has a value which is a string


=end text

=back



=head2 Results

=over 4



=item Description

A pointer to job results. All arguments are optional. Applications
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


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
shocknodes has a value which is a reference to a list where each element is a string
shockurl has a value which is a string
workspaceids has a value which is a reference to a list where each element is a string
workspaceurl has a value which is a string
results has a value which is a reference to a list where each element is an UserAndJobState.Result

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
shocknodes has a value which is a reference to a list where each element is a string
shockurl has a value which is a string
workspaceids has a value which is a reference to a list where each element is a string
workspaceurl has a value which is a string
results has a value which is a reference to a list where each element is an UserAndJobState.Result


=end text

=back



=head2 auth_strategy

=over 4



=item Description

An authorization strategy to use for jobs. Other than the
DEFAULT strategy (ACLs local to the UJS and managed by the UJS
sharing functions), currently the only other strategy is the
'kbaseworkspace' strategy, which consults the workspace service for
authorization information.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 auth_param

=over 4



=item Description

An authorization parameter. The contents of this parameter differ by
auth_strategy, but for the workspace strategy it is the workspace id
(an integer) as a string.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 usermeta

=over 4



=item Description

User provided metadata about a job.
Arbitrary key-value pairs provided by the user.


=item Definition

=begin html

<pre>
a reference to a hash where the key is a string and the value is a string
</pre>

=end html

=begin text

a reference to a hash where the key is a string and the value is a string

=end text

=back



=head2 CreateJobParams

=over 4



=item Description

Parameters for the create_job2 method.

Optional parameters:
auth_strategy authstrat - the authorization strategy to use for the
        job. Omit to use the standard UJS authorization. If an
        authorization strategy is supplied, in most cases an authparam must
        be supplied as well.
auth_param - a parameter for the authorization strategy.
usermeta meta - metadata for the job.


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
authstrat has a value which is an UserAndJobState.auth_strategy
authparam has a value which is an UserAndJobState.auth_param
meta has a value which is an UserAndJobState.usermeta

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
authstrat has a value which is an UserAndJobState.auth_strategy
authparam has a value which is an UserAndJobState.auth_param
meta has a value which is an UserAndJobState.usermeta


=end text

=back



=head2 user_info

=over 4



=item Description

Who owns a job and who canceled a job (null if not canceled).


=item Definition

=begin html

<pre>
a reference to a list containing 2 items:
0: (owner) an UserAndJobState.username
1: (canceledby) an UserAndJobState.username

</pre>

=end html

=begin text

a reference to a list containing 2 items:
0: (owner) an UserAndJobState.username
1: (canceledby) an UserAndJobState.username


=end text

=back



=head2 time_info

=over 4



=item Description

Job timing information.


=item Definition

=begin html

<pre>
a reference to a list containing 3 items:
0: (started) an UserAndJobState.timestamp
1: (last_update) an UserAndJobState.timestamp
2: (est_complete) an UserAndJobState.timestamp

</pre>

=end html

=begin text

a reference to a list containing 3 items:
0: (started) an UserAndJobState.timestamp
1: (last_update) an UserAndJobState.timestamp
2: (est_complete) an UserAndJobState.timestamp


=end text

=back



=head2 auth_info

=over 4



=item Description

Job authorization strategy information.


=item Definition

=begin html

<pre>
a reference to a list containing 2 items:
0: (strat) an UserAndJobState.auth_strategy
1: (param) an UserAndJobState.auth_param

</pre>

=end html

=begin text

a reference to a list containing 2 items:
0: (strat) an UserAndJobState.auth_strategy
1: (param) an UserAndJobState.auth_param


=end text

=back



=head2 progress_info

=over 4



=item Description

Job progress information.


=item Definition

=begin html

<pre>
a reference to a list containing 3 items:
0: (prog) an UserAndJobState.total_progress
1: (max) an UserAndJobState.max_progress
2: (ptype) an UserAndJobState.progress_type

</pre>

=end html

=begin text

a reference to a list containing 3 items:
0: (prog) an UserAndJobState.total_progress
1: (max) an UserAndJobState.max_progress
2: (ptype) an UserAndJobState.progress_type


=end text

=back



=head2 job_info2

=over 4



=item Description

Information about a job.


=item Definition

=begin html

<pre>
a reference to a list containing 13 items:
0: (job) an UserAndJobState.job_id
1: (users) an UserAndJobState.user_info
2: (service) an UserAndJobState.service_name
3: (stage) an UserAndJobState.job_stage
4: (status) an UserAndJobState.job_status
5: (times) an UserAndJobState.time_info
6: (progress) an UserAndJobState.progress_info
7: (complete) an UserAndJobState.boolean
8: (error) an UserAndJobState.boolean
9: (auth) an UserAndJobState.auth_info
10: (meta) an UserAndJobState.usermeta
11: (desc) an UserAndJobState.job_description
12: (res) an UserAndJobState.Results

</pre>

=end html

=begin text

a reference to a list containing 13 items:
0: (job) an UserAndJobState.job_id
1: (users) an UserAndJobState.user_info
2: (service) an UserAndJobState.service_name
3: (stage) an UserAndJobState.job_stage
4: (status) an UserAndJobState.job_status
5: (times) an UserAndJobState.time_info
6: (progress) an UserAndJobState.progress_info
7: (complete) an UserAndJobState.boolean
8: (error) an UserAndJobState.boolean
9: (auth) an UserAndJobState.auth_info
10: (meta) an UserAndJobState.usermeta
11: (desc) an UserAndJobState.job_description
12: (res) an UserAndJobState.Results


=end text

=back



=head2 job_info

=over 4



=item Description

Information about a job.
@deprecated job_info2


=item Definition

=begin html

<pre>
a reference to a list containing 14 items:
0: (job) an UserAndJobState.job_id
1: (service) an UserAndJobState.service_name
2: (stage) an UserAndJobState.job_stage
3: (started) an UserAndJobState.timestamp
4: (status) an UserAndJobState.job_status
5: (last_update) an UserAndJobState.timestamp
6: (prog) an UserAndJobState.total_progress
7: (max) an UserAndJobState.max_progress
8: (ptype) an UserAndJobState.progress_type
9: (est_complete) an UserAndJobState.timestamp
10: (complete) an UserAndJobState.boolean
11: (error) an UserAndJobState.boolean
12: (desc) an UserAndJobState.job_description
13: (res) an UserAndJobState.Results

</pre>

=end html

=begin text

a reference to a list containing 14 items:
0: (job) an UserAndJobState.job_id
1: (service) an UserAndJobState.service_name
2: (stage) an UserAndJobState.job_stage
3: (started) an UserAndJobState.timestamp
4: (status) an UserAndJobState.job_status
5: (last_update) an UserAndJobState.timestamp
6: (prog) an UserAndJobState.total_progress
7: (max) an UserAndJobState.max_progress
8: (ptype) an UserAndJobState.progress_type
9: (est_complete) an UserAndJobState.timestamp
10: (complete) an UserAndJobState.boolean
11: (error) an UserAndJobState.boolean
12: (desc) an UserAndJobState.job_description
13: (res) an UserAndJobState.Results


=end text

=back



=head2 job_filter

=over 4



=item Description

A string-based filter for listing jobs.

        If the string contains:
                'R' - running jobs are returned.
                'C' - completed jobs are returned.
                'N' - canceled jobs are returned.
                'E' - jobs that errored out are returned.
                'S' - shared jobs are returned.
        The string can contain any combination of these codes in any order.
        If the string contains none of the codes or is null, all self-owned 
        jobs are returned. If only the S filter is present, all jobs are
        returned. The S filter is ignored for jobs not using the default
        authorization strategy.


=item Definition

=begin html

<pre>
a string
</pre>

=end html

=begin text

a string

=end text

=back



=head2 ListJobsParams

=over 4



=item Description

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


=item Definition

=begin html

<pre>
a reference to a hash where the following keys are defined:
services has a value which is a reference to a list where each element is an UserAndJobState.service_name
filter has a value which is an UserAndJobState.job_filter
authstrat has a value which is an UserAndJobState.auth_strategy
authparams has a value which is a reference to a list where each element is an UserAndJobState.auth_param

</pre>

=end html

=begin text

a reference to a hash where the following keys are defined:
services has a value which is a reference to a list where each element is an UserAndJobState.service_name
filter has a value which is an UserAndJobState.job_filter
authstrat has a value which is an UserAndJobState.auth_strategy
authparams has a value which is a reference to a list where each element is an UserAndJobState.auth_param


=end text

=back



=cut

package Bio::KBase::userandjobstate::Client::RpcClient;
use base 'JSON::RPC::Client';
use POSIX;
use strict;

#
# Override JSON::RPC::Client::call because it doesn't handle error returns properly.
#

sub call {
    my ($self, $uri, $headers, $obj) = @_;
    my $result;


    {
	if ($uri =~ /\?/) {
	    $result = $self->_get($uri);
	}
	else {
	    Carp::croak "not hashref." unless (ref $obj eq 'HASH');
	    $result = $self->_post($uri, $headers, $obj);
	}

    }

    my $service = $obj->{method} =~ /^system\./ if ( $obj );

    $self->status_line($result->status_line);

    if ($result->is_success) {

        return unless($result->content); # notification?

        if ($service) {
            return JSON::RPC::ServiceObject->new($result, $self->json);
        }

        return JSON::RPC::ReturnObject->new($result, $self->json);
    }
    elsif ($result->content_type eq 'application/json')
    {
        return JSON::RPC::ReturnObject->new($result, $self->json);
    }
    else {
        return;
    }
}


sub _post {
    my ($self, $uri, $headers, $obj) = @_;
    my $json = $self->json;

    $obj->{version} ||= $self->{version} || '1.1';

    if ($obj->{version} eq '1.0') {
        delete $obj->{version};
        if (exists $obj->{id}) {
            $self->id($obj->{id}) if ($obj->{id}); # if undef, it is notification.
        }
        else {
            $obj->{id} = $self->id || ($self->id('JSON::RPC::Client'));
        }
    }
    else {
        # $obj->{id} = $self->id if (defined $self->id);
	# Assign a random number to the id if one hasn't been set
	$obj->{id} = (defined $self->id) ? $self->id : substr(rand(),2);
    }

    my $content = $json->encode($obj);

    $self->ua->post(
        $uri,
        Content_Type   => $self->{content_type},
        Content        => $content,
        Accept         => 'application/json',
	@$headers,
	($self->{token} ? (Authorization => $self->{token}) : ()),
    );
}



1;
