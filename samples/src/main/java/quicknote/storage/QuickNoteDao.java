package quicknote.storage;

import com.amazon.speech.speechlet.Session;

/**
 * Contains the methods to interact with the persistence layer for QuickNote in DynamoDB.
 */
public class QuickNoteDao {
    private final QuickNoteDynamoDbClient dynamoDbClient;

    public QuickNoteDao(QuickNoteDynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    /**
     * Reads and returns the {@link QuickNoteUserDataItem} using user information from the session.
     * 
     * 
     * @param session
     * @return user's saved note or null if the item could not be found in DB
     */
    public QuickNoteUserDataItem getQuickNoteUserDataItem(Session session, String noteName) throws Exception {
        String userId = session.getUser().getUserId();
    	
    	QuickNoteUserDataItem item = new QuickNoteUserDataItem();
        item = dynamoDbClient.loadItem(userId, noteName);
        
        if (item == null) {
            return null;
        }

        return item;
    }

    /**
     * Saves the {@link myNote} into the database.
     * 
     * @param myNote
     */
    public void saveQuickNote(Session session, QuickNoteUserDataItem myNote) throws Exception {
    	
        dynamoDbClient.saveItem(myNote);
    }
}
