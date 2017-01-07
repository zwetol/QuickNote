package quicknote;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quicknote.storage.QuickNote;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link QuickNoteSpeechlet} receives various intents and requests the appropriate response from the QuickNoteManager.
 */
public class QuickNoteSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(QuickNoteSpeechlet.class);

    private AmazonDynamoDBClient amazonDynamoDBClient;

    private QuickNoteManager quickNoteManager;
    
    private static final String NEW_NOTE_KEY = "NewNote";
    
    private static final String SLOT_TEXT = "Text";

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        initializeComponents();
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        
        return getLaunchResponse(request, session);
    }

    @Override
    public SpeechletResponse onIntent(IntentRequest request, Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        initializeComponents();
        
        ObjectMapper mapper = new ObjectMapper();
        QuickNote myNote = mapper.convertValue(session.getAttribute("NewNote"), QuickNote.class);
        
        QuickNote deleteNoteCandidate = mapper.convertValue(session.getAttribute("DeleteNoteCandidate"), QuickNote.class);
                
        Intent intent = request.getIntent();
        
        if ("CreateNewNoteIntent".equals(intent.getName())) {
        	return createNewNote(session);
        } 
        else if("GetNoteByTitleIntent".equals(intent.getName())){
        	return getNote(intent, session);
        }   
    	else if ("DeleteNoteByTitleIntent".equals(intent.getName())){
    		return deleteNote(session, intent);
    	}
    	else if ("AMAZON.NoIntent".equals(intent.getName()) && deleteNoteCandidate != null){
    		return cancelDelete(session);
    	}
        else if ("AMAZON.YesIntent".equals(intent.getName()) && deleteNoteCandidate != null){  
    		return confirmDelete(session);
    	} 
        
        /**
         * the SetFreeFormDataIntent applies to two different user interactions: setting the title
        * AND setting the body of the note.  We want to determine how to execute on this intent 
        * based on what point the user is within the interaction.
        **/ 
        else if("SetFreeFormDataIntent".equals(intent.getName()) && myNote != null){
        	
        	if (myNote.getNoteName() == null && myNote.getNoteBody() == null){
            	return setNoteTitle(intent, session);
        	}
        	else if (myNote.getNoteName() != null && myNote.getNoteBody() == null) {
                return setNoteBodyAndSave(intent, session);  
            } 
        	else {
            	log.error("Unrecognized intent: " + intent.getName());
                throw new IllegalArgumentException("Unrecognized SetFreeFormDataIntent action " + intent.getName());
        	}
        } 
        /*
        else if("ReadAllNotes".equals(intent.getName())){
        	return quickNoteManager.getAllNotes(intent, session);
        }
        */
        else if("AMAZON.HelpIntent".equals(intent.getName())){
        	return getHelpIntentResponse(intent, session);
        }
        else if ("AMAZON.CancelIntent".equals(intent.getName())){
        	return getExitIntentResponse(intent, session);
        }
        else if ("AMAZON.ExitIntent".equals(intent.getName()) || "AMAZON.StopIntent".equals(intent.getName())){
        	return getExitIntentResponse(intent, session);
        }
        else {
        	log.error("Unrecognized intent: " + intent.getName());
            return getHelpIntentResponse(intent, session);
        }
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
    }

    /**
     * Initializes the instance components if needed.
     */
    private void initializeComponents() {
        if (amazonDynamoDBClient == null) {
            amazonDynamoDBClient = new AmazonDynamoDBClient();
            quickNoteManager = new QuickNoteManager(amazonDynamoDBClient);
        }
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
     
        int numItemsFound = quickNoteManager.getNumOfAllNotes(session.getUser().getUserId());
    		
    	if (numItemsFound <= 0){
    		speechText = "Welcome to Quick Note.  You can ask me to create a new note.";
    		repromptText = "You can ask me to create a new note.";
    		return getAskSpeechletResponse(speechText, repromptText);
    	} 

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
    public SpeechletResponse createNewNote(Session session) {
    	
    	QuickNote myNote = new QuickNote();
    			
    	session.setAttribute(NEW_NOTE_KEY, (QuickNote) myNote);
    	
        String speechText = "What is the name of your new note?";
        String repromptText = "Please tell me the name of your new note.";
        
        return getAskSpeechletResponse(speechText, repromptText);
        
    }
    
    public SpeechletResponse setNoteTitle(Intent intent, Session session) {
    	
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
        QuickNote myNote = mapper.convertValue(session.getAttribute("NewNote"), QuickNote.class);
        myNote.setNoteName(newNoteName);
        
        session.setAttribute(NEW_NOTE_KEY, myNote);

        return getAskSpeechletResponse(speechText, repromptText);
    }
    
    /**
     * Creates and returns response for the SetFreeFormDataIntent where the Note Body is being set
     *
     * @param intent
     * @param session
     * 
     * @return response for the add score intent
     */
    public SpeechletResponse setNoteBodyAndSave(Intent intent, Session session) {
 
    	String newNoteBody = intent.getSlot(SLOT_TEXT).getValue();
    	String speechText;
    	String repromptText;
    	
    	if (newNoteBody == null || newNoteBody == ""){
    		speechText = "Sorry I couldn't understand your note content.  Please tell me again.";
    		repromptText = "I didn't catch that.  What is the note content?";
    		
    		return getAskSpeechletResponse(speechText, repromptText);
    	}
         
    	ObjectMapper mapper = new ObjectMapper();
        QuickNote myNote = mapper.convertValue(session.getAttribute("NewNote"), QuickNote.class);
        myNote.setNoteBody(newNoteBody);
        myNote.setCustomerId(session.getUser().getUserId());
        
        session.setAttribute(NEW_NOTE_KEY, myNote);
        
        System.out.println("note name: " + myNote.getNoteName());
        System.out.println("note body:" + myNote.getNoteBody());
        System.out.println("note customer: " + myNote.getCustomerId());
        
        //save the note to dynamoDB
        QuickNote savedNote = quickNoteManager.saveNote(myNote);
        
        if (savedNote.getHasError()){
        	speechText = "Error saving note.";
        }else{
            speechText =  savedNote.getNoteName() + ", saved with: " + savedNote.getNoteBody();
        }
        
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
	public SpeechletResponse getNote(Intent intent, Session session) {
    	
    	String speechText;
    	String noteName = intent.getSlot(SLOT_TEXT).getValue().toString();
    	String customerId = session.getUser().getUserId();
    	
    	System.out.println("Finding note by name in the speechlet: " + noteName);
    	
    	QuickNote bestFound = null;
    	
    	if (noteName == null || noteName == ""){
    		speechText = "I couldn't understand the name of your note.  Please ask me to find it again.";
    		return getTellSpeechletResponse(speechText, false);
    	}
    	
    	bestFound = quickNoteManager.getBestMatchNote(noteName, customerId);
    	    		    		
    	if (bestFound.getHasError()){
    		speechText = "Error retrieving note.";
    		return getTellSpeechletResponse(speechText, false);
    	}
    	if (bestFound.getNoItemsFoundError()){
    		speechText = "I do not see any notes saved for you.";
    		return getTellSpeechletResponse(speechText, false);
    	}
    		
    	speechText = "Found this note title: " + bestFound.getNoteName() + ", which reads: " + bestFound.getNoteBody();
    	
        return getTellSpeechletResponse(speechText, true);
    }
	
	public SpeechletResponse getAllNotes(Session session) {
		
		List<QuickNote> itemsFound = null;
		
		itemsFound = quickNoteManager.getAllNotes(session.getUser().getUserId());
		
		if (itemsFound == null){
			return getTellSpeechletResponse("Error retrieving note.", false);
		}
		
		int numItemsFound = itemsFound.size();
			
		String speechText = null;
				
		if (numItemsFound <= 0){
			speechText = "I couldn't find any notes saved for you.";
		}

		if (numItemsFound == 1){
			speechText = "I found one note saved for you. It is: " + itemsFound.get(0).getNoteName() +
					", which reads: " + itemsFound.get(0).getNoteBody();
		}
		
		if (numItemsFound == 2){
			speechText = "I found two notes saved for you. They are: " + itemsFound.get(0).getNoteName() +
					", which reads: " + itemsFound.get(0).getNoteBody() +
					". And the second one is: " + itemsFound.get(1).getNoteName() + ", which reads: " +
					itemsFound.get(1).getNoteBody() + ".";
		}
		
		if (numItemsFound == 3){
			speechText = "I found three notes saved for you. They are: " + itemsFound.get(0).getNoteName() +
					", which reads: " + itemsFound.get(0).getNoteBody() +
					". The second one is: " + itemsFound.get(1).getNoteName() + ", which reads: " +
					itemsFound.get(1).getNoteBody() + ". And the third note is: " +
					itemsFound.get(2).getNoteName() + ", which reads: " + itemsFound.get(2).getNoteName();
		}
		
		if (numItemsFound >= 4){
			speechText = "I found: " + numItemsFound + "saved for you. The three most recently added are: " +
					itemsFound.get(0).getNoteName() + ", which reads: " + itemsFound.get(0).getNoteBody() + 
					". The second one is: " + itemsFound.get(1).getNoteName() + 
					", which reads: " + itemsFound.get(1).getNoteBody() + ". And the third note is: " + 
					itemsFound.get(2).getNoteName() + ", which reads: " + itemsFound.get(2).getNoteBody() + ".";
					
		}
		return getTellSpeechletResponse(speechText, true);
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
	public SpeechletResponse deleteNote(Session session, Intent intent) {
    	String noteName = intent.getSlot(SLOT_TEXT).getValue().toString();
    	String speechText;
    	String repromptText;
    	
    	System.out.println("Finding note before deleting, by name: " + noteName);
    	
    	QuickNote deleteNoteCandidate = quickNoteManager.getBestMatchNote(noteName, session.getUser().getUserId());
    	
    	if (deleteNoteCandidate.getNoItemsFoundError()){
            speechText = "I couldn't find any notes saved for you.";
            return getAskSpeechletResponse(speechText, speechText);
    	}
    	
    	if (deleteNoteCandidate.getHasError()){
			return getTellSpeechletResponse("Error retrieving note.", false);
    	}
		
		session.setAttribute("DeleteNoteCandidate", deleteNoteCandidate);
		
		String foundNoteName = deleteNoteCandidate.getNoteName();
		
		speechText = "Would you like me to delete the note titled: " + foundNoteName;
		repromptText = "I didn't catch that.  Do you want me to delete your note titled: " + foundNoteName;
		
		return getAskSpeechletResponse(speechText, repromptText);
	}
	
    /**
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
	
    /**
     * Creates and returns response for the YesIntent that is interpreted as a confirmDelete action.
     * 
     * This function will attempt to delete the selected note in the DB.
     *
     * @param session
     *            {@link Session} for this request.  This is used to keep track of the delete not candidate
     * @return response for the Yes intent.
     */
	public SpeechletResponse confirmDelete(Session session) {
		
		Boolean isDeleted = false; 
		
		ObjectMapper mapper = new ObjectMapper();
        QuickNote deleteThisNote = mapper.convertValue(session.getAttribute("DeleteNoteCandidate"), QuickNote.class);
		
		if (deleteThisNote == null){
			return getTellSpeechletResponse("Error deleting note.", false);
		}
		
		isDeleted = quickNoteManager.confirmDelete(deleteThisNote);		
		
		String deletedNoteTitle = deleteThisNote.getNoteName();
		
		if (!isDeleted){
			return getTellSpeechletResponse("I'm having trouble deleting your note.", false);
		}
		
		session.setAttribute("DeleteNoteCandidate", null);
		
		return getTellSpeechletResponse("Sure. Your note titled:  " + deletedNoteTitle + " has been deleted.", true);
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