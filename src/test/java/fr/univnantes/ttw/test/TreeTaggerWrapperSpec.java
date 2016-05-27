package fr.univnantes.ttw.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.InvalidXMLException;
import org.assertj.core.api.iterable.Extractor;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;

import fr.univnantes.lina.uima.engines.TreeTaggerWrapper;
import fr.univnantes.lina.uima.models.TreeTaggerParameter;

public class TreeTaggerWrapperSpec {
	private static final String WORD_ANNOTATION_TYPE = "fr.univnantes.ttw.types.WordAnnotation";
	public static final String PROPERTY_FILE_NAME = "tree-tagger.properties";
	public static final String P_TREE_TAGGER_HOME = "tt.home";
	public static final String TREE_TAGGER_CONFIG_FILE_URL = "file:english-treetagger.xml";

	public static final String TEXT_FILE1 = "/fr/univnantes/ttw/test/fixtures/text1.txt";
	public static final String TEXT_FILE2 = "/fr/univnantes/ttw/test/fixtures/text2.txt";
	public static final String TEXT_FILE3 = "/fr/univnantes/ttw/test/fixtures/text3.txt";
	public static final String TEXT_FILE4 = "/fr/univnantes/ttw/test/fixtures/text4.txt";

	AnalysisEngineDescription ttwAESWWithSGML;
	AnalysisEngineDescription ttwAESWNoSGML;

	JCas cas1;
	JCas cas2;
	JCas cas3;
	JCas cas4;

	@Before
	public void setUp() throws Exception {
		Properties p = new Properties();
		InputStream is = this.getClass().getResourceAsStream("/" + PROPERTY_FILE_NAME);
		if (is == null)
			fail(String.format("Property file %s not found.", PROPERTY_FILE_NAME));
		p.load(is);
		String ttHome = p.getProperty(P_TREE_TAGGER_HOME);
		if (ttHome == null)
			fail(String.format("Property %s not found in file %s.", P_TREE_TAGGER_HOME, PROPERTY_FILE_NAME));

		ttwAESWWithSGML = initAE(ttHome, "-quiet -no-unknown -sgml -token -lemma");
		ttwAESWNoSGML = initAE(ttHome, "-quiet -no-unknown -token -lemma");

		cas1 = JCasFactory.createJCas();
		fillCas(cas1, TEXT_FILE1);
		cas2 = JCasFactory.createJCas();
		fillCas(cas2, TEXT_FILE2);
		cas3 = JCasFactory.createJCas();
		fillCas(cas3, TEXT_FILE3);
		cas4 = JCasFactory.createJCas();
		fillCas(cas4, TEXT_FILE4);
	}

	private AnalysisEngineDescription initAE(String ttHome, String argumenAsString) throws ResourceInitializationException, InvalidXMLException {
		AnalysisEngineDescription ae = AnalysisEngineFactory.createEngineDescription(TreeTaggerWrapper.class,
				TreeTaggerWrapper.PARAM_ANNOTATION_TYPE, WORD_ANNOTATION_TYPE,
				TreeTaggerWrapper.PARAM_TT_ARGUMENTS, argumenAsString,
				TreeTaggerWrapper.PARAM_TAG_FEATURE, "tag", TreeTaggerWrapper.PARAM_LEMMA_FEATURE, "lemma",
				TreeTaggerWrapper.PARAM_UPDATE_ANNOTATION_FEATURES, true, TreeTaggerWrapper.PARAM_TT_HOME_DIRECTORY,
				ttHome);

		ExternalResourceFactory.createDependencyAndBind(ae, TreeTaggerParameter.KEY_TT_PARAMETER,
				TreeTaggerParameter.class, TREE_TAGGER_CONFIG_FILE_URL);
		return ae;
	}

	private void fillCas(JCas cas, String textFile) throws IOException, URISyntaxException {
		String theText = readFile(textFile, Charset.forName("UTF-8"));
		cas.setDocumentText(theText);
		StringTokenizer st = new StringTokenizer(theText, " ");
		int offset = 0;
		Type wordAnnoType = cas.getTypeSystem().getType(WORD_ANNOTATION_TYPE);
		while(st.hasMoreTokens()) {
			String token = st.nextToken();
			Annotation a = (Annotation) cas.getCas().createAnnotation(
					wordAnnoType, 
					offset, 
					offset + token.length());
			a.addToIndexes();
			offset+=token.length() + 1;
		}
	}

	private static String readFile(String path, Charset encoding) throws IOException, URISyntaxException {
		InputStream is = TreeTaggerWrapper.class.getResourceAsStream(path);
	    java.util.Scanner scanner = new java.util.Scanner(is);
		java.util.Scanner s = scanner.useDelimiter("\\A");
	    String string = s.hasNext() ? s.next() : "";
	    scanner.close();
		return string;
	}

	private List<Annotation> wordAnnotations(JCas cas) {
		List<Annotation> list = new ArrayList<Annotation>();
		FSIterator<Annotation> it = cas.getAnnotationIndex().iterator();
		while (it.hasNext()) {
			Annotation annotation = (Annotation) it.next();
			if(annotation.getType().getName().equals(WORD_ANNOTATION_TYPE))
				list.add(annotation);
		}
		return list;
	}

	Extractor<Annotation, Tuple> WORD_ANNOTATION_TUPLE_EXTRACTOR = new Extractor<Annotation, Tuple>() {
		@Override
		public Tuple extract(Annotation input) {
			return new Tuple(
					input.getBegin(), 
					input.getEnd(), 
					input.getStringValue(input.getType().getFeatureByBaseName("lemma")),
					input.getStringValue(input.getType().getFeatureByBaseName("tag")));
		}
	};
	
	
	@Test
	public void testCas1WithSgml() throws Exception {
		SimplePipeline.runPipeline(cas1, ttwAESWWithSGML);
		assertThat(wordAnnotations(cas1)).extracting(WORD_ANNOTATION_TUPLE_EXTRACTOR).containsExactly(
				tuple(0,4,"this", "DT"),
				tuple(5,7,"be", "VBZ"),
				tuple(8,9,"a", "DT"),
				tuple(10,14,"text", "NN"),
				tuple(15,22,"without", "IN"),
				tuple(23,26,"any", "DT"),
				tuple(27,34,"special", "JJ"),
				tuple(35,44,"character", "NN"),
				tuple(45,46,".", "SENT")
		);
	}
	
	/*
	 * Test if an opening chevron "<" causes tt4j to interprete it
	 * as an opnening sgml tag or not.
	 * 
	 */
	@Test
	public void testCas2WithSgml() throws Exception {
		SimplePipeline.runPipeline(cas2, ttwAESWWithSGML);
		assertThat(wordAnnotations(cas2)).extracting(WORD_ANNOTATION_TUPLE_EXTRACTOR).containsExactly(
				tuple(0,4,"this", "DT"),
				tuple(5,7,"be", "VBZ"),
				tuple(8,9,"a", "DT"),
				tuple(10,14,"text", "NN"),
				tuple(15,19,"with", "IN"),
				tuple(20,21,"a", "DT"),
				tuple(22,29,"formula", "NN"),
				tuple(30,31,":", ":"),
				tuple(32,33,"a", "DT"),
				tuple(34,35,"<", "SYM"),
				tuple(36,37,"b", "NN"),
				tuple(38,39,",", ","),
				tuple(40,43,"and", "CC"),
				tuple(44,48,"some", "DT"),
				tuple(49,53,"more", "JJR"),
				tuple(54,65,"description", "NN"),
				tuple(66,71,"after", "IN"),
				tuple(72,75,"the", "DT"),
				tuple(76,83,"formula", "NN"),
				tuple(84,85,".", "SENT")
		);
	}
	
	@Test
	public void testCas3WithSgml() throws Exception {
		SimplePipeline.runPipeline(cas3, ttwAESWWithSGML);
		assertThat(wordAnnotations(cas3)).extracting(WORD_ANNOTATION_TUPLE_EXTRACTOR).containsExactly(
				tuple(0,4,"this", "DT"),
				tuple(5,7,"be", "VBZ"),
				tuple(8,9,"a", "DT"),
				tuple(10,14,"text", "NN"),
				tuple(15,19,"with", "IN"),
				tuple(20,21,"a", "DT"),
				tuple(22,29,"formula", "NN"),
				tuple(30,31,":", ":"),
				tuple(32,33,"a", "DT"),
				tuple(34,35,"<", "SYM"),
				tuple(36,37,"b", "NN"),
				tuple(38,39,",", ","),
				tuple(40,47,"another", "DT"),
				tuple(48,56,"equation", "NN"),
				tuple(57,58,",", ","),
				tuple(59,60,"b", "LS"),
				tuple(61,62,">", "SYM"),
				tuple(63,64,"a", "DT"),
				tuple(65,66,",", ","),
				tuple(67,70,"and", "CC"),
				tuple(71,75,"some", "DT"),
				tuple(76,80,"more", "JJR"),
				tuple(81,92,"description", "NN"),
				tuple(93,98,"after", "IN"),
				tuple(99,102,"the", "DT"),
				tuple(103,110,"formula", "NN"),
				tuple(111,112,".", "SENT")
		);
	}


	@Test
	public void testCas4WithSgml() throws Exception {
		SimplePipeline.runPipeline(cas4, ttwAESWWithSGML);
		assertThat(wordAnnotations(cas4)).extracting(WORD_ANNOTATION_TUPLE_EXTRACTOR).containsExactly(
				tuple(0,4,"this", "DT"),
				tuple(5,7,"be", "VBZ"),
				tuple(8,9,"a", "DT"),
				tuple(10,14,"text", "NN"),
				tuple(15,19,"with", "IN"),
				tuple(20,21,"a", "DT"),
				tuple(22,29,"formula", "NN"),
				tuple(30,31,":", ":"),
				tuple(32,34,"aa", "NN"),
				tuple(35,37,"<b", "NN"),
				tuple(38,39,",", ","),
				tuple(40,47,"another", "DT"),
				tuple(48,56,"equation", "NN"),
				tuple(57,58,",", ","),
				tuple(59,60,"b", "LS"),
				tuple(61,62,">", "SYM"),
				tuple(63,64,"a", "DT"),
				tuple(65,66,",", ","),
				tuple(67,70,"and", "CC"),
				tuple(71,75,"some", "DT"),
				tuple(76,80,"more", "JJR"),
				tuple(81,92,"description", "NN"),
				tuple(93,98,"after", "IN"),
				tuple(99,102,"the", "DT"),
				tuple(103,110,"formula", "NN"),
				tuple(111,112,".", "SENT")
		);
	}


	@Test
	public void testCas4NoSgml() throws Exception {
		SimplePipeline.runPipeline(cas4, ttwAESWNoSGML);
		assertThat(wordAnnotations(cas4)).extracting(WORD_ANNOTATION_TUPLE_EXTRACTOR).containsExactly(
				tuple(0,4,"this", "DT"),
				tuple(5,7,"be", "VBZ"),
				tuple(8,9,"a", "DT"),
				tuple(10,14,"text", "NN"),
				tuple(15,19,"with", "IN"),
				tuple(20,21,"a", "DT"),
				tuple(22,29,"formula", "NN"),
				tuple(30,31,":", ":"),
				tuple(32,34,"aa", "NN"),
				tuple(35,37,"<b", "NN"),
				tuple(38,39,",", ","),
				tuple(40,47,"another", "DT"),
				tuple(48,56,"equation", "NN"),
				tuple(57,58,",", ","),
				tuple(59,60,"b", "LS"),
				tuple(61,62,">", "SYM"),
				tuple(63,64,"a", "DT"),
				tuple(65,66,",", ","),
				tuple(67,70,"and", "CC"),
				tuple(71,75,"some", "DT"),
				tuple(76,80,"more", "JJR"),
				tuple(81,92,"description", "NN"),
				tuple(93,98,"after", "IN"),
				tuple(99,102,"the", "DT"),
				tuple(103,110,"formula", "NN"),
				tuple(111,112,".", "SENT")
		);
	}

	
	
	@Test
	public void testCas3NoSgml() throws Exception {
		SimplePipeline.runPipeline(cas3, ttwAESWNoSGML);
		assertThat(wordAnnotations(cas3)).extracting(WORD_ANNOTATION_TUPLE_EXTRACTOR).containsExactly(
				tuple(0,4,"this", "DT"),
				tuple(5,7,"be", "VBZ"),
				tuple(8,9,"a", "DT"),
				tuple(10,14,"text", "NN"),
				tuple(15,19,"with", "IN"),
				tuple(20,21,"a", "DT"),
				tuple(22,29,"formula", "NN"),
				tuple(30,31,":", ":"),
				tuple(32,33,"a", "DT"),
				tuple(34,35,"<", "SYM"),
				tuple(36,37,"b", "NN"),
				tuple(38,39,",", ","),
				tuple(40,47,"another", "DT"),
				tuple(48,56,"equation", "NN"),
				tuple(57,58,",", ","),
				tuple(59,60,"b", "LS"),
				tuple(61,62,">", "SYM"),
				tuple(63,64,"a", "DT"),
				tuple(65,66,",", ","),
				tuple(67,70,"and", "CC"),
				tuple(71,75,"some", "DT"),
				tuple(76,80,"more", "JJR"),
				tuple(81,92,"description", "NN"),
				tuple(93,98,"after", "IN"),
				tuple(99,102,"the", "DT"),
				tuple(103,110,"formula", "NN"),
				tuple(111,112,".", "SENT")
		);
	}
}
