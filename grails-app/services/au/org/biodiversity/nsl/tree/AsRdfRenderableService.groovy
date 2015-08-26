/*
    Copyright 2015 Australian National Botanic Gardens

    This file is part of NSL tree services plugin project.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy
    of the License at http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package au.org.biodiversity.nsl.tree

import au.org.biodiversity.nsl.*
import grails.transaction.Transactional
import org.hibernate.SessionFactory
import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError

/**
 * Convert various dsl objects to rdf.
 * The point of this class is that it should be more-or-less the only place that cares about the RDF vocabulary.
 */


@Transactional
class AsRdfRenderableService {
    ///////////////////////////////////////////////////////////////////////////////

    static datasource = 'nsl'

    SessionFactory sessionFactory_nsl
    QueryService queryService

    private static enum NodeRenderContext {
        top_node, root_node, sub_node, prevnext, ids_only, in_link
    }

    public RdfRenderable.Top springErrorsAsRenderable(Errors e) {

        UriNs springErrorNs = DomainUtils.getBlankNs()

        RdfRenderable.Obj o = new RdfRenderable.Obj(DomainUtils.u(springErrorNs, 'ValidationErrors'))

        if (!e) return o

        e.allErrors.each { ObjectError ee ->
            RdfRenderable.Obj oo

            if (ee instanceof FieldError) {
                oo = new RdfRenderable.Obj(DomainUtils.u(springErrorNs, 'FieldError'))
                oo.addMulti DomainUtils.uri('rdf', 'type'), DomainUtils.u(springErrorNs, 'ObjectError')
            } else {
                oo = new RdfRenderable.Obj(DomainUtils.u(springErrorNs, 'ObjectError'))
            }

            oo.add(DomainUtils.u(springErrorNs, 'objectName'), ee.getObjectName())
            oo.add(DomainUtils.u(springErrorNs, 'code'), ee.getCode())
            ee.getCodes().each { oo.addMulti(DomainUtils.u(springErrorNs, 'codes'), it) }
            oo.add(DomainUtils.u(springErrorNs, 'defaultMessage'), ee.getDefaultMessage())

            RdfRenderable.Seq args = new RdfRenderable.Seq()
            for (int i = 0; i < ee.getArguments().length; i++) {
                args.set(i + 1, new RdfRenderable.Primitive(ee.getArguments()[i].toString()))
            }
            oo.add(DomainUtils.u(springErrorNs, 'arguments'), args)

            if (ee instanceof FieldError) {
                FieldError eee = (FieldError) ee

                oo.add(DomainUtils.u(springErrorNs, 'field'), eee.getField())
                oo.add(DomainUtils.u(springErrorNs, 'rejectedValue'), (eee.getRejectedValue() ?: '').toString())
                oo.add(DomainUtils.u(springErrorNs, 'isBindingFailure'), eee.isBindingFailure())
            }

            o.addMulti DomainUtils.u(springErrorNs, 'hasError'), oo
        }

        return o
    }

    public RdfRenderable.Top serviceExceptionAsRenderable(ServiceException ex) {
        UriNs errorNs = DomainUtils.getBlankNs()
        RdfRenderable.Obj o = new RdfRenderable.Obj(DomainUtils.u(errorNs, ex.class.simpleName))
        RdfRenderable.Seq stackTrace = new RdfRenderable.Seq()
        o.add DomainUtils.u(errorNs, 'stackTrace'), stackTrace

        for (StackTraceElement ee : ex.getStackTrace()) {
            if (!ee.getClassName()
                    || !ee.getClassName().startsWith('au.org.biodiversity.')
                    || ee.getClassName().startsWith('au.org.biodiversity.nsl.tree.ServiceException')
                    || ee.getLineNumber() == -1
                    || !ee.getMethodName()
                    || ee.getMethodName().startsWith('$'))
                continue;

            RdfRenderable.Obj oo = new RdfRenderable.Obj(DomainUtils.u(errorNs, 'StackTraceElement'))
            stackTrace.add(oo)
            if (ee.getFileName()) oo.add DomainUtils.u(errorNs, 'fileName'), ee.getFileName()
            if (ee.getLineNumber()) oo.add DomainUtils.u(errorNs, 'lineNumber'), ee.getLineNumber()
            if (ee.getClassName()) oo.add DomainUtils.u(errorNs, 'className'), ee.getClassName()
            if (ee.getMethodName()) oo.add DomainUtils.u(errorNs, 'methodName'), ee.getMethodName()
        }

        o.add DomainUtils.u(errorNs, 'message'), messageAsRenderable(ex.msg)

        return o
    }

    public RdfRenderable.Top messageAsRenderable(Message m) {
        UriNs messageNs = DomainUtils.getBlankNs()
        RdfRenderable.Obj o = new RdfRenderable.Obj(DomainUtils.u(messageNs, 'Message'))

        o.add(DomainUtils.uri('rdfs', 'label'), m.msg.name())
        o.add(DomainUtils.uri('dcterms', 'title'), m.getLocalisedString())

        o.add DomainUtils.u(messageNs, 'messageKey'), m.msg.getKey()

        RdfRenderable.Seq args = new RdfRenderable.Seq()
        RdfRenderable.Seq nested = new RdfRenderable.Seq()
        o.add DomainUtils.u(messageNs, 'args'), args
        o.add DomainUtils.u(messageNs, 'nested'), nested

        for (Object oo : m.args) {
            if(RdfRenderable.Primitive.isPrimative(oo)) {
                //noinspection GroovyAssignabilityCheck
                args.add new RdfRenderable.Primitive(oo)
            } else {
                try {
                    //noinspection GroovyAssignabilityCheck
                    addToArgs(args, oo)
                } catch (MissingMethodException e1) {
                    log.warn("RdfRenderable tryed to add arg for type $oo ($e1.message)")
                }
            }
        }

        for (Object perhapsAMessage : m.nested) {
            if(perhapsAMessage instanceof Message){
                nested.add messageAsRenderable(perhapsAMessage as Message)
            }
        }

        return o
    }

    private addToArgs(RdfRenderable.Seq args, Node node) {
        node.refresh()
        args.add(getNodeRdf(node))
    }

    private addToArgs(RdfRenderable.Seq args, Link link) {
        link.refresh()
        args.add(getLinkRdf(link))
    }

    private addToArgs(RdfRenderable.Seq args, Arrangement arrangement) {
        arrangement.refresh()
        if (arrangement.arrangementType.isTree()) {
            args.add(getClassificationRdf(arrangement))
        } else {
            args.add(getArrangementRdf(arrangement))
        }
    }

    private addToArgs(RdfRenderable.Seq args, Message message) {
        args.add(messageAsRenderable(message))
    }

    private addToArgs(RdfRenderable.Seq args, Uri uri) {
        args.add(new RdfRenderable.Resource(uri))
    }

    ///////////////////////////////////////////////////////////////////////////////


    public RdfRenderable.Top getNodeRdf(Map params = [:], Node n) {
        return nodeAsRenderable(n,
                params['idsOnly'] ? NodeRenderContext.ids_only : NodeRenderContext.top_node,
                (Boolean) params['all'],
                (Integer) params['depth'])
    }

    private RdfRenderable.Obj nodeAsRenderable(Node n, NodeRenderContext ctx, Boolean allSubnodes, Integer depth) {
        if (n == null) throw new IllegalArgumentException('n==null')
        if (ctx == null) throw new IllegalArgumentException('ctx==null')

        if (allSubnodes == null) allSubnodes = false
        if (depth == null) depth = 0


        RdfRenderable.Obj o = new RdfRenderable.Obj(DomainUtils.getNodeTypeUri(n), DomainUtils.getNodeUri(n))

        o.addMulti(DomainUtils.uri('rdf', 'type'), DomainUtils.getBoatreeUri('Node'))

        if (DomainUtils.isEndNode(n)) {
            o.addMulti(DomainUtils.uri('rdf', 'type'), DomainUtils.getBoatreeUri('EndNode'))
        } else {
            o.add(DomainUtils.getBoatreeUri('nodeId'), n.id)
        }

        if (ctx == NodeRenderContext.top_node) {
            if (n.prev != null) o.add(DomainUtils.getBoatreeUri('prevVersion'), nodeAsRenderable(n.prev, NodeRenderContext.prevnext, false, 0))
            if (n.next != null) o.add(DomainUtils.getBoatreeUri('nextVersion'), nodeAsRenderable(n.next, NodeRenderContext.prevnext, false, 0))
        }

        if (ctx == NodeRenderContext.top_node) {
            o.add(DomainUtils.getBoatreeUri('nodeRoot'), arrangementAsRenderable(n.root, ArrangementRenderContext.in_top_node, false, 0))
        } else if (ctx != NodeRenderContext.root_node) {
            o.add(DomainUtils.getBoatreeUri('nodeRoot'), DomainUtils.getArrangementUri(n.root))
        }

        if (DomainUtils.hasName(n)) {
            o.add(DomainUtils.getBoatreeUri('nodeName'), DomainUtils.getNameUri(n))
            if (n.nameUriNsPart) {
                o.add(DomainUtils.getBoatreeUri('nodeNameNs'), n.nameUriNsPart.label)
            }
            o.add(DomainUtils.getBoatreeUri('nodeNameId'), n.nameUriIdPart)
        }

        if (DomainUtils.hasTaxon(n)) {
            o.add(DomainUtils.getBoatreeUri('nodeTaxon'), DomainUtils.getTaxonUri(n))
            if (n.taxonUriNsPart) {
                o.add(DomainUtils.getBoatreeUri('nodeTaxonNs'), n.taxonUriNsPart.label)
            }
            o.add(DomainUtils.getBoatreeUri('nodeTaxonId'), n.taxonUriIdPart)
        }

        if (ctx != NodeRenderContext.ids_only) {
            o.add(DomainUtils.getBoatreeUri('isSynthetic'), n.isSynthetic())
            o.add(DomainUtils.getBoatreeUri('isCheckedIn'), DomainUtils.isCheckedIn(n))
            o.add(DomainUtils.getBoatreeUri('isCurrent'), DomainUtils.isCurrent(n))
            o.add(DomainUtils.getBoatreeUri('isEndNode'), DomainUtils.isEndNode(n))
        }

        if (ctx == NodeRenderContext.top_node
                || ctx == NodeRenderContext.root_node
                || (ctx == NodeRenderContext.sub_node && (allSubnodes || depth > 1))) {
            RdfRenderable.Seq childLinks = new RdfRenderable.Seq()

            for (Link l : n.subLink) {
                childLinks.set(l.linkSeq,
                        linkAsRenderable(l, NodeRenderContext.sub_node, allSubnodes, depth > 0 ? depth - 1 : 0)
                )
            }

            o.add(DomainUtils.getBoatreeUri('placements'), childLinks)
        }

        if (ctx == NodeRenderContext.top_node) {
            for (Link l : n.supLink) {
                if (!DomainUtils.isCurrent(l.supernode) || l.supernode.root != n.root)
                    continue

                RdfRenderable.Obj parentLink = new RdfRenderable.Obj(DomainUtils.getLinkTypeUri(l))
                parentLink.addMulti(DomainUtils.uri('rdf', 'type'), DomainUtils.getBoatreeUri('Placement'))
                parentLink.add(DomainUtils.getBoatreeUri("supernode"), nodeAsRenderable(l.supernode, NodeRenderContext.prevnext, false, 0))
                parentLink.add(DomainUtils.getBoatreeUri("subnode"), DomainUtils.getNodeUri(n))
                parentLink.add(DomainUtils.getBoatreeUri("placementSeq"), new RdfRenderable.Primitive(l.getLinkSeq()))
                parentLink.add(DomainUtils.getBoatreeUri("isSynthetic"), new RdfRenderable.Primitive(l.isSynthetic()))
                o.addMulti(DomainUtils.getBoatreeUri('placedIn'), parentLink)
            }


        }

        return o
    }

    ///////////////////////////////////////////////////////////////////////////////

    public RdfRenderable.Top getLinkRdf(Map params = [:], Link l) {
        return linkAsRenderable(l, params['idsOnly'] ? NodeRenderContext.ids_only : NodeRenderContext.in_link, false, 0)
    }

    private RdfRenderable.Top linkAsRenderable(Link l, NodeRenderContext ctx, Boolean allSubnodes, Integer depth) {
        if (l == null) throw new IllegalArgumentException('l==null')
        if (ctx == null) throw new IllegalArgumentException('ctx==null')

        RdfRenderable.Obj childLink = new RdfRenderable.Obj(DomainUtils.getLinkTypeUri(l))
        childLink.addMulti(DomainUtils.uri('rdf', 'type'), DomainUtils.getBoatreeUri('Placement'))
        childLink.add(DomainUtils.getBoatreeUri("placementSeq"), new RdfRenderable.Primitive(l.getLinkSeq()))

        if (ctx == NodeRenderContext.ids_only) {
            RdfRenderable.Obj node = nodeAsRenderable(l.subnode, ctx, false, 0)
            node.add(DomainUtils.getBoatreeUri("placedIn"), childLink)
            return node
        } else {
            childLink.add(DomainUtils.getBoatreeUri("supernode"), DomainUtils.getNodeUri(l.supernode))
            childLink.add(DomainUtils.getBoatreeUri("subnode"), nodeAsRenderable(l.subnode, ctx, allSubnodes, depth))
            childLink.add(DomainUtils.getBoatreeUri("isSynthetic"), new RdfRenderable.Primitive(l.isSynthetic()))
            return childLink
        }
    }

    ///////////////////////////////////////////////////////////////////////////////

    private static enum ArrangementRenderContext {
        as_classification, as_arrangement, in_top_node

        boolean useTreeIdIfPossible() {
            return this != as_arrangement
        }
    }

    public RdfRenderable.Top getArrangementRdf(Map params = [:], Arrangement a) {
        return arrangementAsRenderable(a, ArrangementRenderContext.as_arrangement, (Boolean) params['all'], (Integer) params['depth'])
    }

    public RdfRenderable.Top getClassificationRdf(Map params = [:], Arrangement a) {
        return arrangementAsRenderable(a, ArrangementRenderContext.as_classification, (Boolean) params['all'], (Integer) params['depth'])
    }

    private RdfRenderable.Obj arrangementAsRenderable(Arrangement r, ArrangementRenderContext ctx, Boolean allSubnodes, Integer depth) {
        if (r == null) throw new IllegalArgumentException('r==null')
        if (ctx == null) throw new IllegalArgumentException('ctx==null')

        if (allSubnodes == null) allSubnodes = false
        if (depth == null) depth = 0

        RdfRenderable.Obj o

        if (ctx.useTreeIdIfPossible() && r.label) {
            o = new RdfRenderable.Obj(DomainUtils.getArrangementTypeUri(r.arrangementType), DomainUtils.getTreeUri(r))
            o.add(DomainUtils.uri('owl', 'sameAs'), DomainUtils.getArrangementUri(r))
        } else {
            o = new RdfRenderable.Obj(DomainUtils.getArrangementTypeUri(r.arrangementType), DomainUtils.getArrangementUri(r))
            if (r.label) {
                o.add(DomainUtils.uri('owl', 'sameAs'), DomainUtils.getTreeUri(r))
            }
        }

        o.addMulti(DomainUtils.uri('rdf', 'type'), DomainUtils.getBoatreeUri('Root'))

        if (DomainUtils.isEndTree(r)) {
            o.addMulti(DomainUtils.uri('rdf', 'type'), DomainUtils.getBoatreeUri('EndTree'))
        } else {
            o.add(DomainUtils.getBoatreeUri('arrangementId'), r.id)
            if (r.label) {
                o.add(DomainUtils.getBoatreeUri('classificationId'), r.label)
                o.addMulti(DomainUtils.uri('rdf', 'type'), DomainUtils.getBoatreeUri('Tree'))
            }
        }


        if (r.label) o.add(DomainUtils.uri('rdfs', 'label'), r.label)

        if (ctx == ArrangementRenderContext.in_top_node) {
            o.add(DomainUtils.getBoatreeUri('rootNode'), DomainUtils.getNodeUri(r.node))
        } else {
            o.add(DomainUtils.getBoatreeUri('rootNode'), nodeAsRenderable(r.node, NodeRenderContext.root_node, allSubnodes, depth))
        }

        o.add(DomainUtils.getBoatreeUri('isEndTree'), DomainUtils.isEndTree(r))

        if (ctx != ArrangementRenderContext.in_top_node) {

            QueryService.Statistics stats = queryService.getStatistics(r)

            o.add(DomainUtils.getBoatreeUri('nodesCt'), stats.nodesCt)
            o.add(DomainUtils.getBoatreeUri('typesCt'), stats.typesCt)
            o.add(DomainUtils.getBoatreeUri('namesCt'), stats.namesCt)
            o.add(DomainUtils.getBoatreeUri('taxaCt'), stats.taxaCt)
            o.add(DomainUtils.getBoatreeUri('currentNodesCt'), stats.currentNodesCt)
            o.add(DomainUtils.getBoatreeUri('currentTypesCt'), stats.currentTypesCt)
            o.add(DomainUtils.getBoatreeUri('currentNamesCt'), stats.currentNamesCt)
            o.add(DomainUtils.getBoatreeUri('currentTaxaCt'), stats.currentTaxaCt)

            for (Arrangement aa : stats.dependsOn) {
                o.addMulti(DomainUtils.getBoatreeUri('dependsOnArrangement'), //
                        aa.arrangementType.isTree() && ctx == ArrangementRenderContext.as_classification ?
                                DomainUtils.getTreeUri(aa)
                                :
                                DomainUtils.getArrangementUri(aa))

            }

            for (Arrangement aa : stats.dependants) {
                o.addMulti(DomainUtils.getBoatreeUri('dependantArrangement'), //
                        aa.arrangementType.isTree() && ctx == ArrangementRenderContext.as_classification ?
                                DomainUtils.getTreeUri(aa)
                                :
                                DomainUtils.getArrangementUri(aa))

            }
        }

        return o
    }

    ///////////////////////////////////////////////////////////////////////////////

    public static enum VocItem {
        voc('Vocabularies'),
        ns('RDF Namespaces'),
        classification('Top-level public classification trees'),
        dependencies('Dependencies between trees')

        public final String title

        private VocItem(String title) {
            this.title = title
        }
    }

    public RdfRenderable.Top getVocRdf(VocItem vocItem) {
        Closure getItems
        Closure toRenderable

        switch (vocItem) {
            case VocItem.ns:
                getItems = { UriNs.getAll() }
                toRenderable = { UriNs ns -> vocUriNsAsRenderable(ns) }
                break
            case VocItem.classification:
                getItems = { Arrangement.findAllByArrangementType(ArrangementType.P) }
                toRenderable = { Arrangement r -> vocTreeAsRenderable(r) }
                break
            case VocItem.dependencies:
                getItems = { Arrangement.getAll() }
                toRenderable = { Arrangement r -> vocArrangementDependencies(r) }
                break
            case VocItem.voc:
                getItems = { EnumSet.allOf(VocItem.class) }
                toRenderable = { VocItem v -> vocVocAsRenderable(v) }
                break
            default:
                return null
        }

        return vocItemsAsRenderable(vocItem, getItems, toRenderable)
    }

    private static RdfRenderable.Bag vocItemsAsRenderable(VocItem vocItem, Closure getItems, Closure toRenderable) {
        RdfRenderable.Bag v = new RdfRenderable.Bag()

        if (vocItem != VocItem.voc) {
            RdfRenderable.Obj ont = new RdfRenderable.Obj(DomainUtils.uri('owl', 'Ontology'), DomainUtils.u(DomainUtils.getVocNs(), vocItem.name()))
            ont.add(DomainUtils.uri('rdfs', 'isDefinedBy'), DomainUtils.getVocNs().ownerUriIdPart)
            ont.add(DomainUtils.uri('rdfs', 'label'), vocItem.name())
            v.add(ont)
        }

        for (def vv : getItems()) {
            v.add(toRenderable(vv))
        }
        return v
    }

    private static RdfRenderable.Obj vocUriNsAsRenderable(UriNs ns) {
        if (DomainUtils.isBlank(ns)) return null
        RdfRenderable.Obj o = new RdfRenderable.Obj(DomainUtils.getBoatreeUri('UriNs'), DomainUtils.getNamespaceUri(ns))

        o.add(DomainUtils.getBoatreeUri('nsUriPrefix'), ns.uri)

        if (DomainUtils.isBoatree(ns)) {
            o.add(DomainUtils.uri('rdfs', 'isDefinedBy'), DomainUtils.getOwnerUri(DomainUtils.getBoatreeNs()))
        } else {
            o.add(DomainUtils.uri('rdfs', 'isDefinedBy'), DomainUtils.getOwnerUri(DomainUtils.getNamespaceNs()))
        }

        if (ns.label) o.add(DomainUtils.uri('rdfs', 'label'), ns.label)
        if (ns.title) o.add(DomainUtils.uri('dcterms', 'title'), ns.title)
        if (ns.description) o.add(DomainUtils.uri('dcterms', 'description'), ns.description)

        if (ns.getOwnerUri()) {
            o.add(DomainUtils.getBoatreeUri('owningOntolgy'), ns.ownerUriIdPart)
        }

        return o
    }

    private static RdfRenderable.Obj vocTreeAsRenderable(Arrangement r) {
        RdfRenderable.Obj o = new RdfRenderable.Obj(DomainUtils.getArrangementTypeUri(r.arrangementType), DomainUtils.getTreeUri(r))
        Uri idUri = DomainUtils.getArrangementUri(r)
        o.add(DomainUtils.uri('rdfs', 'sameAs'), idUri)
        o.add(DomainUtils.uri('rdfs', 'isDefinedBy'), DomainUtils.getTreeUri(r).nsPart.ownerUriIdPart)

        return o
    }

    private static RdfRenderable.Obj vocVocAsRenderable(VocItem vocItem) {
        RdfRenderable.Obj ont = new RdfRenderable.Obj(DomainUtils.uri('owl', 'Ontology'), DomainUtils.u(DomainUtils.getVocNs(), vocItem.name()))
        ont.add(DomainUtils.uri('rdfs', 'isDefinedBy'), DomainUtils.getVocNs().ownerUriIdPart)
        ont.add(DomainUtils.uri('rdfs', 'label'), vocItem.name())
        ont.add(DomainUtils.uri('dcterms', 'title'), vocItem.title)
        return ont
    }

    private RdfRenderable.Obj vocArrangementDependencies(Arrangement r) {
        RdfRenderable.Obj o = new RdfRenderable.Obj(DomainUtils.getArrangementTypeUri(r.arrangementType), DomainUtils.getSafeUri(r))

        QueryService.Statistics stats = queryService.getDependencies(r)

        for (Arrangement aa : stats.dependsOn) {
            o.addMulti(DomainUtils.getBoatreeUri('dependsOnArrangement'), DomainUtils.getSafeUri(aa))

        }

        for (Arrangement aa : stats.dependants) {
            o.addMulti(DomainUtils.getBoatreeUri('dependantArrangement'), DomainUtils.getSafeUri(aa))

        }

        return o
    }
}
