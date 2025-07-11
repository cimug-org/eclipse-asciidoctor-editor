package de.jcup.asciidoctor.converter.pdf.latex.preprocessor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Node.js binary extractor utility.
 */
/**
 * Node.js binary extractor utility.
 */
public class NodeBinaryExtractor {

	private static final String PLUGIN_ID = "de.jcup.asciidoctor.converter.pdf.latex";
	public static final String EXECUTABLE_NAME = "node";
	public static final String SCRIPT_NAME = "render-latex.js";

	private static final String OS_NAME = "os.name";
	private static final String OS_ARCH = "os.arch";

	/**
	 * The below call to extract() is invoked each time on startup and performs two
	 * important tasks. If the node.exe does not exist in the "state location" of
	 * the plugin then the appropriate Node runtime (based on platform) will be
	 * executed. Alternatively, if a prior extraction has occurred then the
	 * extractor will perform an SHA256 checksum validation to ensure that the
	 * deployed Node.js runtime has not been tampered with since the last launch. If
	 * it has then the entire runtime is re-extracted back to the original verified
	 * state.
	 */
	public static void extract() throws Exception {
		getNodeExecutable();
	}

	/**
	 * Method will be used by the plugin to located the
	 * 
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws Exception
	 */
	public static File getNodeExecutable() {
		IPath statePath = Platform.getStateLocation(Platform.getBundle(PLUGIN_ID));
		String platform = getPlatform();

		IPath destDir = statePath.append("resources").append(platform);
		File extractedDir = destDir.toFile();

		String archiveName;
		// Though linux isn't currently supported it has been included here for future use.
		if (platform.startsWith("linux")) {
			archiveName = "NodeJS_" + platform + ".tar.gz";
		} else if (platform.startsWith("win") || platform.startsWith("mac")) {
			archiveName = "NodeJS_" + platform + ".zip";
		} else {
			throw new IllegalStateException("Unsupported platform: " + platform);
		}

		File destExe;
		String shaFile = "/resources/sha256/" + platform + "/"
				+ (platform.startsWith("win") ? EXECUTABLE_NAME + ".exe.sha256" : EXECUTABLE_NAME + ".sha256");
		if (platform.startsWith("mac")) {
			destExe = new File(new File(extractedDir, "bin"), EXECUTABLE_NAME);
		} else {
			destExe = new File(extractedDir, EXECUTABLE_NAME + ".exe");
		}

		if (!destExe.exists() || !validateSha256(destExe, shaFile)) {
			try {
				extractZipFromPlugin("/resources/" + archiveName, extractedDir);
			} catch (IOException | URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}
		
		File destScript = new File(destExe.getParentFile(), SCRIPT_NAME);
		String scriptSHAFile = "/resources/sha256/" + SCRIPT_NAME + ".sha256";
		
		if (!destScript.exists() || !validateSha256(destScript, scriptSHAFile)) {
			try {
				extractScriptFromPlugin("/resources/" + SCRIPT_NAME, destExe.getParentFile());
			} catch (IOException | URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		return destExe;
	}

	private static String getPlatform() {
		String os = System.getProperty(OS_NAME).toLowerCase();
		String arch = System.getProperty(OS_ARCH).toLowerCase();
		if (os.contains("win")) {
			return arch.contains("64") ? "win-x86_64" : "win-x86";
		} else if (os.contains("mac")) {
			return ("amd64".equals(arch) || "x86_64".equals(arch)) ? "mac-x86_64" : "mac-arm64";
		} else {
			throw new IllegalStateException("Unsupported platform: " + os);
		}
	}

	private static void extractZipFromPlugin(String pluginZipPath, File outputDir)
			throws IOException, URISyntaxException {
		Bundle bundle = FrameworkUtil.getBundle(NodeBinaryExtractor.class);
		URL zipUrl = FileLocator.toFileURL(bundle.getEntry(pluginZipPath));
		Path zipPath = Paths.get(zipUrl.toURI());

		try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, (ClassLoader) null)) {
			Path root = zipFs.getPath("/");
			Files.walk(root).forEach(src -> {
				try {
					Path dest = outputDir.toPath().resolve(root.relativize(src).toString());
					if (Files.isDirectory(src)) {
						Files.createDirectories(dest);
					} else {
						Files.createDirectories(dest.getParent());
						Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	private static void extractScriptFromPlugin(String pluginScriptPath, File outputDir) 
			throws IOException, URISyntaxException {
		Bundle bundle = FrameworkUtil.getBundle(NodeBinaryExtractor.class);
		URL resourceUrl = FileLocator.toFileURL(bundle.getEntry(pluginScriptPath));
		Path filePath = Paths.get(resourceUrl.toURI());
		
		try {
			Path dest = outputDir.toPath().resolve(SCRIPT_NAME);
			Files.copy(filePath, dest, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private static boolean validateSha256(File file, String sha256ResourcePath) {
		try {
			Bundle bundle = FrameworkUtil.getBundle(NodeBinaryExtractor.class);
			URL shaUrl = FileLocator.toFileURL(bundle.getEntry(sha256ResourcePath));
			Path shaPath = Paths.get(shaUrl.toURI());
			String expected = Files.readAllLines(shaPath).stream().filter(line -> !line.isBlank()).findFirst()
					.orElse("").split("\\s")[0];
			String actual = computeSha256(file);
			return actual.equalsIgnoreCase(expected);
		} catch (Exception e) {
			return false;
		}
	}

	private static String computeSha256(File file) throws IOException {
		try (InputStream in = Files.newInputStream(file.toPath())) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
			}

	        byte[] hashBytes = digest.digest();
	        StringBuilder hexString = new StringBuilder();
	        for (byte b : hashBytes) {
	            hexString.append(String.format("%02x", b));
	        }
	        
	        return hexString.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 not supported", e);
		}
	}
}
