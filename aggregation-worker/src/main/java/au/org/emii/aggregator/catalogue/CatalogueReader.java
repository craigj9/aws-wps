package au.org.emii.aggregator.catalogue;


import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class CatalogueReader {
    private static final Logger logger = LoggerFactory.getLogger(CatalogueReader.class);

    private static final String METADATA_PROTOCOL = "WWW:LINK-1.0-http--metadata-URL";
    private static final String CATALOGUE_SEARCH_TEMPLATE = "%s/srv/eng/xml.search.summary?%s=%s&hitsPerPage=1&fast=index";

    private String catalogueUrl;
    private String layerSearchField;

    public CatalogueReader(String catalogueUrl, String layerSearchField) {
        this.catalogueUrl = catalogueUrl;
        this.layerSearchField = layerSearchField;
    }

    public String getMetadataUrl(String layer) {
        try {
            if (catalogueUrl == null || layerSearchField == null) {
                logger.error("Missing configuration: Catalogue URL [" + catalogueUrl + "], Layer search field [" + layerSearchField + "]");
                return "";
            }

            logger.info("Layer name: " + layer);

            //  Strip the imos: off the front of the layer name if it is present
            if(layer.startsWith("imos:")) {
                layer = StringUtils.removeStart(layer, "imos:");
                logger.info("Adjusted layer name: " + layer);
            }

            String searchUrl = String.format(CATALOGUE_SEARCH_TEMPLATE, this.catalogueUrl,
                    this.layerSearchField, layer);

            logger.info("Catalogue search URL: " + searchUrl);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            Document doc;

            try (InputStream inputStream = new URL(searchUrl).openStream()) {
                doc = factory.newDocumentBuilder().parse(inputStream);
            }

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile("//metadata/link['" + METADATA_PROTOCOL + "']");
            NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

            if (nl.getLength() == 0 || nl.item(0) == null) {
                logger.error("No metadata URL found for {}. Nodelist empty.", layer);
                return "";
            }

            String nodeValue = nl.item(0).getTextContent();

            if(nodeValue == null)
            {
                logger.error("No metadata URL found for {}. Empty node.", layer);
                return "";
            }

            String[] linkInfo = nodeValue.split("\\|");

            if (linkInfo.length < 3) {
                logger.error("Invalid link format for {}", layer);
                return "";
            }

            return linkInfo[2];
        } catch (IOException|SAXException|ParserConfigurationException|XPathExpressionException e) {
            logger.error("Could not retrieve metadata URL for {} from catalogue", layer, e);
            return "";
        }
    }
}
