package edu.harvard.iq.dataverse.api.datadeposit;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseUser;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.api.Dataverses;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.Response;
import org.swordapp.server.AuthCredentials;
import org.swordapp.server.CollectionDepositManager;
import org.swordapp.server.Deposit;
import org.swordapp.server.DepositReceipt;
import org.swordapp.server.SwordAuthException;
import org.swordapp.server.SwordConfiguration;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;
import org.swordapp.server.UriRegistry;

public class CollectionDepositManagerImpl implements CollectionDepositManager {

    private static final Logger logger = Logger.getLogger(CollectionDepositManagerImpl.class.getCanonicalName());
    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    DatasetServiceBean datasetService;
    @Inject
    SwordAuth swordAuth;
    @Inject
    UrlManager urlManager;
    @EJB
    EjbDataverseEngine engineSvc;
    @EJB
    DatasetFieldServiceBean datasetFieldService;
    @Inject
    Dataverses dataversesApi;

    @Override
    public DepositReceipt createNew(String collectionUri, Deposit deposit, AuthCredentials authCredentials, SwordConfiguration config)
            throws SwordError, SwordServerException, SwordAuthException {

        DataverseUser dataverseUser = swordAuth.auth(authCredentials);

        urlManager.processUrl(collectionUri);
        String dvAlias = urlManager.getTargetIdentifier();
        if (urlManager.getTargetType().equals("dataverse") && dvAlias != null) {

            logger.fine("attempting deposit into this dataverse alias: " + dvAlias);

            Dataverse dvThatWillOwnDataset = dataverseService.findByAlias(dvAlias);

            if (dvThatWillOwnDataset != null) {

                if (swordAuth.hasAccessToModifyDataverse(dataverseUser, dvThatWillOwnDataset)) {

                    logger.fine("multipart: " + deposit.isMultipart());
                    logger.fine("binary only: " + deposit.isBinaryOnly());
                    logger.fine("entry only: " + deposit.isEntryOnly());
                    logger.fine("in progress: " + deposit.isInProgress());
                    logger.fine("metadata relevant: " + deposit.isMetadataRelevant());

                    if (deposit.isEntryOnly()) {
                        logger.fine("deposit XML received by createNew():\n" + deposit.getSwordEntry());
                        // require title *and* exercise the SWORD jar a bit
                        Map<String, List<String>> dublinCore = deposit.getSwordEntry().getDublinCore();
                        if (dublinCore.get("title") == null || dublinCore.get("title").get(0) == null || dublinCore.get("title").get(0).isEmpty()) {
                            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "title field is required");
                        }

                        if (dublinCore.get("date") != null) {
                            String date = dublinCore.get("date").get(0);
                            if (date != null) {
                                /**
                                 * @todo re-enable this. use
                                 * datasetFieldValidator.isValid?
                                 */
//                                boolean isValid = DateUtil.validateDate(date);
//                                if (!isValid) {
//                                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Invalid date: '" + date + "'.  Valid formats are YYYY-MM-DD, YYYY-MM, or YYYY.");
//                                }
                            }
                        }

                        /**
                         * @todo don't hard code these! See also saving a
                         * dataset in GUI changes globalId to hard coded value
                         * (doi:10.5072/FK2/5555)
                         * https://redmine.hmdc.harvard.edu/issues/3993
                         */
                        String incomingProtocol = "doi";
                        String incomingAuthority = "myAuthority";
                        String incomingIdentifier = UUID.randomUUID().toString();

                        String incomingTitle = dublinCore.get("title").get(0);
                        String incomingAuthor = dublinCore.get("creator").get(0);
                        String incomingKeyword1 = dublinCore.get("subject").get(0);
                        String incomingKeyword2 = dublinCore.get("subject").get(1);
                        String incomingKeyword3 = dublinCore.get("subject").get(2);

                        JsonObjectBuilder newDatasetJson = Json.createObjectBuilder();
                        JsonObjectBuilder initialVersion = Json.createObjectBuilder();
                        JsonObjectBuilder metadataBlocks = Json.createObjectBuilder();
                        JsonObjectBuilder citationMetadataBlock = Json.createObjectBuilder();
//                        JsonObjectBuilder citationFieldsBuilder = Json.createObjectBuilder();
                        JsonArrayBuilder citationFields = Json.createArrayBuilder();

                        JsonObjectBuilder titleBuilder = Json.createObjectBuilder();
                        titleBuilder.add("typeName", "title");
                        titleBuilder.add("typeClass", "primitive");
                        titleBuilder.add("multiple", false);
                        /**
                         * @todo validation! if you comment this out so no title
                         * is included, the dataset is still created! Shouldn't
                         * this be enforced by the native API?
                         */
                        titleBuilder.add("value", incomingTitle);
                        citationFields.add(titleBuilder);

                        List<String> incomingAuthors = dublinCore.get("creator");
                        if (incomingAuthority.isEmpty()) {
                            throw SwordUtil.throwRegularSwordErrorWithoutStackTrace("At least one dcterms:creator must be specified");
                        }
                        JsonArrayBuilder authorNamesBuilder = Json.createArrayBuilder();
                        for (String author : incomingAuthors) {
                            JsonObjectBuilder authorNameBuilder = Json.createObjectBuilder();
                            authorNameBuilder.add("typeName", "authorName");
                            authorNameBuilder.add("typeClass", "primitive");
                            authorNameBuilder.add("multiple", false);
                            authorNameBuilder.add("value", author);
                            authorNamesBuilder.add(authorNameBuilder);
                        }
                        JsonObjectBuilder authorsBuilder = Json.createObjectBuilder();
                        authorsBuilder.add("typeName", "author");
                        authorsBuilder.add("typeClass", "compound");
                        authorsBuilder.add("multiple", true);
                        authorsBuilder.add("value", authorNamesBuilder);
                        /**
                         * @todo when this is enabled (uncommented), we get
                         * "Error parsing initialVersion:
                         * org.glassfish.json.JsonStringImpl cannot be cast to
                         * javax.json.JsonObject". Why?
                         */
//                        citationFields.add(authorsBuilder);

                        List<String> incomingKeywords = dublinCore.get("subject");
                        if (!incomingKeywords.isEmpty()) {
                            JsonArrayBuilder keywordsArrayBuilder = Json.createArrayBuilder();
                            for (String keyword : incomingKeywords) {
                                keywordsArrayBuilder.add(keyword);
                            }
                            JsonObjectBuilder keywordsBuilder = Json.createObjectBuilder();
                            keywordsBuilder.add("typeName", "keyword");
                            keywordsBuilder.add("typeClass", "primitive");
                            keywordsBuilder.add("multiple", true);
                            keywordsBuilder.add("value", keywordsArrayBuilder);
                            citationFields.add(keywordsBuilder);
                        }

                        citationMetadataBlock.add("fields", citationFields);
                        metadataBlocks.add("citation", citationMetadataBlock);
                        initialVersion.add("metadataBlocks", metadataBlocks);

                        // not used. here for reference.
                        String jsonBody = "{\n"
                                + "  \"authority\": \"" + incomingAuthority + "\",\n"
                                + "  \"identifier\": \"" + incomingIdentifier + "\",\n"
                                + "  \"persistentUrl\": \"http://dx.doi.org/10.5072/FK2/9\",\n"
                                + "  \"protocol\": \"" + incomingProtocol + "\",\n"
                                + "  \"initialVersion\": {\n"
                                + "    \"metadataBlocks\": {\n"
                                + "      \"citation\": {\n"
                                + "        \"fields\": [\n"
                                + "          {\n"
                                + "            \"value\": \"" + incomingTitle + "\",\n"
                                + "            \"typeClass\": \"primitive\",\n"
                                + "            \"multiple\": false,\n"
                                + "            \"typeName\": \"title\"\n"
                                + "          },\n"
                                + "          {\n"
                                + "            \"value\": [\n"
                                + "              {\n"
                                + "                \"authorName\": {\n"
                                + "                  \"value\": \"" + incomingAuthor + "\",\n"
                                + "                  \"typeClass\": \"primitive\",\n"
                                + "                  \"multiple\": false,\n"
                                + "                  \"typeName\": \"authorName\"\n"
                                + "                }\n"
                                + "              }\n"
                                + "            ],\n"
                                + "            \"typeClass\": \"compound\",\n"
                                + "            \"multiple\": true,\n"
                                + "            \"typeName\": \"author\"\n"
                                + "          },\n"
                                + "          {\n"
                                + "            \"value\": [\n"
                                + "              \"" + incomingKeyword1 + "\",\n"
                                + "              \"" + incomingKeyword2 + "s\",\n"
                                + "              \"" + incomingKeyword3 + "\"\n"
                                + "            ],\n"
                                + "            \"typeClass\": \"primitive\",\n"
                                + "            \"multiple\": true,\n"
                                + "            \"typeName\": \"keyword\"\n"
                                + "          },\n"
                                + "          {\n"
                                + "            \"value\": [\n"
                                + "              \"Other\"\n"
                                + "            ],\n"
                                + "            \"typeClass\": \"controlledVocabulary\",\n"
                                + "            \"multiple\": true,\n"
                                + "            \"typeName\": \"subject\"\n"
                                + "          }\n"
                                + "        ],\n"
                                + "        \"displayName\": \"Citation Metadata\"\n"
                                + "      }\n"
                                + "    },\n"
                                + "    \"createTime\": \"2014-05-20 11:52:55 -04\",\n"
                                + "    \"UNF\": \"UNF\",\n"
                                + "    \"id\": 1,\n"
                                + "    \"versionNumber\": 1,\n"
                                + "    \"versionMinorNumber\": 0,\n"
                                + "    \"versionState\": \"DRAFT\",\n"
                                + "    \"distributionDate\": \"Distribution Date\",\n"
                                + "    \"productionDate\": \"Production Date\"\n"
                                + "  }\n"
                                + "}";

                        newDatasetJson
                                .add("protocol", incomingProtocol)
                                .add("authority", incomingAuthority)
                                .add("identifier", incomingIdentifier)
                                .add("initialVersion", initialVersion);

                        String parentDataverseId = dvThatWillOwnDataset.getId().toString();
                        String apiKey = dataverseUser.getUserName();
//                        Response response = dataversesApi.createDataset(jsonBody, parentDataverseId, apiKey);
                        Response response = dataversesApi.createDataset(newDatasetJson.build().toString(), parentDataverseId, apiKey);
                        JsonObject jsonResponse = (JsonObject) response.getEntity();
                        String status = jsonResponse.getString("status");
                        JsonNumber idOfDatasetCreated = null;
                        if (status.equals("OK")) {
                            JsonObject data = jsonResponse.getJsonObject("data");
                            idOfDatasetCreated = data.getJsonNumber("id");
                            if (idOfDatasetCreated != null) {
                                Dataset newDataset = datasetService.find(idOfDatasetCreated.longValue());
                                if (newDataset != null) {
                                    ReceiptGenerator receiptGenerator = new ReceiptGenerator();
                                    String baseUrl = urlManager.getHostnamePlusBaseUrlPath(collectionUri);
                                    DepositReceipt depositReceipt = receiptGenerator.createReceipt(baseUrl, newDataset);
                                    return depositReceipt;
                                } else {
                                    throw new SwordError(response.toString() + ":" + response.getStatusInfo() + ":" + jsonResponse.toString() + "\n" + "status: " + status.toString() + "\n" + "idOfDatasetCreated: " + idOfDatasetCreated);
                                }
                            } else {
                                throw new SwordError(response.toString() + ":" + response.getStatusInfo() + ":" + jsonResponse.toString() + "\n" + "status: " + status.toString() + "\n" + "idOfDatasetCreated: " + idOfDatasetCreated);
                            }
                        } else {
                            throw SwordUtil.throwSpecialSwordErrorWithoutStackTrace(UriRegistry.ERROR_BAD_REQUEST, jsonResponse.toString() + newDatasetJson.build().toString());
                        }
                    } else if (deposit.isBinaryOnly()) {
                        // get here with this:
                        // curl --insecure -s --data-binary "@example.zip" -H "Content-Disposition: filename=example.zip" -H "Content-Type: application/zip" https://sword:sword@localhost:8181/dvn/api/data-deposit/v1/swordv2/collection/dataverse/sword/
                        throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Binary deposit to the collection IRI via POST is not supported. Please POST an Atom entry instead.");
                    } else if (deposit.isMultipart()) {
                        // get here with this:
                        // wget https://raw.github.com/swordapp/Simple-Sword-Server/master/tests/resources/multipart.dat
                        // curl --insecure --data-binary "@multipart.dat" -H 'Content-Type: multipart/related; boundary="===============0670350989=="' -H "MIME-Version: 1.0" https://sword:sword@localhost:8181/dvn/api/data-deposit/v1/swordv2/collection/dataverse/sword/hdl:1902.1/12345
                        // but...
                        // "Yeah, multipart is critically broken across all implementations" -- http://www.mail-archive.com/sword-app-tech@lists.sourceforge.net/msg00327.html
                        throw new UnsupportedOperationException("Not yet implemented");
                    } else {
                        throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "expected deposit types are isEntryOnly, isBinaryOnly, and isMultiPart");
                    }
                } else {
                    throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "user " + dataverseUser.getUserName() + " is not authorized to modify dataset");
                }
            } else {
                throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not find dataverse: " + dvAlias);
            }
        } else {
            throw new SwordError(UriRegistry.ERROR_BAD_REQUEST, "Could not determine target type or identifier from URL: " + collectionUri);
        }
    }

}
