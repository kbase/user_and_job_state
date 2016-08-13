package us.kbase.common.schemamanager.exceptions;

/** 
 * Thrown when there is no way to upgrade from one schema version to another,
 * or the schema version of the database is higher than the schema version
 * of the the codebase.
 * @author gaprice@lbl.gov
 *
 */
public class UpdateInProgressException extends SchemaException {

	private static final long serialVersionUID = 1L;
	
	public UpdateInProgressException() { super(); }
	public UpdateInProgressException(String message) { super(message); }
	public UpdateInProgressException(String message, Throwable cause) { super(message, cause); }
	public UpdateInProgressException(Throwable cause) { super(cause); }
}
