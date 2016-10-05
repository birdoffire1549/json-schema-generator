package com.firebirdcss.tools.json_schema_generator;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.activation.DataHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;

/**
 * This class is the primary entry point of the application.
 * <p>
 * The point of this application is to allow for the quick generation of the JSON Documentation of any {@link Class},
 * by simply pointing this tool at the class file.
 *  
 * @author Scott Griffis
 *
 */
public class ApplicationMain {
	private static final List<String> malformedUrlMessages = new ArrayList<>();

	/**
	 * Application's main point of entry.
	 * 
	 * @param args - The application's arguments, in this case a list of classes for which to generate JSON Documents.
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			displayUsage();
		} else {
			URL[] fileUrls = argsToUrls(args);
	
			CustomClassLoader loader = new CustomClassLoader(new URLClassLoader(fileUrls));
			StringBuilder msg = new StringBuilder();
			msg.append("\n\n");
			
			for (URL url : fileUrls) {
				try {
					Class<?> theClass = loader.loadClass(url);
					msg.append("Output for class: '").append(theClass.getName()).append("':\n");
					msg.append(generateJsonSchema(theClass)).append('\n');
				} catch (Exception e) {
					msg.append(e.getMessage()).append('\n');
				}
				msg.append("\n\n");
			}
			
			if (malformedUrlMessages.size() > 0) {
				
				msg.append("The following arguments were not able to be translated to URLs:\n");
				for (String message : malformedUrlMessages) {
					msg.append("\t" + message).append('\n');
				}
			}
			
			putTextOnClipboard(msg.toString());
			msg.append("\nNote: This output is also available on the clipboard.");
			
			System.out.println(msg.toString());
		}
	}
	
	/**
	 * Displays the program's usage syntax.
	 */
	private static void displayUsage() {
		System.out.println("usage: json-schema-generator <file.class>...\n");
	}
	
	/**
	 * Puts the passed text onto the system clipboard.
	 * 
	 * @param text - The text to place on the clipboard as {@link String}
	 */
	private static void putTextOnClipboard(String text) {
		Transferable trans = new DataHandler(text, DataFlavor.getTextPlainUnicodeFlavor().getMimeType());
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(trans, null);
	}
	
	/**
	 * Translates file path arguments to {@link URL}s.
	 * 
	 * @param args - The file-path arguments as {@link String} array
	 * @return Returns an array of {@link URL}s
	 */
	private static URL[] argsToUrls(String[] args) {
		URL[] fileUrls = new URL[args.length];
		for (int i = 0; i < args.length; i++) {
			File file = new File(args[i]);
			try {
				fileUrls[i] = file.toURI().toURL();
			} catch (MalformedURLException e) {
				malformedUrlMessages.add(e.getMessage());
			}
		}

		return fileUrls;
	}

	/**
	 * Extracts the class name from a class file.
	 * 
	 * @param classFile - The class file as {@link File}
	 * @return Returns the class name as a {@link String}
	 * @throws IOException
	 */
	private static String getClassNameFromFile(File classFile) throws IOException {
		String friendlyFileName = classFile.getName().contains(".") ? classFile.getName().substring(0, classFile.getName().indexOf('.')) : classFile.getName();
		String result = null;

		try (BufferedReader reader = new BufferedReader(new FileReader(classFile));) {
			String line = null;
			while((line = reader.readLine()) != null) {
				if (line.contains("com") && line.contains(friendlyFileName)) {
					result = line.substring(line.indexOf("com"), line.indexOf(friendlyFileName));
					result = result.replaceAll("/", ".");
					result += friendlyFileName;
					break;
				}
			}
		}

		return result;
	}

	/**
	 * Generates the JSON Schema for a given {@link Class}.
	 * 
	 * @param clazz - The class as {@link Class}
	 * @return Returns the generated JSON Schema as {@link String}
	 * @throws IOException
	 */
	private static String generateJsonSchema(Class<?> clazz) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
		
		JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);
		JsonSchema schema = schemaGen.generateSchema(clazz);

		return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
	}

	/* =============================[Private classes below]============================= */

	/**
	 * This class makes up a Custom {@link ClassLoader} capable of reading in the binary 
	 * data of a class file and loading it as a {@link Class}.
	 * 
	 * @author Scott Griffis
	 *
	 */
	private static class CustomClassLoader extends ClassLoader {
		/**
		 * CONSTRUCTOR: Constructor passes given {@link ClassLoader} parent to super-class.
		 * 
		 * @param parent - The parent {@link ClassLoader}
		 */
		public CustomClassLoader(ClassLoader parent) {
			super(parent);
		}

		/**
		 * Loads a {@link Class} from the binary class file.
		 *  
		 * @param classFileUrl - The URL of the class-file as {@link URL}
		 * @return Returns the loaded {@link Class}
		 * @throws IOException  
		 */
		public Class<?> loadClass(URL classFileUrl) throws IOException {
			try {
				File classFile = new File(classFileUrl.toURI());
				URLConnection connection = classFileUrl.openConnection();
				byte[] classData = new byte[0];
				try (InputStream input = connection.getInputStream();) {
					ByteArrayOutputStream buffer = new ByteArrayOutputStream();
					int data;
					while ((data = input.read()) != -1) {
						buffer.write(data);
					}
					classData = buffer.toByteArray();
				}
				
				return defineClass(getClassNameFromFile(classFile), classData, 0, classData.length);
			} catch (Exception e) {
				throw new IOException("Unable to load class using the provided URL: '" + classFileUrl.getPath() + "'");
			}
		}
	}
}
