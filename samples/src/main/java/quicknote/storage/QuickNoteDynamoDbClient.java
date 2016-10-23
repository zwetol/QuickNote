package quicknote.storage;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.document.DeleteItemOutcome;

/**
 * Client for DynamoDB persistence layer for the Quick Note skill.
 */
public class QuickNoteDynamoDbClient {
    private final AmazonDynamoDBClient dynamoDBClient;

    public QuickNoteDynamoDbClient(final AmazonDynamoDBClient dynamoDBClient) {
        this.dynamoDBClient = dynamoDBClient;
    }

    /**
     * Loads an item from DynamoDB by hash key and range key. 
     * Callers of this method should pass in a customer ID and the name
     * of the note to look up.
     * 
     * @param userId
     * @param noteName
     * @return item found in database
     */
    public QuickNoteUserDataItem loadItem(String userId, String noteName) {
        DynamoDBMapper mapper = createDynamoDBMapper();
        QuickNoteUserDataItem item = mapper.load(QuickNoteUserDataItem.class, userId, noteName);

        return item;
    }

    /**
     * Stores an item to DynamoDB.
     * 
     * @param tableItem
     */
    public void saveItem(final QuickNoteUserDataItem tableItem) {
        DynamoDBMapper mapper = createDynamoDBMapper();
        mapper.save(tableItem);
    } 
    

    /**
     * Creates a {@link DynamoDBMapper} using the default configurations.
     * 
     * @return
     */
    private DynamoDBMapper createDynamoDBMapper() {
        return new DynamoDBMapper(dynamoDBClient);
    }
}
