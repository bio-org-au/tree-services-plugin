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

import au.org.biodiversity.nsl.Event
import au.org.biodiversity.nsl.Node
import au.org.biodiversity.nsl.NodeInternalType
import au.org.biodiversity.nsl.VersioningMethod
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.SessionFactory
import spock.lang.Specification

import javax.sql.DataSource

@Mixin(BuildSampleTreeMixin)
class DeleteHistorySpec extends Specification {
    DataSource dataSource_nsl
    SessionFactory sessionFactory_nsl
    static final Log log = LogFactory.getLog(DeleteHistorySpec.class)

    // fields
    BasicOperationsService basicOperationsService
    VersioningService versioningService

    // fixture methods

    def setup() {
    }

    def cleanup() {
    }

    def setupSpec() {
    }

    def cleanupSpec() {
    }

    // feature methods

    void "test simple case"() {
        // this will be very, very simple

        Event e = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test simple case')

        SomeStuffEmptyTree s = makeSampleEmptyTree()
        SomeStuffEmptyTree ws = makeSampleEmptyTree()

        Node versionZero = null
        Node versionOne = null
        Node versionTwo = null
        Node versionThree = null

        Closure reset = {
            sessionFactory_nsl.currentSession.clear()
            s.reloadWithoutClear()
            ws.reloadWithoutClear()

            if (versionZero != null) versionZero = Node.get(versionZero.id)
            if (versionOne != null) versionOne = Node.get(versionOne.id)
            if (versionTwo != null) versionTwo = Node.get(versionTwo.id)
            if (versionThree != null) versionThree = Node.get(versionThree.id)
        }

        when:

        versionZero = basicOperationsService.createDraftNode(s.root, VersioningMethod.T, NodeInternalType.T, name: DomainUtils.getStandaloneUri('ROOT'))
        reset()

        basicOperationsService.persistNode(e, s.root)
        reset()

        BuildSampleTreeUtil.dumpStuff(sessionFactory_nsl, log, [s, ws])

        basicOperationsService.adoptNode(ws.t.node, versionZero, VersioningMethod.V)
        reset()

        versionOne = basicOperationsService.checkoutNode(ws.t.node, versionZero)
        reset()

        basicOperationsService.persistNode(e, versionOne)
        reset()

        Map<Node, Node> v = new HashMap<Node, Node>()
        v.clear()
        v.put(versionZero, versionOne)
        versioningService.performVersioning(e, v, ws.t)
        reset()

        basicOperationsService.moveFinalNodesFromTreeToTree ws.t, s.t
        reset()

        BuildSampleTreeUtil.dumpStuff(sessionFactory_nsl, log, [s, ws])

        versionTwo = basicOperationsService.checkoutNode(ws.t.node, versionOne)
        reset()

        basicOperationsService.persistNode(e, versionTwo)
        reset()

        v.clear()
        v.put(versionOne, versionTwo)
        versioningService.performVersioning(e, v, ws.t)
        reset()
        basicOperationsService.moveFinalNodesFromTreeToTree ws.t, s.t
        reset()

        versionThree = basicOperationsService.checkoutNode(ws.t.node, versionTwo)
        reset()

        basicOperationsService.persistNode(e, versionThree)
        reset()

        v.clear()
        v.put(versionTwo, versionThree)
        versioningService.performVersioning(e, v, ws.t)
        reset()

        basicOperationsService.moveFinalNodesFromTreeToTree ws.t, s.t
        reset()

        BuildSampleTreeUtil.dumpStuff sessionFactory_nsl, log, [s, ws]

        versioningService.deleteHistory(versionTwo)
        reset()

        BuildSampleTreeUtil.dumpStuff sessionFactory_nsl, log, [s, ws]

        then:
        1 == 1

    }
}
