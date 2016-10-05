package com.firebirdcss.tools.json_mapper;

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
		URL[] fileUrls = argsToUrls(args);

		CustomClassLoader loader = new CustomClassLoader(new URLClassLoader(fileUrls));

		for (URL url : fileUrls) {
			try {
				Class<?> theClass = loader.loadClass(url);
				System.out.println(generateJsonSchema(theClass));
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
			System.out.println("\n\n");
		}
		
		if (malformedUrlMessages.size() > 0) {
			System.out.println("The following arguments were not able to be translated to URLs:");
			for (String message : malformedUrlMessages) {
				System.out.println("\t" + message);
			}
		}
	}

	/**
	 * 
	 * @param args
	 * @return
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
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 */
	private static String getClassNameFromFile(File file) throws IOException {
		String friendlyFileName = file.getName().contains(".") ? file.getName().substring(0, file.getName().indexOf('.')) : file.getName();
		String result = null;

		try (BufferedReader reader = new BufferedReader(new FileReader(file));) {
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
	 * 
	 * @param clazz
	 * @return
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
		 * 
		 * @param classFileUrl
		 * @return
		 * @throws IOException 
		 * @throws ClassFormatError 
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
