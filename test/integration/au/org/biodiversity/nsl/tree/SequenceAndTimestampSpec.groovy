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

import org.apache.commons.logging.LogFactory
import spock.lang.*


import org.apache.commons.logging.Log

/**
 *
 */
class SequenceAndTimestampSpec extends Specification {
	static final Log log = LogFactory.getLog(SequenceAndTimestampSpec.class)


	// fields
	BasicOperationsService basicOperationsService

	def setup() {
	}

	def cleanup() {
	}

	def setupSpec() {
	}

	def cleanupSpec() {
	}

	// feature methods

	void "test get nextval"() {
		expect:
		basicOperationsService.getNextval() != 0
	}

	void "test get timestamp"() {
		expect:
		basicOperationsService.getTimestamp()
	}
}
