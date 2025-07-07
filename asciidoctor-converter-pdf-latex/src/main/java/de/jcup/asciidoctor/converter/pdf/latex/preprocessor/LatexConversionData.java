package de.jcup.asciidoctor.converter.pdf.latex.preprocessor;

public class LatexConversionData {
	private String imageDirectory;
	private String processedFilePath;

	public LatexConversionData() {
	}

	public LatexConversionData(String imagesDir, String processedFilePath) {
		this.imageDirectory = imagesDir;
		this.processedFilePath = processedFilePath;
	}

	public String getImagesDir() {
		return this.imageDirectory;
	}

	public String getFile() {
		return this.processedFilePath;
	}
}
