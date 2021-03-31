package gov.nih.nlm.semrep.core;

import gov.nih.nlm.ling.core.SurfaceElement;

public class ChunkSE {
	
	private SurfaceElement surfaceElement;
	private char chunkRole;
	
	public ChunkSE(SurfaceElement surfaceElement, char chunkRole) {
		this.surfaceElement = surfaceElement;
		this.chunkRole = chunkRole;
	}

	public SurfaceElement getSurfaceElement() {
		return surfaceElement;
	}
	
	public void setSurfaceElement(SurfaceElement surfaceElement) {
		this.surfaceElement = surfaceElement;
	}
	
	public char getChunkRole() {
		return chunkRole;
	}
	
	public void setChunkRole(char chunkRole) {
		this.chunkRole = chunkRole;
	}
	
	public String toString() {
		return surfaceElement.toString() + "_" + chunkRole;
	}

}
