package quicknote;

import java.util.List;

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
    
    private static final String NEW_NOTE_KEY = "NewNote";
    
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
        // Speak welcome message and ask user questions
        // based on whether the customer has any notes saved already
        String speechText, repromptText;
        
        //TODO: implement a check for existing notes for the user.  If there is no existing note, then read the following.
        //speechText = "This is Quick Note.  You can ask me to create a new note.";
        //return getTellSpeechletResponse(speechText, false);
        
        //TODO add check for existing notes from that user. If there are existing notes, then read the following
        speechText = "This is Quick Note.  You can ask me to create a new note, or you can ask for an existing note by name. What do you want me to do?";
        repromptText = "What would you like me to do?";

        return getAskSpeechletResponse(speechText, repromptText);
    }

    /**
     * Creates and returns response for the new note intent.
     *
     * @param session
     *            {@link Session} for the request
     *
     * @return response for the create new note intent
     */
    public SpeechletResponse putCreateNewNoteIntentResponse(Session session) {
    	QuickNoteUserDataItem myNote = new QuickNoteUserDataItem();
    			
    	session.setAttribute(NEW_NOTE_KEY, (QuickNoteUserDataItem) myNote);
    	
        String speechText = "What is the name of your new note?";
        String repromptText = "Please tell me the name of your new note.";
        
        return getAskSpeechletResponse(speechText, repromptText);
        
    }

    /**
     * Creates and returns response for the new note title.
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            Speechlet {@link Session} for this request
     * @param quickNoteUserDataItem           
     * 
     * @return response for the SetFreeFormDataIntent where the Note Name is being set
     */
    public SpeechletResponse putSetNoteTitleIntentResponse(Intent intent, Session session) {
    	    	
        String newNoteName = intent.getSlot(SLOT_TEXT).getValue();
        
        String speechText;
        String repromptText;
        
        if (newNoteName == null || newNoteName == ""){
        	speechText = "Sorry I didn't catch that name. Please tell me again.";
        	repromptText = "I couldn't hear your note name.  Please tell me the name again.";
        	return getAskSpeechletResponse(speechText, repromptText);
        }
                
        speechText = "OK. What is the content of your new note?";
        repromptText = "I didn't catch that. What is the content of your new note?";
        
        ObjectMapper mapper = new ObjectMapper();
        QuickNoteUserDataItem myNote = mapper.convertValue(session.getAttribute("NewNote"), QuickNoteUserDataItem.class);
        myNote.setNoteName(newNoteName);
        
        session.setAttribute(NEW_NOTE_KEY, myNote);

        return getAskSpeechletResponse(speechText, repromptText);
    }

    /**
     * Creates and returns response for the SetFreeFormDataIntent where the Note Body is being set
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            {@link Session} for this request
     * @param myNote 
     * 				the note that we are going to add to the DB
     * 
     * @return response for the add score intent
     */
    public SpeechletResponse postSetNoteBodyIntentResponse(Intent intent, Session session) {
 
    	String newNoteBody = intent.getSlot(SLOT_TEXT).getValue();
    	String speechText;
    	String repromptText;
    	
    	if (newNoteBody == null || newNoteBody == ""){
    		speechText = "Sorry I couldn't understand your note content.  Please tell me again.";
    		repromptText = "I didn't catch that.  What is the note content?";
    		
    		return getAskSpeechletResponse(speechText, repromptText);
    	}
         
    	ObjectMapper mapper = new ObjectMapper();
        QuickNoteUserDataItem myNote = mapper.convertValue(session.getAttribute("NewNote"), QuickNoteUserDataItem.class);
        myNote.setNoteBody(newNoteBody);
        myNote.setCustomerId(session.getUser().getUserId());
        
        session.setAttribute(NEW_NOTE_KEY, myNote);
        
        System.out.println("note name: " + myNote.getNoteName());
        System.out.println("note body:" + myNote.getNoteBody());
        System.out.println("note customer: " + myNote.getCustomerId());
        
        
        //save the note to dynamoDB
        try{
        	this.dynamoDbClient.saveItem(myNote);
        } catch (Exception e){
        	return getTellSpeechletResponse("Error saving note.", false);
        }   
        speechText =  myNote.getNoteName() + " saved with " + myNote.getNoteBody();
        
        //delete the session's existing note attribute since the note was saved successfully
        session.removeAttribute(NEW_NOTE_KEY);
        
        return getTellSpeechletResponse(speechText, true);
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
	public SpeechletResponse getGetNoteByTitleIntentResponse(Intent intent, Session session) {
    	
    	String speechText;
    	List<QuickNoteUserDataItem> itemsFound = null;
    	String noteName = intent.getSlot(SLOT_TEXT).getValue().toString();
    	
    	System.out.println("Finding note by name: " + noteName);
    	
    	QuickNoteUserDataItem bestFound = null;
    	
    	if (noteName == null || noteName == ""){
    		speechText = "I couldn't understand the name of your note.  Please ask me to find it again.";
    		
    		return getTellSpeechletResponse(speechText, false);
    	}
    	
    	try{
    	
    		itemsFound = this.dynamoDbClient.findAllUsersItems(session.getUser().getUserId());
    		    		
    		if (itemsFound == null){
    			speechText = "I couldn't find any notes for the user.";
    			return getTellSpeechletResponse(speechText, false);
    		}
    		
    		System.out.println(itemsFound.size());

    		bestFound = this.determineBestMatch(itemsFound, noteName);
    		
    		speechText = bestFound.getNoteBody();
    		
    	} catch (Exception e){
    		System.out.println("what exception is going on: " + e);
    		return getTellSpeechletResponse("Error retrieving note.", false);
    	}

        return getTellSpeechletResponse("Found this note title: " + bestFound.getNoteName() + ", which reads: " + speechText, true);
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
	public QuickNoteUserDataItem determineBestMatch(List<QuickNoteUserDataItem> foundList, String givenNoteName){
		
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
    public int distance(String a, String b) {
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
	public SpeechletResponse putDeleteNoteIntentResponse(Session session, Intent intent) {
    	String noteName = intent.getSlot(SLOT_TEXT).getValue().toString();
    	String speechText;
    	String repromptText;
    	
    	List<QuickNoteUserDataItem> itemsFound = null;
    	
    	System.out.println("Finding note before deleting, by name: " + noteName);
    	
    	try{
    		itemsFound = this.dynamoDbClient.findAllUsersItems(session.getUser().getUserId());
    		
    		if (itemsFound == null){
            	speechText = "I couldn't find a note by the name: " + noteName + ". " + "You can ask me to delete the note by title again.";

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
	public SpeechletResponse postNoIntentResponse(Session session) {
        
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
	public SpeechletResponse postYesIntentResponse(Session session) {
		
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
     * Creates and returns response for the help intent.
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            {@link Session} for this request
     * @return response for the help intent
     */
    public SpeechletResponse getHelpIntentResponse(Intent intent, Session session) {
    	
    	String prompt = "You can tell me to create a note or ask for an existing note by title. You can also ask me to delete an existing note by name."
    			+ " What would you like me to do?";
    	String reprompt = "What would you like me to do?";
    	
        return getAskSpeechletResponse(prompt, reprompt);
    }

    /**
     * Creates and returns response for the exit intent.
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            {@link Session} for this request
     * @return response for the exit intent
     */
    public SpeechletResponse getExitIntentResponse(Intent intent, Session session) {
        return getTellSpeechletResponse("Exiting quick note. Goodbye", false);
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