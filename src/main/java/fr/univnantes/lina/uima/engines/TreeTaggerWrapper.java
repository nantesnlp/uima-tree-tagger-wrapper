package fr.univnantes.lina.uima.engines;

import java.util.ArrayList;
import java.util.List;

import org.annolab.tt4j.TokenAdapter;
import org.annolab.tt4j.TokenHandler;
import org.annolab.tt4j.TreeTaggerException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.ExternalResource;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;

import fr.univnantes.lina.UIMAProfiler;
import fr.univnantes.lina.uima.models.TreeTaggerParameter;

public class TreeTaggerWrapper extends JCasAnnotator_ImplBase {

	// Parameters
	public static final String PARAM_TT_HOME_DIRECTORY = "TreeTaggerHomeDirectory";
	@ConfigurationParameter(name = PARAM_TT_HOME_DIRECTORY, mandatory=false)
	private String ttHomeDirectory;

	public static final String PARAM_TT_PARAMETER_FILE = "TreeTaggerParameterFile";
	@ConfigurationParameter(name = PARAM_TT_PARAMETER_FILE, mandatory=false)
	private String ttParameterFile;
	
	public static final String PARAM_ANNOTATION_TYPE = "AnnotationType";
	@ConfigurationParameter(name = PARAM_ANNOTATION_TYPE, mandatory=true)
	private String annotationType;
	
	public static final String PARAM_TAG_FEATURE = "TagFeature";
	@ConfigurationParameter(name = PARAM_TAG_FEATURE, mandatory=true)
	private String tagFeature;
	
	public static final String PARAM_LEMMA_FEATURE = "LemmaFeature";
	@ConfigurationParameter(name = PARAM_LEMMA_FEATURE, mandatory=true)
	private String lemmaFeature;
	
	public static final String PARAM_UPDATE_ANNOTATION_FEATURES = "UpdateAnnotationFeatures";
	@ConfigurationParameter(name = PARAM_UPDATE_ANNOTATION_FEATURES, mandatory=true)
	private boolean updateAnnotationFeatures;

	public static final String PARAM_TT_CMD_ARGUMENTS = "ParamTreeTaggerCmdArguments";
	@ConfigurationParameter(name = PARAM_TT_CMD_ARGUMENTS, mandatory = true)
	private String[] paramTreeTaggerCmdArguments;

	// Resources
	@ExternalResource(key = TreeTaggerParameter.KEY_TT_PARAMETER)
	private TreeTaggerParameter ttParameter;

	
	private String lemmaType;
	private String tagType;

	
	private Handler handler;
	private Adapter adapter;
	private org.annolab.tt4j.TreeTaggerWrapper<Annotation> wrapper;	
	
	private Type getAnnotationType(JCas cas) {
		return cas.getTypeSystem().getType(this.annotationType);
	}
	
	private Type getTagAnnotationType(JCas cas) {
		return cas.getTypeSystem().getType(this.tagType);
	}
	
	private Feature getTagFeature(JCas cas,Type type) {
		if (this.updateAnnotationFeatures) {
			return type.getFeatureByBaseName(this.tagFeature);
		} else {
			Type tagType = this.getTagAnnotationType(cas);
			return tagType.getFeatureByBaseName(this.tagFeature);
		}
	}
	
	private Type getLemmaAnnotationType(JCas cas) {
		return cas.getTypeSystem().getType(this.lemmaType);
	}
	
	private Feature getLemmaFeature(JCas cas,Type type) {
		if (this.updateAnnotationFeatures) {
			return type.getFeatureByBaseName(this.lemmaFeature);
		} else {
			Type lemmaType = this.getLemmaAnnotationType(cas);
			return lemmaType.getFeatureByBaseName(this.lemmaFeature);
		}
	}
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		try {
			// read parameters from context
			this.ttHomeDirectory = (String) context.getConfigParameterValue(PARAM_TT_HOME_DIRECTORY);
			this.updateAnnotationFeatures = (Boolean) context.getConfigParameterValue(PARAM_UPDATE_ANNOTATION_FEATURES);
			this.tagFeature = (String) context.getConfigParameterValue(PARAM_TAG_FEATURE);
			this.lemmaFeature = (String) context.getConfigParameterValue(PARAM_LEMMA_FEATURE);
			this.ttParameterFile = (String) context.getConfigParameterValue(PARAM_TT_PARAMETER_FILE);
			this.annotationType = (String) context.getConfigParameterValue(PARAM_ANNOTATION_TYPE);
			this.paramTreeTaggerCmdArguments = (String[]) context.getConfigParameterValue(PARAM_TT_CMD_ARGUMENTS);

			// read resources from context
			this.ttParameter = (TreeTaggerParameter) context.getResourceObject(TreeTaggerParameter.KEY_TT_PARAMETER);

			System.setProperty("treetagger.home", ttHomeDirectory);

			ttParameter.override(ttParameterFile);
			
			// init wrapper
			this.handler = new Handler();
			this.adapter = new Adapter();
			this.wrapper = new org.annolab.tt4j.TreeTaggerWrapper<Annotation>();
			this.wrapper.setArguments(paramTreeTaggerCmdArguments);
			this.handler.enableUpdate(updateAnnotationFeatures);
			this.wrapper.setHandler(this.handler);
			this.wrapper.setAdapter(this.adapter);
			this.wrapper.setModel(this.ttParameter.getModel());

			
			String[] path = lemmaFeature.split(":");
			if (path.length == 2) {
				this.lemmaType = path[0];
				this.lemmaFeature = path[1];
			} 
		} catch (ResourceAccessException e) {
			throw new ResourceInitializationException(e);
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}
	}
	
	@Override
	public void process(JCas cas) throws AnalysisEngineProcessException {
		UIMAProfiler.getProfiler("AnalysisEngine").start(this, "process");
		try {
			this.wrapper.setModel(this.ttParameter.getModel());
			Type type = this.getAnnotationType(cas);
			Feature tagFeature = this.getTagFeature(cas,type);
			Feature lemmaFeature = this.getLemmaFeature(cas,type);
			this.handler.setTagFeature(tagFeature);
			this.handler.setLemmaFeature(lemmaFeature);
			AnnotationIndex<Annotation> index = cas.getAnnotationIndex(type);
			FSIterator<Annotation> iter = index.iterator();
			List<Annotation> tokens = new ArrayList<Annotation>();
			while (iter.hasNext()) {
				Annotation token = iter.next();
				tokens.add(token);
			}
			this.wrapper.process(tokens);
		} catch (TreeTaggerException e) {
			Throwable c = e.getCause();
			if (c == null) {
				this.getContext().getLogger().log(Level.WARNING,e.getMessage());
			} else {
				this.getContext().getLogger().log(Level.WARNING,c.getMessage());				
			}
		} catch (Exception e) {
			throw new AnalysisEngineProcessException(e);
		}
		UIMAProfiler.getProfiler("AnalysisEngine").stop(this, "process");
	}
	
	private class Handler implements TokenHandler<Annotation> {	
	
		private Feature tagFeature;
		
		public void setTagFeature(Feature feature) {
			this.tagFeature = feature;
		}
		
		private Feature lemmaFeature;
		
		public void setLemmaFeature(Feature feature) {
			this.lemmaFeature = feature;
		}
		
		private boolean update;
		
		public void enableUpdate(boolean enabled) {
			this.update = enabled;
		}
		
		public void token(Annotation annotation, String tag, String lemma) {
			CAS cas = annotation.getCAS();
			int begin = annotation.getBegin();
			int end = annotation.getEnd();
			String picked = null;
			if (lemma == null) {
				picked = annotation.getCoveredText(); // "unknown"
			} else {
				String lemmata[] = lemma.split("\\|");
				if (lemmata.length == 0) {
					picked = lemma;
				} else {
					picked = lemmata[lemmata.length - 1];
				}						
			}
			if (picked.endsWith("?")) {
				picked = picked.substring(0, picked.length() - 1);
			}
			assert (picked != null);
			if (this.update) {
				this.update(cas, annotation, this.tagFeature, tag);
				this.update(cas, annotation, this.lemmaFeature, picked);
			} else {
				this.annotate(cas, this.tagFeature, begin, end, tag);
				this.annotate(cas, this.lemmaFeature, begin, end, picked);
			}
		}

		private void update(CAS cas, Annotation annotation, Feature feature, String value) {
			annotation.setStringValue(feature,value);
		}
		
		private void annotate(CAS cas, Feature feature, int begin, int end, String value) {
			Type type = feature.getDomain();
			AnnotationFS annotation = cas.createAnnotation(type, begin, end);
			annotation.setStringValue(feature,value);
			cas.addFsToIndexes(annotation);
		}

	}
	
	private class Adapter implements TokenAdapter<Annotation> {

		@Override
		public String getText(Annotation annotation) {
			synchronized (annotation.getCAS()) {
				return annotation.getCoveredText().toLowerCase();		
			}
		}
		
	}
	
}
