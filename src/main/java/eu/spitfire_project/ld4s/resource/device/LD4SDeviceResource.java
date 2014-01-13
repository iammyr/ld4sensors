package eu.spitfire_project.ld4s.resource.device;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.Resource;

import eu.spitfire_project.ld4s.lod_cloud.Context.Domain;
import eu.spitfire_project.ld4s.resource.LD4SDataResource;
import eu.spitfire_project.ld4s.vocabulary.CorelfVocab;
import eu.spitfire_project.ld4s.vocabulary.SptVocab;

/**
 * Construct an oobservation value resource.
 *
 * @author Myriam Leggieri.
 *
 */
public class LD4SDeviceResource extends LD4SDataResource {
	/** Service resource name. */
	protected String resourceName = "Observation Value";

	/** RDF Data Model of this Service resource semantic annotation. */
	protected OntModel rdfData = null;

	/** Resource provided by this Service resource. */
	protected Device ov = null;


	/**
	 * Creates main resources and additional related information
	 * including linked data
	 *
	 * @param m_returned model which the resources to be created should be attached to
	 * @param obj object containing the information to be semantically annotate
	 * @param id resource identification
	 * @return model 
	 * @throws Exception
	 */
	protected Object[] makeOVLinkedData() throws Exception {
		Object[] resp = makeOVData();
		Resource resource = (Resource)resp[0];
		//set the linking criteria
		this.context = ov.getLink_criteria();
		resp = addLinkedData(resource, Domain.ALL, this.context, (OntModel)resp[1]);
		return resp;
	}

	
	/**
	 * Creates the main resource
	 * @param model
	 * @param value
	 * @return
	 * @throws Exception 
	 */
	@Override
	protected  Object[] createOVResource() throws Exception {
		Resource resource = null;
		String subjuri = null;
		if (resourceId != null){
			subjuri = this.uristr;	
		}else{
			subjuri = ov.getRemote_uri();
		}
		resource = rdfData.createResource(subjuri);

		String item = ov.getBase_name();
		if (item != null && item.trim().compareTo("")!=0){
			if (item.startsWith("http://")){
				resource.addProperty(CorelfVocab.BASE_NAME, 
						rdfData.createResource(item));	
			}else{
				resource.addProperty(CorelfVocab.BASE_NAME, 
						rdfData.createTypedLiteral(item));
			}
		}
		item = ov.getBase_ov_name();
		if (item != null && item.trim().compareTo("")!=0){
			if (item.startsWith("http://")){
				resource.addProperty(CorelfVocab.BASE_OV_NAME, 
						rdfData.createResource(item));	
			}else{
				resource.addProperty(CorelfVocab.BASE_OV_NAME, 
						rdfData.createTypedLiteral(item));
			}
		}
		
		item = ov.getObserved_property();
		if (item != null && item.trim().compareTo("")!=0){
			if (item.startsWith("http://")){
				resource.addProperty(SptVocab.OBSERVED_PROPERTY, 
						rdfData.createResource(item));	
			}else{
				resource = addObsProp(resource, item, SptVocab.OBSERVED_PROPERTY, ov.getFoi(), rdfData);
			}
		}	
		item = ov.getUnit_of_measurement();
		if (item != null && item.trim().compareTo("")!=0){
			if (item.startsWith("http://")){
				resource.addProperty(SptVocab.UOM, 
						rdfData.createResource(item));	
			}else{
				resource = addUom(resource, item, rdfData);
			}
		}
		String[] tprops = ov.getTsproperties();
		if (tprops != null){
			for (int i=0; i<tprops.length ;i++){
				if (tprops[i].startsWith("http://")){
					resource.addProperty(SptVocab.TEMPORAL, 
							rdfData.createResource(tprops[i]));	
				}else{
					resource.addProperty(SptVocab.TEMPORAL, 
							rdfData.createTypedLiteral(tprops[i]));
				}
			}			
		}
		String[] vals = ov.getValues();
		if (vals != null){
			for (int i=0; i<vals.length ;i++){
				if (vals[i] != null){
					if (vals[i].startsWith("http://")){
						resource.addProperty(SptVocab.OUT, 
								rdfData.createResource(vals[i]));	
					}else{
						resource.addProperty(SptVocab.OUT, vals[i]);
					}	
				}
			}			
		}
		resource = crossResourcesAnnotation(ov, resource, rdfData);
		return new Object[]{resource, rdfData};
	}




}
