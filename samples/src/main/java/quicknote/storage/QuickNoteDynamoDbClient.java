package quicknote.storage;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

/**
 * Client for DynamoDB persistance layer for the Score Keeper skill.
 */
public class QuickNoteDynamoDbClient {
    private final AmazonDynamoDBClient dynamoDBClient;

    public QuickNoteDynamoDbClient(final AmazonDynamoDBClient dynamoDBClient) {
        this.dynamoDBClient = dynamoDBClient;
    }

    /**
     * Loads an item from DynamoDB by primary Hash Key. Callers of this method should pass in an
     * object which represents an item in the DynamoDB table item with the primary key populated.
     * 
     * @param tableItem
     * @return
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
