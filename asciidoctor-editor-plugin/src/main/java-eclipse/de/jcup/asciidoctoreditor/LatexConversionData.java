package de.jcup.asciidoctoreditor;

public class LatexConversionData {
	private String imageDirectory;
	private String processedFilePath;
	
	public LatexConversionData() {
	}
	
	public LatexConversionData(String imageDir, String processedFilePath) {
		this.imageDirectory = imageDir;
		this.processedFilePath = processedFilePath;
	}
	
	public void SetImageDir(String imageDir) {
		this.imageDirectory = imageDir;
	}

	public void SetFile(String processedFilePath) {
		this.processedFilePath = processedFilePath;
	}
	
	public String GetImageDir() {
		return this.imageDirectory;
	}

	public String GetFile() {
		return this.processedFilePath;
	}
}
