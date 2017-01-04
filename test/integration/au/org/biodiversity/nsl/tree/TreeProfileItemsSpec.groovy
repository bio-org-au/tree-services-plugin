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

import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.Event
import au.org.biodiversity.nsl.Link
import au.org.biodiversity.nsl.Node
import org.hibernate.SessionFactory
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Timestamp

import static au.org.biodiversity.nsl.tree.DomainUtils.*

/**
 * Created by ibis on 11/12/14.
 */
class TreeProfileItemsSpec extends Specification {
    DataSource dataSource_nsl
    SessionFactory sessionFactory_nsl
    BasicOperationsService basicOperationsService
    QueryService queryService
    TreeOperationsService treeOperationsService

    def "create and modify profile data"() {
        when: "create a node"

        Event e = basicOperationsService.newEventTs(TreeTestUtil.getTestNamespace(), new Timestamp(System.currentTimeMillis()), 'TEST', 'create and modify profile data')
        Arrangement t = basicOperationsService.createClassification(e, 'test', 'test', true)

        // I am using LSIDs for the sample uris just to annoy Greg if he ever finds out

        Uri pBack = u(getBlankNs(), 'urn:lsid:example.edu:colors.voc:hasBackgroundColor')
        Uri pFore = u(getBlankNs(), 'urn:lsid:example.edu:colors.voc:hasForegroundColor')
        Uri pHi = u(getBlankNs(), 'urn:lsid:example.edu:colors.voc:hasHighlightColor')

        Uri tColor = u(getBlankNs(), 'urn:lsid:example.edu:colors.voc:Color')

        Uri cRed = u(getBlankNs(), 'urn:lsid:example.edu:colors:red')
        Uri cGreen = u(getBlankNs(), 'urn:lsid:example.edu:colors:green')
        Uri cBlue = u(getBlankNs(), 'urn:lsid:example.edu:colors:blue')

        Uri nameUri = u(getBlankNs(), 'urn:lsid:example.edu:name:Zebra')

        Map<Uri, Map> props = [:]

        props[pFore] = [resource: cRed, type: tColor]
        props[pBack] = [resource: cGreen, type: tColor]

        Node n = treeOperationsService.addName(t, nameUri, (Uri) null, (Uri) null, e.authUser, props, foo: 'bar')

        Map<Uri, Link> pp = getProfileItemsAsMap(n)
        pBack = refetchUri(pBack)
        pFore = refetchUri(pFore)
        pHi = refetchUri(pHi)
        tColor = refetchUri(tColor)
        cRed = refetchUri(cRed)
        cGreen = refetchUri(cGreen)
        cBlue = refetchUri(cBlue)
        nameUri = refetchUri(nameUri)

        then:
        n
        getResourceUri(pp[pFore]?.subnode) == cRed
        getResourceUri(pp[pBack]?.subnode) == cGreen
        !pp[pHi]

        when: "update profile data"
        props.clear()
        props[pHi] = [resource: cBlue, type: tColor]
        props[pBack] = null

        n = treeOperationsService.updateName(t, nameUri, (Uri) null, (Uri) null, e.authUser, props, foo: 'bar')

        pp = getProfileItemsAsMap(n)

        pBack = refetchUri(pBack)
        pFore = refetchUri(pFore)
        pHi = refetchUri(pHi)
        tColor = refetchUri(tColor)
        cRed = refetchUri(cRed)
        cGreen = refetchUri(cGreen)
        cBlue = refetchUri(cBlue)
        nameUri = refetchUri(nameUri)

        then:
        n
        getResourceUri(pp[pFore]?.subnode) == cRed
        !pp[pBack]
        getResourceUri(pp[pHi]?.subnode) == cBlue
    }

}
