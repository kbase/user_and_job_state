package us.kbase.userandjobstate.authorization.exceptions;

/** 
 * Thrown when authorization fails.
 * @author gaprice@lbl.gov
 *
 */
public class UJSAuthorizationException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public UJSAuthorizationException() { super(); }
	public UJSAuthorizationException(String message) { super(message); }
	public UJSAuthorizationException(String message, Throwable cause) { super(message, cause); }
	public UJSAuthorizationException(Throwable cause) { super(cause); }
}
