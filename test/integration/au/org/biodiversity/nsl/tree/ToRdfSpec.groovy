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

import javax.sql.DataSource;

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.hibernate.SessionFactory;

import spock.lang.Specification
import au.org.biodiversity.nsl.Arrangement
import au.org.biodiversity.nsl.Event

@Mixin(BuildSampleTreeMixin)
class ToRdfSpec extends Specification {
	DataSource dataSource_nsl
	SessionFactory sessionFactory_nsl
	static final Log log = LogFactory.getLog(ToRdfSpec.class)

	// fields
	BasicOperationsService basicOperationsService
	AsRdfRenderableService asRdfRenderableService

	def setup() {
	}

	def cleanup() {
	}

	def setupSpec() {
	}

	def cleanupSpec() {
	}

	public void 'test simple tree'() {
		when:
		Event e = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test simple tree')
		SomeStuff s = makeSampleTree()
		s.reload()
		RdfRenderable.Top rdf = asRdfRenderableService.getNodeRdf s.root, all: true

		then:
		rdf
	}

	public void 'test simple arrangement'() {
		when:
		Event e = basicOperationsService.newEventTs(new java.sql.Timestamp(System.currentTimeMillis()), 'TEST', 'test simple arrangement')
		SomeStuff s = makeSampleTree()
		s.reload()
		RdfRenderable.Top rdf = asRdfRenderableService.getArrangementRdf s.t, all: true

		then:
		rdf
	}

	public void 'test tree'() {
		when:
		RdfRenderable.Top rdf = asRdfRenderableService.getClassificationRdf Arrangement.findByLabel('TMP'), all: true

		then:
		rdf
	}

}
