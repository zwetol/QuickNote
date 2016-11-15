package quicknote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quicknote.storage.QuickNoteUserDataItem;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link QuickNoteSpeechlet} receives various intents and requests the appropriate response from the QuickNoteManager.
 */
public class QuickNoteSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(QuickNoteSpeechlet.class);

    private AmazonDynamoDBClient amazonDynamoDBClient;

    private QuickNoteManager quickNoteManager;

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
        
        return quickNoteManager.getLaunchResponse(request, session);
    }

    @Override
    public SpeechletResponse onIntent(IntentRequest request, Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());
        initializeComponents();
        
        ObjectMapper mapper = new ObjectMapper();
        QuickNoteUserDataItem myNote = mapper.convertValue(session.getAttribute("NewNote"), QuickNoteUserDataItem.class);
        
        QuickNoteUserDataItem deleteNoteCandidate = mapper.convertValue(session.getAttribute("DeleteNoteCandidate"), QuickNoteUserDataItem.class);
                
        Intent intent = request.getIntent();
        
        if ("CreateNewNoteIntent".equals(intent.getName())) {
        	return quickNoteManager.putCreateNewNoteIntentResponse(session);
        } 
        else if("GetNoteByTitleIntent".equals(intent.getName())){
        	return quickNoteManager.getGetNoteByTitleIntentResponse(intent, session);
        }   
    	else if ("DeleteNoteByTitleIntent".equals(intent.getName())){
    		return quickNoteManager.putDeleteNoteIntentResponse(session, intent);
    	}
    	else if ("AMAZON.NoIntent".equals(intent.getName()) && deleteNoteCandidate != null){
    		return quickNoteManager.postNoIntentResponse(session);
    	}
        else if ("AMAZON.YesIntent".equals(intent.getName()) && deleteNoteCandidate != null){  
    		return quickNoteManager.postYesIntentResponse(session);
    	} 
        
        /**
         * the SetFreeFormDataIntent applies to two different user interactions: setting the title
        * AND setting the body of the note.  We want to determine how to execute on this intent 
        * based on what point the user is within the interaction.
        **/ 
        else if("SetFreeFormDataIntent".equals(intent.getName()) && myNote != null){
        	
        	if (myNote.getNoteName() == null && myNote.getNoteBody() == null){
            	return quickNoteManager.putSetNoteTitleIntentResponse(intent, session);
        	}
        	else if (myNote.getNoteName() != null && myNote.getNoteBody() == null) {
                return quickNoteManager.postSetNoteBodyIntentResponse(intent, session);  
            } 
        	else {
            	log.error("Unrecognized intent: " + intent.getName());
                throw new IllegalArgumentException("Unrecognized SetFreeFormDataIntent action " + intent.getName());
        	}
        } 
        else if("AMAZON.HelpIntent".equals(intent.getName())){
        	return quickNoteManager.getHelpIntentResponse(intent, session);
        }
        else {
        	log.error("Unrecognized intent: " + intent.getName());
            return quickNoteManager.getHelpIntentResponse(intent, session);
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
}