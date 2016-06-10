

function UserAndJobState(url, auth, auth_cb, timeout, async_job_check_time_ms, async_version) {
    var self = this;

    this.url = url;
    var _url = url;

    this.timeout = timeout;
    var _timeout = timeout;
    
    this.async_job_check_time_ms = async_job_check_time_ms;
    if (!this.async_job_check_time_ms)
        this.async_job_check_time_ms = 5000;
    this.async_version = async_version;

    if (typeof(_url) != "string" || _url.length == 0) {
        _url = "https://kbase.us/services/userandjobstate/";
    }
    var _auth = auth ? auth : { 'token' : '', 'user_id' : ''};
    var _auth_cb = auth_cb;

     this.ver = function (_callback, _errorCallback) {
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 0+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(0+2)+')';
        return json_call_ajax("UserAndJobState.ver",
            [], 1, _callback, _errorCallback);
    };
 
     this.set_state = function (service, key, value, _callback, _errorCallback) {
        if (typeof service === 'function')
            throw 'Argument service can not be a function';
        if (typeof key === 'function')
            throw 'Argument key can not be a function';
        if (typeof value === 'function')
            throw 'Argument value can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 3+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(3+2)+')';
        return json_call_ajax("UserAndJobState.set_state",
            [service, key, value], 0, _callback, _errorCallback);
    };
 
     this.set_state_auth = function (token, key, value, _callback, _errorCallback) {
        if (typeof token === 'function')
            throw 'Argument token can not be a function';
        if (typeof key === 'function')
            throw 'Argument key can not be a function';
        if (typeof value === 'function')
            throw 'Argument value can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 3+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(3+2)+')';
        return json_call_ajax("UserAndJobState.set_state_auth",
            [token, key, value], 0, _callback, _errorCallback);
    };
 
     this.get_state = function (service, key, auth, _callback, _errorCallback) {
        if (typeof service === 'function')
            throw 'Argument service can not be a function';
        if (typeof key === 'function')
            throw 'Argument key can not be a function';
        if (typeof auth === 'function')
            throw 'Argument auth can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 3+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(3+2)+')';
        return json_call_ajax("UserAndJobState.get_state",
            [service, key, auth], 1, _callback, _errorCallback);
    };
 
     this.has_state = function (service, key, auth, _callback, _errorCallback) {
        if (typeof service === 'function')
            throw 'Argument service can not be a function';
        if (typeof key === 'function')
            throw 'Argument key can not be a function';
        if (typeof auth === 'function')
            throw 'Argument auth can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 3+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(3+2)+')';
        return json_call_ajax("UserAndJobState.has_state",
            [service, key, auth], 1, _callback, _errorCallback);
    };
 
     this.get_has_state = function (service, key, auth, _callback, _errorCallback) {
        if (typeof service === 'function')
            throw 'Argument service can not be a function';
        if (typeof key === 'function')
            throw 'Argument key can not be a function';
        if (typeof auth === 'function')
            throw 'Argument auth can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 3+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(3+2)+')';
        return json_call_ajax("UserAndJobState.get_has_state",
            [service, key, auth], 2, _callback, _errorCallback);
    };
 
     this.remove_state = function (service, key, _callback, _errorCallback) {
        if (typeof service === 'function')
            throw 'Argument service can not be a function';
        if (typeof key === 'function')
            throw 'Argument key can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 2+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(2+2)+')';
        return json_call_ajax("UserAndJobState.remove_state",
            [service, key], 0, _callback, _errorCallback);
    };
 
     this.remove_state_auth = function (token, key, _callback, _errorCallback) {
        if (typeof token === 'function')
            throw 'Argument token can not be a function';
        if (typeof key === 'function')
            throw 'Argument key can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 2+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(2+2)+')';
        return json_call_ajax("UserAndJobState.remove_state_auth",
            [token, key], 0, _callback, _errorCallback);
    };
 
     this.list_state = function (service, auth, _callback, _errorCallback) {
        if (typeof service === 'function')
            throw 'Argument service can not be a function';
        if (typeof auth === 'function')
            throw 'Argument auth can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 2+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(2+2)+')';
        return json_call_ajax("UserAndJobState.list_state",
            [service, auth], 1, _callback, _errorCallback);
    };
 
     this.list_state_services = function (auth, _callback, _errorCallback) {
        if (typeof auth === 'function')
            throw 'Argument auth can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.list_state_services",
            [auth], 1, _callback, _errorCallback);
    };
 
     this.create_job2 = function (params, _callback, _errorCallback) {
        if (typeof params === 'function')
            throw 'Argument params can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.create_job2",
            [params], 1, _callback, _errorCallback);
    };
 
     this.create_job = function (_callback, _errorCallback) {
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 0+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(0+2)+')';
        return json_call_ajax("UserAndJobState.create_job",
            [], 1, _callback, _errorCallback);
    };
 
     this.start_job = function (job, token, status, desc, progress, est_complete, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (typeof token === 'function')
            throw 'Argument token can not be a function';
        if (typeof status === 'function')
            throw 'Argument status can not be a function';
        if (typeof desc === 'function')
            throw 'Argument desc can not be a function';
        if (typeof progress === 'function')
            throw 'Argument progress can not be a function';
        if (typeof est_complete === 'function')
            throw 'Argument est_complete can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 6+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(6+2)+')';
        return json_call_ajax("UserAndJobState.start_job",
            [job, token, status, desc, progress, est_complete], 0, _callback, _errorCallback);
    };
 
     this.create_and_start_job = function (token, status, desc, progress, est_complete, _callback, _errorCallback) {
        if (typeof token === 'function')
            throw 'Argument token can not be a function';
        if (typeof status === 'function')
            throw 'Argument status can not be a function';
        if (typeof desc === 'function')
            throw 'Argument desc can not be a function';
        if (typeof progress === 'function')
            throw 'Argument progress can not be a function';
        if (typeof est_complete === 'function')
            throw 'Argument est_complete can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 5+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(5+2)+')';
        return json_call_ajax("UserAndJobState.create_and_start_job",
            [token, status, desc, progress, est_complete], 1, _callback, _errorCallback);
    };
 
     this.update_job_progress = function (job, token, status, prog, est_complete, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (typeof token === 'function')
            throw 'Argument token can not be a function';
        if (typeof status === 'function')
            throw 'Argument status can not be a function';
        if (typeof prog === 'function')
            throw 'Argument prog can not be a function';
        if (typeof est_complete === 'function')
            throw 'Argument est_complete can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 5+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(5+2)+')';
        return json_call_ajax("UserAndJobState.update_job_progress",
            [job, token, status, prog, est_complete], 0, _callback, _errorCallback);
    };
 
     this.update_job = function (job, token, status, est_complete, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (typeof token === 'function')
            throw 'Argument token can not be a function';
        if (typeof status === 'function')
            throw 'Argument status can not be a function';
        if (typeof est_complete === 'function')
            throw 'Argument est_complete can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 4+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(4+2)+')';
        return json_call_ajax("UserAndJobState.update_job",
            [job, token, status, est_complete], 0, _callback, _errorCallback);
    };
 
     this.get_job_description = function (job, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.get_job_description",
            [job], 5, _callback, _errorCallback);
    };
 
     this.get_job_status = function (job, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.get_job_status",
            [job], 7, _callback, _errorCallback);
    };
 
     this.complete_job = function (job, token, status, error, res, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (typeof token === 'function')
            throw 'Argument token can not be a function';
        if (typeof status === 'function')
            throw 'Argument status can not be a function';
        if (typeof error === 'function')
            throw 'Argument error can not be a function';
        if (typeof res === 'function')
            throw 'Argument res can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 5+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(5+2)+')';
        return json_call_ajax("UserAndJobState.complete_job",
            [job, token, status, error, res], 0, _callback, _errorCallback);
    };
 
     this.get_results = function (job, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.get_results",
            [job], 1, _callback, _errorCallback);
    };
 
     this.get_detailed_error = function (job, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.get_detailed_error",
            [job], 1, _callback, _errorCallback);
    };
 
     this.get_job_info2 = function (job, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.get_job_info2",
            [job], 1, _callback, _errorCallback);
    };
 
     this.get_job_info = function (job, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.get_job_info",
            [job], 1, _callback, _errorCallback);
    };
 
     this.list_jobs2 = function (params, _callback, _errorCallback) {
        if (typeof params === 'function')
            throw 'Argument params can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.list_jobs2",
            [params], 1, _callback, _errorCallback);
    };
 
     this.list_jobs = function (services, filter, _callback, _errorCallback) {
        if (typeof services === 'function')
            throw 'Argument services can not be a function';
        if (typeof filter === 'function')
            throw 'Argument filter can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 2+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(2+2)+')';
        return json_call_ajax("UserAndJobState.list_jobs",
            [services, filter], 1, _callback, _errorCallback);
    };
 
     this.list_job_services = function (_callback, _errorCallback) {
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 0+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(0+2)+')';
        return json_call_ajax("UserAndJobState.list_job_services",
            [], 1, _callback, _errorCallback);
    };
 
     this.share_job = function (job, users, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (typeof users === 'function')
            throw 'Argument users can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 2+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(2+2)+')';
        return json_call_ajax("UserAndJobState.share_job",
            [job, users], 0, _callback, _errorCallback);
    };
 
     this.unshare_job = function (job, users, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (typeof users === 'function')
            throw 'Argument users can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 2+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(2+2)+')';
        return json_call_ajax("UserAndJobState.unshare_job",
            [job, users], 0, _callback, _errorCallback);
    };
 
     this.get_job_owner = function (job, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.get_job_owner",
            [job], 1, _callback, _errorCallback);
    };
 
     this.get_job_shared = function (job, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.get_job_shared",
            [job], 1, _callback, _errorCallback);
    };
 
     this.delete_job = function (job, _callback, _errorCallback) {
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 1+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(1+2)+')';
        return json_call_ajax("UserAndJobState.delete_job",
            [job], 0, _callback, _errorCallback);
    };
 
     this.force_delete_job = function (token, job, _callback, _errorCallback) {
        if (typeof token === 'function')
            throw 'Argument token can not be a function';
        if (typeof job === 'function')
            throw 'Argument job can not be a function';
        if (_callback && typeof _callback !== 'function')
            throw 'Argument _callback must be a function if defined';
        if (_errorCallback && typeof _errorCallback !== 'function')
            throw 'Argument _errorCallback must be a function if defined';
        if (typeof arguments === 'function' && arguments.length > 2+2)
            throw 'Too many arguments ('+arguments.length+' instead of '+(2+2)+')';
        return json_call_ajax("UserAndJobState.force_delete_job",
            [token, job], 0, _callback, _errorCallback);
    };
  

    /*
     * JSON call using jQuery method.
     */
    function json_call_ajax(method, params, numRets, callback, errorCallback, json_rpc_context) {
        var deferred = $.Deferred();

        if (typeof callback === 'function') {
           deferred.done(callback);
        }

        if (typeof errorCallback === 'function') {
           deferred.fail(errorCallback);
        }

        var rpc = {
            params : params,
            method : method,
            version: "1.1",
            id: String(Math.random()).slice(2),
        };
        if (json_rpc_context)
            rpc['context'] = json_rpc_context;

        var beforeSend = null;
        var token = (_auth_cb && typeof _auth_cb === 'function') ? _auth_cb()
            : (_auth.token ? _auth.token : null);
        if (token != null) {
            beforeSend = function (xhr) {
                xhr.setRequestHeader("Authorization", token);
            }
        }

        var xhr = jQuery.ajax({
            url: _url,
            dataType: "text",
            type: 'POST',
            processData: false,
            data: JSON.stringify(rpc),
            beforeSend: beforeSend,
            timeout: _timeout,
            success: function (data, status, xhr) {
                var result;
                try {
                    var resp = JSON.parse(data);
                    result = (numRets === 1 ? resp.result[0] : resp.result);
                } catch (err) {
                    deferred.reject({
                        status: 503,
                        error: err,
                        url: _url,
                        resp: data
                    });
                    return;
                }
                deferred.resolve(result);
            },
            error: function (xhr, textStatus, errorThrown) {
                var error;
                if (xhr.responseText) {
                    try {
                        var resp = JSON.parse(xhr.responseText);
                        error = resp.error;
                    } catch (err) { // Not JSON
                        error = "Unknown error - " + xhr.responseText;
                    }
                } else {
                    error = "Unknown Error";
                }
                deferred.reject({
                    status: 500,
                    error: error
                });
            }
        });

        var promise = deferred.promise();
        promise.xhr = xhr;
        return promise;
    }
}


