package org.sagebionetworks.repo;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sagebionetworks.client.Synapse;
import org.sagebionetworks.repo.model.Reference;
import org.sagebionetworks.repo.model.Step;

/**
 * WikiGenerator is used to auto-generate the wiki for the Platform Repository
 * Service.
 * 
 * All the log output goes to stdout. See generateRepositoryServiceWiki.sh for
 * how the log output is cleaned and turned into an actual wiki page. The reason
 * why this writes log output file instead of a normal output to stdout is
 * because I want to include the response headers logged by HttpClient and this
 * was a quick way to make that happen.
 * 
 * Also note that I originally wrote this against HtmlUnit so that I could
 * include a bit of testing to make sure the responses coming back were sane,
 * but HtmlUnit does not support PUT or DELETE.
 * 
 * {code} svn checkout
 * https://sagebionetworks.jira.com/svn/PLFM/trunk/tools/wikiutil cd wikiutil
 * ~/platform/trunk/tools/wikiutil>mvn clean compile
 * ~/platform/trunk/tools/wikiutil>./generateRepositoryServiceWiki.sh
 * http://localhost:8080 > wiki.txt {code}
 * 
 */
public class ReadOnlyWikiGenerator {

	private static final Logger log = Logger.getLogger(WikiGenerator.class
			.getName());

	private static final String QUERY_SYNTAX = "{code}SELECT * FROM <data type> WHERE <expression> (AND <expression>)* [LIMIT <#>] [OFFSET #]\n\n"
			+ "<expression> := <field name> <operator> <value>{code}\n"
			+ " where <field name> is a primary field or an annotation name.\n"
			+ "{{<value>}} should be in quotes for strings, but not numbers (i.e. {{name == \"Smith\" AND size > 10}}). "
			+ "Dates are in milliseconds since Jan 1, 1970.\n\n"
			+ "Curently supported {{<operators>}} with their required URL escape codes:\n"
			+ "||Operator ||Value|| URL Escape Code \n"
			+ "| Equal| == | %3D%3D|\n"
			+ "| Does Not equal| != | !%3D|\n"
			+ "| Greater Than | > | %3E |\n"
			+ "| Less than | < | %3C |\n"
			+ "| Greater than or equals | >= | %3E%3D |\n"
			+ "| Less than or equals | <= | %3C%3D |\n";

	/**
	 * @param args
	 * @return the number of errors encountered during execution
	 * @throws Exception
	 */
	public static int main(String[] args) {

		try {

			WikiGenerator wiki = WikiGenerator
					.createWikiGeneratorFromArgs(args);

			wiki
					.doLogin("h2. Log into Synapse",
							"You must have an account with permission to view entities in Synapse.");

			log.info("h1. Query API");
			log
					.info("The Query API is loosely modeled after Facebook's [Query Language|https://developers.facebook.com/docs/reference/fql/].");
			log.info("h2. Examples");
			wiki
					.doGet(
							"/query?query=select+*+from+dataset+limit+3+offset+1",
							"h3. 'Select *' Query",
							"These queries are generally of the form:\n"
									+ "{code}SELECT * FROM <data type> [LIMIT <#>] [OFFSET <#>]{code}\n"
									+ "The current format supports only selection of the entire record, i.e. 'SELECT *', and not of individual fields.\n"
									+"(Note: 'OFFSET' starts at 1.)");
			wiki
					.doGet(
							"/query?query=select+*+from+dataset+order+by+Number_of_Samples+DESC+limit+3+offset+1",
							"h3. 'Order By' Query",
							"These queries are generally of the form:\n"
									+ "{code}SELECT * FROM <data type> ORDER BY <field name> [ASC|DESC] [LIMIT <#>] [OFFSET #]{code}\n"
									+ " where <field name> is the name of a primary field or annotation.");

			JSONObject results = wiki
					.doGet(
							"/query?query=select+*+from+dataset+where+name+==+%22MSKCC+Prostate+Cancer%22",
							"h3. Single clause 'Where' Query",
							"These queries are generally of the form:\n"
									+ QUERY_SYNTAX);
			JSONArray datasets = results.getJSONArray("results");
			JSONObject dataset = null;
			for (int i = 0; i < datasets.length(); i++) {
				dataset = datasets.getJSONObject(i);
				if (dataset.getString("dataset.name").equals(
						"MSKCC Prostate Cancer")) {
					break;
				}
			}
			if (null == dataset) {
				throw new Exception(
						"Attempting to run the wiki generator against a repository service that has not been "
								+ "bootstrapped with the Sawyers MSKCC Prostate Cancer dataset");
			}

			wiki
					.doGet(
							"/query?query=select+*+from+dataset+where+dataset.Species+==+%22Human%22+and+dataset.Number_of_Samples+%3E+100+limit+3+offset+1",
							"h3. Multiple clause 'Where' Query",
							"These queries are generally of the form:\n"
									+ QUERY_SYNTAX);

			wiki
					.doGet(
							"/query?query=select+*+from+layer+where+layer.parentId+==+%22"
									+ dataset.getString("dataset.id") + "%22",
							"h3. 'Select *' Query for the Layers of a Dataset",
							"These queries are generally of the form:\n"
									+ "{code}SELECT * FROM layer WHERE layer.parentId == <parentId> [LIMIT <#>] [OFFSET <#>]{code}");

			wiki
					.doGet(
							"/query?query=select+*+from+layer+where+layer.parentId+==+%22"
									+ dataset.getString("dataset.id")
									+ "%22+ORDER+BY+type",
							"h3. 'Order By' Query for the Layers of a Dataset",
							"These queries are generally of the form:\n"
									+ "{code}SELECT * FROM layer WHERE layer.parentId == <parentId> ORDER BY <field name> [ASC|DESC] [LIMIT <#>] [OFFSET <#>]{code}\n"
									+"where <field name> is the name of a primary field or annotation.");

			log.info("h2. Schema");
			wiki
					.doGet(
							"/query/schema",
							"h3. Query Response Schema",
							"The [JsonSchema|http://json-schema.org/] is an emerging standard similar to DTDs for XML.");

			log.info("h1. REST API");
			log
					.info("The REST API took inspiration from several successful REST APIs:");
			log
					.info("* Facebook's [Graph API|https://developers.facebook.com/docs/reference/api/]");
			log
					.info("* Google's [Data Protocol|http://code.google.com/apis/gdata/docs/2.0/basics.html]");
			log
					.info("* The [Open Data Protocol|http://www.odata.org/developers/protocols/overview]\n");

			log.info("h2. Read-Only Examples");
			wiki
					.doGet(
							"/query?query='select+*+from+dataset+order+by+name'",
							"h3. Get All Studies",
							"Optional Parameters\n"
									+ "* offset - _integer_ - 1-based pagination offset\n"
									+ "* limit - _integer_ - maximum number of results to return\n"
									+ "* order by - _string_ - the name of the field upon which to sort\n"
									+ "* ascending - _boolean_ - whether or not to sort ascending");
			dataset = wiki
					.doGet(
							"/entity/" + dataset.getString("dataset.id"),
							"h3. Get a Study",
							"This returns the primary fields of a study and links to get additional info.");
			wiki.doGet(dataset.getString("annotations"),
					"h3. Get Annotations for a Dataset",
					"This returns the annotations for a dataset.");
			results = wiki
					.doGet(dataset.getString("uri") + "/layer",
							"h3. Get all the Layers for a Dataset",
							"This returns the primary fields for all the layers of a dataset.");
			JSONArray layers = results.getJSONArray("results");

			// Only show one clinical layer here, skip all others
			JSONObject layer = null;
			for (int i = 0; i < layers.length(); i++) {
				layer = layers.getJSONObject(i);
				String type = layer.getString("type");
				if (type.equals("G")) {
					continue;
				} else if (type.equals("E")) {
					continue;
				} else if (type.equals("C")) {
					type = "Clinical";
				}
				wiki.doGet(layer.getString("uri"), "h3. Get a " + type
						+ " Dataset Layer",
						"This returns the metadata for a dataset layer.");
				wiki.doGet(layer.getString("annotations"),
						"h4. Get Annotations for a " + type + " Dataset Layer",
						"This returns the annotations for a dataset layer.");
				wiki
						.doGet(layer.getString("uri") + "/preview",
								"h4. Get preview data for a " + type
										+ " Dataset Layer",
								"This returns the preview data for a dataset layer.");
				wiki.doGet(layer.getString("uri") + "/preview",
						"h4. Get preview data as a map for a " + type
								+ " Dataset Layer",
						"This returns the preview data for a dataset layer.");
				
				// TODO PLFM-1083 Documentation: refurbish the wiki generator			
				/*
				JSONObject locationsResult = wiki
						.doGet(layer.getString("uri")+"/location",
								"h4. Get the locations for a " + type
										+ " Dataset Layer",
								"This returns all the locations metadata for a dataset layer.");
				JSONArray locations = locationsResult.getJSONArray("results");
				for (int j = 0; j < locations.length(); j++) {
					String locationUri = locations.getJSONObject(j).getString(
							"uri");
					String locationType = locations.getJSONObject(j).getString(
							"type");
					JSONObject location = wiki
							.doGet(locationUri, "h4. Get the " + locationType
									+ " for a " + type + " Dataset Layer",
									"This returns the location data for a dataset layer.");
					if (locationType.equals("awss3")) {
						log
								.info("An example to fetch the file using curl:{code}"
										+ "curl -o local/path/to/file.zip '"
										+ location.getString("path")
										+ "'{code}\n");
						log
								.info("An example to *conditionally* fetch the file using curl:{code}"
										+ "curl -i -H If-None-Match:"
										+ location.getString("md5sum")
										+ " '"
										+ location.getString("path")
										+ "'\n\n"
										+ "HTTP/1.1 304 Not Modified\n"
										+ "x-amz-id-2: h3qt9NfdRw7utcyVMCZF/dNRto9ZpmKY56w69HNpuMkNsaDv9MgduGY9L3zBQWl\n"
										+ "x-amz-request-id: 3AADDC9EF832ADD2\n"
										+ "Date: Tue, 07 Jun 2011 18:40:15 GMT\n"
										+ "Last-Modified: Tue, 05 Apr 2011 00:42:50 GMT\n"
										+ "ETag: \""
										+ location.getString("md5sum")
										+ "\"\n"
										+ "Server: AmazonS3{code}\n");
						JSONObject headLocation = wiki
							.doGet(locationUri+"?method=HEAD", "h4. Get the " + locationType
									+ " for a " + type + " Dataset Layer for HTTP method HEAD",
									"This returns the location data for a dataset layer with the S3 URL presigned for a HEAD request instead of the default GET.");
						log
						.info("An example double check that Synapse's MD5 matches the one in S3 using curl:{code}"
								+ "curl -I -H If-Match:"
								+ headLocation.getString("md5sum")
								+ " '"
								+ headLocation.getString("path")
								+ "'\n\n"
								+ "HTTP/1.1 200 OK\n"
								+ "x-amz-id-2: h3qt9NfdRw7utcyVMCZF/dNRto9ZpmKY56w69HNpuMkNsaDv9MgduGY9L3zBQWl\n"
								+ "x-amz-request-id: 3AADDC9EF832ADD2\n"
								+ "Date: Tue, 07 Jun 2011 18:40:15 GMT\n"
								+ "Last-Modified: Tue, 05 Apr 2011 00:42:50 GMT\n"
								+ "ETag: \""
								+ location.getString("md5sum")
								+ "\"\n"
								+ "Accept-Ranges: bytes\n"
								+ "Content-Type: application/binary\n"
								+ "Content-Length: 30681\n"
								+ "Server: AmazonS3{code}\n");

					}
				}
				*/
				break; // we have displayed one clinical layer, don't bother with any others
			}
			
			log.info("h2. References");
			String datasetId = dataset.getString("id");
			if (datasetId==null || datasetId.length()==0) throw new RuntimeException("No dataset");
			Integer datasetVersion = dataset.getInt("versionNumber");
			if (datasetVersion==null) throw new RuntimeException("No datasetVersion");
			
			Synapse synapse = wiki.getClient();
			JSONObject o = synapse.getEntity("/entity/"+datasetId+"/referencedby");
			if (o.getInt("totalNumberOfResults")==0) {
				Step step = new Step();
				step.setName("Analysis Step");
				Set<Reference> inputs = new HashSet<Reference>();
				Reference ref = new Reference();
				ref.setTargetId(datasetId);
				ref.setTargetVersionNumber((long)datasetVersion);
				inputs.add(ref);
				step.setInput(inputs);
				synapse.createEntity(step);
			}
			wiki
			.doGet(
					"/entity/"+datasetId+"/referencedby",
					"h3. Find reference to any version of an entity",
					"");
			wiki
			.doGet(
					"/entity/"+datasetId+"/version/"+datasetVersion+"/referencedby",
					"h3. Find reference to a specific version of an entity",
					"");

			log.info("h2. Schemas");
			wiki
					.doGet(
							"/query/schema",
							"h3. Query Results Schema",
							"The [JsonSchema|http://json-schema.org/] is an emerging standard similar to DTDs for XML.");
			wiki
					.doGet(
							"/project/schema",
							"h3. Project Schema",
							"The [JsonSchema|http://json-schema.org/] is an emerging standard similar to DTDs for XML.");
			wiki
					.doGet(
							"/dataset/schema",
							"h3. Dataset Schema",
							"The [JsonSchema|http://json-schema.org/] is an emerging standard similar to DTDs for XML.");
			wiki
					.doGet(
							"/layer/schema",
							"h3. Layer Schema",
							"The [JsonSchema|http://json-schema.org/] is an emerging standard similar to DTDs for XML.");
			wiki
					.doGet(
							"/preview/schema",
							"h3. Layer Preview Schema",
							"The [JsonSchema|http://json-schema.org/] is an emerging standard similar to DTDs for XML.");
			wiki
					.doGet(
							"/annotations/schema",
							"h3. Annotations Schema",
							"The [JsonSchema|http://json-schema.org/] is an emerging standard similar to DTDs for XML.");
			wiki
					.doGet(
							"/acl/schema",
							"h3. Access Control List Schema",
							"The [JsonSchema|http://json-schema.org/] is an emerging standard similar to DTDs for XML.");

			return (wiki.getNumErrors());
		} catch (Exception e) {
			log.info(e);
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
