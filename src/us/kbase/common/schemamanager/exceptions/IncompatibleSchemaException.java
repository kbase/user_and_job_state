package us.kbase.common.schemamanager.exceptions;

/** 
 * Thrown when there is no way to upgrade from one schema version to another,
 * or the schema version of the database is higher than the schema version
 * of the the codebase.
 * @author gaprice@lbl.gov
 *
 */
public class IncompatibleSchemaException extends SchemaException {

	private static final long serialVersionUID = 1L;
	
	public IncompatibleSchemaException() { super(); }
	public IncompatibleSchemaException(String message) { super(message); }
	public IncompatibleSchemaException(String message, Throwable cause) { super(message, cause); }
	public IncompatibleSchemaException(Throwable cause) { super(cause); }
}
