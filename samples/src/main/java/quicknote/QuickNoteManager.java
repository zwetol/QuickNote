package quicknote;

import java.util.List;

import quicknote.storage.QuickNote;
import quicknote.storage.QuickNoteDynamoDbClient;
import quicknote.storage.QuickNoteUserDataItem;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link QuickNoteManager} receives various events and intents and manages the flow of the
 * user's interaction with Alexa.
 */
public class QuickNoteManager {
    /**
     * Intent slot for player name.
     */
    private static final String SLOT_TEXT = "Text";

    private final QuickNoteDynamoDbClient dynamoDbClient; 
    
    public QuickNoteManager(final AmazonDynamoDBClient amazonDynamoDbClient) {
    	dynamoDbClient = new QuickNoteDynamoDbClient(amazonDynamoDbClient);
        
    }

    /**
     * Creates and returns response for Launch request.
     *
     * @param request
     *            {@link LaunchRequest} for this request
     * @param session
     *            Speechlet {@link Session} for this request
     * @return response for launch request
     */
    public SpeechletResponse getLaunchResponse(LaunchRequest request, Session session) {
        String speechText, repromptText;
        
        speechText = "This is Quick Note. You can ask me to create a new note, get an existing note by title, or delete a note by title.  What would you like to do?";
        repromptText = "What would you like me to do?";
        
    	List<QuickNoteUserDataItem> itemsFound = null;
        
        try {	
        	itemsFound = this.dynamoDbClient.findAllUsersItems(session.getUser().getUserId());
    		
    		if (itemsFound.size() <= 0){
    			speechText = "Welcome to Quick Note.  You can ask me to create a new note.";
    			return getAskSpeechletResponse(speechText, repromptText);
    		}
        } catch (Exception e){
        	
        }   

        return getAskSpeechletResponse(speechText, repromptText);
    }

    /**
     * Creates a new empty instance of QuickNote.
     *
     * @return response for the create new note intent
     */
    public QuickNote createEmptyNewNote() {
    	
    	QuickNote myNote = new QuickNote();
        
        return myNote;
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
        } 
        
        return myQuickNote;
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

    	List<QuickNoteUserDataItem> itemsFound = null;
    	
    	QuickNote myFoundNote = new QuickNote();
    	
    	System.out.println("Finding note by name: " + findThisNoteName);
    	
    	QuickNoteUserDataItem bestFound = null;
    	
    	try{
    	
    		itemsFound = this.dynamoDbClient.findAllUsersItems(customerId);
    		    		
    		if (itemsFound.size() <= 0){
    			myFoundNote.setDoesNotExistError();
    		}
    		
    		System.out.println(itemsFound.size());

    		bestFound = this.determineBestMatch(itemsFound, findThisNoteName);
    		
    		myFoundNote.setCustomerId(bestFound.getCustomerId());
    		myFoundNote.setNoteName(bestFound.getNoteName());
    		myFoundNote.setNoteBody(bestFound.getNoteBody());
    		
    	} catch (Exception e){
    		System.out.println("what exception is going on: " + e);
    		myFoundNote.setHasError();
    	}

        return myFoundNote;
    }
	
	/**
	public SpeechletResponse getAllNotes(Intent intent, Session session) {
		
		String speechText;
		List<QuickNoteUserDataItem> itemsFound = null;
		
		try{
			itemsFound = this.dynamoDbClient.findAllUsersItems(session.getUser().getUserId());
			
			if (itemsFound.size() <= 0){
				speechText = "I couldn't find any notes saved for you.";
				return getTellSpeechletResponse(speechText, false);
			}
			
		} catch (Exception e){
			return getTellSpeechletResponse("Error retrieving note.", false);
		}
		
		
		
		return getTellSpeechletResponse("I found " + noteCount + " notes saved for you.  The latest are: " + );
	}
	*/
	
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
	private QuickNoteUserDataItem determineBestMatch(List<QuickNoteUserDataItem> foundList, String givenNoteName){
		
		QuickNoteUserDataItem firstItem = foundList.get(0);
		int firstItemDistance = this.distance(firstItem.getNoteName(), givenNoteName);
		
		int bestMatchValue = firstItemDistance;
		QuickNoteUserDataItem bestMatchItem = firstItem;
		
		for (int i = 1; i < foundList.size() - 1; i++){
			QuickNoteUserDataItem checkItem = foundList.get(i);
			String checkItemName = checkItem.getNoteName();
			
			//run some algorithm for matching
			int resultDistance = this.distance(checkItemName, givenNoteName);
			
		
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
	
    /**
     * Creates and returns response for the DeleteNoteIntentResponse
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            {@link Session} for this request
     * @return response for the delete note by title intent
     */
    //TODO: working on deleteNote refactor
	public QuickNote deleteNote(String customerId, String desiredNoteName) {

    	List<QuickNoteUserDataItem> itemsFound = null;
    	
    	System.out.println("Finding note before deleting, by name: " + desiredNoteName);
    	
    	try{
    		itemsFound = this.dynamoDbClient.findAllUsersItems(session.getUser().getUserId());
    		
    		if (itemsFound.size() <= 0){
            	speechText = "I couldn't find a note by the name: " + desiredNoteName + ". " + "You can ask me to delete the note by title again.";

            	return getAskSpeechletResponse(speechText, speechText);
    		}
    	} catch (Exception e){
			return getTellSpeechletResponse("Error retrieving note.", false);
    	}
		
    	QuickNoteUserDataItem bestMatch = this.determineBestMatch(itemsFound, noteName);
        
        QuickNoteUserDataItem deleteNoteCandidate = new QuickNoteUserDataItem();
        
		deleteNoteCandidate.setCustomerId(bestMatch.getCustomerId());
		deleteNoteCandidate.setNoteName(bestMatch.getNoteName());
		deleteNoteCandidate.setNoteBody(bestMatch.getNoteBody());
		
		session.setAttribute("DeleteNoteCandidate", deleteNoteCandidate);
		
		String foundNoteName = deleteNoteCandidate.getNoteName();
		
		speechText = "Would you like me to delete the note titled: " + foundNoteName;
		repromptText = "I didn't catch that.  Do you want me to delete your note titled: " + foundNoteName;
		
		return getAskSpeechletResponse(speechText, repromptText);
	}
	
    /*
     * Creates and returns response for the  No intent.  This no intent is the user's response to the confirmation
     * question on whether or not the selected note should be deleted.  The No Intent means that we should NOT move forward
     * with deleting the selected note.
     * 
     * This function will clear the delete note candidate from the session and NOT move forward with the deletion.
     * 
     * @param session
     *            {@link Session} for this request. This is used to keep track of the delete note candidate
     * @return response for the Yes intent.
     */
	public SpeechletResponse cancelDelete(Session session) {
        
		session.setAttribute("DeleteNoteCandidate", null);
		
		return getTellSpeechletResponse("ok.  I won't delete it.", false);
	}
	
    /*
     * Creates and returns response for the  Yes intent.  This yes intent is the user's response to the confirmation
     * question on whether or not the selected note should be deleted.  
     * 
     * This function will attempt to delete the selected note in the DB.
     *
     * @param session
     *            {@link Session} for this request.  This is used to keep track of the delete not candidate
     * @return response for the Yes intent.
     */
	public SpeechletResponse confirmDelete(Session session) {
		
		ObjectMapper mapper = new ObjectMapper();
        QuickNoteUserDataItem deleteThisNote = mapper.convertValue(session.getAttribute("DeleteNoteCandidate"), QuickNoteUserDataItem.class);
		
		if (deleteThisNote == null){
			return getTellSpeechletResponse("Error deleting note.", false);
		}
		
		String deletedNoteTitle = deleteThisNote.getNoteName();
		
		try{
			dynamoDbClient.deleteItem(session, deleteThisNote);
			
		} catch (Exception e){
			return getTellSpeechletResponse("I'm having trouble deleting your note." + e.getMessage(), false);
		}
		session.setAttribute("DeleteNoteCandidate", null);
		
		return getTellSpeechletResponse("Sure. Deleting your note titled:  " + deletedNoteTitle, true);
	}

    /**
     * Returns an ask Speechlet response for a speech and reprompt text.
     *
     * @param speechText
     *            Text for speech output
     * @param repromptText
     *            Text for reprompt output
     * @param shouldEndSession
     * 			  Set whether the session should end after the response is sent
     * @return ask Speechlet response for a speech and reprompt text
     */
    private SpeechletResponse getAskSpeechletResponse(String speechText, String repromptText) {
    	
        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
        repromptSpeech.setText(repromptText);
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptSpeech);
        
    	SpeechletResponse response = new SpeechletResponse();
    	response = SpeechletResponse.newAskResponse(speech, reprompt);

        return response;
    }

    /**
     * Returns a tell Speechlet response for a speech and reprompt text.
     *
     * @param speechText
     *            Text for speech output
     * @param shouldSendCard
     * 			  Set whether to send a card to the Companion app for the response
     * @param shouldEndSession
     * 			  Set whether the session should end after the response is sent
     * @return a tell Speechlet response for a speech and reprompt text
     */
    private SpeechletResponse getTellSpeechletResponse(String speechText, Boolean shouldSendCard) {
     
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);
        
    	SpeechletResponse response = new SpeechletResponse();
    	
    	if (shouldSendCard == true){
            SimpleCard card = new SimpleCard();
            card.setTitle("Alexa Skills Card:  Quick Note");
            card.setContent(speechText);
            response = SpeechletResponse.newTellResponse(speech, card);
    	}
    	else{
    		response = SpeechletResponse.newTellResponse(speech);
    	}

    	return response;
    }
}