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

package treeServices

import au.org.biodiversity.nsl.Node
import au.org.biodiversity.nsl.tree.QueryService

class TreeServicesTagLib {
    static defaultEncodeAs = [taglib: 'raw']
    static namespace = "tree"

    QueryService queryService;

    //static encodeAsForTags = [tagName: [taglib:'html'], otherTagName: [taglib:'none']]

    def getNodeNameAndInstance = { attrs, body ->
        Node node = attrs.node;
        out << body(name: queryService.resolveName(node), instance: queryService.resolveInstance(node))
    }


}
