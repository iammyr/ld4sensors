package eu.spitfire_project.ld4s.resource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.Language;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Options;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.restlet.security.Role;
import org.restlet.security.User;
import org.restlet.service.MetadataService;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.tdb.TDB;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.update.GraphStore;
import com.hp.hpl.jena.update.GraphStoreFactory;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.vocabulary.DC;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

import eu.spitfire_project.ld4s.lod_cloud.Context;
import eu.spitfire_project.ld4s.lod_cloud.Context.Domain;
import eu.spitfire_project.ld4s.lod_cloud.EncyclopedicApi;
import eu.spitfire_project.ld4s.lod_cloud.GenericApi;
import eu.spitfire_project.ld4s.lod_cloud.LocationApi;
import eu.spitfire_project.ld4s.lod_cloud.PeopleApi;
import eu.spitfire_project.ld4s.lod_cloud.Person;
import eu.spitfire_project.ld4s.lod_cloud.SearchRouter;
import eu.spitfire_project.ld4s.lod_cloud.UomApi;
import eu.spitfire_project.ld4s.lod_cloud.WeatherApi;
import eu.spitfire_project.ld4s.resource.link.Link;
import eu.spitfire_project.ld4s.resource.sparql.SparqlResultsFormatter;
import eu.spitfire_project.ld4s.server.Server;
import eu.spitfire_project.ld4s.server.ServerProperties;
import eu.spitfire_project.ld4s.vocabulary.CorelfVocab;
import eu.spitfire_project.ld4s.vocabulary.FoafVocab;
import eu.spitfire_project.ld4s.vocabulary.LD4SConstants;
import eu.spitfire_project.ld4s.vocabulary.ProvVocab;
import eu.spitfire_project.ld4s.vocabulary.RevVocab;
import eu.spitfire_project.ld4s.vocabulary.SiocVocab;
import eu.spitfire_project.ld4s.vocabulary.SptVocab;
import eu.spitfire_project.ld4s.vocabulary.SsnVocab;
import eu.spitfire_project.ld4s.vocabulary.VoIDVocab;

public abstract class LD4SDataResource extends ServerResource{
	protected static enum SparqlType {SELECT, CONSTRUCT, UPDATE, DESCRIBE, ASK};

	/** Current user. */
	protected User user;

	/** Its role(s). */
	protected List<Role> roles;

	/** This server (ld4s). */
	protected Server ld4sServer;

	/** Resource identification */
	protected String resourceId;

	/** To be retrieved from the URL as the 'timestamp' template parameter, or null. */
	protected String timestamp = null;

	/** Records the time at which each HTTP request was initiated. */
	protected long requestStartTime = new Date().getTime();

	/**  Preferred media type. */
	protected MediaType requestedMedia;

	/**  Content type. */
	protected MediaType contentType;

	//	/** Default URI for annotating unknown resources. */
	//	protected String defaultUri = null;

	/** Query string from the URI. */
	protected String query = null;

	/** Requested string. */
	protected String uristr = null;

	/** Vocabulary of Interlinked Data used to describe the Hackystat dataset. */
	public static final Model voIDModel = LD4SDataResource.initVoIDModel();

	/** Logger for messages. */
	protected Logger logger = null;

	/**Dataset to handle the data stored in the triple db. */
	protected Dataset dataset = null;

	/**Submitted Entity. */
	protected Representation entity = null;

	/** Contextual criteria for link creation. */
	protected Context context = null;

	/** Modality in which the user prefers to get a resource, i.e., linked with external data or not. */
	protected boolean linked = true;

	/** Name of the Named Graph where all the instances of each Service resource are stored. */
	protected String namedModel = null;

	private String generalNamedModel = null;

	protected static HashMap<String, String> resource2namedGraph = null;


	@Put
	public Representation put(String obj){
		if (resourceId == null || resourceId.trim().compareTo("") == 0){
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return null;
		}

		Representation ret = null;
		OntModel rdfData = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM_MICRO_RULE_INF);
		rdfData.read(obj);
		if (rdfData.isEmpty()){
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return null;
		}
		// create a new resource in the database
		if (store(rdfData, this.namedModel)){
			setStatus(Status.SUCCESS_CREATED);	 
			ret = serializeAccordingToReqMediaType(rdfData);
		}else{
			setStatus(Status.SERVER_ERROR_INTERNAL, "Unable to store in the Trple DB");
		}
		return ret;
	}



	@Post
	public Representation post(String obj){

		Representation ret = null;
		OntModel rdfData = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM_MICRO_RULE_INF);
//		InputStream stream;
//		try {
//			stream = new ByteArrayInputStream(obj.getBytes("UTF-8"));
//		} catch (UnsupportedEncodingException e) {
//			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
//			return null;
//		}
//		String lang = mediatypeToJenaLang(contentType);
//		if (lang == null){
//			setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
//			return null;
//		}
//		rdfData.read(stream, null, lang);

		if (rdfData.isEmpty()){
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return null;
		}

		// create a new resource in the database only if the preferred resource hosting server is
		// the LD4S one

		if (update(rdfData, this.namedModel)){
			setStatus(Status.SUCCESS_OK);	 
			ret = serializeAccordingToReqMediaType(rdfData);
		}else{
			setStatus(Status.SERVER_ERROR_INTERNAL, "Unable to update in the Trple DB");
		}

		return ret;
	}

	protected void initResource2NamedGraph(String baseHost){
		String base = baseHost+"graph/";
		resource2namedGraph = new HashMap<String, String>();
		resource2namedGraph.put("ov", base+"ov");
		/**datlink resources are the only resource among the 
		ld4s enriched main ones, that gets generated while
		annotated other resources. Then for logistics
		reasons, they need to go in the generic named graph
		rather than having their own named graph.**/
		//		resource2namedGraph.put("link", base+"link");
		resource2namedGraph.put("device", base+"device");
		resource2namedGraph.put("tpp", base+"tpp");
		resource2namedGraph.put("tps", base+"tps");
		resource2namedGraph.put("platform", base+"platform");
		resource2namedGraph.put("meas_capab", base+"meas_capab");
		resource2namedGraph.put("meas_prop", base+"meas_prop");
	}

	/**
	 * Necessary to overcome a security issue typical of requests sent to the web service
	 * from the jquery's ajax function.
	 * 
	 * @author huangyuan
	 * @param entity
	 * @throws ResourceException
	 */
	@Options
	public void doOptions(Representation entity) throws ResourceException {
		Form responseHeaders = (Form) getResponse().getAttributes().get(
				"org.restlet.http.headers");
		if (responseHeaders == null) {
			responseHeaders = new Form();
			getResponse().getAttributes().put("org.restlet.http.headers",
					responseHeaders);
		}
		responseHeaders.add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");//tell browser all the function can be used
		responseHeaders.add("Access-Control-Allow-Headers", "Content-Type");//tell browser should send content-Type
		responseHeaders.add("Access-Control-Allow-Credentials", "true");//true I have found other people all set true
		responseHeaders.add("Access-Control-Max-Age", "30");//when testing should be  shorter about 30 for server bigger is better
	}

	@Override
	protected void doInit() throws ResourceException {
		/**
		 * SECURITY ADD-on - start
		 */
		Form responseHeaders = (Form) getResponse().getAttributes().get("org.restlet.http.headers");   


		if ( responseHeaders == null ) { 

			responseHeaders = new Form(); 

			getResponse().getAttributes().put("org.restlet.http.headers", responseHeaders);   

		} 

		responseHeaders.add("Access-Control-Allow-Origin", "*"); 
		responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE,OPTIONS"); 
		/**
		 * SECURITY ADD-on - end
		 */

		/**
		 * PRINT full request details and payload
		 */
		System.out.println("\n********ORIGINAL REQUEST:*********\n"+getRequest().toString()+
				"\nHEADERS:"+getRequestAttributes());

		//		try {
		//			System.out.println("\n********PAYLOAD:*********\n"+getRequestEntity().getText());
		//		} catch (IOException e1) {
		//			// TODO Auto-generated catch block
		//			e1.printStackTrace();
		//		}


		this.user = getClientInfo().getUser();
		if (this.user == null){
			this.user = new User();
		}
		this.roles = getClientInfo().getRoles();
		this.requestedMedia = selectMedia(getClientInfo().getAcceptedMediaTypes());
		this.entity = getRequestEntity();
		this.contentType = this.entity.getMediaType();
		MetadataService ms = getMetadataService(); 
		ms.addCommonExtensions(); 
		ms.addExtension("ttl", MediaType.APPLICATION_RDF_TURTLE);
		ms.addExtension("rdf", MediaType.APPLICATION_RDF_XML);
		ms.addExtension("n3", MediaType.TEXT_RDF_N3);
		//		getVariants().add(new Variant(new MediaType(LD4SConstants.MEDIA_TYPE_SPARQL_RESULTS)));

		//		if (this.requestedMedia == null){
		//			setStatusError("The requested Media Type " + requestedMedia + " is not supported .");
		//		}
		this.ld4sServer = (Server) getContext().getAttributes().get("LD4Sensors");
		if (resource2namedGraph == null){
			initResource2NamedGraph(this.ld4sServer.getHostName());
			//			initResource2NamedGraph("http://192.168.56.1:8182/ld4s/");
		}
		this.resourceId = ((String) getRequest().getAttributes().get("resource_id"));
		this.timestamp = (String) getRequest().getAttributes().get("timestamp");
		this.uristr = this.getRequest().getResourceRef().toString();
		this.generalNamedModel = this.ld4sServer.getHostName()+"graph/general";
		this.namedModel = getNamedModel(this.uristr);
		if (this.namedModel == null){
			this.namedModel = generalNamedModel ;
		}

		this.query = ((String) getRequest().getAttributes().get("query"));

		if (this.query != null) {
			try {
				this.query = java.net.URLDecoder.decode(this.query, "UTF-8");
			}
			catch (UnsupportedEncodingException e) {
				setStatusError("Error processing the query. "
						+ "The URI has been encoded using an unsupported encoding scheme.", e);
				return;
			}
		}

		//handle the linking criteria (context) when appended at the URI in a GET request.
		try {
			this.context = new Context(
					getRequest().getResourceRef().getQueryAsForm(),
					this.ld4sServer.getHostName());
		} catch (Exception e) {
			e.printStackTrace();
			setStatus(Status.SERVER_ERROR_INTERNAL, "Unable to extract the linking criteria from the submitted query string.");
			return;
		}
		logger = this.ld4sServer.getLogger();
	}

	public static String getNamedModel(String uri) {
		Iterator<String> it = resource2namedGraph.keySet().iterator();
		String key = null;
		String nm = null;
		while (it.hasNext() && nm == null){
			key = it.next();
			if (uri.contains("/"+key+"/")){
				nm = resource2namedGraph.get(key);
			}	
		}
		return nm;
	}


	/**
	 *
	 * @param doc
	 * @return
	 */
	public static String serializeDomDocument(Document doc)
	// throws IOException, TransformerException
	{
		String ret = null;
		// set up a transformer
		try {
			TransformerFactory transfac = TransformerFactory.newInstance();
			Transformer trans = transfac.newTransformer();
			trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			trans.setOutputProperty(OutputKeys.INDENT, "yes");

			// create string from xml tree
			StringWriter sw = new StringWriter();
			StreamResult result = new StreamResult(sw);
			DOMSource source = new DOMSource(doc);
			trans.transform(source, result);
			ret = sw.toString();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

	/**
	 *
	 * @param content
	 * @return
	 */
	public static Document deserializeDomDocument(String content)
	// throws IOException, ParserConfigurationException, SAXException
	{
		Document ret = null;
		try {
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			InputSource is = new InputSource();
			is.setCharacterStream(new StringReader(content));
			ret = db.parse(is);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

	/**
	 * Extract predicates from a Sparql query.
	 *
	 * @return
	 * @throws Exception
	 */
	protected LinkedList<String> extractPredicatesFromQuery() throws Exception {
		// to enhance performance the model will contain only those predicates
		// useful to determine whether a resource match the query
		String where = "{", queryDecoded = this.query;
		LinkedList<String> predicates = new LinkedList<String>();
		int ind = queryDecoded.indexOf(where), ind1 = queryDecoded.indexOf("}");
		if (ind != -1 && ind1 != -1) {
			String wheresub = queryDecoded.substring(ind + where.length(), ind1);
			if (wheresub.trim().equals("")) {
				return predicates;
			}
			if (!wheresub.contains("(") && !wheresub.contains(")") && wheresub.contains("FILTER")) {
				throw new Exception("Wrong syntax: you have to insert "
						+ "the filter's argument in brackets");
			}
			String[] spaces = null, pred = null;
			LinkedList<String> filters = new LinkedList<String>();
			String tmp = "", curr = null, fullstop = ".", space = " ";
			StringTokenizer tokenizer = new StringTokenizer(wheresub);
			boolean filterTokens = false, bracket = false;
			while (tokenizer.hasMoreTokens()) {
				curr = tokenizer.nextToken();
				if (curr.equals("FILTER")) {
					// not consider the next chars contained within brackets
					filterTokens = true;
				}
				if (!filterTokens && !curr.equals(fullstop)) {
					if (!tmp.trim().equals("")) {
						tmp += space;
					}
					tmp += curr;
				}
				else if (!filterTokens) {
					filters.add(tmp);
					tmp = "";
				}
				else if (filterTokens && curr.endsWith(")")) {
					bracket = true;
				}
				else if (filterTokens && bracket && (curr.equals(fullstop) || curr.startsWith("?"))) {
					filterTokens = false;
					bracket = false;
					tmp += curr;
				}
			}
			filters.add(tmp);
			for (String filter : filters) {
				spaces = filter.split(" ");
				if (spaces.length == 3) {
					pred = spaces[1].split(":");
					if (pred.length == 2) {
						predicates.add(pred[1]);
					}
				}
			}
		}
		return predicates;
	}

	/**
	 * Creates and returns a new Restlet StringRepresentation built from xmlData. The xmlData will be
	 * prefixed with a processing instruction indicating UTF-8 and version 1.0.
	 *
	 * @param xmlData The xml data as a string.
	 * @return A StringRepresentation of that xmldata.
	 */
	public static StringRepresentation getStringRepresentationForSparqlResults(String xmlData) {
		return new StringRepresentation(xmlData, new MediaType(
				LD4SConstants.MEDIA_TYPE_SPARQL_RESULTS), Language.ALL, CharacterSet.UTF_8);
	}


	/**
	 * Content negotiation: checks wether the client is accepted one of the supported media types
	 * i.e., 
	 * application-all (the final output from the server will be Turtle)
	 * application/* (the final output from the server will be Turtle)
	 * text/* (the final output from the server will be Turtle)
	 * 
	 * application/rdf+xml (the final output from the server will be RDF/XML)
	 * text/n3 (the final output from the server will be N3 (which is equal to Turtle))
	 * text/n-triples (the final output from the server will be Ntriples)
	 * application/x-turtle (the final output from the server will be Turtle)
	 * application/rdf+json (the final output from the server will be RDF/JSON)
	 * 
	 * @param acceptedMediaTypes
	 * @return
	 */
	private MediaType selectMedia(List<Preference<MediaType>> acceptedMediaTypes) {
		MediaType ret = null;
		MediaType media = null;
		if (acceptedMediaTypes.size() == 0){
			ret = MediaType.APPLICATION_ALL;
		}else{
			for (int i=0; i<acceptedMediaTypes.size() && ret==null ;i++){
				media = acceptedMediaTypes.get(i).getMetadata();
				if (media.equals(MediaType.ALL)
						|| media.equals(MediaType.APPLICATION_ALL) || media.equals(MediaType.TEXT_ALL)
						|| media.equals(MediaType.TEXT_RDF_N3) || media.equals(MediaType.APPLICATION_RDF_XML)
						|| media.equals(MediaType.TEXT_RDF_NTRIPLES)
						|| media.equals(MediaType.APPLICATION_RDF_TURTLE)
						|| media.getName().equalsIgnoreCase(LD4SConstants.MEDIA_TYPE_RDF_JSON)
						//					    || ((media.equals(MediaType.TEXT_XML) || media.getName().equals(
						//				            LD4SConstants.MEDIA_TYPE_SPARQL_RESULTS)) && this.query != null)
						) 
				{
					ret = media;
				}
			}
		}
		return ret;
	}

	/**
	 * Initialize the specified model with the Spitfire ontology. 
	 * In this way, future inferences
	 * based on this ontology will be allowed.
	 *
	 * @param model model.
	 */
	protected Model initModel(OntModel model, String rdfFileName) {
		if (model == null) {
			String schemapath = SptVocab.class.getResource(rdfFileName).getPath();
			model = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM_MICRO_RULE_INF);
			// use the FileManager to find the input file
			java.io.InputStream in = FileManager.get().open(schemapath);
			if (in == null) {
				throw new IllegalArgumentException("File: " + schemapath + " not found");
			}
			// read the RDF file
			model.read(in, null);

		}
		return model;
	}

	protected void saveVocabEditsToFile(Model model, String rdfFileName) 
			throws IOException{
		if (model == null) {
			model = ModelFactory.createDefaultModel();
		}
		String schemapath = SptVocab.class.getResource(rdfFileName).getPath();
		File file = new File(schemapath); 
		if(!file.exists()){
			file.createNewFile();
		}
		model.write(new PrintWriter(file));
	}

	/**
	 * Creates and returns a new Restlet StringRepresentation built from rdfData. The rdfData will be
	 * prefixed with a processing instruction indicating UTF-8 and version 1.0.
	 *
	 * @param rdfData The rdf data as a string.
	 * @return A StringRepresentation of that rdfdata.
	 */
	public static StringRepresentation getStringRepresentationFromRdf(String rdfData, MediaType media) {
		return new StringRepresentation(rdfData, media, Language.ALL, CharacterSet.UTF_8);
	}

	/**
	 * Generates a log message indicating the type of request, the elapsed time required, the user who
	 * requested the data, and the day.
	 *
	 * @param requestType The type of LD4S request, such as "OV", etc.
	 * @param optionalParams Any additional parameters to the request.
	 */
	protected void logRequest(String requestType, String... optionalParams) {
		long elapsed = new Date().getTime() - requestStartTime;
		String sp = LD4SConstants.SEPARATOR1_ID;
		StringBuffer msg = new StringBuffer(20);
		msg.append(elapsed).append(" ms: ").append(requestType).append(sp);
		if (user != null){
			msg.append(user.getIdentifier()).append(sp);
		}
		if (resourceId != null){
			msg.append(resourceId).append(sp);
		}
		msg.append(timestamp);
		for (String param : optionalParams) {
			msg.append(sp).append(param);
		}
		ld4sServer.getLogger().info(msg.toString());
	}


	/**
	 * Create the RDF model that describes this Spitfire published 
	 * sensor dataset, using the voID vocabulary.
	 */
	public static Model initVoIDModel() {
		Model model = ModelFactory.createDefaultModel();
		com.hp.hpl.jena.rdf.model.Resource dataset = model.createResource(SptVocab.NS);
		dataset.addProperty(RDF.type, VoIDVocab.DATASET);
		dataset.addProperty(DC.title, "LD4Sensors");
		dataset.addProperty(DC.description,
				"Data about sensors, sensing devices in general and " +
						"sensor measurements stored in the LD4Sensors Triple DB"
						+ "published as Linked Data.");
		dataset.addProperty(VoIDVocab.URI_REGEX_PATTERN, ".*resource/ov/.*");
		dataset.addProperty(VoIDVocab.URI_REGEX_PATTERN, ".*resource/ov/sparql?query=.*");
		dataset.addProperty(DC.creator,
				"http://myr.altervista.org/foaf.rdf#iammyr");
		dataset
		.addProperty(
				DC.publisher,
				model
				.createResource()
				.addProperty(RDF.type, FoafVocab.ORGANIZATION)
				.addProperty(
						RDFS.label,
						"LD4Sensors - Digital Enterprise Research Institute (DERI) - National University of Ireland, Galway at Galway")
						.addProperty(FoafVocab.HOMEPAGE, "http://spitfire-project.eu/ld4s"));
		/** The following subject URIs come from the UMBEL dataset (based upon OpenCyc). */
		dataset.addProperty(DC.subject, "http://umbel.org/umbel/sc/SoftwareProject");
		dataset.addProperty(DCTerms.accessRights, "http://www.gnu.org/copyleft/fdl.html");
		dataset.addProperty(VoIDVocab.SPARQL_ENDPOINT,
				"http://spitfire-project.eu/ld4s/ov/sparql?query=");
		dataset.addProperty(VoIDVocab.VOCABULARY, FoafVocab.NS);
		dataset.addProperty(VoIDVocab.VOCABULARY, SptVocab.NS);
		dataset.addProperty(VoIDVocab.VOCABULARY, SiocVocab.NS);
		dataset.addProperty(VoIDVocab.VOCABULARY, VoIDVocab.NS);
		dataset.addProperty(VoIDVocab.VOCABULARY, DC.NS);
		dataset.addProperty(VoIDVocab.VOCABULARY, OWL.NS);
		dataset.addProperty(VoIDVocab.VOCABULARY, DCTerms.NS);
		dataset.addProperty(VoIDVocab.VOCABULARY, "http://umbel.org/umbel/sc/");
		return model;
	}

	/**
	 * Creates main resources and additional related information
	 * excluding linked data
	 *
	 * @param m_returned model which the resources to be created should be attached to
	 * @param obj object containing the information to be semantically annotate
	 * @param id resource identification
	 * @return model 
	 * @throws Exception
	 */
	protected Object[] makeOVData() throws Exception {
		Object[] resp = createOVResource();
		Resource resource = (Resource)resp[0]; 
		resource.addProperty(DCTerms.isPartOf,
				"http://"+this.ld4sServer.getHostName()+"void");
		return resp;
	}


	protected Object[] createOVResource()  throws Exception {
		// TODO Auto-generated method stub
		return null;
	}



	/**
	 * Called when an error resulting from an exception is caught during processing.
	 *
	 * @param msg A description of the error.
	 * @param e A chained exception.
	 */
	protected void setStatusError(String msg, Exception e) {
		String responseMsg = String.format("%s:%n  Request: %s %s%n  Caused by: %s", msg, this
				.getRequest().getMethod().getName(), this.getRequest().getResourceRef().toString(), e
				.getMessage());
		this.getLogger().info(responseMsg);
		getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, removeNewLines(responseMsg));
	}

	/**
	 * Called when an error occurs during processing.
	 *
	 * @param msg A description of the error.
	 */
	protected void setStatusError(String msg) {
		String responseMsg = String.format("%s:%n  Request: %s %s", msg, this.getRequest().getMethod()
				.getName(), this.getRequest().getResourceRef().toString());
		this.getLogger().info(responseMsg);
		getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, removeNewLines(responseMsg));
	}


	/**
	 * Helper function that removes any newline characters from the supplied string and replaces them
	 * with a blank line.
	 *
	 * @param msg The msg whose newlines are to be removed.
	 * @return The string without newlines.
	 */
	private String removeNewLines(String msg) {
		return msg.replace(System.getProperty("line.separator"), LD4SConstants.SEPARATOR1_ID);
	}


	/**
	 * Creates and returns a string representation of a given RDF model, using the specified RDF
	 * serialization (N3, RDF/XML, etc.)
	 *
	 * @param model
	 * @param relativeUriBase
	 * @param lang
	 * @return
	 */
	public static String serializeRDFModel(Model model, String relativeUriBase, String lang) {
		String ret = null;
		java.io.OutputStream os = null;
		// Serialize over an outputStream
		os = new ByteArrayOutputStream();
		model.write(os, lang, relativeUriBase);
		ret = os.toString();
		try {
			os.close();
		}
		catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return ret;
	}

	/**
	 * Build the uri for a resource of the current instance of the LD4S service.
	 * If the resource hosting service is the LD4S one, then it checks whether it actually
	 * exists in the TDB and if not, it creates a new one (with basic data, which could be
	 * eventually enriched by the user, later on, by the LD4S API for update requests).
	 * @param name resource identificator
	 * @return uri
	 */
	public static String getResourceUri(String host,
			String type, String name) {
		String uri = host + type +"/"+ name.toLowerCase();

		return uri;
	}

	/**
	 * Detects the presence inside a given model, of resources that
	 * should be stored under the LD4S generic named model.  
	 * @param model
	 */
	private OntModel handleGenericResources(Model model,
			OntModel genericmodel) {
		if (model == null || genericmodel == null){
			return null;
		}
		StmtIterator rit = model.listStatements();
		Resource res = null;
		Statement st = null;
		while (rit.hasNext()){
			st = rit.next();
			res = st.getSubject();
			//if the resource's uri is local to this LD4S instance
			if (res.getURI() == null){//in case of blank nodes
				genericmodel.add(st);
			}else{
				if (res.getURI().startsWith(this.ld4sServer.getHostName())){
					//if the resource is not part of the linked data
					//enriched set provided by LD4S
					Iterator<String> it= 
							resource2namedGraph.keySet().iterator();
					while (it.hasNext() && 
							!res.getURI().contains("/"+it.next()+"/"))
						;
					if (!it.hasNext()){
						genericmodel.add(st);	
					}								
				}
			}
		}
		return genericmodel;
	}

	/**
	 * Initialize a connection with the triple db
	 */
	private void initTDB(){
		// Direct way: Make a TDB-backed dataset
		String directory = ld4sServer.getServerProperties().getFoldername()+
				LD4SConstants.SYSTEM_SEPARATOR+"tdb"
				+LD4SConstants.SYSTEM_SEPARATOR+"LD4SDataset1" ;
		File dirf = new File (directory);
		if (!dirf.exists()){
			dirf.mkdirs();
		}
		dataset = TDBFactory.createDataset(directory) ;
		TDB.sync(dataset ) ;
	}

	protected boolean sparqlUpdateExec(String query){
		boolean success = true;
		try{
			initTDB();
			GraphStore graphStore = GraphStoreFactory.create(dataset) ;
			UpdateAction.parseExecute(query, graphStore) ;
		}catch(Exception e){
			success = false;
		}finally{
			closeTDB();
		}
		return success;
	}
	/**
	 * Close connection with the triple db
	 */
	private void closeTDB(){
		dataset.close() ;
	}

	/**
	 * Store the given model in the triple db
	 * @param rdfData model to be stored
	 * @return success
	 */
	protected boolean store(OntModel rdfData, String namedModel){
		boolean ret = true;
		//remove invalid entries from the model to be stored
		rdfData.removeAll(null, null, rdfData.createLiteral(""));
		rdfData.removeAll(null, null, rdfData.createLiteral("null"));
		//special handler for resources (out of the scope of the 
		//requested ones
		initTDB();
		this.dataset.begin(ReadWrite.WRITE) ;		
		try {
			OntModel genericmodel =  ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM_MICRO_RULE_INF);
			genericmodel = handleGenericResources(rdfData, genericmodel);
			rdfData.remove(genericmodel);
			if (!dataset.containsNamedModel(generalNamedModel)){
				dataset.addNamedModel(generalNamedModel, genericmodel);
			}else{
				OntModel model = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM_MICRO_RULE_INF);
				model.add(dataset.getNamedModel(generalNamedModel));
				model.add(genericmodel);
			}
			if (!dataset.containsNamedModel(namedModel)){
				dataset.addNamedModel(namedModel, rdfData);
			}else{
				OntModel model = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM_MICRO_RULE_INF);
				model.add(dataset.getNamedModel(namedModel));
				model.add(rdfData);
			} 
			dataset.commit() ;
			// Or call .abort()
		}catch(Exception e){
			e.printStackTrace();
			ret = false;
		}  finally { 
			dataset.end() ;
			closeTDB();
		}
		//		testSparqlPrint(namedModel);
		return ret; 

	}



	/**
	 * 
	 * @param resource
	 * @param searchType
	 * @param context not necessarily referred to the one of the web service requested
	 * resource, since Linked Data creation might be needed for other resources to be created
	 * as a side-effect of the main resource semantic annotation process. 
	 * @return
	 * @throws Exception
	 */
	public Object[] addLinkedData(Resource resource,
			Domain searchType, Context context, OntModel res_model) throws Exception {
		SearchRouter searchobj = null;
		String host = "http://"+this.ld4sServer.getHostName();
		switch(searchType){
		case ALL:
			searchobj = new GenericApi(host, 
					context, this.user, resource, res_model);
			break;
		case PEOPLE:
			searchobj = new PeopleApi(host, 
					context, this.user, resource, res_model);
			break;
		case WEATHER:
			searchobj = new WeatherApi(host, 
					context, this.user, resource, res_model);
			break;
		case LOCATION:
			searchobj = new LocationApi(host, 
					context, this.user, resource, res_model);
			break;
		case FEATURE: //searched in DBpedia ONLY
			searchobj = new EncyclopedicApi(host, 
					context, this.user, resource, res_model);
			break;
		case UNIT:
			searchobj = new UomApi(host, 
					context, this.user, resource, res_model);
			break;
		default: //searched in cross-domain datasets
			searchobj = new EncyclopedicApi(host, 
					context, this.user, resource, res_model);
		}
		OntModel model = searchobj.start();
		if (model != null){
			store(model, namedModel);
		}
		return new Object[]{resource, res_model};
	}

	protected String getRuleFilePath(){
		ServerProperties sp = this.ld4sServer.getServerProperties();
		return sp.get(ServerProperties.RULES_FILE_KEY);
	}

	protected String getDatasetFolderPath(){
		return ld4sServer.getServerProperties().getFoldername()+
				LD4SConstants.SYSTEM_SEPARATOR+"tdb"
				+LD4SConstants.SYSTEM_SEPARATOR+"LD4SDataset1";
	}

	public static String removeBrackets(String string) {
		if (string == null || string.trim().compareTo("") == 0){
			return null;
		}
		if (string.startsWith("[")){
			string = string.substring(1);
		}
		if (string.endsWith("]")){
			string = string.substring(0, string.length()-1);
		}
		if (string.startsWith("\"")){
			string = string.substring(1);
		}
		if (string.endsWith("\"")){
			string = string.substring(0, string.length()-1);
		}
		if (string.trim().compareTo("null")==0){
			string = null;
		}
		return string;
	}


	protected Resource crossResourcesAnnotation(LD4SObject ov, Resource resource, OntModel res_model) throws Exception{
		String 
		//check whether the specified subtype is a valid one,
		item = ov.getType();
		OntClass[] at = null;
		OntClass preftype = null;;
		if (item != null && item.trim().compareTo("")!=0){
			at = ov.getAcceptedTypes();
			if (at != null){
				for (int ind=0; ind<at.length&&preftype==null ;ind++){
					if (at[ind].getURI().toLowerCase().contains(item.toLowerCase())){
						preftype = at[ind];
					}
				}
				if (preftype != null){
					resource.addProperty(RDF.type, preftype);
					//eventually append a new subtype in the ld4s.rdf file
				}
			}			
		}
		if (item == null || item.trim().compareTo("")==0
				|| at == null || preftype == null){
			// if not, just assign the more general type
			resource.addProperty(RDF.type, ov.getDefaultType());
		}

		item = ov.getLocation_name();
		String[] item1 = ov.getCoords();
		if (item != null && item.startsWith("http://")){
			resource.addProperty(
					resource.getModel().createProperty(
							"http://www.ontologydesignpatterns.org/ont/dul/DUL.owl/hasLocation"), 
							resource.getModel().createResource(item));	
		}else {
			resource = addLocation(resource, item, item1, res_model);

		}
		item = ov.getResource_time();
		if (item != null && item.trim().compareTo("")!=0){
			resource.addProperty(CorelfVocab.RESOURCE_TIME, 
					resource.getModel().createTypedLiteral(item, XSDDatatype.XSDlong));
		}
		item = ov.getTime();
		if (item != null && item.trim().compareTo("")!=0){
			resource.addProperty(CorelfVocab.TIME, 
					resource.getModel().createTypedLiteral(item, XSDDatatype.XSDlong));
		}
		item = ov.getStart_range();
		if (item != null && item.trim().compareTo("")!=0){
			resource.addProperty(SptVocab.START_TIME, 
					resource.getModel().createTypedLiteral(item, XSDDatatype.XSDlong));
		}
		item = ov.getEnd_range();
		if (item != null && item.trim().compareTo("")!=0){
			resource.addProperty(SptVocab.END_TIME, 
					resource.getModel().createTypedLiteral(item, XSDDatatype.XSDlong));
		}		
		item = ov.getBase_datetime();
		if (item != null && item.trim().compareTo("")!=0){
			resource.addProperty(CorelfVocab.BASE_TIME, 
					resource.getModel().createTypedLiteral(item, XSDDatatype.XSDdateTime));
		}
		item = ov.getArchive();
		if (item != null && item.trim().compareTo("")!=0){
			resource.addProperty(SptVocab.TS_MAP, resource.getModel().createResource(item));
		}


		Person person = null;
		Resource publisher_resource = null;
		if (this.user.getIdentifier() != null){
			person = new Person(user.getFirstName(), user.getLastName(), user.getName(), user.getEmail(), null, null, null);
			publisher_resource = addPerson(resource, person, DC.publisher, res_model);
		}
		person = ov.getAuthor();
		if (person != null){
			if (publisher_resource != null){
				addPerson(publisher_resource, person, ProvVocab.ACTED_ON_BEHALF_OF, res_model);
			}
			addPerson(resource, person, ProvVocab.WAS_GENERATED_BY, res_model);
		}
		return resource;
	}



	protected Resource addWeatherForecast(Resource resource, OntModel res_model) throws Exception{
		String id = null;		
		Date[] dates = this.context.getTime_range(); 
		String[] location_coords = this.context.getLocation_coords();
		String location = this.context.getLocation();
		if (dates != null && dates.length == 2){
			if (location != null){
				id = URLEncoder.encode(location+"_"+dates[0]
						+dates[1], "utf-8");
			}else if (location_coords != null && location_coords.length == 2){
				id = URLEncoder.encode(location_coords[0]+"_"+location_coords[1]
						+"_"+dates[0]+"_"+dates[1], "utf-8");
			}else{
				throw new Exception("Unable to link with an external weather forecast resource" +
						"because of a wrongly specified location coordinates");
			}
		}else{
			throw new Exception("Unable to link with an external weather forecast resource" +
					"because of a wrongly specified time range");
		}
		String item_uri = getResourceUri(this.ld4sServer.getHostName(), "resource/weather_forecast", id);
		Resource item_resource = res_model.createResource(item_uri);
		Context con = new Context(this.ld4sServer.getHostName());
		con.setTime_range(dates);
		con.setLocation(location);
		con.setLocation_coords(location_coords);
		item_resource = (Resource)addLinkedData(item_resource, Domain.WEATHER, con, res_model)[0];
		resource.addProperty(SptVocab.WEATHER_FORECAST, item_resource);
		return resource;
	}

	protected Resource addLocation(Resource resource, String location_name, String[] location_coords,
			OntModel res_model){
		String item_uri = null;
		if (location_name != null){
			item_uri = getResourceUri(this.ld4sServer.getHostName(), "resource/location", location_name);
		}else if (location_coords != null && location_coords.length > 0){
			String lc = "";
			for (int i =0;i<location_coords.length;i++){
				lc += location_coords[i];
				if (i+1<location_coords.length){
					lc += "_";
				}
			}
			item_uri = getResourceUri(this.ld4sServer.getHostName(), "resource/location", lc);
		}else{
			return resource;
		}
		Resource item_resource = res_model.createResource(item_uri);
		Context con = new Context(this.ld4sServer.getHostName());
		con.setLocation(location_name);
		con.setLocation_coords(location_coords);
		try {
			item_resource = (Resource)addLinkedData(item_resource, Domain.LOCATION, con, res_model)[0];
			if (item_resource != null){
				resource.addProperty(
						resource.getModel().createProperty(
								"http://www.ontologydesignpatterns.org/ont/dul/DUL.owl/hasLocation"), 
								item_resource);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.err.println("Unable to create Linked Data about the specified location.");
		}
		return resource;

		//		String[][] space_item = ov.getSpace();
		////	if (space_item != null && space_item.length > 0){
		////		String item_uri = null;
		////		//getResourceUri(this.ld4sServer.getHostName(), "space", item);
		//		Resource item_resource = null;
		////		//resource.getModel().createResource(item_uri);
		//		Context con = null;		
		//		if (space_item != null){
		//			for (int row=0; row<space_item.length ;row++){
		//				for (int col=0; col<space_item[row].length ;col++){
		//					//get which space rel is referred by checking the row
		//					if (row<Context.spaceRelations.length && space_item[row][col] != null){
		//						con = new Context(this.ld4sServer.getHostName());
		//						con.setThing(space_item[row][col]);
		//						item_resource = addLinkedData(item_resource, Domain.LOCATION, con);
		//						if (item_resource != null){
		//							resource.addProperty(
		//									resource.getModel().createProperty(Context.relation_sem[row]), 
		//									item_resource);
		//						}
		//					}else if (space_item[row][col] != null){
		//						con = new Context(this.ld4sServer.getHostName());
		//						con.setThing(space_item[row][col]);
		//						item_resource = addLinkedData(item_resource, Domain.LOCATION, con);
		//
		//						resource.addProperty(
		//								resource.getModel().createProperty(
		//								"http://www.ontologydesignpatterns.org/ont/dul/DUL.owl/hasLocation"), 
		//								item_resource);
		//					}
		//				}
		//			}
		//
		//		}
		////	}
	}

	protected Resource addFoi(Resource resource, String foi, OntModel res_model) throws Exception{
		if (foi.contains("weather")){
			resource = addWeatherForecast(resource, res_model);
		}else{
			String item_uri = getResourceUri(this.ld4sServer.getHostName(), "resource/property", foi);
			Resource item_resource = resource.getModel().createResource(item_uri);
			Context con = new Context(this.ld4sServer.getHostName());
			con.setThing(foi);
			item_resource = (Resource)addLinkedData(item_resource, Domain.FEATURE, con, res_model)[0];
			if (item_resource != null){
				resource.addProperty(SsnVocab.FEATURE_OF_INTEREST, item_resource);
			}
		}

		return resource;
	}

	public com.hp.hpl.jena.rdf.model.Resource addObsProp (
			com.hp.hpl.jena.rdf.model.Resource resource, 
			java.lang.String observed_property,
			com.hp.hpl.jena.rdf.model.Property prop, 
			java.lang.String foi, OntModel res_model)
					throws java.lang.Exception{
		String item_uri = getResourceUri(this.ld4sServer.getHostName(), "resource/property", observed_property);
		Resource item_resource = res_model.createResource(item_uri);
		Context con = new Context(this.ld4sServer.getHostName());
		con.setThing(observed_property);
		con.setAdditionalTerms(new String[][]{
				{"", foi}
		});
		item_resource = (Resource)addLinkedData(item_resource, Domain.FEATURE, con, res_model)[0];
		if (item_resource != null){
			resource.addProperty(prop, item_resource);
		}
		return resource;
	}

	protected Resource addUom(Resource resource, String uom, OntModel res_model) throws Exception{
		String item_uri = getResourceUri(this.ld4sServer.getHostName(), "resource/uom", uom);
		Resource item_resource = resource.getModel().createResource(item_uri);
		Context con = new Context(this.ld4sServer.getHostName());
		con.setThing(uom);
		item_resource = (Resource)addLinkedData(item_resource, Domain.UNIT, con, res_model)[0];
		if (item_resource != null){
			resource.addProperty(SptVocab.UOM, item_resource);
		}
		return resource;
	}

	public static Resource[] createDataLinkResource(Resource from_resource,
			String baseHost, Link link, Property linking_predicate, String to_uri){
		Resource to_resource = null;
		Resource[] ret = new Resource[]{to_resource, from_resource};
		if (link == null || link.getTo() == null || 
				linking_predicate == null || from_resource == null || baseHost == null){
			return ret;
		}
		try {			 		
			//check whether this link already exists
			Model model = null;
			if (to_uri == null){
				to_uri = LD4SDataResource.getResourceUri(
						baseHost, "link", 
						URLEncoder.encode(from_resource.getURI(), "utf-8")
						+"_"
						+
						URLEncoder.encode((new SimpleDateFormat
								("yyyy/MM/dd HH:mm:ss")).format(new Date())
								, "utf-8"));
			}
			//if not already existing, create it
			if (model == null){
				model = from_resource.getModel();
				to_resource = model.createResource(to_uri);
			}
			//2. add properties to the dataLink resource
			to_resource.addProperty(RDF.type, SptVocab.DATALINKING);
			to_resource.addProperty(SptVocab.LINK_TO, 
					model.createResource(link.getTo()));
			to_resource.addProperty(SptVocab.LINK_FROM, from_resource);

			if (link.getBytes() != -1){
				to_resource.addProperty(SptVocab.BYTES, 
						model.createTypedLiteral(String.valueOf(link.getBytes()),
								XSDDatatype.XSDlong));
			}
			if (link.getTitle() != null){
				to_resource.addProperty(SptVocab.TITLE, 
						model.createResource(link.getTitle()));
			}
			if (link.getDatetime() != null){
				to_resource.addProperty(DCTerms.temporal, 
						model.createTypedLiteral(link.getDatetime(),
								XSDDatatype.XSDdateTime));
			}
			if (link.getFeedbacks() != null){
				for (String uri: link.getFeedbacks()){
					to_resource.addProperty(RevVocab.HAS_FEEDBACK, 
							model.createResource(uri));
				}
			}
			//3. add the author to the dataLink resource
			if (link.getAuthor() != null && link.getAuthor().getUri() != null){
				to_resource.addProperty(DCTerms.creator, 
						model.createResource(link.getAuthor().getUri()));
			}
			//4. link the datalink resource back to the local resource by a predicate that
			//represents the reason why this link was created
			from_resource.addProperty(linking_predicate, to_resource);

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			System.err.println("Unable to encode the from-resource during a data link instance creation");
		}

		return new Resource[]{to_resource, from_resource};
	}

	/**
	 * Links a subject resource to a person object by mean of a specifiable predicate.
	 * Afterwards, it adds linked data for this person, by searching for FOAF external resources.
	 * @param resource subject resource
	 * @param item person
	 * @param prop predicate
	 * @return resource representing the person
	 * @throws Exception
	 */
	protected Resource addPerson(Resource resource, Person item, Property prop, OntModel res_model) throws Exception{
		String id = null;		
		if (item.getEmail() != null && item.getEmail().trim().compareTo("") != 0){
			id = URLEncoder.encode(item.getEmail(), "utf-8");
		}else if (item.getWeblog() != null && item.getWeblog().trim().compareTo("") != 0){
			id = URLEncoder.encode(item.getWeblog(), "utf-8");
		}else if (item.getHomepage() != null && item.getHomepage().trim().compareTo("") != 0){
			id = URLEncoder.encode(item.getHomepage(), "utf-8");
		}else if (item.getNickname() != null && item.getNickname().trim().compareTo("") != 0){
			id = URLEncoder.encode(item.getNickname(), "utf-8");
		}else if (item.getFirstname() != null && item.getFirstname().trim().compareTo("") != 0 
				&& item.getSurname() != null && item.getSurname().trim().compareTo("") != 0){
			id = URLEncoder.encode(item.getFirstname()+item.getSurname(), "utf-8");
		}else{
			return null;
		}
		String item_uri = getResourceUri(this.ld4sServer.getHostName(), "resource/people", id);
		Resource item_resource = resource.getModel().createResource(item_uri);
		Context con = new Context(this.ld4sServer.getHostName());
		con.setPerson(item);
		item_resource = (Resource)addLinkedData(item_resource, Domain.PEOPLE, con, res_model)[0];
		if (item_resource != null){
			resource.addProperty(prop, item_resource);
		}
		return item_resource;
	}

	private void printModel(Model model, String name){
		StmtIterator it = model.listStatements();
		System.out.println("****MODEL named "+name);
		Statement st = null;
		RDFNode object = null;
		while (it.hasNext()){
			st = it.next();
			System.out.print(st.getSubject().getURI()+" ");
			System.out.print(st.getPredicate().getURI()+" ");
			object = st.getObject();
			if (object.isLiteral()){
				System.out.print(((Literal)object).getLexicalForm()+" ");
			}else if (object.isResource()){
				System.out.print(((Resource)object).getURI()+" ");
			}else{
				System.out.print("<some object> ");
			}
		}
	}

	private SparqlType getSparqlQueryType(String query){
		SparqlType ret = null;
		if (query.startsWith(SparqlType.SELECT.name())){
			ret = SparqlType.SELECT;
		}else if (query.startsWith(SparqlType.UPDATE.name())){
			ret = SparqlType.UPDATE;
		}else if (query.startsWith(SparqlType.ASK.name())){
			ret = SparqlType.ASK;
		}else if (query.startsWith(SparqlType.DESCRIBE.name())){
			ret = SparqlType.DESCRIBE;
		}else if (query.startsWith(SparqlType.CONSTRUCT.name())){
			ret = SparqlType.CONSTRUCT;
		}
		
		return ret;
	}

	/**
	 * 
	 * @param queryString
	 * @param type
	 * @return Object[]{serialized results, values}
	 */
	protected Representation sparqlQueryExec(String queryString){
		Representation ret = null;
		QueryExecution qexec = null;
		try {
			initTDB();
			this.dataset.begin(ReadWrite.READ) ;
			
			SparqlType type = getSparqlQueryType(queryString);
			if (type == null){
				setStatus(Status.CLIENT_ERROR_NOT_ACCEPTABLE);
				return null;
			}
			
			Query q = QueryFactory.create(queryString);
			qexec = QueryExecutionFactory.create(q, this.dataset);
			
			switch(type){
			case SELECT:
				ResultSet rs = qexec.execSelect();
				//				embed the results in an XML doc
				Object[] resp = SparqlResultsFormatter.xmlResults(rs);
				ret = new StringRepresentation(serializeDomDocument((Document)resp[0]));

				break;
			case UPDATE:
				boolean success = sparqlUpdateExec(this.query);
				ret = new StringRepresentation(String.valueOf(success));

				break;
			case CONSTRUCT:
				Model md = qexec.execConstruct();
				ret = serializeAccordingToReqMediaType(md);

				break;
			case DESCRIBE:
				md = qexec.execDescribe();
				ret = serializeAccordingToReqMediaType(md);

				break;
			case ASK:
				boolean ask = qexec.execAsk();
				ret = new StringRepresentation(String.valueOf(ask));

			default:
				;
			}

		} finally { 
			if (qexec != null)qexec.close();
			if (dataset != null)dataset.end() ;
			closeTDB();
		}

		return ret;	
	}

	/**
	 * Prints the dataset content with a limit of 100results, for test purposes.
	 */
	@SuppressWarnings("unused")
	private void testSparqlPrint(String namedModel){
		// A SPARQL query will see the new statement added.
		initTDB();
		this.dataset.begin(ReadWrite.READ) ;
		QueryExecution qExec = null;
		String query = null;
		ResultSet rs = null;
		try{
			query = //			           "SELECT (count(*) AS ?count) { ?s ?p ?o} LIMIT 10", 
					//			           dataset) ;
					//				"SELECT DISTINCT ?graph { GRAPH ?graph {?s ?p ?o}}";
					//				"SELECT * from named <http://192.168.56.1:8182/ld4s/graph/general> WHERE { ?s ?p ?o }";
					//				"SELECT * from <http://192.168.56.1:8182/ld4s/graph/ov> WHERE { ?s ?p ?o }";
					//				"SELECT * WHERE { graph <http://192.168.56.1:8182/ld4s/graph/ov> {?s ?p ?o }}limit 2";
					"select ?s ?p ?o from <http://lsm.deri.ie/metadata#> where {?s ?p ?o.} limit 20";
			System.out.println("Querying:\n"+query);
			qExec = QueryExecutionFactory.create(
					query, dataset) ;
			rs = qExec.execSelect() ;
			ResultSetFormatter.out(rs) ;

			query = //			           "SELECT (count(*) AS ?count) { ?s ?p ?o} LIMIT 10", 
					//			           dataset) ;
					//				"SELECT DISTINCT ?graph { GRAPH ?graph {?s ?p ?o}}";
					//				"SELECT * from named <http://192.168.56.1:8182/ld4s/graph/general> WHERE { ?s ?p ?o }";
					//				"SELECT * from <http://192.168.56.1:8182/ld4s/graph/ov> WHERE { ?s ?p ?o }";
					"SELECT * from <http://192.168.56.1:8182/ld4s/graph/ov> WHERE { ?s ?p ?o }limit 2";
			System.out.println("Querying:\n"+query);
			qExec = QueryExecutionFactory.create(
					query, dataset) ;
			rs = qExec.execSelect() ;
			ResultSetFormatter.out(rs) ;

			query = "SELECT distinct ?g WHERE { graph ?g {?s ?p ?o }}";
			System.out.println("Querying:\n"+query);
			qExec = QueryExecutionFactory.create(
					query, dataset) ;
			rs = qExec.execSelect() ;
			ResultSetFormatter.out(rs) ;

			namedModel = "http://0.0.0.0:8182/ld4s/graph/ov";
			query = //			           "SELECT (count(*) AS ?count) { ?s ?p ?o} LIMIT 10", 
					//			           dataset) ;
					"SELECT * { GRAPH <"+namedModel+"> {?s ?p ?o}} LIMIT 100";
			System.out.println("Querying:\n"+query);
			qExec = QueryExecutionFactory.create(
					query, dataset) ;
			rs = qExec.execSelect() ;
			ResultSetFormatter.out(rs) ;


			//			query = "SELECT *  FROM <localhost:8182/ld4s/ov> {?s ?p ?o} LIMIT 100";
			//			System.out.println("Querying:\n"+query);
			//			qExec = QueryExecutionFactory.create(
			//					query, dataset) ;
			//			rs = qExec.execSelect() ;
			//			ResultSetFormatter.out(rs) ;


		}catch(Exception e){
			e.printStackTrace();
		} finally { 
			if (qExec != null)qExec.close();
			if (dataset != null)dataset.end() ;
			closeTDB();
		}



		//
		//			     // ... perform a SPARQL Update
		//			     GraphStore graphStore = GraphStoreFactory.create(dataset) ;
		//			     String sparqlUpdateString = StrUtils.strjoinNL(
		//			          "PREFIX . <http://example/>",
		//			          "INSERT { :s :p ?now } WHERE { BIND(now() AS ?now) }"
		//			          ) ;
		//
		//			     UpdateRequest request = UpdateFactory.create(sparqlUpdateString) ;
		//			     UpdateProcessor proc = UpdateExecutionFactory.create(request, graphStore) ;
		//			     proc.execute() ;
	}

	protected OntModel retrieve (String uri, String namedModel){
		if (uri == null){
			return null;
		}
		if (namedModel == null){
			namedModel = generalNamedModel;
		}
		OntModel ret = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM_MICRO_RULE_INF);
		ret.createResource(uri);
		initTDB();
		this.dataset.begin(ReadWrite.READ) ;
		try {
			if (!dataset.containsNamedModel(namedModel)){
				return null;
			}
			Model model = dataset.getNamedModel(namedModel) ;
			StmtIterator retit = model.listStatements(model.createResource(uri), null, (RDFNode)null);
			while (retit.hasNext()){
				ret.add(retit.next());
			} 
			dataset.commit() ;
			// Or call .abort()
		}catch(Exception e){
			e.printStackTrace();
		}  finally { 
			dataset.end() ;
			closeTDB();
		}
		//		testSparqlPrint(namedModel);
		return ret;
	}

	protected String mediatypeToJenaLang(MediaType mediatype){
		String jenalang = null;
		if (mediatype == null){
			jenalang = MediaType.APPLICATION_RDF_XML.getName();
		}
		if (mediatype.getName().equalsIgnoreCase(LD4SConstants.MEDIA_TYPE_RDF_JSON)) {
			jenalang = LD4SConstants.LANG_RDFJSON;
		}
		else if (mediatype.equals(MediaType.APPLICATION_RDF_XML)) {
			jenalang = LD4SConstants.LANG_RDFXML;
		}
		else if (mediatype.equals(MediaType.TEXT_RDF_NTRIPLES)) {
			jenalang = LD4SConstants.LANG_NTRIPLE;
		}else if (mediatype.equals(MediaType.TEXT_ALL) 
				|| mediatype.equals(MediaType.TEXT_RDF_N3)
				|| mediatype.equals(MediaType.TEXT_PLAIN)
				|| mediatype.equals(MediaType.APPLICATION_RDF_TURTLE)
				|| mediatype.equals(MediaType.APPLICATION_ALL)
				|| mediatype.equals(MediaType.ALL)
				){		
			jenalang = LD4SConstants.LANG_TURTLE;
		}
		return jenalang;
	}

	protected Representation serializeAccordingToReqMediaType(Model rdfData){
		String str_rdfData = null;
		String lang =  mediatypeToJenaLang(this.requestedMedia);
		if (lang == null){
			setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
			return null;
		}		
		str_rdfData = serializeRDFModel(rdfData, LD4SConstants.RESOURCE_URI_BASE, lang);

		Representation ret = getStringRepresentationFromRdf(str_rdfData, requestedMedia);
		try {
			this.getLogger().info("***RESPONSE***" +ret.getText());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ret;
	}

	/**
	 * Creates a new type in the LD4S vocabulary IFF in case neither in there nor in the 
	 * SPITFIRE-sn vocabulary there is a resource having the specified
	 * type contained in its rdf:label 
	 * 
	 * @param type
	 * @return either the newly created type (class) or an already existing one matching with 
	 * the requests
	 * @throws IOException
	 */
	protected Resource createNewType(String type){
		//search whether the type requested is already existing in the local vocabulary
		Statement st = null;
		Resource subj = null;
		Literal lit = null;
		OntModel vocab = ModelFactory.createOntologyModel( OntModelSpec.OWL_MEM_MICRO_RULE_INF);

		//in the ld4s vocabulary
		initModel(vocab, "ld4s.rdf");
		StmtIterator it = vocab.listStatements(null, RDFS.label, (RDFNode)null);
		while (it.hasNext() && subj == null){
			st = it.nextStatement();
			if (st.getObject().isLiteral()){
				lit = (Literal)st.getObject();
				if (lit.getLexicalForm().toLowerCase().contains(type.toLowerCase())){
					subj = st.getSubject();
				}
			}
		}
		//or in the SPITFIRE-sn one
		if (subj == null){
			initModel(vocab, "spt_sn.rdf");
			it = vocab.listStatements(null, RDFS.label, (RDFNode)null);
			while (it.hasNext() && subj == null){
				st = it.nextStatement();
				if (st.getObject().isLiteral()){
					lit = (Literal)st.getObject();
					if (lit.getLexicalForm().contains(type)){
						subj = st.getSubject();
					}
				}
			}
		}
		if (subj != null){
			return subj;
		}
		//not found then just create a new one in the local ld4s vocabulary
		Resource newtype = vocab.createResource(this.ld4sServer.getHostName()
				+"ns/"+type.replaceAll(" ", "_"));
		newtype.addProperty(RDF.type, RDFS.Class);
		try {
			saveVocabEditsToFile(vocab, "ld4s.rdf");
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return newtype;
	}

	/**
	 * Remove from the triple db any triple that matches subject and predicate
	 * of those triples in the given model
	 * @param rdfData model containing the subj-pred couples to be matched and removed
	 * @return success
	 */
	protected boolean delete(Model rdfData, String namedModel){
		boolean ret = true;
		initTDB();
		this.dataset.begin(ReadWrite.WRITE) ;

		try {
			if (!dataset.containsNamedModel(namedModel)){
				return true;
			}
			Model model = dataset.getNamedModel(namedModel) ;
			StmtIterator iter = rdfData.listStatements();
			Statement stmt = null;
			Resource  subject = null;
			Property  predicate = null;
			while (iter.hasNext()) {
				stmt = iter.nextStatement();  // get next statement
				subject   = stmt.getSubject();     // get the subject
				predicate = stmt.getPredicate();   // get the predicate
				model.removeAll(subject, predicate, null);
			}
			dataset.commit() ;
			// Or call .abort()
		}catch(Exception e){
			e.printStackTrace();
			ret = false;
		}  finally { 
			dataset.end() ;
			closeTDB();
		}
		//		testSparqlPrint(namedModel);
		return ret;
	}

	/**
	 * Remove from the triple db those triples that match subj-pred of those in the given model
	 * and adds new ones with the given values
	 * @param rdfData model containing the subj-pred couples to be updated in their values
	 * @return success
	 */
	protected boolean update(OntModel rdfData, String namedModel){
		delete(rdfData, namedModel);
		return store(rdfData, namedModel);
	}

	protected boolean delete(String uri, String namedModel){
		boolean ret = true;
		initTDB();
		this.dataset.begin(ReadWrite.WRITE) ;

		try {
			if (!dataset.containsNamedModel(namedModel)){
				return true;
			}
			Model model = dataset.getNamedModel(namedModel);
			model.removeAll(model.createResource(uri), null, null);
			dataset.commit() ;
			// Or call .abort()
		}catch(Exception e){
			e.printStackTrace();
			ret = false;
		}  finally { 
			dataset.end() ;
			closeTDB();
		}
		//		testSparqlPrint(namedModel);
		return ret;
	}



	public static String getCurrentTime(){
		String now = null;
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		//get current date time with Date()
		Date date = new Date();
		now = dateFormat.format(date);
		return now;
	}
}
