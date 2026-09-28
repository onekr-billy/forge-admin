package com.mdframe.forge.starter.flow.helper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BPMN XML 工具。
 */
public final class BpmnXmlUtils {

    private BpmnXmlUtils() {
    }

    public static NormalizationResult normalizeDuplicateSequenceFlows(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return new NormalizationResult(bpmnXml, List.of());
        }

        try {
            Document document = parseXml(bpmnXml);
            List<DuplicateSequenceFlowRepair> repairs = removeDuplicateSequenceFlows(document);
            if (repairs.isEmpty()) {
                return new NormalizationResult(bpmnXml, repairs);
            }
            return new NormalizationResult(serialize(document), repairs);
        }
        catch (Exception e) {
            throw new RuntimeException("BPMN XML 解析失败：" + e.getMessage(), e);
        }
    }

    public static LegacyMultiInstanceNormalizationResult normalizeLegacyMultiInstanceExpressions(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return new LegacyMultiInstanceNormalizationResult(bpmnXml, List.of());
        }

        try {
            Document document = parseXml(bpmnXml);
            List<String> normalizedNodeIds = removeLegacyMultiInstanceExpressions(document);
            if (normalizedNodeIds.isEmpty()) {
                return new LegacyMultiInstanceNormalizationResult(bpmnXml, normalizedNodeIds);
            }
            return new LegacyMultiInstanceNormalizationResult(serialize(document), normalizedNodeIds);
        }
        catch (Exception e) {
            throw new RuntimeException("BPMN XML 多实例配置解析失败：" + e.getMessage(), e);
        }
    }

    private static List<String> removeLegacyMultiInstanceExpressions(Document document) {
        List<String> normalizedNodeIds = new ArrayList<>();
        NodeList allNodes = document.getElementsByTagName("*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Node node = allNodes.item(i);
            if (!(node instanceof Element element)
                    || !"multiInstanceLoopCharacteristics".equals(localName(element))
                    || !hasFlowableCollection(element)) {
                continue;
            }

            int removed = removeLegacyMultiInstanceChildren(element);
            if (removed > 0) {
                normalizedNodeIds.add(findOwnerNodeId(element));
            }
        }
        return normalizedNodeIds;
    }

    private static boolean hasFlowableCollection(Element element) {
        return !normalizeText(element.getAttribute("flowable:collection")).isBlank()
                || !normalizeText(element.getAttribute("collection")).isBlank();
    }

    private static int removeLegacyMultiInstanceChildren(Element multiInstanceElement) {
        List<Node> nodesToRemove = new ArrayList<>();
        NodeList children = multiInstanceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element childElement)) {
                continue;
            }
            String localName = localName(childElement);
            String expressionText = normalizeExpressionText(childElement.getTextContent());
            if ("loopCardinality".equals(localName) && "${nrOfInstances}".equals(expressionText)) {
                nodesToRemove.add(childElement);
            }
            if ("completionCondition".equals(localName)
                    && "${nrOfCompletedInstances==nrOfInstances}".equals(expressionText)) {
                nodesToRemove.add(childElement);
            }
        }
        nodesToRemove.forEach(BpmnXmlUtils::removeElement);
        return nodesToRemove.size();
    }

    private static String findOwnerNodeId(Element element) {
        Node parent = element.getParentNode();
        if (parent instanceof Element parentElement) {
            String id = normalizeText(parentElement.getAttribute("id"));
            if (!id.isBlank()) {
                return id;
            }
        }
        return "unknown";
    }

    private static List<DuplicateSequenceFlowRepair> removeDuplicateSequenceFlows(Document document) {
        Set<String> explicitFlowRefs = collectExplicitFlowRefs(document);
        Set<String> defaultFlowRefs = collectDefaultFlowRefs(document);
        Set<String> diagramFlowRefs = collectDiagramFlowRefs(document);
        Map<String, List<SequenceFlowInfo>> flowsBySemanticKey = collectSequenceFlows(document);
        List<DuplicateSequenceFlowRepair> repairs = new ArrayList<>();

        for (List<SequenceFlowInfo> flows : flowsBySemanticKey.values()) {
            if (flows.size() <= 1) {
                continue;
            }

            SequenceFlowInfo keep = chooseFlowToKeep(flows, explicitFlowRefs, defaultFlowRefs, diagramFlowRefs);
            List<String> removedIds = new ArrayList<>();
            for (SequenceFlowInfo flow : flows) {
                if (flow == keep) {
                    continue;
                }
                removedIds.add(flow.id);
                removeElement(flow.element);
                removeFlowReferences(document, flow.id);
                removeDiagramEdges(document, flow.id);
            }
            repairs.add(new DuplicateSequenceFlowRepair(
                    keep.id,
                    removedIds,
                    keep.sourceRef,
                    keep.targetRef
            ));
        }

        return repairs;
    }

    private static Map<String, List<SequenceFlowInfo>> collectSequenceFlows(Document document) {
        Map<String, List<SequenceFlowInfo>> result = new LinkedHashMap<>();
        NodeList allNodes = document.getElementsByTagName("*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Node node = allNodes.item(i);
            if (!(node instanceof Element element) || !"sequenceFlow".equals(localName(element))) {
                continue;
            }
            String id = element.getAttribute("id");
            String sourceRef = element.getAttribute("sourceRef");
            String targetRef = element.getAttribute("targetRef");
            if (id == null || id.isBlank() || sourceRef == null || sourceRef.isBlank()
                    || targetRef == null || targetRef.isBlank()) {
                continue;
            }

            String semanticKey = buildSemanticKey(element, sourceRef, targetRef);
            result.computeIfAbsent(semanticKey, key -> new ArrayList<>())
                    .add(new SequenceFlowInfo(id, sourceRef, targetRef, element));
        }
        return result;
    }

    private static String buildSemanticKey(Element element, String sourceRef, String targetRef) {
        String scopeId = findScopeId(element);
        return scopeId + "\u0001"
                + sourceRef + "\u0001"
                + targetRef + "\u0001"
                + buildAttributeSignature(element) + "\u0001"
                + buildChildSignature(element);
    }

    private static String findScopeId(Element element) {
        Node current = element.getParentNode();
        while (current instanceof Element scope) {
            if ("process".equals(localName(scope)) || "subProcess".equals(localName(scope))) {
                String id = scope.getAttribute("id");
                return localName(scope) + ":" + (id == null ? "" : id);
            }
            current = current.getParentNode();
        }
        return "";
    }

    private static String buildAttributeSignature(Element element) {
        NamedNodeMap attrs = element.getAttributes();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String name = attr.getNodeName();
            if ("id".equals(name) || "sourceRef".equals(name) || "targetRef".equals(name)) {
                continue;
            }
            parts.add(name + "=" + normalizeText(attr.getNodeValue()));
        }
        parts.sort(Comparator.naturalOrder());
        return String.join("|", parts);
    }

    private static String buildChildSignature(Element element) {
        List<String> parts = new ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element childElement)) {
                continue;
            }
            parts.add(localName(childElement) + "[" + buildAttributeSignature(childElement) + "]="
                    + normalizeText(childElement.getTextContent()));
        }
        return String.join("|", parts);
    }

    private static SequenceFlowInfo chooseFlowToKeep(List<SequenceFlowInfo> flows,
                                                     Set<String> explicitFlowRefs,
                                                     Set<String> defaultFlowRefs,
                                                     Set<String> diagramFlowRefs) {
        SequenceFlowInfo selected = flows.get(0);
        int selectedScore = score(selected.id, explicitFlowRefs, defaultFlowRefs, diagramFlowRefs);
        for (int i = 1; i < flows.size(); i++) {
            SequenceFlowInfo candidate = flows.get(i);
            int candidateScore = score(candidate.id, explicitFlowRefs, defaultFlowRefs, diagramFlowRefs);
            if (candidateScore > selectedScore) {
                selected = candidate;
                selectedScore = candidateScore;
            }
        }
        return selected;
    }

    private static int score(String flowId,
                             Set<String> explicitFlowRefs,
                             Set<String> defaultFlowRefs,
                             Set<String> diagramFlowRefs) {
        int score = 0;
        if (defaultFlowRefs.contains(flowId)) {
            score += 100;
        }
        if (explicitFlowRefs.contains(flowId)) {
            score += 50;
        }
        if (diagramFlowRefs.contains(flowId)) {
            score += 10;
        }
        return score;
    }

    private static Set<String> collectExplicitFlowRefs(Document document) {
        Set<String> result = new HashSet<>();
        NodeList allNodes = document.getElementsByTagName("*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Node node = allNodes.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            String localName = localName(element);
            if (!"incoming".equals(localName) && !"outgoing".equals(localName)) {
                continue;
            }
            String flowId = normalizeText(element.getTextContent());
            if (!flowId.isBlank()) {
                result.add(flowId);
            }
        }
        return result;
    }

    private static Set<String> collectDefaultFlowRefs(Document document) {
        Set<String> result = new HashSet<>();
        NodeList allNodes = document.getElementsByTagName("*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Node node = allNodes.item(i);
            if (!(node instanceof Element element) || !element.hasAttribute("default")) {
                continue;
            }
            String flowId = normalizeText(element.getAttribute("default"));
            if (!flowId.isBlank()) {
                result.add(flowId);
            }
        }
        return result;
    }

    private static Set<String> collectDiagramFlowRefs(Document document) {
        Set<String> result = new HashSet<>();
        NodeList allNodes = document.getElementsByTagName("*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Node node = allNodes.item(i);
            if (!(node instanceof Element element) || !"BPMNEdge".equals(localName(element))) {
                continue;
            }
            String bpmnElement = normalizeText(element.getAttribute("bpmnElement"));
            if (!bpmnElement.isBlank()) {
                result.add(bpmnElement);
            }
        }
        return result;
    }

    private static void removeFlowReferences(Document document, String flowId) {
        List<Node> nodesToRemove = new ArrayList<>();
        NodeList allNodes = document.getElementsByTagName("*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Node node = allNodes.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            String localName = localName(element);
            if (!"incoming".equals(localName) && !"outgoing".equals(localName)) {
                continue;
            }
            if (flowId.equals(normalizeText(element.getTextContent()))) {
                nodesToRemove.add(element);
            }
        }
        nodesToRemove.forEach(BpmnXmlUtils::removeElement);
    }

    private static void removeDiagramEdges(Document document, String flowId) {
        List<Node> nodesToRemove = new ArrayList<>();
        NodeList allNodes = document.getElementsByTagName("*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Node node = allNodes.item(i);
            if (!(node instanceof Element element) || !"BPMNEdge".equals(localName(element))) {
                continue;
            }
            if (flowId.equals(normalizeText(element.getAttribute("bpmnElement")))) {
                nodesToRemove.add(element);
            }
        }
        nodesToRemove.forEach(BpmnXmlUtils::removeElement);
    }

    private static void removeElement(Node node) {
        Node parent = node.getParentNode();
        if (parent != null) {
            parent.removeChild(node);
        }
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        // Xerces/JAXP implementations bundled with older Flowable runtimes do not
        // recognize the JAXP 1.5 external-access properties.  These properties are
        // defense-in-depth (DOCTYPE and external entities are disabled above), so an
        // implementation that does not expose them must not make an otherwise valid
        // BPMN deployment fail with "property ... is not recognized".
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private static void setAttributeIfSupported(Object factory, String name, Object value) {
        try {
            if (factory instanceof DocumentBuilderFactory documentBuilderFactory) {
                documentBuilderFactory.setAttribute(name, value);
            } else if (factory instanceof TransformerFactory transformerFactory) {
                transformerFactory.setAttribute(name, value);
            }
        } catch (IllegalArgumentException ignored) {
            // Optional JAXP hardening property is unavailable in this parser/provider.
            // Keep the mandatory entity/DOCTYPE protections and continue parsing.
        }
    }

    private static String localName(Element element) {
        String nodeName = element.getNodeName();
        int idx = nodeName.indexOf(':');
        return idx >= 0 ? nodeName.substring(idx + 1) : nodeName;
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private static String normalizeExpressionText(String text) {
        return normalizeText(text).replace(" ", "");
    }

    private static final class SequenceFlowInfo {
        private final String id;
        private final String sourceRef;
        private final String targetRef;
        private final Element element;

        private SequenceFlowInfo(String id, String sourceRef, String targetRef, Element element) {
            this.id = id;
            this.sourceRef = sourceRef;
            this.targetRef = targetRef;
            this.element = element;
        }
    }

    public static final class NormalizationResult {
        private final String bpmnXml;
        private final List<DuplicateSequenceFlowRepair> repairs;

        private NormalizationResult(String bpmnXml, List<DuplicateSequenceFlowRepair> repairs) {
            this.bpmnXml = bpmnXml;
            this.repairs = repairs;
        }

        public String getBpmnXml() {
            return bpmnXml;
        }

        public List<DuplicateSequenceFlowRepair> getRepairs() {
            return repairs;
        }

        public boolean hasRepairs() {
            return repairs != null && !repairs.isEmpty();
        }
    }

    public static final class LegacyMultiInstanceNormalizationResult {
        private final String bpmnXml;
        private final List<String> normalizedNodeIds;

        private LegacyMultiInstanceNormalizationResult(String bpmnXml, List<String> normalizedNodeIds) {
            this.bpmnXml = bpmnXml;
            this.normalizedNodeIds = normalizedNodeIds;
        }

        public String getBpmnXml() {
            return bpmnXml;
        }

        public List<String> getNormalizedNodeIds() {
            return normalizedNodeIds;
        }

        public boolean hasRepairs() {
            return normalizedNodeIds != null && !normalizedNodeIds.isEmpty();
        }
    }

    public static final class DuplicateSequenceFlowRepair {
        private final String keptFlowId;
        private final List<String> removedFlowIds;
        private final String sourceRef;
        private final String targetRef;

        private DuplicateSequenceFlowRepair(String keptFlowId, List<String> removedFlowIds, String sourceRef, String targetRef) {
            this.keptFlowId = keptFlowId;
            this.removedFlowIds = removedFlowIds;
            this.sourceRef = sourceRef;
            this.targetRef = targetRef;
        }

        public String getKeptFlowId() {
            return keptFlowId;
        }

        public List<String> getRemovedFlowIds() {
            return removedFlowIds;
        }

        public String getSourceRef() {
            return sourceRef;
        }

        public String getTargetRef() {
            return targetRef;
        }
    }
}
