package quicknote;

import java.util.ArrayList;
import java.util.List;

import quicknote.storage.QuickNote;
import quicknote.storage.QuickNoteDynamoDbClient;
import quicknote.storage.QuickNoteUserDataItem;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.Session;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;

/**
 * The {@link QuickNoteManager} manages the interaction with QuickNoteDynamoDbClient.
 */
public class QuickNoteManager {

    private final QuickNoteDynamoDbClient dynamoDbClient; 
    
    public QuickNoteManager(final AmazonDynamoDBClient amazonDynamoDbClient) {
    	dynamoDbClient = new QuickNoteDynamoDbClient(amazonDynamoDbClient);
        
    }

    /**
     * Creates and returns response for the SetFreeFormDataIntent where the Note Body is being set
     *
     * @param intent
     * @param session
     * @param myNote 
     * 				the note that we are going to add to the DB
     * 
     * @return response for the add score intent
     */
    public QuickNote saveNote(QuickNote myQuickNote) {
        
    	QuickNoteUserDataItem myQuickNoteUserDataItem = new QuickNoteUserDataItem();
    	
    	myQuickNoteUserDataItem.setCustomerId(myQuickNote.getCustomerId());
    	myQuickNoteUserDataItem.setNoteName(myQuickNote.getNoteName());
    	myQuickNoteUserDataItem.setNoteBody(myQuickNote.getNoteBody());
    	
        //save the note to dynamoDB
        try{	
        	this.dynamoDbClient.saveItem(myQuickNoteUserDataItem);   	
        } catch (Exception e){
        	myQuickNote.setHasError();
        	System.out.println("Here is the exception when saving in DynamoDB: " + e);
        } 
        
        return myQuickNote;
    }
    
    /**
     * Creates and returns response for the  Yes intent.  This yes intent is the user's response to the confirmation
     * question on whether or not the selected note should be deleted.  
     * 
     * This function will attempt to delete the selected note in the DB.
     *
     * @param session
     *            {@link Session} for this request.  This is used to keep track of the delete not candidate
     * @return response for the Yes intent.
     */
	public Boolean confirmDelete(QuickNote deleteThisNote) {
		
		QuickNoteUserDataItem noteToDelete = new QuickNoteUserDataItem();
		
		noteToDelete.setCustomerId(deleteThisNote.getCustomerId());
		noteToDelete.setNoteBody(deleteThisNote.getNoteBody());
		noteToDelete.setNoteName(deleteThisNote.getNoteName());
		
		try{
			dynamoDbClient.deleteItem(noteToDelete);
			
		} catch (Exception e){
			return false;
		}
		
		return true;
	}

    /**
     * Creates and returns response for the get note by name intent
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            {@link Session} for this request
     * @return response for the get note by name intent
     */
	public QuickNote getBestMatchNote(String findThisNoteName, String customerId) {

    	List<QuickNote> itemsFound = null;
       	itemsFound = getAllNotes(customerId);
    	
    	System.out.println("Finding note by name in the manager: " + findThisNoteName);
    	
    	QuickNote bestFound = null;
       	QuickNote myFoundNote = new QuickNote();
    	
    	if (itemsFound == null){
    		myFoundNote.setHasError();
    		return myFoundNote;
    	}
		if (itemsFound.size() <= 0){
			myFoundNote.setNoItemsFoundError();
			return myFoundNote;
		}
		
		System.out.println("Number of items found: " + itemsFound.size());

		bestFound = this.determineBestMatch(itemsFound, findThisNoteName);
		
		myFoundNote.setCustomerId(bestFound.getCustomerId());
		myFoundNote.setNoteName(bestFound.getNoteName());
		myFoundNote.setNoteBody(bestFound.getNoteBody());

        return myFoundNote;
    }
	
	public int getNumOfAllNotes(String customerId) {
		
		int numOfNotes = getAllNotes(customerId).size();
		
		return numOfNotes;
	}
	
	public List<QuickNote> getAllNotes(String customerId) {
		
		List<QuickNoteUserDataItem> itemsFound = null;
		List<QuickNote> quickNotes = null;
		
		try{
			itemsFound = this.dynamoDbClient.findAllUsersItems(customerId);
		} catch (Exception e){
			System.out.println("Here is the exception: " + e.getMessage());
			return null;
		}
		
		quickNotes = new ArrayList<QuickNote>();
		
		for(QuickNoteUserDataItem i: itemsFound){
			QuickNote myQuickNote = new QuickNote();
			myQuickNote.setCustomerId(i.getCustomerId());
			myQuickNote.setNoteBody(i.getNoteBody());
			myQuickNote.setNoteName(i.getNoteName());
			quickNotes.add(myQuickNote);
		}	
		return quickNotes;
	}
	
	/**
	 * This function will return the best matching QuickNoteUserDataItem from a list of QuickNoteUserDataItems.
	 * The best match is determined by finding the note with a title that has the lowest Levenshtein distance from the givenNoteName.
	 * 
	 * NOTE: Currently the function will always return a best match, but we will want to make a cut-off somehow. 
	 * 
	 * @param givenNoteName = item name as determined by NLU/ASR
	 * @param foundList = list of all items returned for that user from DynamoDB
	 * @return the best matching item
	 */
	private QuickNote determineBestMatch(List<QuickNote> foundList, String givenNoteName){
		
		QuickNote firstItem = foundList.get(0);
		int firstItemDistance = this.distance(firstItem.getNoteName(), givenNoteName);
		
		int bestMatchValue = firstItemDistance;
		QuickNote bestMatchItem = firstItem;
		
		for (int i = 1; i <= foundList.size() - 1; i++){
			QuickNote checkItem = foundList.get(i);
			String checkItemName = checkItem.getNoteName();
			
			//run some algorithm for matching
			int resultDistance = this.distance(checkItemName, givenNoteName);
			
			System.out.println(checkItemName + ". " + givenNoteName + ". with score: " + resultDistance);
			
		
			if(resultDistance <= bestMatchValue){
				bestMatchValue = resultDistance;
				bestMatchItem = checkItem;
			}
		}
		return bestMatchItem;
	}
	
	/**
	 * Get the Levenshtein distance, a.k.a. cost, between two strings.
	 * 
	 * The greater the cost the more operations needed in order to 
	 * transform one string into the other.
	 * 
	 * @param a
	 * @param b
	 * @return cost
	 */
    private int distance(String a, String b) {
        a = a.toLowerCase();
        b = b.toLowerCase();

        int [] costs = new int [b.length() + 1];
        for (int j = 0; j < costs.length; j++)
            costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }
}