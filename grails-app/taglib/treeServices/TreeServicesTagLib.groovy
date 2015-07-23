package treeServices

import au.org.biodiversity.nsl.Event
import au.org.biodiversity.nsl.Node
import au.org.biodiversity.nsl.tree.QueryService

class TreeServicesTagLib {
    static defaultEncodeAs = [taglib: 'raw']
    static namespace = "tree"

    QueryService queryService;

    //static encodeAsForTags = [tagName: [taglib:'html'], otherTagName: [taglib:'none']]

    def getEventInfo = { attrs, body ->
        Event event = attrs.event;
        def info = queryService.getEventInfo(event);
        out << body(eventInfo: info)
    }

    def getNodeNameAndInstance = { attrs, body ->
        Node node = attrs.node;
        out << body(name: queryService.resolveName(node), instance: queryService.resolveInstance(node))
    }


}
