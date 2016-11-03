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

/**
 * The {@link QuickNoteSpeechlet} receives various intents and requests the appropriate response from the QuickNoteManager.
 */
public class QuickNoteSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(QuickNoteSpeechlet.class);

    private AmazonDynamoDBClient amazonDynamoDBClient;

    private QuickNoteManager quickNoteManager;
    
    private QuickNoteUserDataItem myNote = null;
    
    private QuickNoteUserDataItem deleteNoteCandidate = null;

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
        
        if(deleteNoteCandidate != null){
            System.out.println("checking delete note status: " + deleteNoteCandidate.getCustomerId());
        }
        else {
        	System.out.println("poop");
        }

        Intent intent = request.getIntent();
        
        //check if current context means we should get title or body
        if ("CreateNewNoteIntent".equals(intent.getName())) {
        	myNote = new QuickNoteUserDataItem();
        	return quickNoteManager.getCreateNewNoteIntentResponse(session);

        } 
        else if("GetNoteByTitleIntent".equals(intent.getName())){
        	return quickNoteManager.getGetNoteByTitleIntentResponse(intent, session);
        }
        
    	else if ("DeleteNoteByTitleIntent".equals(intent.getName())){
    		deleteNoteCandidate = new QuickNoteUserDataItem();
    		return quickNoteManager.getDeleteNoteIntentResponse(session, intent, this.deleteNoteCandidate);
    	}
    	else if ("AMAZON.NoIntent".equals(intent.getName()) && this.deleteNoteCandidate != null){
    		deleteNoteCandidate = null;
    		return quickNoteManager.getNoIntentResponse(session);
    	}
        else if ("AMAZON.YesIntent".equals(intent.getName()) && this.deleteNoteCandidate != null){
        	//QuickNoteUserDataItem deleteThisNote = deleteNoteCandidate;
  
    		return quickNoteManager.getYesIntentResponse(session, this.deleteNoteCandidate);
    	} 
        
        /**the SetFreeFormDataIntent applies to two different user interactions: setting the title
        * AND setting the body of the note.  We want to determine how to execute on this intent 
        * based on what point the user is within the interaction.
        **/ 
        else if("SetFreeFormDataIntent".equals(intent.getName()) && this.myNote != null){
        	System.out.println(this.myNote.getNoteName() + " " + this.myNote.getNoteBody());
        	
        	if (this.myNote.getNoteName() == null && this.myNote.getNoteBody() == null){
            	return quickNoteManager.getSetNoteTitleIntentResponse(intent, session, this.myNote);
        	}
        	else if (this.myNote.getNoteName() != null && this.myNote.getNoteBody() == null) {
                return quickNoteManager.getSetNoteBodyIntentResponse(intent, session, this.myNote);  
            } 
        	else {
                throw new IllegalArgumentException("Unrecognized intent: " + intent.getName());
        	}
        } 
        else {
            throw new IllegalArgumentException("Unrecognized intent: " + intent.getName());
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
