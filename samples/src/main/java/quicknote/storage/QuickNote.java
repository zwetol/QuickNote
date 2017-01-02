package quicknote.storage;

public class QuickNote implements Note{
    
    private String customerId;

    private String noteName;
    
    private String noteBody;
    
    private Boolean hasError = false;
    
    private Boolean doesNotExistError = false;

    
    public String getCustomerId() {
        return customerId;
    }    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }    
    
    public String getNoteName() {
    	return noteName;
    }    
    public void setNoteName(String noteName) {
    	this.noteName = noteName;
    }
    
    public String getNoteBody() {
    	return noteBody;
    }
    public void setNoteBody(String noteBody) {
    	this.noteBody = noteBody;
    }
    
    public void setHasError() {
    	this.hasError = true;
    }
    public Boolean getHasError() {
    	return hasError;
    }
    
	public void setDoesNotExistError() {
		this.doesNotExistError = true;
	}    
	public Boolean getDoesNotExistError(){
		return doesNotExistError;
	}
}
