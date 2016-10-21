package quicknote.storage;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;


/**
 * Model representing an item of the QuickNoteUserData table in DynamoDB for the QuickNote
 * skill.
 */
@DynamoDBTable(tableName = "QuickNoteUserData")
public class QuickNoteUserDataItem {
    
    private String customerId;

    private String noteName;
    
    private String noteBody;
    
    
    @DynamoDBHashKey(attributeName = "CustomerId")
    public String getCustomerId() {
        return customerId;
    }
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    @DynamoDBRangeKey(attributeName = "NoteName")
    public String getNoteName() {
    	return noteName;
    }
    public void setNoteName(String noteName) {
    	this.noteName = noteName;
    }
    
    @DynamoDBAttribute(attributeName = "NoteBody")
    public String getNoteBody() {
    	return noteBody;
    }
    public void setNoteBody(String noteBody) {
    	this.noteBody = noteBody;
    }
}
