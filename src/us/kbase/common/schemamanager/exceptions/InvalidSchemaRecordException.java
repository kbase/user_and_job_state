package us.kbase.common.schemamanager.exceptions;

/** 
 * Thrown when the schema records in the schema database are corrupt.
 * @author gaprice@lbl.gov
 *
 */
public class InvalidSchemaRecordException extends SchemaException {

	private static final long serialVersionUID = 1L;
	
	public InvalidSchemaRecordException() { super(); }
	public InvalidSchemaRecordException(String message) { super(message); }
	public InvalidSchemaRecordException(String message, Throwable cause) { super(message, cause); }
	public InvalidSchemaRecordException(Throwable cause) { super(cause); }
}
