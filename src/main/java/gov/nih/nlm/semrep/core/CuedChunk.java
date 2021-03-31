package gov.nih.nlm.semrep.core;

public class CuedChunk {
	
	private Chunk chunk;
	private Chunk cue;
	private CueType type;
	
	public CuedChunk(Chunk chunk, Chunk cue) {
		this.chunk = chunk;
		this.cue = cue;
		this.type = null;
	}

	public CuedChunk(Chunk chunk, Chunk cue, CueType type) {
		this(chunk,cue);
		this.type = type;
	}

	public Chunk getChunk() {
		return chunk;
	}

	public void setChunk(Chunk chunk) {
		this.chunk = chunk;
	}

	public Chunk getCue() {
		return cue;
	}

	public void setCue(Chunk cue) {
		this.cue = cue;
	}
	
	public CueType getType() {
		return type;
	}

	public void setType(CueType type) {
		this.type = type;
	}

	public String toString() {
		return cue.getHead().getText() + "_" + chunk.toString();
	}

}
