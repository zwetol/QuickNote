package quicknote;

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
    public SpeechletResponse getCreateNewNoteIntentResponse(Session session) {

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
    public SpeechletResponse getSetNoteTitleIntentResponse(Intent intent, Session session, QuickNoteUserDataItem myNote) {
    	    	
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
  
        myNote.setNoteName(newNoteName);

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
    public SpeechletResponse getSetNoteBodyIntentResponse(Intent intent, Session session, QuickNoteUserDataItem myNote) {
 
    	String newNoteBody = intent.getSlot(SLOT_TEXT).getValue();
    	String speechText;
    	String repromptText;
    	
    	if (newNoteBody == null || newNoteBody == ""){
    		speechText = "Sorry I couldn't understand your note content.  Please tell me again.";
    		repromptText = "I didn't catch that.  What is the note content?";
    		
    		return getAskSpeechletResponse(speechText, repromptText);
    	}
                
        myNote.setNoteBody(newNoteBody);
        myNote.setCustomerId(session.getUser().getUserId());
        
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
    	QuickNoteUserDataItem itemFound = null;
    	String noteName = intent.getSlot(SLOT_TEXT).getValue().toString();
    	
    	System.out.println("Finding note by name: " + noteName);
    	
    	if (noteName == null){
    		speechText = "I couldn't understand the name of your note.  Please ask me to find it again.";
    		
    		return getTellSpeechletResponse(speechText, false);
    	}
        try{
        	itemFound = this.dynamoDbClient.loadItem(session.getUser().getUserId(), noteName);

            if (itemFound == null){
            	speechText = "I couldn't find a note by that name.  You can ask me to find it again.";
            	
            	return getTellSpeechletResponse(speechText, false);
            }
            speechText = itemFound.getNoteBody();	             
        } catch (Exception e){
        	return getTellSpeechletResponse("Error retrieving note.", false);
        }
        return getTellSpeechletResponse("Found the note titled: " + noteName + ", which reads: " + speechText, true);
    }
	
	/*
	 * This function has a side effect of changing the deleteNoteCandidate values.  I use this 
	 * to keep track of state, but I should probably have something else to track it?
	 */
	public SpeechletResponse getDeleteNoteIntentResponse(Session session, Intent intent, QuickNoteUserDataItem deleteNoteCandidate) {
		QuickNoteUserDataItem itemFound = null;
    	String noteName = intent.getSlot(SLOT_TEXT).getValue().toString();
    	String speechText;
    	String repromptText;
    	
    	System.out.println("Finding note before deleting, by name: " + noteName);
		
		try{
			itemFound = this.dynamoDbClient.loadItem(session.getUser().getUserId(), noteName);
			
			if (itemFound == null){
            	speechText = "I couldn't find a note by that name.  You can ask me to delete the note again.";
            	
            	return getTellSpeechletResponse(speechText, false);
			}
		} catch (Exception e){
			return getTellSpeechletResponse("Error retrieving note.", false);
		}
		
		deleteNoteCandidate.setCustomerId(itemFound.getCustomerId());
		deleteNoteCandidate.setNoteName(itemFound.getNoteName());
		deleteNoteCandidate.setNoteBody(itemFound.getNoteBody());
		
		System.out.println("check this " + deleteNoteCandidate.getCustomerId());
		String foundNoteName = deleteNoteCandidate.getNoteName();
		
		speechText = "Would you like me to delete the note titled: " + foundNoteName;
		repromptText = "I didn't catch that.  Do you want me to delete your note titled: " + foundNoteName;
		
		return getAskSpeechletResponse(speechText, repromptText);
	}

	public SpeechletResponse getNoIntentResponse(Session seession) {
		return getTellSpeechletResponse("ok.  I won't delete it.", false);
	}

	public SpeechletResponse getYesIntentResponse(Session session, QuickNoteUserDataItem deleteThisNote ) {
		
		if (deleteThisNote == null){
			return getTellSpeechletResponse("Error deleting note.", false);
		}
		
		String deletedNoteTitle = deleteThisNote.getNoteName();
		
		try{
			dynamoDbClient.deleteItem(session, deleteThisNote);
			
		} catch (Exception e){
			return getTellSpeechletResponse("I'm having trouble deleting your note." + e.getMessage(), false);
		}
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
        return getTellSpeechletResponse("You can tell me to create a note or ask for an existing note.", false);
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

        return SpeechletResponse.newAskResponse(speech, reprompt);
    }

    /**
     * Returns a tell Speechlet response for a speech and reprompt text.
     *
     * @param speechText
     *            Text for speech output
     * @return a tell Speechlet response for a speech and reprompt text
     */
    private SpeechletResponse getTellSpeechletResponse(String speechText, Boolean isSendCard) {
     
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

    	if (isSendCard == true){
            SimpleCard card = new SimpleCard();
            card.setTitle("Alexa Skills Card:  Quick Note");
            card.setContent(speechText);
            return SpeechletResponse.newTellResponse(speech, card);
    	}
    	else{
    		return SpeechletResponse.newTellResponse(speech);
    	}
    }
}
