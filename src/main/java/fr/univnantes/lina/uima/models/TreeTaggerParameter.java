package fr.univnantes.lina.uima.models;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.uima.UIMAFramework;
import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;
import org.apache.uima.util.Level;

public class TreeTaggerParameter implements SharedResourceObject {

	public static final String KEY_TT_PARAMETER = "TreeTaggerParameter";
	
	private String file;
	private String encoding;
	
	
	public String getModel() throws IOException {
		return this.file + ":" + this.encoding;
	}
	
	private void doLoad(InputStream inputStream) throws IOException {
		Properties properties = new Properties();
		properties.loadFromXML(inputStream);
		this.file = properties.getProperty("file");
		this.encoding = properties.getProperty("encoding");
	}
	
	@Override
	public void load(DataResource data) throws ResourceInitializationException {
		try {
			this.doLoad(data.getInputStream());
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}
	}

	public void override(String parameter) throws IOException {
		if (parameter != null) {
			InputStream inputStream = new FileInputStream(parameter);
			UIMAFramework.getLogger().log(Level.INFO, "Loading " + parameter);
			this.doLoad(inputStream);			
		}
	}
			
}
